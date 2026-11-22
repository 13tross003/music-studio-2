package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.example.data.model.UiState
import com.example.ui.theme.EngineerCyan
import com.example.ui.theme.LyricistAmber
import com.example.ui.theme.ProducerMint
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.StudioBorderHairline
import com.example.ui.theme.StudioConsoleDark
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioRackSurface
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val telemetry by viewModel.audioTelemetry.collectAsStateWithLifecycle()
    val mixState by viewModel.mixState.collectAsStateWithLifecycle()
    val voiceBoothState by viewModel.voiceBoothState.collectAsStateWithLifecycle()
    val isAiGenerating by viewModel.isAiGenerating.collectAsStateWithLifecycle()
    val isExportingWav by viewModel.isExportingWav.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showProductionFloorSheet by rememberSaveable { mutableStateOf(false) }
    var showSessionsSheet by rememberSaveable { mutableStateOf(false) }
    var pendingAudioAction by remember { mutableStateOf<String?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingAudioAction) {
                "SPEECH" -> viewModel.voiceBoothManager.startSpeechToText()
                "HUM" -> viewModel.voiceBoothManager.startHumRecording()
            }
        }
        pendingAudioAction = null
    }

    fun requestMicThen(action: String, block: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            block()
        } else {
            pendingAudioAction = action
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size, isAiGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
    ) {
        val isExpanded = maxWidth >= 840.dp

        Column(modifier = Modifier.fillMaxSize()) {
            StudioTopConsoleBar(
                session = activeSession,
                telemetry = telemetry,
                voiceBoothState = voiceBoothState,
                showFloorButton = !isExpanded,
                onOpenSessions = { showSessionsSheet = true },
                onTogglePlay = { viewModel.togglePlayback(null) },
                onToggleVoiceToVoice = { viewModel.voiceBoothManager.toggleVoiceToVoice() },
                onOpenProductionFloor = { showProductionFloorSheet = true },
                onSignOut = {
                    viewModel.voiceBoothManager.interruptSpeaking()
                    onSignOut()
                }
            )

            AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
                Surface(
                    color = StudioRackSurface,
                    border = BorderStroke(1.dp, EngineerCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearBanner() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss notice",
                                tint = StudioTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Unified Multi-Agent Chat Column
                Column(
                    modifier = Modifier
                        .weight(if (isExpanded) 0.58f else 1f)
                        .fillMaxHeight()
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (val msgState = messagesState) {
                            is UiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = EngineerCyan)
                                }
                            }
                            is UiState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        color = StudioRackSurface,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = msgState.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = StudioTextPrimary,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                            is UiState.Success -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("studio_chat_list")
                                ) {
                                    items(
                                        items = msgState.data,
                                        key = { it.id.ifBlank { "${it.speaker}_${it.text.hashCode()}" } }
                                    ) { message ->
                                        StudioMessageCard(
                                            message = message,
                                            isAgentSpeaking = voiceBoothState.activeSpeakingRole == message.role &&
                                                message == msgState.data.lastOrNull { it.role == message.role },
                                            telemetry = telemetry,
                                            isExportingWav = isExportingWav,
                                            onPlayStem = { stem -> viewModel.togglePlayback(stem) },
                                            onExportWav = { stem, share -> viewModel.exportAndShareStem(stem, share) }
                                        )
                                    }

                                    if (isAiGenerating) {
                                        item(key = "ai_collaborating") {
                                            CollaborationWorkingCard()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    VoiceBoothInputBar(
                        inputText = inputText,
                        onInputTextChange = { inputText = it },
                        isAiGenerating = isAiGenerating,
                        voiceBoothState = voiceBoothState,
                        onSendText = {
                            val text = inputText
                            inputText = ""
                            viewModel.sendProducerPrompt(text)
                        },
                        onQuickPrompt = { preset ->
                            viewModel.sendProducerPrompt(preset)
                        },
                        onMicSpeechClick = {
                            if (voiceBoothState.isListeningText) {
                                viewModel.voiceBoothManager.stopSpeechToText()
                            } else {
                                requestMicThen("SPEECH") {
                                    viewModel.voiceBoothManager.startSpeechToText()
                                }
                            }
                        },
                        onHumRecordClick = {
                            if (voiceBoothState.isRecordingHum) {
                                viewModel.voiceBoothManager.stopAndSendHumRecording()
                            } else {
                                requestMicThen("HUM") {
                                    viewModel.voiceBoothManager.startHumRecording()
                                }
                            }
                        },
                        onInterruptClick = {
                            viewModel.voiceBoothManager.interruptSpeaking()
                        }
                    )
                }

                // Expanded Tablet/Desktop Side-by-Side Sovereign Production Floor
                if (isExpanded) {
                    Box(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .background(StudioConsoleDark)
                    ) {
                        ProductionFloorPanel(
                            session = activeSession,
                            telemetry = telemetry,
                            mixState = mixState,
                            isExportingWav = isExportingWav,
                            onTogglePlay = { viewModel.togglePlayback(null) },
                            onToggleMute = { viewModel.toggleStemMute(it) },
                            onToggleSolo = { viewModel.toggleStemSolo(it) },
                            onToggleStep = { track, idx -> viewModel.toggleSequencerStep(track, idx) },
                            onUpdateMix = { transform -> viewModel.updateMixControls(transform) },
                            onExportWav = { stem, share -> viewModel.exportAndShareStem(stem, share) }
                        )
                    }
                }
            }
        }

        // Compact Mobile Production Floor Sheet
        if (showProductionFloorSheet && !isExpanded) {
            ModalBottomSheet(
                onDismissRequest = { showProductionFloorSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = StudioConsoleDark,
                contentColor = StudioTextPrimary
            ) {
                ProductionFloorPanel(
                    session = activeSession,
                    telemetry = telemetry,
                    mixState = mixState,
                    isExportingWav = isExportingWav,
                    onTogglePlay = { viewModel.togglePlayback(null) },
                    onToggleMute = { viewModel.toggleStemMute(it) },
                    onToggleSolo = { viewModel.toggleStemSolo(it) },
                    onToggleStep = { track, idx -> viewModel.toggleSequencerStep(track, idx) },
                    onUpdateMix = { transform -> viewModel.updateMixControls(transform) },
                    onExportWav = { stem, share -> viewModel.exportAndShareStem(stem, share) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Studio Sessions Drawer Sheet
        if (showSessionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSessionsSheet = false },
                containerColor = StudioConsoleDark,
                contentColor = StudioTextPrimary
            ) {
                SessionsSheetContent(
                    sessionsState = sessionsState,
                    activeSessionId = activeSession.id,
                    onSelectSession = { sid ->
                        viewModel.selectSession(sid)
                        showSessionsSheet = false
                    },
                    onNewSession = {
                        viewModel.createNewSession("Quantum Session ${(sessionsState as? UiState.Success)?.data?.size?.plus(1) ?: 1}")
                        showSessionsSheet = false
                    },
                    onDeleteSession = { sid -> viewModel.deleteSession(sid) }
                )
            }
        }
    }
}

@Composable
private fun CollaborationWorkingCard() {
    Surface(
        color = StudioRackSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, EngineerCyan.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                color = EngineerCyan,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Engineer & Lyricist are locking in the pocket...",
                    style = MaterialTheme.typography.titleSmall,
                    color = EngineerCyan
                )
                Text(
                    text = "Sculpting sub-bass EQ, HPF headroom, 16-step groove & bars",
                    style = MaterialTheme.typography.labelSmall,
                    color = LyricistAmber
                )
            }
        }
    }
}

