package com.varad.unstoppableash.data.sos

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.Receipt
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.orIfTrue
import com.varad.unstoppableash.data.firebase.resilient
import com.varad.unstoppableash.data.firebase.double
import com.varad.unstoppableash.data.firebase.int
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.firebase.string
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.SosRecordState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Backend half of SOS (D-6). The phone-local half — SMS, receipts per contact — lives in ActiveSosStore. */
class SosRepository(private val db: RtdbClient, private val clock: com.varad.unstoppableash.core.time.Clock) {

    fun newSosId(pairId: String): String = db.newKey(DbPaths.sosAll(pairId))

    /** Push keys don't depend on the path, so an SOS gets its id before the orbit is even known. */
    fun newSosId(): String = db.newKey("sos")

    /**
     * Writes the core record first; location follows in a separate update so a location problem can
     * never block the alert (review C2). Retrying with the same id is a no-op success (review I2).
     */
    suspend fun raise(pairId: String, uid: String, sosId: String, location: LocationFix?, battery: Int?): Outcome<Unit> {
        val path = DbPaths.sos(pairId, sosId)
        val core = if (record(path)?.string("from") == uid) Outcome.Success(Unit) else {
            val value = buildMap<String, Any?> {
                put("from", uid); put("state", "active"); put("at", db.serverTimestamp)
                battery?.let { put("battery", it.coerceIn(0, 100)) }
            }
            db.set(path, value).orIfTrue { record(path)?.string("from") == uid }
        }
        if (core is Outcome.Success && location != null) updateLocation(pairId, sosId, location)
        return core
    }

    /** State and time in one update so the rules see a consistent cancellation; cancelling twice is a success. */
    suspend fun cancel(pairId: String, sosId: String): Outcome<Unit> {
        val path = DbPaths.sos(pairId, sosId)
        if (record(path)?.string("state") == "cancelled") return Outcome.Success(Unit)
        return db.update(path, mapOf("state" to "cancelled", "cancelledAt" to db.serverTimestamp))
            .orIfTrue { record(path)?.string("state") == "cancelled" }
    }

    /** `locAt` comes from the device clock; it is clamped to server-corrected now so a fast clock isn't rejected. */
    suspend fun updateLocation(pairId: String, sosId: String, fix: LocationFix): Outcome<Unit> =
        db.update(DbPaths.sos(pairId, sosId), locationFields(fix) + ("acc" to fix.accuracyM?.takeIf { it > 0f }?.toDouble()))

    suspend fun updateSmsCounts(pairId: String, sosId: String, sent: Int, failed: Int): Outcome<Unit> =
        db.update(DbPaths.sos(pairId, sosId), mapOf("smsSent" to sent, "smsFailed" to failed))

    suspend fun markDelivered(pairId: String, record: SosRecord, myUid: String): Outcome<Unit> =
        receipt(pairId, record, myUid, Receipt.DELIVERED, record.deliveredTo)

    suspend fun ack(pairId: String, record: SosRecord, myUid: String): Outcome<Unit> =
        receipt(pairId, record, myUid, Receipt.ACK, record.ackBy)

    fun records(pairId: String, limit: Int = 20): Flow<List<SosRecord>> =
        db.observeLast(DbPaths.sosAll(pairId), limit, orderByChild = "at").map(::recordsOf).resilient()

    fun partnerActiveSos(pairId: String, myUid: String): Flow<SosRecord?> = records(pairId).map { list ->
        list.lastOrNull { it.from != myUid && it.state == SosRecordState.ACTIVE }
    }

    private suspend fun receipt(pairId: String, r: SosRecord, myUid: String, kind: Receipt, existing: Map<String, Long>): Outcome<Unit> {
        if (r.from == myUid || myUid in existing) return Outcome.Success(Unit)
        val path = DbPaths.sosReceipt(pairId, r.id, kind, myUid)
        return db.set(path, db.serverTimestamp).orIfTrue { (db.get(path) as? Outcome.Success)?.value != null }
    }

    private suspend fun record(path: String): Map<*, *>? = (db.get(path) as? Outcome.Success)?.value as? Map<*, *>

    private fun locationFields(fix: LocationFix): Map<String, Any?> = buildMap {
        put("lat", fix.lat); put("lng", fix.lng); put("locAt", minOf(fix.at, clock.now()))
        fix.accuracyM?.takeIf { it > 0f }?.let { put("acc", it.toDouble()) }
    }

    private fun recordsOf(value: Any?): List<SosRecord> =
        (value as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            SosRecord(
                id = k as String,
                from = m.string("from") ?: return@mapNotNull null,
                state = when (m.string("state")) { "active" -> SosRecordState.ACTIVE; "cancelled" -> SosRecordState.CANCELLED; else -> return@mapNotNull null },
                at = m.long("at") ?: return@mapNotNull null,
                lat = m.double("lat"), lng = m.double("lng"), locAt = m.long("locAt"),
                accuracyM = m.double("acc")?.toFloat()?.takeIf { it > 0f },
                battery = m.int("battery"), smsSent = m.int("smsSent"), smsFailed = m.int("smsFailed"),
                cancelledAt = m.long("cancelledAt"),
                deliveredTo = receipts(m["deliveredTo"]), ackBy = receipts(m["ackBy"]),
            )
        }?.sortedBy { it.at }.orEmpty()

    private fun receipts(value: Any?): Map<String, Long> =
        (value as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> (v as? Number)?.toLong()?.let { k as String to it } }?.toMap().orEmpty()
}
