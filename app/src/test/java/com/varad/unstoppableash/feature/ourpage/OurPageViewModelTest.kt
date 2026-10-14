package com.varad.unstoppableash.feature.ourpage

import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.media.EncodedPhoto
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.wall.WallRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OurPageViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()
    private val db = FakeRtdb(nowMs = 1_000L)

    private fun seed() = runBlocking {
        db.set("userPairs/alice", "p1"); db.set("userPairs/bob", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Alice"), "bob" to mapOf("name" to "Bob")))
    }

    private fun TestScope.vmFor(uid: String, cache: String = uid): OurPageViewModel {
        val auth = FakeAuthGateway(FakeAuthGateway.google(uid))
        val vm = OurPageViewModel(
            PairRepository(db, auth, Clock { db.nowMs }), WallRepository(db),
            encode = { uri -> if (uri == "bad") null else EncodedPhoto(uri.toByteArray(), 800, 600) },
            cacheDir = tmp.root.resolve("cache-$cache"), io = UnconfinedTestDispatcher(testScheduler),
        )
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test fun aPhotoOneAddsAppearsOnTheOthersPhone() = runTest(main.dispatcher) {
        seed()
        val alice = vmFor("alice"); val bob = vmFor("bob")
        alice.add(listOf("content://beach"))
        val seen = bob.state.first { s -> s.photos.any { it.file != null } }.photos.single()
        assertEquals("alice", seen.from)
        assertEquals("content://beach", java.io.File(seen.file!!).readText())
    }

    @Test fun eitherOfThemCanTakeAnyPhotoDown() = runTest(main.dispatcher) {
        seed()
        val alice = vmFor("alice"); val bob = vmFor("bob")
        alice.add(listOf("content://a"))
        val id = bob.state.first { it.photos.isNotEmpty() }.photos.single().id
        bob.remove(id)
        alice.state.first { it.photos.isEmpty() }
        assertEquals(null, db.read("pairs/p1/wallData/$id"))
    }

    @Test fun unreadablePhotosAreSkippedAndSaidSo() = runTest(main.dispatcher) {
        seed()
        val alice = vmFor("alice")
        alice.add(listOf("bad", "content://ok"))
        val s = alice.state.first { it.photos.size == 1 && !it.adding }
        assertEquals(1, s.skipped)
    }

    @Test fun theWallIsCapped() = runTest(main.dispatcher) {
        seed()
        val alice = vmFor("alice")
        alice.add((1..WallRepository.MAX + 3).map { "content://p$it" })
        val s = alice.state.first { !it.adding && it.photos.size == WallRepository.MAX }
        assertTrue(s.full)
        assertEquals(3, s.skipped)
    }

    @Test fun aPhotoIsDownloadedOnceThenServedFromThisPhone() = runTest(main.dispatcher) {
        seed()
        vmFor("alice").add(listOf("content://x"))
        val bob = vmFor("bob", cache = "bob")
        bob.state.first { s -> s.photos.any { it.file != null } }
        val reads = db.getCount
        val again = vmFor("bob", cache = "bob")
        again.state.first { s -> s.photos.any { it.file != null } }
        assertEquals("cached: no second download", reads, db.getCount)
    }
}
