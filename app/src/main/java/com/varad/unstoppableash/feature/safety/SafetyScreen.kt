package com.varad.unstoppableash.feature.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.SmsReceipt
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SosState
import com.varad.unstoppableash.service.sos.FalseAlarm
import com.varad.unstoppableash.service.sos.SosLocation
import com.varad.unstoppableash.ui.components.HoldDefaults
import com.varad.unstoppableash.ui.components.HoldToConfirmButton
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitDialog
import com.varad.unstoppableash.ui.components.ReceiptRow
import com.varad.unstoppableash.ui.components.ReceiptStatus
import com.varad.unstoppableash.ui.components.shortAgeAgo
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class SafetyActions(
    val onRaise: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetrySms: () -> Unit = {},
    val onOfferFalseAlarm: () -> Unit = {},
    val onConfirmFalseAlarm: () -> Unit = {},
    val onDeclineFalseAlarm: () -> Unit = {},
    val onDone: () -> Unit = {},
    /** Opens the dialer with this number; AshGo never places a call itself. */
    val onCall: (String) -> Unit = {},
    val onOpenContacts: () -> Unit = {},
    val onOpenFakeCall: () -> Unit = {},
    val onPickPartnerNumber: () -> Unit = {},
    val onRequestSms: () -> Unit = {},
    val onRequestLocation: () -> Unit = {},
    /**
     * Over the lock screen: runs the action only after the person unlocks. Raising never needs this; calling
     * off an SOS or texting "false alarm" does, so someone else holding the locked phone can't (review 7 I5).
     */
    val requireUnlock: ((() -> Unit) -> Unit)? = null,
)

/** REDESIGN_SPEC §Safety and §SosActive. Neutral copy only (spec §7): no hearts, no relationship tone. */
@Composable
fun SafetyScreen(state: SafetyUiState, givenActions: SafetyActions, modifier: Modifier = Modifier) {
    val guard = givenActions.requireUnlock
    val actions = if (guard == null) givenActions else givenActions.copy(
        onCancel = { guard(givenActions.onCancel) },
        onOfferFalseAlarm = { guard(givenActions.onOfferFalseAlarm) },
        onConfirmFalseAlarm = { guard(givenActions.onConfirmFalseAlarm) },
    )
    // Locked: who your contacts are is nobody else's business.
    val shown = if (guard == null) state else state.copy(contacts = state.contacts.mapIndexed { i, c -> c.copy(name = "#${i + 1}") })
    SafetyContent(shown, actions, modifier)
}

@Composable
private fun SafetyContent(state: SafetyUiState, actions: SafetyActions, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().background(Orbit.colors.background).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        when (val sos = state.sos) {
            is SosState.Active -> ActivePanel(state, sos, actions)
            is SosState.Cancelled -> CancelledPanel(state, sos, actions)
            SosState.Idle -> IdlePanel(state, actions)
        }
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CallButtons(state, actions)
        }
    }
    state.falseAlarmRecipients?.let { recipients ->
        OrbitDialog(
            title = stringResource(R.string.sos_false_alarm_title),
            text = stringResource(R.string.sos_false_alarm_body, recipients.joinToString { it.name }),
            confirmLabel = stringResource(R.string.action_send),
            onConfirm = actions.onConfirmFalseAlarm,
            dismissLabel = stringResource(R.string.action_dont_send),
            onDismiss = actions.onDeclineFalseAlarm,
        )
    }
}

@Composable
private fun IdlePanel(state: SafetyUiState, actions: SafetyActions) {
    Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(stringResource(R.string.safety_title), style = MaterialTheme.typography.headlineSmall, color = Orbit.colors.text, modifier = Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HoldToConfirmButton(
                label = stringResource(R.string.sos_hold_label),
                holdMillis = SOS_HOLD_MS,
                onConfirmed = actions.onRaise,
                visibleText = stringResource(R.string.sos_hold_visible),
                enabled = !state.raising,
            )
        }
        OrbitCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.what_happens), style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text, modifier = Modifier.semantics { heading() })
            WhatHappens(state, actions)
        }
        OrbitCard(Modifier.fillMaxWidth(), onClick = actions.onOpenContacts) {
            Text(stringResource(R.string.emergency_contacts), style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text)
            Text(pluralStringResource(R.plurals.emergency_contacts_count, state.contacts.size, state.contacts.size), style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
        }
        OrbitCard(Modifier.fillMaxWidth(), onClick = actions.onOpenFakeCall) {
            Text(stringResource(R.string.fake_call_open), style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text)
            Text(stringResource(R.string.fake_call_open_body), style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
        }
    }
}

