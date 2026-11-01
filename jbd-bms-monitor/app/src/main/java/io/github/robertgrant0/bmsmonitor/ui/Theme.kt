package io.github.robertgrant0.bmsmonitor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.robertgrant0.bmsmonitor.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CC8FF),
    onPrimary = Color(0xFF00243A),
    primaryContainer = Color(0xFF0B3A57),
    onPrimaryContainer = Color(0xFFCDE9FF),
    secondary = Color(0xFF8BD3C7),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF232B36),
    onSurfaceVariant = Color(0xFF9DA7B3),
    surfaceContainerLowest = Color(0xFF0A0E13),
    surfaceContainerLow = Color(0xFF131922),
    surfaceContainer = Color(0xFF161C25),
    surfaceContainerHigh = Color(0xFF1C232E),
    surfaceContainerHighest = Color(0xFF232B36),
    outline = Color(0xFF3D4754),
    outlineVariant = Color(0xFF2A323D),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00639B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF00696B),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFF5F7FA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3E8EE),
    onSurfaceVariant = Color(0xFF55606C),
    outline = Color(0xFFB8C1CB),
    outlineVariant = Color(0xFFD5DBE2),
    error = Color(0xFFC62828),
)

/** Colours with a fixed meaning across the dashboard. */
@Immutable
data class StatusColors(
    val charging: Color,
    val discharging: Color,
    val idle: Color,
    val ok: Color,
    val warning: Color,
    val alarm: Color,
    val cellLow: Color,
    val cellHigh: Color,
)

private val DarkStatus = StatusColors(
    charging = Color(0xFF4ADE80),
    discharging = Color(0xFFFFB547),
    idle = Color(0xFF9DA7B3),
    ok = Color(0xFF4ADE80),
    warning = Color(0xFFFFB547),
    alarm = Color(0xFFFF5C5C),
    cellLow = Color(0xFF6CB6FF),
    cellHigh = Color(0xFFFF8A65),
)

private val LightStatus = StatusColors(
    charging = Color(0xFF15803D),
    discharging = Color(0xFFB45309),
    idle = Color(0xFF55606C),
    ok = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    alarm = Color(0xFFC62828),
    cellLow = Color(0xFF1565C0),
    cellHigh = Color(0xFFD84315),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun BmsTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
