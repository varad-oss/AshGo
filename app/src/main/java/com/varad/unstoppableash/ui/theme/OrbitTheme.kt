package com.varad.unstoppableash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun OrbitTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkOrbitColors else LightOrbitColors
    CompositionLocalProvider(LocalOrbitColors provides colors) {
        MaterialTheme(colorScheme = colors.toMaterial(), shapes = OrbitShapes, content = content)
    }
}

/** Accessor mirroring MaterialTheme.colorScheme: `Orbit.colors.primary`. */
object Orbit {
    val colors: OrbitColors
        @Composable get() = LocalOrbitColors.current
}

private fun OrbitColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceWarm,
        onSurfaceVariant = textSecondary,
        secondaryContainer = blush,
        onSecondaryContainer = text,
        tertiaryContainer = lavender,
        onTertiaryContainer = text,
        error = sos,
        onError = onSos,
        outline = outline,
        outlineVariant = outline,
    )
}
