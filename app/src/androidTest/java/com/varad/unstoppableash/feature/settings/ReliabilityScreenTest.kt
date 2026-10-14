package com.varad.unstoppableash.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReliabilityScreenTest {
    @get:Rule val rule = createComposeRule()
    private val fixes = mutableListOf<Fix>()
    private var tests = 0
    private val good = ReliabilityChecks(true, true, true, true, true, true, true)

    private fun show(c: ReliabilityChecks) = rule.setContent {
        OrbitTheme(ThemeMode.LIGHT) { ReliabilityScreen(reliabilityItems(c), ReliabilityActions(onFix = { fixes += it }, onTest = { tests++ })) }
    }

    @Test fun allGoodShowsNoFixButtons() {
        show(good)
        rule.onAllNodesWithText("Fix").assertCountEquals(0)
        rule.onNodeWithContentDescription("SOS alerts switched on: OK").assertExists()
    }

    @Test fun aProblemOffersItsFix() {
        show(good.copy(sosChannelOn = false))
        rule.onNodeWithContentDescription("SOS alerts switched on: needs attention").assertExists()
        rule.onNodeWithText("Fix").performClick()
        rule.runOnIdle { assertEquals(listOf(Fix.SOS_CHANNEL_SETTINGS), fixes) }
    }

    @Test fun testNotification() {
        show(good)
        rule.onNodeWithText("Send a test notification").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, tests) }
    }
}
