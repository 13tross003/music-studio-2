package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.StemType
import com.example.data.model.StudioSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

data class StemMixState(
    val drumsMuted: Boolean = false,
    val bassMuted: Boolean = false,
    val melodyMuted: Boolean = false,
    val vocalMuted: Boolean = false,
    val soloStem: StemType? = null
) {
    fun isStemActive(stem: StemType): Boolean {
        if (soloStem != null && soloStem != StemType.FULL_MIX) {
            return soloStem == stem
        }
        return when (stem) {
            StemType.DRUMS -> !drumsMuted
            StemType.SUB_BASS -> !bassMuted
            StemType.MELODY -> !melodyMuted
            StemType.VOCAL_GUIDE -> !vocalMuted
            StemType.FULL_MIX -> true
        }
    }
}

data class AudioTelemetry(
    val isPlaying: Boolean = false,
    val currentStep: Int = 0,
    val activeSoloStem: StemType = StemType.FULL_MIX,
    val peakLevel: Float = 0f,
    val bands: List<Float> = List(16) { 0.08f }
)

class QuantumAudioEngine {
    companion object {
        const val SAMPLE_RATE = 44100
        private const val CHUNK_FRAMES = 512
        private const val TWO_PI = 2.0 * PI
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null

    @Volatile
    private var activeSession: StudioSession = StudioSession()

    @Volatile
    private var activeMixState: StemMixState = StemMixState()

    private val _telemetry = MutableStateFlow(AudioTelemetry())
    val telemetry: StateFlow<AudioTelemetry> = _telemetry.asStateFlow()

    fun updateSession(session: StudioSession) {
        activeSession = session.normalized()
    }

    fun updateMixState(mixState: StemMixState) {
        activeMixState = mixState
        _telemetry.value = _telemetry.value.copy(
            activeSoloStem = mixState.soloStem ?: StemType.FULL_MIX
        )
    }

    fun playOrToggle(stemOverride: StemType? = null) {
        val currentTelemetry = _telemetry.value
        if (stemOverride != null) {
            val targetSolo = if (stemOverride == StemType.FULL_MIX) null else stemOverride
            if (currentTelemetry.isPlaying && currentTelemetry.activeSoloStem == stemOverride) {
                stop()
                return
            }
            updateMixState(activeMixState.copy(soloStem = targetSolo))
            if (!currentTelemetry.isPlaying) {
                startPlayback()
            }
        } else {
            if (currentTelemetry.isPlaying) {
                stop()
            } else {
                startPlayback()
            }
        }
    }

    fun startPlayback() {
        if (playbackJob?.isActive == true) return
        playbackJob = scope.launch {
            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(CHUNK_FRAMES * 4 * 2)

            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val audioTrack = trackBuilder.build()
            val synth = DspSynthState()
            val pcmBuffer = ShortArray(CHUNK_FRAMES * 2)

            try {
                audioTrack.play()
                _telemetry.value = _telemetry.value.copy(isPlaying = true)

                while (isActive) {
                    val session = activeSession
                    val mix = activeMixState
                    var peak = 0f
                    var bassEnergy = 0f
                    var midEnergy = 0f
                    var highEnergy = 0f

                    for (frame in 0 until CHUNK_FRAMES) {
                        val (left, right, stepIdx, bE, mE, hE) = synth.nextFrame(session, mix)
                        val lShort = (left.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
                        val rShort = (right.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
                        pcmBuffer[frame * 2] = lShort
                        pcmBuffer[frame * 2 + 1] = rShort

                        val absPeak = max(abs(left), abs(right)).toFloat()
                        if (absPeak > peak) peak = absPeak
                        bassEnergy += bE
                        midEnergy += mE
                        highEnergy += hE
                    }

                    val normBass = (bassEnergy / CHUNK_FRAMES * 2.8f).coerceIn(0f, 1f)
                    val normMid = (midEnergy / CHUNK_FRAMES * 3.4f).coerceIn(0f, 1f)
                    val normHigh = (highEnergy / CHUNK_FRAMES * 4.5f).coerceIn(0f, 1f)
                    val stepIdx = synth.currentStep

                    val bands = List(16) { idx ->
                        val base = when {
                            idx < 4 -> normBass * (1.0f - idx * 0.1f)
                            idx < 10 -> normMid * (0.75f + 0.25f * sin(idx + stepIdx * 0.7f))
                            else -> normHigh * (0.7f + 0.3f * sin(idx * 1.3f + stepIdx))
                        }
                        val stepAccent = if (idx == stepIdx) 0.22f else 0f
                        (base + stepAccent).coerceIn(0.06f, 1f)
                    }

                    _telemetry.value = AudioTelemetry(
                        isPlaying = true,
                        currentStep = stepIdx,
                        activeSoloStem = mix.soloStem ?: StemType.FULL_MIX,
                        peakLevel = peak.coerceIn(0f, 1f),
                        bands = bands
                    )

                    audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
                }
            } finally {
                runCatching {
                    audioTrack.pause()
                    audioTrack.flush()
                    audioTrack.stop()
                    audioTrack.release()
                }
                _telemetry.value = AudioTelemetry(
                    isPlaying = false,
                    currentStep = 0,
                    activeSoloStem = activeMixState.soloStem ?: StemType.FULL_MIX,
                    peakLevel = 0f,
                    bands = List(16) { 0.08f }
                )
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _telemetry.value = _telemetry.value.copy(
            isPlaying = false,
            peakLevel = 0f,
            bands = List(16) { 0.08f }
        )
    }

    fun release() {
        stop()
    }

    suspend fun renderWavFile(
        context: Context,
        session: StudioSession,
        stemType: StemType,
        bars: Int = 2
    ): File = withContext(Dispatchers.Default) {
        val clean = session.normalized()
        val mix = when (stemType) {
            StemType.FULL_MIX -> StemMixState()
            else -> StemMixState(soloStem = stemType)
        }
        val secondsPerBeat = 60.0 / clean.bpm.toDouble()
        val totalFrames = (secondsPerBeat * 4.0 * bars * SAMPLE_RATE).toInt().coerceAtLeast(SAMPLE_RATE)

        val dir = File(context.filesDir, "stems").apply { mkdirs() }
        val safeTitle = clean.title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "quantum_track" }
        val fileName = "${safeTitle}_${clean.bpm}bpm_${stemType.fileSuffix}.wav"
        val outFile = File(dir, fileName)

        val synth = DspSynthState()
        val pcmBytes = ByteArray(totalFrames * 4)
        var byteIdx = 0
        for (frame in 0 until totalFrames) {
            val (left, right, _, _, _, _) = synth.nextFrame(clean, mix)
            val lShort = (left.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
            val rShort = (right.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
            pcmBytes[byteIdx++] = (lShort.toInt() and 0xFF).toByte()
            pcmBytes[byteIdx++] = ((lShort.toInt() shr 8) and 0xFF).toByte()
            pcmBytes[byteIdx++] = (rShort.toInt() and 0xFF).toByte()
            pcmBytes[byteIdx++] = ((rShort.toInt() shr 8) and 0xFF).toByte()
        }

        FileOutputStream(outFile).use { fos ->
            fos.write(createWavHeader(pcmBytes.size, SAMPLE_RATE, 2, 16))
            fos.write(pcmBytes)
        }
        outFile
    }

    suspend fun exportWavToDeviceMusic(
        context: Context,
        wavFile: File,
        displayTitle: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, wavFile.name)
                    put(MediaStore.Audio.Media.TITLE, displayTitle)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/QuantumStudio")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to create MediaStore audio entry."))
                resolver.openOutputStream(uri)?.use { out ->
                    wavFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success(uri)
            } else {
                val musicDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "QuantumStudio").apply { mkdirs() }
                val dest = File(musicDir, wavFile.name)
                wavFile.copyTo(dest, overwrite = true)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
                Result.success(uri)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getShareUri(context: Context, wavFile: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", wavFile)

    private data class FrameOutput(
        val left: Double,
        val right: Double,
        val step: Int,
        val bassE: Float,
        val midE: Float,
        val highE: Float
    )

    private class DspSynthState {
        var currentStep: Int = 0
            private set

        private var samplesIntoStep = 0.0
        private var noiseSeed = 123456789

        // Drum envelopes
        private var kickPhase = 0.0
        private var kickTime = 1.0
        private var kickVel = 0.0

        private var snarePhase = 0.0
        private var snareTime = 1.0
        private var snareVel = 0.0

        private var hatTime = 1.0
        private var hatVel = 0.0
        private var hatPrevSample = 0.0
        private var hatPrevOutput = 0.0

        // Sub-bass 808 state
        private var bassPhase = 0.0
        private var bassFreq = 55.0
        private var bassTargetFreq = 55.0
        private var bassEnv = 0.0
        private var bassTime = 1.0

        // Melody Rhodes/Pad state
        private var melPhase1 = 0.0
        private var melPhase2 = 0.0
        private var melPhase3 = 0.0
        private var melFreq = 261.63
        private var melTime = 1.0
        private var melActive = false

        // Vocal Guide Formant Lead state
        private var vocPhase = 0.0
        private var vocVibratoPhase = 0.0
        private var vocFreq = 440.0
        private var vocTime = 1.0
        private var vocActive = false

        // DSP Filter states (HPF on melodic/vocal, Low-shelf sub, High-shelf air, Compressor)
        private var hpfPrevInL = 0.0
        private var hpfPrevOutL = 0.0
        private var hpfPrevInR = 0.0
        private var hpfPrevOutR = 0.0
        private var subFilterL = 0.0
        private var subFilterR = 0.0
        private var airFilterL = 0.0
        private var airFilterR = 0.0
        private var compEnvelope = 0.0

        private fun nextNoise(): Double {
            noiseSeed = noiseSeed * 1664525 + 1013904223
            return ((noiseSeed shr 16) and 0x7FFF) / 16384.0 - 1.0
        }

        private fun midiToHz(midi: Long): Double =
            440.0 * 2.0.pow((midi - 69.0) / 12.0)

        fun nextFrame(session: StudioSession, mix: StemMixState): FrameOutput {
            val baseStepSamples = (SAMPLE_RATE * 60.0 / session.bpm.toDouble()) / 4.0
            val swingOffset = if (currentStep % 2 == 1) 1.0 + session.swing else 1.0 - session.swing * 0.5
            val stepLength = (baseStepSamples * swingOffset).coerceAtLeast(1000.0)

            if (samplesIntoStep == 0.0) {
                triggerStep(session, currentStep)
            }

            val dt = 1.0 / SAMPLE_RATE

            // 1. Drums synthesis
            var drumL = 0.0
            var drumR = 0.0
            if (mix.isStemActive(StemType.DRUMS)) {
                // Kick with pitch sweep
                val kFreq = 44.0 + 115.0 * exp(-kickTime * 34.0)
                kickPhase += TWO_PI * kFreq * dt
                val kEnv = exp(-kickTime * 7.8) * kickVel
                val kickSample = sin(kickPhase) * kEnv * 0.92 + sin(kickPhase * 2.0) * exp(-kickTime * 28.0) * kickVel * 0.22
                kickTime += dt

                // Snare body + crisp noise
                snarePhase += TWO_PI * 192.0 * dt
                val sBody = sin(snarePhase) * exp(-snareTime * 24.0) * 0.45
                val sNoise = nextNoise() * exp(-snareTime * 13.5) * 0.55
                val snareSample = (sBody + sNoise) * snareVel
                snareTime += dt

                // Hi-hat high-passed metallic burst
                val rawNoise = nextNoise()
                val hpHat = 0.85 * (hatPrevOutput + rawNoise - hatPrevSample)
                hatPrevSample = rawNoise
                hatPrevOutput = hpHat
                val hatSample = hpHat * exp(-hatTime * 38.0) * hatVel * 0.38
                hatTime += dt

                drumL = kickSample + snareSample * 0.94 + hatSample * 0.72
                drumR = kickSample + snareSample * 1.06 + hatSample * 1.24
            }

            // 2. 808 Sub-Bass synthesis
            var subSample = 0.0
            if (mix.isStemActive(StemType.SUB_BASS)) {
                bassFreq += (bassTargetFreq - bassFreq) * 0.008
                bassPhase += TWO_PI * bassFreq * dt
                val attack = min(1.0, bassTime / 0.008)
                val decay = exp(-bassTime * 3.4)
                val env = attack * decay * bassEnv
                val fundamental = sin(bassPhase)
                val secondHarm = 0.24 * sin(bassPhase * 2.0)
                subSample = (fundamental + secondHarm) * env * 0.75
                bassTime += dt
            }

            // 3. Melody Rhodes/Synth Keys
            var melL = 0.0
            var melR = 0.0
            if (mix.isStemActive(StemType.MELODY) && melActive) {
                melPhase1 += TWO_PI * melFreq * dt
                melPhase2 += TWO_PI * (melFreq * 1.003) * dt
                melPhase3 += TWO_PI * (melFreq * 1.5) * dt // fifth shimmer
                val attack = min(1.0, melTime / 0.014)
                val decay = exp(-melTime * 2.8)
                val env = attack * decay
                val voiceL = (sin(melPhase1) * 0.65 + sin(melPhase1 * 2.0) * 0.18 + sin(melPhase3) * 0.22) * env
                val voiceR = (sin(melPhase2) * 0.65 + sin(melPhase2 * 2.0) * 0.18 + sin(melPhase3) * 0.22) * env
                melL = voiceL * 0.36
                melR = voiceR * 0.36
                melTime += dt
            }

            // 4. Vocal Guide Formant Topline
            var vocL = 0.0
            var vocR = 0.0
            if (mix.isStemActive(StemType.VOCAL_GUIDE) && vocActive) {
                vocVibratoPhase += TWO_PI * 5.2 * dt
                val vibFreq = vocFreq * (1.0 + 0.006 * sin(vocVibratoPhase))
                vocPhase += TWO_PI * vibFreq * dt
                val attack = min(1.0, vocTime / 0.025)
                val decay = exp(-vocTime * 3.2)
                val env = attack * decay
                val formant = (sin(vocPhase) * 0.6 + sin(vocPhase * 2.0) * 0.28 + sin(vocPhase * 3.0) * 0.12) * env
                vocL = formant * 0.30
                vocR = formant * 0.30
                vocTime += dt
            }

            // Apply Surgical High-Pass Filter (HPF) to Melody + Vocal Guide
            val hpfRc = 1.0 / (TWO_PI * session.hpfHz.toDouble().coerceIn(20.0, 350.0))
            val hpfAlpha = hpfRc / (hpfRc + dt)
            val preHpfL = melL + vocL
            val preHpfR = melR + vocR
            val postHpfL = hpfAlpha * (hpfPrevOutL + preHpfL - hpfPrevInL)
            val postHpfR = hpfAlpha * (hpfPrevOutR + preHpfR - hpfPrevInR)
            hpfPrevInL = preHpfL
            hpfPrevOutL = postHpfL
            hpfPrevInR = preHpfR
            hpfPrevOutR = postHpfR

            // Sum Stems
            var sumL = drumL + subSample + postHpfL
            var sumR = drumR + subSample + postHpfR

            // Parametric Sub-Bass Low-Shelf Boost (~65Hz)
            val subBoostGain = 10.0.pow(session.subBoostDb / 20.0) - 1.0
            val lpAlpha = (TWO_PI * 75.0 * dt) / (1.0 + TWO_PI * 75.0 * dt)
            subFilterL += lpAlpha * (sumL - subFilterL)
            subFilterR += lpAlpha * (sumR - subFilterR)
            sumL += subFilterL * subBoostGain * 0.65
            sumR += subFilterR * subBoostGain * 0.65

            // High-Shelf Air / Presence EQ (~4.5kHz)
            val airGain = 10.0.pow(session.airPresenceDb / 20.0) - 1.0
            val airAlpha = (TWO_PI * 4200.0 * dt) / (1.0 + TWO_PI * 4200.0 * dt)
            airFilterL += airAlpha * (sumL - airFilterL)
            airFilterR += airAlpha * (sumR - airFilterR)
            val highOnlyL = sumL - airFilterL
            val highOnlyR = sumR - airFilterR
            sumL += highOnlyL * airGain * 0.55
            sumR += highOnlyR * airGain * 0.55

            // Harmonic Analog Saturation
            val drive = 1.0 + session.saturation * 2.8
            if (drive > 1.02) {
                val norm = tanh(drive)
                sumL = tanh(sumL * drive) / norm
                sumR = tanh(sumR * drive) / norm
            }

            // Dynamic Range Master Bus Compressor
            val level = max(abs(sumL), abs(sumR))
            val envCoeff = if (level > compEnvelope) 0.02 else 0.0005
            compEnvelope += envCoeff * (level - compEnvelope)
            val threshold = 0.42 - session.compression * 0.22
            val compGain = if (compEnvelope > threshold && session.compression > 0.01) {
                val over = compEnvelope - threshold
                val target = threshold + over / (1.0 + session.compression * 4.5)
                (target / compEnvelope) * (1.0 + session.compression * 0.35)
            } else {
                1.0 + session.compression * 0.22
            }

            val outL = tanh(sumL * compGain * 0.85)
            val outR = tanh(sumR * compGain * 0.85)

            val stepOut = currentStep
            samplesIntoStep += 1.0
            if (samplesIntoStep >= stepLength) {
                samplesIntoStep = 0.0
                currentStep = (currentStep + 1) % 16
            }

            return FrameOutput(
                left = outL,
                right = outR,
                step = stepOut,
                bassE = abs(subSample + subFilterL * 0.4).toFloat(),
                midE = abs(postHpfL).toFloat(),
                highE = abs(highOnlyL + drumL * 0.3).toFloat()
            )
        }

        private fun triggerStep(session: StudioSession, step: Int) {
            val k = session.kickPattern.getOrElse(step) { 0L }
            if (k > 0L) {
                kickVel = (k / 127.0).coerceIn(0.1, 1.0)
                kickTime = 0.0
                kickPhase = 0.0
            }

            val s = session.snarePattern.getOrElse(step) { 0L }
            if (s > 0L) {
                snareVel = (s / 127.0).coerceIn(0.1, 1.0)
                snareTime = 0.0
                snarePhase = 0.0
            }

            val h = session.hihatPattern.getOrElse(step) { 0L }
            if (h > 0L) {
                hatVel = (h / 127.0).coerceIn(0.1, 1.0)
                hatTime = 0.0
            }

            val bassNote = session.bassNotes.getOrElse(step) { 0L }
            if (bassNote > 0L) {
                bassTargetFreq = midiToHz(bassNote)
                if (bassEnv < 0.05) bassFreq = bassTargetFreq
                bassEnv = 1.0
                bassTime = 0.0
            }

            val melNote = session.melodyNotes.getOrElse(step) { 0L }
            if (melNote > 0L) {
                melFreq = midiToHz(melNote)
                melTime = 0.0
                melActive = true
            }

            val vocNote = session.vocalGuideNotes.getOrElse(step) { 0L }
            if (vocNote > 0L) {
                vocFreq = midiToHz(vocNote)
                vocTime = 0.0
                vocActive = true
            }
        }
    }

    private fun createWavHeader(
        pcmDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val totalDataLen = pcmDataSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmDataSize)
        }.array()
    }
}

fun wrapPcmAsWav(pcm16Mono: ByteArray, sampleRate: Int = 16000): ByteArray {
    val out = ByteArrayOutputStream(44 + pcm16Mono.size)
    val totalDataLen = pcm16Mono.size + 36
    val byteRate = sampleRate * 2
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt(totalDataLen)
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)
        putShort(1.toShort())
        putShort(1.toShort())
        putInt(sampleRate)
        putInt(byteRate)
        putShort(2.toShort())
        putShort(16.toShort())
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(pcmDataSizeSafe(pcm16Mono.size))
    }.array()
    out.write(header)
    out.write(pcm16Mono)
    return out.toByteArray()
}

private fun pcmDataSizeSafe(size: Int): Int = size
