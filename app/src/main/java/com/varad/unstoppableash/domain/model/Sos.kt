package com.varad.unstoppableash.domain.model

/** An emergency contact, by local id only; numbers never enter the domain or the backend. */
data class SmsTarget(val contactId: String)

enum class SmsStatus { QUEUED, SENT, DELIVERED, FAILED }

data class SmsReceipt(val status: SmsStatus, val failure: String? = null)

enum class BackendStatus { PENDING, SAVED, FAILED }

sealed interface SosState {
    data object Idle : SosState

    data class Active(
        val sosId: String,
        val raisedAt: Long,
        val backend: BackendStatus,
        val serverAt: Long?,
        val deliveredToPartnerAt: Long?,
        val partnerAckAt: Long?,
        val sms: Map<String, SmsReceipt>,
    ) : SosState

    data class Cancelled(val sosId: String, val cancelledAt: Long, val sms: Map<String, SmsReceipt>) : SosState
}

sealed interface SosAction {
    data class Raise(val sosId: String, val now: Long, val targets: List<SmsTarget>) : SosAction
    data class BackendSaved(val sosId: String, val serverAt: Long) : SosAction
    data class BackendFailed(val sosId: String) : SosAction
    /** One aggregated (worst-of-parts) result per message attempt; never one per multipart part. */
    data class SmsResult(val sosId: String, val contactId: String, val status: SmsStatus, val failure: String? = null) : SosAction
    data class PartnerDelivered(val sosId: String, val at: Long) : SosAction
    data class PartnerAcked(val sosId: String, val at: Long) : SosAction
    data class RetryFailedSms(val sosId: String) : SosAction
    data class Cancel(val sosId: String, val now: Long) : SosAction
    data object Reset : SosAction
}

sealed interface SosEffect {
    data class WriteBackend(val sosId: String) : SosEffect
    data class SendSms(val sosId: String, val contactId: String) : SosEffect
    data class WriteCancel(val sosId: String) : SosEffect
}

data class SosTransition(val state: SosState, val effects: List<SosEffect> = emptyList())
