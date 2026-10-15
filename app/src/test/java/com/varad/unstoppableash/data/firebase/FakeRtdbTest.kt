package com.varad.unstoppableash.data.firebase

import app.cash.turbine.test
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Contract tests for the in-memory fixture every repository test relies on. */
class FakeRtdbTest {
    private val db = FakeRtdb(nowMs = 5_000L)

    @Test fun setObserveUpdateRemove() = runTest {
        db.observe("a/b").test {
            assertNull(awaitItem())
            db.set("a/b", mapOf("x" to 1L))
            assertEquals(mapOf("x" to 1L), awaitItem())
            db.update("a/b", mapOf("y" to "z", "n/m" to true))
            assertEquals(mapOf("x" to 1L, "y" to "z", "n" to mapOf("m" to true)), awaitItem())
            db.remove("a/b/x")
            assertEquals(mapOf("y" to "z", "n" to mapOf("m" to true)), awaitItem())
            db.remove("a")
            assertNull(awaitItem())
        }
    }

    @Test fun serverTimestampBecomesFakeNow() = runTest {
        db.set("t", mapOf("at" to db.serverTimestamp))
        assertEquals(mapOf("at" to 5_000L), db.observe("t").first())
    }

    @Test fun observerCountReturnsToZeroAfterCancel() = runTest {
        db.observe("a").test {
            awaitItem()
            assertEquals(1, db.activeObservers)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, db.activeObservers)
    }

    @Test fun injectedFailureIsTyped() = runTest {
        db.failWrites("pairs/p1", FailureReason.PERMISSION_DENIED)
        assertEquals(Outcome.Failure(FailureReason.PERMISSION_DENIED), db.set("pairs/p1/chat/m1", "x"))
        assertNull(db.observe("pairs/p1/chat/m1").first())
    }

    @Test fun observeLastOrdersByChildAndLimits() = runTest {
        db.set("c", mapOf("k1" to mapOf("at" to 3L), "k2" to mapOf("at" to 1L), "k3" to mapOf("at" to 2L)))
        val last2 = db.observeLast("c", limit = 2, orderByChild = "at").first() as Map<*, *>
        assertEquals(listOf("k3", "k1"), last2.keys.toList())
    }
}
