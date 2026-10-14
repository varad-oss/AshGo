package com.varad.unstoppableash.domain.sos

import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SmsStatus
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SOS texts. Everything except the person's own name is GSM-7, so a typical alert fits in two SMS parts
 * instead of five UCS-2 parts (the v1 failure in USER_JOURNEYS J10). No emoji, no "±".
 */
object SmsComposer {

    fun sos(name: String, fix: LocationFix?, battery: Int?, nowMs: Long): String = buildString {
        append("AshGo SOS: ").append(name.trim()).append(" needs help. ")
        if (fix != null && isReal(fix)) {
            val lat = coord(fix.lat)
            val lng = coord(fix.lng)
            append("Location: https://www.openstreetmap.org/?mlat=").append(lat).append("&mlon=").append(lng)
            append("#map=17/").append(lat).append('/').append(lng).append(' ')
            append('(')
            fix.accuracyM?.takeIf { it > 0f }?.let { append("+/-").append(it.roundToInt()).append(" m, ") }
            append(age(nowMs - fix.at)).append("). ")
        } else {
            append("Location unavailable. ")
        }
        battery?.let { append("Battery ").append(it.coerceIn(0, 100)).append("%. ") }
        append("Sent automatically by AshGo.")
    }

    fun falseAlarm(name: String): String = "AshGo: ${name.trim()} is safe. The earlier SOS was a false alarm. Sorry for the worry."

    /** 0,0 is what a broken provider reports; sending it would send help to the Gulf of Guinea. */
    private fun isReal(fix: LocationFix) = !(abs(fix.lat) < 1e-6 && abs(fix.lng) < 1e-6) &&
        fix.lat in -90.0..90.0 && fix.lng in -180.0..180.0

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.5f", v)

    private fun age(ms: Long): String {
        val m = (ms.coerceAtLeast(0) / 60_000)
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 48 * 60 -> "${m / 60} h ago"
            else -> "${m / (24 * 60)} d ago"
        }
    }
}

/** One result per message attempt from per-part callbacks: the worst part wins, "delivered" needs every part. */
object SmsAggregator {
    fun combine(parts: List<SmsStatus>): SmsStatus = when {
        parts.isEmpty() -> SmsStatus.QUEUED
        SmsStatus.FAILED in parts -> SmsStatus.FAILED
        SmsStatus.QUEUED in parts -> SmsStatus.QUEUED
        parts.all { it == SmsStatus.DELIVERED } -> SmsStatus.DELIVERED
        else -> SmsStatus.SENT
    }
}
