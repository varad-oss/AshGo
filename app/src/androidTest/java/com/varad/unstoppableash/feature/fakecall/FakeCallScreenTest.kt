package com.varad.unstoppableash.feature.fakecall

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FakeCallScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun incomingCallShowsTheCallerAndBothActions() {
        val log = mutableListOf<String>()
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                FakeCallScreen(FakeCallUiState(caller = "Mum", phase = CallPhase.Ringing), FakeCallActions(onAccept = { log += "accept" }, onDecline = { log += "decline" }), uptime = { 0L })
            }
        }
        rule.onNodeWithText("Mum").assertExists()
        rule.onNodeWithText("Incoming call").assertExists()
        rule.onNodeWithText("AshGo", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Decline").performClick()
        rule.onNodeWithText("Accept").performClick()
        rule.runOnIdle { assertEquals(listOf("decline", "accept"), log) }
    }

    @Test fun setupOffersTheBuiltInVoiceAndStarts() {
        var started = 0
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { FakeCallScreen(FakeCallUiState(), FakeCallActions(onStart = { started++ }), uptime = { 0L }) } }
        rule.onNodeWithText("Built-in recording").assertExists()
        rule.onNodeWithText("Start fake call").performClick()
        rule.runOnIdle { assertEquals(1, started) }
    }
}
