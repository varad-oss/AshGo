package com.varad.unstoppableash.domain.presence

import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.time.Durations.HOUR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceCalculatorTest {
    private val n = 1_000_000_000L

    private fun snap(
        online: Boolean? = null, lastSeen: Long? = null, sharing: SharingMode? = null, locationAt: Long? = null,
    ) = PresenceSnapshot(
        online = online, lastSeen = lastSeen, heartbeatAt = null, battery = null, charging = null,
        sharing = sharing, locationAt = locationAt, accuracyM = null,
    )

    private fun compute(s: PresenceSnapshot?) = PresenceCalculator.compute(s, n)

    @Test fun r01_nullSnapshotIsUnknown() = assertEquals(PresenceState.Unknown, compute(null))
    @Test fun r02_allFieldsNullIsUnknown() = assertEquals(PresenceState.Unknown, compute(snap()))
    @Test fun r03_pausedKeepsLastLocationTime() =
        assertEquals(PresenceState.Paused(n - 3 * HOUR), compute(snap(sharing = SharingMode.PAUSED, locationAt = n - 3 * HOUR)))
    @Test fun r04_pausedWithoutLocation() =
        assertEquals(PresenceState.Paused(null), compute(snap(sharing = SharingMode.PAUSED)))
    @Test fun r05_offIsNotSharing() = assertEquals(PresenceState.NotSharing, compute(snap(sharing = SharingMode.OFF)))
    @Test fun r06_legacyPartnerWithoutSharingFieldUsesAge() =
        assertEquals(PresenceState.Live(10_000), compute(snap(locationAt = n - 10_000)))
    @Test fun r07_disconnectedPastGraceIsOffline() = assertEquals(
        PresenceState.Offline(n - 121_000),
        compute(snap(online = false, lastSeen = n - 121_000, sharing = SharingMode.LIVE)),
    )
    @Test fun r08_disconnectedWithinGraceStillUsesAge() = assertEquals(
        PresenceState.Live(60_000),
        compute(snap(online = false, lastSeen = n - 60_000, sharing = SharingMode.LIVE, locationAt = n - 60_000)),
    )
    @Test fun r09_freshFixIsLiveZero() =
        assertEquals(PresenceState.Live(0), compute(snap(online = true, sharing = SharingMode.LIVE, locationAt = n)))
    @Test fun r10_futureTimestampClampsToLiveZero() =
        assertEquals(PresenceState.Live(0), compute(snap(locationAt = n + 5_000)))
    @Test fun r11_justUnderLiveThreshold() =
        assertTrue(compute(snap(locationAt = n - 119_999)) is PresenceState.Live)
    @Test fun r12_atLiveThresholdIsRecent() =
        assertEquals(PresenceState.Recent(120_000), compute(snap(locationAt = n - 120_000)))
    @Test fun r13_justUnderRecentThreshold() =
        assertTrue(compute(snap(locationAt = n - 899_999)) is PresenceState.Recent)
    @Test fun r14_atRecentThresholdIsStale() =
        assertEquals(PresenceState.Stale(900_000), compute(snap(locationAt = n - 900_000)))
    @Test fun r15_sharingLiveButNoFixIsUnknownNeverLive() =
        assertEquals(PresenceState.Unknown, compute(snap(sharing = SharingMode.LIVE)))
    @Test fun r16_pausedWinsOverOffline() = assertTrue(
        compute(snap(online = false, lastSeen = n - 10 * HOUR, sharing = SharingMode.PAUSED)) is PresenceState.Paused,
    )
    @Test fun r17_offWinsOverOffline() = assertEquals(
        PresenceState.NotSharing,
        compute(snap(online = false, lastSeen = n - 10 * HOUR, sharing = SharingMode.OFF)),
    )
}
