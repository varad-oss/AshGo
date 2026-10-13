package com.varad.unstoppableash.data.pair

import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import com.varad.unstoppableash.core.result.then
import com.varad.unstoppableash.core.time.Clock
import com.varad.unstoppableash.data.auth.AuthGateway
import com.varad.unstoppableash.data.auth.AuthUser
import com.varad.unstoppableash.data.firebase.DbPaths
import com.varad.unstoppableash.data.firebase.RtdbClient
import com.varad.unstoppableash.data.firebase.emailKey
import com.varad.unstoppableash.domain.model.Invite
import com.varad.unstoppableash.domain.model.PairState
import com.varad.unstoppableash.domain.model.Relationship
import com.varad.unstoppableash.domain.time.Durations
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    fun pairState(): Flow<PairState> = auth.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(PairState.NotSignedIn)
        else db.observe(DbPaths.userPair(user.uid)).safe().map { it as? String }.distinctUntilChanged()
            .flatMapLatest { pairId -> if (pairId == null) flowOf(PairState.Unpaired) else pairStateOf(user.uid, pairId) }
    }

    private fun pairStateOf(uid: String, pairId: String): Flow<PairState> =
        combine(db.observe(DbPaths.meta(pairId)).safe(), db.observe(DbPaths.profiles(pairId)).safe()) { metaValue, profilesValue ->
            val meta = PairMappers.meta(pairId, metaValue)
            val profiles = PairMappers.profiles(profilesValue)
            when {
                meta == null || (meta.memberA != uid && meta.memberB != uid) -> PairState.Unpaired
                meta.memberB == null -> PairState.Waiting(meta, profiles[uid])
                meta.confirmedAt == null -> PairState.Confirming(meta, uid, profiles[uid], profiles[partnerOf(meta.memberA, meta.memberB, uid)])
                else -> {
                    val partner = partnerOf(meta.memberA, meta.memberB, uid)
                    PairState.Paired(meta, uid, partner, profiles[uid], profiles[partner])
                }
            }
        }

    fun myInvites(): Flow<List<Invite>> = auth.currentUser.flatMapLatest { user ->
        val email = user?.email
        if (user == null || !user.canPair || email == null) flowOf(emptyList())
        else db.observe(DbPaths.invites(emailKey(email))).safe().map { PairMappers.invites(it) }
    }

    suspend fun createPair(relationship: Relationship, myName: String): Outcome<String> {
        val user = pairingUser() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN, "Google sign-in required")
        val pairId = db.newKey("pairs")
        return db.set(DbPaths.meta(pairId), mapOf("memberA" to user.uid, "relationship" to relationship.name, "createdAt" to db.serverTimestamp))
            .then { writeProfile(pairId, user, myName) }
            .then { db.set(DbPaths.userPair(user.uid), pairId) }
            .then { Outcome.Success(pairId) }
    }

    suspend fun invite(partnerEmail: String, myName: String): Outcome<Unit> {
        val user = pairingUser() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        val email = partnerEmail.trim()
        if (!isPlausibleEmail(email)) return Outcome.Failure(FailureReason.INVALID, "email")
        val pairId = (db.get(DbPaths.userPair(user.uid)) as? Outcome.Success)?.value as? String
            ?: return Outcome.Failure(FailureReason.NOT_PAIRED)
        return db.set(
            DbPaths.invite(emailKey(email), pairId),
            mapOf(
                "fromUid" to user.uid,
                "fromName" to myName.take(40),
                "fromEmail" to user.email,
                "createdAt" to db.serverTimestamp,
                // Rules allow at most 24 h; stay safely inside it.
                "expiresAt" to clock.now() + Durations.DAY - INVITE_MARGIN_MS,
            ),
        )
    }

    suspend fun join(invite: Invite, myName: String): Outcome<Unit> {
        val user = pairingUser() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        val key = emailKey(user.email!!)
        return db.set(DbPaths.meta(invite.pairId) + "/memberB", user.uid)
            .then { writeProfile(invite.pairId, user, myName) }
            .then { db.set(DbPaths.userPair(user.uid), invite.pairId) }
            .then { db.remove(DbPaths.invite(key, invite.pairId)) }
    }

    suspend fun declineInvite(invite: Invite): Outcome<Unit> {
        val user = pairingUser() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        return db.remove(DbPaths.invite(emailKey(user.email!!), invite.pairId))
    }

    suspend fun confirm(pairId: String): Outcome<Unit> = db.set(DbPaths.meta(pairId) + "/confirmedAt", db.serverTimestamp)

    /** Leaving deletes everything the pair shared (D-16). "That's not them" is also a leave. */
    suspend fun leave(pairId: String): Outcome<Unit> {
        val user = auth.current() ?: return Outcome.Failure(FailureReason.NOT_SIGNED_IN)
        return db.remove(DbPaths.pair(pairId)).then { db.remove(DbPaths.userPair(user.uid)) }
    }

    private suspend fun writeProfile(pairId: String, user: AuthUser, name: String) = db.set(
        DbPaths.profile(pairId, user.uid),
        mapOf("name" to name.trim().take(40).ifEmpty { user.displayName.orEmpty().take(40) }, "email" to user.email, "greeting" to true),
    )

    private fun pairingUser(): AuthUser? = auth.current()?.takeIf { it.canPair }

    private fun Flow<Any?>.safe(): Flow<Any?> = catch { emit(null) }

    companion object {
        private const val INVITE_MARGIN_MS = 5 * 60_000L
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun isPlausibleEmail(email: String) = EMAIL.matches(email)
        private fun partnerOf(a: String, b: String?, me: String): String = if (a == me) b.orEmpty() else a
    }
}
