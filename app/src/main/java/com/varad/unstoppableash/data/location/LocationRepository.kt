package com.varad.unstoppableash.data.location

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.resilient
import com.varad.unstoppableash.data.firebase.double
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.domain.model.LocationFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Latest-point-only location (D-14). Time is always the server's. */
class LocationRepository(private val db: RtdbClient) {

    /** Stop sharing removes the last point too (unlike pause, which keeps it visible as paused — D-14). */
    suspend fun clear(pairId: String, uid: String): Outcome<Unit> = db.remove(DbPaths.location(pairId, uid))

    suspend fun write(pairId: String, uid: String, lat: Double, lng: Double, accuracyM: Float?): Outcome<Unit> {
        val value = buildMap<String, Any?> {
            put("lat", lat)
            put("lng", lng)
            // Android reports 0 when it has no accuracy estimate; that is "unknown", not "perfect".
            if (accuracyM != null && accuracyM > 0f) put("acc", accuracyM.toDouble())
            put("at", db.serverTimestamp)
        }
        return db.set(DbPaths.location(pairId, uid), value)
    }

    fun observe(pairId: String, uid: String): Flow<LocationFix?> =
        db.observe(DbPaths.location(pairId, uid)).map(::fix).resilient()

    companion object {
        internal fun fix(value: Any?): LocationFix? {
            val m = value as? Map<*, *> ?: return null
            return LocationFix(
                lat = m.double("lat") ?: return null,
                lng = m.double("lng") ?: return null,
                accuracyM = m.double("acc")?.toFloat()?.takeIf { it > 0f },
                at = m.long("at") ?: return null,
            )
        }
    }
}
