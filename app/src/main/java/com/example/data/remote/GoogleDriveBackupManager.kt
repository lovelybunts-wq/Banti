package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome()
    data class NeedsResolution(val intentSender: android.content.IntentSender) : DriveAuthOutcome()
    data class Error(val message: String) : DriveAuthOutcome()
}

data class GoogleDriveProfile(
    val id: String,
    val email: String,
    val name: String,
)

data class DriveBackupSummary(
    val folderId: String,
    val uploadedFilesCount: Int,
)

data class DriveRestoredPayload(
    val snapshotJson: String,
    val restoredBinaryFiles: Map<String, ByteArray>,
)

class GoogleDriveBackupManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val driveScope = Scope("https://www.googleapis.com/auth/drive.file")
    private val emailScope = Scope("https://www.googleapis.com/auth/userinfo.email")
    private val profileScope = Scope("https://www.googleapis.com/auth/userinfo.profile")

    suspend fun requestDriveAuthorization(activity: Activity): DriveAuthOutcome =
        requestAuthorizationInternal(activity, listOf(driveScope, emailScope, profileScope))

    suspend fun requestAccountAndDriveAuthorization(activity: Activity): DriveAuthOutcome =
        requestAuthorizationInternal(activity, listOf(driveScope, emailScope, profileScope))

    suspend fun silentGetDriveToken(context: Context): String? {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(driveScope))
                .build()
            val result: AuthorizationResult = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()
            if (!result.hasResolution()) result.accessToken?.takeIf { it.isNotBlank() } else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun requestAuthorizationInternal(
        activity: Activity,
        scopes: List<Scope>,
    ): DriveAuthOutcome {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(scopes)
                .build()
            val result: AuthorizationResult = Identity.getAuthorizationClient(activity)
                .authorize(request)
                .await()
            val token = result.accessToken
            if (result.hasResolution() && result.pendingIntent != null) {
                DriveAuthOutcome.NeedsResolution(result.pendingIntent!!.intentSender)
            } else if (!token.isNullOrBlank()) {
                DriveAuthOutcome.Authorized(token)
            } else {
                DriveAuthOutcome.Error("Google Drive access token unavailable. Please select a Google account.")
            }
        } catch (e: Exception) {
            DriveAuthOutcome.Error(e.message ?: "Google authorization failed on this device.")
        }
    }

    suspend fun fetchGoogleUserProfile(accessToken: String): Result<GoogleDriveProfile> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    val txt = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Profile fetch failed (${resp.code})")
                    }
                    val json = JSONObject(txt)
                    val email = json.optString("email", "").trim()
                    val sub = json.optString("sub", "").ifBlank { email }.ifBlank { "google_user" }
                    val name = json.optString("name", "").ifBlank { email.substringBefore("@") }.ifBlank { "Google User" }
                    Result.success(GoogleDriveProfile(id = "drive_${sub.take(32)}", email = email, name = name))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun extractTokenFromIntent(activity: Activity, data: Intent?): String? {
        return try {
            val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            result.accessToken
        } catch (_: Exception) {
            null
        }
    }

    suspend fun backupToLearnTalentFolder(
        accessToken: String,
        snapshotJson: String,
        attachedFiles: List<File>,
    ): Result<DriveBackupSummary> = withContext(Dispatchers.IO) {
        try {
            val folderId = findOrCreateLearnTalentFolder(accessToken)
            var count = 0

            uploadOrUpdateFile(
                accessToken = accessToken,
                folderId = folderId,
                fileName = BACKUP_JSON_NAME,
                mimeType = "application/json",
                bytes = snapshotJson.toByteArray(Charsets.UTF_8)
            )
            count++

            for (file in attachedFiles) {
                if (file.exists() && file.length() > 0L && file.length() <= 20L * 1024L * 1024L) {
                    val alreadyOnDrive = findFileIdInFolder(accessToken, folderId, file.name) != null
                    if (!alreadyOnDrive) {
                        val mime = guessMime(file.name)
                        uploadOrUpdateFile(
                            accessToken = accessToken,
                            folderId = folderId,
                            fileName = file.name,
                            mimeType = mime,
                            bytes = file.readBytes()
                        )
                        count++
                    }
                }
            }
            Result.success(DriveBackupSummary(folderId = folderId, uploadedFilesCount = count))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasBackupInLearnTalentFolder(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val folderId = findFolderId(accessToken, FOLDER_NAME) ?: return@withContext false
            findFileIdInFolder(accessToken, folderId, BACKUP_JSON_NAME) != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreFromLearnTalentFolder(accessToken: String): Result<DriveRestoredPayload> =
        withContext(Dispatchers.IO) {
            try {
                val folderId = findFolderId(accessToken, FOLDER_NAME)
                    ?: throw IllegalStateException("Google Drive पर '$FOLDER_NAME' फोल्डर नहीं मिला।")

                val query = URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
                val listReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name,mimeType)&pageSize=500")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                val listBody = client.newCall(listReq).execute().use { resp ->
                    val txt = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw IllegalStateException("Drive list failed (${resp.code}): $txt")
                    txt
                }

                val filesArr = JSONObject(listBody).optJSONArray("files") ?: JSONArray()
                var jsonString: String? = null
                val binaryFiles = mutableMapOf<String, ByteArray>()

                for (i in 0 until filesArr.length()) {
                    val obj = filesArr.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val bytes = downloadFileBytes(accessToken, id)
                    if (name == BACKUP_JSON_NAME) {
                        jsonString = String(bytes, Charsets.UTF_8)
                    } else {
                        binaryFiles[name] = bytes
                    }
                }

                val resolvedJson = jsonString
                    ?: throw IllegalStateException("'$FOLDER_NAME' फोल्डर में बैकअप डेटा ($BACKUP_JSON_NAME) नहीं मिला।")

                Result.success(DriveRestoredPayload(resolvedJson, binaryFiles))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun findOrCreateLearnTalentFolder(accessToken: String): String {
        findFolderId(accessToken, FOLDER_NAME)?.let { return it }

        val meta = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", "application/vnd.google-apps.folder")
            .toString()

        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer $accessToken")
            .post(meta.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Could not create Drive folder (${resp.code}): $body")
            JSONObject(body).getString("id")
        }
    }

    private fun findFolderId(accessToken: String, folderName: String): String? {
        val rawQ = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val q = URLEncoder.encode(rawQ, "UTF-8")
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)&pageSize=5")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Drive search failed (${resp.code}): $body")
            val files = JSONObject(body).optJSONArray("files") ?: return@use null
            if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    private fun findFileIdInFolder(accessToken: String, folderId: String, fileName: String): String? {
        val escaped = fileName.replace("'", "\\'")
        val rawQ = "name = '$escaped' and '$folderId' in parents and trashed = false"
        val q = URLEncoder.encode(rawQ, "UTF-8")
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)&pageSize=5")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@use null
            val files = JSONObject(body).optJSONArray("files") ?: return@use null
            if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    private fun uploadOrUpdateFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        val existingId = findFileIdInFolder(accessToken, folderId, fileName)
        if (existingId != null) {
            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media")
                .header("Authorization", "Bearer $accessToken")
                .patch(bytes.toRequestBody(mimeType.toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Drive update failed for $fileName (${resp.code}): $err")
                }
            }
        } else {
            val meta = JSONObject()
                .put("name", fileName)
                .put("parents", JSONArray().put(folderId))
                .toString()

            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(meta.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(bytes.toRequestBody(mimeType.toMediaType()))
                .build()

            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Drive upload failed for $fileName (${resp.code}): $err")
                }
            }
        }
    }

    private fun downloadFileBytes(accessToken: String, fileId: String): ByteArray {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Drive download failed (${resp.code})")
            }
            resp.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun guessMime(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    companion object {
        const val FOLDER_NAME = "Learn Talent"
        const val BACKUP_JSON_NAME = "learn_talent_backup.json"
    }
}
