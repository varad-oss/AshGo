package com.varad.unstoppableash.domain.model

enum class SosRecordState { ACTIVE, CANCELLED }

/** An SOS as stored in the pair. Contact numbers never appear here — only counts. */
data class SosRecord(
    val id: String,
    val from: String,
    val state: SosRecordState,
    val at: Long,
    val lat: Double?,
    val lng: Double?,
    val locAt: Long?,
    val accuracyM: Float?,
    val battery: Int?,
    val smsSent: Int?,
    val smsFailed: Int?,
    val cancelledAt: Long?,
    val deliveredTo: Map<String, Long>,
    val ackBy: Map<String, Long>,
)
