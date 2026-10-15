package com.varad.unstoppableash.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OrbitButtonTest {
    @get:Rule val rule = createComposeRule()

    @Test fun orbitButton_loadingIgnoresClicks() {
        var clicks = 0
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                OrbitButton(text = "Send", onClick = { clicks++ }, loading = true, modifier = Modifier.testTag("btn"))
            }
        }
        rule.onNodeWithTag("btn").performClick()
        rule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun orbitButton_clicksWhenIdle() {
        var clicks = 0
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                OrbitButton(text = "Send", onClick = { clicks++ }, modifier = Modifier.testTag("btn"))
            }
        }
        rule.onNodeWithTag("btn").performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun orbitButton_disabledIgnoresClicks() {
        var clicks = 0
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                OrbitButton(text = "Send", onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("btn"))
            }
        }
        rule.onNodeWithTag("btn").performClick()
        rule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun orbitButton_minTouchTarget() {
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                OrbitButton(text = "Go", onClick = {}, style = OrbitButtonStyle.Quiet, modifier = Modifier.testTag("btn"))
            }
        }
        rule.onNodeWithTag("btn").assertHeightIsAtLeast(48.dp)
    }
}
