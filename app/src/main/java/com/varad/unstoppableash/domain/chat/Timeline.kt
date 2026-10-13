package com.varad.unstoppableash.domain.chat

import com.varad.unstoppableash.domain.model.ChatMessage
import com.varad.unstoppableash.domain.model.OrbitEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class OutboxStatus { SENDING, FAILED }

/** A message I composed that the server hasn't confirmed yet. [id] is the client id the send will reuse on retry. */
data class OutboxEntry(val id: String, val text: String?, val mediaId: String?, val createdAt: Long, val status: OutboxStatus)

enum class BubbleStatus { NONE, SENDING, SENT, SEEN, FAILED }

sealed interface TimelineItem {
    val key: String

    data class DaySeparator(val date: LocalDate) : TimelineItem { override val key = "sep:$date" }

    data class Bubble(
        val id: String,
        val mine: Boolean,
        val text: String?,
        val mediaId: String?,
        val at: Long,
        val status: BubbleStatus,
        val showTime: Boolean,
    ) : TimelineItem { override val key = "m:$id" }

    data class EventChip(val event: OrbitEvent, val mine: Boolean) : TimelineItem { override val key = "e:${event.id}" }
}

fun buildTimeline(
    messages: List<ChatMessage>,
    events: List<OrbitEvent>,
    outbox: List<OutboxEntry>,
    myUid: String,
    partnerLastReadAt: Long?,
    zone: ZoneId,
): List<TimelineItem> {
    val onServer = messages.mapTo(HashSet()) { it.id }
    val server: List<Pair<Long, TimelineItem>> =
        messages.map { m ->
            val mine = m.from == myUid
            val status = when {
                !mine -> BubbleStatus.NONE
                partnerLastReadAt != null && partnerLastReadAt >= m.at -> BubbleStatus.SEEN
                else -> BubbleStatus.SENT
            }
            m.at to TimelineItem.Bubble(m.id, mine, m.text, m.mediaId, m.at, status, showTime = true)
        } + events.map { it.at to TimelineItem.EventChip(it, mine = it.from == myUid) }
    // My unconfirmed sends always sit at the end, in the order I wrote them, so they never jump around.
    val pending = outbox.filter { it.id !in onServer }.sortedBy { it.createdAt }.map { o ->
        o.createdAt to TimelineItem.Bubble(
            o.id, mine = true, o.text, o.mediaId, o.createdAt,
            if (o.status == OutboxStatus.FAILED) BubbleStatus.FAILED else BubbleStatus.SENDING, showTime = true,
        )
    }
    val ordered = server.sortedBy { it.first } + pending

    val out = ArrayList<TimelineItem>(ordered.size + 4)
    var lastDate: LocalDate? = null
    ordered.forEachIndexed { i, (at, item) ->
        val date = localDate(at, zone)
        if (lastDate == null || date.isAfter(lastDate)) {
            out += TimelineItem.DaySeparator(date)
            lastDate = date
        }
        out += if (item is TimelineItem.Bubble) item.copy(showTime = !continuesGroup(item, ordered.getOrNull(i + 1)?.second, zone)) else item
    }
    return out
}

/** A group is consecutive bubbles from one sender less than [GROUP_GAP_MS] apart; time shows under its last bubble. */
private fun continuesGroup(bubble: TimelineItem.Bubble, next: TimelineItem?, zone: ZoneId): Boolean =
    next is TimelineItem.Bubble && next.mine == bubble.mine && next.at - bubble.at in 0 until GROUP_GAP_MS &&
        localDate(next.at, zone) == localDate(bubble.at, zone)

private fun localDate(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()

private const val GROUP_GAP_MS = 5 * 60_000L
