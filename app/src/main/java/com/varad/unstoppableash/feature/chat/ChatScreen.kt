package com.varad.unstoppableash.feature.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.varad.unstoppableash.R
import com.varad.unstoppableash.domain.chat.BubbleStatus
import com.varad.unstoppableash.domain.chat.TimelineItem
import com.varad.unstoppableash.domain.copy.CopyVariant
import com.varad.unstoppableash.domain.copy.copyVariantFor
import com.varad.unstoppableash.domain.model.EventType
import com.varad.unstoppableash.feature.home.NudgeSheet
import com.varad.unstoppableash.feature.home.kindLabel
import com.varad.unstoppableash.ui.components.AvatarImage
import com.varad.unstoppableash.ui.components.EmptyState
import com.varad.unstoppableash.ui.components.ErrorBanner
import com.varad.unstoppableash.ui.components.LoadingOrbit
import com.varad.unstoppableash.ui.components.LoadingOrbitSize
import com.varad.unstoppableash.ui.components.OrbitTextField
import com.varad.unstoppableash.ui.components.StatusPill
import com.varad.unstoppableash.ui.components.failureMessage
import com.varad.unstoppableash.ui.theme.Orbit
import com.varad.unstoppableash.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class ChatActions(
    val onSend: (String) -> Unit = {},
    val onRetry: (String) -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onNudge: (String) -> Unit = {},
    val onLoadOlder: () -> Unit = {},
    val onClearNotice: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val photo: (mediaId: String) -> Flow<String?> = { emptyFlow() },
)

