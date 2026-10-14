package com.varad.unstoppableash.feature.map

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import org.junit.rules.TemporaryFolder
import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.location.PauseChoice
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.TripDestination
import com.varad.unstoppableash.domain.trips.OverduePhase
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class MapViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private val base = 60_000_000L
    private val db = FakeRtdb(nowMs = base)

    private fun seed() = runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Asha"), "bob" to mapOf("name" to "Bob")))
    }

    private lateinit var settings: SettingsStore

    private fun TestScope.vm(): MapViewModel {
        val clock = Clock { base + testScheduler.currentTime }
        val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("s.preferences_pb") })
        return MapViewModel(PairRepository(db, auth, clock), PresenceRepository(db), LocationRepository(db), TripRepository(db), settings, clock, ZoneId.of("UTC"))
            .also { v -> backgroundScope.launch { v.state.collect {} } }
    }

    private fun TestScope.settle(ms: Long = 1_000) { testScheduler.advanceTimeBy(ms); testScheduler.runCurrent() }

    @Test fun noLocationMeansNoPartnerMarker() = runTest(main.dispatcher) {
        seed(); db.set("pairs/p1/presence/bob", mapOf("online" to true, "lastSeen" to base, "sharing" to "live"))
        val vm = vm(); settle()
        assertNull(vm.state.value.partnerFix); assertEquals("Bob", vm.state.value.partnerName)
    }

    @Test fun aFreshPartnerIsVividAndAStaleOneFaded() = runTest(main.dispatcher) {
        seed()
        db.set("pairs/p1/presence/bob", mapOf("online" to true, "lastSeen" to base, "sharing" to "live"))
        db.set("pairs/p1/location/bob", mapOf("lat" to 18.5, "lng" to 73.8, "acc" to 10.0, "at" to base))
        val vm = vm(); settle()
        assertNotNull(vm.state.value.partnerFix); assertTrue(vm.state.value.partnerPresence is PresenceState.Live); assertFalse(vm.state.value.partnerFaded)
        settle(20 * 60_000L)
        assertTrue("old data is shown as old", vm.state.value.partnerFaded)
    }

    @Test fun sharingControlsWriteMyPresence() = runTest(main.dispatcher) {
        seed(); val vm = vm(); settle()
        vm.shareLive(); settle()
        assertEquals("live", db.read("pairs/p1/presence/alice/sharing")); assertEquals(SharingMode.LIVE, vm.state.value.mySharing)
        vm.pause(PauseChoice.ONE_HOUR); settle()
        assertEquals("paused", db.read("pairs/p1/presence/alice/sharing")); assertNotNull(db.read("pairs/p1/presence/alice/sharingUntil"))
        vm.stopSharing(); settle()
        assertEquals("off", db.read("pairs/p1/presence/alice/sharing"))
    }

    @Test fun tripLifecycle() = runTest(main.dispatcher) {
        seed(); val vm = vm(); settle()
        val deadline = base + 3_600_000
        vm.startTrip(TripDestination(18.5, 73.8, "Home"), deadline, " train ", notifyPartner = true, shareDuringTrip = false); settle()
        assertEquals("active", db.read("pairs/p1/trips/alice/state")); assertEquals(deadline, db.read("pairs/p1/trips/alice/expectBy")); assertEquals("train", db.read("pairs/p1/trips/alice/note"))
        vm.extendTrip(); settle()
        assertEquals(deadline + 15 * 60_000, db.read("pairs/p1/trips/alice/extendedTo"))
        vm.arrive(); settle()
        assertEquals("arrived", db.read("pairs/p1/trips/alice/state"))
    }

    @Test fun overduePhasesForMeAndForMyPartner() = runTest(main.dispatcher) {
        seed()
        db.set("pairs/p1/trips/alice", mapOf("state" to "active", "startedAt" to base - 7_200_000, "notifyPartner" to true, "expectBy" to base - 60_000))
        db.set("pairs/p1/trips/bob", mapOf("state" to "active", "startedAt" to base - 7_200_000, "notifyPartner" to true, "expectBy" to base - 3_600_000))
        val vm = vm(); settle()
        assertEquals(OverduePhase.ASK_TRAVELLER, vm.state.value.myTripPhase)
        assertEquals(OverduePhase.ALERT_PARTNER, vm.state.value.partnerTripPhase)
    }

    @Test fun stopAlsoTakesMyLastPointOffTheirMap() = runTest(main.dispatcher) {
        seed(); db.set("pairs/p1/location/alice", mapOf("lat" to 1.0, "lng" to 2.0, "at" to base))
        val vm = vm(); settle()
        vm.shareLive(); settle()
        assertTrue(settings.settings.first().shareFromThisPhone)
        vm.stopSharing(); settle()
        assertNull("Stop means gone, unlike Pause (D-14)", db.read("pairs/p1/location/alice"))
        assertFalse(settings.settings.first().shareFromThisPhone)
    }

    @Test fun pauseKeepsTheLastPoint() = runTest(main.dispatcher) {
        seed(); db.set("pairs/p1/location/alice", mapOf("lat" to 1.0, "lng" to 2.0, "at" to base))
        val vm = vm(); settle()
        vm.pause(PauseChoice.ONE_HOUR); settle()
        assertNotNull(db.read("pairs/p1/location/alice"))
    }

    @Test fun startingATripCanTurnSharingOn() = runTest(main.dispatcher) {
        seed(); val vm = vm(); settle()
        vm.startTrip(null, base + 3_600_000, null, notifyPartner = true, shareDuringTrip = true); settle()
        assertEquals("live", db.read("pairs/p1/presence/alice/sharing"))
        assertTrue(settings.settings.first().shareFromThisPhone)
    }

    @Test fun sharingOnForTheAccountButNotChosenHereIsSaidPlainly() = runTest(main.dispatcher) {
        seed(); db.set("pairs/p1/presence/alice", mapOf("sharing" to "live"))
        val vm = vm(); settle()
        assertEquals(SharingMode.LIVE, vm.state.value.mySharing)
        assertFalse(vm.state.value.sharingFromThisPhone)
    }

    @Test fun extendWithoutADeadlineDoesNothing() = runTest(main.dispatcher) {
        seed(); val vm = vm(); settle()
        vm.startTrip(null, null, null, notifyPartner = true, shareDuringTrip = false); settle()
        vm.extendTrip(); settle()
        assertNull("+15 min on an open trip would invent an overdue alert", db.read("pairs/p1/trips/alice/extendedTo"))
    }
}
