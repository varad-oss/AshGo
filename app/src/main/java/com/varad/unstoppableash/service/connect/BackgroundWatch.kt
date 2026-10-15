package com.varad.unstoppableash.service.connect

import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.local.SettingsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.presence.PresenceRepository
import com.varad.unstoppableash.data.trips.TripRepository
import com.varad.unstoppableash.domain.location.SharingPlan
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.PresenceSnapshot
import com.varad.unstoppableash.domain.model.SharingMode
import com.varad.unstoppableash.domain.model.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Live answer to [BackgroundPolicy] for this phone: should AshGo keep running once it's cleared? */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundWatch(
    private val pairs: PairRepository,
    private val trips: TripRepository,
    private val presence: PresenceRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val locationServiceRunning: Flow<Boolean>,
) {
    fun needed(): Flow<Boolean> = pairs.pairState().map { it as? PairState.Paired }
        .distinctUntilChanged { a, b -> a?.meta?.pairId == b?.meta?.pairId && a?.myUid == b?.myUid && a?.isWatcher == b?.isWatcher }
        .flatMapLatest { p ->
            if (p == null) flowOf(false)
            else combine(
                listOf(
                    trips.observe(p.meta.pairId, p.partnerUid).onStart { emit(null) },
                    trips.observe(p.meta.pairId, p.myUid).onStart { emit(null) },
                    presence.snapshot(p.meta.pairId, p.partnerUid).map<PresenceSnapshot, PresenceSnapshot?> { it }.onStart { emit(null) },
                    settings.settings.map { it.followPartnerSharing },
                    locationServiceRunning,
                    ticker(), // a timed pause ends by time alone
                ),
            ) { v ->
                val partner = v[2] as PresenceSnapshot?
                BackgroundPolicy.keepConnected(
                    BackgroundInputs(
                        watcher = p.isWatcher,
                        partnerTrip = v[0] as Trip?,
                        partnerSharing = partner?.let { SharingPlan.effective(it.sharing ?: SharingMode.OFF, it.sharingUntil, clock.now()) } ?: SharingMode.OFF,
                        followSharing = v[3] as Boolean,
                        myTrip = v[1] as Trip?,
                        locationServiceRunning = v[4] as Boolean,
                    ),
                )
            }
        }
        .distinctUntilChanged()

    private fun ticker(): Flow<Unit> = flow { while (true) { emit(Unit); delay(60_000) } }
}
