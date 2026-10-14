package com.varad.unstoppableash.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.varad.unstoppableash.domain.location.PauseChoice
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.Trip
import com.varad.unstoppableash.domain.model.TripDestination
import com.varad.unstoppableash.domain.model.TripState
import com.varad.unstoppableash.domain.trips.OverduePhase
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The map widget is replaced by an empty box: these tests never load OpenStreetMap tiles. */
class MapScreenTest {
    @get:Rule val rule = createComposeRule()
    private val now = 1_000_000_000L
    private var pins: MapPins? = null
    private val pauses = mutableListOf<PauseChoice>()
    private var shared = 0; private var help = 0
    private var started: List<Any?>? = null
    private val base = MapUiState(loading = false, partnerName = "Bob", nowMs = now)

    private fun show(state: MapUiState) = rule.setContent {
        OrbitTheme(ThemeMode.LIGHT) {
            MapScreen(
                state,
                MapActions(onShareLive = { shared++ }, onPause = { pauses += it }, onGetHelp = { help++ }, onStartTrip = { d, e, n, t -> started = listOf(d, e, n, t) }),
                map = { p, _, _, m -> pins = p; Box(m) },
            )
        }
    }

    @Test fun attributionIsAlwaysOnScreen() {
        show(base)
        rule.onNodeWithText("© OpenStreetMap contributors").assertExists()
    }

    @Test fun noLocationNoPinAndNoCentreButton() {
        show(base)
        rule.onNodeWithText("Show Bob").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(null, pins?.partner) }
    }

    @Test fun aStalePinIsDrawnFaded() {
        show(base.copy(partnerFix = LocationFix(1.0, 2.0, 5f, now - 3_600_000), partnerFaded = true))
        rule.runOnIdle { assertEquals(true, pins?.partnerFaded) }
    }

    @Test fun shareAndPauseControls() {
        show(base.copy(mySharing = SharingMode.OFF))
        rule.onNodeWithText("Share live").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, shared) }
    }

    @Test fun pauseChoicesWhileLive() {
        show(base.copy(mySharing = SharingMode.LIVE))
        rule.onNodeWithText("Pause 1 h").performScrollTo().performClick()
        rule.onNodeWithText("Until morning").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(PauseChoice.ONE_HOUR, PauseChoice.UNTIL_MORNING), pauses) }
    }

    @Test fun startingATripTellsThePartnerByDefault() {
        show(base)
        rule.onNodeWithText("Start a trip").performScrollTo().performClick()
        rule.onNodeWithText("Start").performClick()
        rule.runOnIdle { assertEquals(listOf(null, now + 3_600_000, null, true), started) }
    }

    @Test fun myOverdueTripAsksFirst() {
        val trip = Trip(TripState.ACTIVE, TripDestination(1.0, 2.0, null), now - 120_000, null, null, true, now - 3_600_000, null)
        show(base.copy(myTrip = trip, myTripPhase = OverduePhase.ASK_TRAVELLER))
        rule.onNodeWithText("Are you okay? Your trip is past its time.").assertExists()
        rule.onNodeWithText("Get help").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, help) }
    }

    @Test fun partnerOverdueIsShownWithHowLong() {
        val trip = Trip(TripState.ACTIVE, null, now - 25 * 60_000, null, null, true, now - 3_600_000, null)
        show(base.copy(partnerTrip = trip, partnerTripPhase = OverduePhase.ALERT_PARTNER))
        rule.onNodeWithText("Bob's trip is 25 min overdue").assertExists()
    }
}
