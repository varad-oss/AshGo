package com.varad.unstoppableash.domain.copy

import com.varad.unstoppableash.domain.model.Relationship

/** Tone of everyday microcopy; safety screens always use their own neutral set. */
enum class CopyVariant { WARM, NEUTRAL, CASUAL }

enum class GreetingSlot { MORNING, AFTERNOON, EVENING, NIGHT }

fun copyVariantFor(rel: Relationship, greetingEnabled: Boolean): CopyVariant = when {
    !greetingEnabled -> CopyVariant.NEUTRAL
    rel == Relationship.PARTNER -> CopyVariant.WARM
    rel == Relationship.FAMILY -> CopyVariant.NEUTRAL
    else -> CopyVariant.CASUAL
}

fun greetingSlot(hourOfDay: Int): GreetingSlot {
    require(hourOfDay in 0..23) { "hourOfDay must be 0..23, was $hourOfDay" }
    return when (hourOfDay) {
        in 5..11 -> GreetingSlot.MORNING
        in 12..16 -> GreetingSlot.AFTERNOON
        in 17..21 -> GreetingSlot.EVENING
        else -> GreetingSlot.NIGHT
    }
}
