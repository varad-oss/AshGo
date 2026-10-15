package com.varad.unstoppableash.domain.model

enum class EventType { CHECKIN, NUDGE }

/** A check-in or nudge. Receipts are facts the partner's phone wrote, never assumed. */
data class OrbitEvent(
    val id: String,
    val from: String,
    val type: EventType,
    val kind: String,
    val text: String?,
    val at: Long,
    val deliveredTo: Map<String, Long>,
    val seenBy: Map<String, Long>,
)
