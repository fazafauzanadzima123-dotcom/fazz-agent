package com.fazz.agent.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fazz.agent.R

/** Semantic colors shared by light (cream) and dark (warm night) schemes. */
object FazzColors {
    // Cream palette
    val Cream = Color(0xFFF3EEE3)
    val Paper = Color(0xFFFBF8F1)
    val Sand = Color(0xFFEDE5D6)
    val SandDeep = Color(0xFFE4D9C6)
    val Line = Color(0xFFDDD4C4)
    val Ink = Color(0xFF1E1C19)
    val InkSoft = Color(0xFF6F675C)
    /** Batik indigo (nila): the single accent. */
    val Nila = Color(0xFF2E4A7D)
    val NilaMuda = Color(0xFFDCE3F0)
    val Sage = Color(0xFF5F7D5A)

    // Warm night palette
    val Night = Color(0xFF171513)
    val NightSurface = Color(0xFF1F1C19)
    val NightRaised = Color(0xFF2A2622)
    val NightLine = Color(0xFF3A352F)
    val Parchment = Color(0xFFEFE8DC)

    // Status
    val Success = Color(0xFF3F7D4E)
    val Danger = Color(0xFFB3261E)
    val Warning = Color(0xFFB7791F)
}

private val LightScheme: ColorScheme = lightColorScheme(
    primary = FazzColors.Ink,
    onPrimary = FazzColors.Paper,
    primaryContainer = FazzColors.SandDeep,
    onPrimaryContainer = FazzColors.Ink,
    secondary = FazzColors.Nila,
    onSecondary = Color.White,
    secondaryContainer = FazzColors.NilaMuda,
    onSecondaryContainer = Color(0xFF14264A),
    tertiary = FazzColors.Sage,
    background = FazzColors.Cream,
    onBackground = FazzColors.Ink,
    surface = FazzColors.Paper,
    onSurface = FazzColors.Ink,
    surfaceVariant = FazzColors.Sand,
    onSurfaceVariant = FazzColors.InkSoft,
    surfaceContainer = FazzColors.Sand,
    surfaceContainerHigh = FazzColors.SandDeep,
    surfaceContainerHighest = FazzColors.SandDeep,
    surfaceContainerLow = FazzColors.Paper,
    outline = FazzColors.Line,
    outlineVariant = FazzColors.Line,
    error = FazzColors.Danger,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = FazzColors.Parchment,
    onPrimary = FazzColors.Night,
    primaryContainer = FazzColors.NightRaised,
    onPrimaryContainer = FazzColors.Parchment,
    secondary = Color(0xFF9DB4E0),
    onSecondary = Color(0xFF10203F),
    secondaryContainer = Color(0xFF26385C),
    onSecondaryContainer = Color(0xFFDCE3F0),
    tertiary = Color(0xFF9DB597),
    background = FazzColors.Night,
    onBackground = FazzColors.Parchment,
    surface = FazzColors.NightSurface,
    onSurface = FazzColors.Parchment,
    surfaceVariant = FazzColors.NightRaised,
    onSurfaceVariant = Color(0xFFA79C8E),
    surfaceContainer = FazzColors.NightRaised,
    surfaceContainerHigh = FazzColors.NightRaised,
    surfaceContainerHighest = FazzColors.NightRaised,
    surfaceContainerLow = FazzColors.NightSurface,
    outline = FazzColors.NightLine,
    outlineVariant = FazzColors.NightLine,
    error = Color(0xFFF2B8B5),
)

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Plus Jakarta Sans — designed by Tokotype, an Indonesian type foundry. */
val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700))

/** Instrument Serif for editorial display text. */
val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

private val Base = Typography()
private fun TextStyle.j(w: FontWeight = FontWeight.Normal) = copy(fontFamily = Jakarta, fontWeight = w)

private val FazzTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Serif, letterSpacing = (-0.5).sp),
    displayMedium = Base.displayMedium.copy(fontFamily = Serif, letterSpacing = (-0.4).sp),
    displaySmall = Base.displaySmall.copy(fontFamily = Serif, fontSize = 40.sp, lineHeight = 44.sp),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Serif),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Serif, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = Base.headlineSmall.j(FontWeight.SemiBold),
    titleLarge = Base.titleLarge.j(FontWeight.SemiBold).copy(fontSize = 20.sp),
    titleMedium = Base.titleMedium.j(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.j(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.j().copy(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = Base.bodyMedium.j().copy(lineHeight = 21.sp),
    bodySmall = Base.bodySmall.j().copy(lineHeight = 18.sp),
    labelLarge = Base.labelLarge.j(FontWeight.Medium),
    labelMedium = Base.labelMedium.j(FontWeight.Medium),
    labelSmall = Base.labelSmall.j(FontWeight.Medium).copy(letterSpacing = 0.8.sp),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

/** Cream light by default; follows the phone's dark mode automatically. */
@Composable
fun FazzTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, typography = FazzTypography, content = content)
}

/** Accent used for the agent's "presence" (orb core, active states). */
val accent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