@Composable
private fun ColumnScope.WhatHappens(state: SafetyUiState, actions: SafetyActions) {
    Fact(
        ok = state.paired,
        text = if (state.paired) stringResource(R.string.wh_partner_alert, state.partnerName) else stringResource(R.string.wh_no_orbit),
    )
    when {
        state.contacts.isEmpty() -> Fact(false, stringResource(R.string.wh_no_contacts), stringResource(R.string.action_add), actions.onOpenContacts)
        !state.permissions.sms -> Fact(false, stringResource(R.string.wh_sms_off), stringResource(R.string.action_allow), actions.onRequestSms)
        else -> Fact(true, pluralStringResource(R.plurals.wh_sms_contacts, state.contacts.size, state.contacts.size))
    }
    when (state.permissions.location) {
        LocationAccess.PRECISE -> Fact(true, stringResource(R.string.wh_location_precise))
        LocationAccess.APPROXIMATE -> Fact(false, stringResource(R.string.wh_location_approx), stringResource(R.string.action_fix), actions.onRequestLocation)
        LocationAccess.OFF -> Fact(false, stringResource(R.string.wh_location_off), stringResource(R.string.action_allow), actions.onRequestLocation)
    }
}

@Composable
private fun Fact(ok: Boolean, text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val c = Orbit.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        // The icon repeats the sentence's meaning, so it stays silent for TalkBack.
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = if (ok) c.goodInk else c.warnInk, modifier = Modifier.size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) OrbitButton(actionLabel, onAction, style = OrbitButtonStyle.Quiet)
    }
}

