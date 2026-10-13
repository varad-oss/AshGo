package com.varad.unstoppableash.domain.sos

import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.SmsReceipt
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SosAction
import com.varad.unstoppableash.domain.model.SosEffect
import com.varad.unstoppableash.domain.model.SosState
import com.varad.unstoppableash.domain.model.SosTransition

/**
 * Pure SOS reducer. Each fact (backend write, partner delivery, partner ack,
 * per-contact SMS) is tracked separately so the UI never claims more than is known.
 */
object SosStateMachine {

    fun reduce(state: SosState, action: SosAction): SosTransition = when (action) {
        is SosAction.Raise -> raise(state, action)
        SosAction.Reset -> SosTransition(SosState.Idle)
        is SosAction.Cancel -> cancel(state, action)
        is SosAction.SmsResult -> withSms(state, action.sosId) { sms ->
            val current = sms[action.contactId] ?: return@withSms sms
            val next = SmsReceipt(action.status, action.failure)
            if (supersedes(next.status, current.status)) sms + (action.contactId to next) else sms
        }
        is SosAction.RetryFailedSms -> retry(state, action)
        is SosAction.BackendSaved -> withActive(state, action.sosId) {
            it.copy(backend = BackendStatus.SAVED, serverAt = action.serverAt)
        }
        is SosAction.BackendFailed -> withActive(state, action.sosId) { it.copy(backend = BackendStatus.FAILED) }
        is SosAction.PartnerDelivered -> withActive(state, action.sosId) { it.copy(deliveredToPartnerAt = action.at) }
        is SosAction.PartnerAcked -> withActive(state, action.sosId) { it.copy(partnerAckAt = action.at) }
    }

    private fun raise(state: SosState, action: SosAction.Raise): SosTransition {
        if (state is SosState.Active) return SosTransition(state)
        val active = SosState.Active(
            sosId = action.sosId,
            raisedAt = action.now,
            backend = BackendStatus.PENDING,
            serverAt = null,
            deliveredToPartnerAt = null,
            partnerAckAt = null,
            sms = action.targets.associate { it.contactId to SmsReceipt(SmsStatus.QUEUED) },
        )
        val effects = listOf(SosEffect.WriteBackend(action.sosId)) +
            action.targets.map { SosEffect.SendSms(action.sosId, it.contactId) }
        return SosTransition(active, effects)
    }

    private fun cancel(state: SosState, action: SosAction.Cancel): SosTransition {
        if (state !is SosState.Active || state.sosId != action.sosId) return SosTransition(state)
        return SosTransition(
            SosState.Cancelled(state.sosId, action.now, state.sms),
            listOf(SosEffect.WriteCancel(state.sosId)),
        )
    }

    private fun retry(state: SosState, action: SosAction.RetryFailedSms): SosTransition {
        if (state !is SosState.Active || state.sosId != action.sosId) return SosTransition(state)
        val failed = state.sms.filterValues { it.status == SmsStatus.FAILED }.keys.toList()
        val sms = state.sms + failed.associateWith { SmsReceipt(SmsStatus.QUEUED) }
        return SosTransition(state.copy(sms = sms), failed.map { SosEffect.SendSms(state.sosId, it) })
    }

    private inline fun withActive(state: SosState, sosId: String, f: (SosState.Active) -> SosState.Active) =
        SosTransition(if (state is SosState.Active && state.sosId == sosId) f(state) else state)

    private inline fun withSms(
        state: SosState,
        sosId: String,
        f: (Map<String, SmsReceipt>) -> Map<String, SmsReceipt>,
    ): SosTransition = SosTransition(
        when {
            state is SosState.Active && state.sosId == sosId -> state.copy(sms = f(state.sms))
            state is SosState.Cancelled && state.sosId == sosId -> state.copy(sms = f(state.sms))
            else -> state
        },
    )

    /** Delivery facts only move forward; FAILED can never erase a confirmed DELIVERED. */
    private fun supersedes(incoming: SmsStatus, current: SmsStatus): Boolean =
        if (incoming == SmsStatus.FAILED) current == SmsStatus.QUEUED || current == SmsStatus.SENT
        else rank(incoming) > rank(current)

    private fun rank(s: SmsStatus) = when (s) {
        SmsStatus.QUEUED, SmsStatus.FAILED -> 0
        SmsStatus.SENT -> 1
        SmsStatus.DELIVERED -> 2
    }
}
