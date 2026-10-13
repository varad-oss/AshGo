package com.varad.unstoppableash.domain.location

/**
 * Arrival = [requiredFixes] consecutive fixes within max([floorM], accuracy).
 * Fixes worse than [maxUsableAccuracyM] are ignored entirely, so a 2 km-accuracy
 * fix can never "arrive" from 1.9 km away.
 */
class ArrivalDetector(
    private val floorM: Float = 100f,
    private val requiredFixes: Int = 2,
    private val maxUsableAccuracyM: Float = 200f,
) {
    private var streak = 0

    fun onFix(distanceM: Double, accuracyM: Float): Boolean {
        if (accuracyM <= maxUsableAccuracyM) {
            val radius = maxOf(floorM, accuracyM)
            streak = if (distanceM < radius) streak + 1 else 0
        }
        return streak >= requiredFixes
    }

    fun reset() {
        streak = 0
    }
}
