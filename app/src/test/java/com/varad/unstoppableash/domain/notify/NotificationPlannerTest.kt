package com.varad.unstoppableash.domain.notify

import com.varad.unstoppableash.domain.model.ChatMessage
import com.varad.unstoppableash.domain.model.EventType
import com.varad.unstoppableash.domain.model.OrbitEvent
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.SosRecordState
import com.varad.unstoppableash.domain.trips.OverduePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPlannerTest {
    private val now = 10_000_000L
    private val me = "alice"; private val bob = "bob"
    private fun msg(id: String, from: String = bob, at: Long = now - 1_000) = ChatMessage(id, from, "hi $id", null, at)
    private fun event(id: String, type: EventType, from: String = bob, at: Long = now - 1_000) = OrbitEvent(id, from, type, "call_me", null, at, emptyMap(), emptyMap())
    private fun sos(id: String, state: SosRecordState = SosRecordState.ACTIVE, from: String = bob, at: Long = now - 1_000) =
        SosRecord(id, from, state, at, null, null, null, null, null, null, null, null, emptyMap(), emptyMap())

    private fun plan(
        chat: List<ChatMessage> = emptyList(), events: List<OrbitEvent> = emptyList(), sos: List<SosRecord> = emptyList(),
        partnerTrip: TripAlert? = null, myTrip: TripAlert? = null, toggles: Toggles = Toggles(), seen: Map<String, String> = emptyMap(),
    ) = NotificationPlanner.plan(PlannerInput(me, chat, events, sos, partnerTrip, myTrip, toggles, seen, now))

    @Test fun aPartnerMessageNotifiesOnce() {
        val first = plan(chat = listOf(msg("m1")))
        assertEquals(listOf(NotificationKind.CHAT), first.post.map { it.kind })
        val again = plan(chat = listOf(msg("m1")), seen = first.seen)
        assertTrue("never twice", again.post.isEmpty())
    }

    @Test fun myOwnItemsNeverNotify() {
        assertTrue(plan(chat = listOf(msg("m1", from = me)), events = listOf(event("e1", EventType.NUDGE, from = me)), sos = listOf(sos("s1", from = me))).post.isEmpty())
    }

    @Test fun oldHistoryIsNotReplayedOnFirstRun() {
        assertTrue(plan(chat = listOf(msg("m0", at = now - 60 * 60_000))).post.isEmpty())
    }

    @Test fun severalMessagesCollapseIntoOneWithACount() {
        val p = plan(chat = listOf(msg("m1"), msg("m2"), msg("m3")))
        assertEquals(1, p.post.size); assertEquals(3, p.post.single().count)
    }

    @Test fun nudgesAndCheckInsHaveTheirOwnChannels() {
        val p = plan(events = listOf(event("e1", EventType.NUDGE), event("e2", EventType.CHECKIN)))
        assertEquals(setOf(NotificationKind.NUDGE, NotificationKind.CHECKIN), p.post.map { it.kind }.toSet())
    }

    @Test fun togglesSilenceEverythingButSafety() {
        val off = Toggles(chat = false, nudges = false, checkins = false)
        val p = plan(chat = listOf(msg("m1")), events = listOf(event("e1", EventType.NUDGE)), sos = listOf(sos("s1")), partnerTrip = TripAlert("t1", OverduePhase.ALERT_PARTNER), toggles = off)
        assertEquals(setOf(NotificationKind.PARTNER_SOS, NotificationKind.PARTNER_OVERDUE), p.post.map { it.kind }.toSet())
    }

    @Test fun anActiveSosAlertsEvenIfOldAndACancelledOneCancelsIt() {
        val raised = plan(sos = listOf(sos("s1", at = now - 60 * 60_000)))
        assertEquals("an SOS still active is never 'history'", listOf(NotificationKind.PARTNER_SOS), raised.post.map { it.kind })
        val cancelled = plan(sos = listOf(sos("s1", SosRecordState.CANCELLED)), seen = raised.seen)
        assertTrue(cancelled.post.isEmpty()); assertEquals(listOf("sos:s1"), cancelled.cancel)
    }

    @Test fun overdueAlertsOncePerDeadline() {
        val first = plan(partnerTrip = TripAlert("bob-trip@100", OverduePhase.ALERT_PARTNER), myTrip = TripAlert("my-trip@200", OverduePhase.ASK_TRAVELLER))
        assertEquals(setOf(NotificationKind.PARTNER_OVERDUE, NotificationKind.MY_OVERDUE), first.post.map { it.kind }.toSet())
        val again = plan(partnerTrip = TripAlert("bob-trip@100", OverduePhase.ALERT_PARTNER), myTrip = TripAlert("my-trip@200", OverduePhase.ASK_TRAVELLER), seen = first.seen)
        assertTrue(again.post.isEmpty())
        val extended = plan(myTrip = TripAlert("my-trip@1100", OverduePhase.ASK_TRAVELLER), seen = first.seen)
        assertEquals(listOf(NotificationKind.MY_OVERDUE), extended.post.map { it.kind })
    }

    @Test fun everyKindHasARegisteredChannel() {
        NotificationKind.entries.forEach { assertTrue("$it", it.channel in Channel.entries) }
        assertEquals(Channel.entries.size, Channel.entries.map { it.id }.toSet().size)
    }
}