/** REDESIGN_SPEC §Chat. Stateless apart from the draft text and which sheet/photo is open. */
@Composable
fun ChatScreen(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }
    var nudging by rememberSaveable { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf<ImageBitmap?>(null) }

    Column(modifier.fillMaxSize().background(Orbit.colors.background).imePadding()) {
        Header(state)
        state.error?.let {
            ErrorBanner(failureMessage(it), Modifier.padding(horizontal = Spacing.lg), onRetry = actions.onDismissError, actionLabel = stringResource(R.string.action_dismiss))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingOrbit(Modifier.align(Alignment.Center), size = LoadingOrbitSize.LARGE)
                state.items.isEmpty() -> EmptyState(null, emptyTitle(state), stringResource(R.string.chat_empty_body, state.partnerName), Modifier.align(Alignment.Center))
                else -> Timeline(state, actions, onOpenPhoto = { fullscreen = it })
            }
        }
        Notice(state.notice, actions.onClearNotice)
        Composer(
            draft = draft,
            onDraft = { draft = it },
            canSend = draft.isNotBlank() && !state.sending,
            encodingPhoto = state.encodingPhoto,
            onSend = { actions.onSend(draft.trim()); draft = "" },
            onPhoto = actions.onPickPhoto,
            onNudge = { nudging = true },
        )
    }
    if (nudging) NudgeSheet(state.partnerName, onSend = { actions.onNudge(it); nudging = false }, onDismiss = { nudging = false })
    fullscreen?.let { bitmap ->
        Dialog(onDismissRequest = { fullscreen = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val close = stringResource(R.string.chat_close_photo)
            Box(
                Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                    .clickable(onClickLabel = close) { fullscreen = null },
                contentAlignment = Alignment.Center,
            ) {
                Image(bitmap, contentDescription = stringResource(R.string.chat_photo), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun Header(state: ChatUiState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AvatarImage(state.partnerName, state.partnerAvatar, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(state.partnerName, style = MaterialTheme.typography.titleMedium, color = Orbit.colors.text, modifier = Modifier.semantics { heading() })
            StatusPill(state.partnerName, state.partnerPresence)
        }
    }
}

@Composable
private fun emptyTitle(state: ChatUiState): String = stringResource(
    when (copyVariantFor(state.relationship, state.greetingEnabled)) {
        CopyVariant.WARM -> R.string.chat_empty_warm
        CopyVariant.NEUTRAL -> R.string.chat_empty_neutral
        CopyVariant.CASUAL -> R.string.chat_empty_casual
    },
)

@Composable
private fun Timeline(state: ChatUiState, actions: ChatActions, onOpenPhoto: (ImageBitmap) -> Unit) {
    val listState = rememberLazyListState()
    // Newest first in a reversed list: item 0 sits at the bottom, so new messages and the keyboard keep it anchored.
    val newestFirst = remember(state.items) { state.items.asReversed() }
    val loadOlder by rememberUpdatedState(actions.onLoadOlder)
    val canLoadMore by rememberUpdatedState(state.hasMore && !state.loadingOlder)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collect { (last, total) -> if (last != null && last >= total - 3 && canLoadMore) loadOlder() }
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize().testTag("chat-timeline"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        // spacedBy() alone is top-aligned even in a reversed list; Bottom keeps a short chat resting on the composer.
        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.Bottom),
    ) {
        items(newestFirst, key = { it.key }) { item ->
            when (item) {
                is TimelineItem.DaySeparator -> DaySeparator(item.date)
                is TimelineItem.Bubble -> Bubble(item, state.partnerName, actions, onOpenPhoto)
                is TimelineItem.EventChip -> EventChip(item, state.partnerName)
            }
        }
        if (state.loadingOlder) {
            item(key = "loading-older") {
                val label = stringResource(R.string.chat_loading_older)
                Box(Modifier.fillMaxWidth().padding(Spacing.md).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                    LoadingOrbit(size = LoadingOrbitSize.SMALL)
                }
            }
        }
    }
}

@Composable
private fun DaySeparator(date: LocalDate) {
    val today = LocalDate.now()
    val text = when (date) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = Orbit.colors.textSecondary, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm).semantics { heading() },
    )
}

/** Same cap the repository and rules enforce; no counter, it only matters for pasted essays. */
private const val MAX_MESSAGE = 2000

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private fun timeOf(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(timeFormat)

@Composable
private fun Bubble(b: TimelineItem.Bubble, partnerName: String, actions: ChatActions, onOpenPhoto: (ImageBitmap) -> Unit) {
    val c = Orbit.colors
    val tail = 6.dp
    val round = 20.dp
    val shape = if (b.mine) RoundedCornerShape(round, round, tail, round) else RoundedCornerShape(round, round, round, tail)
    val who = if (b.mine) stringResource(R.string.chat_you) else partnerName
    val body = b.text ?: stringResource(R.string.chat_photo)
    val time = timeOf(b.at)
    val status = when (b.status) {
        BubbleStatus.NONE -> null
        BubbleStatus.SENDING -> stringResource(R.string.chat_status_sending)
        BubbleStatus.SENT -> stringResource(R.string.chat_status_sent)
        BubbleStatus.SEEN -> stringResource(R.string.chat_status_seen)
        BubbleStatus.FAILED -> stringResource(R.string.chat_status_failed)
    }
    val description = if (status == null) stringResource(R.string.chat_bubble_cd, who, body, time) else stringResource(R.string.chat_bubble_cd_status, who, body, time, status)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (b.mine) Alignment.End else Alignment.Start) {
        Surface(
            color = if (b.mine) c.blush else c.lavender, contentColor = c.text, shape = shape,
            modifier = Modifier.widthIn(max = 300.dp).semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            if (b.mediaId != null) {
                Photo(b.mediaId, actions.photo, onOpenPhoto)
            } else {
                Text(b.text.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
            }
        }
        if (b.status == BubbleStatus.FAILED) {
            val retry = stringResource(R.string.chat_retry_cd)
            Text(
                stringResource(R.string.chat_retry), style = MaterialTheme.typography.labelMedium, color = c.warnInk,
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { actions.onRetry(b.id) }
                    .clearAndSetSemantics { contentDescription = retry; role = Role.Button; onClick { actions.onRetry(b.id); true } }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.md),
            )
        } else if (b.showTime) {
            val suffix = when (b.status) {
                BubbleStatus.SENDING -> " · 🕓"
                BubbleStatus.SENT -> " · ✓"
                BubbleStatus.SEEN -> " · " + stringResource(R.string.chat_seen)
                else -> ""
            }
            // Already part of the bubble's description; hidden here so TalkBack doesn't read it twice.
            Text(time + suffix, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = Spacing.xs).clearAndSetSemantics {})
        }
    }
}

