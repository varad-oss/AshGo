package com.varad.unstoppableash.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.auth.AuthGateway
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.domain.model.Invite
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.Profile
import com.varad.unstoppableash.domain.model.Relationship
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** USER_JOURNEYS J2–J3. */
enum class OnboardingStep { SIGN_IN, CHOOSE, CREATE_PROFILE, INVITE, WAITING, INVITATIONS, CONFIRM_PARTNER, AWAITING_CONFIRMATION, DONE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.SIGN_IN,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val email: String? = null,
    val suggestedName: String = "",
    val invites: List<Invite> = emptyList(),
    val partner: Profile? = null,
    val sentInviteTo: String? = null,
    val nameError: Boolean = false,
    val emailError: Boolean = false,
    val failure: FailureReason? = null,
)

/**
 * The step is derived from the real pair state (so it can never disagree with the backend) plus a
 * little local intent: which branch the person chose, and whether they've sent an invite this session.
 */
class OnboardingViewModel(
    private val auth: AuthGateway,
    private val pairs: PairRepository,
) : ViewModel() {

    private enum class Branch { NONE, CREATE, JOIN }

    private data class Local(
        val branch: Branch = Branch.NONE,
        val sentInviteTo: String? = null,
        val busy: Boolean = false,
        val nameError: Boolean = false,
        val emailError: Boolean = false,
        val failure: FailureReason? = null,
    )

    private val local = MutableStateFlow(Local())
    @Volatile private var pair: PairState? = null

    val state: StateFlow<OnboardingUiState> =
        combine(auth.currentUser, pairs.pairState(), pairs.myInvites(), local) { user, p, invites, l ->
            pair = p
            val step = when (p) {
                PairState.NotSignedIn -> OnboardingStep.SIGN_IN
                PairState.Unpaired -> when (l.branch) {
                    Branch.CREATE -> OnboardingStep.CREATE_PROFILE
                    Branch.JOIN -> OnboardingStep.INVITATIONS
                    Branch.NONE -> OnboardingStep.CHOOSE
                }
                is PairState.Waiting -> if (l.sentInviteTo == null) OnboardingStep.INVITE else OnboardingStep.WAITING
                is PairState.Confirming -> if (p.iMustConfirm) OnboardingStep.CONFIRM_PARTNER else OnboardingStep.AWAITING_CONFIRMATION
                is PairState.Paired -> OnboardingStep.DONE
            }
            OnboardingUiState(
                step = step,
                loading = false,
                busy = l.busy,
                email = user?.email,
                suggestedName = user?.displayName.orEmpty(),
                invites = invites,
                partner = (p as? PairState.Confirming)?.partner,
                sentInviteTo = l.sentInviteTo,
                nameError = l.nameError,
                emailError = l.emailError,
                failure = l.failure,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun signIn(googleIdToken: String) = act { auth.signInWithGoogleIdToken(googleIdToken) }

    fun chooseCreate() = local.update { Local(branch = Branch.CREATE) }
    fun chooseJoin() = local.update { Local(branch = Branch.JOIN) }
    fun back() = local.update { Local() }
    fun inviteAgain() = local.update { it.copy(sentInviteTo = null) }
    fun clearFailure() = local.update { it.copy(failure = null) }

    fun create(name: String, relationship: Relationship) {
        if (!nameIsUsable(name)) return local.update { it.copy(nameError = true) }
        act { pairs.createPair(relationship, name) }
    }

    fun invite(email: String) {
        if (!PairRepository.isPlausibleEmail(email.trim())) return local.update { it.copy(emailError = true) }
        val myName = (pair as? PairState.Waiting)?.me?.name ?: auth.current()?.displayName.orEmpty()
        act(onSuccess = { it.copy(sentInviteTo = email.trim()) }) { pairs.invite(email, myName) }
    }

    fun join(invite: Invite, name: String) {
        if (!nameIsUsable(name)) return local.update { it.copy(nameError = true) }
        act { pairs.join(invite, name) }
    }

    fun decline(invite: Invite) = act { pairs.declineInvite(invite) }

    fun confirm() {
        val p = pair as? PairState.Confirming ?: return
        act { pairs.confirm(p.meta.pairId) }
    }

    /** "That's not them": ends the orbit so the inviter can start again (D-16). */
    fun notThem() {
        val p = pair as? PairState.Confirming ?: return
        act(onSuccess = { Local() }) { pairs.leave(p.meta.pairId) }
    }

    private fun nameIsUsable(typed: String): Boolean {
        val user = auth.current() ?: return false
        return PairRepository.displayName(typed, user) != null
    }

    private fun act(onSuccess: (Local) -> Local = { it }, block: suspend () -> Outcome<*>) {
        local.update { it.copy(busy = true, failure = null, nameError = false, emailError = false) }
        viewModelScope.launch {
            when (val r = block()) {
                is Outcome.Success -> local.update { onSuccess(it).copy(busy = false) }
                is Outcome.Failure -> local.update { it.copy(busy = false, failure = r.reason) }
            }
        }
    }
}
