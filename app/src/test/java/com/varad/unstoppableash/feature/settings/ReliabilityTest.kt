package com.varad.unstoppableash.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityTest {
    private val allGood = ReliabilityChecks(
        notificationsAllowed = true, sosChannelOn = true, fullScreenAllowed = true, batteryUnrestricted = true,
        locationPrecise = true, smsAllowed = true, connected = true,
    )

    @Test fun allGoodMeansNothingToFix() {
        assertTrue(reliabilityItems(allGood).all { it.ok })
    }

    @Test fun eachProblemNamesItsFix() {
        val items = reliabilityItems(
            ReliabilityChecks(false, false, false, false, false, false, false),
        ).associateBy { it.check }
        assertEquals(Fix.NOTIFICATION_SETTINGS, items.getValue(Check.NOTIFICATIONS).fix)
        assertEquals(Fix.SOS_CHANNEL_SETTINGS, items.getValue(Check.SOS_CHANNEL).fix)
        assertEquals(Fix.FULL_SCREEN_SETTINGS, items.getValue(Check.FULL_SCREEN).fix)
        assertEquals(Fix.BATTERY_SETTINGS, items.getValue(Check.BATTERY).fix)
        assertEquals(Fix.LOCATION_PERMISSION, items.getValue(Check.LOCATION).fix)
        assertEquals(Fix.SMS_PERMISSION, items.getValue(Check.SMS).fix)
        assertEquals(Fix.RESTART_CONNECTION, items.getValue(Check.CONNECTION).fix)
        assertTrue(items.values.none { it.ok })
    }

    @Test fun safetyProblemsComeFirst() {
        val items = reliabilityItems(allGood.copy(smsAllowed = false, sosChannelOn = false))
        assertEquals(Check.SOS_CHANNEL, items.first { !it.ok }.check)
    }
}
