package com.varad.unstoppableash.data.events

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.result.then
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.Receipt
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.orIfTrue
import com.varad.unstoppableash.data.firebase.resilient
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.firebase.string
import com.varad.unstoppableash.domain.model.EventType
import com.varad.unstoppableash.domain.model.OrbitEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Check-ins (O-1) and nudges (O-3). "Seen" is only ever written by the partner's phone. */
class EventRepository(private val db: RtdbClient, private val clock: Clock) {
    private var lastNudgeAt: Long? = null

    fun events(pairId: String, limit: Int = 50): Flow<List<OrbitEvent>> =
        db.observeLast(DbPaths.events(pairId), limit, orderByChild = "at").map(::eventsOf).resilient()

    suspend fun checkIn(pairId: String, uid: String, kind: String, text: String?): Outcome<String> =
        create(pairId, uid, "checkin", kind, text)

    suspend fun nudge(pairId: String, uid: String, kind: String): Outcome<String> {
        val now = clock.now()
        lastNudgeAt?.let { if (now - it < NUDGE_GAP_MS) return Outcome.Failure(FailureReason.RATE_LIMITED) }
        return create(pairId, uid, "nudge", kind, null).also { if (it is Outcome.Success) lastNudgeAt = now }
    }

    suspend fun markDelivered(pairId: String, event: OrbitEvent, myUid: String): Outcome<Unit> =
        receipt(pairId, event, myUid, Receipt.DELIVERED, event.deliveredTo)

    suspend fun markSeen(pairId: String, event: OrbitEvent, myUid: String): Outcome<Unit> =
        receipt(pairId, event, myUid, Receipt.SEEN, event.seenBy)

    private suspend fun receipt(pairId: String, e: OrbitEvent, myUid: String, kind: Receipt, existing: Map<String, Long>): Outcome<Unit> {
        if (e.from == myUid || myUid in existing) return Outcome.Success(Unit)
        val path = DbPaths.eventReceipt(pairId, e.id, kind, myUid)
        return db.set(path, db.serverTimestamp).orIfTrue { (db.get(path) as? Outcome.Success)?.value != null }
    }

    private suspend fun create(pairId: String, uid: String, type: String, kind: String, text: String?): Outcome<String> {
        val body = text?.trim()?.ifEmpty { null }
        if (kind.isBlank() || kind.length > 30 || (body != null && body.length > 60)) return Outcome.Failure(FailureReason.INVALID)
        val id = db.newKey(DbPaths.events(pairId))
        val value = buildMap<String, Any?> {
            put("from", uid); put("type", type); put("kind", kind)
            if (body != null) put("text", body)
            put("at", db.serverTimestamp)
        }
        return db.set(DbPaths.event(pairId, id), value).then { Outcome.Success(id) }
    }

    private fun eventsOf(value: Any?): List<OrbitEvent> =
        (value as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            OrbitEvent(
                id = k as String,
                from = m.string("from") ?: return@mapNotNull null,
                type = when (m.string("type")) { "checkin" -> EventType.CHECKIN; "nudge" -> EventType.NUDGE; else -> return@mapNotNull null },
                kind = m.string("kind") ?: return@mapNotNull null,
                text = m.string("text"),
                at = m.long("at") ?: return@mapNotNull null,
                deliveredTo = receipts(m["deliveredTo"]),
                seenBy = receipts(m["seenBy"]),
            )
        }?.sortedBy { it.at }.orEmpty()

    private fun receipts(value: Any?): Map<String, Long> =
        (value as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> (v as? Number)?.toLong()?.let { k as String to it } }?.toMap().orEmpty()

    private companion object {
        const val NUDGE_GAP_MS = 30_000L
    }
}
