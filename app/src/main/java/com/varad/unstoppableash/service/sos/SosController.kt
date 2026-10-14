package com.varad.unstoppableash.service.sos

import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.local.ActiveSosStore
import com.varad.unstoppableash.data.local.EmergencyContactsStore
import com.varad.unstoppableash.data.pair.PairRepository
import com.varad.unstoppableash.data.sos.SosRepository
import com.varad.unstoppableash.domain.model.BackendStatus
import com.varad.unstoppableash.domain.model.EmergencyContact
import com.varad.unstoppableash.domain.model.LocationFix
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.SmsStatus
import com.varad.unstoppableash.domain.model.SmsTarget
import com.varad.unstoppableash.domain.model.SosAction
import com.varad.unstoppableash.domain.model.SosEffect
import com.varad.unstoppableash.domain.model.SosState
import com.varad.unstoppableash.domain.sos.SmsComposer
import com.varad.unstoppableash.domain.sos.SosStateMachine
import com.varad.unstoppableash.service.location.LocationSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RaiseResult { RAISED, ALREADY_ACTIVE }

/** What this phone knows about its own location for the current SOS. */
sealed interface SosLocation {
    data object Locating : SosLocation
    data class Known(val fix: LocationFix) : SosLocation
    data object Unavailable : SosLocation
}

/** What happened to the false-alarm text the person confirmed. */
sealed interface FalseAlarm {
    data class Sent(val count: Int) : FalseAlarm
    data object SmsOff : FalseAlarm
}

/** The SOS operations screens use; [SosController] is the real one. */
interface SosControl {
    val state: StateFlow<SosState>
    val location: StateFlow<SosLocation>
    val falseAlarm: StateFlow<FalseAlarm?>
    suspend fun raise(): RaiseResult
    fun cancel()
    fun retryFailedSms()
    suspend fun falseAlarmRecipients(): List<EmergencyContact>
    fun sendFalseAlarm()
    fun dismiss()
}

/**
 * Runs an SOS from this phone (D-6, J10). App-scoped, so it outlives every screen, and persisted through
 * [ActiveSosStore], so it survives process death. Every fact on screen comes from [SosStateMachine]; this class
 * only carries out the reducer's effects and feeds their outcomes back in. The alert record and the SMS
 * never wait for each other, and neither waits for a location fix longer than the [LocationSource] timeout.
 */
