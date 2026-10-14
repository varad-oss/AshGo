package com.varad.unstoppableash.service.connect

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.trips.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackgroundWatchTest {
    @get:Rule val tmp = TemporaryFolder()
    private val db = FakeRtdb(nowMs = 1_000_000L)

    private fun seed(me: String) = runBlocking {
        db.set("userPairs/$me", "p1")
        // alice created the orbit (watcher); bob joined.
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
    }

    private fun TestScope.watch(me: String): Pair<BackgroundWatch, SettingsStore> {
        val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("$me.preferences_pb") })
        val clock = Clock { db.nowMs }
        return BackgroundWatch(PairRepository(db, FakeAuthGateway(FakeAuthGateway.google(me)), clock), TripRepository(db), PresenceRepository(db), settings, clock, MutableStateFlow(false)) to settings
    }

    @Test fun theWatcherFollowsTheirTripAndLetsGoWhenItEnds() = runTest {
        seed("alice")
        val (w, _) = watch("alice")
        w.needed().first { !it }
        db.set("pairs/p1/trips/bob", mapOf("state" to "active", "startedAt" to 1L, "notifyPartner" to true))
        w.needed().first { it }
        db.set("pairs/p1/trips/bob/state", "arrived")
        w.needed().first { !it }
    }

    @Test fun theWatcherFollowsTheirLiveSharingOnlyIfTheyChoseTo() = runTest {
        seed("alice")
        val (w, settings) = watch("alice")
        db.set("pairs/p1/presence/bob", mapOf("sharing" to "live"))
        w.needed().first { it }
        settings.update { it.copy(followPartnerSharing = false) }
        w.needed().first { !it }
    }

    @Test fun notPairedNeverKeepsRunning() = runTest {
        val (w, _) = watch("carol")
        w.needed().first { !it }
    }
}
