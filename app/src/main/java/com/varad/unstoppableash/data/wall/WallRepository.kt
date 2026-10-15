package com.varad.unstoppableash.data.wall

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.firebase.resilient
import com.varad.unstoppableash.data.firebase.string
import com.varad.unstoppableash.domain.media.ImageBudget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Base64
import java.util.UUID

data class WallEntry(val id: String, val from: String, val at: Long)

/**
 * "Our page", shared by both phones (owner's ask). Entries (small) are listened to; each photo's data is fetched
 * once by id and kept on the phone, so opening the page doesn't re-download the wall (Spark quota). Either member
 * may take any photo down; leaving the orbit deletes the whole wall with the pair.
 */
class WallRepository(private val db: RtdbClient) {

    fun entries(pairId: String): Flow<List<WallEntry>> = db.observe("${DbPaths.pair(pairId)}/wall").map { raw ->
        (raw as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            WallEntry(k as? String ?: return@mapNotNull null, m.string("from") ?: return@mapNotNull null, m.long("at") ?: 0L)
        }.sortedWith(compareBy({ it.at }, { it.id }))
    }.resilient()

    /** One multi-path write: the entry and its photo land together or not at all. */
    suspend fun add(pairId: String, uid: String, jpeg: ByteArray, width: Int, height: Int): Outcome<String> {
        if (jpeg.isEmpty() || jpeg.size > ImageBudget.HARD_LIMIT_BYTES) return Outcome.Failure(FailureReason.INVALID, "photo size")
        val id = UUID.randomUUID().toString().replace("-", "")
        val write = db.update(
            DbPaths.pair(pairId),
            mapOf(
                "wall/$id" to mapOf("from" to uid, "at" to db.serverTimestamp, "w" to width, "h" to height),
                "wallData/$id" to Base64.getEncoder().encodeToString(jpeg),
            ),
        )
        return if (write is Outcome.Failure) write else Outcome.Success(id)
    }

    suspend fun remove(pairId: String, id: String): Outcome<Unit> =
        db.update(DbPaths.pair(pairId), mapOf("wall/$id" to null, "wallData/$id" to null))

    suspend fun photo(pairId: String, id: String): ByteArray? =
        ((db.get("${DbPaths.pair(pairId)}/wallData/$id") as? Outcome.Success)?.value as? String)
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    companion object { const val MAX = 24 }
}