class SosController(
    private val scope: CoroutineScope,
    private val pairs: PairRepository,
    private val sos: SosRepository,
    private val contacts: EmergencyContactsStore,
    private val store: ActiveSosStore,
    private val sms: SmsDispatcher,
    private val locator: LocationSource,
    private val clock: Clock,
    private val battery: () -> Int?,
    private val fallbackName: () -> String? = { null },
) : SosControl {
    private val _state = MutableStateFlow<SosState>(SosState.Idle)
    override val state: StateFlow<SosState> = _state.asStateFlow()
    private val _location = MutableStateFlow<SosLocation>(SosLocation.Unavailable)
    override val location: StateFlow<SosLocation> = _location.asStateFlow()

    private val mutex = Mutex()
    private val raiseLock = Mutex()
    private val fixes = HashMap<String, Deferred<LocationFix?>>()
    private var watcher: Job? = null
    private var started = false

    /** Restores a persisted SOS and starts listening for SMS results. Idempotent. */
    fun start() {
        if (started) return
        started = true
        scope.launch { sms.results.collect { r -> dispatch(SosAction.SmsResult(r.sosId, r.contactId, r.status, r.failure)) } }
        scope.launch {
            val restored = runCatching { store.state.first() }.getOrDefault(SosState.Idle)
            mutex.withLock { if (_state.value == SosState.Idle) _state.value = restored }
            this@SosController.restored.complete(Unit)
            if (restored !is SosState.Active) return@launch
            // The alert record is idempotent, so a PENDING or FAILED one is simply written again.
            if (restored.backend != BackendStatus.SAVED) run(SosEffect.WriteBackend(restored.sosId)) else watchPartner(restored.sosId)
            // An SMS still "queued" may or may not have gone before the process died; resending could text
            // someone twice, so it is shown as unconfirmed with Retry instead.
            restored.sms.filterValues { it.status == SmsStatus.QUEUED }.keys.forEach {
                dispatch(SosAction.SmsResult(restored.sosId, it, SmsStatus.FAILED, NOT_CONFIRMED))
            }
        }
    }

    /** Two quick holds (or the tile and the app at once) still make one SOS. */
    /**
     * Runs on the app scope, so a caller going away (the hold screen closing) can't cancel it half-way. Needs
     * nothing from the network: the orbit is resolved later by the record write, never before the texts.
     */
    override suspend fun raise(): RaiseResult = scope.async { raiseNow() }.await()

    private suspend fun raiseNow(): RaiseResult = raiseLock.withLock {
        restored.await() // a persisted SOS wins over a raise in the first moments after start-up
        if (_state.value is SosState.Active) return@withLock RaiseResult.ALREADY_ACTIVE
        val targets = runCatching { contacts.contacts.first() }.getOrDefault(emptyList()).map { SmsTarget(it.id) }
        val id = sos.newSosId()
        _falseAlarm.value = null
        synchronized(fixes) { fixes[id] = locate() } // started now, shared by the record and every SMS
        dispatch(SosAction.Raise(id, clock.now(), targets))
        RaiseResult.RAISED
    }

    override fun cancel() = withActive { dispatch(SosAction.Cancel(it.sosId, clock.now())) }

    override fun retryFailedSms() = withActive { dispatch(SosAction.RetryFailedSms(it.sosId)) }

    /** Contacts who actually got the SOS text: the only ones a "false alarm" text is offered for. */
    override suspend fun falseAlarmRecipients(): List<EmergencyContact> = recipientsOf(_state.value as? SosState.Cancelled)

    private suspend fun recipientsOf(s: SosState.Cancelled?): List<EmergencyContact> {
        if (s == null) return emptyList()
        val texted = s.sms.filterValues { it.status == SmsStatus.SENT || it.status == SmsStatus.DELIVERED }.keys
        return contacts.contacts.first().filter { it.id in texted }
    }

    /** Only after the person confirmed on screen (it costs money). Results are not tracked against the SOS. */
    override fun sendFalseAlarm() {
        // Captured now: the person may tap Done straight away, and the text they confirmed must still go.
        val s = _state.value as? SosState.Cancelled ?: return
        scope.launch {
            if (!sms.canSend()) { _falseAlarm.value = FalseAlarm.SmsOff; return@launch }
            val recipients = recipientsOf(s)
            val text = SmsComposer.falseAlarm(myName())
            recipients.forEach { sms.send("${s.sosId}-false-alarm", it.id, it.number, text) }
            _falseAlarm.value = FalseAlarm.Sent(recipients.size)
        }
    }

    /** Leaves the "Cancelled" summary. An active SOS can only end through [cancel]. */
    override fun dismiss() {
        scope.launch { dispatch(SosAction.Reset) }
    }

    private fun withActive(block: suspend (SosState.Active) -> Unit) {
        scope.launch { (_state.value as? SosState.Active)?.let { block(it) } }
    }

    private suspend fun dispatch(action: SosAction) {
        val effects = mutex.withLock {
            val before = _state.value
            val t = SosStateMachine.reduce(before, action)
            if (t.state != before) {
                _state.value = t.state
                // Best effort: a full or broken disk must never stop the alert and the texts.
                runCatching { store.save(t.state) }
            }
            t.effects
        }
        effects.forEach(::run)
        if (action is SosAction.SmsResult) pushSmsCounts()
    }

    private fun run(effect: SosEffect) {
        when (effect) {
            is SosEffect.WriteBackend -> scope.launch { writeBackend(effect.sosId) }
            is SosEffect.SendSms -> scope.launch { sendSms(effect.sosId, effect.contactId) }
            is SosEffect.WriteCancel -> scope.launch { writeCancel(effect.sosId) }
        }
    }

    private suspend fun writeBackend(id: String) {
        var backoff = 2_000L
        while (isActive(id)) {
            val state = withTimeoutOrNull(PAIR_WAIT_MS) { pairs.pairState().first() }
            if (state != null && state !is PairState.Paired) return dispatch(SosAction.BackendFailed(id)) // no orbit: texts still go
            val p = state as? PairState.Paired
            if (p == null) { // offline with nothing cached: keep trying, the texts don't wait for this
                dispatch(SosAction.BackendFailed(id)); delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000); continue
            }
            // Core record first, without location, so a slow GPS never delays the partner's alert. Offline the SDK
            // holds the write until it reconnects; after a while the screen says "waiting" instead of "saving".
            val write = scope.async { sos.raise(p.meta.pairId, p.myUid, id, location = null, battery = battery()) }
            val result = withTimeoutOrNull(BACKEND_WAIT_MS) { write.await() } ?: run { dispatch(SosAction.BackendFailed(id)); write.await() }
            when (result) {
                is Outcome.Success -> {
                    dispatch(SosAction.BackendSaved(id, clock.now()))
                    watchPartner(id)
                    fixOf(id).await()?.let { sos.updateLocation(p.meta.pairId, id, it) }
                    pushSmsCounts()
                    return
                }
                is Outcome.Failure -> {
                    dispatch(SosAction.BackendFailed(id))
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(30_000)
                }
            }
        }
    }

    private suspend fun sendSms(id: String, contactId: String) {
        if (!sms.canSend()) return dispatch(SosAction.SmsResult(id, contactId, SmsStatus.FAILED, SMS_OFF))
        val contact = contacts.contacts.first().firstOrNull { it.id == contactId }
            ?: return dispatch(SosAction.SmsResult(id, contactId, SmsStatus.FAILED, "contact removed"))
        val text = SmsComposer.sos(myName(), fixOf(id).await(), battery(), clock.now())
        // The location wait can outlast an "I'm safe": never send the alarm after it was called off.
        if (!isActive(id)) return dispatch(SosAction.SmsResult(id, contactId, SmsStatus.FAILED, NOT_SENT_CANCELLED))
        sms.send(id, contactId, contact.number, text) // a refusal comes back through results as FAILED
    }

    private suspend fun writeCancel(id: String) {
        val p = paired() ?: return
        var backoff = 2_000L
        while (sos.cancel(p.meta.pairId, id) is Outcome.Failure) {
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000)
        }
    }

    /** The partner's phone writes these; they are the only source of "delivered" and "seen". */
    private fun watchPartner(id: String) {
        watcher?.cancel()
        watcher = scope.launch {
            val p = paired() ?: return@launch
            sos.records(p.meta.pairId).collect { list ->
                val r = list.firstOrNull { it.id == id } ?: return@collect
                val s = _state.value as? SosState.Active ?: return@collect
                if (s.sosId != id) return@collect
                r.deliveredTo.values.minOrNull()?.takeIf { s.deliveredToPartnerAt == null }?.let { dispatch(SosAction.PartnerDelivered(id, it)) }
                r.ackBy.values.minOrNull()?.takeIf { s.partnerAckAt == null }?.let { dispatch(SosAction.PartnerAcked(id, it)) }
            }
        }
    }

    private suspend fun pushSmsCounts() {
        val s = _state.value as? SosState.Active ?: return
        if (s.backend != BackendStatus.SAVED) return
        val p = paired() ?: return
        val sent = s.sms.values.count { it.status == SmsStatus.SENT || it.status == SmsStatus.DELIVERED }
        val failed = s.sms.values.count { it.status == SmsStatus.FAILED }
        sos.updateSmsCounts(p.meta.pairId, s.sosId, sent, failed)
    }

    private fun fixOf(id: String): Deferred<LocationFix?> = synchronized(fixes) { fixes.getOrPut(id) { locate() } }

    private fun locate(): Deferred<LocationFix?> {
        _location.value = SosLocation.Locating
        return scope.async {
            locator.current().also { _location.value = it?.let(SosLocation::Known) ?: SosLocation.Unavailable }
        }
    }

    private fun isActive(id: String) = (_state.value as? SosState.Active)?.sosId == id

    private suspend fun paired(): PairState.Paired? = pairs.pairState().first() as? PairState.Paired

    /** Never waits long: offline with nothing cached, the alert goes out with the Google name instead. */
    private suspend fun myName(): String = withTimeoutOrNull(NAME_WAIT_MS) { paired() }?.me?.name?.takeIf { it.isNotBlank() }
        ?: fallbackName()?.takeIf { it.isNotBlank() } ?: "An AshGo user"

    private val _falseAlarm = MutableStateFlow<FalseAlarm?>(null)
    override val falseAlarm: StateFlow<FalseAlarm?> = _falseAlarm.asStateFlow()
    private val restored = CompletableDeferred<Unit>()

    companion object {
        const val BACKEND_WAIT_MS = 10_000L
        private const val PAIR_WAIT_MS = 5_000L
        private const val NAME_WAIT_MS = 1_000L
        const val NOT_SENT_CANCELLED = "not sent: cancelled"
        const val SMS_OFF = "SMS off"
        const val NOT_CONFIRMED = "not confirmed: app restarted"
    }
}
