package com.varad.unstoppableash.ui.nav

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainScaffoldTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fourTabsWithUnreadBadgeOnChat() {
        var selected: Tab? = null
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { MainScaffold(Tab.HOME, unread = 3, onSelect = { selected = it }) { Text("content") } } }
        for (label in listOf("Home", "Map", "Chat", "Safety")) rule.onNodeWithText(label).assertExists()
        rule.onNode(hasText("Chat") and hasStateDescription("3 unread")).assertExists()
        rule.onNodeWithText("Safety").performClick()
        rule.runOnIdle { assertEquals(Tab.SAFETY, selected) }
    }

    @Test fun comingLaterIsExplicit() {
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { ComingLater() } }
        rule.onNodeWithText("Not built yet").assertExists()
    }
}
