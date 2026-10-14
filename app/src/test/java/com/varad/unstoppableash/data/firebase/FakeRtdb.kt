package com.varad.unstoppableash.data.firebase

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * In-memory [RtdbClient] for JVM tests: a nested map, server time from [nowMs], a live-observer
 * count to prove listeners are released, and per-path failure injection. It does not evaluate
 * security rules — those are covered by the emulator tests in firebase/test.
 */
class FakeRtdb(var nowMs: Long = 1_000_000L) : RtdbClient {
    private object ServerTimestamp { override fun toString() = "ServerTimestamp" }
    override val serverTimestamp: Any = ServerTimestamp

    private var root: Map<String, Any?> = emptyMap()
    private val version = MutableStateFlow(0L)
    private val writeFailures = mutableMapOf<String, FailureReason>()
    private val readFailurePaths = mutableSetOf<String>()
    private val createOnly = mutableListOf<Regex>()
    private val failureEvents = kotlinx.coroutines.flow.MutableSharedFlow<FailureReason>(extraBufferCapacity = 16)
    override val readFailures: Flow<FailureReason> = failureEvents
    private var keyCounter = 0

    val connectedState = MutableStateFlow(true)
    /** When false, writes wait for the server like the real SDK does offline (they never fail, they just don't finish). */
    val serverReachable = MutableStateFlow(true)
    /**
     * When true, writes behave like the real SDK offline: listeners see them at once (latency compensation),
     * but the call doesn't return until [ackHeld] goes false.
     */
    val ackHeld = MutableStateFlow(false)
    val offset = MutableStateFlow(0L)
    val onDisconnectWrites = mutableMapOf<String, Map<String, Any?>>()
    val writeLog = mutableListOf<String>()
    /** Every write handed to the "SDK", including ones still waiting for the server. */
    val issued = mutableListOf<String>()
    var activeObservers = 0
    /** Total listener subscriptions ever started; a jump means listeners were torn down and rebuilt. */
    var subscriptions = 0
        private set

    fun failWrites(pathPrefix: String, reason: FailureReason) { writeFailures[pathPrefix] = reason }
    /** Observing under [pathPrefix] terminates with [RtdbException] (as rules denying a read do). */
    fun failReads(pathPrefix: String) { readFailurePaths += pathPrefix; version.value++ }
    fun clearFailures() { writeFailures.clear(); readFailurePaths.clear(); version.value++ }
    /** Mimics rules like memberB / SOS / chat that only allow creating, never overwriting. */
    fun createOnly(pathPattern: String) { createOnly += Regex(pathPattern) }
    fun read(path: String): Any? = readAt(root, segments(path))

    override fun observe(path: String): Flow<Any?> = counted(version.map { checkRead(path); read(path) })

    override fun observeLast(path: String, limit: Int, orderByChild: String?): Flow<Any?> = counted(
        version.map {
            checkRead(path)
            val children = (read(path) as? Map<*, *>)?.entries?.map { it.key as String to it.value } ?: return@map null
            val sorted = if (orderByChild == null) children.sortedBy { it.first }
            else children.sortedBy { ((it.second as? Map<*, *>)?.get(orderByChild) as? Number)?.toLong() ?: Long.MIN_VALUE }
            LinkedHashMap<String, Any?>().apply { sorted.takeLast(limit).forEach { put(it.first, it.second) } }.ifEmpty { null }
        },
    )

    override suspend fun get(path: String): Outcome<Any?> = Outcome.Success(read(path))

    override suspend fun set(path: String, value: Any?): Outcome<Unit> {
        if (createOnly.any { it.matches(path) } && read(path) != null) return Outcome.Failure(FailureReason.PERMISSION_DENIED, "exists")
        return setUnchecked(path, value)
    }

    private suspend fun setUnchecked(path: String, value: Any?): Outcome<Unit> = write("set $path", path) {
        root = writeAt(root, segments(path), resolve(value))
    }

    /** Multi-path updates are atomic, as in the SDK: a failure on any child path fails the whole update. */
    override suspend fun update(path: String, values: Map<String, Any?>): Outcome<Unit> {
        values.keys.firstNotNullOfOrNull { rel -> failureFor("$path/$rel") }?.let { return Outcome.Failure(it) }
        return write("update $path ${values.keys.sorted()}", path) {
            values.forEach { (rel, v) -> root = writeAt(root, segments(path) + segments(rel), resolve(v)) }
        }
    }

    override suspend fun remove(path: String): Outcome<Unit> = write("remove $path", path) {
        root = writeAt(root, segments(path), null)
    }

    override fun newKey(path: String): String = "k%06d".format(++keyCounter)
    override fun connected(): Flow<Boolean> = connectedState
    override fun serverTimeOffset(): Flow<Long> = offset

    override suspend fun onDisconnectUpdate(path: String, values: Map<String, Any?>): Outcome<Unit> {
        onDisconnectWrites[path] = values
        return Outcome.Success(Unit)
    }

    override suspend fun cancelOnDisconnect(path: String): Outcome<Unit> {
        issued += "cancelOnDisconnect $path"
        serverReachable.first { it }
        onDisconnectWrites.remove(path)
        return Outcome.Success(Unit)
    }

    /** Simulates the connection dropping: applies queued onDisconnect writes. */
    suspend fun disconnect() {
        connectedState.value = false
        onDisconnectWrites.forEach { (p, v) -> update(p, v) }
    }

    private suspend inline fun write(log: String, path: String, block: () -> Unit): Outcome<Unit> {
        issued += log
        serverReachable.first { it }
        if (ackHeld.value) {
            block(); version.value++
            ackHeld.first { !it }
            writeLog += log
            return Outcome.Success(Unit)
        }
        failureFor(path)?.let { return Outcome.Failure(it) }
        block()
        writeLog += log
        version.value++
        return Outcome.Success(Unit)
    }

    private fun failureFor(path: String): FailureReason? =
        writeFailures.entries.firstOrNull { path == it.key || path.startsWith(it.key + "/") }?.value

    private fun checkRead(path: String) {
        if (readFailurePaths.any { path == it || path.startsWith("$it/") }) {
            failureEvents.tryEmit(FailureReason.PERMISSION_DENIED)
            throw RtdbException(FailureReason.PERMISSION_DENIED, "denied")
        }
    }

    private fun counted(source: Flow<Any?>): Flow<Any?> = flow {
        activeObservers++; subscriptions++
        try { emitAll(source.distinctUntilChanged()) } finally { activeObservers-- }
    }

    private fun resolve(value: Any?): Any? = when (value) {
        ServerTimestamp -> nowMs
        is Map<*, *> -> value.entries.associate { it.key as String to resolve(it.value) }.filterValues { it != null }.ifEmpty { null }
        is Int -> value.toLong()
        is Float -> value.toDouble()
        else -> value
    }

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }

    private fun readAt(node: Any?, segs: List<String>): Any? =
        if (segs.isEmpty()) node else readAt((node as? Map<*, *>)?.get(segs.first()), segs.drop(1))

    private fun writeAt(node: Any?, segs: List<String>, value: Any?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val map = LinkedHashMap((node as? Map<String, Any?>) ?: emptyMap())
        if (segs.size == 1) {
            if (value == null) map.remove(segs[0]) else map[segs[0]] = value
        } else {
            val child = writeAt(map[segs[0]], segs.drop(1), value)
            if (child.isEmpty()) map.remove(segs[0]) else map[segs[0]] = child
        }
        return map
    }
}
