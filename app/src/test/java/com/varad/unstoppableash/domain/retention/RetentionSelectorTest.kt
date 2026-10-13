package com.varad.unstoppableash.domain.retention

import com.varad.unstoppableash.domain.time.Durations.DAY
import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionSelectorTest {
    private val now = 100 * DAY
    private fun expired(vararg items: RetentionItem) = RetentionSelector.selectExpired(items.toList(), now)
    private fun item(id: String, kind: RetainedKind, at: Long, endedAt: Long? = null, sosActive: Boolean = false, cancelledAt: Long? = null) =
        RetentionItem(id, kind, at, endedAt, sosActive, cancelledAt)

    @Test fun chatBoundary() = assertEquals(
        listOf("old"),
        expired(item("old", RetainedKind.CHAT, now - 7 * DAY - 1), item("new", RetainedKind.CHAT, now - 7 * DAY + 1)),
    )
    @Test fun mediaBoundary() = assertEquals(
        listOf("old"),
        expired(item("old", RetainedKind.MEDIA, now - 3 * DAY - 1), item("new", RetainedKind.MEDIA, now - 3 * DAY + 1)),
    )
    @Test fun eventBoundary() = assertEquals(
        listOf("old"),
        expired(item("old", RetainedKind.EVENT, now - 7 * DAY - 1), item("new", RetainedKind.EVENT, now - 7 * DAY + 1)),
    )
    @Test fun activeTripIsKept() = assertEquals(emptyList<String>(), expired(item("t", RetainedKind.TRIP, now - 30 * DAY)))
    @Test fun endedTripExpiresAfterADay() = assertEquals(
        listOf("t1"),
        expired(item("t1", RetainedKind.TRIP, now - 2 * DAY, endedAt = now - DAY - 1), item("t2", RetainedKind.TRIP, now - 2 * DAY, endedAt = now - DAY + 1)),
    )
    @Test fun activeSosIsNeverDeleted() =
        assertEquals(emptyList<String>(), expired(item("s", RetainedKind.SOS, now - 90 * DAY, sosActive = true)))
    @Test fun cancelledSosExpiresAfterThirtyDays() =
        assertEquals(listOf("s"), expired(item("s", RetainedKind.SOS, now - 60 * DAY, cancelledAt = now - 30 * DAY - 1)))
    @Test fun recentlyCancelledSosIsKept() =
        assertEquals(emptyList<String>(), expired(item("s", RetainedKind.SOS, now - 60 * DAY, cancelledAt = now - 29 * DAY)))
    @Test fun unresolvedSosWithoutCancelTimeIsKept() =
        assertEquals(emptyList<String>(), expired(item("s", RetainedKind.SOS, now - 90 * DAY)))
    @Test fun preservesInputOrder() = assertEquals(
        listOf("b", "a"),
        expired(item("b", RetainedKind.CHAT, now - 10 * DAY), item("keep", RetainedKind.CHAT, now), item("a", RetainedKind.MEDIA, now - 10 * DAY)),
    )
}
