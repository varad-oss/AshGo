package com.varad.unstoppableash.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import com.varad.unstoppableash.core.result.FailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val db = FakeRtdb(nowMs = 1_000L)
    private val auth = FakeAuthGateway(FakeAuthGateway.google("alice"))

    private fun seed() = runBlocking {
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L, "confirmedAt" to 2L))
        db.set("pairs/p1/profiles", mapOf("alice" to mapOf("name" to "Alice", "email" to "alice@example.com"), "bob" to mapOf("name" to "Bob", "email" to "bob@example.com")))
        db.set("pairs/p1/presence/alice", mapOf("online" to true))
    }

    private fun kotlinx.coroutines.test.TestScope.vm(): SettingsViewModel {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { tmp.root.resolve("s.preferences_pb") }))
        return SettingsViewModel(PairRepository(db, auth, Clock { db.nowMs }), PresenceRepository(db), auth, store)
    }

    @Test fun showsProfileAndPartner() = runTest(main.dispatcher) {
        seed()
        val s = vm().state.first { it.partnerName.isNotEmpty() }
        assertEquals("Alice", s.myName); assertEquals("alice@example.com", s.myEmail); assertEquals("Bob", s.partnerName)
    }

    @Test fun themeChoiceIsRemembered() = runTest(main.dispatcher) {
        val vm = vm()
        vm.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.state.first { it.themeMode == ThemeMode.DARK }.themeMode)
    }

    @Test fun leaveDeletesThePair() = runTest(main.dispatcher) {
        seed()
        val vm = vm()
        vm.state.first { it.pairId != null }
        vm.leave()
        vm.state.first { it.pairId == null }
        assertNull(db.read("pairs/p1")); assertNull(db.read("userPairs/alice"))
    }

    @Test fun signOutGoesOfflineFirst() = runTest(main.dispatcher) {
        seed()
        db.onDisconnectWrites["pairs/p1/presence/alice"] = mapOf("online" to false)
        val vm = vm()
        vm.state.first { it.pairId != null }
        vm.signOut()
        vm.state.first { it.signedOut }
        assertEquals(false, db.read("pairs/p1/presence/alice/online"))
        assertNull(db.onDisconnectWrites["pairs/p1/presence/alice"])
        assertEquals("signing out stops sharing", "off", db.read("pairs/p1/presence/alice/sharing"))
        assertNull(auth.current())
    }
    @Test fun signOutFinishesEvenWhenTheServerIsUnreachable() = runTest(main.dispatcher) {
        seed()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} }
        vm.state.first { it.pairId != null }
        db.serverReachable.value = false
        vm.signOut()
        val during = vm.state.first { it.signingOut }
        assertFalse("leave button doesn't spin for a sign-out", during.busy)
        testScheduler.advanceTimeBy(SettingsViewModel.OFFLINE_GRACE_MS + 1); testScheduler.runCurrent()
        vm.state.first { it.signedOut && !it.signingOut }
        assertNull(auth.current())
    }

    @Test fun leaveOfflineSaysSoInsteadOfSpinningForever() = runTest(main.dispatcher) {
        seed()
        val vm = vm()
        backgroundScope.launch { vm.state.collect {} } // a visible screen keeps its state
        vm.state.first { it.pairId != null }
        db.serverReachable.value = false
        vm.leave()
        vm.state.first { it.busy }
        testScheduler.advanceTimeBy(SettingsViewModel.LEAVE_TIMEOUT_MS + 1); testScheduler.runCurrent()
        val s = vm.state.first { !it.busy }
        assertEquals(FailureReason.OFFLINE, s.failure)
    }
}
