package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.robertgrant0.bmsmonitor.data.ThemeMode

/** Every colour in the app. Semantic meaning is fixed: green = charging/OK, sky = discharging,
 *  gold = solar, violet = DC-DC & loads, orange = warning, red = alarm. */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    val card1: Color,
    val card2: Color,
    val warnCard: Color,
    val alarmCard: Color,
    val line: Color,
    val line2: Color,
    val well: Color,
    val highlight: Color,
    val t1: Color,
    val t2: Color,
    val t3: Color,
    val green: Color,
    val greenDeep: Color,
    val sky: Color,
    val gold: Color,
    val violet: Color,
    val orange: Color,
    val orangeDeep: Color,
    val red: Color,
    val redDeep: Color,
    val track: Color,
    val glyphOutline: Color,
    val glyphBody: Color,
    val onAccent: Color,
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF05070B),
    glowA = Color(0xFF34D399).copy(alpha = 0.13f),
    glowB = Color(0xFF38BDF8).copy(alpha = 0.08f),
    glowC = Color(0xFFA78BFA).copy(alpha = 0.07f),
    card1 = Color(0xFF0F141B),
    card2 = Color(0xFF0A0E14),
    warnCard = Color(0xFF16120E),
    alarmCard = Color(0xFF1B0E10),
    line = Color.White.copy(alpha = 0.075f),
    line2 = Color.White.copy(alpha = 0.05f),
    well = Color.White.copy(alpha = 0.03f),
    highlight = Color.White.copy(alpha = 0.12f),
    t1 = Color(0xFFF2F4F7),
    t2 = Color(0xFF98A2B3),
    t3 = Color(0xFF5D6676),
    green = Color(0xFF34D399),
    greenDeep = Color(0xFF059669),
    sky = Color(0xFF38BDF8),
    gold = Color(0xFFFACC15),
    violet = Color(0xFFA78BFA),
    orange = Color(0xFFFB923C),
    orangeDeep = Color(0xFFC2410C),
    red = Color(0xFFF87171),
    redDeep = Color(0xFFB91C1C),
    track = Color.White.copy(alpha = 0.06f),
    glyphOutline = Color.White.copy(alpha = 0.16f),
    glyphBody = Color.White.copy(alpha = 0.03f),
    onAccent = Color(0xFF04261A),
)

private val Ink = Color(0xFF0F172A)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF3F5F8),
    glowA = Color(0xFF34D399).copy(alpha = 0.12f),
    glowB = Color(0xFF38BDF8).copy(alpha = 0.10f),
    glowC = Color(0xFFA78BFA).copy(alpha = 0.08f),
    card1 = Color(0xFFFFFFFF),
    card2 = Color(0xFFFAFBFC),
    warnCard = Color(0xFFFFF5EB),
    alarmCard = Color(0xFFFFEFEF),
    line = Ink.copy(alpha = 0.09f),
    line2 = Ink.copy(alpha = 0.06f),
    well = Ink.copy(alpha = 0.035f),
    highlight = Color.White.copy(alpha = 0f),
    t1 = Color(0xFF0B1220),
    t2 = Color(0xFF556070),
    t3 = Color(0xFF8A94A3),
    green = Color(0xFF059669),
    greenDeep = Color(0xFF047857),
    sky = Color(0xFF0284C7),
    gold = Color(0xFFCA8A04),
    violet = Color(0xFF7C3AED),
    orange = Color(0xFFEA580C),
    orangeDeep = Color(0xFFC2410C),
    red = Color(0xFFDC2626),
    redDeep = Color(0xFF991B1B),
    track = Ink.copy(alpha = 0.07f),
    glyphOutline = Ink.copy(alpha = 0.18f),
    glyphBody = Ink.copy(alpha = 0.03f),
    onAccent = Color.White,
)

/** Fonts are supplied by the platform (Android resources, or files for desktop previews). */
@Immutable
data class AppFonts(val text: FontFamily, val display: FontFamily)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
val LocalAppFonts = staticCompositionLocalOf { AppFonts(FontFamily.Default, FontFamily.Default) }

/** False when the user switched animations off (saves power on slower tablets). */
val LocalMotion = staticCompositionLocalOf { true }

