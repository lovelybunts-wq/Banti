package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.local.UserPreferencesManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
    private const val TAG = "Auth"

    fun isFirebaseInitialized(context: Context): Boolean {
        val appCtx = context.applicationContext
        val existing = runCatching { FirebaseApp.getApps(appCtx).isNotEmpty() }.getOrDefault(false)
        if (existing) return true
        return runCatching { FirebaseApp.initializeApp(appCtx) != null }.getOrDefault(false)
    }

    fun safeCurrentUser(context: Context): FirebaseUser? {
        if (!isFirebaseInitialized(context)) return null
        return runCatching { Firebase.auth.currentUser }.getOrNull()
    }

    fun currentUserEmail(context: Context): String =
        safeCurrentUser(context)?.email?.trim().orEmpty()

    fun currentUserName(context: Context): String {
        val user = safeCurrentUser(context) ?: return ""
        return user.displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Student"
    }

    fun currentUserPhotoUrl(context: Context): String? =
        safeCurrentUser(context)?.photoUrl?.toString()?.takeIf { it.isNotBlank() }

    fun isCurrentUserAdmin(context: Context): Boolean =
        currentUserEmail(context).equals(UserPreferencesManager.ADMIN_EMAIL, ignoreCase = true)

    fun resolveWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return null
        return runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope,
    ) {
        if (!isFirebaseInitialized(context)) {
            onUnauthenticated()
            return
        }
        if (safeCurrentUser(context) != null) {
            onAuthSuccess()
            return
        }
        val clientId = resolveWebClientId(context)
        if (clientId == null) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        scope.launch {
            try {
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {},
    ) {
        if (!isFirebaseInitialized(context)) {
            onAuthError(
                "Firebase / Google login config (google-services.json) is missing in this APK build.\n" +
                    "यदि आपने GitHub से APK बनाया है तो Repository Secrets में GOOGLE_SERVICES_JSON जोड़ें। आप तब तक Offline / Google Drive Mode से जारी रख सकते हैं।"
            )
            return
        }
        val clientId = resolveWebClientId(context)
        if (clientId == null) {
            onAuthError(
                "Google Sign-In Web Client ID (default_web_client_id) missing in google-services.json. " +
                    "Firebase Console -> Authentication -> Google Sign-In सक्षम करें।"
            )
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            onAuthError("Activity context required for Google Sign-In")
            return
        }

        scope.launch {
            try {
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val cred = try {
                    credentialManager.getCredential(activity, request).credential
                } catch (noCred: NoCredentialException) {
                    Log.w(TAG, "GetSignInWithGoogleOption returned NoCredentialException, trying GetGoogleIdOption fallback", noCred)
                    val fallbackOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackReq = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    credentialManager.getCredential(activity, fallbackReq).credential
                }

                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned by Google.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled: ${e.message}", e)
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.e(TAG, "No Google credential available", e)
                onAuthError(
                    "इस डिवाइस पर कोई Google Account उपलब्ध नहीं मिला या Firebase / OAuth SHA-1 सेटअप अधूरा है।\n" +
                        "कृपया डिवाइस में Gmail Account जोड़ें (या Google Drive Sign-In / Offline Mode चुनें)।"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                val msg = e.localizedMessage ?: "Google Sign-In failed"
                onAuthError(
                    if (msg.contains("10:") || msg.contains("DEVELOPER_ERROR", ignoreCase = true)) {
                        "Google Sign-In SHA-1 / OAuth Config Error (Code 10): Firebase Console में App का SHA-1 व SHA-256 जोड़कर google-services.json अपडेट करें।"
                    } else {
                        msg
                    }
                )
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {},
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    runCatching { Firebase.auth.signOut() }
                    onSignOutComplete()
                }
            }
        }
    }
}
