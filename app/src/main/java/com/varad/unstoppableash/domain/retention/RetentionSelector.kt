package com.varad.unstoppableash.domain.retention

import com.varad.unstoppableash.domain.time.Durations.DAY

enum class RetainedKind { CHAT, MEDIA, EVENT, TRIP, SOS }

data class RetentionItem(
    val id: String,
    val kind: RetainedKind,
    val at: Long,
    val endedAt: Long? = null,
    val sosActive: Boolean = false,
    val cancelledAt: Long? = null,
)

/** Retention defaults from PRODUCT_DECISIONS D-14. Active trips and unresolved SOS are never selected. */
object RetentionSelector {
    const val CHAT_MS = 7 * DAY
    const val MEDIA_MS = 3 * DAY
    const val EVENT_MS = 7 * DAY
    const val TRIP_AFTER_END_MS = DAY
    const val SOS_AFTER_CANCEL_MS = 30 * DAY

    fun selectExpired(items: List<RetentionItem>, now: Long): List<String> =
        items.filter { isExpired(it, now) }.map { it.id }

    private fun isExpired(item: RetentionItem, now: Long): Boolean = when (item.kind) {
        RetainedKind.CHAT -> item.at < now - CHAT_MS
        RetainedKind.MEDIA -> item.at < now - MEDIA_MS
        RetainedKind.EVENT -> item.at < now - EVENT_MS
        RetainedKind.TRIP -> item.endedAt != null && item.endedAt < now - TRIP_AFTER_END_MS
        RetainedKind.SOS -> !item.sosActive && item.cancelledAt != null && item.cancelledAt < now - SOS_AFTER_CANCEL_MS
    }
}
