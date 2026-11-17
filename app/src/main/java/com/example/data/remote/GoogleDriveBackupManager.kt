package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.data.local.DriveStructuredFilesPayload
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
    val photoUrl: String? = null,
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
        .followRedirects(true)
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
                .setRequestedScopes(listOf(driveScope, emailScope, profileScope))
                .build()
            val result: AuthorizationResult = Identity.getAuthorizationClient(context)
                .authorize(request)
                .awaitGmsTask()
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
                .awaitGmsTask()
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
                    val picture = json.optString("picture", "").trim().takeIf { it.isNotBlank() }
                    Result.success(GoogleDriveProfile(id = "drive_${sub.take(32)}", email = email, name = name, photoUrl = picture))
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

    suspend fun syncStructuredVaultToDrive(
        accessToken: String,
        payload: DriveStructuredFilesPayload,
        attachedFiles: List<File>,
    ): Result<DriveBackupSummary> = withContext(Dispatchers.IO) {
        try {
            val rootId = findOrCreateFolder(accessToken, ROOT_FOLDER_NAME, parentId = null)
            var count = 0

            // 1. Full restore snapshot inside LEARN TALENT/learn_talent_backup.json
            uploadOrUpdateFile(
                accessToken = accessToken,
                folderId = rootId,
                fileName = BACKUP_JSON_NAME,
                mimeType = "application/json",
                bytes = payload.fullBackupJson.toByteArray(Charsets.UTF_8)
            )
            count++

            // 2. Organized Subfolders inside LEARN TALENT/
            val profileFolderId = findOrCreateFolder(accessToken, SUBFOLDER_PROFILE, rootId)
            uploadOrUpdateFile(accessToken, profileFolderId, FILE_STUDENT_PROFILE, "application/json", payload.studentProfileJson.toByteArray(Charsets.UTF_8))
            count++

            val examResultsFolderId = findOrCreateFolder(accessToken, SUBFOLDER_EXAM_RESULTS, rootId)
            uploadOrUpdateFile(accessToken, examResultsFolderId, FILE_EXAM_RESULTS, "application/json", payload.examResultsJson.toByteArray(Charsets.UTF_8))
            count++

            val testHistoryFolderId = findOrCreateFolder(accessToken, SUBFOLDER_TEST_HISTORY, rootId)
            uploadOrUpdateFile(accessToken, testHistoryFolderId, FILE_TEST_HISTORY, "application/json", payload.testHistoryJson.toByteArray(Charsets.UTF_8))
            count++

            val notesFolderId = findOrCreateFolder(accessToken, SUBFOLDER_NOTES, rootId)
            uploadOrUpdateFile(accessToken, notesFolderId, FILE_NOTES_INDEX, "application/json", payload.notesIndexJson.toByteArray(Charsets.UTF_8))
            count++

            for (file in attachedFiles) {
                if (file.exists() && file.length() > 0L && file.length() <= 20L * 1024L * 1024L) {
                    val alreadyInNotes = findFileIdInFolder(accessToken, notesFolderId, file.name) != null
                    if (!alreadyInNotes) {
                        uploadOrUpdateFile(
                            accessToken = accessToken,
                            folderId = notesFolderId,
                            fileName = file.name,
                            mimeType = guessMime(file.name),
                            bytes = file.readBytes()
                        )
                        count++
                    }
                }
            }

            val bookmarksFolderId = findOrCreateFolder(accessToken, SUBFOLDER_BOOKMARKS, rootId)
            uploadOrUpdateFile(accessToken, bookmarksFolderId, FILE_BOOKMARKS, "application/json", payload.bookmarksJson.toByteArray(Charsets.UTF_8))
            count++

            val progressFolderId = findOrCreateFolder(accessToken, SUBFOLDER_PROGRESS_REPORTS, rootId)
            uploadOrUpdateFile(accessToken, progressFolderId, FILE_PROGRESS_REPORT, "application/json", payload.progressReportJson.toByteArray(Charsets.UTF_8))
            count++

            Result.success(DriveBackupSummary(folderId = rootId, uploadedFilesCount = count))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hasBackupInLearnTalentFolder(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootId = findRootLearnTalentFolderId(accessToken) ?: return@withContext false
            findFileIdInFolder(accessToken, rootId, BACKUP_JSON_NAME) != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreFromLearnTalentFolder(accessToken: String): Result<DriveRestoredPayload> =
        withContext(Dispatchers.IO) {
            try {
                val rootId = findRootLearnTalentFolderId(accessToken)
                    ?: throw IllegalStateException("Google Drive पर '$ROOT_FOLDER_NAME' फोल्डर नहीं मिला।")

                val rootFiles = listFilesInFolder(accessToken, rootId)
                var jsonString: String? = null
                val binaryFiles = mutableMapOf<String, ByteArray>()

                for ((id, name, mime) in rootFiles) {
                    if (mime == FOLDER_MIME) continue
                    val bytes = downloadFileBytes(accessToken, id)
                    if (name == BACKUP_JSON_NAME) {
                        jsonString = String(bytes, Charsets.UTF_8)
                    } else {
                        binaryFiles[name] = bytes
                    }
                }

                // Also download attached PDF/HTML/Image files stored inside LEARN TALENT/Notes
                val notesFolderId = findFolderId(accessToken, SUBFOLDER_NOTES, parentId = rootId)
                if (notesFolderId != null) {
                    val notesFiles = listFilesInFolder(accessToken, notesFolderId)
                    for ((id, name, mime) in notesFiles) {
                        if (mime == FOLDER_MIME || name == FILE_NOTES_INDEX) continue
                        if (!binaryFiles.containsKey(name)) {
                            runCatching { downloadFileBytes(accessToken, id) }
                                .onSuccess { bytes -> binaryFiles[name] = bytes }
                        }
                    }
                }

                val resolvedJson = jsonString
                    ?: throw IllegalStateException("'$ROOT_FOLDER_NAME' फोल्डर में बैकअप डेटा ($BACKUP_JSON_NAME) नहीं मिला।")

                Result.success(DriveRestoredPayload(resolvedJson, binaryFiles))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun publishSharedFilePublicReadOnly(
        accessToken: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rootId = findOrCreateFolder(accessToken, ROOT_FOLDER_NAME, parentId = null)
            val catalogFolderId = findOrCreateFolder(accessToken, SUBFOLDER_SHARED_CATALOG, parentId = rootId)
            val fileId = uploadOrUpdateFile(accessToken, catalogFolderId, fileName, mimeType, bytes)
            makeFileAnyoneReadable(accessToken, fileId)
            Result.success("https://drive.google.com/uc?export=download&id=$fileId")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun publishSharedCatalogJsonToDrive(
        accessToken: String,
        catalogJson: String,
    ): Result<String> = publishSharedFilePublicReadOnly(
        accessToken = accessToken,
        fileName = FILE_SHARED_CATALOG,
        mimeType = "application/json",
        bytes = catalogJson.toByteArray(Charsets.UTF_8),
    )

    suspend fun downloadPublicDriveUrl(urlOrId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val resolvedUrl = resolvePublicDriveDownloadUrl(urlOrId)
                ?: throw IllegalArgumentException("Valid Google Drive link or File ID required.")
            val req = Request.Builder().url(resolvedUrl).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Download failed (${resp.code})")
                }
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                if (bytes.isEmpty()) throw IllegalStateException("Downloaded file is empty")
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun findRootLearnTalentFolderId(accessToken: String): String? =
        findFolderId(accessToken, ROOT_FOLDER_NAME, parentId = null)
            ?: findFolderId(accessToken, LEGACY_FOLDER_NAME, parentId = null)

    private fun findOrCreateFolder(accessToken: String, folderName: String, parentId: String?): String {
        findFolderId(accessToken, folderName, parentId)?.let { return it }

        val metaJson = JSONObject()
            .put("name", folderName)
            .put("mimeType", FOLDER_MIME)
        if (!parentId.isNullOrBlank()) {
            metaJson.put("parents", JSONArray().put(parentId))
        }

        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer $accessToken")
            .post(metaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Could not create Drive folder $folderName (${resp.code}): $body")
            JSONObject(body).getString("id")
        }
    }

    private fun findFolderId(accessToken: String, folderName: String, parentId: String?): String? {
        val escaped = folderName.replace("'", "\\'")
        val parentClause = if (!parentId.isNullOrBlank()) " and '$parentId' in parents" else " and 'root' in parents"
        val rawQ = "name = '$escaped' and mimeType = '$FOLDER_MIME'$parentClause and trashed = false"
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

    private fun listFilesInFolder(accessToken: String, folderId: String): List<Triple<String, String, String>> {
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
        val result = mutableListOf<Triple<String, String, String>>()
        for (i in 0 until filesArr.length()) {
            val obj = filesArr.getJSONObject(i)
            result += Triple(obj.getString("id"), obj.getString("name"), obj.optString("mimeType", ""))
        }
        return result
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
    ): String {
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
            return existingId
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
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
                .header("Authorization", "Bearer $accessToken")
                .post(body)
                .build()

            return client.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Drive upload failed for $fileName (${resp.code}): $respBody")
                }
                JSONObject(respBody).getString("id")
            }
        }
    }

    private fun makeFileAnyoneReadable(accessToken: String, fileId: String) {
        val perm = JSONObject()
            .put("role", "reader")
            .put("type", "anyone")
            .toString()
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
            .header("Authorization", "Bearer $accessToken")
            .post(perm.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().close()
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
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val ROOT_FOLDER_NAME = "LEARN TALENT"
        const val LEGACY_FOLDER_NAME = "Learn Talent"

        const val SUBFOLDER_PROFILE = "Student Profile"
        const val SUBFOLDER_EXAM_RESULTS = "Exam Results"
        const val SUBFOLDER_TEST_HISTORY = "Test History"
        const val SUBFOLDER_NOTES = "Notes"
        const val SUBFOLDER_BOOKMARKS = "Bookmarks"
        const val SUBFOLDER_PROGRESS_REPORTS = "Progress Reports"
        const val SUBFOLDER_SHARED_CATALOG = "Shared Catalog"

        val ALL_STUDENT_SUBFOLDERS = listOf(
            SUBFOLDER_PROFILE,
            SUBFOLDER_EXAM_RESULTS,
            SUBFOLDER_TEST_HISTORY,
            SUBFOLDER_NOTES,
            SUBFOLDER_BOOKMARKS,
            SUBFOLDER_PROGRESS_REPORTS,
        )

        const val BACKUP_JSON_NAME = "learn_talent_backup.json"
        const val FILE_STUDENT_PROFILE = "student_profile.json"
        const val FILE_EXAM_RESULTS = "exam_results.json"
        const val FILE_TEST_HISTORY = "test_history.json"
        const val FILE_NOTES_INDEX = "notes_index.json"
        const val FILE_BOOKMARKS = "bookmarks.json"
        const val FILE_PROGRESS_REPORT = "progress_report.json"
        const val FILE_SHARED_CATALOG = "shared_catalog.json"

        fun resolvePublicDriveDownloadUrl(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isBlank()) return null
            if (trimmed.startsWith("https://drive.google.com/uc?")) return trimmed
            val idMatch = Regex("""(?:id=|/d/)([a-zA-Z0-9_-]{15,})""").find(trimmed)
            if (idMatch != null) {
                val id = idMatch.groupValues[1]
                return "https://drive.google.com/uc?export=download&id=$id"
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
            if (Regex("""^[a-zA-Z0-9_-]{15,}$""").matches(trimmed)) {
                return "https://drive.google.com/uc?export=download&id=$trimmed"
            }
            return null
        }
    }
}

internal suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitGmsTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
