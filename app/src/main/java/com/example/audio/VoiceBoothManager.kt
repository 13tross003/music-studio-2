package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.model.SpeakerRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private const val BOOTH_TAG = "VoiceBooth"

data class VoiceBoothState(
    val isVoiceToVoiceEnabled: Boolean = true,
    val isListeningText: Boolean = false,
    val isRecordingHum: Boolean = false,
    val activeSpeakingRole: SpeakerRole? = null,
    val partialTranscript: String = "",
    val humInputLevel: Float = 0f,
    val statusHint: String? = null
)

class VoiceBoothManager(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onHumRecorded: (ByteArray) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(VoiceBoothState())
    val state: StateFlow<VoiceBoothState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var engineerVoice: Voice? = null
    private var lyricistVoice: Voice? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var humJob: Job? = null
    private var humOutStream: ByteArrayOutputStream? = null

    private val speechQueue = ArrayDeque<Pair<SpeakerRole, String>>()
    private var currentUtteranceId: String? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                val voices = runCatching {
                    tts?.voices?.filter {
                        it.locale.language == "en" && !it.isNetworkConnectionRequired
                    }.orEmpty()
                }.getOrDefault(emptyList())

                if (voices.size >= 2) {
                    engineerVoice = voices[0]
                    lyricistVoice = voices[1]
                } else if (voices.size == 1) {
                    engineerVoice = voices[0]
                    lyricistVoice = voices[0]
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        scope.launch {
                            if (utteranceId == currentUtteranceId) {
                                playNextInQueue()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        scope.launch {
                            if (utteranceId == currentUtteranceId) {
                                playNextInQueue()
                            }
                        }
                    }
                })
                ttsReady = true
            } else {
                Log.w(BOOTH_TAG, "TTS init failed with status=$status")
            }
        }
    }

    fun toggleVoiceToVoice() {
        val next = !_state.value.isVoiceToVoiceEnabled
        _state.value = _state.value.copy(isVoiceToVoiceEnabled = next)
        if (!next) {
            interruptSpeaking()
        }
    }

    fun speakStudioExchange(engineerText: String?, lyricistText: String?) {
        if (!_state.value.isVoiceToVoiceEnabled || !ttsReady) return
        interruptSpeaking()
        if (!engineerText.isNullOrBlank()) {
            speechQueue.addLast(SpeakerRole.ENGINEER to cleanForSpeech(engineerText))
        }
        if (!lyricistText.isNullOrBlank()) {
            speechQueue.addLast(SpeakerRole.LYRICIST to cleanForSpeech(lyricistText))
        }
        playNextInQueue()
    }

    private fun playNextInQueue() {
        val next = speechQueue.removeFirstOrNull()
        if (next == null) {
            currentUtteranceId = null
            _state.value = _state.value.copy(activeSpeakingRole = null)
            return
        }
        val (role, text) = next
        _state.value = _state.value.copy(activeSpeakingRole = role)
        val engine = tts ?: return

        when (role) {
            SpeakerRole.ENGINEER -> {
                engineerVoice?.let { runCatching { engine.voice = it } }
                engine.setPitch(0.84f)
                engine.setSpeechRate(1.03f)
            }
            SpeakerRole.LYRICIST -> {
                lyricistVoice?.let { runCatching { engine.voice = it } }
                engine.setPitch(1.08f)
                engine.setSpeechRate(0.95f)
            }
            SpeakerRole.PRODUCER -> {
                engine.setPitch(1.0f)
                engine.setSpeechRate(1.0f)
            }
        }

        val uttId = UUID.randomUUID().toString()
        currentUtteranceId = uttId
        val params = Bundle()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, uttId)
    }

    fun interruptSpeaking() {
        speechQueue.clear()
        currentUtteranceId = null
        runCatching { tts?.stop() }
        _state.value = _state.value.copy(activeSpeakingRole = null)
    }

    fun startSpeechToText() {
        interruptSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                statusHint = "On-device speech recognition unavailable; use Hum Booth or text input."
            )
            return
        }

        stopSpeechToText()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            speechRecognizer = it
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(
                    isListeningText = true,
                    partialTranscript = "",
                    statusHint = "Booth mic live — speak to Engineer & Lyricist..."
                )
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _state.value = _state.value.copy(humInputLevel = level)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isListeningText = false, humInputLevel = 0f)
            }

            override fun onError(error: Int) {
                _state.value = _state.value.copy(
                    isListeningText = false,
                    humInputLevel = 0f,
                    partialTranscript = "",
                    statusHint = if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                        "Didn't catch that — tap mic and speak clearly."
                    } else null
                )
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val final = matches?.firstOrNull()?.trim().orEmpty()
                _state.value = _state.value.copy(
                    isListeningText = false,
                    partialTranscript = "",
                    humInputLevel = 0f,
                    statusHint = null
                )
                if (final.isNotBlank()) {
                    onSpeechRecognized(final)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                _state.value = _state.value.copy(partialTranscript = partial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    fun stopSpeechToText() {
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
        _state.value = _state.value.copy(isListeningText = false, humInputLevel = 0f)
    }

    @SuppressLint("MissingPermission")
    fun startHumRecording() {
        interruptSpeaking()
        stopSpeechToText()
        if (_state.value.isRecordingHum) return

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
        } catch (e: Exception) {
            Log.e(BOOTH_TAG, "AudioRecord init failed", e)
            _state.value = _state.value.copy(statusHint = "Microphone unavailable for hum capture.")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            _state.value = _state.value.copy(statusHint = "Microphone failed to initialize.")
            return
        }

        val outStream = ByteArrayOutputStream()
        humOutStream = outStream
        _state.value = _state.value.copy(
            isRecordingHum = true,
            statusHint = "Hum your melody or beatbox groove — tap again to send to Engineer!"
        )

        humJob = scope.launch(Dispatchers.IO) {
            val shortBuf = ShortArray(minBuf / 2)
            val byteBuf = ByteArray(minBuf)
            val maxBytes = sampleRate * 2 * 12 // 12 seconds max
            try {
                recorder.startRecording()
                while (isActive && outStream.size() < maxBytes) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        var maxAmp = 0
                        var bi = 0
                        for (i in 0 until read) {
                            val s = shortBuf[i]
                            val a = abs(s.toInt())
                            if (a > maxAmp) maxAmp = a
                            byteBuf[bi++] = (s.toInt() and 0xFF).toByte()
                            byteBuf[bi++] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        outStream.write(byteBuf, 0, bi)
                        val norm = (maxAmp / 16000f).coerceIn(0f, 1f)
                        _state.value = _state.value.copy(humInputLevel = norm)
                    }
                }
            } finally {
                runCatching {
                    recorder.stop()
                    recorder.release()
                }
            }
        }
    }

    fun stopAndSendHumRecording() {
        if (!_state.value.isRecordingHum) return
        humJob?.cancel()
        humJob = null
        val pcm = humOutStream?.toByteArray() ?: ByteArray(0)
        humOutStream = null
        _state.value = _state.value.copy(
            isRecordingHum = false,
            humInputLevel = 0f,
            statusHint = null
        )
        if (pcm.size > 3200) {
            val wav = wrapPcmAsWav(pcm, 16000)
            onHumRecorded(wav)
        } else {
            _state.value = _state.value.copy(statusHint = "Hum take was too short — hold or tap to record at least 1s.")
        }
    }

    fun clearStatusHint() {
        _state.value = _state.value.copy(statusHint = null)
    }

    private fun cleanForSpeech(input: String): String =
        input.replace(Regex("[*_`#]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)

    fun release() {
        interruptSpeaking()
        stopSpeechToText()
        humJob?.cancel()
        runCatching { tts?.shutdown() }
    }
}
