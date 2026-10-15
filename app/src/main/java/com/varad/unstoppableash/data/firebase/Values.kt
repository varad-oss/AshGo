package com.varad.unstoppableash.data.firebase

/** Tolerant readers for plain database values: wrong types and v1 sentinels (0, negatives) become null. */
internal fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()?.takeIf { it > 0 }
internal fun Map<*, *>.double(key: String): Double? = (this[key] as? Number)?.toDouble()
internal fun Map<*, *>.int(key: String): Int? = (this[key] as? Number)?.toInt()
internal fun Map<*, *>.bool(key: String): Boolean? = this[key] as? Boolean
internal fun Map<*, *>.string(key: String): String? = this[key] as? String
