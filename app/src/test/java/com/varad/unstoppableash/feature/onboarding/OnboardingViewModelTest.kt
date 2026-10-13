package com.varad.unstoppableash.feature.onboarding

import com.varad.unstoppableash.MainDispatcherRule
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.FakeAuthGateway
import com.varad.unstoppableash.data.firebase.FakeRtdb
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.domain.model.Relationship
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.emailKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val db = FakeRtdb(nowMs = 5_000_000L)
    private val aliceAuth = FakeAuthGateway()
    private val bobAuth = FakeAuthGateway(FakeAuthGateway.google("bob", "bob@example.com"))
    private fun vm(auth: FakeAuthGateway) = OnboardingViewModel(auth, PairRepository(db, auth, Clock { db.nowMs }))
    private suspend fun OnboardingViewModel.at(step: OnboardingStep) = state.first { it.step == step && !it.busy }

    @Test fun signInThenChoose() = runTest(main.dispatcher) {
        val vm = vm(aliceAuth)
        vm.at(OnboardingStep.SIGN_IN)
        vm.signIn("alice")
        assertEquals("alice@example.com", vm.at(OnboardingStep.CHOOSE).email)
    }

    @Test fun creatorFlowValidatesAndWaits() = runTest(main.dispatcher) {
        aliceAuth.user.value = FakeAuthGateway.google("alice")
        val vm = vm(aliceAuth)
        vm.at(OnboardingStep.CHOOSE)
        vm.chooseCreate()
        vm.at(OnboardingStep.CREATE_PROFILE)
        vm.create("Alice", Relationship.PARTNER)
        vm.at(OnboardingStep.INVITE)
        vm.invite("not-an-email")
        assertTrue(vm.state.first { it.emailError }.step == OnboardingStep.INVITE)
        vm.invite("bob@example.com")
        assertEquals("bob@example.com", vm.at(OnboardingStep.WAITING).sentInviteTo)
    }

    @Test fun joinerSeesInviterEmailThenWaitsForConfirmation() = runTest(main.dispatcher) {
        aliceAuth.user.value = FakeAuthGateway.google("alice")
        val alice = vm(aliceAuth)
        alice.at(OnboardingStep.CHOOSE); alice.chooseCreate(); alice.at(OnboardingStep.CREATE_PROFILE)
        alice.create("Alice", Relationship.FAMILY); alice.at(OnboardingStep.INVITE)
        alice.invite("bob@example.com"); alice.at(OnboardingStep.WAITING)

        val bob = vm(bobAuth)
        bob.at(OnboardingStep.CHOOSE)
        bob.chooseJoin()
        val invites = bob.state.first { it.step == OnboardingStep.INVITATIONS && it.invites.isNotEmpty() }.invites
        assertEquals("alice@example.com", invites.single().fromEmail)
        bob.join(invites.single(), "Bob")
        bob.at(OnboardingStep.AWAITING_CONFIRMATION)

        val confirm = alice.at(OnboardingStep.CONFIRM_PARTNER)
        assertEquals("bob@example.com", confirm.partner?.email)
        alice.confirm()
        alice.at(OnboardingStep.DONE)
        bob.at(OnboardingStep.DONE)
    }

    @Test fun notThemEndsTheOrbit() = runTest(main.dispatcher) {
        aliceAuth.user.value = FakeAuthGateway.google("alice")
        val alice = vm(aliceAuth)
        alice.at(OnboardingStep.CHOOSE); alice.chooseCreate(); alice.at(OnboardingStep.CREATE_PROFILE)
        alice.create("Alice", Relationship.PARTNER); alice.at(OnboardingStep.INVITE)
        alice.invite("bob@example.com"); alice.at(OnboardingStep.WAITING)
        val bob = vm(bobAuth)
        bob.at(OnboardingStep.CHOOSE); bob.chooseJoin()
        bob.join(bob.state.first { it.invites.isNotEmpty() }.invites.single(), "Bob")
        alice.at(OnboardingStep.CONFIRM_PARTNER)
        alice.notThem()
        alice.at(OnboardingStep.CHOOSE)
    }

    @Test fun failureKeepsTheStepWithAReason() = runTest(main.dispatcher) {
        aliceAuth.user.value = FakeAuthGateway.google("alice")
        val vm = vm(aliceAuth)
        vm.at(OnboardingStep.CHOOSE); vm.chooseCreate(); vm.at(OnboardingStep.CREATE_PROFILE)
        db.failWrites("pairs", FailureReason.OFFLINE)
        vm.create("Alice", Relationship.PARTNER)
        val s = vm.state.first { it.failure != null }
        assertEquals(OnboardingStep.CREATE_PROFILE, s.step); assertEquals(FailureReason.OFFLINE, s.failure)
    }

    @Test fun blankNameWithoutGoogleNameIsFlagged() = runTest(main.dispatcher) {
        aliceAuth.user.value = FakeAuthGateway.google("alice").copy(displayName = null)
        val vm = vm(aliceAuth)
        vm.at(OnboardingStep.CHOOSE); vm.chooseCreate(); vm.at(OnboardingStep.CREATE_PROFILE)
        vm.create("  ", Relationship.PARTNER)
        assertTrue(vm.state.first { it.nameError }.step == OnboardingStep.CREATE_PROFILE)
    }
    private suspend fun aliceInvitesBob(): OnboardingViewModel {
        aliceAuth.user.value = FakeAuthGateway.google("alice")
        val alice = vm(aliceAuth)
        alice.at(OnboardingStep.CHOOSE); alice.chooseCreate(); alice.at(OnboardingStep.CREATE_PROFILE)
        alice.create("Alice", Relationship.PARTNER); alice.at(OnboardingStep.INVITE)
        alice.invite("bob@example.com"); alice.at(OnboardingStep.WAITING)
        return alice
    }

    private fun pairIdOf(uid: String) = db.read("userPairs/$uid") as String?

    @Test fun creatorWhoMeantToJoinCanCancelFromInvite() = runTest(main.dispatcher) {
        aliceAuth.user.value = FakeAuthGateway.google("alice")
        val vm = vm(aliceAuth)
        vm.at(OnboardingStep.CHOOSE); vm.chooseCreate(); vm.at(OnboardingStep.CREATE_PROFILE)
        vm.create("Alice", Relationship.PARTNER); vm.at(OnboardingStep.INVITE)
        val pid = pairIdOf("alice")!!
        vm.cancelOrbit()
        vm.at(OnboardingStep.CHOOSE)
        assertNull(db.read("pairs/$pid")); assertNull(pairIdOf("alice"))
        vm.chooseJoin()
        vm.at(OnboardingStep.INVITATIONS)
    }

    @Test fun cancellingWhileWaitingAlsoWithdrawsTheInvite() = runTest(main.dispatcher) {
        val alice = aliceInvitesBob()
        val pid = pairIdOf("alice")!!
        assertNotNull(db.read(DbPaths.invite(emailKey("bob@example.com"), pid)))
        alice.cancelOrbit()
        alice.at(OnboardingStep.CHOOSE)
        assertNull(db.read(DbPaths.invite(emailKey("bob@example.com"), pid)))
    }

    @Test fun joinerCanGiveUpWaitingForConfirmation() = runTest(main.dispatcher) {
        val alice = aliceInvitesBob()
        val bob = vm(bobAuth)
        bob.at(OnboardingStep.CHOOSE); bob.chooseJoin()
        bob.join(bob.state.first { it.invites.isNotEmpty() }.invites.single(), "Bob")
        bob.at(OnboardingStep.AWAITING_CONFIRMATION)
        bob.cancelOrbit()
        bob.at(OnboardingStep.CHOOSE)
        alice.at(OnboardingStep.CHOOSE)
    }

    @Test fun signOutBeforePairingStartsAfresh() = runTest(main.dispatcher) {
        aliceAuth.user.value = FakeAuthGateway.google("alice")
        val vm = vm(aliceAuth)
        vm.at(OnboardingStep.CHOOSE); vm.chooseJoin(); vm.at(OnboardingStep.INVITATIONS)
        vm.signOut()
        vm.at(OnboardingStep.SIGN_IN)
        vm.signIn("carol")
        assertEquals("carol@example.com", vm.at(OnboardingStep.CHOOSE).email)
    }

    @Test fun cannotConfirmSomeoneWhoseIdentityHasNotLoaded() = runTest(main.dispatcher) {
        aliceAuth.user.value = FakeAuthGateway.google("alice")
        db.set("userPairs/alice", "p1")
        db.set("pairs/p1/meta", mapOf("memberA" to "alice", "memberB" to "bob", "relationship" to "PARTNER", "createdAt" to 1L))
        db.set("pairs/p1/profiles/alice", mapOf("name" to "Alice", "email" to "alice@example.com"))
        val vm = vm(aliceAuth)
        val s = vm.at(OnboardingStep.CONFIRM_PARTNER)
        assertFalse(s.canConfirm)
        vm.confirm()
        vm.at(OnboardingStep.CONFIRM_PARTNER)
        assertNull("nobody confirmed an unknown joiner", db.read("pairs/p1/meta/confirmedAt"))
        db.set("pairs/p1/profiles/bob", mapOf("name" to "Bob", "email" to "bob@example.com"))
        assertTrue(vm.state.first { it.partner?.email != null }.canConfirm)
    }
}
