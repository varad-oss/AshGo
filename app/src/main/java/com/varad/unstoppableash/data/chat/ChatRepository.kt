package com.varad.unstoppableash.data.chat

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.result.then
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.firebase.string
import com.varad.unstoppableash.domain.media.ImageBudget
import com.varad.unstoppableash.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Base64

/** Text and photo messages. Callers keep a client-generated id per send so a retry can never duplicate. */
class ChatRepository(private val db: RtdbClient) {

    fun messages(pairId: String, limit: Int = 100): Flow<List<ChatMessage>> =
        db.observeLast(DbPaths.chat(pairId), limit, orderByChild = "at").map(::messagesOf).catch { emit(emptyList()) }

    fun newMessageId(pairId: String): String = db.newKey(DbPaths.chat(pairId))

    suspend fun send(pairId: String, uid: String, id: String, text: String): Outcome<Unit> {
        val body = text.trim()
        if (body.isEmpty() || body.length > MAX_TEXT) return Outcome.Failure(FailureReason.INVALID, "text")
        if (alreadySent(pairId, uid, id)) return Outcome.Success(Unit)
        return db.set(DbPaths.chatMessage(pairId, id), mapOf("from" to uid, "text" to body, "at" to db.serverTimestamp))
    }

    /** [jpeg] must already be downscaled/compressed with ImageBudget; anything over the hard limit is refused. */
    suspend fun sendPhoto(pairId: String, uid: String, id: String, jpeg: ByteArray, width: Int, height: Int): Outcome<Unit> {
        if (jpeg.isEmpty() || jpeg.size > ImageBudget.HARD_LIMIT_BYTES) return Outcome.Failure(FailureReason.INVALID, "photo size")
        if (alreadySent(pairId, uid, id)) return Outcome.Success(Unit)
        val mediaId = db.newKey(DbPaths.pair(pairId) + "/media")
        val data = Base64.getEncoder().encodeToString(jpeg)
        return db.set(DbPaths.media(pairId, mediaId), mapOf("from" to uid, "data" to data, "w" to width, "h" to height, "at" to db.serverTimestamp))
            .then { db.set(DbPaths.chatMessage(pairId, id), mapOf("from" to uid, "mediaId" to mediaId, "at" to db.serverTimestamp)) }
    }

    fun media(pairId: String, mediaId: String): Flow<String?> =
        db.observe(DbPaths.media(pairId, mediaId)).map { (it as? Map<*, *>)?.string("data") }.catch { emit(null) }

    suspend fun markRead(pairId: String, uid: String): Outcome<Unit> =
        db.set(DbPaths.profile(pairId, uid) + "/lastReadAt", db.serverTimestamp)

    fun unreadCount(pairId: String, myUid: String): Flow<Int> = combine(
        messages(pairId),
        db.observe(DbPaths.profile(pairId, myUid) + "/lastReadAt").catch { emit(null) },
    ) { messages, lastRead ->
        val since = (lastRead as? Number)?.toLong() ?: 0L
        messages.count { it.from != myUid && it.at > since }
    }

    private suspend fun alreadySent(pairId: String, uid: String, id: String): Boolean {
        val existing = (db.get(DbPaths.chatMessage(pairId, id)) as? Outcome.Success)?.value as? Map<*, *>
        return existing?.string("from") == uid
    }

    private fun messagesOf(value: Any?): List<ChatMessage> =
        (value as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            ChatMessage(
                id = k as String,
                from = m.string("from") ?: return@mapNotNull null,
                text = m.string("text"),
                mediaId = m.string("mediaId"),
                at = m.long("at") ?: return@mapNotNull null,
            ).takeIf { it.text != null || it.mediaId != null }
        }?.sortedBy { it.at }.orEmpty()

    private companion object {
        const val MAX_TEXT = 2000
    }
}
