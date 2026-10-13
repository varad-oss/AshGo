package com.varad.unstoppableash.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirebaseRtdbClient(private val db: FirebaseDatabase) : RtdbClient {

    override val serverTimestamp: Any = ServerValue.TIMESTAMP

    override fun observe(path: String): Flow<Any?> = listen(db.getReference(path))

    override fun observeLast(path: String, limit: Int, orderByChild: String?): Flow<Any?> {
        val ref = db.getReference(path)
        val ordered = if (orderByChild != null) ref.orderByChild(orderByChild) else ref.orderByKey()
        return listen(ordered.limitToLast(limit))
    }

    override suspend fun get(path: String): Outcome<Any?> = attempt { plain(db.getReference(path).get().await()) }

    override suspend fun set(path: String, value: Any?): Outcome<Unit> =
        attempt { db.getReference(path).setValue(value).await(); Unit }

    override suspend fun update(path: String, values: Map<String, Any?>): Outcome<Unit> =
        attempt { db.getReference(path).updateChildren(values).await(); Unit }

    override suspend fun remove(path: String): Outcome<Unit> =
        attempt { db.getReference(path).removeValue().await(); Unit }

    override fun newKey(path: String): String = requireNotNull(db.getReference(path).push().key)

    override fun connected(): Flow<Boolean> = observe(DbPaths.CONNECTED).map { it == true }

    override suspend fun onDisconnectUpdate(path: String, values: Map<String, Any?>): Outcome<Unit> =
        attempt { db.getReference(path).onDisconnect().updateChildren(values).await(); Unit }

    override fun serverTimeOffset(): Flow<Long> = observe(DbPaths.SERVER_TIME_OFFSET).map { (it as? Number)?.toLong() ?: 0L }

    private fun listen(query: Query): Flow<Any?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(plain(snapshot)) }
            override fun onCancelled(error: DatabaseError) { close(RtdbException(reasonOf(error.code), error.message)) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: DatabaseException) {
        Outcome.Failure(reasonOf(e.message), e.message)
    } catch (e: Exception) {
        val error = runCatching { DatabaseError.fromException(e) }.getOrNull()
        Outcome.Failure(error?.let { reasonOf(it.code) } ?: reasonOf(e.message), e.message)
    }

    companion object {
        /** Snapshot → plain values, preserving the database's child order. */
        fun plain(snapshot: DataSnapshot): Any? =
            if (snapshot.hasChildren()) {
                LinkedHashMap<String, Any?>().apply { snapshot.children.forEach { put(requireNotNull(it.key), plain(it)) } }
            } else {
                snapshot.value
            }

        fun reasonOf(code: Int): FailureReason = when (code) {
            DatabaseError.PERMISSION_DENIED -> FailureReason.PERMISSION_DENIED
            DatabaseError.DISCONNECTED, DatabaseError.NETWORK_ERROR, DatabaseError.UNAVAILABLE -> FailureReason.OFFLINE
            DatabaseError.MAX_RETRIES, DatabaseError.OVERRIDDEN_BY_SET, DatabaseError.WRITE_CANCELED -> FailureReason.QUOTA_OR_SERVER
            else -> FailureReason.UNKNOWN
        }

        private fun reasonOf(message: String?): FailureReason = when {
            message == null -> FailureReason.UNKNOWN
            message.contains("permission", ignoreCase = true) -> FailureReason.PERMISSION_DENIED
            message.contains("network", ignoreCase = true) || message.contains("disconnect", ignoreCase = true) -> FailureReason.OFFLINE
            else -> FailureReason.UNKNOWN
        }
    }
}

/** Terminates an observing Flow when the database cancels the listener (e.g. rules deny the read). */
class RtdbException(val reason: FailureReason, message: String?) : Exception(message)