@Composable
private fun SessionsSheetContent(
    sessionsState: UiState<List<StudioSession>>,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "STUDIO SESSIONS",
                style = MaterialTheme.typography.titleLarge,
                color = StudioTextPrimary
            )
            Button(
                onClick = onNewSession,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProducerMint,
                    contentColor = StudioObsidian
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("new_session_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Track", style = MaterialTheme.typography.labelLarge)
            }
        }

        HorizontalDivider(color = StudioBorderHairline)

        when (sessionsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EngineerCyan)
                }
            }
            is UiState.Error -> {
                Text(
                    text = sessionsState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = SignalCrimson
                )
            }
            is UiState.Success -> {
                if (sessionsState.data.isEmpty()) {
                    Text(
                        text = "No saved studio sessions yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sessionsState.data.forEach { session ->
                            val isSelected = session.id == activeSessionId
                            Surface(
                                color = if (isSelected) StudioRackSurface else StudioObsidian,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) EngineerCyan else StudioBorderHairline
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSession(session.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = if (isSelected) EngineerCyan else StudioTextMuted
                                        )
                                        Column {
                                            Text(
                                                text = session.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = StudioTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${session.bpm} BPM • ${session.musicalKey} • ${session.genreMood}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StudioTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (sessionsState.data.size > 1) {
                                        IconButton(onClick = { onDeleteSession(session.id) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete Session",
                                                tint = StudioTextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
