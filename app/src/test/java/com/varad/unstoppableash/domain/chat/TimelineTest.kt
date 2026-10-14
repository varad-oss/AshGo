package com.varad.unstoppableash.domain.chat

import com.varad.unstoppableash.domain.model.ChatMessage
import com.varad.unstoppableash.domain.model.EventType
import com.varad.unstoppableash.domain.model.OrbitEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimelineTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun msg(id: String, from: String, at: Long, text: String = id) = ChatMessage(id, from, text, null, at)

    private fun build(
        messages: List<ChatMessage> = emptyList(),
        events: List<OrbitEvent> = emptyList(),
        outbox: List<OutboxEntry> = emptyList(),
        partnerReadAt: Long? = null,
    ) = buildTimeline(messages, events, outbox, myUid = "me", partnerLastReadAt = partnerReadAt, zone = zone)

    @Test fun daySeparatorsFollowTheLocalDate() {
        // 23:50 and 00:10 IST are the same UTC day but different local days.
        val items = build(listOf(msg("a", "me", at(13, 23, 50)), msg("b", "you", at(14, 0, 10))))
        val days = items.filterIsInstance<TimelineItem.DaySeparator>().map { it.date }
        assertEquals(listOf(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14)), days)
    }

    @Test fun timeShowsOnceAtTheEndOfAGroup() {
        val items = build(listOf(msg("a", "me", at(13, 10, 0)), msg("b", "me", at(13, 10, 4)), msg("c", "me", at(13, 10, 10)), msg("d", "you", at(13, 10, 11))))
        val shown = items.filterIsInstance<TimelineItem.Bubble>().associate { it.id to it.showTime }
        assertEquals(mapOf("a" to false, "b" to true, "c" to true, "d" to true), shown)
    }

    @Test fun anOutboxEntryAlreadyInTheListenerIsShownOnceAndStillUnconfirmed() {
        // The SDK echoes local writes into listeners before the server acks; only the outbox knows the truth.
        val items = build(
            messages = listOf(msg("x", "me", at(13, 10, 0))),
            outbox = listOf(OutboxEntry("x", "x", null, at(13, 10, 0), OutboxStatus.SENDING)),
            partnerReadAt = Long.MAX_VALUE,
        )
        assertEquals(1, items.filterIsInstance<TimelineItem.Bubble>().size)
        assertEquals(BubbleStatus.SENDING, items.filterIsInstance<TimelineItem.Bubble>().single().status)
    }

    @Test fun pendingAndFailedSendsSitAtTheEnd() {
        val items = build(
            messages = listOf(msg("a", "you", at(13, 10, 0))),
            outbox = listOf(OutboxEntry("p", "hello", null, at(13, 9, 0), OutboxStatus.FAILED)),
        )
        val last = items.last() as TimelineItem.Bubble
        assertEquals("p", last.id); assertEquals(BubbleStatus.FAILED, last.status); assertTrue(last.mine)
    }

    @Test fun seenComesOnlyFromThePartnersReadMarker() {
        val m = msg("a", "me", at(13, 10, 0))
        assertEquals(BubbleStatus.SENT, (build(listOf(m)).last() as TimelineItem.Bubble).status)
        assertEquals(BubbleStatus.SENT, (build(listOf(m), partnerReadAt = m.at - 1).last() as TimelineItem.Bubble).status)
        assertEquals(BubbleStatus.SEEN, (build(listOf(m), partnerReadAt = m.at).last() as TimelineItem.Bubble).status)
        val theirs = msg("b", "you", at(13, 10, 1))
        assertEquals("partner's messages carry no send status", BubbleStatus.NONE, (build(listOf(theirs), partnerReadAt = Long.MAX_VALUE).last() as TimelineItem.Bubble).status)
    }

    @Test fun eventsAreInterleavedByTime() {
        val e = OrbitEvent("e", "you", EventType.NUDGE, "call_me", null, at(13, 10, 5), emptyMap(), emptyMap())
        val items = build(listOf(msg("a", "me", at(13, 10, 0)), msg("b", "you", at(13, 10, 9))), listOf(e))
        assertEquals(listOf("sep:2026-10-13", "m:a", "e:e", "m:b"), items.map { it.key })
        assertTrue("an event chip ends a bubble group", (items[1] as TimelineItem.Bubble).showTime)
    }

    @Test fun keysAreUnique() {
        val items = build(listOf(msg("a", "me", at(13, 10, 0))), outbox = listOf(OutboxEntry("b", "x", null, at(13, 10, 1), OutboxStatus.SENDING)))
        assertEquals(items.size, items.map { it.key }.toSet().size)
    }
}
