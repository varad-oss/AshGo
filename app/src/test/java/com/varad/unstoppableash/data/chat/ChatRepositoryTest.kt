package com.varad.unstoppableash.data.chat

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.domain.media.ImageBudget
import com.varad.unstoppableash.domain.model.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ChatRepositoryTest {
    private val db = FakeRtdb(nowMs = 100_000L)
    private val chat = ChatRepository(db)
    private val p = "p1"

    @Test fun messagesAreMappedAndOrderedByTime() = runTest {
        db.set("pairs/p1/chat", mapOf(
            "m2" to mapOf("from" to "bob", "text" to "second", "at" to 20L),
            "m1" to mapOf("from" to "alice", "text" to "first", "at" to 10L),
            "bad" to mapOf("text" to "no sender", "at" to 30L),
        ))
        assertEquals(
            listOf(ChatMessage("m1", "alice", "first", null, 10L), ChatMessage("m2", "bob", "second", null, 20L)),
            chat.messages(p).first(),
        )
    }

    @Test fun sendingTheSameIdTwiceWritesOnce() = runTest {
        val id = chat.newMessageId(p)
        assertTrue(chat.send(p, "alice", id, "hi") is Outcome.Success)
        assertTrue(chat.send(p, "alice", id, "hi") is Outcome.Success)
        assertEquals(1, db.writeLog.count { it.startsWith("set pairs/p1/chat/") })
        assertEquals(mapOf("from" to "alice", "text" to "hi", "at" to 100_000L), db.read("pairs/p1/chat/$id"))
    }

    @Test fun blankOrOversizedTextIsRejected() = runTest {
        assertEquals(FailureReason.INVALID, (chat.send(p, "alice", "m", "   ") as Outcome.Failure).reason)
        assertEquals(FailureReason.INVALID, (chat.send(p, "alice", "m", "x".repeat(2001)) as Outcome.Failure).reason)
        assertTrue(db.writeLog.isEmpty())
    }

    @Test fun photoOverHardLimitIsRejectedBeforeAnyWrite() = runTest {
        val r = chat.sendPhoto(p, "alice", "m", ByteArray(ImageBudget.HARD_LIMIT_BYTES + 1), 10, 10)
        assertEquals(FailureReason.INVALID, (r as Outcome.Failure).reason)
        assertTrue(db.writeLog.isEmpty())
    }

    @Test fun photoGoesToMediaThenMessage() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(chat.sendPhoto(p, "alice", "m9", bytes, 640, 480) is Outcome.Success)
        val msg = db.read("pairs/p1/chat/m9") as Map<*, *>
        val mediaId = msg["mediaId"] as String
        assertEquals("m9", mediaId)
        assertEquals(mapOf("from" to "alice", "data" to Base64.getEncoder().encodeToString(bytes), "w" to 640L, "h" to 480L, "at" to 100_000L), db.read("pairs/p1/media/$mediaId"))
        assertEquals(listOf("set pairs/p1/media/$mediaId", "set pairs/p1/chat/m9"), db.writeLog)
        assertEquals(Base64.getEncoder().encodeToString(bytes), chat.media(p, mediaId).first())
    }

    @Test fun photoRetryReusesMediaIdAndSkipsRewrite() = runTest {
        db.failWrites("pairs/p1/chat", FailureReason.OFFLINE)
        assertTrue(chat.sendPhoto(p, "alice", "m7", byteArrayOf(9), 1, 1) is Outcome.Failure)
        db.clearFailures()
        assertTrue(chat.sendPhoto(p, "alice", "m7", byteArrayOf(9), 1, 1) is Outcome.Success)
        assertEquals("m7", (db.read("pairs/p1/chat/m7") as Map<*, *>)["mediaId"])
        assertEquals(1, db.writeLog.count { it.startsWith("set pairs/p1/media/") })
        assertEquals(setOf("m7"), (db.read("pairs/p1/media") as Map<*, *>).keys)
    }

    @Test fun markReadUsesServerTime() = runTest {
        chat.markRead(p, "alice")
        assertEquals(100_000L, db.read("pairs/p1/profiles/alice/lastReadAt"))
    }

    @Test fun unreadCountsPartnerMessagesAfterLastRead() = runTest {
        db.set("pairs/p1/chat", mapOf(
            "a" to mapOf("from" to "bob", "text" to "1", "at" to 10L),
            "b" to mapOf("from" to "alice", "text" to "2", "at" to 20L),
            "c" to mapOf("from" to "bob", "text" to "3", "at" to 30L),
        ))
        assertEquals(2, chat.unreadCount(p, "alice").first())
        db.set("pairs/p1/profiles/alice/lastReadAt", 15L)
        assertEquals(1, chat.unreadCount(p, "alice").first())
        db.set("pairs/p1/profiles/alice/lastReadAt", 30L)
        assertEquals(0, chat.unreadCount(p, "alice").first())
    }

    @Test fun partnersReadMarkerIsObservable() = runTest {
        assertEquals(null, chat.lastReadAt(p, "bob").first())
        db.set("pairs/p1/profiles/bob/lastReadAt", 42L)
        assertEquals(42L, chat.lastReadAt(p, "bob").first())
    }

    @Test fun aLargerLimitReachesOlderMessages() = runTest {
        repeat(5) { i -> db.set("pairs/p1/chat/m$i", mapOf("from" to "bob", "text" to "t$i", "at" to (i + 1).toLong())) }
        assertEquals(listOf("m3", "m4"), chat.messages(p, limit = 2).first().map { it.id })
        assertEquals(listOf("m1", "m2", "m3", "m4"), chat.messages(p, limit = 4).first().map { it.id })
    }
}
