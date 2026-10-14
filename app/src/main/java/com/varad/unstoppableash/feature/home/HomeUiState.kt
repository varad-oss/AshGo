package com.varad.unstoppableash.feature.home

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.OrbitEvent
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.Relationship
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.Trip

enum class Action { CHECK_IN, NUDGE }

/** What actually happened to the last quick action — never a success the backend didn't confirm. */
sealed interface ActionStatus {
    data object Idle : ActionStatus
    data class Sending(val action: Action) : ActionStatus
    data class Saved(val action: Action) : ActionStatus
    data class Failed(val action: Action, val reason: FailureReason) : ActionStatus
}

data class HomeUiState(
    val loading: Boolean = true,
    val unpaired: Boolean = false,
    val myName: String = "",
    val myAvatar: String? = null,
    val partnerName: String = "",
    val partnerAvatar: String? = null,
    val relationship: Relationship = Relationship.PARTNER,
    val greetingEnabled: Boolean = true,
    val partnerPresence: PresenceState = PresenceState.Unknown,
    val partnerBattery: Int? = null,
    val partnerCharging: Boolean? = null,
    val partnerLocation: LocationFix? = null,
    val mySharing: SharingMode = SharingMode.OFF,
    val unread: Int = 0,
    val latestPartnerCheckIn: OrbitEvent? = null,
    val partnerSos: SosRecord? = null,
    val partnerTrip: Trip? = null,
    val action: ActionStatus = ActionStatus.Idle,
    /** Not connected to the database right now. */
    val connectionProblem: FailureReason? = null,
    /** A listener was cancelled (e.g. denied); dismissible. */
    val error: FailureReason? = null,
    /** Server-corrected now, ticking, so ages on screen keep moving. */
    val nowMs: Long = 0L,
    /** My own SOS is running (from SosController on this phone). */
    val mySosActive: Boolean = false,
)