private sealed interface PhotoLoad {
    data object Loading : PhotoLoad
    data object Unavailable : PhotoLoad
    data class Ready(val bitmap: ImageBitmap) : PhotoLoad
}

@Composable
private fun Photo(mediaId: String, source: (String) -> Flow<String?>, onOpen: (ImageBitmap) -> Unit) {
    // Loaded-and-absent (expired after 3 days, D-14) or undecodable is "unavailable", never an endless spinner.
    val load by produceState<PhotoLoad>(PhotoLoad.Loading, mediaId) {
        source(mediaId).collect { data ->
            value = data?.let { d -> withContext(Dispatchers.Default) { decode(d) } }?.let { PhotoLoad.Ready(it) } ?: PhotoLoad.Unavailable
        }
    }
    val open = stringResource(R.string.chat_open_photo)
    when (val l = load) {
        PhotoLoad.Loading -> Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) { LoadingOrbit(size = LoadingOrbitSize.SMALL) }
        PhotoLoad.Unavailable -> Text(
            stringResource(R.string.chat_photo_unavailable), style = MaterialTheme.typography.bodyMedium, color = Orbit.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
        is PhotoLoad.Ready -> Image(
            l.bitmap, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.widthIn(max = 240.dp).clickable(onClickLabel = open) { onOpen(l.bitmap) },
        )
    }
}

private fun decode(base64: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
private fun EventChip(chip: TimelineItem.EventChip, partnerName: String) {
    val e = chip.event
    val what = kindLabel(e.kind, e.text)
    val text = when {
        e.type == EventType.NUDGE && chip.mine -> stringResource(R.string.chat_nudge_from_me, partnerName, what)
        e.type == EventType.NUDGE -> stringResource(R.string.chat_nudge_from_partner, partnerName, what)
        chip.mine -> stringResource(R.string.chat_checkin_from_me, what)
        else -> stringResource(R.string.chat_checkin_from_partner, partnerName, what)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), contentAlignment = Alignment.Center) {
        Text(
            "$text · ${timeOf(e.at)}", style = MaterialTheme.typography.labelMedium, color = Orbit.colors.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.clip(MaterialTheme.shapes.small).background(Orbit.colors.surfaceWarm).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun Notice(notice: ChatNotice?, onClear: () -> Unit) {
    val text = when (notice) {
        null -> null
        ChatNotice.PhotoTooLarge -> stringResource(R.string.chat_photo_too_large)
        ChatNotice.NudgeSent -> stringResource(R.string.status_saved_nudge)
        is ChatNotice.Failed -> failureMessage(notice.reason)
    }
    if (notice == ChatNotice.NudgeSent) LaunchedEffect(notice) { delay(4_000); onClear() }
    if (text != null) {
        Text(
            text, style = MaterialTheme.typography.bodyMedium,
            color = if (notice == ChatNotice.NudgeSent) Orbit.colors.goodInk else Orbit.colors.warnInk,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    canSend: Boolean,
    encodingPhoto: Boolean,
    onSend: () -> Unit,
    onPhoto: () -> Unit,
    onNudge: () -> Unit,
) {
    val c = Orbit.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (encodingPhoto) {
            val preparing = stringResource(R.string.chat_preparing_photo)
            Box(Modifier.size(48.dp).semantics { contentDescription = preparing; liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
                LoadingOrbit(size = LoadingOrbitSize.SMALL)
            }
        } else {
            IconButton(onClick = onPhoto) { Icon(Icons.Rounded.PhotoCamera, contentDescription = stringResource(R.string.chat_attach_photo), tint = c.primary) }
        }
        IconButton(onClick = onNudge) { Icon(Icons.Rounded.Bolt, contentDescription = stringResource(R.string.action_nudge), tint = c.primary) }
        OrbitTextField(draft, { if (it.length <= MAX_MESSAGE) onDraft(it) }, stringResource(R.string.chat_hint), Modifier.weight(1f), singleLine = false)
        IconButton(
            onClick = onSend, enabled = canSend,
            colors = IconButtonDefaults.iconButtonColors(contentColor = c.primary),
        ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.action_send)) }
    }
}
