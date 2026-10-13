package com.varad.unstoppableash.feature.onboarding

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.varad.unstoppableash.domain.model.Invite
import com.varad.unstoppableash.domain.model.Profile
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val rule = createComposeRule()

    private var signIns = 0
    private var created: String? = null
    private fun show(state: OnboardingUiState) = rule.setContent {
        OrbitTheme(ThemeMode.LIGHT) {
            OnboardingScreen(state, OnboardingActions(onSignIn = { signIns++ }, onCreate = { name, _ -> created = name }))
        }
    }

    @Test fun signInOffersGoogle() {
        show(OnboardingUiState(step = OnboardingStep.SIGN_IN, loading = false))
        rule.onNodeWithText("Continue with Google").performClick()
        rule.runOnIdle { assertEquals(1, signIns) }
    }

    @Test fun createIsDisabledUntilANameIsEntered() {
        show(OnboardingUiState(step = OnboardingStep.CREATE_PROFILE, loading = false, suggestedName = ""))
        rule.onNodeWithText("Create").assertIsNotEnabled()
        rule.onNodeWithText("Your name").performTextInput("Alice")
        rule.onNodeWithText("Create").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("Alice", created) }
    }

    @Test fun invalidEmailShowsAnError() {
        show(OnboardingUiState(step = OnboardingStep.INVITE, loading = false, emailError = true))
        rule.onNodeWithText("That doesn't look like an email address").assertExists()
    }

    @Test fun invitationShowsTheInvitersRealEmail() {
        show(
            OnboardingUiState(
                step = OnboardingStep.INVITATIONS, loading = false, email = "bob@example.com",
                invites = listOf(Invite("p1", "alice", "Alice", "alice@example.com", Long.MAX_VALUE)),
            ),
        )
        rule.onNodeWithText("alice@example.com").assertExists()
        rule.onNodeWithText("Alice invited you").assertExists()
    }

    @Test fun confirmShowsPartnerAvatarAndEmail() {
        show(
            OnboardingUiState(
                step = OnboardingStep.CONFIRM_PARTNER, loading = false,
                partner = Profile("bob", "Bob", null, "bob@example.com", null, true),
            ),
        )
        rule.onNodeWithText("bob@example.com").assertExists()
        rule.onNodeWithContentDescription("Bob").assertExists()
        rule.onNodeWithText("Yes, that's them").assertExists()
        rule.onNodeWithText("That's not them").assertExists()
    }
}
