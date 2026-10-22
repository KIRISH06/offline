package com.offlinepay.app

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleAuth {
    /** Paste your "Web application" OAuth Client ID from Google Cloud Console here (see README). */
    const val WEB_CLIENT_ID = ""

    val configured: Boolean get() = WEB_CLIENT_ID.isNotBlank()

    /** Returns name to email. */
    suspend fun signIn(activity: Activity): Result<Pair<String, String>> {
        return try {
            val option = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val cred = result.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                Result.success((g.displayName ?: "") to g.id)
            } else {
                Result.failure(IllegalStateException("Unexpected credential"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
