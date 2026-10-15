package com.varad.unstoppableash.ui.components

import com.varad.unstoppableash.domain.model.PresenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PresenceToneTest {
    @Test fun onlyLiveIsGood() = assertEquals(PresenceTone.GOOD, presenceTone(PresenceState.Live(0)))
    @Test fun recentIsNotPaintedGood() {
        assertEquals(PresenceTone.GOOD_OUTLINE, presenceTone(PresenceState.Recent(14 * 60_000L)))
        assertNotEquals(PresenceTone.GOOD, presenceTone(PresenceState.Recent(14 * 60_000L)))
    }
    @Test fun staleAndOfflineWarn() {
        assertEquals(PresenceTone.WARN, presenceTone(PresenceState.Stale(1)))
        assertEquals(PresenceTone.WARN, presenceTone(PresenceState.Offline(1)))
    }
    @Test fun pausedIsPaused() = assertEquals(PresenceTone.PAUSED, presenceTone(PresenceState.Paused(null)))
    @Test fun notSharingIsDashedNeutral() = assertEquals(PresenceTone.NEUTRAL_DASHED, presenceTone(PresenceState.NotSharing))
    @Test fun unknownIsNeutral() = assertEquals(PresenceTone.NEUTRAL, presenceTone(PresenceState.Unknown))
}
