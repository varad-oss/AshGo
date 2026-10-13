package com.varad.unstoppableash.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.varad.unstoppableash.R
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HoldToConfirmButtonTest {
    @get:Rule val rule = createComposeRule()

    private val label = "Send SOS"
    private var confirmations = 0

    private fun show(enabled: Boolean = true) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                HoldToConfirmButton(
                    label = label,
                    holdMillis = 3_000,
                    onConfirmed = { confirmations++ },
                    enabled = enabled,
                    modifier = Modifier.testTag("hold"),
                )
            }
        }
    }

    private fun press() = rule.onNodeWithTag("hold").performTouchInput { down(center) }
    private fun release() = rule.onNodeWithTag("hold").performTouchInput { up() }
    private fun advance(ms: Long) = rule.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)

    @Test fun release_at_2999ms_doesNotConfirm() {
        show()
        press(); advance(2_999); release()
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle { assertEquals(0, confirmations) }
        val hint = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.hold_released_early)
        rule.onNodeWithText(hint).assertExists()
    }

    @Test fun hold_3000ms_confirmsOnce() {
        show()
        press(); advance(3_000)
        rule.runOnIdle { assertEquals(1, confirmations) }
        advance(2_000)
        rule.runOnIdle { assertEquals(1, confirmations) }
        release()
        rule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test fun rapidRepress_restartsFromZero() {
        show()
        press(); advance(2_000); release()
        press(); advance(2_000)
        rule.runOnIdle { assertEquals(0, confirmations) }
        advance(1_000)
        rule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test fun disabled_neverConfirms() {
        show(enabled = false)
        press(); advance(5_000); release()
        rule.runOnIdle { assertEquals(0, confirmations) }
    }

    @Test fun accessibilityAction_confirms() {
        show()
        val node = rule.onNodeWithTag("hold").fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        rule.runOnUiThread { action.action() }
        rule.runOnIdle { assertEquals(1, confirmations) }
    }
}
