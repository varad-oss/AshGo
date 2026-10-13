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
import org.junit.Assert.assertEquals
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
}
