package com.varad.unstoppableash.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    @Test fun eachRowOpensItsPage() {
        val opened = mutableListOf<SettingsPage>()
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { SettingsScreen(SettingsUiState(myName = "Alice", pairId = "p1"), SettingsActions(onOpen = { opened += it })) } }
        for (label in listOf("Our page", "Emergency contacts", "Reliability", "Privacy & data", "Updates", "Help", "About & licences")) {
            rule.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
        }
        rule.runOnIdle { assertEquals(SettingsPage.entries.toList(), opened) }
    }

    @Test fun sosAlertsHaveNoInAppSwitch() {
        var openedChannel = 0
        val toggles = mutableListOf<NotifyToggle>()
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                SettingsScreen(SettingsUiState(myName = "Alice"), SettingsActions(onOpenSosChannel = { openedChannel++ }, onNotify = { t, _ -> toggles += t }))
            }
        }
        rule.onAllNodes(isToggleable()).assertCountEquals(3)
        rule.onNode(hasText("Messages") and hasClickAction()).performScrollTo().performClick()
        rule.onNode(hasText("SOS and overdue alerts") and hasClickAction()).performScrollTo().assert(isToggleable().not()).performClick()
        rule.runOnIdle { assertEquals(1, openedChannel); assertEquals(listOf(NotifyToggle.CHAT), toggles) }
    }

    @Test fun deleteMyMessagesAsksFirstThenReportsTheCount() {
        var deletes = 0
        var state by mutableStateOf(SettingsUiState(pairId = "p1"))
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { PrivacyScreen(state, onBack = {}, onDeleteMessages = { deletes++ }) } }
        rule.onNodeWithText("Delete my messages now").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, deletes) }
        rule.onNode(hasText("Delete") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        rule.runOnIdle { assertEquals(1, deletes); state = state.copy(deletedCount = 3) }
        rule.onNodeWithText("Deleted 3 items.").assertExists()
    }

    @Test fun updatesExplainARefusedChecksum() {
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) { UpdatesScreen(UpdatesState.Failed(com.varad.unstoppableash.data.update.UpdateFailure.CHECKSUM), "2.0", {}, {}, {}) }
        }
        rule.onNodeWithText("didn't match its checksum", substring = true).assertExists()
        rule.onNodeWithText("Installed: 2.0").assertExists()
    }
}
