package com.varad.unstoppableash.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.model.Invite
import com.varad.unstoppableash.domain.model.Relationship
import com.varad.unstoppableash.ui.components.AvatarImage
import com.varad.unstoppableash.ui.components.EmptyState
import com.varad.unstoppableash.ui.components.ErrorBanner
import com.varad.unstoppableash.ui.components.LoadingOrbit
import com.varad.unstoppableash.ui.components.LoadingOrbitSize
import com.varad.unstoppableash.ui.components.OrbitButton
import com.varad.unstoppableash.ui.components.OrbitButtonStyle
import com.varad.unstoppableash.ui.components.OrbitCard
import com.varad.unstoppableash.ui.components.OrbitTextField
import com.varad.unstoppableash.ui.components.failureMessage
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing

data class OnboardingActions(
    val onSignIn: () -> Unit = {},
    val onDevSignIn: (() -> Unit)? = null,
    val onChooseCreate: () -> Unit = {},
    val onChooseJoin: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onCreate: (String, Relationship) -> Unit = { _, _ -> },
    val onInvite: (String) -> Unit = {},
    val onInviteAgain: () -> Unit = {},
    val onJoin: (Invite, String) -> Unit = { _, _ -> },
    val onDecline: (Invite) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onNotThem: () -> Unit = {},
    val onDismissFailure: () -> Unit = {},
)

/** USER_JOURNEYS J1–J3. Stateless: renders [state], reports intent through [actions]. */
@Composable
fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        state.failure?.let { ErrorBanner(failureMessage(it), onRetry = actions.onDismissFailure, actionLabel = stringResource(R.string.action_dismiss)) }
        when (state.step) {
            OnboardingStep.SIGN_IN -> SignIn(state, actions)
            OnboardingStep.CHOOSE -> Choose(state, actions)
            OnboardingStep.CREATE_PROFILE -> CreateProfile(state, actions)
            OnboardingStep.INVITE -> InvitePartner(state, actions)
            OnboardingStep.WAITING -> Waiting(state, actions)
            OnboardingStep.INVITATIONS -> Invitations(state, actions)
            OnboardingStep.CONFIRM_PARTNER -> ConfirmPartner(state, actions)
            OnboardingStep.AWAITING_CONFIRMATION -> Centered {
                LoadingOrbit(size = LoadingOrbitSize.LARGE)
                Text(stringResource(R.string.awaiting_confirmation, state.partner?.name.orEmpty()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            OnboardingStep.DONE -> Centered { LoadingOrbit(size = LoadingOrbitSize.LARGE) }
        }
    }
}

@Composable
private fun Centered(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().padding(top = Spacing.xxxl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    content = content,
)

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, color = Orbit.colors.text)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.textSecondary)

@Composable
private fun SignIn(state: OnboardingUiState, actions: OnboardingActions) = Centered {
    Image(painterResource(R.drawable.ic_orbit_glyph), contentDescription = null, modifier = Modifier.size(72.dp), colorFilter = ColorFilter.tint(Orbit.colors.primary))
    Text(stringResource(R.string.app_brand), style = MaterialTheme.typography.displaySmall, color = Orbit.colors.text)
    Body(stringResource(R.string.welcome_tagline))
    Body(stringResource(R.string.welcome_point_share))
    Body(stringResource(R.string.welcome_point_help))
    OrbitButton(stringResource(R.string.sign_in_google), actions.onSignIn, Modifier.fillMaxWidth(), loading = state.busy)
    actions.onDevSignIn?.let { OrbitButton(stringResource(R.string.sign_in_dev), it, style = OrbitButtonStyle.Quiet) }
}

@Composable
private fun Choose(state: OnboardingUiState, actions: OnboardingActions) {
    Title(stringResource(R.string.choose_title))
    state.email?.let { Body(stringResource(R.string.signed_in_as, it)) }
    OrbitButton(stringResource(R.string.choose_create), actions.onChooseCreate, Modifier.fillMaxWidth())
    OrbitButton(stringResource(R.string.choose_join), actions.onChooseJoin, Modifier.fillMaxWidth(), style = OrbitButtonStyle.Secondary)
}

