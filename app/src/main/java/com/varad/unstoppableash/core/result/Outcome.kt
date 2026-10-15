package com.varad.unstoppableash.core.result

/** Why an operation failed, in terms the UI can explain (ARCHITECTURE_PROPOSAL §9). */
enum class FailureReason { OFFLINE, PERMISSION_DENIED, QUOTA_OR_SERVER, INVALID, NOT_SIGNED_IN, NOT_PAIRED, RATE_LIMITED, UNKNOWN }

sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val reason: FailureReason, val detail: String? = null) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.then(next: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> next(value)
    is Outcome.Failure -> this
}

val Outcome<*>.isSuccess: Boolean get() = this is Outcome.Success
