package com.varad.unstoppableash.service.connect

import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatTest {
    private val db = FakeRtdb(nowMs = 5_000L)
    private fun pairs(uid: String = "alice") = PairRepository(db, FakeAuthGateway(FakeAuthGateway.google(uid)), Clock { db.nowMs })

    @Test fun writesTheServerTimeWhenInAnOrbit() = runTest {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        assertTrue(heartbeatOnce(pairs(), PresenceRepository(db)))
        assertEquals(5_000L, db.read("pairs/p1/presence/alice/heartbeatAt"))
    }

    @Test fun withoutAnOrbitItWritesNothing() = runTest {
        assertFalse(heartbeatOnce(pairs(), PresenceRepository(db)))
        assertEquals(null, db.read("pairs"))
    }

    @Test fun anUnreachableOrbitGivesUpInsteadOfHanging() = runTest {
        db.failReads("userPairs")
        assertFalse(heartbeatOnce(pairs(), PresenceRepository(db), timeoutMs = 1_000))
    }
}
