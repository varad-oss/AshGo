package com.varad.unstoppableash.domain.model

/** Kept only on this phone; never uploaded (ARCHITECTURE_PROPOSAL §5). */
data class EmergencyContact(val id: String, val name: String, val number: String)

/** A user-chosen recording for the fake-call feature; [uri] is a persisted content URI. */
data class FakeCallVoice(val id: String, val name: String, val uri: String)

/** Digits and a leading '+' only, so "+91 98765 43210" and "+919876543210" compare equal. */
fun normalizedNumber(number: String): String {
    val trimmed = number.trim()
    val digits = trimmed.filter { it.isDigit() }
    return if (trimmed.startsWith("+")) "+$digits" else digits
}
