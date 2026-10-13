package com.varad.unstoppableash.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.chat.ChatRepository
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.media.EncodedPhoto
import com.varad.unstoppableash.data.media.PhotoEncoder
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.domain.chat.OutboxEntry
import com.varad.unstoppableash.domain.chat.OutboxStatus
import com.varad.unstoppableash.domain.chat.TimelineItem
import com.varad.unstoppableash.domain.chat.buildTimeline
import com.varad.unstoppableash.domain.model.ChatMessage
import com.varad.unstoppableash.domain.model.OrbitEvent
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.PresenceState
import com.varad.unstoppableash.domain.model.Relationship
import com.varad.unstoppableash.domain.presence.PresenceCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** One-off feedback under the composer. */
sealed interface ChatNotice {
    data object PhotoTooLarge : ChatNotice
    data object NudgeSent : ChatNotice
    data class Failed(val reason: FailureReason) : ChatNotice
}

data class ChatUiState(
    val loading: Boolean = true,
    val partnerName: String = "",
    val partnerAvatar: String? = null,
    val partnerPresence: PresenceState = PresenceState.Unknown,
    val relationship: Relationship = Relationship.PARTNER,
    val greetingEnabled: Boolean = true,
    val items: List<TimelineItem> = emptyList(),
    val sending: Boolean = false,
    val encodingPhoto: Boolean = false,
    val hasMore: Boolean = false,
    val loadingOlder: Boolean = false,
    val notice: ChatNotice? = null,
    val error: FailureReason? = null,
)

