package com.varad.unstoppableash.feature.chat

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.domain.chat.BubbleStatus
import com.varad.unstoppableash.domain.chat.TimelineItem
import com.varad.unstoppableash.domain.model.EventType
import com.varad.unstoppableash.domain.model.OrbitEvent
import com.varad.unstoppableash.domain.model.Relationship
import com.varad.unstoppableash.ui.theme.OrbitTheme
import com.varad.unstoppableash.ui.theme.ThemeMode
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ChatScreenTest {
    @get:Rule val rule = createComposeRule()

    private val sent = mutableListOf<String>()
    private val retried = mutableListOf<String>()
    private val base = ChatUiState(loading = false, partnerName = "Bob")
    private val at = LocalDate.of(2026, 10, 14).atTime(9, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun show(state: ChatUiState) = rule.setContent {
        OrbitTheme(ThemeMode.LIGHT) {
            ChatScreen(state, ChatActions(onSend = { sent += it }, onRetry = { retried += it }, photo = { flowOf(null) }))
        }
    }

    private fun bubble(id: String, mine: Boolean, text: String, status: BubbleStatus) =
        TimelineItem.Bubble(id, mine, text, null, at, status, showTime = true)

    @Test fun emptyChatInvitesAHello() {
        show(base)
        rule.onNodeWithText("Say hi 💌").assertExists()
    }

    @Test fun emptyChatFollowsTheRelationshipTone() {
        show(base.copy(relationship = Relationship.FAMILY))
        rule.onNodeWithText("Say hi").assertExists()
    }

    @Test fun sendIsDisabledUntilThereIsTextAndWhileSending() {
        show(base)
        rule.onNodeWithContentDescription("Send").assertIsNotEnabled()
        rule.onNodeWithText("Message").performTextInput("hello")
        rule.onNodeWithContentDescription("Send").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf("hello"), sent) }
    }

    @Test fun sendIsDisabledWhileAMessageIsInFlight() {
        show(base.copy(sending = true))
        rule.onNodeWithText("Message").performTextInput("again")
        rule.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    @Test fun aFailedBubbleOffersRetryWithItsId() {
        show(base.copy(items = listOf(TimelineItem.DaySeparator(LocalDate.of(2026, 10, 14)), bubble("x1", true, "are you home?", BubbleStatus.FAILED))))
        rule.onNodeWithContentDescription("Retry", substring = true).performClick()
        rule.runOnIdle { assertEquals(listOf("x1"), retried) }
    }

    @Test fun bubblesReadAsOneSentenceToTalkBack() {
        show(base.copy(items = listOf(bubble("a", false, "on my way", BubbleStatus.NONE), bubble("b", true, "see you", BubbleStatus.SEEN))))
        rule.onNodeWithContentDescription("Bob: on my way", substring = true).assertExists()
        rule.onNodeWithContentDescription("You: see you", substring = true).assertExists()
        rule.onNodeWithContentDescription("seen", substring = true).assertExists()
    }

    @Test fun nudgeChipShowsWhoAndWhat() {
        val e = OrbitEvent("e1", "bob", EventType.NUDGE, "call_me", null, at, emptyMap(), emptyMap())
        show(base.copy(items = listOf(TimelineItem.EventChip(e, mine = false))))
        rule.onNodeWithText("Bob nudged you · Call me when you're free?", substring = true).assertExists()
    }

    @Test fun anOversizedPhotoIsExplainedPolitely() {
        show(base.copy(notice = ChatNotice.PhotoTooLarge))
        rule.onNodeWithText("Photo too large even after compression")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test fun rateLimitedNudgeSaysSo() {
        show(base.copy(notice = ChatNotice.Failed(FailureReason.RATE_LIMITED)))
        rule.onNodeWithText("Give it a moment before nudging again").assertExists()
    }

    @Test fun aShortConversationRestsOnTheComposer() {
        show(base.copy(items = listOf(TimelineItem.DaySeparator(LocalDate.of(2026, 10, 14)), bubble("a", false, "first", BubbleStatus.NONE), bubble("b", true, "newest", BubbleStatus.SENT))))
        val list = rule.onNodeWithTag("chat-timeline").fetchSemanticsNode().boundsInRoot
        val newest = rule.onNodeWithText("newest").fetchSemanticsNode().boundsInRoot
        val gap = with(rule.density) { (list.bottom - newest.bottom).toDp() }
        assertTrue("newest message sits at the bottom, gap was $gap", gap < 64.dp)
    }
}
