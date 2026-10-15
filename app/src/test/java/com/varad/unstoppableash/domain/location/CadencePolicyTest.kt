package com.varad.unstoppableash.domain.location

import com.varad.unstoppableash.domain.model.SharingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CadencePolicyTest {
    // No minimum distance on a trip: someone who stops at the destination must keep producing fixes, or arrival can never be confirmed.
    private val trip = LocationCadence(15_000, 0f, LocationAccuracy.HIGH)
    private val idle = LocationCadence(180_000, 0f, LocationAccuracy.BALANCED)
    private val lowBattery = LocationCadence(600_000, 0f, LocationAccuracy.BALANCED)

    @Test fun pausedSendsNothing() = assertNull(CadencePolicy.cadenceFor(SharingMode.PAUSED, onTrip = true, batteryPct = 80, charging = false))
    @Test fun offSendsNothing() = assertNull(CadencePolicy.cadenceFor(SharingMode.OFF, onTrip = false, batteryPct = 80, charging = false))
    @Test fun liveOnTrip() = assertEquals(trip, CadencePolicy.cadenceFor(SharingMode.LIVE, onTrip = true, batteryPct = 80, charging = false))
    @Test fun liveIdle() = assertEquals(idle, CadencePolicy.cadenceFor(SharingMode.LIVE, onTrip = false, batteryPct = 80, charging = false))
    @Test fun lowBatteryIdle() = assertEquals(lowBattery, CadencePolicy.cadenceFor(SharingMode.LIVE, onTrip = false, batteryPct = 14, charging = false))
    @Test fun lowBatteryOnTrip() = assertEquals(lowBattery, CadencePolicy.cadenceFor(SharingMode.LIVE, onTrip = true, batteryPct = 14, charging = false))
    @Test fun lowBatteryUnknownChargingCountsAsNotCharging() =
        assertEquals(lowBattery, CadencePolicy.cadenceFor(SharingMode.LIVE, onTrip = false, batteryPct = 14, charging = null))
    @Test fun batteryAtFifteenIsNormal() = assertEquals(idle, CadencePolicy.cadenceFor(SharingMode.LIVE, onTrip = false, batteryPct = 15, charging = false))
    @Test fun lowBatteryWhileChargingIsNormal() = assertEquals(trip, CadencePolicy.cadenceFor(SharingMode.LIVE, onTrip = true, batteryPct = 14, charging = true))
    @Test fun unknownBatteryIsNormal() = assertEquals(idle, CadencePolicy.cadenceFor(SharingMode.LIVE, onTrip = false, batteryPct = null, charging = null))
}
