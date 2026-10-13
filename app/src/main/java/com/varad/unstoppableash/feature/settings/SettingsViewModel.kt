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
)

/** Settings shell for Stage 5: profile summary, appearance, leave orbit, sign out. */
class SettingsViewModel(
    private val pairs: PairRepository,
    private val presence: PresenceRepository,
    private val auth: AuthGateway,
    private val settings: SettingsStore,
) : ViewModel() {

    private data class Local(val busy: Boolean = false, val failure: FailureReason? = null)
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.update { it.copy(themeMode = mode.name) } }

    fun leave() {
        val pairId = pairIdOf(current) ?: return
        act { pairs.leave(pairId) }
    }

    fun signOut() {
        val pair = current
        act {
            val uid = auth.current()?.uid
            val pairId = pairIdOf(pair)
            if (uid != null && pairId != null) presence.goOffline(pairId, uid)
            auth.signOut()
            Outcome.Success(Unit)
        }
    }

    fun clearFailure() = local.update { it.copy(failure = null) }

    private fun pairIdOf(p: PairState?): String? = when (p) {
        is PairState.Paired -> p.meta.pairId
        is PairState.Confirming -> p.meta.pairId
        is PairState.Waiting -> p.meta.pairId
        else -> null
    }

    private fun act(block: suspend () -> Outcome<*>) {
        local.update { Local(busy = true) }
        viewModelScope.launch {
            val r = block()
            local.update { Local(failure = (r as? Outcome.Failure)?.reason) }
        }
    }
}
