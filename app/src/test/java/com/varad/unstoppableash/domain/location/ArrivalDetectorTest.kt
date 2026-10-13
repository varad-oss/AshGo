package com.varad.unstoppableash.domain.location

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalDetectorTest {
    private fun run(vararg fixes: Pair<Double, Float>): List<Boolean> {
        val d = ArrivalDetector()
        return fixes.map { (distance, accuracy) -> d.onFix(distance, accuracy) }
    }

    @Test fun twoConsecutiveCloseFixesArrive() = assertEquals(listOf(false, true), run(50.0 to 20f, 50.0 to 20f))
    @Test fun farFixResetsStreak() = assertEquals(listOf(false, false, false), run(50.0 to 20f, 300.0 to 20f, 50.0 to 20f))
    @Test fun radiusGrowsWithAccuracy() = assertEquals(listOf(false, true), run(140.0 to 150f, 140.0 to 150f))
    @Test fun outsideFloorWithGoodAccuracyDoesNotCount() = assertEquals(listOf(false, false), run(140.0 to 20f, 140.0 to 20f))
    @Test fun wildlyImpreciseFixesNeverArrive() = assertEquals(listOf(false, false), run(1_900.0 to 2_000f, 1_900.0 to 2_000f))
    @Test fun unusableFixLeavesStreakUnchanged() =
        assertEquals(listOf(false, false, true), run(50.0 to 20f, 1_900.0 to 2_000f, 50.0 to 20f))

    @Test fun resetClearsStreak() {
        val d = ArrivalDetector()
        d.onFix(50.0, 20f)
        d.reset()
        assertEquals(false, d.onFix(50.0, 20f))
    }
}
