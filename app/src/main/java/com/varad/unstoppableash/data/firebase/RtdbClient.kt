package com.varad.unstoppableash.data.firebase

import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The single seam between the app and Firebase Realtime Database. Values are plain Kotlin:
 * `Map<String, Any?>` (insertion-ordered as the database orders children), `String`, `Long`,
 * `Double`, `Boolean`, or null. Every observing Flow removes its listener when collection stops.
 *
 * **Offline contract (review I4):** write functions suspend until the server acknowledges. While
 * offline the SDK queues the write and the call stays suspended. Time-critical callers (the SOS
 * orchestrator) must launch writes and reduce their results asynchronously — never gate SMS or UI
 * feedback on a backend write completing.
 */
interface RtdbClient {
    /** Sentinel replaced by server time on write. */
    val serverTimestamp: Any

    fun observe(path: String): Flow<Any?>
    fun observeLast(path: String, limit: Int, orderByChild: String? = null): Flow<Any?>
    suspend fun get(path: String): Outcome<Any?>
    suspend fun set(path: String, value: Any?): Outcome<Unit>
    /** Multi-path update relative to [path]; keys may contain '/'. Applied atomically. */
    suspend fun update(path: String, values: Map<String, Any?>): Outcome<Unit>
    suspend fun remove(path: String): Outcome<Unit>
    fun newKey(path: String): String
    fun connected(): Flow<Boolean>
    suspend fun onDisconnectUpdate(path: String, values: Map<String, Any?>): Outcome<Unit>
    suspend fun cancelOnDisconnect(path: String): Outcome<Unit>
    /** Emits whenever the database cancels a listener (e.g. a read is denied), for the ErrorBanner. */
    val readFailures: Flow<com.varad.unstoppableash.core.result.FailureReason>
    fun serverTimeOffset(): Flow<Long>
}
