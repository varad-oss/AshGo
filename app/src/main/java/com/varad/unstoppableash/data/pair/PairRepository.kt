package com.varad.unstoppableash.data.pair

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.result.then
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.AuthGateway
import com.varad.unstoppableash.data.auth.AuthUser
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.emailKey
import com.varad.unstoppableash.data.firebase.resilient
import com.varad.unstoppableash.domain.model.Invite
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.Relationship
import com.varad.unstoppableash.domain.time.Durations
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Pairing (PRODUCT_DECISIONS D-1, D-16). Write order follows the security rules: a pair's meta must
 * exist before profiles, and membership before the userPairs pointer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairRepository(
    private val db: RtdbClient,
    private val auth: AuthGateway,
    private val clock: Clock,
) {
    private object Denied

    fun pairState(): Flow<PairState> = auth.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(PairState.NotSignedIn)
        else db.observe(DbPaths.userPair(user.uid)).resilient().map { it as? String }.distinctUntilChanged()
            .flatMapLatest { pairId -> if (pairId == null) flowOf(PairState.Unpaired) else pairStateOf(user.uid, pairId) }
    }

    /** A denied meta read means "not a member of that pair" (left, removed, stale pointer) — but keep retrying. */
    private fun pairStateOf(uid: String, pairId: String): Flow<PairState> =
        combine(observeOrDenied(DbPaths.meta(pairId)), observeOrDenied(DbPaths.profiles(pairId))) { metaValue, profilesValue ->
            val meta = if (metaValue === Denied) null else PairMappers.meta(pairId, metaValue)
            val profiles = if (profilesValue === Denied) emptyMap() else PairMappers.profiles(profilesValue)
            when {
                meta == null || (meta.memberA != uid && meta.memberB != uid) -> PairState.Unpaired
                meta.memberB == null -> PairState.Waiting(meta, profiles[uid])
                meta.confirmedAt == null -> PairState.Confirming(meta, uid, profiles[uid], profiles[partnerOf(meta.memberA, meta.memberB, uid)])
                else -> {
                    val partner = partnerOf(meta.memberA, meta.memberB, uid)
                    PairState.Paired(meta, uid, partner, profiles[uid], profiles[partner])
                }
            }
        }.distinctUntilChanged()

    private fun observeOrDenied(path: String): Flow<Any?> = db.observe(path).resilient { emit(Denied) }

    fun myInvites(): Flow<List<Invite>> = auth.currentUser.flatMapLatest { user ->
        val email = user?.email
        if (user == null || !user.canPair || email == null) flowOf(emptyList())
        else db.observe(DbPaths.invites(emailKey(email))).resilient().map { PairMappers.invites(it) }
    }

    /**
     * Resumable: if a previous attempt already made me memberA of a pair, that pair is reused.
     * Order meta → pointer → profile, so a failure after the meta write can always be found again;
     * a failed pointer write rolls the meta back so no orphan pair is left.
     */
    suspend fun createPair(relationship: Relationship, myName: String): Outcome<String> {
        val user = pairingUser() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN, "Google sign-in required")
        val name = displayName(myName, user) ?: return Outcome.Failure(FailureReason.INVALID, "name")
        existingPairAsCreator(user.uid)?.let { pairId -> return writeProfile(pairId, user, name).then { Outcome.Success(pairId) } }
        val pairId = db.newKey("pairs")
        val meta = db.set(DbPaths.meta(pairId), mapOf("memberA" to user.uid, "relationship" to relationship.name, "createdAt" to db.serverTimestamp))
        if (meta is Outcome.Failure) return meta
        val pointer = db.set(DbPaths.userPair(user.uid), pairId)
        if (pointer is Outcome.Failure) {
            db.remove(DbPaths.pair(pairId))
            return pointer
        }
        return writeProfile(pairId, user, name).then { Outcome.Success(pairId) }
    }

    suspend fun invite(partnerEmail: String, myName: String): Outcome<Unit> {
        val user = pairingUser() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        val name = displayName(myName, user) ?: return Outcome.Failure(FailureReason.INVALID, "name")
        val email = partnerEmail.trim()
        if (!isPlausibleEmail(email)) return Outcome.Failure(FailureReason.INVALID, "email")
        val pointer = db.get(DbPaths.userPair(user.uid))
        if (pointer is Outcome.Failure) return pointer
        val pairId = (pointer as Outcome.Success).value as? String ?: return Outcome.Failure(FailureReason.NOT_PAIRED)
        return db.set(
            DbPaths.invite(emailKey(email), pairId),
            mapOf(
                "fromUid" to user.uid,
                "fromName" to name,
                "fromEmail" to user.email,
                "createdAt" to db.serverTimestamp,
                // Rules allow at most 24 h. A wide margin covers a phone clock that runs fast before
                // the server offset has loaded.
                "expiresAt" to clock.now() + Durations.DAY - INVITE_MARGIN_MS,
            ),
        )
    }

    /** Resumable: memberB is set-once in the rules, so a retry skips it when it's already me. */
    suspend fun join(invite: Invite, myName: String): Outcome<Unit> {
        val user = pairingUser() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        val name = displayName(myName, user) ?: return Outcome.Failure(FailureReason.INVALID, "name")
        val memberBPath = DbPaths.meta(invite.pairId) + "/memberB"
        val alreadyJoined = (db.get(memberBPath) as? Outcome.Success)?.value == user.uid
        val claim = if (alreadyJoined) Outcome.Success(Unit) else db.set(memberBPath, user.uid)
        return claim
            .then { db.set(DbPaths.userPair(user.uid), invite.pairId) }
            .then { writeProfile(invite.pairId, user, name) }
            .then { db.remove(DbPaths.invite(emailKey(user.email!!), invite.pairId)) }
    }

    suspend fun declineInvite(invite: Invite): Outcome<Unit> {
        val user = pairingUser() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        return db.remove(DbPaths.invite(emailKey(user.email!!), invite.pairId))
    }

    suspend fun confirm(pairId: String): Outcome<Unit> = db.set(DbPaths.meta(pairId) + "/confirmedAt", db.serverTimestamp)

    /**
     * Leaving deletes everything the pair shared (D-16). The presence onDisconnect handler is cancelled
     * first, otherwise a later disconnect would recreate presence under the deleted pair (review I3).
     * "That's not them" is also a leave.
     */
    /** The inviter takes an unused invite back (spec §9: "the inviter can cancel"). */
    suspend fun withdrawInvite(email: String, pairId: String): Outcome<Unit> =
        db.remove(DbPaths.invite(emailKey(email), pairId))

    /**
     * Every delete is handed to the SDK before any is awaited: offline, the SDK queues writes, so a
     * caller that stops waiting (a timeout) still leaves completely once the connection returns.
     */
    suspend fun leave(pairId: String): Outcome<Unit> = coroutineScope {
        val user = auth.current() ?: return@coroutineScope Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        val cancel = async(start = CoroutineStart.UNDISPATCHED) { db.cancelOnDisconnect(DbPaths.presence(pairId, user.uid)) }
        val pair = async(start = CoroutineStart.UNDISPATCHED) { db.remove(DbPaths.pair(pairId)) }
        val pointer = async(start = CoroutineStart.UNDISPATCHED) { db.remove(DbPaths.userPair(user.uid)) }
        cancel.await()
        pair.await().then { pointer.await() }
    }

    private suspend fun existingPairAsCreator(uid: String): String? {
        val pairId = (db.get(DbPaths.userPair(uid)) as? Outcome.Success)?.value as? String ?: return null
        val meta = PairMappers.meta(pairId, (db.get(DbPaths.meta(pairId)) as? Outcome.Success)?.value) ?: return null
        return pairId.takeIf { meta.memberA == uid }
    }

    private suspend fun writeProfile(pairId: String, user: AuthUser, name: String) = db.set(
        DbPaths.profile(pairId, user.uid),
        mapOf("name" to name, "email" to user.email, "greeting" to true),
    )

    private fun pairingUser(): AuthUser? = auth.current()?.takeIf { it.canPair }

    companion object {
        private const val INVITE_MARGIN_MS = 60 * 60_000L
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun isPlausibleEmail(email: String) = EMAIL.matches(email)

        /** The rules require 1–40 characters; fall back to the Google name, else refuse before writing. */
        internal fun displayName(typed: String, user: AuthUser): String? =
            typed.trim().ifEmpty { user.displayName.orEmpty().trim() }.take(40).ifEmpty { null }

        private fun partnerOf(a: String, b: String?, me: String): String = if (a == me) b.orEmpty() else a
    }
}
