package com.varad.unstoppableash.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.time.Durations.HOUR
import com.varad.unstoppableash.domain.time.Durations.MINUTE
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test

class StatusPillTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: PresenceState) = rule.setContent {
        OrbitTheme(ThemeMode.LIGHT) { StatusPill(name = "Sam", state = state) }
    }

    private fun assertNeverClaimsLive() {
        rule.onAllNodes(hasText("live", substring = true, ignoreCase = true)).assertCountEquals(0)
        rule.onAllNodes(hasContentDescription("live", substring = true, ignoreCase = true)).assertCountEquals(0)
    }

    @Test fun statusPill_liveShowsSentence() {
        show(PresenceState.Live(60_000))
        rule.onNodeWithContentDescription("Sam: live, 1 minute ago").assertExists()
    }

    @Test fun statusPill_staleIsNotLive() {
        show(PresenceState.Stale(40 * MINUTE))
        rule.onNodeWithText("Last update 40 min ago").assertExists()
        rule.onNodeWithContentDescription("Sam: stale, last update 40 minutes ago").assertExists()
        assertNeverClaimsLive()
    }

    @Test fun statusPill_pausedShowsLastSeen() {
        show(PresenceState.Paused(lastLocationAt = 1_000_000_000_000L))
        rule.onAllNodesWithText("Paused", substring = true).assertCountEquals(1)
        rule.onAllNodesWithText("last seen", substring = true).assertCountEquals(1)
        assertNeverClaimsLive()
    }

    @Test fun statusPill_offlineShowsSince() {
        show(PresenceState.Offline(since = 1_000_000_000_000L))
        rule.onAllNodesWithText("Offline since", substring = true).assertCountEquals(1)
        assertNeverClaimsLive()
    }

    @Test fun statusPill_unknownNeverLive() {
        show(PresenceState.Unknown)
        assertNeverClaimsLive()
    }

    @Test fun statusPill_notSharingNeverLive() {
        show(PresenceState.NotSharing)
        rule.onNodeWithText("Not sharing location").assertExists()
        assertNeverClaimsLive()
    }

    @Test fun statusPill_recentShowsAgeOnly() {
        show(PresenceState.Recent(4 * MINUTE))
        rule.onNodeWithText("4 min ago").assertExists()
        assertNeverClaimsLive()
    }

    @Test fun statusPill_hoursUseHourUnit() {
        show(PresenceState.Stale(3 * HOUR))
        rule.onNodeWithText("Last update 3 h ago").assertExists()
    }
}
