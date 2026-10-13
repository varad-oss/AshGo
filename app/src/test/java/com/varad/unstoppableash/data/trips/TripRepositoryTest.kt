package com.varad.unstoppableash.data.trips

import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.domain.model.TripDestination
import com.varad.unstoppableash.domain.model.TripSnapshot
import com.varad.unstoppableash.domain.model.TripState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRepositoryTest {
    private val db = FakeRtdb(nowMs = 700_000L)
    private val trips = TripRepository(db)
    private val p = "p1"

    @Test fun startWritesRuleValidShape() = runTest {
        trips.start(p, "alice", TripDestination(18.5, 73.8, "Home"), expectBy = 900_000L, note = "Cab KA-01", notifyPartner = true)
        assertEquals(
            mapOf("state" to "active", "startedAt" to 700_000L, "notifyPartner" to true, "dest" to mapOf("lat" to 18.5, "lng" to 73.8, "label" to "Home"), "expectBy" to 900_000L, "note" to "Cab KA-01"),
            db.read("pairs/p1/trips/alice"),
        )
    }

    @Test fun minimalTripHasNoOptionalFields() = runTest {
        trips.start(p, "alice", null, null, null, notifyPartner = false)
        assertEquals(mapOf("state" to "active", "startedAt" to 700_000L, "notifyPartner" to false), db.read("pairs/p1/trips/alice"))
    }

    @Test fun extendArriveAndEnd() = runTest {
        trips.start(p, "alice", null, expectBy = 900_000L, note = null, notifyPartner = true)
        trips.extend(p, "alice", 1_800_000L)
        assertEquals(TripSnapshot(TripState.ACTIVE, 900_000L, 1_800_000L, null), trips.observe(p, "alice").first()?.snapshot)
        db.nowMs = 800_000L
        trips.arrive(p, "alice")
        assertEquals(TripSnapshot(TripState.ARRIVED, 900_000L, 1_800_000L, 800_000L), trips.observe(p, "alice").first()?.snapshot)
        trips.start(p, "alice", null, null, null, true)
        trips.end(p, "alice")
        assertEquals(TripState.ENDED, trips.observe(p, "alice").first()?.state)
    }

    @Test fun noTripIsNull() = runTest {
        assertNull(trips.observe(p, "bob").first())
        db.set("pairs/p1/trips/bob", mapOf("state" to "teleporting", "startedAt" to 1L, "notifyPartner" to true))
        assertNull(trips.observe(p, "bob").first())
    }
}
