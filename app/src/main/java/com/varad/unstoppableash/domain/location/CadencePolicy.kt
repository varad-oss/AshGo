package com.varad.unstoppableash.domain.location

import com.varad.unstoppableash.domain.model.SharingMode

enum class LocationAccuracy { HIGH, BALANCED }

data class LocationCadence(val intervalMs: Long, val minDistanceM: Float, val accuracy: LocationAccuracy)

/** How often to send location (ARCHITECTURE_PROPOSAL §8); null means send nothing. */
object CadencePolicy {
    private const val LOW_BATTERY_PCT = 15
    private val TRIP = LocationCadence(15_000, 10f, LocationAccuracy.HIGH)
    private val IDLE = LocationCadence(180_000, 0f, LocationAccuracy.BALANCED)
    private val LOW_BATTERY = LocationCadence(600_000, 0f, LocationAccuracy.BALANCED)

    fun cadenceFor(mode: SharingMode, onTrip: Boolean, batteryPct: Int?, charging: Boolean?): LocationCadence? {
        if (mode != SharingMode.LIVE) return null
        val lowBattery = batteryPct != null && batteryPct < LOW_BATTERY_PCT && charging != true
        return when {
            lowBattery -> LOW_BATTERY
            onTrip -> TRIP
            else -> IDLE
        }
    }
}
