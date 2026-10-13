package com.varad.unstoppableash.domain.copy

import com.varad.unstoppableash.domain.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Test

class CopyVariantTest {
    @Test fun partnerWithGreetingIsWarm() = assertEquals(CopyVariant.WARM, copyVariantFor(Relationship.PARTNER, true))
    @Test fun partnerWithoutGreetingIsNeutral() = assertEquals(CopyVariant.NEUTRAL, copyVariantFor(Relationship.PARTNER, false))
    @Test fun familyIsNeutral() = assertEquals(CopyVariant.NEUTRAL, copyVariantFor(Relationship.FAMILY, true))
    @Test fun friendsAreCasual() = assertEquals(CopyVariant.CASUAL, copyVariantFor(Relationship.FRIENDS, true))
    @Test fun trustedIsCasual() = assertEquals(CopyVariant.CASUAL, copyVariantFor(Relationship.TRUSTED, true))
    @Test fun greetingOffIsNeutralForEveryone() = assertEquals(CopyVariant.NEUTRAL, copyVariantFor(Relationship.FRIENDS, false))

    @Test fun greetingSlots() {
        val expected = mapOf(
            5 to GreetingSlot.MORNING, 11 to GreetingSlot.MORNING,
            12 to GreetingSlot.AFTERNOON, 16 to GreetingSlot.AFTERNOON,
            17 to GreetingSlot.EVENING, 21 to GreetingSlot.EVENING,
            22 to GreetingSlot.NIGHT, 4 to GreetingSlot.NIGHT, 0 to GreetingSlot.NIGHT,
        )
        expected.forEach { (hour, slot) -> assertEquals("hour $hour", slot, greetingSlot(hour)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHourRejected() { greetingSlot(24) }
}
