package com.varad.unstoppableash.data.auth

import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.flow.Flow

data class AuthUser(
    val uid: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
    val photoUrl: String?,
    /** Rules only let Google-verified accounts pair (PRODUCT_DECISIONS D-16). */
    val isGoogle: Boolean,
) {
    val canPair: Boolean get() = isGoogle && emailVerified && !email.isNullOrBlank()
}

interface AuthGateway {
    val currentUser: Flow<AuthUser?>
    fun current(): AuthUser?
    /** Signs in with a Google ID token from Credential Manager, replacing any v1 anonymous session. */
    suspend fun signInWithGoogleIdToken(idToken: String): Outcome<AuthUser>
    suspend fun signOut()
}
