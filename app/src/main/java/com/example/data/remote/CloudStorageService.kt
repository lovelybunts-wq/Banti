package com.example.data.remote

import android.content.Context
import com.example.data.local.LocalFileStorage
import com.example.data.local.SharedDownloadRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CloudUploadResult(
    val storagePath: String,
    val downloadUrl: String,
    val fileSizeBytes: Long,
)

class CloudStorageService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun resolveBucketName(): String {
        val resId = context.resources.getIdentifier("google_storage_bucket", "string", context.packageName)
        if (resId != 0) {
            val value = runCatching { context.getString(resId) }.getOrNull()?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return "gen-lang-client-0425839834.firebasestorage.app"
    }

    private suspend fun requireFirebaseIdToken(): String {
        val user = Firebase.auth.currentUser
            ?: throw IllegalStateException("Please sign in with Google before uploading or downloading cloud files.")
        return user.getIdToken(false).await().token
            ?: throw IllegalStateException("Unable to obtain Firebase auth token.")
    }

    suspend fun uploadSharedFile(
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Float) -> Unit = {},
    ): Result<CloudUploadResult> = withContext(Dispatchers.IO) {
        try {
            val token = requireFirebaseIdToken()
            val bucket = resolveBucketName()
            val encodedName = URLEncoder.encode(storagePath, "UTF-8")
            val downloadToken = UUID.randomUUID().toString()
            val url = "https://firebasestorage.googleapis.com/v0/b/$bucket/o?uploadType=media&name=$encodedName"

            val body = object : RequestBody() {
                override fun contentType() = mimeType.toMediaType()
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: BufferedSink) {
                    val total = bytes.size.coerceAtLeast(1)
                    var offset = 0
                    val chunk = 16 * 1024
                    while (offset < bytes.size) {
                        val len = minOf(chunk, bytes.size - offset)
                        sink.write(bytes, offset, len)
                        offset += len
                        onProgress((offset.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Firebase $token")
                .header("x-goog-meta-firebaseStorageDownloadTokens", downloadToken)
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException(
                        "Cloud Storage Upload failed (${resp.code}). Verify Firebase Storage Rules in Firebase Console: $raw"
                    )
                }
                val json = runCatching { JSONObject(raw) }.getOrNull()
                val tokensFromServer = json?.optString("downloadTokens", "").orEmpty()
                    .split(",").firstOrNull { it.isNotBlank() } ?: downloadToken
                val resolvedUrl = "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedName?alt=media&token=$tokensFromServer"
                onProgress(1f)
                Result.success(
                    CloudUploadResult(
                        storagePath = storagePath,
                        downloadUrl = resolvedUrl,
                        fileSizeBytes = bytes.size.toLong(),
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadSharedFileToCache(
        contentId: String,
        version: Long,
        localFileName: String,
        downloadUrl: String?,
        storagePath: String?,
        inlineHtml: String?,
        fileStorage: LocalFileStorage,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destFile = File(fileStorage.sharedCacheDir(), "${contentId}_${localFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")
            if (!inlineHtml.isNullOrBlank() && downloadUrl.isNullOrBlank()) {
                onProgress(0.5f)
                destFile.writeText(inlineHtml, Charsets.UTF_8)
                fileStorage.saveSharedDownloadRecord(
                    SharedDownloadRecord(
                        contentId = contentId,
                        version = version,
                        localFileName = destFile.name,
                        fileSizeBytes = destFile.length(),
                        downloadedAtMillis = System.currentTimeMillis(),
                    )
                )
                onProgress(1f)
                return@withContext Result.success(destFile)
            }

            val resolvedUrl = when {
                !downloadUrl.isNullOrBlank() -> downloadUrl
                !storagePath.isNullOrBlank() -> {
                    val bucket = resolveBucketName()
                    val enc = URLEncoder.encode(storagePath, "UTF-8")
                    "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$enc?alt=media"
                }
                else -> throw IllegalStateException("File download link unavailable.")
            }

            val reqBuilder = Request.Builder().url(resolvedUrl).get()
            if (!resolvedUrl.contains("token=")) {
                runCatching { requireFirebaseIdToken() }.getOrNull()?.let { tok ->
                    reqBuilder.header("Authorization", "Firebase $tok")
                }
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Download failed (${resp.code})")
                }
                val body = resp.body ?: throw IllegalStateException("Empty download body")
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0L) {
                                onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (destFile.length() == 0L) {
                throw IllegalStateException("Downloaded file was empty.")
            }
            fileStorage.saveSharedDownloadRecord(
                SharedDownloadRecord(
                    contentId = contentId,
                    version = version,
                    localFileName = destFile.name,
                    fileSizeBytes = destFile.length(),
                    downloadedAtMillis = System.currentTimeMillis(),
                )
            )
            onProgress(1f)
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStorageObject(storagePath: String?) {
        if (storagePath.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val token = requireFirebaseIdToken()
                val bucket = resolveBucketName()
                val enc = URLEncoder.encode(storagePath, "UTF-8")
                val req = Request.Builder()
                    .url("https://firebasestorage.googleapis.com/v0/b/$bucket/o/$enc")
                    .header("Authorization", "Firebase $token")
                    .delete()
                    .build()
                client.newCall(req).execute().close()
            }
        }
    }
}