/**
 * Chat (REDESIGN_SPEC §Chat). A message gets its id when it is composed, and every retry reuses it,
 * so a double tap or a flaky network can never post it twice. Nothing is "read" unless the chat is on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val pairs: PairRepository,
    private val chat: ChatRepository,
    private val events: EventRepository,
    private val presence: PresenceRepository,
    private val encoder: PhotoEncoder,
    private val rtdb: RtdbClient,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private data class Local(
        val pairId: String? = null,
        val outbox: List<OutboxEntry> = emptyList(),
        val sending: Boolean = false,
        val encodingPhoto: Boolean = false,
        val notice: ChatNotice? = null,
        val error: FailureReason? = null,
    )

    private val local = MutableStateFlow(Local())
    private val limit = MutableStateFlow(PAGE)
    private val visible = MutableStateFlow(false)
    private val photos = HashMap<String, EncodedPhoto>() // kept for retry until the send succeeds
    @Volatile private var current: PairState.Paired? = null

    private val paired: Flow<PairState.Paired?> = pairs.pairState()
        .map { it as? PairState.Paired }
        .distinctUntilChanged { a, b -> a?.meta?.pairId == b?.meta?.pairId && a?.myUid == b?.myUid && a?.partner == b?.partner }

    init {
        viewModelScope.launch { rtdb.readFailures.collect { r -> local.update { it.copy(error = r) } } }
        viewModelScope.launch {
            paired.collectLatest { p ->
                current = p
                // A new orbit or account starts clean: no outbox, notices or errors from the last one.
                if (local.value.pairId != p?.meta?.pairId) { local.value = Local(pairId = p?.meta?.pairId); limit.value = PAGE; photos.clear() }
                if (p != null) markReadWhileVisible(p)
            }
        }
    }

    val state: StateFlow<ChatUiState> = paired.flatMapLatest { p ->
        if (p == null) flowOf(ChatUiState(loading = true)) else stateOf(p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), ChatUiState())

    private fun stateOf(p: PairState.Paired): Flow<ChatUiState> {
        val pid = p.meta.pairId
        val messages = limit.flatMapLatest { n -> chat.messages(pid, n).map { n to it } }
        return combine(
            listOf(
                messages,
                events.events(pid).onStart { emit(emptyList()) },
                chat.lastReadAt(pid, p.partnerUid).onStart { emit(null) },
                presence.snapshot(pid, p.partnerUid).map<PresenceSnapshot, PresenceSnapshot?> { it }.onStart { emit(null) },
                ticker().map { clock.now() },
                local,
                limit,
            ),
        ) { v ->
            @Suppress("UNCHECKED_CAST") val (n, list) = v[0] as Pair<Int, List<ChatMessage>>
            @Suppress("UNCHECKED_CAST") val evs = v[1] as List<OrbitEvent>
            val partnerRead = v[2] as Long?
            val snapshot = v[3] as PresenceSnapshot?
            val l = v[5] as Local
            val hasMore = list.size >= n
            // While older messages remain unloaded, don't show events from before the oldest loaded message.
            val since = if (hasMore) list.firstOrNull()?.at ?: Long.MIN_VALUE else Long.MIN_VALUE
            ChatUiState(
                loading = false,
                partnerName = p.partner?.name.orEmpty(),
                partnerAvatar = p.partner?.avatar,
                partnerPresence = snapshot?.let { PresenceCalculator.compute(it, v[4] as Long) } ?: PresenceState.Unknown,
                relationship = p.meta.relationship,
                greetingEnabled = p.me?.greeting ?: true,
                items = buildTimeline(list, evs.filter { it.at >= since }, l.outbox, p.myUid, partnerRead, zone),
                sending = l.sending,
                encodingPhoto = l.encodingPhoto,
                hasMore = hasMore,
                loadingOlder = (v[6] as Int) > n, // asked for more; the bigger page hasn't arrived yet
                notice = l.notice,
                error = l.error,
            )
        }
    }

    /** The screen reports whether it is actually showing; read markers and "seen" depend on it. */
    fun setVisible(isVisible: Boolean) { visible.value = isVisible }

    fun send(text: String) {
        val p = current ?: return
        val body = text.trim()
        if (body.isEmpty() || local.value.sending) return
        val entry = OutboxEntry(chat.newMessageId(p.meta.pairId), body, null, clock.now(), OutboxStatus.SENDING)
        local.update { it.copy(sending = true, outbox = it.outbox + entry, notice = null) }
        deliver(p, entry, releasesComposer = true)
    }

    fun sendPhoto(uri: String) {
        val p = current ?: return
        if (local.value.encodingPhoto) return
        local.update { it.copy(encodingPhoto = true, notice = null) }
        viewModelScope.launch {
            val encoded = encoder.encode(uri)
            if (encoded == null) {
                local.update { it.copy(encodingPhoto = false, notice = ChatNotice.PhotoTooLarge) }
                return@launch
            }
            val id = chat.newMessageId(p.meta.pairId)
            photos[id] = encoded
            val entry = OutboxEntry(id, null, id, clock.now(), OutboxStatus.SENDING)
            local.update { it.copy(encodingPhoto = false, outbox = it.outbox + entry) }
            deliver(p, entry, releasesComposer = false)
        }
    }

    fun retry(id: String) {
        val p = current ?: return
        val entry = local.value.outbox.firstOrNull { it.id == id && it.status == OutboxStatus.FAILED } ?: return
        val again = entry.copy(status = OutboxStatus.SENDING)
        local.update { l -> l.copy(outbox = l.outbox.map { if (it.id == id) again else it }) }
        deliver(p, again, releasesComposer = false)
    }

    fun nudge(kind: String) {
        val p = current ?: return
        viewModelScope.launch {
            val notice = when (val r = events.nudge(p.meta.pairId, p.myUid, kind)) {
                is Outcome.Success -> ChatNotice.NudgeSent
                is Outcome.Failure -> ChatNotice.Failed(r.reason)
            }
            local.update { it.copy(notice = notice) }
        }
    }

    fun loadOlder() {
        if (state.value.hasMore) limit.update { it + PAGE }
    }

    fun clearNotice() = local.update { it.copy(notice = null) }
    fun dismissError() = local.update { it.copy(error = null) }

    /** Photo data for a bubble; the screen decodes it off the main thread. */
    fun photo(mediaId: String): Flow<String?> = current?.let { chat.media(it.meta.pairId, mediaId) } ?: emptyFlow()

    private fun deliver(p: PairState.Paired, entry: OutboxEntry, releasesComposer: Boolean) {
        viewModelScope.launch {
            val pid = p.meta.pairId
            val r = if (entry.mediaId != null) {
                val ph = photos[entry.id] ?: return@launch
                chat.sendPhoto(pid, p.myUid, entry.id, ph.jpeg, ph.width, ph.height)
            } else {
                chat.send(pid, p.myUid, entry.id, entry.text.orEmpty())
            }
            if (local.value.pairId != pid) return@launch
            local.update { l ->
                val outbox = when (r) {
                    // The listener already has it (local writes are reflected immediately), so the outbox copy goes.
                    is Outcome.Success -> l.outbox.filterNot { it.id == entry.id }
                    is Outcome.Failure -> l.outbox.map { if (it.id == entry.id) it.copy(status = OutboxStatus.FAILED) else it }
                }
                l.copy(outbox = outbox, sending = if (releasesComposer) false else l.sending)
            }
            if (r is Outcome.Success) photos.remove(entry.id)
        }
    }

    private suspend fun markReadWhileVisible(p: PairState.Paired) {
        val pid = p.meta.pairId
        combine(visible, chat.messages(pid, PAGE), chat.lastReadAt(pid, p.myUid), events.events(pid)) { v, msgs, myRead, evs ->
            if (!v) return@combine
            val newest = msgs.lastOrNull { it.from != p.myUid }?.at
            if (newest != null && newest > (myRead ?: 0L)) chat.markRead(pid, p.myUid)
            evs.filter { it.from != p.myUid && p.myUid !in it.seenBy }.forEach { events.markSeen(pid, it, p.myUid) }
        }.collect {}
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) { emit(Unit); delay(30_000) }
    }

    companion object {
        const val PAGE = 50
    }
}
