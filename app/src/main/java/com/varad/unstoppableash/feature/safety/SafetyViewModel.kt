package com.varad.unstoppableash.feature.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.data.local.EmergencyContactsStore
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.SosState
import com.varad.unstoppableash.service.sos.FalseAlarm
import com.varad.unstoppableash.service.sos.SosControl
import com.varad.unstoppableash.service.sos.SosLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LocationAccess { PRECISE, APPROXIMATE, OFF }

data class SafetyPermissions(val sms: Boolean, val location: LocationAccess)

data class SafetyUiState(
    val loading: Boolean = true,
    val paired: Boolean = false,
    val partnerName: String = "",
    val partnerPhone: String? = null,
    val contacts: List<EmergencyContact> = emptyList(),
    val permissions: SafetyPermissions = SafetyPermissions(sms = false, location = LocationAccess.OFF),
    val sos: SosState = SosState.Idle,
    val location: SosLocation = SosLocation.Unavailable,
    val raising: Boolean = false,
    /** Non-null while the "tell them it was a false alarm?" confirmation is open. */
    val falseAlarmRecipients: List<EmergencyContact>? = null,
    val falseAlarm: FalseAlarm? = null,
    /** I created the orbit: SOS lives on the other person's phone, not mine (roles). */
    val watcher: Boolean = false,
) {
    fun contactName(id: String): String = contacts.firstOrNull { it.id == id }?.name ?: ""
}

/**
 * Safety tab (REDESIGN_SPEC §Safety, §SosActive). "What happens" is built only from real state — the
 * orbit, the contacts on this phone and the permissions Android reports — never from what we hope.
 */
class SafetyViewModel(
    pairs: PairRepository,
    contacts: EmergencyContactsStore,
    private val settings: SettingsStore,
    private val sos: SosControl,
    private val permissions: () -> SafetyPermissions,
) : ViewModel() {

    private data class Local(val perms: SafetyPermissions, val raising: Boolean = false, val falseAlarm: List<EmergencyContact>? = null)

    private val local = MutableStateFlow(Local(permissions()))

    val state: StateFlow<SafetyUiState> = combine(
        // Offline with nothing cached the orbit may never load; everything else must still show.
        pairs.pairState().map<PairState, PairState?> { it }.onStart { emit(null) },
        contacts.contacts, settings.settings, sos.state, sos.location, local, sos.falseAlarm,
    ) { v ->
        val pair = v[0] as PairState?
        @Suppress("UNCHECKED_CAST") val list = v[1] as List<EmergencyContact>
        val prefs = v[2] as com.varad.unstoppableash.data.local.Settings
        val l = v[5] as Local
        SafetyUiState(
            loading = false,
            paired = pair is PairState.Paired,
            partnerName = (pair as? PairState.Paired)?.partner?.name.orEmpty(),
            watcher = (pair as? PairState.Paired)?.isWatcher == true,
            partnerPhone = prefs.partnerPhone,
            contacts = list,
            permissions = l.perms,
            sos = v[3] as SosState,
            location = v[4] as SosLocation,
            raising = l.raising,
            falseAlarmRecipients = l.falseAlarm,
            falseAlarm = v[6] as FalseAlarm?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), SafetyUiState())

    /** Called when the screen resumes: the person may have changed a permission in Android settings. */
    fun refreshPermissions() = local.update { it.copy(perms = permissions()) }

    /** Called by the hold button after the full 3 s. A second confirmation while one is in flight is ignored. */
    fun raise() {
        if (local.value.raising) return
        local.update { it.copy(raising = true) }
        viewModelScope.launch {
            try { sos.raise() } finally { local.update { it.copy(raising = false) } }
        }
    }

    fun cancel() = sos.cancel()
    fun retrySms() = sos.retryFailedSms()

    /** Opens the confirmation only if somebody actually got the SOS text. */
    fun offerFalseAlarm() {
        viewModelScope.launch {
            val recipients = sos.falseAlarmRecipients()
            local.update { it.copy(falseAlarm = recipients.ifEmpty { null }) }
        }
    }

    fun confirmFalseAlarm() {
        sos.sendFalseAlarm()
        local.update { it.copy(falseAlarm = null) }
    }

    fun declineFalseAlarm() = local.update { it.copy(falseAlarm = null) }

    fun done() = sos.dismiss()

    fun setPartnerPhone(number: String?) {
        viewModelScope.launch { settings.update { it.copy(partnerPhone = number?.trim()?.ifEmpty { null }) } }
    }
}
