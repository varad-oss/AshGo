package com.varad.unstoppableash.service.notify

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.chat.ChatRepository
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.notify.NotificationKind
import com.varad.unstoppableash.domain.notify.PlannedNotification
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationEngineTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private val base = 80_000_000L
    private val db = FakeRtdb(nowMs = base)

    class FakeNotifier : Notifier {
        val posted = mutableListOf<Pair<PlannedNotification, String>>()
        val cancelled = mutableListOf<String>()
        override fun post(n: PlannedNotification, partnerName: String) { posted += n to partnerName }
        override fun cancel(tag: String) { cancelled += tag }
    }

    private val notifier = FakeNotifier()
    private lateinit var settings: SettingsStore

    private fun seed() = runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Asha"), "bob" to mapOf("name" to "Bob")))
    }

    private fun TestScope.start(): Job {
        if (!::settings.isInitialized) settings = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("s.preferences_pb") })
        val clock = Clock { base + testScheduler.currentTime }
        val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
        val engine = NotificationEngine(PairRepository(db, auth, clock), ChatRepository(db), EventRepository(db, clock), SosRepository(db, clock), TripRepository(db), settings, notifier, clock)
        return backgroundScope.launch { engine.run() }
    }

    private fun TestScope.settle(ms: Long = 1_000) { testScheduler.advanceTimeBy(ms); testScheduler.runCurrent() }

    @Test fun aPartnerMessagePostsOnceEvenAcrossRestarts() = runTest(main.dispatcher) {
        seed()
        val first = start(); settle()
        db.set("pairs/p1/chat/m1", mapOf("from" to "bob", "text" to "hi", "at" to base)); settle()
        assertEquals(listOf(NotificationKind.CHAT to "Bob"), notifier.posted.map { it.first.kind to it.second })
        settle(60_000)
        assertEquals(1, notifier.posted.size)
        first.cancel(); start(); settle()
        assertEquals("a restarted service doesn't repost", 1, notifier.posted.size)
    }

    @Test fun myOwnActivityPostsNothing() = runTest(main.dispatcher) {
        seed(); start(); settle()
        db.set("pairs/p1/chat/m1", mapOf("from" to "alice", "text" to "hi", "at" to base)); settle()
        assertTrue(notifier.posted.isEmpty())
    }

    @Test fun anSosAlertsEvenWithChatMutedAndCancelsWhenCalledOff() = runTest(main.dispatcher) {
        seed(); start(); settle()
        settings.update { it.copy(notifyChat = false) }; settle()
        db.set("pairs/p1/chat/m1", mapOf("from" to "bob", "text" to "hi", "at" to base))
        db.set("pairs/p1/sos/s1", mapOf("from" to "bob", "state" to "active", "at" to base)); settle()
        assertEquals(listOf(NotificationKind.PARTNER_SOS), notifier.posted.map { it.first.kind })
        db.set("pairs/p1/sos/s1/state", "cancelled"); settle()
        assertEquals(listOf("sos:s1"), notifier.cancelled)
    }

    @Test fun aPartnerOverdueTripAlertsAfterTheGrace() = runTest(main.dispatcher) {
        seed()
        db.set("pairs/p1/trips/bob", mapOf("state" to "active", "startedAt" to base - 3_600_000, "notifyPartner" to true, "expectBy" to base - 5 * 60_000))
        start(); settle()
        assertTrue("inside the grace the traveller is asked first", notifier.posted.isEmpty())
        settle(6 * 60_000L)
        assertEquals(listOf(NotificationKind.PARTNER_OVERDUE), notifier.posted.map { it.first.kind })
    }
}
