package com.varad.unstoppableash.data.auth

import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthGateway(initial: AuthUser? = null) : AuthGateway {
    val user = MutableStateFlow(initial)
    override val currentUser: Flow<AuthUser?> = user
    override fun current(): AuthUser? = user.value
    override suspend fun signInWithGoogleIdToken(idToken: String): Outcome<AuthUser> =
        Outcome.Success(google(idToken).also { user.value = it })
    override suspend fun signOut() { user.value = null }

    companion object {
        fun google(uid: String, email: String = "$uid@example.com") =
            AuthUser(uid = uid, email = email, emailVerified = true, displayName = uid.replaceFirstChar { it.uppercase() }, photoUrl = null, isGoogle = true)
    }
}
