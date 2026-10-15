package com.varad.unstoppableash.service.cleanup

import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.domain.time.Durations.DAY
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CleanupTest {
    private val now = 100 * DAY
    private val db = FakeRtdb(nowMs = now)
    private val cleanup = Cleanup(db, PairRepository(db, FakeAuthGateway(FakeAuthGateway.google("alice")), Clock { now }), Clock { now })

    private suspend fun seed() {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
    }

    @Test fun expiredItemsGoFreshOnesStay() = runTest {
        seed()
        db.set("pairs/p1/chat/old", mapOf("from" to "bob", "text" to "x", "at" to now - 8 * DAY))
        db.set("pairs/p1/chat/new", mapOf("from" to "bob", "text" to "y", "at" to now - DAY))
        db.set("pairs/p1/chat/photo", mapOf("from" to "bob", "mediaId" to "photo", "at" to now - 4 * DAY))
        db.set("pairs/p1/media/photo", mapOf("from" to "bob", "data" to "AAAA", "at" to now - 4 * DAY))
        db.set("pairs/p1/events/e-old", mapOf("from" to "bob", "type" to "nudge", "kind" to "call_me", "at" to now - 9 * DAY))
        db.set("pairs/p1/events/e-new", mapOf("from" to "bob", "type" to "nudge", "kind" to "call_me", "at" to now - DAY))
        val r = cleanup.run()
        assertNull(db.read("pairs/p1/chat/old")); assertNotNull(db.read("pairs/p1/chat/new"))
        assertNull("photos go after 3 days", db.read("pairs/p1/media/photo")); assertNotNull("its message stays until 7", db.read("pairs/p1/chat/photo"))
        assertNull(db.read("pairs/p1/events/e-old")); assertNotNull(db.read("pairs/p1/events/e-new"))
        assertEquals(3, r.deleted)
    }

    @Test fun anActiveSosIsNeverDeletedAndOnlyMyOwnCancelledOnesGo() = runTest {
        seed()
        db.set("pairs/p1/sos/active", mapOf("from" to "alice", "state" to "active", "at" to now - 90 * DAY))
        db.set("pairs/p1/sos/mine-old", mapOf("from" to "alice", "state" to "cancelled", "at" to now - 40 * DAY, "cancelledAt" to now - 35 * DAY))
        db.set("pairs/p1/sos/theirs-old", mapOf("from" to "bob", "state" to "cancelled", "at" to now - 40 * DAY, "cancelledAt" to now - 35 * DAY))
        cleanup.run()
        assertNotNull(db.read("pairs/p1/sos/active"))
        assertNull(db.read("pairs/p1/sos/mine-old"))
        assertNotNull("the rules let only its sender delete it", db.read("pairs/p1/sos/theirs-old"))
    }

    @Test fun onlyMyFinishedTripAfterADay() = runTest {
        seed()
        db.set("pairs/p1/trips/alice", mapOf("state" to "arrived", "startedAt" to now - 3 * DAY, "endedAt" to now - 2 * DAY, "notifyPartner" to true))
        db.set("pairs/p1/trips/bob", mapOf("state" to "ended", "startedAt" to now - 3 * DAY, "endedAt" to now - 2 * DAY, "notifyPartner" to true))
        cleanup.run()
        assertNull(db.read("pairs/p1/trips/alice")); assertNotNull(db.read("pairs/p1/trips/bob"))
    }

    @Test fun anActiveTripStays() = runTest {
        seed()
        db.set("pairs/p1/trips/alice", mapOf("state" to "active", "startedAt" to now - 3 * DAY, "notifyPartner" to true))
        cleanup.run()
        assertNotNull(db.read("pairs/p1/trips/alice"))
    }

    @Test fun deleteMyMessagesNowRemovesOnlyMine() = runTest {
        seed()
        db.set("pairs/p1/chat/a", mapOf("from" to "alice", "text" to "x", "at" to now - 1))
        db.set("pairs/p1/chat/p", mapOf("from" to "alice", "mediaId" to "p", "at" to now - 1))
        db.set("pairs/p1/media/p", mapOf("from" to "alice", "data" to "AAAA", "at" to now - 1))
        db.set("pairs/p1/chat/b", mapOf("from" to "bob", "text" to "y", "at" to now - 1))
        assertEquals(3, cleanup.deleteMyMessages())
        assertNull(db.read("pairs/p1/chat/a")); assertNull(db.read("pairs/p1/media/p")); assertNotNull(db.read("pairs/p1/chat/b"))
    }

    @Test fun withoutAnOrbitNothingHappens() = runTest {
        assertEquals(0, cleanup.run().deleted)
    }
}
