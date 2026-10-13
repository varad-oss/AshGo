package com.varad.unstoppableash.data.trips

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.resilient
import com.varad.unstoppableash.data.firebase.bool
import com.varad.unstoppableash.data.firebase.double
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.firebase.string
import com.varad.unstoppableash.domain.model.Trip
import com.varad.unstoppableash.domain.model.TripDestination
import com.varad.unstoppableash.domain.model.TripState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A member may only write their own trip (rules); the partner observes it. */
class TripRepository(private val db: RtdbClient) {

    suspend fun start(pairId: String, uid: String, dest: TripDestination?, expectBy: Long?, note: String?, notifyPartner: Boolean): Outcome<Unit> {
        val value = buildMap<String, Any?> {
            put("state", "active"); put("startedAt", db.serverTimestamp); put("notifyPartner", notifyPartner)
            dest?.let { put("dest", buildMap { put("lat", it.lat); put("lng", it.lng); it.label?.take(60)?.let { l -> put("label", l) } }) }
            expectBy?.let { put("expectBy", it) }
            note?.trim()?.take(80)?.ifEmpty { null }?.let { put("note", it) }
        }
        return db.set(DbPaths.trip(pairId, uid), value)
    }

    suspend fun extend(pairId: String, uid: String, newDeadline: Long): Outcome<Unit> =
        db.update(DbPaths.trip(pairId, uid), mapOf("extendedTo" to newDeadline))

    suspend fun arrive(pairId: String, uid: String) = finish(pairId, uid, "arrived")
    suspend fun end(pairId: String, uid: String) = finish(pairId, uid, "ended")
    suspend fun markOverdue(pairId: String, uid: String): Outcome<Unit> =
        db.update(DbPaths.trip(pairId, uid), mapOf("state" to "overdue"))

    fun observe(pairId: String, uid: String): Flow<Trip?> =
        db.observe(DbPaths.trip(pairId, uid)).map(::tripOf).resilient()

    private suspend fun finish(pairId: String, uid: String, state: String): Outcome<Unit> =
        db.update(DbPaths.trip(pairId, uid), mapOf("state" to state, "endedAt" to db.serverTimestamp))

    private fun tripOf(value: Any?): Trip? {
        val m = value as? Map<*, *> ?: return null
        val state = when (m.string("state")) {
            "active" -> TripState.ACTIVE; "arrived" -> TripState.ARRIVED; "ended" -> TripState.ENDED; "overdue" -> TripState.OVERDUE
            else -> return null
        }
        val dest = (m["dest"] as? Map<*, *>)?.let { d ->
            val lat = d.double("lat"); val lng = d.double("lng")
            if (lat != null && lng != null) TripDestination(lat, lng, d.string("label")) else null
        }
        return Trip(state, dest, m.long("expectBy"), m.long("extendedTo"), m.string("note"), m.bool("notifyPartner") ?: false, m.long("startedAt"), m.long("endedAt"))
    }
}
