package com.varad.unstoppableash.service.sos

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.local.ActiveSosStore
import com.varad.unstoppableash.data.local.EmergencyContactsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SmsReceipt
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SosState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SosControllerTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val base = 70_000_000L
    private val db = FakeRtdb(nowMs = base)
    private val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
    private val sms = FakeSms()
    private var fix: LocationFix? = LocationFix(18.52, 73.85, 9f, base - 30_000)

    class FakeSms(var permitted: Boolean = true) : SmsDispatcher {
        val sent = mutableListOf<Triple<String, String, String>>() // sosId, number, text
        val out = MutableSharedFlow<SmsResult>(extraBufferCapacity = 16)
        override fun canSend() = permitted
        override fun send(sosId: String, contactId: String, number: String, text: String): Boolean { sent += Triple(sosId, number, text); return true }
        override val results: SharedFlow<SmsResult> = out
    }

    private fun seedPair() = runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Asha"), "bob" to mapOf("name" to "Bob")))
    }

    private lateinit var contacts: EmergencyContactsStore
    private lateinit var store: ActiveSosStore

    private fun TestScope.stores() {
        if (::contacts.isInitialized) return
        contacts = EmergencyContactsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("c.preferences_pb") })
        store = ActiveSosStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("s.preferences_pb") })
    }

    private fun TestScope.controller(): SosController {
        stores()
        val clock = Clock { db.nowMs }
        return SosController(
            scope = backgroundScope, pairs = PairRepository(db, auth, clock), sos = SosRepository(db, clock), contacts = contacts,
            store = store, sms = sms, locator = { fix }, clock = clock, battery = { 57 },
        ).also { it.start(); settle() }
    }

    private fun TestScope.settle(ms: Long = 1_000) { testScheduler.advanceTimeBy(ms); testScheduler.runCurrent() }
    private fun records() = (db.read("pairs/p1/sos") as? Map<*, *>).orEmpty()
    private fun SosController.active() = state.value as SosState.Active

    @Test fun raiseWritesOneRecordAndTextsEachContactOnce() = runTest(main.dispatcher) {
        seedPair(); stores(); contacts.add("Mum", "+91 98765 43210"); contacts.add("Priya", "+91 91234 56789")
        val c = controller()
        assertEquals(RaiseResult.RAISED, c.raise()); settle(15_000)
        assertEquals(1, records().size)
        val id = records().keys.single() as String
        assertEquals("active", db.read("pairs/p1/sos/$id/state")); assertEquals(18.52, db.read("pairs/p1/sos/$id/lat"))
        assertEquals(2, sms.sent.size)
        assertTrue(sms.sent.all { it.third.startsWith("AshGo SOS: Asha needs help.") && "mlat=18.52000" in it.third && "Battery 57%" in it.third })
        assertEquals(BackendStatus.SAVED, c.active().backend)
    }

    @Test fun raisingAgainWhileActiveCreatesNothing() = runTest(main.dispatcher) {
        seedPair(); stores(); contacts.add("Mum", "111")
        val c = controller()
        c.raise(); settle(15_000)
        assertEquals(RaiseResult.ALREADY_ACTIVE, c.raise()); settle(15_000)
        assertEquals(1, records().size); assertEquals(1, sms.sent.size)
    }

    @Test fun noContactsMeansNoSms() = runTest(main.dispatcher) {
        seedPair()
        val c = controller()
        c.raise(); settle(15_000)
        assertTrue(sms.sent.isEmpty()); assertTrue(c.active().sms.isEmpty())
    }

    @Test fun withoutSmsPermissionNothingIsSentAndRowsSaySo() = runTest(main.dispatcher) {
        seedPair(); stores(); contacts.add("Mum", "111"); sms.permitted = false
        val c = controller()
        c.raise(); settle(15_000)
        assertTrue(sms.sent.isEmpty())
        assertEquals(SmsReceipt(SmsStatus.FAILED, SosController.SMS_OFF), c.active().sms.values.single())
    }

    @Test fun unknownLocationIsSaidPlainly() = runTest(main.dispatcher) {
        seedPair(); stores(); contacts.add("Mum", "111"); fix = null
        val c = controller()
        c.raise(); settle(15_000)
        assertTrue("Location unavailable." in sms.sent.single().third)
        assertNull(db.read("pairs/p1/sos/${records().keys.single()}/lat"))
    }

    @Test fun smsResultsReachTheStateAndTheRecordCounts() = runTest(main.dispatcher) {
        seedPair(); stores(); contacts.add("Mum", "111"); contacts.add("Priya", "222")
        val c = controller()
        c.raise(); settle(15_000)
        val id = c.active().sosId
        val (mum, priya) = c.active().sms.keys.toList()
        sms.out.emit(SmsResult(id, mum, SmsStatus.DELIVERED)); sms.out.emit(SmsResult(id, priya, SmsStatus.FAILED, "no signal")); settle()
        assertEquals(SmsStatus.DELIVERED, c.active().sms[mum]?.status); assertEquals("no signal", c.active().sms[priya]?.failure)
        assertEquals(1L, (db.read("pairs/p1/sos/$id/smsSent") as Number).toLong()); assertEquals(1L, (db.read("pairs/p1/sos/$id/smsFailed") as Number).toLong())
        c.retryFailedSms(); settle(15_000)
        assertEquals(3, sms.sent.size)
    }

    @Test fun partnerDeliveryAndAckReachTheState() = runTest(main.dispatcher) {
        seedPair()
        val c = controller()
        c.raise(); settle(15_000)
        val id = c.active().sosId
        db.set("pairs/p1/sos/$id/deliveredTo/bob", base + 5); settle()
        assertEquals(base + 5, c.active().deliveredToPartnerAt)
        db.set("pairs/p1/sos/$id/ackBy/bob", base + 9); settle()
        assertEquals(base + 9, c.active().partnerAckAt)
    }

    @Test fun cancelWritesOnceAndFalseAlarmGoesOnlyToThoseAlreadyTexted() = runTest(main.dispatcher) {
        seedPair(); stores(); contacts.add("Mum", "111"); contacts.add("Priya", "222")
        val c = controller()
        c.raise(); settle(15_000)
        val id = c.active().sosId
        val (mum, priya) = c.active().sms.keys.toList()
        sms.out.emit(SmsResult(id, mum, SmsStatus.SENT)); sms.out.emit(SmsResult(id, priya, SmsStatus.FAILED)); settle()
        c.cancel(); settle()
        assertEquals("cancelled", db.read("pairs/p1/sos/$id/state"))
        assertTrue(c.state.value is SosState.Cancelled)
        assertEquals(listOf("Mum"), c.falseAlarmRecipients().map { it.name })
        c.sendFalseAlarm(); settle()
        assertEquals(Triple("111", "AshGo: Asha is safe. The earlier SOS was a false alarm. Sorry for the worry.", 3), Triple(sms.sent.last().second, sms.sent.last().third, sms.sent.size))
        c.dismiss(); settle()
        assertEquals(SosState.Idle, c.state.value)
    }

    @Test fun afterProcessDeathThePendingAlertIsResentButSmsIsNeverRepeated() = runTest(main.dispatcher) {
        seedPair(); stores(); contacts.add("Mum", "111")
        val mumId = contacts.contacts.first().single().id
        store.save(SosState.Active("s-restored", base - 1_000, BackendStatus.PENDING, null, null, null, mapOf(mumId to SmsReceipt(SmsStatus.QUEUED))))
        val c = controller(); settle(15_000)
        assertEquals("active", db.read("pairs/p1/sos/s-restored/state"))
        assertTrue("never auto-resent", sms.sent.isEmpty())
        assertEquals(SmsReceipt(SmsStatus.FAILED, SosController.NOT_CONFIRMED), c.active().sms[mumId])
    }

    @Test fun withoutAnOrbitContactsAreStillTexted() = runTest(main.dispatcher) {
        stores(); contacts.add("Mum", "111")
        val c = controller()
        assertEquals(RaiseResult.RAISED, c.raise()); settle(15_000)
        assertEquals(1, sms.sent.size)
        assertEquals(BackendStatus.FAILED, c.active().backend)
    }
}
