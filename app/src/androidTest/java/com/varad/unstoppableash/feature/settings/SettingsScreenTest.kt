package com.varad.unstoppableash.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun leaveAsksForConfirmationAndExplainsWhatIsDeleted() {
        var left = 0
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                SettingsScreen(SettingsUiState(myName = "Alice", myEmail = "alice@example.com", partnerName = "Bob", pairId = "p1"), SettingsActions(onLeave = { left++ }))
            }
        }
        rule.onNodeWithText("alice@example.com").assertExists()
        rule.onNodeWithText("Leave orbit").performScrollTo().performClick()
        rule.onNodeWithText("deletes everything", substring = true).assertExists()
        rule.runOnIdle { assertEquals(0, left) }
        rule.onNode(hasText("Leave orbit") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.runOnIdle { assertEquals(1, left) }
    }
}
