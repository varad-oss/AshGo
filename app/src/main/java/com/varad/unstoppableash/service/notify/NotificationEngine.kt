package com.varad.unstoppableash.service.notify

import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.chat.ChatRepository
import com.varad.unstoppableash.data.events.EventRepository
import com.varad.unstoppableash.data.local.Settings
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.model.ChatMessage
import com.varad.unstoppableash.domain.model.OrbitEvent
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.SosRecord
import com.varad.unstoppableash.domain.model.Trip
import com.varad.unstoppableash.domain.notify.NotificationPlanner
import com.varad.unstoppableash.domain.notify.PlannedNotification
import com.varad.unstoppableash.domain.notify.PlannerInput
import com.varad.unstoppableash.domain.notify.Toggles
import com.varad.unstoppableash.domain.notify.TripAlert
import com.varad.unstoppableash.domain.trips.OverdueEvaluator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Posts and cancels; the Android implementation builds the actual notifications. */
interface Notifier {
    fun post(n: PlannedNotification, partnerName: String)
    fun cancel(tag: String)
}

/**
 * Turns what's in the orbit into notifications, without a push server (Spark plan): the partner's messages,
 * check-ins, nudges, SOS and overdue trips, plus my own overdue prompt. What was already notified is kept in
 * [SettingsStore] so a restart never reposts. The decisions themselves are [NotificationPlanner]'s.
 */
class NotificationEngine(
    private val pairs: PairRepository,
    private val chat: ChatRepository,
    private val events: EventRepository,
    private val sos: SosRepository,
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val notifier: Notifier,
    private val clock: Clock,
    private val tickMs: Long = 30_000,
) {
    private data class Snapshot(
        val chat: List<ChatMessage>, val events: List<OrbitEvent>, val sos: List<SosRecord>,
        val partnerTrip: Trip?, val myTrip: Trip?, val now: Long,
    )

    suspend fun run() {
        pairs.pairState().map { it as? PairState.Paired }
            .distinctUntilChanged { a, b -> a?.meta?.pairId == b?.meta?.pairId && a?.myUid == b?.myUid && a?.partner?.name == b?.partner?.name }
            .collectLatest { p -> if (p != null) runFor(p) }
    }

    private suspend fun runFor(p: PairState.Paired) {
        val pid = p.meta.pairId
        combine(
            chat.messages(pid, 30).onStart { emit(emptyList()) },
            events.events(pid, 30).onStart { emit(emptyList()) },
            sos.records(pid).onStart { emit(emptyList()) },
            trips.observe(pid, p.partnerUid).onStart { emit(null) },
            trips.observe(pid, p.myUid).onStart { emit(null) },
            ticker(), // overdue becomes due by time alone
        ) { v ->
            @Suppress("UNCHECKED_CAST")
            Snapshot(v[0] as List<ChatMessage>, v[1] as List<OrbitEvent>, v[2] as List<SosRecord>, v[3] as Trip?, v[4] as Trip?, clock.now())
        }.collect { snap ->
            // Read-modify-write of the "already notified" map, one snapshot at a time (collect is sequential).
            val prefs = settings.settings.first()
            val plan = NotificationPlanner.plan(
                PlannerInput(
                    myUid = p.myUid, chat = snap.chat, events = snap.events, sos = snap.sos,
                    partnerTrip = snap.partnerTrip?.let { alertOf(p.partnerUid, it, snap.now) },
                    myTrip = snap.myTrip?.let { alertOf(p.myUid, it, snap.now) },
                    toggles = Toggles(chat = prefs.notifyChat, nudges = prefs.notifyNudges, checkins = prefs.notifyCheckins),
                    seen = prefs.lastNotified, now = snap.now,
                ),
            )
            val name = p.partner?.name.orEmpty()
            plan.cancel.forEach(notifier::cancel)
            plan.post.forEach { notifier.post(it, name) }
            if (plan.seen != prefs.lastNotified) settings.update { s: Settings -> s.copy(lastNotified = plan.seen) }
        }
    }

    private fun alertOf(uid: String, trip: Trip, now: Long) =
        TripAlert("$uid@${trip.extendedTo ?: trip.expectBy}", OverdueEvaluator.evaluate(trip.snapshot, now))

    private fun ticker(): Flow<Unit> = flow { while (true) { emit(Unit); delay(tickMs) } }
}
