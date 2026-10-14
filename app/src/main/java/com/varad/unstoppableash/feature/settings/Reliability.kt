package com.varad.unstoppableash.feature.settings

data class ReliabilityChecks(
    val notificationsAllowed: Boolean,
    val sosChannelOn: Boolean,
    val fullScreenAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val locationPrecise: Boolean,
    val smsAllowed: Boolean,
    val connected: Boolean,
)

enum class Check { NOTIFICATIONS, SOS_CHANNEL, FULL_SCREEN, BATTERY, LOCATION, SMS, CONNECTION }
enum class Fix { NOTIFICATION_SETTINGS, SOS_CHANNEL_SETTINGS, FULL_SCREEN_SETTINGS, BATTERY_SETTINGS, LOCATION_PERMISSION, SMS_PERMISSION, RESTART_CONNECTION }
data class ReliabilityItem(val check: Check, val ok: Boolean, val fix: Fix)

/** J12: everything that can silently stop an alert reaching this phone, safety-critical first, each with its fix. */
fun reliabilityItems(c: ReliabilityChecks): List<ReliabilityItem> {
    val items = listOf(
        ReliabilityItem(Check.NOTIFICATIONS, c.notificationsAllowed, Fix.NOTIFICATION_SETTINGS),
        ReliabilityItem(Check.SOS_CHANNEL, c.sosChannelOn, Fix.SOS_CHANNEL_SETTINGS),
        ReliabilityItem(Check.CONNECTION, c.connected, Fix.RESTART_CONNECTION),
        ReliabilityItem(Check.FULL_SCREEN, c.fullScreenAllowed, Fix.FULL_SCREEN_SETTINGS),
        ReliabilityItem(Check.BATTERY, c.batteryUnrestricted, Fix.BATTERY_SETTINGS),
        ReliabilityItem(Check.SMS, c.smsAllowed, Fix.SMS_PERMISSION),
        ReliabilityItem(Check.LOCATION, c.locationPrecise, Fix.LOCATION_PERMISSION),
    )
    // Problems first, in the order above (which is already by how much they matter in an emergency).
    return items.filter { !it.ok } + items.filter { it.ok }
}
