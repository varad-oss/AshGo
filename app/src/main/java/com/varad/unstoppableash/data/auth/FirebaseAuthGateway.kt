package com.varad.unstoppableash.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthGateway(private val auth: FirebaseAuth) : AuthGateway {

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override fun current(): AuthUser? = auth.currentUser?.toAuthUser()

    override suspend fun signInWithGoogleIdToken(idToken: String): Outcome<AuthUser> = try {
        // v1 stored nothing under user ids, so the anonymous session is replaced rather than linked;
        // a fresh Google session also guarantees the token's sign_in_provider is google.com.
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        result.user?.toAuthUser()?.let { Outcome.Success(it) } ?: Outcome.Failure(FailureReason.UNKNOWN)
    } catch (e: FirebaseNetworkException) {
        Outcome.Failure(FailureReason.OFFLINE, e.message)
    } catch (e: Exception) {
        Outcome.Failure(FailureReason.UNKNOWN, e.message)
    }

    override suspend fun signOut() = auth.signOut()

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        emailVerified = isEmailVerified,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
        isGoogle = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID },
    )
}
