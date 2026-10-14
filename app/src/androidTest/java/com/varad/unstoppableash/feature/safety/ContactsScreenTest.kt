package com.varad.unstoppableash.feature.safety

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ContactsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val removed = mutableListOf<String>()
    private val added = mutableListOf<Pair<String, String>>()

    private fun show(state: ContactsUiState) = rule.setContent {
        OrbitTheme(ThemeMode.LIGHT) { ContactsScreen(state, ContactsActions(onAdd = { n, p -> added += n to p }, onRemove = { removed += it })) }
    }

    @Test fun removingAsksFirst() {
        show(ContactsUiState(listOf(EmergencyContact("c1", "Mum", "111"))))
        rule.onNodeWithContentDescription("Remove Mum").performClick()
        rule.runOnIdle { assertEquals(emptyList<String>(), removed) }
        rule.onNodeWithText("Remove").performClick()
        rule.runOnIdle { assertEquals(listOf("c1"), removed) }
    }

    @Test fun typedContactIsAdded() {
        show(ContactsUiState())
        rule.onNodeWithText("Name").performScrollTo().performTextInput("Priya")
        rule.onNodeWithText("Phone number").performScrollTo().performTextInput("+91 91234 56789")
        rule.onNodeWithText("Add").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("Priya" to "+91 91234 56789"), added) }
    }

    @Test fun costAndPrivacyAreExplained() {
        show(ContactsUiState())
        rule.onNodeWithText("Normal SMS charges may apply", substring = true).assertExists()
        rule.onNodeWithText("kept only on this phone", substring = true).assertExists()
    }

    @Test fun aFullListCannotGrow() {
        show(ContactsUiState((1..5).map { EmergencyContact("c$it", "C$it", "9$it") }, ContactsNotice.FULL))
        rule.onNodeWithText("Choose from your contacts").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("You can have up to 5 emergency contacts").assertExists()
    }
}
