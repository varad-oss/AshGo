package com.varad.unstoppableash.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    private var enabled by mutableStateOf(true)
    private var holdMillis by mutableLongStateOf(3_000L)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(startEnabled: Boolean = true) {
        enabled = startEnabled
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OrbitTheme(ThemeMode.LIGHT) {
                HoldToConfirmButton(
                    label = label,
                    holdMillis = holdMillis,
                    onConfirmed = { confirmations++ },
                    enabled = enabled,
                    visibleText = "SOS",
                    modifier = Modifier.testTag("hold"),
                )
            }
        }
    }

    private fun press() = rule.onNodeWithTag("hold").performTouchInput { down(center) }
    private fun release() = rule.onNodeWithTag("hold").performTouchInput { up() }
    private fun advance(ms: Long) = rule.mainClock.advanceTimeBy(ms, ignoreFrameDuration = true)
    private fun assertConfirmations(n: Int) = rule.runOnIdle { assertEquals(n, confirmations) }

    @Test fun release_at_2999ms_doesNotConfirm() {
        show()
        press(); advance(2_999); release()
        rule.mainClock.advanceTimeBy(100)
        assertConfirmations(0)
        rule.onNodeWithText(context.getString(R.string.hold_released_early)).assertExists()
    }

    @Test fun hold_3000ms_confirmsOnce() {
        show()
        press(); advance(3_000)
        assertConfirmations(1)
        advance(2_000)
        assertConfirmations(1)
        release()
        assertConfirmations(1)
    }

    @Test fun rapidRepress_restartsFromZero() {
        show()
        press(); advance(2_000); release()
        press(); advance(2_000)
        assertConfirmations(0)
        advance(1_000)
        assertConfirmations(1)
    }

    @Test fun disabled_neverConfirms() {
        show(startEnabled = false)
        press(); advance(5_000); release()
        assertConfirmations(0)
    }

    @Test fun disableMidHold_neverConfirms() {
        show()
        press(); advance(500)
        rule.runOnIdle { enabled = false }
        advance(4_000)
        release()
        advance(1_000)
        assertConfirmations(0)
    }

    @Test fun holdMillisChangeMidHold_releaseDoesNotConfirm() {
        show()
        press(); advance(500)
        rule.runOnIdle { holdMillis = 3_500L }
        advance(500)
        release()
        advance(5_000)
        assertConfirmations(0)
    }

    @Test fun secondPressAfterKeyChange_confirmsOnce() {
        show()
        press(); advance(500)
        rule.runOnIdle { holdMillis = 3_001L }
        rule.mainClock.advanceTimeBy(100) // let the key change restart the gesture handler
        release()
        press(); advance(3_500)
        assertConfirmations(1)
    }

    @Test fun accessibilityAction_confirms() {
        show()
        val node = rule.onNodeWithTag("hold").fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        rule.runOnUiThread { action.action() }
        assertConfirmations(1)
    }

    @Test fun earlyReleaseHint_isPoliteLiveRegion() {
        show()
        press(); advance(1_000); release()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText(context.getString(R.string.hold_released_early))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test fun progressAnnouncedEachSecond() {
        show()
        press(); advance(1_000)
        rule.mainClock.advanceTimeByFrame()
        val expected = context.resources.getQuantityString(R.plurals.hold_progress, 3, 1, 3)
        rule.onNodeWithTag("hold")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
        release()
    }

    @Test fun visibleTextIsNotASeparateFocusStop() {
        show()
        rule.onAllNodesWithText("SOS", useUnmergedTree = true).assertCountEquals(0)
    }
}
