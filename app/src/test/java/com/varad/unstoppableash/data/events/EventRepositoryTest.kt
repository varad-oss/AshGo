package com.varad.unstoppableash.data.events

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.domain.model.EventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRepositoryTest {
    private val db = FakeRtdb(nowMs = 100_000L)
    private val events = EventRepository(db, Clock { db.nowMs })
    private val p = "p1"

    @Test fun checkInWritesTypedEvent() = runTest {
        val id = (events.checkIn(p, "alice", "home_safe", text = null) as Outcome.Success).value
        assertEquals(mapOf("from" to "alice", "type" to "checkin", "kind" to "home_safe", "at" to 100_000L), db.read("pairs/p1/events/$id"))
        val e = events.events(p).first().single()
        assertEquals(EventType.CHECKIN, e.type); assertEquals("home_safe", e.kind); assertEquals(emptyMap<String, Long>(), e.seenBy)
    }

    @Test fun nudgeIsRateLimitedToOnePerThirtySeconds() = runTest {
        assertTrue(events.nudge(p, "alice", "thinking_of_you") is Outcome.Success)
        db.nowMs += 29_999
        assertEquals(FailureReason.RATE_LIMITED, (events.nudge(p, "alice", "call_me") as Outcome.Failure).reason)
        db.nowMs += 1
        assertTrue(events.nudge(p, "alice", "call_me") is Outcome.Success)
    }

    @Test fun receiptsOnlyForPartnerEventsAndOnlyOnce() = runTest {
        val mine = (events.checkIn(p, "alice", "home_safe", null) as Outcome.Success).value
        val theirs = (events.checkIn(p, "bob", "on_my_way", null) as Outcome.Success).value
        val all = events.events(p).first()
        events.markSeen(p, all.first { it.id == mine }, "alice")
        assertNull(db.read("pairs/p1/events/$mine/seenBy"))
        val bobs = all.first { it.id == theirs }
        events.markDelivered(p, bobs, "alice")
        events.markSeen(p, bobs, "alice")
        assertEquals(mapOf("alice" to 100_000L), db.read("pairs/p1/events/$theirs/seenBy"))
        assertEquals(mapOf("alice" to 100_000L), db.read("pairs/p1/events/$theirs/deliveredTo"))
        val seenAgain = events.events(p).first().first { it.id == theirs }
        db.writeLog.clear()
        events.markSeen(p, seenAgain, "alice")
        assertTrue(db.writeLog.isEmpty())
    }

    @Test fun checkInTextIsTrimmedAndLimited() = runTest {
        assertEquals(FailureReason.INVALID, (events.checkIn(p, "alice", "custom", "x".repeat(61)) as Outcome.Failure).reason)
    }
}
