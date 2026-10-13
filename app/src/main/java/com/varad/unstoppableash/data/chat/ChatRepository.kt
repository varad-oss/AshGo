package com.varad.unstoppableash.data.chat

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.result.then
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.orIfTrue
import com.varad.unstoppableash.data.firebase.resilient
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.firebase.string
import com.varad.unstoppableash.domain.media.ImageBudget
import com.varad.unstoppableash.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Base64

/** Text and photo messages. Callers keep a client-generated id per send so a retry can never duplicate. */
class ChatRepository(private val db: RtdbClient) {

    fun messages(pairId: String, limit: Int = 100): Flow<List<ChatMessage>> =
        db.observeLast(DbPaths.chat(pairId), limit, orderByChild = "at").map(::messagesOf).resilient()

    fun newMessageId(pairId: String): String = db.newKey(DbPaths.chat(pairId))

    suspend fun send(pairId: String, uid: String, id: String, text: String): Outcome<Unit> {
        val body = text.trim()
        if (body.isEmpty() || body.length > MAX_TEXT) return Outcome.Failure(FailureReason.INVALID, "text")
        if (alreadySent(pairId, uid, id)) return Outcome.Success(Unit)
        return db.set(DbPaths.chatMessage(pairId, id), mapOf("from" to uid, "text" to body, "at" to db.serverTimestamp))
            .orIfTrue { alreadySent(pairId, uid, id) }
    }

    /** [jpeg] must already be downscaled/compressed with ImageBudget; anything over the hard limit is refused. */
    suspend fun sendPhoto(pairId: String, uid: String, id: String, jpeg: ByteArray, width: Int, height: Int): Outcome<Unit> {
        if (jpeg.isEmpty() || jpeg.size > ImageBudget.HARD_LIMIT_BYTES) return Outcome.Failure(FailureReason.INVALID, "photo size")
        if (alreadySent(pairId, uid, id)) return Outcome.Success(Unit)
        // The media id is the message id, so a retry finds the photo it already uploaded instead of orphaning it.
        val mediaPath = DbPaths.media(pairId, id)
        val mediaStored = ((db.get(mediaPath) as? Outcome.Success)?.value as? Map<*, *>)?.string("from") == uid
        val media = if (mediaStored) Outcome.Success(Unit) else {
            val data = Base64.getEncoder().encodeToString(jpeg)
            db.set(mediaPath, mapOf("from" to uid, "data" to data, "w" to width, "h" to height, "at" to db.serverTimestamp))
        }
        return media
            .then { db.set(DbPaths.chatMessage(pairId, id), mapOf("from" to uid, "mediaId" to id, "at" to db.serverTimestamp)) }
            .orIfTrue { alreadySent(pairId, uid, id) }
    }

    fun media(pairId: String, mediaId: String): Flow<String?> =
        db.observe(DbPaths.media(pairId, mediaId)).map { (it as? Map<*, *>)?.string("data") }.resilient()

    suspend fun markRead(pairId: String, uid: String): Outcome<Unit> =
        db.set(DbPaths.profile(pairId, uid) + "/lastReadAt", db.serverTimestamp)

    fun unreadCount(pairId: String, myUid: String): Flow<Int> = combine(
        messages(pairId),
        db.observe(DbPaths.profile(pairId, myUid) + "/lastReadAt").resilient(),
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
