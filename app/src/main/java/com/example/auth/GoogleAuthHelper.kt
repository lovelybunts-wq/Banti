package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalOfflineStore
import com.example.data.local.UserPreferencesManager
import com.example.data.remote.DriveAuthOutcome
import com.example.data.remote.GoogleDriveBackupManager
import com.example.data.remote.awaitGmsTask
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class GoogleSessionUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val isOffline: Boolean = false,
)

sealed interface GoogleSignInResult {
    data class Success(val session: GoogleSessionUser, val accessToken: String) : GoogleSignInResult
    data class NeedsConsentUi(val intentSender: IntentSender) : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

object GoogleAuthHelper {
    private const val TAG = "GoogleOAuth"

    fun offlineSession(): GoogleSessionUser = GoogleSessionUser(
        uid = LocalOfflineStore.OFFLINE_UID,
        displayName = "Offline Learner",
        email = "",
        photoUrl = null,
        isOffline = true,
    )

    fun sessionFromSettings(settings: LocalAppSettings): GoogleSessionUser? {
        if (settings.driveUserId.isNotBlank() && settings.driveUserEmail.isNotBlank()) {
            return GoogleSessionUser(
                uid = settings.driveUserId,
                displayName = settings.driveUserName.ifBlank { settings.driveUserEmail.substringBefore("@") }.ifBlank { "Student" },
                email = settings.driveUserEmail,
                photoUrl = settings.driveUserPhotoUrl.takeIf { it.isNotBlank() },
                isOffline = false,
            )
        }
        if (settings.offlineModeAccepted) {
            return offlineSession()
        }
        return null
    }

    fun resolveRole(email: String?): String =
        if (email.equals(UserPreferencesManager.ADMIN_EMAIL, ignoreCase = true)) "admin" else "student"

    fun isAdminEmail(email: String?): Boolean = resolveRole(email) == "admin"

    suspend fun startGoogleSignIn(
        activity: Activity,
        driveManager: GoogleDriveBackupManager,
        prefs: UserPreferencesManager,
    ): GoogleSignInResult {
        return when (val outcome = driveManager.requestAccountAndDriveAuthorization(activity)) {
            is DriveAuthOutcome.Authorized -> completeSignInWithToken(outcome.accessToken, driveManager, prefs)
            is DriveAuthOutcome.NeedsResolution -> GoogleSignInResult.NeedsConsentUi(outcome.intentSender)
            is DriveAuthOutcome.Error -> GoogleSignInResult.Error(friendlyOAuthMessage(outcome.message))
        }
    }

    suspend fun completeSignInFromIntent(
        activity: Activity,
        data: Intent?,
        driveManager: GoogleDriveBackupManager,
        prefs: UserPreferencesManager,
    ): GoogleSignInResult {
        val token = driveManager.extractTokenFromIntent(activity, data)
            ?: return GoogleSignInResult.Error("Google sign-in cancelled or token was not returned.")
        return completeSignInWithToken(token, driveManager, prefs)
    }

    private suspend fun completeSignInWithToken(
        accessToken: String,
        driveManager: GoogleDriveBackupManager,
        prefs: UserPreferencesManager,
    ): GoogleSignInResult {
        val profile = driveManager.fetchGoogleUserProfile(accessToken).getOrElse { err ->
            Log.w(TAG, "Profile fetch error: ${err.message}")
            return GoogleSignInResult.Error(
                "Google खाते की प्रोफाइल नहीं मिल सकी (${err.message ?: "Network error"})। इंटरनेट जांचकर पुनः प्रयास करें।"
            )
        }
        if (profile.email.isBlank()) {
            return GoogleSignInResult.Error("Google खाते से Gmail पता प्राप्त नहीं हुआ। कृपया पुनः प्रयास करें।")
        }
        prefs.setDriveUserSession(
            userId = profile.id,
            email = profile.email,
            name = profile.name,
            photoUrl = profile.photoUrl.orEmpty(),
        )
        prefs.setOfflineModeAccepted(true)
        val session = GoogleSessionUser(
            uid = profile.id,
            displayName = profile.name,
            email = profile.email,
            photoUrl = profile.photoUrl,
            isOffline = false,
        )
        return GoogleSignInResult.Success(session = session, accessToken = accessToken)
    }

    suspend fun silentRefreshDriveToken(
        context: Context,
        driveManager: GoogleDriveBackupManager,
    ): String? = driveManager.silentGetDriveToken(context)

    suspend fun signOut(
        context: Context,
        prefs: UserPreferencesManager,
        returnToAuthScreen: Boolean = true,
    ) {
        withContext(NonCancellable) {
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }
            runCatching {
                Identity.getSignInClient(context).signOut().awaitGmsTask()
            }
            prefs.clearDriveUserSession()
            if (returnToAuthScreen) {
                prefs.setOfflineModeAccepted(false)
            }
        }
    }

    private fun friendlyOAuthMessage(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "10:" in lower || "developer_error" in lower ->
                "Google OAuth SHA-1 / Package Config त्रुटि (Code 10)। Google Cloud Console में package com.aistudio.learntalent.xkqmtz व SHA-1 जोड़ें और Drive API सक्षम करें।"
            "16:" in lower || "cancel" in lower ->
                "Google लॉगिन रद्द किया गया।"
            "network" in lower || "timeout" in lower || "unable to resolve host" in lower ->
                "इंटरनेट कनेक्शन उपलब्ध नहीं है। आप 'Continue Offline' से पढ़ाई जारी रख सकते हैं।"
            else -> raw
        }
    }
}
