package com.varad.unstoppableash.feature.home

import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.chat.ChatRepository
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.TripState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val base = 10_000_000L
    private val db = FakeRtdb(nowMs = base)

    private fun seedPair() = kotlinx.coroutines.runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf(
            "alice" to mapOf("name" to "Alice", "email" to "alice@example.com", "greeting" to true),
            "bob" to mapOf("name" to "Bob", "email" to "bob@example.com", "greeting" to true, "avatar" to "QUJD"),
        ))
    }

    private val mySos = MutableStateFlow<com.varad.unstoppableash.domain.model.SosState>(com.varad.unstoppableash.domain.model.SosState.Idle)

    private fun vm(clock: Clock = Clock { db.nowMs }): HomeViewModel {
        val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
        return HomeViewModel(
            pairs = PairRepository(db, auth, clock), presence = PresenceRepository(db), location = LocationRepository(db),
            chat = ChatRepository(db), events = EventRepository(db, clock), sos = SosRepository(db, clock), trips = TripRepository(db),
            rtdb = db, clock = clock, mySos = mySos,
        )
    }

    @Test fun presenceAgesWithoutNewData() = runTest(main.dispatcher) {
        seedPair()
        db.set("pairs/p1/presence/bob", mapOf("online" to true, "lastSeen" to base, "sharing" to "live"))
        db.set("pairs/p1/location/bob", mapOf("lat" to 18.5, "lng" to 73.8, "at" to base))
        val vm = vm(Clock { base + testScheduler.currentTime })
        backgroundScope.launch { vm.state.collect {} }
        settle(10)
        assertTrue(vm.state.value.partnerPresence is PresenceState.Live)
        settle(2 * 60_000L)
        assertTrue("re-evaluated without new data", vm.state.value.partnerPresence is PresenceState.Recent)
        settle(15 * 60_000L)
        assertTrue(vm.state.value.partnerPresence is PresenceState.Stale)
    }

    @Test fun noLocationIsNeverLive() = runTest(main.dispatcher) {
        seedPair()
        db.set("pairs/p1/presence/bob", mapOf("online" to true, "lastSeen" to base, "battery" to 80L, "sharing" to "live"))
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        settle(10)
        assertFalse(vm.state.value.loading)
        assertEquals(PresenceState.Unknown, vm.state.value.partnerPresence)
    }

    @Test fun homeCombinesEverythingAboutThePartner() = runTest(main.dispatcher) {
        seedPair()
        db.set("pairs/p1/presence/bob", mapOf("online" to true, "lastSeen" to base, "battery" to 12L, "charging" to false, "sharing" to "live"))
        db.set("pairs/p1/location/bob", mapOf("lat" to 18.5, "lng" to 73.8, "at" to base - 30_000))
        db.set("pairs/p1/presence/alice", mapOf("sharing" to "paused"))
        db.set("pairs/p1/chat/m1", mapOf("from" to "bob", "text" to "hi", "at" to base - 1))
        db.set("pairs/p1/events/e1", mapOf("from" to "bob", "type" to "checkin", "kind" to "home_safe", "at" to base - 5))
        db.set("pairs/p1/sos/s1", mapOf("from" to "bob", "state" to "active", "at" to base - 2))
        db.set("pairs/p1/trips/bob", mapOf("state" to "active", "startedAt" to base - 9, "notifyPartner" to true))
        val s = vm().state.first { !it.loading && it.partnerTrip != null && it.partnerSos != null && it.unread > 0 }
        assertEquals("Bob", s.partnerName); assertEquals("QUJD", s.partnerAvatar); assertEquals("Alice", s.myName)
        assertTrue(s.partnerPresence is PresenceState.Live)
        assertEquals(12, s.partnerBattery); assertEquals(SharingMode.PAUSED, s.mySharing); assertEquals(1, s.unread)
        assertEquals("home_safe", s.latestPartnerCheckIn?.kind); assertEquals("s1", s.partnerSos?.id)
        assertEquals(TripState.ACTIVE, s.partnerTrip?.state); assertEquals(18.5, s.partnerLocation?.lat)
    }

    @Test fun checkInAndNudgeReportRealOutcomes() = runTest(main.dispatcher) {
        seedPair()
        val vm = vm()
        vm.state.first { !it.loading }
        vm.checkIn("home_safe", null)
        assertEquals(ActionStatus.Saved(Action.CHECK_IN), vm.state.first { it.action is ActionStatus.Saved }.action)
        vm.nudge("thinking_of_you")
        vm.state.first { it.action == ActionStatus.Saved(Action.NUDGE) }
        vm.nudge("call_me")
        assertEquals(ActionStatus.Failed(Action.NUDGE, FailureReason.RATE_LIMITED), vm.state.first { it.action is ActionStatus.Failed }.action)
    }

    @Test fun marksMeOnlineAndReRegistersAfterReconnect() = runTest(main.dispatcher) {
        seedPair()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        vm.state.first { !it.loading }
        main.dispatcher.scheduler.advanceTimeBy(1_000); main.dispatcher.scheduler.runCurrent()
        assertEquals(true, db.read("pairs/p1/presence/alice/online"))
        db.disconnect()
        assertEquals(false, db.read("pairs/p1/presence/alice/online"))
        main.dispatcher.scheduler.advanceTimeBy(1_000); main.dispatcher.scheduler.runCurrent() // time passes while offline
        db.connectedState.value = true
        main.dispatcher.scheduler.advanceTimeBy(1_000); main.dispatcher.scheduler.runCurrent()
        assertEquals("online again after reconnect", true, db.read("pairs/p1/presence/alice/online"))
        assertTrue("disconnect handler re-registered", db.onDisconnectWrites.containsKey("pairs/p1/presence/alice"))
    }

    @Test fun offlineAndReadFailuresShowBanners() = runTest(main.dispatcher) {
        seedPair()
        val vm = vm()
        vm.state.first { !it.loading }
        db.connectedState.value = false
        assertEquals(FailureReason.OFFLINE, vm.state.first { it.connectionProblem != null }.connectionProblem)
        db.connectedState.value = true
        vm.state.first { it.connectionProblem == null }
        db.failReads("pairs/p1/sos")
        assertEquals(FailureReason.PERMISSION_DENIED, vm.state.first { it.error != null }.error)
        vm.dismissError()
        vm.state.first { it.error == null }
    }

    private fun kotlinx.coroutines.test.TestScope.settle(ms: Long = 1_000) { testScheduler.advanceTimeBy(ms); testScheduler.runCurrent() }

    @Test fun partnerSosShowsEvenWhenAnotherStreamIsDenied() = runTest(main.dispatcher) {
        seedPair()
        db.set("pairs/p1/sos/s1", mapOf("from" to "bob", "state" to "active", "at" to base - 2))
        db.failReads("pairs/p1/trips")
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        settle()
        val s = vm.state.value
        assertEquals("s1", s.partnerSos?.id)
        assertFalse("Home isn't stuck loading on one denied stream", s.loading)
    }

    @Test fun partnerSosShowsBeforePresenceLoads() = runTest(main.dispatcher) {
        seedPair()
        db.set("pairs/p1/sos/s1", mapOf("from" to "bob", "state" to "active", "at" to base - 2))
        db.failReads("pairs/p1/presence/bob")
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        settle()
        assertEquals("s1", vm.state.value.partnerSos?.id)
    }

    @Test fun cachedStateIsDroppedOnceNobodyIsWatching() = runTest(main.dispatcher) {
        seedPair()
        val vm = vm()
        val job = backgroundScope.launch { vm.state.collect {} }
        settle()
        assertFalse(vm.state.value.loading)
        job.cancel()
        settle(6_000)
        assertTrue("a returning screen starts from loading, not old data", vm.state.value.loading)
    }

    @Test fun savedStatusClearsAfterAFewSeconds() = runTest(main.dispatcher) {
        seedPair()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        settle()
        vm.checkIn("home_safe", null)
        settle(100)
        assertEquals(ActionStatus.Saved(Action.CHECK_IN), vm.state.value.action)
        settle(HomeViewModel.SAVED_VISIBLE_MS + 100)
        assertEquals(ActionStatus.Idle, vm.state.value.action)
    }

    @Test fun errorsAndOutcomesDontFollowYouToANewPair() = runTest(main.dispatcher) {
        seedPair()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        settle()
        vm.nudge("thinking_of_you"); settle(100)
        vm.nudge("call_me"); settle(100)
        assertTrue(vm.state.value.action is ActionStatus.Failed)
        db.failReads("pairs/p1/sos"); settle(100)
        assertEquals(FailureReason.PERMISSION_DENIED, vm.state.value.error)

        db.remove("pairs/p1"); db.clearFailures()
        db.set("userPairs/alice", "p2")
        db.set("pairs/p2/meta", mapOf("memberA" to "alice", "memberB" to "carol", "relationship" to "FRIENDS", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p2/profiles", mapOf("alice" to mapOf("name" to "Alice"), "carol" to mapOf("name" to "Carol")))
        settle()
        val s = vm.state.value
        assertEquals("Carol", s.partnerName)
        assertEquals(null, s.error)
        assertEquals(ActionStatus.Idle, s.action)
    }

    @Test fun nowTicksFromTheServerClock() = runTest(main.dispatcher) {
        seedPair()
        val clock = Clock { base + 7_000 + testScheduler.currentTime }
        val vm = vm(clock)
        backgroundScope.launch { vm.state.collect {} }
        settle(10)
        val first = vm.state.value.nowMs
        assertTrue(first >= base + 7_000)
        settle(31_000)
        assertTrue("now advances on its own", vm.state.value.nowMs >= first + 30_000)
    }

    @Test fun myActiveSosReachesHome() = runTest(main.dispatcher) {
        seedPair()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        settle()
        assertFalse(vm.state.value.mySosActive)
        mySos.value = com.varad.unstoppableash.domain.model.SosState.Active("s1", 1L, com.varad.unstoppableash.domain.model.BackendStatus.PENDING, null, null, null, emptyMap())
        settle()
        assertTrue(vm.state.value.mySosActive)
    }
}
