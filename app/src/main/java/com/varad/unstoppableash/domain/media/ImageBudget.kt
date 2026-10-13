package com.varad.unstoppableash.domain.media

import kotlin.math.roundToInt

/** Photo and avatar size limits that keep RTDB use inside the Spark quota (PRODUCT_DECISIONS D-5). */
object ImageBudget {
    const val MAX_EDGE_PX = 1024
    const val TARGET_BYTES = 120_000
    const val HARD_LIMIT_BYTES = 150_000
    const val AVATAR_EDGE_PX = 256
    const val AVATAR_MAX_BYTES = 30_000
    val QUALITY_STEPS = listOf(85, 75, 65, 55, 45)

    /** Scales so the longer edge is at most [maxEdge]; never upscales, never below 1 px. */
    fun scaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val longEdge = maxOf(width, height)
        if (longEdge <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longEdge
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /**
     * Walks [QUALITY_STEPS] from best to worst. Returns the first quality whose output fits [target],
     * else the lowest quality if it fits [hardLimit], else null (reject the photo).
     */
    fun chooseQuality(encodedBytesAt: (Int) -> Int, target: Int, hardLimit: Int): Int? {
        var lastBytes = Int.MAX_VALUE
        for (quality in QUALITY_STEPS) {
            lastBytes = encodedBytesAt(quality)
            if (lastBytes <= target) return quality
        }
        return QUALITY_STEPS.last().takeIf { lastBytes <= hardLimit }
    }
}
