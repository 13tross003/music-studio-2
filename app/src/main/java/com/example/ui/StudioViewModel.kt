package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioTelemetry
import com.example.audio.QuantumAudioEngine
import com.example.audio.StemMixState
import com.example.audio.VoiceBoothManager
import com.example.audio.VoiceBoothState
import com.example.data.model.SpeakerRole
import com.example.data.model.StemType
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.example.data.model.UiState
import com.example.data.repository.StudioAiRepository
import com.example.data.repository.StudioRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(
    private val appContext: Context,
    private val repository: StudioRepository,
    private val aiRepository: StudioAiRepository,
    private val audioEngine: QuantumAudioEngine,
    private val currentUserId: String
) : ViewModel() {

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _draftSession = MutableStateFlow<StudioSession?>(null)
    private val _isAiGenerating = MutableStateFlow(false)
    val isAiGenerating: StateFlow<Boolean> = _isAiGenerating.asStateFlow()

    private val _mixState = MutableStateFlow(StemMixState())
    val mixState: StateFlow<StemMixState> = _mixState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isExportingWav = MutableStateFlow(false)
    val isExportingWav: StateFlow<Boolean> = _isExportingWav.asStateFlow()

    private var mixSaveJob: Job? = null
    private var initialSessionSeeded = false

    val audioTelemetry: StateFlow<AudioTelemetry> = audioEngine.telemetry

    val voiceBoothManager = VoiceBoothManager(
        context = appContext,
        onSpeechRecognized = { transcript ->
            sendProducerPrompt(transcript)
        },
        onHumRecorded = { wavBytes ->
            sendProducerHum(wavBytes)
        }
    )

    val voiceBoothState: StateFlow<VoiceBoothState> = voiceBoothManager.state

    val sessionsState: StateFlow<UiState<List<StudioSession>>> =
        repository.observeSessions(currentUserId)
            .map<List<StudioSession>, UiState<List<StudioSession>>> { list ->
                if (list.isEmpty() && !initialSessionSeeded) {
                    initialSessionSeeded = true
                    seedWelcomeSession()
                } else if (_activeSessionId.value == null && list.isNotEmpty()) {
                    _activeSessionId.value = list.first().id
                }
                UiState.Success(list)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                emit(UiState.Error(root.message ?: "Failed to load studio sessions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val activeSession: StateFlow<StudioSession> = combine(
        sessionsState,
        _activeSessionId,
        _draftSession
    ) { sessionsUi, selectedId, draft ->
        val list = (sessionsUi as? UiState.Success)?.data.orEmpty()
        val persisted = list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
        val resolved = when {
            draft != null && (persisted == null || draft.id == persisted.id) -> draft
            persisted != null -> persisted
            else -> StudioSession(userId = currentUserId)
        }
        audioEngine.updateSession(resolved)
        resolved
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = StudioSession(userId = currentUserId)
    )

    val messagesState: StateFlow<UiState<List<StudioMessage>>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf<UiState<List<StudioMessage>>>(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, sessionId)
                    .map<List<StudioMessage>, UiState<List<StudioMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        emit(UiState.Error(root.message ?: "Failed to observe studio thread"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private fun seedWelcomeSession() {
        viewModelScope.launch {
            val starter = StudioSession(
                userId = currentUserId,
                title = "Midnight Hologram",
                genreMood = "Atmospheric Trap Soul",
                musicalKey = "C Minor",
                bpm = 92L,
                hpfHz = 90L,
                subBoostDb = 5.0,
                airPresenceDb = 3.5,
                saturation = 0.28,
                compression = 0.48,
                swing = 0.08,
                activeLyrics = "Neon static on the console glass,\nChasing ghosts out the analog past."
            )
            val created = repository.createSession(starter)
            created.onSuccess { sessionId ->
                _activeSessionId.value = sessionId
                repository.addMessage(
                    sessionId,
                    StudioMessage(
                        userId = currentUserId,
                        speaker = SpeakerRole.ENGINEER.name,
                        text = "Boards are hot, Producer! Seven Grammys on the shelf and we're locking in #8 and #9 right here. I already carved a 90Hz high-pass off the Rhodes keys so that C-minor 808 sub punches straight through your chest.",
                        sampleReference = "Warm Rhodes voicing & knock pocket inspired by Kid Cudi & Kanye West - Gorgeous",
                        mixSummary = "92 BPM • C Minor • 90Hz HPF • +5.0dB Sub Shelf • 28% Tape Drive",
                        hasStemAttachment = true,
                        stemTitle = "Midnight Hologram — Starter Pack",
                        stemBpm = 92L,
                        stemKey = "C Minor"
                    )
                )
                repository.addMessage(
                    sessionId,
                    StudioMessage(
                        userId = currentUserId,
                        speaker = SpeakerRole.LYRICIST.name,
                        text = "Yo Engineer, that 60Hz pocket is crazy—left me wide open air around 4kHz! Producer, drop a mood, hum a melody in the booth, or tell me what story we're bleeding onto this track tonight.",
                        lyricSection = "[Hook Pocket Sketch]\nNeon static on the console glass,\nChasing ghosts out the analog past.",
                        hasStemAttachment = false
                    )
                )
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to create initial session"
            }
        }
    }

    fun selectSession(sessionId: String) {
        _draftSession.value = null
        _activeSessionId.value = sessionId
    }

    fun createNewSession(title: String = "Untitled Session") {
        viewModelScope.launch {
            audioEngine.stop()
            voiceBoothManager.interruptSpeaking()
            val fresh = StudioSession(
                userId = currentUserId,
                title = title
            )
            repository.createSession(fresh)
                .onSuccess { newId ->
                    _draftSession.value = null
                    _activeSessionId.value = newId
                    repository.addMessage(
                        newId,
                        StudioMessage(
                            userId = currentUserId,
                            speaker = SpeakerRole.ENGINEER.name,
                            text = "Fresh canvas loaded. Tell me the BPM, mood, or record you want to flip—and hit the Hum Booth anytime if you hear a melody in your head.",
                            mixSummary = "${fresh.bpm} BPM • ${fresh.musicalKey} • Clean Mastering Bus",
                            hasStemAttachment = true,
                            stemTitle = "${fresh.title} — Clean Rack",
                            stemBpm = fresh.bpm,
                            stemKey = fresh.musicalKey
                        )
                    )
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not create session"
                }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_activeSessionId.value == sessionId) {
                audioEngine.stop()
                _activeSessionId.value = null
                _draftSession.value = null
            }
            repository.deleteSession(sessionId)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Could not delete session"
                }
        }
    }

    fun sendProducerPrompt(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _isAiGenerating.value) return
        voiceBoothManager.interruptSpeaking()
        runCollaboration(cleanPrompt, hummedWav = null)
    }

    fun sendProducerHum(wavBytes: ByteArray) {
        if (wavBytes.isEmpty() || _isAiGenerating.value) return
        runCollaboration(
            producerPrompt = "[Recorded Hum / Melody Idea in Studio Booth — analyze pitch, tempo, and vibe to build beat & bars]",
            hummedWav = wavBytes
        )
    }

    private fun runCollaboration(producerPrompt: String, hummedWav: ByteArray?) {
        viewModelScope.launch {
            _isAiGenerating.value = true
            _statusBanner.value = null

            val session = activeSession.value
            val sessionId = _activeSessionId.value ?: session.id.takeIf { it.isNotBlank() }
            if (sessionId.isNullOrBlank()) {
                _isAiGenerating.value = false
                _statusBanner.value = "Waiting for studio session to initialize..."
                return@launch
            }

            // 1. Persist Producer message immediately
            repository.addMessage(
                sessionId,
                StudioMessage(
                    userId = currentUserId,
                    speaker = SpeakerRole.PRODUCER.name,
                    text = producerPrompt,
                    hasStemAttachment = false
                )
            )

            // 2. Call multi-agent Firebase AI Logic
            val recent = (messagesState.value as? UiState.Success)?.data.orEmpty()
            val aiResult = aiRepository.collaborate(
                producerPrompt = producerPrompt,
                currentSession = session,
                recentMessages = recent,
                hummedAudioWav = hummedWav
            )

            aiResult.onSuccess { turn ->
                val updatedSession = if (turn.shouldUpdateBeat) {
                    session.copy(
                        title = turn.sessionTitle,
                        genreMood = turn.genreMood,
                        musicalKey = turn.musicalKey,
                        bpm = turn.bpm,
                        hpfHz = turn.hpfHz,
                        subBoostDb = turn.subBoostDb,
                        airPresenceDb = turn.airPresenceDb,
                        saturation = turn.saturation,
                        compression = turn.compression,
                        swing = turn.swing,
                        kickPattern = turn.kickPattern,
                        snarePattern = turn.snarePattern,
                        hihatPattern = turn.hihatPattern,
                        bassNotes = turn.bassNotes,
                        melodyNotes = turn.melodyNotes,
                        vocalGuideNotes = turn.vocalGuideNotes,
                        activeLyrics = turn.lyricBars ?: session.activeLyrics
                    ).normalized()
                } else {
                    session.copy(
                        activeLyrics = turn.lyricBars ?: session.activeLyrics
                    ).normalized()
                }

                _draftSession.value = updatedSession
                audioEngine.updateSession(updatedSession)
                repository.updateSessionMix(sessionId, updatedSession)

                // 3. Persist Sound Engineer turn (with stem attachment if beat updated)
                repository.addMessage(
                    sessionId,
                    StudioMessage(
                        userId = currentUserId,
                        speaker = SpeakerRole.ENGINEER.name,
                        text = turn.engineerMessage,
                        sampleReference = turn.engineerSampleReference,
                        mixSummary = turn.mixEngineeringNotes,
                        hasStemAttachment = turn.shouldUpdateBeat,
                        stemTitle = if (turn.shouldUpdateBeat) turn.stemPackTitle else null,
                        stemBpm = if (turn.shouldUpdateBeat) updatedSession.bpm else null,
                        stemKey = if (turn.shouldUpdateBeat) updatedSession.musicalKey else null
                    )
                )

                // 4. Persist AI Lyricist turn
                if (!turn.lyricistMessage.isNullOrBlank() || !turn.lyricBars.isNullOrBlank()) {
                    repository.addMessage(
                        sessionId,
                        StudioMessage(
                            userId = currentUserId,
                            speaker = SpeakerRole.LYRICIST.name,
                            text = turn.lyricistMessage ?: "Locked my cadence right into that pocket—check these bars.",
                            sampleReference = turn.lyricistSampleReference,
                            lyricSection = turn.lyricBars,
                            hasStemAttachment = false
                        )
                    )
                }

                // 5. Auto-audition & voice-to-voice readout
                if (turn.shouldUpdateBeat && !audioTelemetry.value.isPlaying) {
                    audioEngine.startPlayback()
                }
                voiceBoothManager.speakStudioExchange(
                    engineerText = turn.engineerMessage,
                    lyricistText = turn.lyricistMessage
                )
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Studio AI error. Please retry."
            }

            _isAiGenerating.value = false
        }
    }

    fun togglePlayback(stemOverride: StemType? = null) {
        audioEngine.playOrToggle(stemOverride)
    }

    fun toggleStemMute(stem: StemType) {
        val current = _mixState.value
        val next = when (stem) {
            StemType.DRUMS -> current.copy(drumsMuted = !current.drumsMuted, soloStem = null)
            StemType.SUB_BASS -> current.copy(bassMuted = !current.bassMuted, soloStem = null)
            StemType.MELODY -> current.copy(melodyMuted = !current.melodyMuted, soloStem = null)
            StemType.VOCAL_GUIDE -> current.copy(vocalMuted = !current.vocalMuted, soloStem = null)
            StemType.FULL_MIX -> current.copy(soloStem = null)
        }
        _mixState.value = next
        audioEngine.updateMixState(next)
    }

    fun toggleStemSolo(stem: StemType) {
        val current = _mixState.value
        val nextSolo = if (current.soloStem == stem) null else stem
        val next = current.copy(soloStem = nextSolo)
        _mixState.value = next
        audioEngine.updateMixState(next)
    }

    fun updateMixControls(transform: (StudioSession) -> StudioSession) {
        val updated = transform(activeSession.value).normalized()
        _draftSession.value = updated
        audioEngine.updateSession(updated)
        schedulePersistMix(updated)
    }

    fun toggleSequencerStep(track: String, stepIndex: Int) {
        if (stepIndex !in 0..15) return
        updateMixControls { s ->
            when (track) {
                "KICK" -> s.copy(kickPattern = toggleBinaryStep(s.kickPattern, stepIndex, 120L))
                "SNARE" -> s.copy(snarePattern = toggleBinaryStep(s.snarePattern, stepIndex, 120L))
                "HAT" -> s.copy(hihatPattern = toggleBinaryStep(s.hihatPattern, stepIndex, 96L))
                else -> s
            }
        }
    }

    private fun toggleBinaryStep(list: List<Long>, idx: Int, defaultVel: Long): List<Long> {
        val mutable = list.toMutableList()
        while (mutable.size < 16) mutable.add(0L)
        mutable[idx] = if (mutable[idx] > 0L) 0L else defaultVel
        return mutable
    }

    private fun schedulePersistMix(session: StudioSession) {
        val sid = _activeSessionId.value ?: return
        mixSaveJob?.cancel()
        mixSaveJob = viewModelScope.launch {
            delay(450L)
            repository.updateSessionMix(sid, session)
        }
    }

    fun exportAndShareStem(stemType: StemType, shareViaChooser: Boolean) {
        if (_isExportingWav.value) return
        viewModelScope.launch {
            _isExportingWav.value = true
            try {
                val session = activeSession.value
                val wavFile = audioEngine.renderWavFile(appContext, session, stemType, bars = 2)
                if (shareViaChooser) {
                    val uri = audioEngine.getShareUri(appContext, wavFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/wav"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "${session.title} - ${stemType.label}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share ${stemType.label} (.wav)").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(chooser)
                    _statusBanner.value = "Rendered ${wavFile.name} for sharing"
                } else {
                    audioEngine.exportWavToDeviceMusic(
                        appContext,
                        wavFile,
                        "${session.title} (${stemType.label})"
                    ).onSuccess {
                        _statusBanner.value = "Saved ${wavFile.name} to Music/QuantumStudio"
                    }.onFailure { err ->
                        _statusBanner.value = err.localizedMessage ?: "Failed to save WAV stem"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "WAV export failed"
            } finally {
                _isExportingWav.value = false
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        voiceBoothManager.release()
    }
}