object Type {
    /** Big tabular numbers ("82", "+249"). */
    @Composable
    @ReadOnlyComposable
    fun number(size: TextUnit, weight: FontWeight = FontWeight.SemiBold): TextStyle = TextStyle(
        fontFamily = LocalAppFonts.current.display,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size * 1.08f,
        letterSpacing = (-0.02).em,
        fontFeatureSettings = "tnum",
    )

    /** Regular text that contains figures (so digits keep the same width while they update). */
    @Composable
    @ReadOnlyComposable
    fun figures(size: TextUnit, weight: FontWeight = FontWeight.SemiBold): TextStyle = TextStyle(
        fontFamily = LocalAppFonts.current.text,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size * 1.3f,
        fontFeatureSettings = "tnum",
    )

    @Composable
    @ReadOnlyComposable
    fun text(size: TextUnit, weight: FontWeight = FontWeight.Normal): TextStyle = TextStyle(
        fontFamily = LocalAppFonts.current.text,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size * 1.35f,
    )

    /** Small uppercase section label ("CELLS", "ENERGY FLOW"). */
    @Composable
    @ReadOnlyComposable
    fun label(): TextStyle = TextStyle(
        fontFamily = LocalAppFonts.current.text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.09.em,
    )

    @Composable
    @ReadOnlyComposable
    fun title(size: TextUnit = 30.sp): TextStyle = TextStyle(
        fontFamily = LocalAppFonts.current.display,
        fontWeight = FontWeight.SemiBold,
        fontSize = size,
        lineHeight = size * 1.15f,
        letterSpacing = (-0.02).em,
    )
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

private fun typographyFor(text: FontFamily): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = text),
        displayMedium = t.displayMedium.copy(fontFamily = text),
        displaySmall = t.displaySmall.copy(fontFamily = text),
        headlineLarge = t.headlineLarge.copy(fontFamily = text),
        headlineMedium = t.headlineMedium.copy(fontFamily = text),
        headlineSmall = t.headlineSmall.copy(fontFamily = text),
        titleLarge = t.titleLarge.copy(fontFamily = text),
        titleMedium = t.titleMedium.copy(fontFamily = text),
        titleSmall = t.titleSmall.copy(fontFamily = text),
        bodyLarge = t.bodyLarge.copy(fontFamily = text),
        bodyMedium = t.bodyMedium.copy(fontFamily = text),
        bodySmall = t.bodySmall.copy(fontFamily = text),
        labelLarge = t.labelLarge.copy(fontFamily = text),
        labelMedium = t.labelMedium.copy(fontFamily = text),
        labelSmall = t.labelSmall.copy(fontFamily = text),
    )
}

@Composable
fun BmsTheme(dark: Boolean, fonts: AppFonts, animations: Boolean = true, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.green,
            onPrimary = p.onAccent,
            secondary = p.sky,
            background = p.bg,
            onBackground = p.t1,
            surface = p.card1,
            onSurface = p.t1,
            surfaceVariant = Color(0xFF1A212B),
            onSurfaceVariant = p.t2,
            surfaceContainerLowest = p.bg,
            surfaceContainerLow = p.card2,
            surfaceContainer = p.card1,
            surfaceContainerHigh = Color(0xFF141A22),
            surfaceContainerHighest = Color(0xFF1A212B),
            outline = Color(0xFF2E3744),
            outlineVariant = Color(0xFF1E252F),
            error = p.red,
        )
    } else {
        lightColorScheme(
            primary = p.green,
            onPrimary = p.onAccent,
            secondary = p.sky,
            background = p.bg,
            onBackground = p.t1,
            surface = p.card1,
            onSurface = p.t1,
            surfaceVariant = Color(0xFFE6EAF0),
            onSurfaceVariant = p.t2,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = p.card2,
            surfaceContainer = p.card1,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color(0xFFEEF1F5),
            outline = Color(0xFFC4CBD5),
            outlineVariant = Color(0xFFDDE2E8),
            error = p.red,
        )
    }
    CompositionLocalProvider(
        LocalPalette provides p,
        LocalAppFonts provides fonts,
        LocalMotion provides animations,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typographyFor(fonts.text), content = content)
    }
}
