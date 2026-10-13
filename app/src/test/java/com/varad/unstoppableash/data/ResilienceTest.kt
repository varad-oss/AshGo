package com.varad.unstoppableash.data

import app.cash.turbine.test
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.domain.model.PairState
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review C1: a cancelled listener must never read as "nothing there" and must recover. */
class ResilienceTest {
    private val db = FakeRtdb(nowMs = 1_000L)

    @Test fun partnerSosKeepsLastValueOnFailureAndRecovers() = runTest {
        val sos = SosRepository(db, Clock { db.nowMs })
        db.set("pairs/p1/sos/s1", mapOf("from" to "bob", "state" to "active", "at" to 100L))
        sos.partnerActiveSos("p1", "alice").test {
            assertEquals("s1", awaitItem()?.id)
            db.failReads("pairs/p1/sos")
            advanceTimeBy(500)
            expectNoEvents() // never a fake "no SOS"
            db.clearFailures()
            db.set("pairs/p1/sos/s2", mapOf("from" to "bob", "state" to "active", "at" to 200L))
            advanceTimeBy(60_000)
            assertEquals("s2", expectMostRecentItem()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun deniedPairReadsAsUnpairedThenRecovers() = runTest {
        val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))
        val pairs = PairRepository(db, auth, Clock { db.nowMs })
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        pairs.pairState().test {
            assertTrue(awaitItem() is PairState.Paired)
            db.failReads("pairs/p1/meta")
            assertEquals(PairState.Unpaired, awaitItem())
            db.clearFailures()
            advanceTimeBy(60_000)
            assertTrue(expectMostRecentItem() is PairState.Paired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun readFailuresAreReportedForTheErrorBanner() = runTest {
        val sos = SosRepository(db, Clock { db.nowMs })
        db.readFailures.test {
            sos.partnerActiveSos("p1", "alice").test {
                db.failReads("pairs/p1/sos")
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(FailureReason.PERMISSION_DENIED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
