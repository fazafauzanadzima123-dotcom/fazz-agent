package com.orbit.agent.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object OrbitColors {
    val Void = Color(0xFF0A0B10)
    val Surface = Color(0xFF12141C)
    val SurfaceHigh = Color(0xFF1A1D29)
    val SurfaceHighest = Color(0xFF232738)
    val Outline = Color(0xFF2C3146)
    val Indigo = Color(0xFF7C8CFF)
    val Teal = Color(0xFF5EEAD4)
    val Orchid = Color(0xFFF0ABFC)
    val TextPrimary = Color(0xFFECEEF6)
    val TextSecondary = Color(0xFF9AA0B8)
    val Danger = Color(0xFFFF6B81)
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
}

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = OrbitColors.Indigo,
    onPrimary = Color(0xFF0B0E2A),
    primaryContainer = Color(0xFF262C5C),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = OrbitColors.Teal,
    onSecondary = Color(0xFF00201C),
    tertiary = OrbitColors.Orchid,
    background = OrbitColors.Void,
    onBackground = OrbitColors.TextPrimary,
    surface = OrbitColors.Surface,
    onSurface = OrbitColors.TextPrimary,
    surfaceVariant = OrbitColors.SurfaceHigh,
    onSurfaceVariant = OrbitColors.TextSecondary,
    surfaceContainer = OrbitColors.SurfaceHigh,
    surfaceContainerHigh = OrbitColors.SurfaceHighest,
    surfaceContainerHighest = OrbitColors.SurfaceHighest,
    outline = OrbitColors.Outline,
    outlineVariant = OrbitColors.Outline,
    error = OrbitColors.Danger,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4652D6),
    secondary = Color(0xFF0F9488),
    tertiary = Color(0xFFA21CAF),
    background = Color(0xFFF7F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECEDF5),
    onSurfaceVariant = Color(0xFF5B6077),
    error = Color(0xFFD6334C),
)

private val Base = Typography()
private val OrbitTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 21.sp),
    labelSmall = Base.labelSmall.copy(letterSpacing = 0.6.sp),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

/** Dark-first: the product is designed dark. A light scheme exists for future opt-in. */
@Composable
fun OrbitTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, typography = OrbitTypography, content = content)
}
