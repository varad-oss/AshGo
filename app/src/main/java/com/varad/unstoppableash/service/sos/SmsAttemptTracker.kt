package com.varad.unstoppableash.service.sos

import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.sos.SmsAggregator

enum class SmsPart { SENT, DELIVERED, FAILED }

/**
 * Turns Android's per-part sent/delivered callbacks into one status per message attempt, reported
 * only when it changes (the reducer's contract: one aggregated result per attempt, never per part).
 */
class SmsAttemptTracker {
    private class Attempt(parts: Int) {
        val status = Array(parts) { SmsStatus.QUEUED }
        var reported = SmsStatus.QUEUED
    }

    private val attempts = HashMap<String, Attempt>()

    @Synchronized fun start(attempt: String, parts: Int) {
        attempts[attempt] = Attempt(parts.coerceAtLeast(1))
    }

    /** The new combined status, or null when nothing a person would see has changed. */
    @Synchronized fun record(attempt: String, part: Int, result: SmsPart): SmsStatus? {
        val a = attempts[attempt] ?: return null
        if (part !in a.status.indices) return null
        val current = a.status[part]
        a.status[part] = when (result) {
            SmsPart.FAILED -> SmsStatus.FAILED
            SmsPart.SENT -> if (current == SmsStatus.QUEUED) SmsStatus.SENT else current
            SmsPart.DELIVERED -> if (current == SmsStatus.FAILED) current else SmsStatus.DELIVERED
        }
        val combined = SmsAggregator.combine(a.status.toList())
        if (combined == a.reported || a.reported == SmsStatus.FAILED) return null
        a.reported = combined
        return combined
    }
}
