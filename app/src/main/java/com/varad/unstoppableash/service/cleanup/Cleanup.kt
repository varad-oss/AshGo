package com.varad.unstoppableash.service.cleanup

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.firebase.string
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.retention.RetainedKind
import com.varad.unstoppableash.domain.retention.RetentionItem
import com.varad.unstoppableash.domain.retention.RetentionSelector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class CleanupResult(val deleted: Int)

/**
 * D-14 retention, run from either phone. Deletes only what the rules allow this person to delete: anything past
 * retention in chat/media/events, and their own finished trip or cancelled SOS. Never an active SOS or trip.
 * Photos are found through their message (media id == message id), so cleanup never downloads a photo.
 */
class Cleanup(private val db: RtdbClient, private val pairs: PairRepository, private val clock: Clock) {

    suspend fun run(): CleanupResult {
        val p = paired() ?: return CleanupResult(0)
        val pid = p.meta.pairId
        // Server-time cutoffs with a small margin, so a slightly-off clock offset never asks the rules for too much.
        val now = clock.now() - MARGIN_MS
        var deleted = 0

        val chat = children(DbPaths.chat(pid))
        val chatItems = chat.map { (id, m) -> RetentionItem(id, RetainedKind.CHAT, m.long("at") ?: Long.MAX_VALUE) }
        val photoItems = chat.mapNotNull { (_, m) ->
            val media = m.string("mediaId") ?: return@mapNotNull null
            RetentionItem(media, RetainedKind.MEDIA, m.long("at") ?: Long.MAX_VALUE)
        }
        RetentionSelector.selectExpired(photoItems, now).forEach { if (remove(DbPaths.media(pid, it))) deleted++ }
        RetentionSelector.selectExpired(chatItems, now).forEach { if (remove(DbPaths.chatMessage(pid, it))) deleted++ }

        val events = children(DbPaths.events(pid)).map { (id, e) -> RetentionItem(id, RetainedKind.EVENT, e.long("at") ?: Long.MAX_VALUE) }
        RetentionSelector.selectExpired(events, now).forEach { if (remove(DbPaths.event(pid, it))) deleted++ }

        val mySos = children(DbPaths.sosAll(pid)).filter { (_, s) -> s.string("from") == p.myUid }.map { (id, s) ->
            RetentionItem(id, RetainedKind.SOS, s.long("at") ?: Long.MAX_VALUE, sosActive = s.string("state") != "cancelled", cancelledAt = s.long("cancelledAt"))
        }
        RetentionSelector.selectExpired(mySos, now).forEach { if (remove(DbPaths.sos(pid, it))) deleted++ }

        val myTrip = (get(DbPaths.trip(pid, p.myUid)) as? Map<*, *>)
        if (myTrip != null && myTrip.string("state") in FINISHED) {
            val item = RetentionItem(p.myUid, RetainedKind.TRIP, myTrip.long("startedAt") ?: 0, endedAt = myTrip.long("endedAt"))
            if (RetentionSelector.selectExpired(listOf(item), now).isNotEmpty() && remove(DbPaths.trip(pid, p.myUid))) deleted++
        }
        return CleanupResult(deleted)
    }

    /** Privacy & data → "Delete my messages now": my messages and my photos, in this orbit. */
    suspend fun deleteMyMessages(): Int {
        val p = paired() ?: return 0
        val pid = p.meta.pairId
        var deleted = 0
        children(DbPaths.chat(pid)).filter { (_, m) -> m.string("from") == p.myUid }.forEach { (id, m) ->
            m.string("mediaId")?.let { if (remove(DbPaths.media(pid, it))) deleted++ }
            if (remove(DbPaths.chatMessage(pid, id))) deleted++
        }
        return deleted
    }

    private suspend fun paired(): PairState.Paired? = withTimeoutOrNull(15_000) { pairs.pairState().first() } as? PairState.Paired

    private suspend fun get(path: String): Any? = (db.get(path) as? Outcome.Success)?.value

    private suspend fun children(path: String): List<Pair<String, Map<*, *>>> =
        (get(path) as? Map<*, *>)?.mapNotNull { (k, v) -> (k as? String)?.let { id -> (v as? Map<*, *>)?.let { id to it } } }.orEmpty()

    private suspend fun remove(path: String): Boolean = db.remove(path) is Outcome.Success

    private companion object {
        const val MARGIN_MS = 5 * 60_000L
        val FINISHED = setOf("arrived", "ended")
    }
}
