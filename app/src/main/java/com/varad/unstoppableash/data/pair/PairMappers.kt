package com.varad.unstoppableash.data.pair

import com.varad.unstoppableash.domain.model.Invite
import com.varad.unstoppableash.domain.model.PairMeta
import com.varad.unstoppableash.domain.model.Profile
import com.varad.unstoppableash.domain.model.Relationship

/** Plain database values → domain. Malformed data maps to null rather than throwing. */
internal object PairMappers {
    fun meta(pairId: String, value: Any?): PairMeta? {
        val m = value as? Map<*, *> ?: return null
        val memberA = m["memberA"] as? String ?: return null
        return PairMeta(
            pairId = pairId,
            memberA = memberA,
            memberB = m["memberB"] as? String,
            relationship = (m["relationship"] as? String)?.let { runCatching { Relationship.valueOf(it) }.getOrNull() } ?: Relationship.PARTNER,
            createdAt = m.long("createdAt"),
            confirmedAt = m.long("confirmedAt"),
        )
    }

    fun profiles(value: Any?): Map<String, Profile> =
        (value as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> profile(k as String, v)?.let { k to it } }?.toMap().orEmpty()

    fun profile(uid: String, value: Any?): Profile? {
        val m = value as? Map<*, *> ?: return null
        return Profile(
            uid = uid,
            name = m["name"] as? String ?: return null,
            avatar = m["avatar"] as? String,
            email = m["email"] as? String,
            lastReadAt = m.long("lastReadAt"),
            greeting = m["greeting"] as? Boolean ?: true,
        )
    }

    fun invites(value: Any?): List<Invite> =
        (value as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
            val m = v as? Map<*, *> ?: return@mapNotNull null
            Invite(
                pairId = k as String,
                fromUid = m["fromUid"] as? String ?: return@mapNotNull null,
                fromName = m["fromName"] as? String ?: "",
                fromEmail = m["fromEmail"] as? String ?: return@mapNotNull null,
                expiresAt = m.long("expiresAt") ?: return@mapNotNull null,
            )
        }.orEmpty()

    private fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()?.takeIf { it > 0 }
}
