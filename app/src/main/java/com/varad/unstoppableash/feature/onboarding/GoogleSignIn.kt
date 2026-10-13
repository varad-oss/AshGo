package com.varad.unstoppableash.feature.onboarding

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.varad.unstoppableash.R
import com.varad.unstoppableash.core.result.FailureReason
import com.varad.unstoppableash.core.result.Outcome

/** Credential Manager → Google ID token, which AuthGateway exchanges for a Firebase session (D-1). */
object GoogleSignIn {
    const val CANCELLED = "cancelled"

    suspend fun idToken(activity: Activity): Outcome<String> {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .build()
        return try {
            val credential = CredentialManager.create(activity)
                .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
                .credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Outcome.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Outcome.Failure(FailureReason.UNKNOWN, "unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            Outcome.Failure(FailureReason.UNKNOWN, CANCELLED)
        } catch (e: NoCredentialException) {
            Outcome.Failure(FailureReason.NOT_SIGNED_IN, "no Google account")
        } catch (e: GetCredentialException) {
            Outcome.Failure(FailureReason.UNKNOWN, e.message)
        }
    }
}
