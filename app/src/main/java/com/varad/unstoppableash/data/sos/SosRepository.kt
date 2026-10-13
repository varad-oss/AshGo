package com.varad.unstoppableash.data.sos

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.Receipt
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.double
import com.varad.unstoppableash.data.firebase.int
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.firebase.string
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.SosRecordState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** Backend half of SOS (D-6). The phone-local half — SMS, receipts per contact — lives in ActiveSosStore. */
class SosRepository(private val db: RtdbClient) {

    fun newSosId(pairId: String): String = db.newKey(DbPaths.sosAll(pairId))

    suspend fun raise(pairId: String, uid: String, sosId: String, location: LocationFix?, battery: Int?): Outcome<Unit> {
        val value = buildMap<String, Any?> {
            put("from", uid); put("state", "active"); put("at", db.serverTimestamp)
            location?.let { putAll(locationFields(it)) }
            battery?.let { put("battery", it.coerceIn(0, 100)) }
        }
        return db.set(DbPaths.sos(pairId, sosId), value)
    }

    /** State and time are written in one update so the rules see a consistent cancellation. */
    suspend fun cancel(pairId: String, sosId: String): Outcome<Unit> =
        db.update(DbPaths.sos(pairId, sosId), mapOf("state" to "cancelled", "cancelledAt" to db.serverTimestamp))

    suspend fun updateLocation(pairId: String, sosId: String, fix: LocationFix): Outcome<Unit> =
        db.update(DbPaths.sos(pairId, sosId), locationFields(fix) + ("acc" to fix.accuracyM?.takeIf { it > 0f }?.toDouble()))

    suspend fun updateSmsCounts(pairId: String, sosId: String, sent: Int, failed: Int): Outcome<Unit> =
        db.update(DbPaths.sos(pairId, sosId), mapOf("smsSent" to sent, "smsFailed" to failed))

    suspend fun markDelivered(pairId: String, record: SosRecord, myUid: String): Outcome<Unit> =
        receipt(pairId, record, myUid, Receipt.DELIVERED, record.deliveredTo)

    suspend fun ack(pairId: String, record: SosRecord, myUid: String): Outcome<Unit> =
        receipt(pairId, record, myUid, Receipt.ACK, record.ackBy)

    fun records(pairId: String, limit: Int = 5): Flow<List<SosRecord>> =
        db.observeLast(DbPaths.sosAll(pairId), limit, orderByChild = "at").map(::recordsOf).catch { emit(emptyList()) }

    fun partnerActiveSos(pairId: String, myUid: String): Flow<SosRecord?> = records(pairId).map { list ->
        list.lastOrNull { it.from != myUid && it.state == SosRecordState.ACTIVE }
    }

    private suspend fun receipt(pairId: String, r: SosRecord, myUid: String, kind: Receipt, existing: Map<String, Long>): Outcome<Unit> {
        if (r.from == myUid || myUid in existing) return Outcome.Success(Unit)
        return db.set(DbPaths.sosReceipt(pairId, r.id, kind, myUid), db.serverTimestamp)
    }

    private fun locationFields(fix: LocationFix): Map<String, Any?> = buildMap {
        put("lat", fix.lat); put("lng", fix.lng); put("locAt", fix.at)
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
