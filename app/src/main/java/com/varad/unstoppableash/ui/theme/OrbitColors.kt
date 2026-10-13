package com.varad.unstoppableash.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours. `good*` only for genuinely good states (Live, Arrived, Delivered),
 * `warn*` for uncertainty, `sos*` only for emergencies and destructive actions.
 */
@Immutable
data class OrbitColors(
    val background: Color,
    val surface: Color,
    val surfaceWarm: Color,
    val primary: Color,
    val onPrimary: Color,
    val text: Color,
    val textSecondary: Color,
    val blush: Color,
    val lavender: Color,
    val goodBg: Color,
    val goodInk: Color,
    val warnBg: Color,
    val warnInk: Color,
    val sos: Color,
    val onSos: Color,
    val sosText: Color,
    val outline: Color,
    val isDark: Boolean,
)

val LightOrbitColors = with(OrbitPalette.Light) {
    OrbitColors(
        Color(background), Color(surface), Color(surfaceWarm), Color(primary), Color(onPrimary),
        Color(text), Color(textSecondary), Color(blush), Color(lavender), Color(goodBg), Color(goodInk),
        Color(warnBg), Color(warnInk), Color(sos), Color(onSos), Color(sosText), Color(outline), isDark = false,
    )
}

val DarkOrbitColors = with(OrbitPalette.Dark) {
    OrbitColors(
        Color(background), Color(surface), Color(surfaceWarm), Color(primary), Color(onPrimary),
        Color(text), Color(textSecondary), Color(blush), Color(lavender), Color(goodBg), Color(goodInk),
        Color(warnBg), Color(warnInk), Color(sos), Color(onSos), Color(sosText), Color(outline), isDark = true,
    )
}

val LocalOrbitColors = staticCompositionLocalOf { LightOrbitColors }
