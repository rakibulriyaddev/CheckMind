package com.checkmind.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CheckMindColors {
    val AppBackground = Color(0xFFF4F3EE)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFE6E5DE)
    val OnSurface = Color(0xFF262522)
    val OnSurfaceMuted = Color(0xFF6B6963)
    val Outline = Color(0xFFB5B3AB)
    val Primary = Color(0xFF4E7D2A)
    val Danger = Color(0xFFC0392B)
    val BoardLight = Color(0xFFEBECD0)
    val BoardDark = Color(0xFF739552)
    val HighlightYellow = Color(0xFFF6F669)
    val MoveDot = Color(0x24000000)
    val CheckGlow = Color(0xB3FF0000)
}

private val scheme = lightColorScheme(
    primary = CheckMindColors.Primary,
    onPrimary = Color.White,
    secondary = CheckMindColors.Primary,
    onSecondary = Color.White,
    secondaryContainer = CheckMindColors.SurfaceRaised,
    onSecondaryContainer = CheckMindColors.OnSurface,
    error = CheckMindColors.Danger,
    onError = Color.White,
    background = CheckMindColors.AppBackground,
    onBackground = CheckMindColors.OnSurface,
    surface = CheckMindColors.Surface,
    onSurface = CheckMindColors.OnSurface,
    surfaceVariant = CheckMindColors.SurfaceRaised,
    onSurfaceVariant = CheckMindColors.OnSurfaceMuted,
    outline = CheckMindColors.Outline,
    outlineVariant = CheckMindColors.SurfaceRaised,
    // Dialogs and tonal surfaces read these; the Material defaults are purple-tinted.
    surfaceContainerLowest = CheckMindColors.AppBackground,
    surfaceContainerLow = CheckMindColors.Surface,
    surfaceContainer = CheckMindColors.Surface,
    surfaceContainerHigh = CheckMindColors.SurfaceRaised,
    surfaceContainerHighest = CheckMindColors.SurfaceRaised,
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun CheckMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = CheckMindTypography, shapes = shapes, content = content)
}
