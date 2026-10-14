package com.varad.unstoppableash.feature.safety

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.SmsReceipt
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SosState
import com.varad.unstoppableash.service.sos.SosLocation
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SafetyScreenTest {
    @get:Rule val rule = createComposeRule()

    private var raises = 0; private var cancels = 0; private var retries = 0; private var falseAlarms = 0
    private val dialled = mutableListOf<String>()
    private val mum = EmergencyContact("c1", "Mum", "111")
    private val ready = SafetyUiState(
        loading = false, paired = true, partnerName = "Bob", partnerPhone = "999", contacts = listOf(mum),
        permissions = SafetyPermissions(sms = true, location = LocationAccess.PRECISE),
    )

    private fun show(state: SafetyUiState) = rule.setContent {
        OrbitTheme(ThemeMode.LIGHT) {
            SafetyScreen(
                state,
                SafetyActions(
                    onRaise = { raises++ }, onCancel = { cancels++ }, onRetrySms = { retries++ }, onConfirmFalseAlarm = { falseAlarms++ },
                    onCall = { dialled += it },
                ),
            )
        }
    }

    @Test fun aTapNeverRaises() {
        rule.mainClock.autoAdvance = false
        show(ready)
        rule.onNodeWithContentDescription("Hold for 3 seconds to send SOS").performClick()
        rule.mainClock.advanceTimeBy(5_000)
        rule.runOnIdle { assertEquals(0, raises) }
    }

    @Test fun aFullHoldRaises() {
        rule.mainClock.autoAdvance = false
        show(ready)
        rule.onNodeWithContentDescription("Hold for 3 seconds to send SOS").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(3_100, ignoreFrameDuration = true)
        rule.runOnIdle { assertEquals(1, raises) }
    }

    @Test fun whatHappensSaysWhatWillReallyHappen() {
        show(ready)
        rule.onNodeWithText("Bob gets an alert in AshGo").assertExists()
        rule.onNodeWithText("SMS to 1 emergency contact").assertExists()
        rule.onNodeWithText("Your location is included").assertExists()
    }

    @Test fun gapsAreNamedPlainly() {
        show(ready.copy(paired = false, contacts = emptyList(), permissions = SafetyPermissions(sms = false, location = LocationAccess.APPROXIMATE)))
        rule.onNodeWithText("No orbit yet: no in-app alert").assertExists()
        rule.onNodeWithText("No emergency contacts yet").assertExists()
        rule.onNodeWithText("Approximate location only").assertExists()
    }

    @Test fun smsOffIsNamedEvenWithContacts() {
        show(ready.copy(permissions = SafetyPermissions(sms = false, location = LocationAccess.OFF)))
        rule.onNodeWithText("SMS off: allow AshGo to send SMS").assertExists()
        rule.onNodeWithText("Location off").assertExists()
    }

    @Test fun callButtonsOpenTheDialer() {
        show(ready)
        rule.onNodeWithText("Call 112").performScrollTo().performClick()
        rule.onNodeWithText("Call Bob").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("112", "999"), dialled) }
    }

    private val active = ready.copy(
        sos = SosState.Active("s1", 1L, BackendStatus.SAVED, 1L, null, null, mapOf("c1" to SmsReceipt(SmsStatus.FAILED, "no signal"))),
        location = SosLocation.Known(LocationFix(1.0, 2.0, 5f, 1L)),
    )

    @Test fun activeSosShowsEachFactSeparately() {
        show(active)
        rule.onNodeWithText("SOS sent").assertExists()
        rule.onNodeWithContentDescription("Saved to AshGo: done", substring = true).assertExists()
        rule.onNodeWithContentDescription("Delivered to Bob's phone: not confirmed yet", substring = true).assertExists()
        rule.onNodeWithContentDescription("Bob has seen it: not confirmed yet", substring = true).assertExists()
        rule.onNodeWithContentDescription("SMS to Mum (no signal): failed", substring = true).assertExists()
        rule.onNodeWithText("Retry").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun cancelNeedsAHoldToo() {
        rule.mainClock.autoAdvance = false
        show(active)
        rule.onNodeWithContentDescription("I'm safe: cancel SOS").performClick()
        rule.mainClock.advanceTimeBy(3_000)
        rule.runOnIdle { assertEquals(0, cancels) }
    }

    @Test fun falseAlarmConfirmationListsRecipientsAndCost() {
        show(ready.copy(sos = SosState.Cancelled("s1", 1L, emptyMap()), falseAlarmRecipients = listOf(mum)))
        rule.onNodeWithText("Mum", substring = true).assertExists()
        rule.onNodeWithText("SMS charges may apply", substring = true).assertExists()
        rule.onNodeWithText("Send").performClick()
        rule.runOnIdle { assertEquals(1, falseAlarms) }
    }
}
