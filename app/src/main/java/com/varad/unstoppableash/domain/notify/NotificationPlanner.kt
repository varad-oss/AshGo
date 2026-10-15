package com.varad.unstoppableash.domain.notify

import com.varad.unstoppableash.domain.model.ChatMessage
import com.varad.unstoppableash.domain.model.EventType
import com.varad.unstoppableash.domain.model.OrbitEvent
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.SosRecordState
import com.varad.unstoppableash.domain.trips.OverduePhase

enum class Channel(val id: String) { SOS("sos"), OVERDUE("overdue"), CHAT("chat"), CHECKINS("checkins"), NUDGES("nudge_alerts"), SERVICE("service") }

enum class NotificationKind(val channel: Channel) {
    PARTNER_SOS(Channel.SOS), PARTNER_OVERDUE(Channel.OVERDUE), MY_OVERDUE(Channel.OVERDUE),
    CHAT(Channel.CHAT), CHECKIN(Channel.CHECKINS), NUDGE(Channel.NUDGES),
}

data class Toggles(val chat: Boolean = true, val nudges: Boolean = true, val checkins: Boolean = true)

/** A trip needing attention; [key] changes when the deadline does, so an extended trip can alert again. */
data class TripAlert(val key: String, val phase: OverduePhase)

data class PlannerInput(
    val myUid: String,
    val chat: List<ChatMessage>,
    val events: List<OrbitEvent>,
    val sos: List<SosRecord>,
    val partnerTrip: TripAlert?,
    val myTrip: TripAlert?,
    val toggles: Toggles,
    val seen: Map<String, String>,
    val now: Long,
    /** Tags Android is showing right now; null when unknown (then a posted item is assumed still shown). */
    val showing: Set<String>? = null,
    /** False until SOS and trips have really loaded: an empty first emission proves nothing is gone. */
    val loaded: Boolean = true,
)

/** [detail] is an event kind (e.g. "home_safe"); [text] is free text (a message or a custom note). */
data class PlannedNotification(
    val kind: NotificationKind, val tag: String, val itemId: String, val count: Int = 1, val text: String? = null, val detail: String? = null,
    /** The [Plan.seen] entry this post advanced; rolled back when Android didn't show it. */
    val seenKey: String = tag,
)

data class Plan(val post: List<PlannedNotification>, val cancel: List<String>, val seen: Map<String, String>)

/**
 * Decides which notifications to post, purely (B-1, J9, J10). Each item notifies at most once: [Plan.seen] is
 * persisted between runs. A fresh install doesn't replay history (only the last [FRESH_MS]), but an active SOS
 * always alerts, however old, and safety alerts ignore the per-kind toggles.
 */
object NotificationPlanner {
    const val FRESH_MS = 10 * 60_000L

    fun plan(input: PlannerInput): Plan {
        val seen = input.seen.toMutableMap()
        val post = mutableListOf<PlannedNotification>()
        val cancel = mutableListOf<String>()
        val partner = { from: String -> from != input.myUid }

        fun newSince(key: String, at: List<Long>): List<Long> {
            val since = seen[key]?.toLongOrNull() ?: (input.now - FRESH_MS)
            return at.filter { it > since }
        }

        val chat = input.chat.filter { partner(it.from) }
        val freshChat = newSince(KEY_CHAT, chat.map { it.at })
        if (freshChat.isNotEmpty()) {
            if (input.toggles.chat) {
                val latest = chat.filter { it.at in freshChat }.maxBy { it.at }
                post += PlannedNotification(NotificationKind.CHAT, KEY_CHAT, latest.id, count = freshChat.size, text = latest.text)
            }
            seen[KEY_CHAT] = freshChat.max().toString() // advanced even when muted, so un-muting never floods
        }

        for ((type, key, kind, on) in listOf(
            EventRule(EventType.CHECKIN, KEY_CHECKIN, NotificationKind.CHECKIN, input.toggles.checkins),
            EventRule(EventType.NUDGE, KEY_NUDGE, NotificationKind.NUDGE, input.toggles.nudges),
        )) {
            val events = input.events.filter { partner(it.from) && it.type == type }
            val fresh = events.filter { it.at in newSince(key, events.map { e -> e.at }) }
            if (fresh.isEmpty()) continue
            if (on) fresh.forEach { post += PlannedNotification(kind, "$key:${it.id}", it.id, text = it.text, detail = it.kind, seenKey = key) }
            seen[key] = fresh.maxOf { it.at }.toString()
        }

        val partnerSos = input.sos.filter { partner(it.from) }
        partnerSos.forEach { r ->
            val key = "sos:${r.id}"
            val missing = seen[key] == POSTED && input.showing != null && key !in input.showing
            when {
                // An active SOS I haven't acknowledged is (re)shown whenever it isn't on screen: after a reboot,
                // or after notifications were off when it arrived.
                r.state == SosRecordState.ACTIVE && input.myUid !in r.ackBy && (seen[key] == null || missing) -> {
                    post += PlannedNotification(NotificationKind.PARTNER_SOS, key, r.id); seen[key] = POSTED
                }
                r.state == SosRecordState.CANCELLED && seen[key] == POSTED -> {
                    cancel += key; seen[key] = CANCELLED
                }
            }
        }
        // A record that's gone (they left the orbit, or it was cleaned up) can't keep claiming someone needs help.
        if (input.loaded) {
            val ids = partnerSos.map { "sos:${it.id}" }.toSet()
            seen.keys.filter { it.startsWith("sos:") && it !in ids }.forEach { key ->
                if (seen[key] == POSTED) cancel += key
                seen.remove(key)
            }
        }

        fun overdue(trip: TripAlert?, due: Boolean, key: String, kind: NotificationKind) {
            val current = seen[key]
            if (trip != null && due) {
                if (current != trip.key) { post += PlannedNotification(kind, key, trip.key); seen[key] = trip.key }
            } else if (input.loaded && current != null && !current.endsWith(CLEARED)) {
                cancel += key; seen[key] = current + CLEARED
            }
        }
        overdue(input.partnerTrip, input.partnerTrip?.phase == OverduePhase.ALERT_PARTNER, KEY_PARTNER_OVERDUE, NotificationKind.PARTNER_OVERDUE)
        overdue(input.myTrip, input.myTrip?.phase?.let { it != OverduePhase.NOT_DUE } == true, KEY_MY_OVERDUE, NotificationKind.MY_OVERDUE)
        return Plan(post, cancel, seen)
    }

    private data class EventRule(val type: EventType, val key: String, val kind: NotificationKind, val enabled: Boolean)

    const val KEY_CHAT = "chat"
    private const val KEY_CHECKIN = "checkin"
    private const val KEY_NUDGE = "nudge"
    const val KEY_PARTNER_OVERDUE = "partnerOverdue"
    const val KEY_MY_OVERDUE = "myOverdue"
    private const val POSTED = "posted"
    private const val CANCELLED = "cancelled"
    /** Suffix on an overdue key whose notification was taken down because the trip is no longer due. */
    const val CLEARED = "|cleared"
}
