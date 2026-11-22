package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.AudioTelemetry
import com.example.audio.VoiceBoothState
import com.example.data.model.SpeakerRole
import com.example.data.model.StemType
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.example.ui.theme.EngineerCyan
import com.example.ui.theme.EngineerCyanDim
import com.example.ui.theme.GrammyGold
import com.example.ui.theme.GrammyGoldDim
import com.example.ui.theme.LyricistAmber
import com.example.ui.theme.LyricistSurface
import com.example.ui.theme.LyricistViolet
import com.example.ui.theme.ProducerMint
import com.example.ui.theme.ProducerMintDim
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonDim
import com.example.ui.theme.StudioBorderHairline
import com.example.ui.theme.StudioConsoleDark
import com.example.ui.theme.StudioModuleCard
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioRackSurface
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@Composable
fun StudioTopConsoleBar(
    session: StudioSession,
    telemetry: AudioTelemetry,
    voiceBoothState: VoiceBoothState,
    showFloorButton: Boolean,
    onOpenSessions: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleVoiceToVoice: () -> Unit,
    onOpenProductionFloor: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        color = StudioConsoleDark,
        border = BorderStroke(1.dp, StudioBorderHairline.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onOpenSessions,
                        modifier = Modifier.testTag("sessions_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = "Studio Sessions",
                            tint = StudioTextSecondary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${session.bpm} BPM • ${session.musicalKey}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EngineerCyan
                            )
                            Text(
                                text = "• HPF ${session.hpfHz}Hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = GrammyGold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleVoiceToVoice,
                        modifier = Modifier.testTag("voice_to_voice_toggle")
                    ) {
                        Icon(
                            imageVector = if (voiceBoothState.isVoiceToVoiceEnabled) {
                                Icons.Rounded.VolumeUp
                            } else {
                                Icons.Rounded.VolumeOff
                            },
                            contentDescription = "Toggle AI Voice Playback",
                            tint = if (voiceBoothState.isVoiceToVoiceEnabled) ProducerMint else StudioTextMuted
                        )
                    }

                    if (showFloorButton) {
                        IconButton(
                            onClick = onOpenProductionFloor,
                            modifier = Modifier.testTag("open_production_floor_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Open Production Floor & DSP",
                                tint = EngineerCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign Out",
                            tint = StudioTextMuted
                        )
                    }
                }
            }

            // Holographic Transport + Live Spectrum Strip
            Surface(
                color = StudioRackSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (telemetry.isPlaying) EngineerCyan.copy(alpha = 0.5f) else StudioBorderHairline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = if (telemetry.isPlaying) EngineerCyan else StudioModuleCard,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onTogglePlay)
                            .testTag("master_play_toggle")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (telemetry.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (telemetry.isPlaying) "Pause Beat" else "Play Beat",
                                tint = if (telemetry.isPlaying) StudioObsidian else EngineerCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    HolographicSpectrumCanvas(
                        bands = telemetry.bands,
                        isPlaying = telemetry.isPlaying,
                        activeStep = telemetry.currentStep,
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                    )

                    Surface(
                        color = if (telemetry.activeSoloStem != StemType.FULL_MIX) {
                            GrammyGoldDim
                        } else {
                            StudioModuleCard
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (telemetry.isPlaying) {
                                "${telemetry.activeSoloStem.label.uppercase()} • S${telemetry.currentStep + 1}"
                            } else {
                                "READY • ${session.genreMood.take(16)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.isPlaying) GrammyGold else StudioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HolographicSpectrumCanvas(
    bands: List<Float>,
    isPlaying: Boolean,
    activeStep: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = bands.size.coerceAtLeast(1)
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
        for (i in 0 until count) {
            val mag = bands[i].coerceIn(0.06f, 1f)
            val barH = size.height * mag
            val x = i * (barWidth + gap)
            val y = size.height - barH
            val barColor = when {
                !isPlaying -> StudioBorderHairline
                i == activeStep -> GrammyGold
                i < 4 -> EngineerCyan
                i < 10 -> ProducerMint
                else -> LyricistAmber
            }
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}

@Composable
fun StudioMessageCard(
    message: StudioMessage,
    isAgentSpeaking: Boolean,
    telemetry: AudioTelemetry,
    isExportingWav: Boolean,
    onPlayStem: (StemType) -> Unit,
    onExportWav: (StemType, Boolean) -> Unit
) {
    val role = message.role
    val accent = when (role) {
        SpeakerRole.PRODUCER -> ProducerMint
        SpeakerRole.ENGINEER -> EngineerCyan
        SpeakerRole.LYRICIST -> LyricistAmber
    }
    val cardBg = when (role) {
        SpeakerRole.PRODUCER -> ProducerMintDim.copy(alpha = 0.55f)
        SpeakerRole.ENGINEER -> StudioRackSurface
        SpeakerRole.LYRICIST -> LyricistSurface.copy(alpha = 0.72f)
    }

    val pulse = rememberInfiniteTransition(label = "agent_pulse")
    val borderAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    val borderColor = if (isAgentSpeaking) accent.copy(alpha = borderAlpha) else accent.copy(alpha = 0.28f)

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (isAgentSpeaking) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_card_${role.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Speaker Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (role) {
                        SpeakerRole.ENGINEER -> {
                            Image(
                                painter = painterResource(id = R.drawable.img_engineer_avatar),
                                contentDescription = "Sound Engineer",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, EngineerCyan, CircleShape)
                            )
                        }
                        SpeakerRole.LYRICIST -> {
                            Image(
                                painter = painterResource(id = R.drawable.img_lyricist_avatar),
                                contentDescription = "AI Lyricist",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, LyricistAmber, CircleShape)
                            )
                        }
                        SpeakerRole.PRODUCER -> {
                            Surface(
                                color = ProducerMint.copy(alpha = 0.18f),
                                shape = CircleShape,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.RecordVoiceOver,
                                        contentDescription = "Producer",
                                        tint = ProducerMint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        Text(
                            text = when (role) {
                                SpeakerRole.PRODUCER -> "THE PRODUCER (YOU)"
                                SpeakerRole.ENGINEER -> "SOUND ENGINEER"
                                SpeakerRole.LYRICIST -> "AI LYRICIST"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = accent
                        )
                        Text(
                            text = when (role) {
                                SpeakerRole.PRODUCER -> "Executive Direction"
                                SpeakerRole.ENGINEER -> "7× Grammy Winner • Chasing #8 & #9"
                                SpeakerRole.LYRICIST -> "Elite Pen • Engineer's Best Friend"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextMuted
                        )
                    }
                }

                if (isAgentSpeaking) {
                    Surface(
                        color = accent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Speaking",
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                        }
                    }
                }
            }

            // Conversational Message Body
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = StudioTextPrimary
            )

            // Mandatory Re-Innovation & Sample Transparency Callout
            if (!message.sampleReference.isNullOrBlank()) {
                Surface(
                    color = GrammyGoldDim.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GrammyGold.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = "Sample Transparency Reference",
                            tint = GrammyGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "RE-INNOVATION & SAMPLE TRANSPARENCY",
                                style = MaterialTheme.typography.labelSmall,
                                color = GrammyGold
                            )
                            Text(
                                text = message.sampleReference,
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioTextPrimary
                            )
                        }
                    }
                }
            }

            // Lyricist Bars Sheet
            if (!message.lyricSection.isNullOrBlank()) {
                Surface(
                    color = StudioObsidian.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LyricistViolet.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = LyricistViolet,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "POCKET-SYNCED LYRICS",
                                style = MaterialTheme.typography.labelSmall,
                                color = LyricistViolet
                            )
                        }
                        Text(
                            text = message.lyricSection,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextPrimary
                        )
                    }
                }
            }

            // Engineer DSP Readout + Playable/Downloadable Multi-Stem Rack
            if (message.hasStemAttachment) {
                Surface(
                    color = StudioObsidian.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EngineerCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.stemTitle ?: "Multi-Stem Master Rack",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!message.mixSummary.isNullOrBlank()) {
                                    Text(
                                        text = message.mixSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EngineerCyan
                                    )
                                }
                            }

                            Surface(
                                color = if (telemetry.isPlaying && telemetry.activeSoloStem == StemType.FULL_MIX) {
                                    EngineerCyan
                                } else {
                                    EngineerCyanDim
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { onPlayStem(StemType.FULL_MIX) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (telemetry.isPlaying && telemetry.activeSoloStem == StemType.FULL_MIX) {
                                            Icons.Rounded.Stop
                                        } else {
                                            Icons.Rounded.PlayArrow
                                        },
                                        contentDescription = "Audition Master Mix",
                                        tint = if (telemetry.isPlaying && telemetry.activeSoloStem == StemType.FULL_MIX) {
                                            StudioObsidian
                                        } else {
                                            EngineerCyan
                                        }
                                    )
                                }
                            }
                        }

                        // Audition Individual Stems Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                StemType.DRUMS,
                                StemType.SUB_BASS,
                                StemType.MELODY,
                                StemType.VOCAL_GUIDE
                            ).forEach { stem ->
                                val isActive = telemetry.isPlaying && telemetry.activeSoloStem == stem
                                Surface(
                                    color = if (isActive) ProducerMint else StudioModuleCard,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isActive) ProducerMint else StudioBorderHairline
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clickable { onPlayStem(stem) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isActive) Icons.Rounded.Stop else Icons.Rounded.Equalizer,
                                            contentDescription = null,
                                            tint = if (isActive) StudioObsidian else ProducerMint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = stem.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActive) StudioObsidian else StudioTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Download & Share WAV Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = StudioModuleCard,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, StudioBorderHairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = !isExportingWav) {
                                        onExportWav(StemType.FULL_MIX, false)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Save WAV",
                                        tint = EngineerCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isExportingWav) "Rendering..." else "Save WAV",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StudioTextPrimary
                                    )
                                }
                            }

                            Surface(
                                color = StudioModuleCard,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, StudioBorderHairline),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = !isExportingWav) {
                                        onExportWav(StemType.FULL_MIX, true)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share WAV",
                                        tint = GrammyGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Share Stem",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StudioTextPrimary
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

@Composable
fun VoiceBoothInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isAiGenerating: Boolean,
    voiceBoothState: VoiceBoothState,
    onSendText: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onMicSpeechClick: () -> Unit,
    onHumRecordClick: () -> Unit,
    onInterruptClick: () -> Unit
) {
    Surface(
        color = StudioConsoleDark,
        border = BorderStroke(1.dp, StudioBorderHairline.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mid-Stream Barge-In Banner
            AnimatedVisibility(visible = voiceBoothState.activeSpeakingRole != null) {
                Surface(
                    color = SignalCrimsonDim,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onInterruptClick)
                        .testTag("interrupt_agents_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${voiceBoothState.activeSpeakingRole?.name} speaking — Tap to interrupt & steer",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioTextPrimary
                        )
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Interrupt Speaking",
                            tint = SignalCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Live Mic / Hum Status Pill
            val boothHint = when {
                voiceBoothState.isRecordingHum -> "HUM BOOTH LIVE • Sing or hum melody, tap button to send to Engineer!"
                voiceBoothState.isListeningText && voiceBoothState.partialTranscript.isNotBlank() ->
                    "\"${voiceBoothState.partialTranscript}\""
                !voiceBoothState.statusHint.isNullOrBlank() -> voiceBoothState.statusHint
                else -> null
            }
            AnimatedVisibility(visible = boothHint != null) {
                Surface(
                    color = if (voiceBoothState.isRecordingHum) SignalCrimsonDim else EngineerCyanDim,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = boothHint.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (voiceBoothState.isRecordingHum || voiceBoothState.isListeningText) {
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(StudioObsidian)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(voiceBoothState.humInputLevel.coerceIn(0.12f, 1f))
                                        .height(8.dp)
                                        .background(ProducerMint)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Producer Direction Prompts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickPrompts = listOf(
                    "Flip a 90s soul break + heavy 808",
                    "Write Cudi-inspired cosmic hook",
                    "Switch flow like J. Cole at 140 BPM",
                    "Carve 120Hz HPF & add tape warmth"
                )
                quickPrompts.forEach { preset ->
                    Surface(
                        color = StudioRackSurface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, StudioBorderHairline),
                        modifier = Modifier.clickable(enabled = !isAiGenerating) {
                            onQuickPrompt(preset)
                        }
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Main Studio Composer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voice-to-Text Mic
                Surface(
                    color = if (voiceBoothState.isListeningText) ProducerMint else StudioRackSurface,
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (voiceBoothState.isListeningText) ProducerMint else StudioBorderHairline
                    ),
                    modifier = Modifier
                        .size(46.dp)
                        .clickable(enabled = !isAiGenerating) { onMicSpeechClick() }
                        .testTag("voice_to_text_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Voice to Text",
                            tint = if (voiceBoothState.isListeningText) StudioObsidian else ProducerMint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Hum / Melody Audio Capture
                Surface(
                    color = if (voiceBoothState.isRecordingHum) SignalCrimson else StudioRackSurface,
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (voiceBoothState.isRecordingHum) SignalCrimson else LyricistAmber.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .size(46.dp)
                        .clickable(enabled = !isAiGenerating) { onHumRecordClick() }
                        .testTag("hum_melody_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (voiceBoothState.isRecordingHum) Icons.Rounded.Stop else Icons.Rounded.GraphicEq,
                            contentDescription = "Hum Melody to Beat",
                            tint = if (voiceBoothState.isRecordingHum) StudioTextPrimary else LyricistAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    placeholder = {
                        Text(
                            text = "Direct Engineer & Lyricist...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextMuted
                        )
                    },
                    maxLines = 3,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !isAiGenerating) onSendText()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = StudioRackSurface,
                        unfocusedContainerColor = StudioRackSurface,
                        focusedBorderColor = EngineerCyan,
                        unfocusedBorderColor = StudioBorderHairline,
                        focusedTextColor = StudioTextPrimary,
                        unfocusedTextColor = StudioTextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_prompt_input")
                )

                Surface(
                    color = if (inputText.isNotBlank() && !isAiGenerating) EngineerCyan else StudioRackSurface,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable(enabled = inputText.isNotBlank() && !isAiGenerating) {
                            onSendText()
                        }
                        .testTag("send_prompt_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isAiGenerating) {
                            CircularProgressIndicator(
                                color = EngineerCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send to Studio",
                                tint = if (inputText.isNotBlank()) StudioObsidian else StudioTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
