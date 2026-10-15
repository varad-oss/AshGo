package com.varad.unstoppableash.data.presence

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.SharingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceRepositoryTest {
    private val db = FakeRtdb(nowMs = 50_000L)
    private val presence = PresenceRepository(db)
    private val location = LocationRepository(db)
    private val p = "p1"

    @Test fun partnerSnapshotCombinesPresenceAndLocation() = runTest {
        db.set("pairs/p1/presence/bob", mapOf("online" to true, "lastSeen" to 40_000L, "heartbeatAt" to 41_000L, "battery" to 64L, "charging" to false, "sharing" to "live"))
        db.set("pairs/p1/location/bob", mapOf("lat" to 18.5, "lng" to 73.8, "acc" to 12.0, "at" to 45_000L))
        assertEquals(
            PresenceSnapshot(online = true, lastSeen = 40_000L, heartbeatAt = 41_000L, battery = 64, charging = false, sharing = SharingMode.LIVE, locationAt = 45_000L, accuracyM = 12f),
            presence.partnerSnapshot(p, "bob").first(),
        )
    }

    @Test fun partialOrLegacyValuesBecomeNulls() = runTest {
        db.set("pairs/p1/presence/bob", mapOf("sharing" to "warp", "battery" to "full"))
        db.set("pairs/p1/location/bob", mapOf("lat" to 1.0, "lng" to 2.0, "at" to 0L))
        val s = presence.partnerSnapshot(p, "bob").first()
        assertNull(s.sharing); assertNull(s.battery); assertNull(s.locationAt); assertNull(s.online)
    }

    @Test fun nothingStoredIsAllNull() = runTest {
        assertEquals(PresenceSnapshot(null, null, null, null, null, null, null, null), presence.partnerSnapshot(p, "bob").first())
    }

    @Test fun setSharingWritesModeAndUntil() = runTest {
        assertTrue(presence.setSharing(p, "alice", SharingMode.PAUSED, until = 99_000L) is Outcome.Success)
        assertEquals(mapOf("sharing" to "paused", "sharingUntil" to 99_000L), db.read("pairs/p1/presence/alice"))
        presence.setSharing(p, "alice", SharingMode.LIVE, until = null)
        assertEquals(mapOf("sharing" to "live"), db.read("pairs/p1/presence/alice"))
    }

    @Test fun goOnlineRegistersDisconnectHandler() = runTest {
        presence.goOnline(p, "alice")
        assertEquals(mapOf("online" to true, "lastSeen" to 50_000L), db.read("pairs/p1/presence/alice"))
        db.nowMs = 60_000L
        db.disconnect()
        assertEquals(mapOf("online" to false, "lastSeen" to 60_000L), db.read("pairs/p1/presence/alice"))
    }

    @Test fun batteryWrittenOnlyOnFivePercentChangeOrChargingFlip() = runTest {
        presence.reportBattery(p, "alice", 80, charging = false)
        presence.reportBattery(p, "alice", 77, charging = false)
        assertEquals(80L, db.read("pairs/p1/presence/alice/battery"))
        presence.reportBattery(p, "alice", 75, charging = false)
        assertEquals(75L, db.read("pairs/p1/presence/alice/battery"))
        presence.reportBattery(p, "alice", 74, charging = true)
        assertEquals(true, db.read("pairs/p1/presence/alice/charging"))
        assertEquals(3, db.writeLog.count { it.startsWith("update pairs/p1/presence/alice") })
    }

    @Test fun writeLocationUsesServerTimeAndDropsZeroAccuracy() = runTest {
        location.write(p, "alice", 18.5, 73.8, accuracyM = 0f)
        assertEquals(mapOf("lat" to 18.5, "lng" to 73.8, "at" to 50_000L), db.read("pairs/p1/location/alice"))
        location.write(p, "alice", 18.5, 73.8, accuracyM = 9.5f)
        assertEquals(9.5, (db.read("pairs/p1/location/alice") as Map<*, *>)["acc"])
    }

    @Test fun observeLocationMapsFix() = runTest {
        db.set("pairs/p1/location/bob", mapOf("lat" to 18L, "lng" to 73.5, "acc" to 0.0, "at" to 45_000L))
        assertEquals(LocationFix(18.0, 73.5, null, 45_000L), location.observe(p, "bob").first())
        db.set("pairs/p1/location/bob", mapOf("lat" to 18.0, "lng" to 73.5, "at" to -1L))
        assertNull(location.observe(p, "bob").first())
    }
}
