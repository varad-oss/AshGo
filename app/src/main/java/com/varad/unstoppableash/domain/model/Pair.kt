package com.varad.unstoppableash.domain.model

data class PairMeta(
    val pairId: String,
    val memberA: String,
    val memberB: String?,
    val relationship: Relationship,
    val createdAt: Long?,
    val confirmedAt: Long?,
)

data class Profile(
    val uid: String,
    val name: String,
    val avatar: String?,
    val email: String?,
    val lastReadAt: Long?,
    val greeting: Boolean,
)

data class Invite(val pairId: String, val fromUid: String, val fromName: String, val fromEmail: String, val expiresAt: Long)

/** Where the signed-in person is in the orbit lifecycle (USER_JOURNEYS J2–J3, J11). */
sealed interface PairState {
    data object NotSignedIn : PairState
    data object Unpaired : PairState
    /** Created by me; nobody has joined yet. */
    data class Waiting(val meta: PairMeta, val me: Profile?) : PairState
    /** Partner joined; memberA has not confirmed. Private data stays hidden until confirmation. */
    data class Confirming(val meta: PairMeta, val myUid: String, val me: Profile?, val partner: Profile?) : PairState {
        val iMustConfirm: Boolean get() = meta.memberA == myUid
    }
    data class Paired(val meta: PairMeta, val myUid: String, val partnerUid: String, val me: Profile?, val partner: Profile?) : PairState
}
