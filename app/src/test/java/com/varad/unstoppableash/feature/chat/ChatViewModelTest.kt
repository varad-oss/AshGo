package com.varad.unstoppableash.feature.chat

import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.chat.ChatRepository
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.media.EncodedPhoto
import com.varad.unstoppableash.data.media.PhotoEncoder
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.domain.chat.BubbleStatus
import com.varad.unstoppableash.domain.chat.TimelineItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class ChatViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val base = 50_000_000L
    private val db = FakeRtdb(nowMs = base)
    private var photo: EncodedPhoto? = null

    private fun seedPair() = runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Alice"), "bob" to mapOf("name" to "Bob")))
    }

    private fun vm(): ChatViewModel {
        val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
        val clock = Clock { db.nowMs }
        return ChatViewModel(
            pairs = PairRepository(db, auth, clock), chat = ChatRepository(db), events = EventRepository(db, clock),
            presence = PresenceRepository(db), encoder = PhotoEncoder { photo }, rtdb = db, clock = clock, zone = ZoneId.of("UTC"),
        )
    }

    private fun TestScope.started(): ChatViewModel {
        seedPair()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        settle()
        return vm
    }

    private fun TestScope.settle(ms: Long = 1_000) { testScheduler.advanceTimeBy(ms); testScheduler.runCurrent() }
    private fun chatIds() = (db.read("pairs/p1/chat") as? Map<*, *>)?.keys.orEmpty()
    private fun ChatViewModel.bubbles() = state.value.items.filterIsInstance<TimelineItem.Bubble>()

    @Test fun showsThePartner() = runTest(main.dispatcher) {
        val s = started().state.value
        assertFalse(s.loading); assertEquals("Bob", s.partnerName)
    }

    @Test fun aDoubleTapSendsOnce() = runTest(main.dispatcher) {
        val vm = started()
        vm.send("hi"); vm.send("hi")
        settle()
        assertEquals(1, chatIds().size)
        assertEquals(listOf(BubbleStatus.SENT), vm.bubbles().map { it.status })
    }

    @Test fun blankIsNotSent() = runTest(main.dispatcher) {
        val vm = started()
        vm.send("   ")
        settle()
        assertTrue(chatIds().isEmpty())
    }

    @Test fun aFailedSendStaysAndRetryReusesItsId() = runTest(main.dispatcher) {
        val vm = started()
        db.failWrites("pairs/p1/chat", FailureReason.OFFLINE)
        vm.send("are you home?")
        settle()
        val failed = vm.bubbles().single()
        assertEquals(BubbleStatus.FAILED, failed.status); assertEquals("are you home?", failed.text)
        db.clearFailures()
        vm.retry(failed.id)
        settle()
        assertEquals(setOf(failed.id), chatIds())
        assertEquals(listOf(BubbleStatus.SENT), vm.bubbles().map { it.status })
    }

    @Test fun aPhotoTooLargeIsRefusedAndNothingIsWritten() = runTest(main.dispatcher) {
        val vm = started()
        photo = null
        vm.sendPhoto("content://picked/1")
        settle()
        assertEquals(ChatNotice.PhotoTooLarge, vm.state.value.notice)
        assertTrue(chatIds().isEmpty()); assertNull(db.read("pairs/p1/media"))
    }

    @Test fun aPhotoUploadsMediaThenTheMessage() = runTest(main.dispatcher) {
        val vm = started()
        photo = EncodedPhoto(ByteArray(10) { it.toByte() }, 4, 3)
        vm.sendPhoto("content://picked/2")
        settle()
        val id = chatIds().single() as String
        assertEquals(id, db.read("pairs/p1/chat/$id/mediaId"))
        assertNotNull(db.read("pairs/p1/media/$id/data"))
        assertFalse(vm.state.value.encodingPhoto)
    }

    @Test fun openingTheChatMarksMessagesAndEventsRead() = runTest(main.dispatcher) {
        db.set("pairs/p1/chat/m1", mapOf("from" to "bob", "text" to "hey", "at" to base - 10))
        db.set("pairs/p1/events/e1", mapOf("from" to "bob", "type" to "nudge", "kind" to "call_me", "at" to base - 5))
        val vm = started()
        assertNull("nothing is read while the chat isn't on screen", db.read("pairs/p1/profiles/alice/lastReadAt"))
        vm.setVisible(true)
        settle()
        assertEquals(base - 10, db.read("pairs/p1/profiles/alice/lastReadAt"))
        assertEquals(base, db.read("pairs/p1/events/e1/seenBy/alice"))
    }

    @Test fun seenComesFromThePartnersReadMarker() = runTest(main.dispatcher) {
        val vm = started()
        vm.send("hello"); settle()
        assertEquals(BubbleStatus.SENT, vm.bubbles().single().status)
        db.set("pairs/p1/profiles/bob/lastReadAt", base + 1); settle()
        assertEquals(BubbleStatus.SEEN, vm.bubbles().single().status)
    }

    @Test fun olderMessagesLoadOnDemand() = runTest(main.dispatcher) {
        seedPair()
        repeat(60) { i -> db.set("pairs/p1/chat/m%02d".format(i), mapOf("from" to "bob", "text" to "t$i", "at" to base - 1_000 + i)) }
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        settle()
        assertEquals(ChatViewModel.PAGE, vm.bubbles().size); assertTrue(vm.state.value.hasMore)
        vm.loadOlder(); settle()
        assertEquals(60, vm.bubbles().size); assertFalse(vm.state.value.hasMore)
    }

    @Test fun nudgesAreLimitedToOneEvery30Seconds() = runTest(main.dispatcher) {
        val vm = started()
        vm.nudge("thinking_of_you"); settle(100)
        assertEquals(ChatNotice.NudgeSent, vm.state.value.notice)
        vm.nudge("call_me"); settle(100)
        assertEquals(ChatNotice.Failed(FailureReason.RATE_LIMITED), vm.state.value.notice)
        assertEquals(1, (db.read("pairs/p1/events") as Map<*, *>).size)
    }

    @Test fun anUnconfirmedSendShowsSendingNotSent() = runTest(main.dispatcher) {
        val vm = started()
        db.ackHeld.value = true
        vm.send("on my way"); settle()
        assertEquals("the listener already has it, but the server hasn't confirmed", BubbleStatus.SENDING, vm.bubbles().single().status)
        db.ackHeld.value = false; settle()
        assertEquals(BubbleStatus.SENT, vm.bubbles().single().status)
    }

    @Test fun offlineTheComposerStaysUsable() = runTest(main.dispatcher) {
        val vm = started()
        db.ackHeld.value = true
        vm.send("one"); settle()
        assertFalse("composer not locked by an unconfirmed send", vm.state.value.sending)
        vm.send("two"); settle()
        assertEquals(2, chatIds().size)
    }

    @Test fun readMarkerCoversOnlyWhatWasShown() = runTest(main.dispatcher) {
        db.set("pairs/p1/chat/m1", mapOf("from" to "bob", "text" to "hey", "at" to base - 10))
        val vm = started()
        vm.setVisible(true); settle()
        assertEquals("the newest shown message, not server time", base - 10, db.read("pairs/p1/profiles/alice/lastReadAt"))
    }

    @Test fun thePartnerReadingDoesNotRebuildTheChat() = runTest(main.dispatcher) {
        val vm = started()
        settle()
        val before = db.subscriptions
        db.set("pairs/p1/profiles/bob/lastReadAt", base + 1); settle()
        assertEquals("no listener torn down and rebuilt", before, db.subscriptions)
        assertFalse(vm.state.value.loading)
    }

    @Test fun aPhotoIsNeverStoredWithoutItsMessage() = runTest(main.dispatcher) {
        val vm = started()
        photo = EncodedPhoto(ByteArray(10) { it.toByte() }, 4, 3)
        db.ackHeld.value = true
        vm.sendPhoto("content://picked/3"); settle()
        val id = chatIds().singleOrNull() as String?
        assertNotNull("message issued together with its media, not after the media ack", id)
        assertNotNull(db.read("pairs/p1/media/$id/data"))
    }
}
