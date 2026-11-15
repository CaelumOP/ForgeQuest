package com.forgequest.app.data.friends

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

private const val TAG = "GoogleSignIn"
private const val WEB_CLIENT_ID = "787186946908-ctsif69lokfk6i609727696lm44avo57.apps.googleusercontent.com"

/**
 * Prompts the user with Google Sign-In via Credential Manager and returns a verified Google ID token for
 * [FriendsApi.authGoogle]. Maps user cancellation to `FriendsException("cancelled", 0)` and other failures to
 * `FriendsException("sign_in_failed", 0)`.
 */
suspend fun googleIdToken(activity: Activity): String {
    val option = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
    val response = try {
        CredentialManager.create(activity).getCredential(activity, request)
    } catch (e: CancellationException) {
        throw e
    } catch (_: GetCredentialCancellationException) {
        throw FriendsException("cancelled", 0)
    } catch (e: Exception) {
        Log.w(TAG, "Credential Manager sign-in failed", e)
        throw FriendsException("sign_in_failed", 0)
    }
    val credential = response.credential
    if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        Log.w(TAG, "Unexpected credential type: ${credential.type}")
        throw FriendsException("sign_in_failed", 0)
    }
    val token = try {
        GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse GoogleIdTokenCredential", e)
        throw FriendsException("sign_in_failed", 0)
    }
    if (token.isBlank()) {
        Log.w(TAG, "Received blank Google ID token")
        throw FriendsException("sign_in_failed", 0)
    }
    return token
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
