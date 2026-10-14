package com.varad.unstoppableash.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.data.auth.AuthGateway
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.media.PhotoEncoder
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
import java.util.Base64

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
    val greeting: Boolean = true,
    val relationship: String? = null,
    val notifyChat: Boolean = true,
    val notifyNudges: Boolean = true,
    val notifyCheckins: Boolean = true,
    val savingAvatar: Boolean = false,
    val deleting: Boolean = false,
    /** How many of my messages and photos "Delete my messages" removed; null until it has run. */
    val deletedCount: Int? = null,
    /** I created the orbit: I watch over the other person (roles). */
    val watcher: Boolean = false,
    /** Keep following their live location with AshGo cleared (trips always count). */
    val followSharing: Boolean = true,
)

enum class NotifyToggle { CHAT, NUDGES, CHECKINS }

/** Settings (REDESIGN_SPEC §Settings): profile, orbit, notifications, appearance, privacy, sign out. */
class SettingsViewModel(
    private val pairs: PairRepository,
    private val presence: PresenceRepository,
    private val auth: AuthGateway,
    private val settings: SettingsStore,
    private val avatarEncoder: PhotoEncoder = PhotoEncoder { null },
    private val deleteMyMessages: suspend () -> Int = { 0 },
) : ViewModel() {

    private data class Local(
        val busy: Boolean = false,
        val signingOut: Boolean = false,
        val failure: FailureReason? = null,
        val savingAvatar: Boolean = false,
        val deleting: Boolean = false,
        val deletedCount: Int? = null,
    )
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
            greeting = me?.greeting ?: true,
            relationship = (pair as? PairState.Paired)?.meta?.relationship?.name,
            notifyChat = prefs.notifyChat,
            notifyNudges = prefs.notifyNudges,
            notifyCheckins = prefs.notifyCheckins,
            savingAvatar = l.savingAvatar,
            deleting = l.deleting,
            deletedCount = l.deletedCount,
            watcher = (pair as? PairState.Paired)?.isWatcher == true,
            followSharing = prefs.followPartnerSharing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), SettingsUiState())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.update { it.copy(themeMode = mode.name) } }

    fun rename(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > NAME_MAX) return
        profile(mapOf("name" to clean))
    }

    fun setGreeting(on: Boolean) = profile(mapOf("greeting" to on))

    fun removeAvatar() = profile(mapOf("avatar" to null))

    /** Shrunk to 256 px / 30 KB on this phone before anything is sent (D-5). */
    fun setAvatar(uri: String) {
        local.update { it.copy(savingAvatar = true, failure = null) }
        viewModelScope.launch {
            val photo = avatarEncoder.encode(uri)
            val r = if (photo == null) Outcome.Failure(FailureReason.INVALID) else writeProfile(mapOf("avatar" to Base64.getEncoder().encodeToString(photo.jpeg)))
            local.update { it.copy(savingAvatar = false, failure = (r as? Outcome.Failure)?.reason) }
        }
    }

    fun setNotify(toggle: NotifyToggle, on: Boolean) = viewModelScope.launch {
        settings.update {
            when (toggle) {
                NotifyToggle.CHAT -> it.copy(notifyChat = on)
                NotifyToggle.NUDGES -> it.copy(notifyNudges = on)
                NotifyToggle.CHECKINS -> it.copy(notifyCheckins = on)
            }
        }
    }

    fun setFollowSharing(on: Boolean) = viewModelScope.launch { settings.update { it.copy(followPartnerSharing = on) } }

    fun deleteMyMessages() {
        local.update { it.copy(deleting = true, deletedCount = null) }
        viewModelScope.launch {
            val n = deleteMyMessages.invoke()
            local.update { it.copy(deleting = false, deletedCount = n) }
        }
    }

    private fun profile(fields: Map<String, Any?>) = act { writeProfile(fields) }

    private suspend fun writeProfile(fields: Map<String, Any?>): Outcome<Unit> {
        val pairId = pairIdOf(current) ?: return Outcome.Failure(FailureReason.NOT_PAIRED)
        val uid = auth.current()?.uid ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        return withTimeoutOrNull(LEAVE_TIMEOUT_MS) { pairs.updateProfile(pairId, uid, fields) } ?: Outcome.Failure(FailureReason.OFFLINE)
    }

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
            if (uid != null && pairId != null) withTimeoutOrNull(OFFLINE_GRACE_MS) {
                // Signing out stops sharing: nobody expects a signed-out phone to keep reporting where it is.
                presence.setSharing(pairId, uid, com.varad.unstoppableash.domain.model.SharingMode.OFF, null)
                presence.goOffline(pairId, uid)
            }
            settings.update { it.copy(shareFromThisPhone = false) }
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
        const val NAME_MAX = 40
    }

    private fun act(block: suspend () -> Outcome<*>) {
        local.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            val r = block()
            local.update { it.copy(busy = false, failure = (r as? Outcome.Failure)?.reason) }
        }
    }
}
