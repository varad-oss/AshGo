package com.varad.unstoppableash.feature.safety

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.SosRecordState
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PartnerSosScreenTest {
    @get:Rule val rule = createComposeRule()
    private val now = 1_000_000_000L
    private var acks = 0
    private val maps = mutableListOf<Pair<Double, Double>>()
    private val calls = mutableListOf<String>()
    private fun rec(state: SosRecordState = SosRecordState.ACTIVE, lat: Double? = 18.5, cancelledAt: Long? = null) =
        SosRecord("s1", "bob", state, now - 120_000, lat, lat?.let { 73.8 }, now - 180_000, 12f, 23, 1, 0, cancelledAt, emptyMap(), emptyMap())

    private fun show(state: PartnerSosUiState, phone: String? = "999") = rule.setContent {
        OrbitTheme(ThemeMode.LIGHT) {
            PartnerSosScreen(state, phone, now, PartnerSosActions(onAcknowledge = { acks++ }, onOpenMap = { a, b -> maps += a to b }, onCall = { calls += it }))
        }
    }

    @Test fun showsWhoWhenWhereAndBattery() {
        show(PartnerSosUiState(false, "Bob", rec()))
        rule.onNodeWithText("Bob needs help").assertExists()
        rule.onNodeWithText("Last location 3 min ago · within 12 m").assertExists()
        rule.onNodeWithText("Battery 23%").assertExists()
        rule.onNodeWithText("Open location in maps").performClick()
        rule.runOnIdle { assertEquals(listOf(18.5 to 73.8), maps) }
    }

    @Test fun acknowledgeIsExplicitAndThenConfirmed() {
        show(PartnerSosUiState(false, "Bob", rec()))
        rule.onNodeWithText("Acknowledge").performClick()
        rule.runOnIdle { assertEquals(1, acks) }
    }

    @Test fun afterAcknowledgingItSaysSo() {
        show(PartnerSosUiState(false, "Bob", rec(), acked = true))
        rule.onNodeWithText("You acknowledged · Bob can see it").assertExists()
    }

    @Test fun noLocationIsSaidPlainly() {
        show(PartnerSosUiState(false, "Bob", rec(lat = null)))
        rule.onNodeWithText("Location unavailable").assertExists()
    }

    @Test fun callButtons() {
        show(PartnerSosUiState(false, "Bob", rec()))
        rule.onNodeWithText("Call Bob").performScrollTo().performClick()
        rule.onNodeWithText("Call 112").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("999", "112"), calls) }
    }

    @Test fun cancelledIsCalm() {
        show(PartnerSosUiState(false, "Bob", rec(SosRecordState.CANCELLED, cancelledAt = now - 1_000)))
        rule.onNodeWithText("Bob cancelled the SOS", substring = true).assertExists()
    }
}
