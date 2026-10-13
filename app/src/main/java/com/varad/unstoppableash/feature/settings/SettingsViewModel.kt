package com.varad.unstoppableash.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.auth.AuthGateway
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class SettingsUiState(
    val myName: String = "",
    val myEmail: String? = null,
    val myAvatar: String? = null,
    val partnerName: String = "",
    val pairId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val busy: Boolean = false,
    val failure: FailureReason? = null,
    val signedOut: Boolean = false,
    val signingOut: Boolean = false,
)

/** Settings shell for Stage 5: profile summary, appearance, leave orbit, sign out. */
class SettingsViewModel(
    private val pairs: PairRepository,
    private val presence: PresenceRepository,
    private val auth: AuthGateway,
    private val settings: SettingsStore,
) : ViewModel() {

    private data class Local(val busy: Boolean = false, val signingOut: Boolean = false, val failure: FailureReason? = null)
    private val local = MutableStateFlow(Local())
    @Volatile private var current: PairState? = null

    val state: StateFlow<SettingsUiState> = combine(auth.currentUser, pairs.pairState(), settings.settings, local) { user, pair, prefs, l ->
        current = pair
        val (me, partner, pairId) = when (pair) {
            is PairState.Paired -> Triple(pair.me, pair.partner, pair.meta.pairId)
            is PairState.Confirming -> Triple(pair.me, pair.partner, pair.meta.pairId)
            is PairState.Waiting -> Triple(pair.me, null, pair.meta.pairId)
            else -> Triple(null, null, null)
        }
        SettingsUiState(
            myName = me?.name ?: user?.displayName.orEmpty(),
            myEmail = user?.email,
            myAvatar = me?.avatar,
            partnerName = partner?.name.orEmpty(),
            pairId = pairId,
            themeMode = runCatching { ThemeMode.valueOf(prefs.themeMode) }.getOrDefault(ThemeMode.SYSTEM),
            busy = l.busy,
            failure = l.failure,
            signedOut = user == null,
            signingOut = l.signingOut,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), SettingsUiState())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.update { it.copy(themeMode = mode.name) } }

    fun leave() {
        val pairId = pairIdOf(current) ?: return
        // Offline the deletes stay queued in the SDK and finish on reconnect; say so rather than spin.
        act { withTimeoutOrNull(LEAVE_TIMEOUT_MS) { pairs.leave(pairId) } ?: Outcome.Failure(FailureReason.OFFLINE) }
    }

    /** Going offline is a courtesy; signing out must never wait on the network. */
    fun signOut() {
        val pairId = pairIdOf(current)
        local.update { it.copy(signingOut = true, failure = null) }
        viewModelScope.launch {
            val uid = auth.current()?.uid
            if (uid != null && pairId != null) withTimeoutOrNull(OFFLINE_GRACE_MS) { presence.goOffline(pairId, uid) }
            auth.signOut()
            local.update { it.copy(signingOut = false) }
        }
    }

    fun clearFailure() = local.update { it.copy(failure = null) }

    private fun pairIdOf(p: PairState?): String? = when (p) {
        is PairState.Paired -> p.meta.pairId
        is PairState.Confirming -> p.meta.pairId
        is PairState.Waiting -> p.meta.pairId
        else -> null
    }

    companion object {
        const val OFFLINE_GRACE_MS = 3_000L
        const val LEAVE_TIMEOUT_MS = 10_000L
    }

    private fun act(block: suspend () -> Outcome<*>) {
        local.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            val r = block()
            local.update { it.copy(busy = false, failure = (r as? Outcome.Failure)?.reason) }
        }
    }
}
