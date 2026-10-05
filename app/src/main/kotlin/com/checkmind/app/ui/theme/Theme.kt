package com.checkmind.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CheckMindColors {
    val AppBackground = Color(0xFF1F2B24)
    val Surface = Color(0xFF17211B)
    val SurfaceRaised = Color(0xFF2F4036)
    val OnSurface = Color(0xFFE8EFE6)
    val OnSurfaceMuted = Color(0xFFA3B0A6)
    val Outline = Color(0xFF62706A)
    val Primary = Color(0xFF8CC152)
    val Danger = Color(0xFFE5604F)
    val BoardLight = Color(0xFFEBECD0)
    val BoardDark = Color(0xFF739552)
    val HighlightYellow = Color(0xFFF6F669)
    val MoveDot = Color(0x24000000)
    val CheckGlow = Color(0xB3FF0000)
}

private val scheme = darkColorScheme(
    primary = CheckMindColors.Primary,
    onPrimary = Color(0xFF14210A),
    secondary = CheckMindColors.Primary,
    onSecondary = Color(0xFF14210A),
    secondaryContainer = CheckMindColors.SurfaceRaised,
    onSecondaryContainer = CheckMindColors.OnSurface,
    error = CheckMindColors.Danger,
    onError = Color(0xFF2B0A06),
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
