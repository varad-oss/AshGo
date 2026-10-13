package com.varad.unstoppableash.data.firebase

import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The single seam between the app and Firebase Realtime Database. Values are plain Kotlin:
 * `Map<String, Any?>` (insertion-ordered as the database orders children), `String`, `Long`,
 * `Double`, `Boolean`, or null. Every observing Flow removes its listener when collection stops.
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
    fun serverTimeOffset(): Flow<Long>
}