@Composable
private fun CreateProfile(state: OnboardingUiState, actions: OnboardingActions) {
    var name by remember { mutableStateOf(state.suggestedName) }
    var relationship by remember { mutableStateOf(Relationship.PARTNER) }
    Title(stringResource(R.string.profile_title))
    OrbitTextField(
        name, { name = it }, stringResource(R.string.profile_name_label), Modifier.fillMaxWidth(), maxLength = 40,
        error = if (state.nameError) stringResource(R.string.profile_name_error) else null,
    )
    Text(stringResource(R.string.relationship_label), style = MaterialTheme.typography.labelLarge, color = Orbit.colors.text)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Relationship.entries.take(2).forEach { r -> RelationshipChip(r, r == relationship) { relationship = r } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Relationship.entries.drop(2).forEach { r -> RelationshipChip(r, r == relationship) { relationship = r } }
    }
    OrbitButton(stringResource(R.string.action_create), { actions.onCreate(name, relationship) }, Modifier.fillMaxWidth(), enabled = name.isNotBlank(), loading = state.busy)
    OrbitButton(stringResource(R.string.action_back), actions.onBack, style = OrbitButtonStyle.Quiet)
}

@Composable
private fun RelationshipChip(r: Relationship, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(
        when (r) {
            Relationship.PARTNER -> R.string.relationship_partner
            Relationship.FAMILY -> R.string.relationship_family
            Relationship.FRIENDS -> R.string.relationship_friends
            Relationship.TRUSTED -> R.string.relationship_trusted
        },
    )
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun InvitePartner(state: OnboardingUiState, actions: OnboardingActions) {
    var email by remember { mutableStateOf("") }
    Title(stringResource(R.string.invite_title))
    OrbitTextField(
        email, { email = it }, stringResource(R.string.invite_email_label), Modifier.fillMaxWidth(),
        error = if (state.emailError) stringResource(R.string.invite_email_error) else null,
    )
    Body(stringResource(R.string.invite_validity))
    OrbitButton(stringResource(R.string.action_send_invite), { actions.onInvite(email) }, Modifier.fillMaxWidth(), enabled = email.isNotBlank(), loading = state.busy)
}

@Composable
private fun Waiting(state: OnboardingUiState, actions: OnboardingActions) = Centered {
    LoadingOrbit(size = LoadingOrbitSize.LARGE)
    Text(stringResource(R.string.waiting_title, state.sentInviteTo.orEmpty()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = Orbit.colors.text)
    Body(stringResource(R.string.waiting_body))
    OrbitButton(stringResource(R.string.action_invite_again), actions.onInviteAgain, style = OrbitButtonStyle.Quiet)
}

@Composable
private fun Invitations(state: OnboardingUiState, actions: OnboardingActions) {
    var name by remember { mutableStateOf(state.suggestedName) }
    Title(stringResource(R.string.invitations_title))
    if (state.invites.isEmpty()) {
        EmptyState(null, stringResource(R.string.invitations_empty_title), stringResource(R.string.invitations_empty_body, state.email.orEmpty()))
    } else {
        OrbitTextField(
            name, { name = it }, stringResource(R.string.profile_name_label), Modifier.fillMaxWidth(), maxLength = 40,
            error = if (state.nameError) stringResource(R.string.profile_name_error) else null,
        )
        state.invites.forEach { invite ->
            OrbitCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.invitation_from, invite.fromName), style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text)
                Text(invite.fromEmail, style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                    OrbitButton(stringResource(R.string.action_join), { actions.onJoin(invite, name) }, enabled = name.isNotBlank(), loading = state.busy)
                    OrbitButton(stringResource(R.string.action_not_now), { actions.onDecline(invite) }, style = OrbitButtonStyle.Quiet)
                }
            }
        }
    }
    OrbitButton(stringResource(R.string.action_back), actions.onBack, style = OrbitButtonStyle.Quiet)
}

@Composable
private fun ConfirmPartner(state: OnboardingUiState, actions: OnboardingActions) = Centered {
    val partner = state.partner
    Title(stringResource(R.string.confirm_title))
    if (partner != null) {
        AvatarImage(partner.name, partner.avatar, size = 96.dp)
        Text(partner.name, style = MaterialTheme.typography.titleLarge, color = Orbit.colors.text)
        partner.email?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Orbit.colors.textSecondary) }
    }
    Body(stringResource(R.string.confirm_body))
    OrbitButton(stringResource(R.string.action_yes_them), actions.onConfirm, Modifier.fillMaxWidth(), loading = state.busy)
    OrbitButton(stringResource(R.string.action_not_them), actions.onNotThem, style = OrbitButtonStyle.Quiet)
}