@Composable
private fun ActivePanel(state: SafetyUiState, sos: SosState.Active, actions: SafetyActions) {
    val c = Orbit.colors
    Surface(color = c.sos, contentColor = c.onSos, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // "Active", not "sent": what was actually sent is in the rows below, fact by fact.
            Text(stringResource(R.string.sos_active), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Assertive })
            Text(stringResource(R.string.sos_sent_at, timeOf(sos.serverAt ?: sos.raisedAt)), style = MaterialTheme.typography.bodyLarge)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HoldToConfirmButton(
            label = stringResource(R.string.sos_cancel_label),
            holdMillis = CANCEL_HOLD_MS,
            onConfirmed = actions.onCancel,
            colors = HoldDefaults.primary(),
            visibleText = stringResource(R.string.sos_cancel_visible),
        )
        Text(stringResource(R.string.sos_cancel_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        BackendRow(state, sos)
        if (state.paired) {
            ReceiptRow(stringResource(R.string.rcpt_shown, state.partnerName), if (sos.deliveredToPartnerAt != null) ReceiptStatus.DONE else ReceiptStatus.UNKNOWN, sos.deliveredToPartnerAt?.let(::timeOf), null)
            ReceiptRow(stringResource(R.string.rcpt_acked, state.partnerName), if (sos.partnerAckAt != null) ReceiptStatus.DONE else ReceiptStatus.UNKNOWN, sos.partnerAckAt?.let(::timeOf), null)
        }
        SmsRows(state, sos.sms, actions.onRetrySms)
        LocationRow(state.location)
    }
}

@Composable
private fun BackendRow(state: SafetyUiState, sos: SosState.Active) = when {
    sos.backend == BackendStatus.SAVED -> ReceiptRow(stringResource(R.string.rcpt_saved), ReceiptStatus.DONE, sos.serverAt?.let(::timeOf), null)
    !state.paired -> ReceiptRow(stringResource(R.string.rcpt_no_orbit), ReceiptStatus.UNKNOWN, null, null)
    sos.backend == BackendStatus.FAILED -> ReceiptRow(stringResource(R.string.rcpt_waiting), ReceiptStatus.PENDING, null, null)
    else -> ReceiptRow(stringResource(R.string.rcpt_saving), ReceiptStatus.PENDING, null, null)
}

@Composable
private fun SmsRows(state: SafetyUiState, sms: Map<String, SmsReceipt>, onRetry: (() -> Unit)?) {
    sms.forEach { (id, r) ->
        val name = state.contactName(id)
        when (r.status) {
            SmsStatus.QUEUED -> ReceiptRow(stringResource(R.string.rcpt_sms, name), ReceiptStatus.QUEUED, null, null)
            SmsStatus.SENT -> ReceiptRow(stringResource(R.string.rcpt_sms_sent, name), ReceiptStatus.DONE, null, null)
            SmsStatus.DELIVERED -> ReceiptRow(stringResource(R.string.rcpt_sms_delivered, name), ReceiptStatus.DONE, null, null)
            SmsStatus.FAILED -> ReceiptRow(
                if (r.failure != null) stringResource(R.string.rcpt_sms_why, name, r.failure) else stringResource(R.string.rcpt_sms, name),
                ReceiptStatus.FAILED, null, onRetry,
            )
        }
    }
}

@Composable
private fun LocationRow(location: SosLocation) = when (location) {
    is SosLocation.Known -> ReceiptRow(
        location.fix.accuracyM?.let { stringResource(R.string.rcpt_location, it.toInt()) } ?: stringResource(R.string.rcpt_location_plain),
        ReceiptStatus.DONE, shortAgeAgo(System.currentTimeMillis() - location.fix.at), null,
    )
    SosLocation.Locating -> ReceiptRow(stringResource(R.string.rcpt_locating), ReceiptStatus.PENDING, null, null)
    SosLocation.Unavailable -> ReceiptRow(stringResource(R.string.rcpt_location_unavailable), ReceiptStatus.UNKNOWN, null, null)
}

@Composable
private fun CancelledPanel(state: SafetyUiState, sos: SosState.Cancelled, actions: SafetyActions) {
    Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(stringResource(R.string.sos_cancelled_at, timeOf(sos.cancelledAt)), style = MaterialTheme.typography.headlineSmall, color = Orbit.colors.text, modifier = Modifier.semantics { heading() })
        SmsRows(state, sos.sms, onRetry = null)
        val texted = sos.sms.values.any { it.status == SmsStatus.SENT || it.status == SmsStatus.DELIVERED }
        when (val fa = state.falseAlarm) {
            is FalseAlarm.Sent -> Text(pluralStringResource(R.plurals.sos_false_alarm_sent, fa.count, fa.count), style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.goodInk, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            FalseAlarm.SmsOff -> Text(stringResource(R.string.sos_false_alarm_sms_off), style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.warnInk, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            null -> if (texted) OrbitButton(stringResource(R.string.sos_offer_false_alarm), actions.onOfferFalseAlarm, Modifier.fillMaxWidth(), style = OrbitButtonStyle.Secondary)
        }
        OrbitButton(stringResource(R.string.action_done), actions.onDone, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CallButtons(state: SafetyUiState, actions: SafetyActions) {
    val phone = state.partnerPhone
    if (state.paired) {
        if (phone != null) {
            OrbitButton(stringResource(R.string.call_partner, state.partnerName), { actions.onCall(phone) }, Modifier.fillMaxWidth(), style = OrbitButtonStyle.Secondary)
        } else {
            OrbitButton(stringResource(R.string.add_partner_number, state.partnerName), actions.onPickPartnerNumber, Modifier.fillMaxWidth(), style = OrbitButtonStyle.Quiet)
        }
    }
    OrbitButton(stringResource(R.string.call_112), { actions.onCall(EMERGENCY_NUMBER) }, Modifier.fillMaxWidth(), style = OrbitButtonStyle.Secondary)
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
private fun timeOf(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(timeFormat)

const val SOS_HOLD_MS = 3_000L
private const val CANCEL_HOLD_MS = 1_500L
/** India's single emergency number; it also works from any phone without a SIM. */
const val EMERGENCY_NUMBER = "112"
