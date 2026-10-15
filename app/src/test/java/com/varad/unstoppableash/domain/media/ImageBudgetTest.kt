package com.varad.unstoppableash.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageBudgetTest {
    @Test fun landscapeScalesLongEdge() = assertEquals(1024 to 768, ImageBudget.scaledSize(4000, 3000, 1024))
    @Test fun portraitScalesLongEdge() = assertEquals(768 to 1024, ImageBudget.scaledSize(3000, 4000, 1024))
    @Test fun neverUpscales() = assertEquals(800 to 600, ImageBudget.scaledSize(800, 600, 1024))
    @Test fun minimumOnePixel() = assertEquals(1 to 1024, ImageBudget.scaledSize(1, 5000, 1024))

    private fun encoder(vararg bytes: Pair<Int, Int>): (Int) -> Int {
        val table = bytes.toMap()
        return { q -> table[q] ?: 100_000 }
    }

    @Test fun picksFirstQualityUnderTarget() = assertEquals(
        65,
        ImageBudget.chooseQuality(encoder(85 to 200_000, 75 to 130_000, 65 to 110_000), ImageBudget.TARGET_BYTES, ImageBudget.HARD_LIMIT_BYTES),
    )
    @Test fun fallsBackToLastStepUnderHardLimit() = assertEquals(
        45,
        ImageBudget.chooseQuality(
            encoder(85 to 400_000, 75 to 300_000, 65 to 200_000, 55 to 160_000, 45 to 140_000),
            ImageBudget.TARGET_BYTES, ImageBudget.HARD_LIMIT_BYTES,
        ),
    )
    @Test fun rejectsWhenEvenLowestQualityTooLarge() = assertNull(
        ImageBudget.chooseQuality({ 500_000 }, ImageBudget.TARGET_BYTES, ImageBudget.HARD_LIMIT_BYTES),
    )
    @Test fun firstStepAlreadySmall() = assertEquals(
        85,
        ImageBudget.chooseQuality({ 50_000 }, ImageBudget.TARGET_BYTES, ImageBudget.HARD_LIMIT_BYTES),
    )
}
