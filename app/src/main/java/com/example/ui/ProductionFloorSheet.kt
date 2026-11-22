package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioTelemetry
import com.example.audio.StemMixState
import com.example.data.model.StemType
import com.example.data.model.StudioSession
import com.example.ui.theme.EngineerCyan
import com.example.ui.theme.GrammyGold
import com.example.ui.theme.LyricistAmber
import com.example.ui.theme.LyricistViolet
import com.example.ui.theme.ProducerMint
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.StudioBorderHairline
import com.example.ui.theme.StudioConsoleDark
import com.example.ui.theme.StudioModuleCard
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioRackSurface
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlin.math.roundToInt

@Composable
fun ProductionFloorPanel(
    session: StudioSession,
    telemetry: AudioTelemetry,
    mixState: StemMixState,
    isExportingWav: Boolean,
    onTogglePlay: () -> Unit,
    onToggleMute: (StemType) -> Unit,
    onToggleSolo: (StemType) -> Unit,
    onToggleStep: (String, Int) -> Unit,
    onUpdateMix: ((StudioSession) -> StudioSession) -> Unit,
    onExportWav: (StemType, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Transport
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "SOVEREIGN PRODUCTION FLOOR",
                    style = MaterialTheme.typography.titleMedium,
                    color = EngineerCyan
                )
                Text(
                    text = "${session.title} • ${session.bpm} BPM • ${session.musicalKey}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextSecondary
                )
            }

            Surface(
                color = if (telemetry.isPlaying) EngineerCyan else StudioModuleCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EngineerCyan),
                modifier = Modifier
                    .clickable(onClick = onTogglePlay)
                    .testTag("floor_play_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (telemetry.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Transport",
                        tint = if (telemetry.isPlaying) StudioObsidian else EngineerCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (telemetry.isPlaying) "STOP" else "PLAY",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (telemetry.isPlaying) StudioObsidian else EngineerCyan
                    )
                }
            }
        }

        // Live Holographic Spectrum Analyzer
        Surface(
            color = StudioObsidian,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, StudioBorderHairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                HolographicSpectrumCanvas(
                    bands = telemetry.bands,
                    isPlaying = telemetry.isPlaying,
                    activeStep = telemetry.currentStep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
            }
        }

        // 4-Stem Mixer & WAV Export Strip
        Text(
            text = "MULTI-STEM BUS & WAV EXPORT",
            style = MaterialTheme.typography.labelMedium,
            color = GrammyGold
        )

        val stems = listOf(
            Triple(StemType.DRUMS, ProducerMint, mixState.drumsMuted),
            Triple(StemType.SUB_BASS, EngineerCyan, mixState.bassMuted),
            Triple(StemType.MELODY, LyricistAmber, mixState.melodyMuted),
            Triple(StemType.VOCAL_GUIDE, LyricistViolet, mixState.vocalMuted)
        )

        stems.forEach { (stem, accent, isMuted) ->
            val isSolo = mixState.soloStem == stem
            Surface(
                color = StudioModuleCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSolo) accent else StudioBorderHairline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) StudioTextMuted else accent)
                        )
                        Text(
                            text = stem.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isMuted) StudioTextMuted else StudioTextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmallRackChip(
                            label = "M",
                            active = isMuted,
                            activeColor = SignalCrimson,
                            onClick = { onToggleMute(stem) }
                        )
                        SmallRackChip(
                            label = "S",
                            active = isSolo,
                            activeColor = accent,
                            onClick = { onToggleSolo(stem) }
                        )
                        SmallRackIconChip(
                            icon = Icons.Rounded.Download,
                            contentDescription = "Save ${stem.label} WAV",
                            tint = EngineerCyan,
                            enabled = !isExportingWav,
                            onClick = { onExportWav(stem, false) }
                        )
                        SmallRackIconChip(
                            icon = Icons.Rounded.Share,
                            contentDescription = "Share ${stem.label} WAV",
                            tint = GrammyGold,
                            enabled = !isExportingWav,
                            onClick = { onExportWav(stem, true) }
                        )
                    }
                }
            }
        }

        // 16-Step Sequencer Pads
        Text(
            text = "16-STEP SEQUENCER",
            style = MaterialTheme.typography.labelMedium,
            color = ProducerMint
        )
        Surface(
            color = StudioRackSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, StudioBorderHairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SequencerRow(
                    label = "KICK",
                    steps = session.kickPattern,
                    activeStep = if (telemetry.isPlaying) telemetry.currentStep else -1,
                    accent = ProducerMint,
                    onToggle = { idx -> onToggleStep("KICK", idx) }
                )
                SequencerRow(
                    label = "SNARE",
                    steps = session.snarePattern,
                    activeStep = if (telemetry.isPlaying) telemetry.currentStep else -1,
                    accent = EngineerCyan,
                    onToggle = { idx -> onToggleStep("SNARE", idx) }
                )
                SequencerRow(
                    label = "HAT",
                    steps = session.hihatPattern,
                    activeStep = if (telemetry.isPlaying) telemetry.currentStep else -1,
                    accent = LyricistAmber,
                    onToggle = { idx -> onToggleStep("HAT", idx) }
                )
            }
        }

        // Sound Engineer Real-Time DSP Chain
        Text(
            text = "SOUND ENGINEER DSP CHAIN",
            style = MaterialTheme.typography.labelMedium,
            color = EngineerCyan
        )
        Surface(
            color = StudioRackSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, StudioBorderHairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DspSliderRow(
                    label = "TEMPO",
                    valueText = "${session.bpm} BPM",
                    value = session.bpm.toFloat(),
                    range = 55f..190f,
                    accent = GrammyGold,
                    onValueChange = { v -> onUpdateMix { it.copy(bpm = v.roundToInt().toLong()) } }
                )
                DspSliderRow(
                    label = "SURGICAL HPF",
                    valueText = "${session.hpfHz} Hz",
                    value = session.hpfHz.toFloat(),
                    range = 20f..320f,
                    accent = EngineerCyan,
                    onValueChange = { v -> onUpdateMix { it.copy(hpfHz = v.roundToInt().toLong()) } }
                )
                DspSliderRow(
                    label = "SUB-BASS SHELF",
                    valueText = "+${String.format("%.1f", session.subBoostDb)} dB",
                    value = session.subBoostDb.toFloat(),
                    range = 0f..12f,
                    accent = ProducerMint,
                    onValueChange = { v -> onUpdateMix { it.copy(subBoostDb = v.toDouble()) } }
                )
                DspSliderRow(
                    label = "AIR & PRESENCE",
                    valueText = "${if (session.airPresenceDb >= 0) "+" else ""}${String.format("%.1f", session.airPresenceDb)} dB",
                    value = session.airPresenceDb.toFloat(),
                    range = -6f..9f,
                    accent = LyricistAmber,
                    onValueChange = { v -> onUpdateMix { it.copy(airPresenceDb = v.toDouble()) } }
                )
                DspSliderRow(
                    label = "ANALOG DRIVE",
                    valueText = "${(session.saturation * 100).roundToInt()}%",
                    value = session.saturation.toFloat(),
                    range = 0f..1f,
                    accent = LyricistViolet,
                    onValueChange = { v -> onUpdateMix { it.copy(saturation = v.toDouble()) } }
                )
                DspSliderRow(
                    label = "BUS COMPRESSOR",
                    valueText = "${(session.compression * 100).roundToInt()}%",
                    value = session.compression.toFloat(),
                    range = 0f..1f,
                    accent = EngineerCyan,
                    onValueChange = { v -> onUpdateMix { it.copy(compression = v.toDouble()) } }
                )
                DspSliderRow(
                    label = "GROOVE SWING",
                    valueText = "${(session.swing * 100).roundToInt()}%",
                    value = session.swing.toFloat(),
                    range = 0f..0.38f,
                    accent = GrammyGold,
                    onValueChange = { v -> onUpdateMix { it.copy(swing = v.toDouble()) } }
                )
            }
        }
    }
}

@Composable
private fun SmallRackChip(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) activeColor else StudioConsoleDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (active) activeColor else StudioBorderHairline),
        modifier = Modifier
            .size(34.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) StudioObsidian else StudioTextSecondary
            )
        }
    }
}

@Composable
private fun SmallRackIconChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = StudioConsoleDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, StudioBorderHairline),
        modifier = Modifier
            .size(34.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else StudioTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SequencerRow(
    label: String,
    steps: List<Long>,
    activeStep: Int,
    accent: Color,
    onToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 0 until 16) {
                val triggered = steps.getOrElse(i) { 0L } > 0L
                val isCurrent = i == activeStep
                val bg = when {
                    isCurrent && triggered -> GrammyGold
                    isCurrent -> StudioBorderHairline
                    triggered -> accent
                    i % 4 == 0 -> StudioModuleCard
                    else -> StudioObsidian
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(bg)
                        .clickable { onToggle(i) }
                )
            }
        }
    }
}

@Composable
private fun DspSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = StudioObsidian
            )
        )
    }
}
