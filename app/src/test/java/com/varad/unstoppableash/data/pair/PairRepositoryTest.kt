package com.varad.unstoppableash.data.pair

import app.cash.turbine.test
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.AuthUser
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.Relationship
import com.varad.unstoppableash.domain.time.Durations.DAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import kotlinx.coroutines.launch
import org.junit.Assert.assertNotNull
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.emailKey
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairRepositoryTest {
    private val db = FakeRtdb(nowMs = 10 * DAY)
    private val alice = FakeAuthGateway.google("alice")
    private val bob = FakeAuthGateway.google("bob", "Bob.Smith@example.com")
    private val auth = FakeAuthGateway(alice)
    private val repo = PairRepository(db, auth, Clock { db.nowMs })

    private suspend fun created(): String = (repo.createPair(Relationship.PARTNER, "Alice") as Outcome.Success).value

    private suspend fun joinedAsBob(pairId: String) {
        repo.invite("Bob.Smith@example.com", "Alice")
        auth.user.value = bob
        val invite = repo.myInvites().first().single()
        assertEquals(pairId, invite.pairId)
        assertTrue(repo.join(invite, "Bob") is Outcome.Success)
    }

    @Test fun createPairWritesMetaThenPointerThenProfile() = runTest {
        val pid = created()
        assertEquals(listOf("set pairs/$pid/meta", "set userPairs/alice", "set pairs/$pid/profiles/alice"), db.writeLog)
        assertEquals(mapOf("memberA" to "alice", "relationship" to "PARTNER", "createdAt" to db.nowMs), db.read("pairs/$pid/meta"))
        assertEquals(mapOf("name" to "Alice", "email" to "alice@example.com", "greeting" to true), db.read("pairs/$pid/profiles/alice"))
        assertEquals(pid, db.read("userPairs/alice"))
    }

    @Test fun createPairNeedsGoogleSignIn() = runTest {
        auth.user.value = null
        assertEquals(FailureReason.NOT_SIGNED_IN, (repo.createPair(Relationship.PARTNER, "A") as Outcome.Failure).reason)
        auth.user.value = AuthUser("anon", null, false, null, null, isGoogle = false)
        assertEquals(FailureReason.NOT_SIGNED_IN, (repo.createPair(Relationship.PARTNER, "A") as Outcome.Failure).reason)
        assertTrue(db.writeLog.isEmpty())
    }

    @Test fun inviteCarriesRealEmailAndExpiresWithinADay() = runTest {
        val pid = created()
        assertTrue(repo.invite(" Bob.Smith@Example.com ", "Alice") is Outcome.Success)
        @Suppress("UNCHECKED_CAST")
        val inv = db.read("invites/bob,smith@example,com/$pid") as Map<String, Any?>
        assertEquals("alice", inv["fromUid"]); assertEquals("Alice", inv["fromName"]); assertEquals("alice@example.com", inv["fromEmail"])
        assertEquals(db.nowMs, inv["createdAt"])
        val expires = inv["expiresAt"] as Long
        assertTrue(expires > db.nowMs && expires <= db.nowMs + DAY)
    }

    @Test fun malformedEmailIsRejectedWithoutWriting() = runTest {
        created(); db.writeLog.clear()
        for (bad in listOf("", "bob", "bob@", "@x.com", "bob@x", "a b@x.com")) {
            assertEquals(bad, FailureReason.INVALID, (repo.invite(bad, "Alice") as Outcome.Failure).reason)
        }
        assertTrue(db.writeLog.isEmpty())
    }

    @Test fun joinWritesMemberProfilePointerThenDeletesInvite() = runTest {
        val pid = created(); db.writeLog.clear()
        joinedAsBob(pid)
        assertEquals(
            listOf("set invites/bob,smith@example,com/$pid", "set pairs/$pid/meta/memberB", "set userPairs/bob", "set pairs/$pid/profiles/bob", "remove invites/bob,smith@example,com/$pid"),
            db.writeLog,
        )
    }

    @Test fun confirmSetsServerTime() = runTest {
        val pid = created(); joinedAsBob(pid)
        auth.user.value = alice
        assertTrue(repo.confirm(pid) is Outcome.Success)
        assertEquals(db.nowMs, db.read("pairs/$pid/meta/confirmedAt"))
    }

    @Test fun leaveRemovesPairAndOwnPointer() = runTest {
        val pid = created()
        assertTrue(repo.leave(pid) is Outcome.Success)
        assertNull(db.read("pairs/$pid")); assertNull(db.read("userPairs/alice"))
    }

    @Test fun leaveOfflineHandsEveryDeleteToTheSdkAtOnce() = runTest {
        val pid = created()
        db.serverReachable.value = false
        val job = launch { repo.leave(pid) }
        testScheduler.runCurrent()
        // Offline the SDK queues writes; a caller that gives up waiting must not leave half of them unsent.
        assertTrue(db.issued.contains("remove pairs/$pid"))
        assertTrue(db.issued.contains("remove userPairs/alice"))
        job.cancel()
    }

    @Test fun withdrawInviteRemovesIt() = runTest {
        val pid = created()
        repo.invite("Bob.Smith@example.com", "Alice")
        assertNotNull(db.read(DbPaths.invite(emailKey("bob.smith@example.com"), pid)))
        assertTrue(repo.withdrawInvite("Bob.Smith@example.com", pid) is Outcome.Success)
        assertNull(db.read(DbPaths.invite(emailKey("bob.smith@example.com"), pid)))
    }

    @Test fun pairStateWalksTheWholeFlow() = runTest {
        repo.pairState().test {
            assertEquals(PairState.Unpaired, awaitItem())
            val pid = created()
            assertTrue(expectMostRecentItem() is PairState.Waiting)
            joinedAsBob(pid)
            auth.user.value = alice
            val confirming = expectMostRecentItem() as PairState.Confirming
            assertEquals("Bob", confirming.partner?.name)
            assertEquals("Bob.Smith@example.com", confirming.partner?.email)
            repo.confirm(pid)
            val paired = expectMostRecentItem() as PairState.Paired
            assertEquals("alice", paired.myUid); assertEquals("bob", paired.partnerUid); assertEquals("Bob", paired.partner?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun joinResumesAfterAPartialFailure() = runTest {
        db.createOnly("pairs/[^/]+/meta/memberB")
        val pid = created()
        repo.invite("Bob.Smith@example.com", "Alice")
        auth.user.value = bob
        val invite = repo.myInvites().first().single()
        db.failWrites("userPairs/bob", FailureReason.OFFLINE)
        assertTrue(repo.join(invite, "Bob") is Outcome.Failure)
        db.clearFailures()
        assertTrue(repo.join(invite, "Bob") is Outcome.Success)
        assertEquals(1, db.writeLog.count { it == "set pairs/$pid/meta/memberB" })
        assertEquals(pid, db.read("userPairs/bob"))
    }

    @Test fun createPairResumesInsteadOfOrphaning() = runTest {
        db.failWrites("pairs", FailureReason.OFFLINE)
        db.clearFailures()
        db.failWrites("userPairs/alice", FailureReason.OFFLINE)
        assertTrue(repo.createPair(Relationship.PARTNER, "Alice") is Outcome.Failure)
        db.clearFailures()
        val first = db.writeLog.count { it.endsWith("/meta") }
        val pid = created()
        assertEquals(pid, db.read("userPairs/alice"))
        val metas = (db.read("pairs") as Map<*, *>).keys
        assertEquals("no orphan pair left behind", 1, metas.size)
        assertTrue(first <= 1)
    }

    @Test fun blankNameWithoutGoogleNameIsRejectedBeforeWriting() = runTest {
        auth.user.value = alice.copy(displayName = null)
        assertEquals(FailureReason.INVALID, (repo.createPair(Relationship.PARTNER, "   ") as Outcome.Failure).reason)
        assertTrue(db.writeLog.isEmpty())
    }

    @Test fun leaveCancelsThePresenceDisconnectHandler() = runTest {
        val pid = created()
        com.varad.unstoppableash.data.presence.PresenceRepository(db).goOnline(pid, "alice")
        repo.leave(pid)
        db.disconnect()
        assertNull("no presence residue after leaving", db.read("pairs/$pid"))
    }

    @Test fun signedOutIsNotSignedIn() = runTest {
        auth.user.value = null
        assertEquals(PairState.NotSignedIn, repo.pairState().first())
    }

    @Test fun stalePointerToDeletedPairIsUnpaired() = runTest {
        db.set("userPairs/alice", "gone")
        assertEquals(PairState.Unpaired, repo.pairState().first())
    }

    @Test fun deniedPairReadIsUnpairedNotACrash() = runTest {
        db.set("userPairs/alice", "p9")
        db.set("pairs/p9/meta", mapOf("memberA" to "x", "relationship" to "PARTNER", "createdAt" to 1L))
        db.failReads("pairs/p9")
        assertEquals(PairState.Unpaired, repo.pairState().first())
    }

    @Test fun listenersAreReleased() = runTest {
        created()
        repo.pairState().test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        assertEquals(0, db.activeObservers)
    }

    @Test fun whenThePartnerEndsTheOrbitMyLeftoverPointerIsClearedAndICanStartAgain() = runTest {
        val pid = created()
        joinedAsBob(pid)
        auth.user.value = alice; repo.confirm(pid)
        auth.user.value = bob
        assertTrue(repo.leave(pid) is Outcome.Success)
        auth.user.value = alice
        // Real server: the deleted pair's meta is no longer readable at all.
        db.failReads("pairs/$pid")
        assertEquals(PairState.Unpaired, repo.pairState().first { it == PairState.Unpaired })
        repo.pairState().first { db.read("userPairs/alice") == null }
        assertNull("my pointer to the dead orbit is gone", db.read("userPairs/alice"))
        val again = repo.createPair(Relationship.PARTNER, "Alice")
        assertTrue("a new orbit can be started", again is Outcome.Success)
        assertEquals((again as Outcome.Success).value, db.read("userPairs/alice"))
    }

    @Test fun aPointerIsNotClearedWhenTheServerCannotBeReached() = runTest {
        val pid = created()
        db.failReads("pairs/$pid")
        db.serverReachable.value = false
        assertEquals(PairState.Unpaired, repo.pairState().first())
        assertEquals("offline proves nothing: keep it", pid, db.read("userPairs/alice"))
    }
}
