package com.varad.unstoppableash.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.x contrast for every text/background pairing in REDESIGN_SPEC §2. */
class ContrastTest {

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Long, b: Long): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertReadable(name: String, fg: Long, bg: Long) {
        val ratio = contrast(fg, bg)
        assertTrue("$name contrast %.2f < 4.5".format(ratio), ratio >= 4.5)
    }

    @Test fun lightThemePairs() = with(OrbitPalette.Light) {
        assertReadable("text/background", text, background)
        assertReadable("text/surface", text, surface)
        assertReadable("text/surfaceWarm", text, surfaceWarm)
        assertReadable("primary/background", primary, background)
        assertReadable("onPrimary/primary", onPrimary, primary)
        assertReadable("textSecondary/background", textSecondary, background)
        assertReadable("textSecondary/surface", textSecondary, surface)
        assertReadable("text/blush", text, blush)
        assertReadable("text/lavender", text, lavender)
        assertReadable("goodInk/goodBg", goodInk, goodBg)
        assertReadable("warnInk/warnBg", warnInk, warnBg)
        assertReadable("goodInk/surfaceWarm (Recent pill)", goodInk, surfaceWarm)
        assertReadable("onSos/sos", onSos, sos)
        assertReadable("sosText/background", sosText, background)
    }

    @Test fun darkThemePairs() = with(OrbitPalette.Dark) {
        assertReadable("text/background", text, background)
        assertReadable("text/surface", text, surface)
        assertReadable("text/surfaceWarm", text, surfaceWarm)
        assertReadable("primary/background", primary, background)
        assertReadable("onPrimary/primary", onPrimary, primary)
        assertReadable("textSecondary/background", textSecondary, background)
        assertReadable("textSecondary/surface", textSecondary, surface)
        assertReadable("text/blush", text, blush)
        assertReadable("text/lavender", text, lavender)
        assertReadable("goodInk/goodBg", goodInk, goodBg)
        assertReadable("warnInk/warnBg", warnInk, warnBg)
        assertReadable("goodInk/surfaceWarm (Recent pill)", goodInk, surfaceWarm)
        assertReadable("onSos/sos", onSos, sos)
        assertReadable("sosText/background", sosText, background)
    }
}
