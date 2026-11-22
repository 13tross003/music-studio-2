package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val QuantumDarkColorScheme = darkColorScheme(
    primary = EngineerCyan,
    onPrimary = StudioObsidian,
    primaryContainer = EngineerCyanDim,
    onPrimaryContainer = EngineerCyan,
    secondary = LyricistAmber,
    onSecondary = StudioObsidian,
    secondaryContainer = LyricistSurface,
    onSecondaryContainer = LyricistAmber,
    tertiary = ProducerMint,
    onTertiary = StudioObsidian,
    tertiaryContainer = ProducerMintDim,
    onTertiaryContainer = ProducerMint,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioConsoleDark,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioRackSurface,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLow = StudioConsoleDark,
    surfaceContainer = StudioRackSurface,
    surfaceContainerHigh = StudioModuleCard,
    outline = StudioBorderHairline,
    error = SignalCrimson,
    onError = StudioTextPrimary,
    errorContainer = SignalCrimsonDim,
    onErrorContainer = SignalCrimson
)

val QuantumShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun QuantumStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = QuantumDarkColorScheme,
        typography = QuantumTypography,
        shapes = QuantumShapes,
        content = content
    )
}
