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
import com.example.R
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
import com.google.firebase.auth.userProfileChangeRequest
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
        return resolveDisplayName(user)
    }

    fun resolveDisplayName(user: FirebaseUser?): String {
        if (user == null) return "Student"
        user.displayName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        user.providerData.firstNotNullOfOrNull { it.displayName?.trim()?.takeIf { n -> n.isNotBlank() } }?.let { return it }
        user.email?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return "Student"
    }

    fun resolveEmail(user: FirebaseUser?): String? {
        if (user == null) return null
        return user.email?.trim()?.takeIf { it.isNotBlank() }
            ?: user.providerData.firstNotNullOfOrNull { it.email?.trim()?.takeIf { e -> e.isNotBlank() } }
    }

    fun resolvePhotoUrl(user: FirebaseUser?): String? {
        if (user == null) return null
        return user.photoUrl?.toString()?.takeIf { it.isNotBlank() }
            ?: user.providerData.firstNotNullOfOrNull { it.photoUrl?.toString()?.takeIf { p -> p.isNotBlank() } }
    }

    fun currentUserPhotoUrl(context: Context): String? =
        resolvePhotoUrl(safeCurrentUser(context))

    fun isCurrentUserAdmin(context: Context): Boolean =
        currentUserEmail(context).equals(UserPreferencesManager.ADMIN_EMAIL, ignoreCase = true)

    fun resolveWebClientId(context: Context): String? =
        runCatching { context.getString(R.string.default_web_client_id) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private suspend fun signInFirebaseWithGoogleToken(googleCred: GoogleIdTokenCredential): FirebaseUser? {
        val authResult = Firebase.auth
            .signInWithCredential(GoogleAuthProvider.getCredential(googleCred.idToken, null))
            .await()
        val user = authResult.user ?: Firebase.auth.currentUser
        if (user != null) {
            val wantedName = googleCred.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: googleCred.givenName?.trim()?.takeIf { it.isNotBlank() }
                ?: googleCred.id.substringBefore("@").takeIf { it.isNotBlank() }
            val wantedPhoto = googleCred.profilePictureUri
            val needsProfileSync = (user.displayName.isNullOrBlank() && !wantedName.isNullOrBlank()) ||
                (user.photoUrl == null && wantedPhoto != null)
            if (needsProfileSync) {
                runCatching {
                    val updates = userProfileChangeRequest {
                        if (user.displayName.isNullOrBlank() && !wantedName.isNullOrBlank()) {
                            displayName = wantedName
                        }
                        if (user.photoUrl == null && wantedPhoto != null) {
                            photoUri = wantedPhoto
                        }
                    }
                    user.updateProfile(updates).await()
                    user.reload().await()
                }.onFailure { err ->
                    Log.w(TAG, "Non-fatal profile sync warning: ${err.message}")
                }
            }
        }
        return Firebase.auth.currentUser ?: user
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: (FirebaseUser?) -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope,
    ) {
        if (!isFirebaseInitialized(context)) {
            onUnauthenticated()
            return
        }
        safeCurrentUser(context)?.let { existing ->
            onAuthSuccess(existing)
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
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    val signedIn = signInFirebaseWithGoogleToken(googleCred)
                    if (signedIn != null) onAuthSuccess(signedIn) else onUnauthenticated()
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
        onAuthSuccess: (FirebaseUser?) -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {},
    ) {
        if (!isFirebaseInitialized(context)) {
            onAuthError(
                "Firebase / Google login config (google-services.json) इस बिल्ड में मौजूद नहीं है।\n" +
                    "यदि आपने GitHub से APK बनाया है तो Repository Secrets में GOOGLE_SERVICES_JSON जोड़ें।"
            )
            return
        }
        val clientId = resolveWebClientId(context)
        if (clientId == null) {
            onAuthError(
                "Google Sign-In Web Client ID (default_web_client_id) missing. Firebase Console -> Authentication -> Google Sign-In सक्षम करें।"
            )
            return
        }
        val activity = context as? Activity
        if (activity == null) {
            onAuthError("Google Sign-In के लिए Activity उपलब्ध नहीं है।")
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
                    val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                    val signedIn = signInFirebaseWithGoogleToken(googleCred)
                    if (signedIn != null) {
                        onAuthSuccess(signedIn)
                    } else {
                        onAuthError("Google खाता सत्यापित हुआ पर Firebase सेशन शुरू नहीं हो सका। कृपया पुनः प्रयास करें।")
                    }
                } else {
                    onAuthError("Google से अमान्य क्रेडेंशियल प्राप्त हुआ। कृपया पुनः प्रयास करें।")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.e(TAG, "No Google credential available", e)
                onAuthError(
                    "इस डिवाइस पर कोई Google Account लॉगिन नहीं मिला या SHA-1 सेटअप अधूरा है।\n" +
                        "कृपया फोन सेटिंग्स में Gmail Account जोड़ें और इंटरनेट जांचें।"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                val msg = e.localizedMessage ?: "Google Sign-In failed"
                onAuthError(
                    if (msg.contains("10:") || msg.contains("DEVELOPER_ERROR", ignoreCase = true)) {
                        "Google Sign-In SHA-1 / OAuth Config Error (Code 10): Firebase Console में App का SHA-1 व SHA-256 जोड़कर google-services.json अपडेट करें।"
                    } else {
                        "लॉगिन त्रुटि: $msg"
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
