package com.varad.unstoppableash.domain.sos

import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.SmsReceipt
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SmsTarget
import com.varad.unstoppableash.domain.model.SosAction
import com.varad.unstoppableash.domain.model.SosEffect
import com.varad.unstoppableash.domain.model.SosState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SosStateMachineTest {
    private val id = "sos-1"
    private val t0 = 1_000L
    private val targets = listOf(SmsTarget("c1"), SmsTarget("c2"))

    private fun reduce(s: SosState, a: SosAction) = SosStateMachine.reduce(s, a)
    private fun raised(): SosState.Active = reduce(SosState.Idle, SosAction.Raise(id, t0, targets)).state as SosState.Active
    private fun SosState.apply(vararg actions: SosAction): SosState = actions.fold(this) { s, a -> reduce(s, a).state }
    private fun SosState.sms(c: String) = when (this) {
        is SosState.Active -> sms.getValue(c).status
        is SosState.Cancelled -> sms.getValue(c).status
        SosState.Idle -> error("idle")
    }

    @Test fun raiseFromIdle_queuesBackendAndOneSmsPerContact() {
        val t = reduce(SosState.Idle, SosAction.Raise(id, t0, targets))
        val s = t.state as SosState.Active
        assertEquals(BackendStatus.PENDING, s.backend)
        assertEquals(t0, s.raisedAt)
        assertEquals(mapOf("c1" to SmsReceipt(SmsStatus.QUEUED), "c2" to SmsReceipt(SmsStatus.QUEUED)), s.sms)
        assertEquals(
            listOf(SosEffect.WriteBackend(id), SosEffect.SendSms(id, "c1"), SosEffect.SendSms(id, "c2")),
            t.effects,
        )
    }

    @Test fun raiseWithNoContacts_stillWritesBackend() {
        val t = reduce(SosState.Idle, SosAction.Raise(id, t0, emptyList()))
        assertEquals(listOf(SosEffect.WriteBackend(id)), t.effects)
        assertTrue((t.state as SosState.Active).sms.isEmpty())
    }

    @Test fun raiseWhileActive_isNoOp() {
        val active = raised()
        val t = reduce(active, SosAction.Raise("sos-2", t0 + 5, targets))
        assertEquals(active, t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test fun backendSaved_setsSavedAndServerAt() {
        val s = raised().apply(SosAction.BackendSaved(id, 1_234L)) as SosState.Active
        assertEquals(BackendStatus.SAVED, s.backend)
        assertEquals(1_234L, s.serverAt)
    }

    @Test fun backendFailed_setsFailed() {
        val s = raised().apply(SosAction.BackendFailed(id)) as SosState.Active
        assertEquals(BackendStatus.FAILED, s.backend)
    }

    @Test fun smsSentThenDelivered_endsDelivered() {
        val s = raised().apply(
            SosAction.SmsResult(id, "c1", SmsStatus.SENT),
            SosAction.SmsResult(id, "c1", SmsStatus.DELIVERED),
        )
        assertEquals(SmsStatus.DELIVERED, s.sms("c1"))
        assertEquals(SmsStatus.QUEUED, s.sms("c2"))
    }

    @Test fun smsDeliveredBeforeSent_staysDelivered() {
        val s = raised().apply(
            SosAction.SmsResult(id, "c1", SmsStatus.DELIVERED),
            SosAction.SmsResult(id, "c1", SmsStatus.SENT),
        )
        assertEquals(SmsStatus.DELIVERED, s.sms("c1"))
    }

    @Test fun failedNeverOverridesDelivered() {
        val s = raised().apply(
            SosAction.SmsResult(id, "c1", SmsStatus.DELIVERED),
            SosAction.SmsResult(id, "c1", SmsStatus.FAILED, "generic"),
        )
        assertEquals(SmsStatus.DELIVERED, s.sms("c1"))
    }

    @Test fun smsSentAfterFailed_upgrades() {
        val s = raised().apply(
            SosAction.SmsResult(id, "c1", SmsStatus.FAILED, "no service"),
            SosAction.SmsResult(id, "c1", SmsStatus.SENT),
        ) as SosState.Active
        assertEquals(SmsReceipt(SmsStatus.SENT), s.sms.getValue("c1"))
    }

    @Test fun smsFailed_thenRetry_requeuesOnlyFailed() {
        val failed = raised().apply(
            SosAction.SmsResult(id, "c1", SmsStatus.FAILED, "no service"),
            SosAction.SmsResult(id, "c2", SmsStatus.SENT),
        )
        assertEquals(SmsReceipt(SmsStatus.FAILED, "no service"), (failed as SosState.Active).sms.getValue("c1"))
        val t = reduce(failed, SosAction.RetryFailedSms(id))
        assertEquals(listOf(SosEffect.SendSms(id, "c1")), t.effects)
        assertEquals(SmsStatus.QUEUED, t.state.sms("c1"))
        assertEquals(SmsStatus.SENT, t.state.sms("c2"))
    }

    @Test fun resultForOtherSosId_isIgnored() {
        val active = raised()
        val t = reduce(active, SosAction.SmsResult("old-sos", "c1", SmsStatus.SENT))
        assertEquals(active, t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test fun partnerDeliveredAndAcked_recorded() {
        val s = raised().apply(SosAction.PartnerDelivered(id, 2_000L), SosAction.PartnerAcked(id, 3_000L)) as SosState.Active
        assertEquals(2_000L, s.deliveredToPartnerAt)
        assertEquals(3_000L, s.partnerAckAt)
    }

    @Test fun ackDoesNotImplyNothingElse() {
        val before = raised()
        val after = before.apply(SosAction.PartnerAcked(id, 3_000L)) as SosState.Active
        assertEquals(before.sms, after.sms)
        assertEquals(before.backend, after.backend)
        assertEquals(null, after.deliveredToPartnerAt)
    }

    @Test fun cancelActive_movesToCancelledWithWriteCancel() {
        val active = raised().apply(SosAction.SmsResult(id, "c1", SmsStatus.SENT))
        val t = reduce(active, SosAction.Cancel(id, 9_000L))
        val c = t.state as SosState.Cancelled
        assertEquals(id, c.sosId)
        assertEquals(9_000L, c.cancelledAt)
        assertEquals(SmsStatus.SENT, c.sms.getValue("c1").status)
        assertEquals(listOf(SosEffect.WriteCancel(id)), t.effects)
    }

    @Test fun cancelFromIdle_isNoOp() {
        val t = reduce(SosState.Idle, SosAction.Cancel(id, 9_000L))
        assertEquals(SosState.Idle, t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test fun cancelWrongId_isNoOp() {
        val active = raised()
        val t = reduce(active, SosAction.Cancel("other", 9_000L))
        assertEquals(active, t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test fun raiseAfterCancelled_startsNewActive() {
        val cancelled = raised().apply(SosAction.Cancel(id, 9_000L))
        val t = reduce(cancelled, SosAction.Raise("sos-2", 10_000L, targets))
        assertEquals("sos-2", (t.state as SosState.Active).sosId)
        assertEquals(SosEffect.WriteBackend("sos-2"), t.effects.first())
    }

    @Test fun smsResultAfterCancel_stillRecorded() {
        val cancelled = raised().apply(SosAction.Cancel(id, 9_000L))
        val s = cancelled.apply(SosAction.SmsResult(id, "c2", SmsStatus.DELIVERED))
        assertEquals(SmsStatus.DELIVERED, s.sms("c2"))
    }

    @Test fun resetReturnsToIdle() {
        assertEquals(SosState.Idle, raised().apply(SosAction.Reset))
    }
}
