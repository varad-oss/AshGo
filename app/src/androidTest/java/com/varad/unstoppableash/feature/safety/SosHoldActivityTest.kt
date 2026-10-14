package com.varad.unstoppableash.feature.safety

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.varad.unstoppableash.AshGoApplication
import com.varad.unstoppableash.domain.model.SosState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Never completes a hold here: an automated test must not be able to send an SOS or an SMS. */
class SosHoldActivityTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun opensOnTheHoldAndATapSendsNothing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, SosHoldActivity::class.java).putExtra("useEmulators", true)
        ActivityScenario.launch<SosHoldActivity>(intent).use {
            rule.onNodeWithContentDescription("Hold for 3 seconds to send SOS").performClick()
            rule.waitForIdle()
            val controller = (context.applicationContext as AshGoApplication).container.sosController
            assertEquals(SosState.Idle, controller.state.value)
        }
    }
}
