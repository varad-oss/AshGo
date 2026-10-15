package com.varad.unstoppableash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pins REDESIGN_SPEC §3: Nunito everywhere, exact sizes / line heights / weights. */
class OrbitTypeTest {
    @get:Rule val rule = createComposeRule()

    private fun assertStyle(name: String, s: TextStyle, size: TextUnit, line: TextUnit, weight: Int) {
        assertEquals("$name family", Nunito, s.fontFamily)
        assertEquals("$name size", size, s.fontSize)
        assertEquals("$name line height", line, s.lineHeight)
        assertEquals("$name weight", FontWeight(weight), s.fontWeight)
    }

    @Test fun themeUsesOrbitTypeScale() {
        lateinit var t: Typography
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { t = MaterialTheme.typography } }
        rule.runOnIdle {
            assertStyle("displaySmall", t.displaySmall, 32.sp, 40.sp, 800)
            assertStyle("headlineSmall", t.headlineSmall, 26.sp, 32.sp, 700)
            assertStyle("titleLarge", t.titleLarge, 22.sp, 28.sp, 700)
            assertStyle("titleMedium", t.titleMedium, 18.sp, 24.sp, 700)
            assertStyle("bodyLarge", t.bodyLarge, 16.sp, 24.sp, 400)
            assertStyle("bodyMedium", t.bodyMedium, 14.sp, 20.sp, 400)
            assertStyle("labelLarge", t.labelLarge, 14.sp, 20.sp, 600)
            assertStyle("bodySmall", t.bodySmall, 12.sp, 16.sp, 400)
            assertStyle("labelSmall", t.labelSmall, 12.sp, 16.sp, 400)
        }
    }

    @Test fun emergencyStyle() = assertStyle("SosTextStyle", SosTextStyle, 28.sp, 34.sp, 800)

    @Test fun noStyleBelowTwelveSp() {
        lateinit var t: Typography
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { t = MaterialTheme.typography } }
        rule.runOnIdle {
            listOf(
                t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
                t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
                t.labelLarge, t.labelMedium, t.labelSmall,
            ).forEach { assertEquals("family", Nunito, it.fontFamily); assert(it.fontSize.value >= 12f) { "below 12 sp: $it" } }
        }
    }
}
