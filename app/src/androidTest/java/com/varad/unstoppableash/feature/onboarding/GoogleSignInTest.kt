package com.varad.unstoppableash.feature.onboarding

import android.os.Bundle
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.varad.unstoppableash.core.result.Outcome
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInTest {
    @Test fun malformedGoogleCredentialIsAFailureNotACrash() {
        val broken = CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())
        assertTrue(GoogleSignIn.tokenFrom(broken) is Outcome.Failure)
    }
}
