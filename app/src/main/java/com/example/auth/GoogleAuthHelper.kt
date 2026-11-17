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
import com.example.data.remote.IntentTokenResolution
import com.example.data.remote.awaitGmsTask
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class GoogleSessionUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val isOffline: Boolean = false,
    val emailVerified: Boolean = true,
)

sealed interface GoogleSignInResult {
    data class Success(val session: GoogleSessionUser, val accessToken: String?) : GoogleSignInResult
    data class NeedsConsentUi(val intentSender: IntentSender) : GoogleSignInResult
    data class NeedsFallbackSignInUi(val signInIntent: Intent) : GoogleSignInResult
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

    fun resolveRole(email: String?, emailVerified: Boolean = true): String =
        if (emailVerified && email.equals(UserPreferencesManager.ADMIN_EMAIL, ignoreCase = true)) "admin" else "student"

    fun isAdminEmail(email: String?, emailVerified: Boolean = true): Boolean =
        resolveRole(email, emailVerified) == "admin"

    suspend fun startGoogleSignIn(
        activity: Activity,
        driveManager: GoogleDriveBackupManager,
        prefs: UserPreferencesManager,
    ): GoogleSignInResult {
        return when (val outcome = driveManager.requestAccountAndDriveAuthorization(activity)) {
            is DriveAuthOutcome.Authorized -> completeSignInWithToken(outcome.accessToken, driveManager, prefs)
            is DriveAuthOutcome.NeedsResolution -> GoogleSignInResult.NeedsConsentUi(outcome.intentSender)
            is DriveAuthOutcome.Error -> {
                val lower = outcome.message.lowercase()
                if ("network" in lower || "timeout" in lower || "unable to resolve host" in lower) {
                    GoogleSignInResult.Error(friendlyOAuthMessage(outcome.message))
                } else {
                    // Fallback seamlessly to Play Services GoogleSignIn identity picker so sign-in succeeds
                    // even when Drive AuthorizationClient returns Code 10 / unverified consent or device quirk.
                    Log.w(TAG, "AuthorizationClient fell back to GoogleSignIn: ${outcome.message}")
                    GoogleSignInResult.NeedsFallbackSignInUi(driveManager.buildFallbackGoogleSignInIntent(activity))
                }
            }
        }
    }

    suspend fun completeSignInFromIntent(
        activity: Activity,
        resultCode: Int,
        data: Intent?,
        driveManager: GoogleDriveBackupManager,
        prefs: UserPreferencesManager,
    ): GoogleSignInResult {
        return when (val resolution = driveManager.resolveTokenFromActivityResult(activity, resultCode, data)) {
            is IntentTokenResolution.Token -> {
                completeSignInWithToken(resolution.accessToken, driveManager, prefs)
            }
            is IntentTokenResolution.UserCancelled -> {
                GoogleSignInResult.Error("Google लॉगिन रद्द किया गया। पुनः प्रयास करें या ऑफलाइन जारी रखें।")
            }
            is IntentTokenResolution.Failed -> {
                Log.w(TAG, "Token resolution failed (${resolution.statusCode}): ${resolution.message}. Falling back to GoogleSignIn picker.")
                // Check if GoogleSignIn already has a signed-in account or launch fallback picker
                val existing = runCatching { GoogleSignIn.getLastSignedInAccount(activity) }.getOrNull()
                if (existing != null && !existing.email.isNullOrBlank()) {
                    persistAndReturnFallbackAccount(activity, existing, driveManager, prefs)
                } else {
                    GoogleSignInResult.NeedsFallbackSignInUi(driveManager.buildFallbackGoogleSignInIntent(activity))
                }
            }
        }
    }

    suspend fun completeFallbackSignInFromIntent(
        activity: Activity,
        resultCode: Int,
        data: Intent?,
        driveManager: GoogleDriveBackupManager,
        prefs: UserPreferencesManager,
    ): GoogleSignInResult {
        if (resultCode == Activity.RESULT_CANCELED && data == null) {
            return GoogleSignInResult.Error("Google लॉगिन रद्द किया गया।")
        }
        val accountResult = driveManager.extractFallbackAccountFromIntent(data)
        val account = accountResult.getOrElse { err ->
            val apiEx = err as? ApiException
            return when (apiEx?.statusCode) {
                CommonStatusCodes.CANCELED, 12501 ->
                    GoogleSignInResult.Error("Google लॉगिन रद्द किया गया।")
                CommonStatusCodes.NETWORK_ERROR ->
                    GoogleSignInResult.Error("इंटरनेट कनेक्शन उपलब्ध नहीं है। आप 'Continue Offline' से पढ़ाई जारी रख सकते हैं।")
                else ->
                    GoogleSignInResult.Error(friendlyOAuthMessage(err.message ?: "Google Sign-In failed (${apiEx?.statusCode})"))
            }
        }
        return persistAndReturnFallbackAccount(activity, account, driveManager, prefs)
    }

    private suspend fun persistAndReturnFallbackAccount(
        activity: Activity,
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount,
        driveManager: GoogleDriveBackupManager,
        prefs: UserPreferencesManager,
    ): GoogleSignInResult {
        val email = account.email?.trim().orEmpty()
        if (email.isBlank()) {
            return GoogleSignInResult.Error("Google खाते का ईमेल प्राप्त नहीं हो सका। कृपया पुनः प्रयास करें।")
        }
        val stableId = "drive_${(account.id ?: email).take(40)}"
        val displayName = account.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").ifBlank { "Student" }
        val photoUrl = account.photoUrl?.toString()?.takeIf { it.isNotBlank() }
        val mintedToken = driveManager.tryMintDriveTokenForAccount(activity, account)

        prefs.setDriveUserSession(
            userId = stableId,
            email = email,
            name = displayName,
            photoUrl = photoUrl.orEmpty(),
        )
        prefs.setOfflineModeAccepted(true)

        return GoogleSignInResult.Success(
            session = GoogleSessionUser(
                uid = stableId,
                displayName = displayName,
                email = email,
                photoUrl = photoUrl,
                isOffline = false,
                emailVerified = true,
            ),
            accessToken = mintedToken,
        )
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
        if (profile.email.isBlank() || !profile.emailVerified) {
            return GoogleSignInResult.Error("Google खाते का ईमेल सत्यापित (verified) नहीं है। कृपया सत्यापित Gmail खाते से लॉगिन करें।")
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
            emailVerified = profile.emailVerified,
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
            GoogleDriveBackupManager().signOutFallbackClient(context)
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
                "Google OAuth SHA-1 / Package Config त्रुटि (Code 10)। Google Cloud Console में package com.aistudio.learntalent.xkqmtz व SHA-1 जोड़ें।"
            "16:" in lower || "12501" in lower || "cancel" in lower ->
                "Google लॉगिन रद्द किया गया।"
            "network" in lower || "timeout" in lower || "unable to resolve host" in lower ->
                "इंटरनेट कनेक्शन उपलब्ध नहीं है। आप 'Continue Offline' से पढ़ाई जारी रख सकते हैं।"
            else -> raw
        }
    }
}
