package com.varad.unstoppableash.core.time

/** Source of "now" in server-aligned epoch millis; faked in tests. */
fun interface Clock {
    fun now(): Long
}
