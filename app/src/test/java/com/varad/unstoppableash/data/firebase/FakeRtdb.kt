package com.varad.unstoppableash.data.firebase

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private var keyCounter = 0

    val connectedState = MutableStateFlow(true)
    val offset = MutableStateFlow(0L)
    val onDisconnectWrites = mutableMapOf<String, Map<String, Any?>>()
    val writeLog = mutableListOf<String>()
    var activeObservers = 0
        private set

    fun failWrites(pathPrefix: String, reason: FailureReason) { writeFailures[pathPrefix] = reason }
    fun clearFailures() = writeFailures.clear()
    fun read(path: String): Any? = readAt(root, segments(path))

    override fun observe(path: String): Flow<Any?> = counted(version.map { read(path) })

    override fun observeLast(path: String, limit: Int, orderByChild: String?): Flow<Any?> = counted(
        version.map {
            val children = (read(path) as? Map<*, *>)?.entries?.map { it.key as String to it.value } ?: return@map null
            val sorted = if (orderByChild == null) children.sortedBy { it.first }
            else children.sortedBy { ((it.second as? Map<*, *>)?.get(orderByChild) as? Number)?.toLong() ?: Long.MIN_VALUE }
            LinkedHashMap<String, Any?>().apply { sorted.takeLast(limit).forEach { put(it.first, it.second) } }.ifEmpty { null }
        },
    )

    override suspend fun get(path: String): Outcome<Any?> = Outcome.Success(read(path))

    override suspend fun set(path: String, value: Any?): Outcome<Unit> = write("set $path", path) {
        root = writeAt(root, segments(path), resolve(value))
    }

    override suspend fun update(path: String, values: Map<String, Any?>): Outcome<Unit> =
        write("update $path ${values.keys.sorted()}", path) {
            values.forEach { (rel, v) -> root = writeAt(root, segments(path) + segments(rel), resolve(v)) }
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

    /** Simulates the connection dropping: applies queued onDisconnect writes. */
    suspend fun disconnect() {
        connectedState.value = false
        onDisconnectWrites.forEach { (p, v) -> update(p, v) }
    }

    private inline fun write(log: String, path: String, block: () -> Unit): Outcome<Unit> {
        writeFailures.entries.firstOrNull { path == it.key || path.startsWith(it.key + "/") }?.let {
            return Outcome.Failure(it.value)
        }
        block()
        writeLog += log
        version.value++
        return Outcome.Success(Unit)
    }

    private fun counted(source: Flow<Any?>): Flow<Any?> = flow {
        activeObservers++
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
