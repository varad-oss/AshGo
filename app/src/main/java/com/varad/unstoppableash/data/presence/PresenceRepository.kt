package com.varad.unstoppableash.data.presence

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.result.then
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.bool
import com.varad.unstoppableash.data.firebase.int
import com.varad.unstoppableash.data.firebase.long
import com.varad.unstoppableash.data.location.LocationRepository
import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.SharingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlin.math.abs

/**
 * Connection, sharing mode and battery for both members. Interpretation of freshness lives only in
 * PresenceCalculator; this class just reads and writes facts.
 */
class PresenceRepository(private val db: RtdbClient) {
    private var lastBattery: Int? = null
    private var lastCharging: Boolean? = null

    fun partnerSnapshot(pairId: String, partnerUid: String): Flow<PresenceSnapshot> = combine(
        db.observe(DbPaths.presence(pairId, partnerUid)).catch { emit(null) },
        db.observe(DbPaths.location(pairId, partnerUid)).catch { emit(null) },
    ) { presence, location ->
        val p = presence as? Map<*, *> ?: emptyMap<Any, Any>()
        val fix = LocationRepository.fix(location)
        PresenceSnapshot(
            online = p.bool("online"),
            lastSeen = p.long("lastSeen"),
            heartbeatAt = p.long("heartbeatAt"),
            battery = p.int("battery")?.takeIf { it in 0..100 },
            charging = p.bool("charging"),
            sharing = sharingOf(p["sharing"]),
            locationAt = fix?.at,
            accuracyM = fix?.accuracyM,
        )
    }

    suspend fun setSharing(pairId: String, uid: String, mode: SharingMode, until: Long?): Outcome<Unit> =
        db.update(DbPaths.presence(pairId, uid), mapOf("sharing" to mode.name.lowercase(), "sharingUntil" to until))

    /** Marks this phone online and has the server mark it offline (with server time) when the connection drops. */
    suspend fun goOnline(pairId: String, uid: String): Outcome<Unit> {
        val path = DbPaths.presence(pairId, uid)
        return db.onDisconnectUpdate(path, mapOf("online" to false, "lastSeen" to db.serverTimestamp))
            .then { db.update(path, mapOf("online" to true, "lastSeen" to db.serverTimestamp)) }
    }

    suspend fun heartbeat(pairId: String, uid: String): Outcome<Unit> =
        db.update(DbPaths.presence(pairId, uid), mapOf("heartbeatAt" to db.serverTimestamp))

    /** Writes battery only when it moved ≥ 5 points or charging flipped, to save quota and battery. */
    suspend fun reportBattery(pairId: String, uid: String, pct: Int, charging: Boolean): Outcome<Unit> {
        val previous = lastBattery
        if (previous != null && abs(previous - pct) < BATTERY_STEP && lastCharging == charging) return Outcome.Success(Unit)
        return db.update(DbPaths.presence(pairId, uid), mapOf("battery" to pct.coerceIn(0, 100), "charging" to charging))
            .also { if (it is Outcome.Success) { lastBattery = pct; lastCharging = charging } }
    }

    private fun sharingOf(value: Any?): SharingMode? = when (value) {
        "live" -> SharingMode.LIVE
        "paused" -> SharingMode.PAUSED
        "off" -> SharingMode.OFF
        else -> null
    }

    private companion object {
        const val BATTERY_STEP = 5
    }
}
