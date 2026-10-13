package com.varad.unstoppableash.data

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FirebaseAuthGateway
import com.varad.unstoppableash.data.chat.ChatRepository
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.FirebaseRtdbClient
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.Relationship
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Real Firebase SDK + real repositories + the real rules, against the local emulators (demo project only).
 * Run with: cd firebase && npx firebase emulators:exec --only auth,database --project demo-ashgo \
 *   "cd .. && ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.varad.unstoppableash.data.FirebaseEndToEndTest"
 * Skipped automatically when the emulators aren't reachable.
 */
class FirebaseEndToEndTest {
    private val host = "10.0.2.2"

    private class Member(val uid: String, val pairs: PairRepository, val chat: ChatRepository, val sos: SosRepository, val rtdb: FirebaseRtdbClient)

    @Before fun emulatorsUp() {
        assumeTrue("Firebase emulators not running on the host", reachable(9000) && reachable(9099))
    }

    private fun reachable(port: Int) = runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 1_000) } }.isSuccess

    private suspend fun member(name: String, email: String): Member {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val options = FirebaseOptions.Builder()
            .setProjectId("demo-ashgo").setApplicationId("1:1:android:1").setApiKey("demo-key")
            .setDatabaseUrl("https://demo-ashgo-default-rtdb.firebaseio.com").build()
        val app = FirebaseApp.initializeApp(ctx, options, "e2e-$name-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099) }
        val db = FirebaseDatabase.getInstance(app).apply { useEmulator(host, 9000) }
        val gateway = FirebaseAuthGateway(auth)
        // The Auth emulator accepts an unsigned JSON "ID token" for the google.com provider.
        val token = """{"sub":"$name-${UUID.randomUUID()}","email":"$email","email_verified":true}"""
        val user = (gateway.signInWithGoogleIdToken(token) as Outcome.Success).value
        assertTrue("$name signs in as a Google user", user.isGoogle)
        val rtdb = FirebaseRtdbClient(db)
        val clock = Clock { System.currentTimeMillis() }
        return Member(user.uid, PairRepository(rtdb, gateway, clock), ChatRepository(rtdb), SosRepository(rtdb), rtdb)
    }

    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = withTimeout(15_000) { first(predicate) }

    @Test fun pairChatSosAndOutsiderAgainstRealRules() = runBlocking {
        val run = UUID.randomUUID().toString().take(8)
        val alice = member("alice", "alice.$run@example.com")
        val bob = member("bob", "Bob.$run@Example.com")
        val carol = member("carol", "carol.$run@example.com")

        // Pairing: create → invite → join → confirm.
        val pairId = (alice.pairs.createPair(Relationship.PARTNER, "Alice") as Outcome.Success).value
        assertTrue(alice.pairs.invite("Bob.$run@Example.com", "Alice") is Outcome.Success)
        val invite = bob.pairs.myInvites().await { it.isNotEmpty() }.single()
        assertEquals("alice.$run@example.com", invite.fromEmail)
        assertTrue(bob.pairs.join(invite, "Bob") is Outcome.Success)
        alice.pairs.pairState().await { it is PairState.Confirming }
        assertTrue(alice.pairs.confirm(pairId) is Outcome.Success)
        val paired = bob.pairs.pairState().await { it is PairState.Paired } as PairState.Paired
        assertEquals("Alice", paired.partner?.name)

        // Chat round trip with a client id (retry must not duplicate).
        val id = alice.chat.newMessageId(pairId)
        assertTrue(alice.chat.send(pairId, alice.uid, id, "hello") is Outcome.Success)
        assertTrue(alice.chat.send(pairId, alice.uid, id, "hello") is Outcome.Success)
        val messages = bob.chat.messages(pairId).await { it.isNotEmpty() }
        assertEquals(listOf("hello"), messages.map { it.text })
        assertEquals(alice.uid, messages.single().from)

        // SOS: Bob raises, Alice acknowledges, only Bob can cancel.
        val sosId = bob.sos.newSosId(pairId)
        assertTrue(bob.sos.raise(pairId, bob.uid, sosId, location = null, battery = 40) is Outcome.Success)
        val active = alice.sos.partnerActiveSos(pairId, alice.uid).await { it != null }!!
        assertTrue(alice.sos.ack(pairId, active, alice.uid) is Outcome.Success)
        assertEquals(FailureReason.PERMISSION_DENIED, (alice.sos.cancel(pairId, sosId) as Outcome.Failure).reason)
        assertTrue(bob.sos.cancel(pairId, sosId) is Outcome.Success)
        alice.sos.partnerActiveSos(pairId, alice.uid).await { it == null }

        // Outsider: can't read or write anything in the pair.
        assertEquals(FailureReason.PERMISSION_DENIED, (carol.rtdb.get(DbPaths.chat(pairId)) as Outcome.Failure).reason)
        assertEquals(FailureReason.PERMISSION_DENIED, (carol.chat.send(pairId, carol.uid, "x", "hi") as Outcome.Failure).reason)
    }
}
