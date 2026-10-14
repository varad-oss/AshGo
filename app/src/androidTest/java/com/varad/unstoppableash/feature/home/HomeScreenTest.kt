package com.varad.unstoppableash.feature.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.SosRecordState
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    private var safetyOpened = 0
    private var partnerSosOpened = 0
    private val base = HomeUiState(loading = false, myName = "Alice", partnerName = "Bob")

    private fun show(state: HomeUiState, fontScale: Float = 1f) = rule.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
            OrbitTheme(ThemeMode.LIGHT) { HomeScreen(state, HomeActions(onOpenSafety = { safetyOpened++ }, onOpenPartnerSos = { partnerSosOpened++ })) }
        }
    }

    private fun assertNoLive() = rule.onAllNodes(hasText("live", substring = true, ignoreCase = true)).assertCountEquals(0)

    @Test fun livePartnerShowsLive() {
        show(base.copy(partnerPresence = PresenceState.Live(30_000)))
        rule.onNodeWithText("Live", substring = true).assertExists()
    }

    @Test fun nonLiveStatesNeverSayLive() {
        var presence by mutableStateOf<PresenceState>(PresenceState.Unknown)
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { HomeScreen(base.copy(partnerPresence = presence), HomeActions()) } }
        for (state in listOf(PresenceState.Recent(300_000), PresenceState.Stale(3_600_000), PresenceState.Paused(1L), PresenceState.Offline(1L), PresenceState.NotSharing, PresenceState.Unknown)) {
            presence = state
            rule.waitForIdle()
            assertNoLive()
        }
    }

    @Test fun noLocationMeansNoLocationCard() {
        show(base.copy(partnerPresence = PresenceState.Unknown, partnerLocation = null))
        rule.onAllNodes(hasText("open map", substring = true)).assertCountEquals(0)
    }

    @Test fun locationCardShowsWhenThereIsALocation() {
        show(base.copy(partnerPresence = PresenceState.Live(1000), partnerLocation = LocationFix(1.0, 2.0, null, 1_000L), nowMs = 61_000L))
        rule.onNodeWithText("open map", substring = true).assertExists()
    }

    @Test fun sosRowOpensSafetyAndSendsNothing() {
        show(base)
        rule.onNodeWithContentDescription("SOS. Opens the Safety screen. Nothing is sent from here.").performClick()
        rule.runOnIdle { assertEquals(1, safetyOpened) }
    }

    @Test fun partnerSosBannerAppears() {
        val sos = SosRecord("s1", "bob", SosRecordState.ACTIVE, 1L, null, null, null, null, null, null, null, null, emptyMap(), emptyMap())
        show(base.copy(partnerSos = sos))
        rule.onNodeWithText("Bob needs help · tap to see").assertExists()
    }

    @Test fun offlineBarShows() {
        show(base.copy(connectionProblem = FailureReason.OFFLINE))
        rule.onNodeWithContentDescription("No connection, showing last known information").assertExists()
    }

    @Test fun rendersAtDoubleFontScale() {
        show(base.copy(partnerPresence = PresenceState.Stale(3_600_000), partnerBattery = 9), fontScale = 2f)
        rule.onNodeWithText("Bob").assertExists()
        rule.onNodeWithText("Check in").assertExists()
    }

    private val sos = SosRecord("s1", "bob", SosRecordState.ACTIVE, 1L, null, null, null, null, null, null, null, null, emptyMap(), emptyMap())

    @Test fun partnerSosIsShownEvenWhileLoading() {
        show(base.copy(loading = true, partnerSos = sos))
        rule.onNodeWithText("Bob needs help", substring = true).assertExists()
    }

    @Test fun unpairedLooksLikeLoadingNotAnEmptyHome() {
        show(base.copy(unpaired = true))
        rule.onAllNodes(hasText("Bob", substring = true)).assertCountEquals(0)
        rule.onAllNodes(hasText("Check in")).assertCountEquals(0)
    }

    @Test fun actionOutcomeIsAnnouncedPolitely() {
        show(base.copy(action = ActionStatus.Failed(Action.CHECK_IN, FailureReason.OFFLINE)))
        rule.onNodeWithText("You're offline", substring = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test fun locationAgeUsesTheStateClock() {
        val at = 1_000_000_000L
        show(base.copy(partnerPresence = PresenceState.Paused(at), partnerLocation = LocationFix(1.0, 2.0, null, at), nowMs = at + 5 * 60_000))
        rule.onNodeWithText("Last location 5 min ago", substring = true).assertExists()
    }

    @Test fun thePartnersSosBannerOpensTheirSos() {
        show(base.copy(partnerSos = sos))
        rule.onNodeWithText("Bob needs help", substring = true).performClick()
        rule.runOnIdle { assertEquals(1, partnerSosOpened); assertEquals(0, safetyOpened) }
    }

    @Test fun myActiveSosIsShownOnTheSosRow() {
        show(base.copy(mySosActive = true))
        rule.onNodeWithContentDescription("Your SOS is active. Opens the Safety screen.").performClick()
        rule.runOnIdle { assertEquals(1, safetyOpened) }
    }
}
