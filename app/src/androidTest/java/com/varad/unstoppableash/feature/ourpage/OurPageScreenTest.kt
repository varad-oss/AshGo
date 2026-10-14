package com.varad.unstoppableash.feature.ourpage

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test

class OurPageScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun emptyPageInvitesPhotosAndSaysTheyStayHere() {
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { OurPageScreen(OurPageState(), OurPageActions()) } }
        rule.onNodeWithText("Your photos, on a string").assertExists()
        rule.onNodeWithText("Add photos").assertExists()
        rule.onNodeWithText("never uploaded", substring = true).assertExists()
    }

    @Test fun aFullPageOffersNoAddButton() {
        rule.setContent { OrbitTheme(ThemeMode.LIGHT) { OurPageScreen(OurPageState(photos = List(OurPhotosStore.MAX) { "/nope/$it.jpg" }), OurPageActions()) } }
        rule.onNodeWithText("Add photos").assertDoesNotExist()
        rule.onNodeWithText("most this page holds", substring = true).assertExists()
    }
}
