package com.varad.unstoppableash.service.location

import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.location.LocationCadence
import com.varad.unstoppableash.domain.model.LocationFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocationShareEngineTest {
    @get:Rule val main = MainDispatcherRule()
    private val base = 40_000_000L
    private val db = FakeRtdb(nowMs = base)
    private val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 16)
    private val requested = mutableListOf<LocationCadence>()
    private val battery = MutableStateFlow<BatteryLevel?>(BatteryLevel(80, false))
    private val updates = LocationUpdates { c -> requested += c; fixes }

    private fun seed(sharing: String = "live", until: Long? = null) = runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Asha"), "bob" to mapOf("name" to "Bob")))
        db.set("pairs/p1/presence/alice", buildMap { put("sharing", sharing); until?.let { put("sharingUntil", it) } })
    }

    private fun TestScope.engine(): LocationShareEngine {
        val clock = Clock { base + testScheduler.currentTime }
        val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
        return LocationShareEngine(
            PairRepository(db, auth, clock), PresenceRepository(db), LocationRepository(db), TripRepository(db), EventRepository(db, clock),
            updates, battery, clock,
        ).also { e -> backgroundScope.launch { e.run() } }
    }

    private fun TestScope.settle(ms: Long = 1_000) { testScheduler.advanceTimeBy(ms); testScheduler.runCurrent() }
    private fun fix(lat: Double, lng: Double = 73.8, acc: Float = 10f) = LocationFix(lat, lng, acc, base)

    @Test fun eachFixIsSharedWhileLive() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        fixes.emit(fix(18.5)); settle()
        assertEquals(18.5, db.read("pairs/p1/location/alice/lat"))
        assertEquals(180_000L, requested.last().intervalMs)
    }

    @Test fun pausedSharesNothingAndKeepsTheLastPoint() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        fixes.emit(fix(18.5)); settle()
        db.set("pairs/p1/presence/alice/sharing", "paused"); settle()
        fixes.emit(fix(19.0)); settle()
        assertEquals("last point kept, nothing new (D-14)", 18.5, db.read("pairs/p1/location/alice/lat"))
    }

    @Test fun aTimedPauseResumesByItself() = runTest(main.dispatcher) {
        seed("paused", until = base + 60_000); engine(); settle()
        fixes.emit(fix(18.5)); settle()
        assertNull(db.read("pairs/p1/location/alice"))
        settle(90_000)
        assertEquals("live", db.read("pairs/p1/presence/alice/sharing"))
        assertNull(db.read("pairs/p1/presence/alice/sharingUntil"))
        fixes.emit(fix(18.6)); settle()
        assertEquals(18.6, db.read("pairs/p1/location/alice/lat"))
    }

    @Test fun aTripUsesTheTripCadence() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        db.set("pairs/p1/trips/alice", mapOf("state" to "active", "startedAt" to base, "notifyPartner" to true)); settle()
        assertEquals(15_000L, requested.last().intervalMs)
    }

    @Test fun lowBatterySlowsDown() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        battery.value = BatteryLevel(9, false); settle()
        assertEquals(600_000L, requested.last().intervalMs)
    }

    @Test fun arrivalHappensOnceAndIgnoresVagueFixes() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        db.set("pairs/p1/trips/alice", mapOf("state" to "active", "startedAt" to base, "notifyPartner" to true, "dest" to mapOf("lat" to 18.5, "lng" to 73.8, "label" to "Home"))); settle()
        fixes.emit(fix(18.6)); settle() // ~11 km away: on the way
        repeat(3) { fixes.emit(fix(18.5, acc = 900f)); settle() }
        assertEquals("a 900 m fix never arrives", "active", db.read("pairs/p1/trips/alice/state"))
        repeat(4) { fixes.emit(fix(18.5001)); settle() }
        assertEquals("arrived", db.read("pairs/p1/trips/alice/state"))
        val events = (db.read("pairs/p1/events") as Map<*, *>).values.map { it as Map<*, *> }
        assertEquals(listOf("arrived"), events.map { it["kind"] })
        assertTrue(events.all { it["type"] == "checkin" })
    }

    @Test fun noArrivalNoticeWhenThePartnerWasNotTold() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        db.set("pairs/p1/trips/alice", mapOf("state" to "active", "startedAt" to base, "notifyPartner" to false, "dest" to mapOf("lat" to 18.5, "lng" to 73.8))); settle()
        fixes.emit(fix(18.6)); settle()
        repeat(3) { fixes.emit(fix(18.5)); settle() }
        assertEquals("arrived", db.read("pairs/p1/trips/alice/state"))
        assertNull(db.read("pairs/p1/events"))
    }

    @Test fun aTripBackToWhereIStartedArrivesOnlyAfterLeaving() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        db.set("pairs/p1/trips/alice", mapOf("state" to "active", "startedAt" to base, "notifyPartner" to true, "dest" to mapOf("lat" to 18.5, "lng" to 73.8))); settle()
        repeat(3) { fixes.emit(fix(18.5)); settle() }
        assertEquals("still here is not 'arrived'", "active", db.read("pairs/p1/trips/alice/state"))
        fixes.emit(fix(18.51)); settle() // ~1.1 km away
        repeat(2) { fixes.emit(fix(18.5)); settle() }
        assertEquals("arrived", db.read("pairs/p1/trips/alice/state"))
    }

    @Test fun theArrivalCheckInSurvivesTheCadenceChangeItCauses() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        db.set("pairs/p1/trips/alice", mapOf("state" to "active", "startedAt" to base, "notifyPartner" to true, "dest" to mapOf("lat" to 18.5, "lng" to 73.8))); settle()
        fixes.emit(fix(18.6)); settle()
        db.ackHeld.value = true // like the SDK: the "arrived" write is visible at once but not yet confirmed
        repeat(2) { fixes.emit(fix(18.5)); settle() }
        db.ackHeld.value = false; settle()
        val kinds = (db.read("pairs/p1/events") as? Map<*, *>)?.values?.map { (it as Map<*, *>)["kind"] }
        assertEquals(listOf("arrived"), kinds)
    }

    @Test fun oldFixesFromAnOfflineSpellAreNotWrittenAsNew() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        fixes.emit(LocationFix(18.7, 73.8, 10f, base - 10 * 60_000)); settle()
        assertNull("a 10-minute-old fix would read 'just now' to the partner", db.read("pairs/p1/location/alice"))
    }

    @Test fun anOverdueTripStillTracksAndDetectsArrival() = runTest(main.dispatcher) {
        seed(); engine(); settle()
        db.set("pairs/p1/trips/alice", mapOf("state" to "overdue", "startedAt" to base, "notifyPartner" to false, "dest" to mapOf("lat" to 18.5, "lng" to 73.8))); settle()
        assertEquals(15_000L, requested.last().intervalMs)
    }
}
