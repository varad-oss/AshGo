package com.varad.unstoppableash.feature.safety

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.local.EmergencyContactsStore
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.domain.model.SmsReceipt
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SosState
import com.varad.unstoppableash.service.sos.RaiseResult
import com.varad.unstoppableash.service.sos.SosControl
import com.varad.unstoppableash.service.sos.SosLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafetyViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val db = FakeRtdb(nowMs = 1_000_000L)
    private val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
    private var perms = SafetyPermissions(sms = true, location = LocationAccess.PRECISE)

    class FakeSos : SosControl {
        var raises = 0; var cancels = 0; var retries = 0; var falseAlarms = 0; var dismissals = 0
        var recipients = emptyList<EmergencyContact>()
        override val state = MutableStateFlow<SosState>(SosState.Idle)
        override val location = MutableStateFlow<SosLocation>(SosLocation.Unavailable)
        override suspend fun raise(): RaiseResult { raises++; return RaiseResult.RAISED }
        override fun cancel() { cancels++ }
        override fun retryFailedSms() { retries++ }
        override suspend fun falseAlarmRecipients() = recipients
        override fun sendFalseAlarm() { falseAlarms++ }
        override fun dismiss() { dismissals++ }
    }

    private val sos = FakeSos()
    private lateinit var contacts: EmergencyContactsStore
    private lateinit var settings: SettingsStore

    private fun seedPair() = runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Asha"), "bob" to mapOf("name" to "Bob")))
    }

    private fun TestScope.vm(): SafetyViewModel {
        contacts = EmergencyContactsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("c.preferences_pb") })
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("s.preferences_pb") })
        return SafetyViewModel(PairRepository(db, auth, Clock { db.nowMs }), contacts, settings, sos, permissions = { perms })
            .also { v -> backgroundScope.launch { v.state.collect {} } }
    }

    private fun TestScope.settle() { testScheduler.advanceTimeBy(1_000); testScheduler.runCurrent() }

    @Test fun whatHappensReflectsRealState() = runTest(main.dispatcher) {
        seedPair()
        val vm = vm(); contacts.add("Mum", "111"); contacts.add("Priya", "222"); settle()
        val s = vm.state.value
        assertTrue(s.paired); assertEquals("Bob", s.partnerName); assertEquals(2, s.contacts.size)
        assertTrue(s.permissions.sms); assertEquals(LocationAccess.PRECISE, s.permissions.location)
    }

    @Test fun permissionChangesShowAfterRefresh() = runTest(main.dispatcher) {
        val vm = vm(); settle()
        assertTrue(vm.state.value.permissions.sms)
        perms = SafetyPermissions(sms = false, location = LocationAccess.OFF)
        vm.refreshPermissions(); settle()
        assertFalse(vm.state.value.permissions.sms); assertEquals(LocationAccess.OFF, vm.state.value.permissions.location)
    }

    @Test fun raiseGoesThroughTheControllerOnce() = runTest(main.dispatcher) {
        val vm = vm(); settle()
        vm.raise(); vm.raise(); settle()
        assertEquals("a second confirmation while the first is in flight is ignored", 1, sos.raises)
    }

    @Test fun activeStateAndContactNamesReachTheScreen() = runTest(main.dispatcher) {
        val vm = vm(); contacts.add("Mum", "111"); settle()
        val mum = contacts.contacts.first().single()
        sos.state.value = SosState.Active("s1", 5L, BackendStatus.SAVED, 6L, null, null, mapOf(mum.id to SmsReceipt(SmsStatus.FAILED, "no signal")))
        settle()
        assertEquals("Mum", vm.state.value.contactName(mum.id))
        vm.retrySms(); vm.cancel(); settle()
        assertEquals(1, sos.retries); assertEquals(1, sos.cancels)
    }

    @Test fun falseAlarmIsOfferedOnlyWithRecipientsAndSentOnlyOnConfirm() = runTest(main.dispatcher) {
        val vm = vm(); settle()
        sos.state.value = SosState.Cancelled("s1", 9L, emptyMap())
        vm.offerFalseAlarm(); settle()
        assertNull("nobody was texted, nothing to offer", vm.state.value.falseAlarmRecipients)
        sos.recipients = listOf(EmergencyContact("c1", "Mum", "111"))
        vm.offerFalseAlarm(); settle()
        assertEquals(listOf("Mum"), vm.state.value.falseAlarmRecipients?.map { it.name })
        assertEquals(0, sos.falseAlarms)
        vm.confirmFalseAlarm(); settle()
        assertEquals(1, sos.falseAlarms); assertNull(vm.state.value.falseAlarmRecipients)
        vm.done(); settle()
        assertEquals(1, sos.dismissals)
    }

    @Test fun partnerNumberIsKeptOnThisPhone() = runTest(main.dispatcher) {
        val vm = vm(); settle()
        vm.setPartnerPhone("+91 99999 00000"); settle()
        assertEquals("+91 99999 00000", vm.state.value.partnerPhone)
        assertNull("never written to the backend", db.read("pairs/p1/profiles/alice/phone"))
    }
}
