package com.varad.unstoppableash.feature.safety

import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.domain.model.SosRecordState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PartnerSosViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val base = 90_000_000L
    private val db = FakeRtdb(nowMs = base)

    private fun seed() = runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Asha"), "bob" to mapOf("name" to "Bob")))
    }

    private fun TestScope.vm(): PartnerSosViewModel {
        val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
        val clock = Clock { db.nowMs }
        return PartnerSosViewModel(PairRepository(db, auth, clock), SosRepository(db, clock))
            .also { v -> backgroundScope.launch { v.state.collect {} } }
    }

    private fun TestScope.settle() { testScheduler.advanceTimeBy(1_000); testScheduler.runCurrent() }

    @Test fun showingItRecordsDeliveryOnceAndAckIsExplicit() = runTest(main.dispatcher) {
        seed()
        db.set("pairs/p1/sos/s1", mapOf("from" to "bob", "state" to "active", "at" to base - 60_000, "lat" to 18.5, "lng" to 73.8, "locAt" to base - 90_000, "battery" to 23L))
        val vm = vm(); settle()
        val s = vm.state.value
        assertEquals("Bob", s.partnerName); assertEquals(SosRecordState.ACTIVE, s.record?.state); assertEquals(23, s.record?.battery)
        assertNull("not delivered until it is actually on screen", db.read("pairs/p1/sos/s1/deliveredTo/alice"))
        vm.setVisible(true); settle(); vm.setVisible(false); settle(); vm.setVisible(true); settle()
        assertEquals(base, db.read("pairs/p1/sos/s1/deliveredTo/alice"))
        assertNull("seeing is not acknowledging", db.read("pairs/p1/sos/s1/ackBy/alice"))
        vm.acknowledge(); settle()
        assertEquals(base, db.read("pairs/p1/sos/s1/ackBy/alice"))
    }

    @Test fun myOwnSosIsNeverShownHere() = runTest(main.dispatcher) {
        seed()
        db.set("pairs/p1/sos/s1", mapOf("from" to "alice", "state" to "active", "at" to base - 1))
        val vm = vm(); settle()
        assertNull(vm.state.value.record)
    }

    @Test fun aCancelledSosSaysSo() = runTest(main.dispatcher) {
        seed()
        db.set("pairs/p1/sos/s1", mapOf("from" to "bob", "state" to "cancelled", "at" to base - 60_000, "cancelledAt" to base - 1_000))
        val vm = vm(); settle()
        assertEquals(SosRecordState.CANCELLED, vm.state.value.record?.state)
    }
}
