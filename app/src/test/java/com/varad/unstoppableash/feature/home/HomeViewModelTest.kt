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

    private fun vm(clock: Clock = Clock { db.nowMs }): HomeViewModel {
        val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
        return HomeViewModel(
            pairs = PairRepository(db, auth, clock), presence = PresenceRepository(db), location = LocationRepository(db),
            chat = ChatRepository(db), events = EventRepository(db, clock), sos = SosRepository(db, clock), trips = TripRepository(db),
            rtdb = db, clock = clock,
        )
    }

    @Test fun presenceAgesWithoutNewData() = runTest(main.dispatcher) {
        val clock = Clock { base + testScheduler.currentTime }
        val snapshots = MutableStateFlow(PresenceSnapshot(true, null, null, null, null, SharingMode.LIVE, base, null))
        val states = mutableListOf<PresenceState>()
        val job = backgroundScope.launchCollect(presenceStates(snapshots, clock, tickMs = 30_000), states)
        advanceTimeBy(1)
        assertTrue(states.last() is PresenceState.Live)
        advanceTimeBy(2 * 60_000L)
        assertTrue("re-evaluated without new data", states.last() is PresenceState.Recent)
        advanceTimeBy(15 * 60_000L)
        assertTrue(states.last() is PresenceState.Stale)
        job.cancel()
    }

    @Test fun noLocationIsNeverLive() = runTest(main.dispatcher) {
        val snapshots = MutableStateFlow(PresenceSnapshot(true, base, base, 80, false, SharingMode.LIVE, null, null))
        assertEquals(PresenceState.Unknown, presenceStates(snapshots, Clock { base }, 30_000).first())
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
}

private fun <T> CoroutineScope.launchCollect(flow: Flow<T>, into: MutableList<T>): Job =
    launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { into += it } }
