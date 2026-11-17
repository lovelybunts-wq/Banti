package com.example.data.local

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.model.InProgressTestAttempt
import com.example.data.model.PdfAnnotationDocument
import com.example.data.model.RecentActivityItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SharedDownloadRecord(
    val contentId: String = "",
    val version: Long = 1L,
    val localFileName: String = "",
    val fileSizeBytes: Long = 0L,
    val downloadedAtMillis: Long = 0L,
)

class LocalFileStorage(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun userDir(uid: String): File {
        val dir = File(context.filesDir, "learn_talent/$uid")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sharedCacheDir(): File {
        val dir = File(context.filesDir, "learn_talent/_shared_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportsDir(uid: String): File {
        val dir = File(userDir(uid), "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun brandingDir(): File {
        val dir = File(context.filesDir, "learn_talent/_branding")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun downloadIndexFile(): File = File(sharedCacheDir(), "shared_downloads.json")

    fun loadSharedDownloadsMap(): Map<String, SharedDownloadRecord> {
        val file = downloadIndexFile()
        if (!file.exists()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, SharedDownloadRecord>>(file.readText(Charsets.UTF_8))
        }.getOrDefault(emptyMap())
    }

    fun saveSharedDownloadRecord(record: SharedDownloadRecord) {
        runCatching {
            val current = loadSharedDownloadsMap().toMutableMap()
            current[record.contentId] = record
            downloadIndexFile().writeText(json.encodeToString(current), Charsets.UTF_8)
        }
    }

    fun removeSharedDownloadRecord(contentId: String) {
        runCatching {
            val current = loadSharedDownloadsMap().toMutableMap()
            val existing = current.remove(contentId)
            if (existing != null && existing.localFileName.isNotBlank()) {
                val f = File(sharedCacheDir(), existing.localFileName)
                if (f.exists()) f.delete()
            }
            downloadIndexFile().writeText(json.encodeToString(current), Charsets.UTF_8)
        }
    }

    fun getSharedCachedFile(contentId: String, localFileName: String? = null): File? {
        val rec = loadSharedDownloadsMap()[contentId]
        val resolvedName = rec?.localFileName?.takeIf { it.isNotBlank() } ?: localFileName
        if (!resolvedName.isNullOrBlank()) {
            val file = File(sharedCacheDir(), resolvedName)
            if (file.exists() && file.length() > 0L) return file
        }
        return null
    }

    fun resolveAnyNoteFile(uid: String, noteId: String, localFileName: String?, isShared: Boolean): File? {
        if (isShared) {
            getSharedCachedFile(noteId, localFileName)?.let { return it }
        }
        return getLocalFile(uid, localFileName) ?: getSharedCachedFile(noteId, localFileName)
    }

    fun savePdfAnnotationDoc(uid: String, doc: PdfAnnotationDocument) {
        runCatching {
            val dir = File(userDir(uid), "annotations")
            if (!dir.exists()) dir.mkdirs()
            val safeId = doc.noteId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            File(dir, "$safeId.json").writeText(json.encodeToString(doc), Charsets.UTF_8)
        }
    }

    fun loadPdfAnnotationDoc(uid: String, noteId: String): PdfAnnotationDocument? {
        return runCatching {
            val dir = File(userDir(uid), "annotations")
            val safeId = noteId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, "$safeId.json")
            if (!file.exists()) return@runCatching null
            json.decodeFromString<PdfAnnotationDocument>(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun saveInProgressTestAttempt(uid: String, attempt: InProgressTestAttempt) {
        runCatching {
            val dir = File(userDir(uid), "test_drafts")
            if (!dir.exists()) dir.mkdirs()
            val safeId = attempt.noteId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            File(dir, "$safeId.json").writeText(json.encodeToString(attempt), Charsets.UTF_8)
        }
    }

    fun loadInProgressTestAttempt(uid: String, noteId: String): InProgressTestAttempt? {
        return runCatching {
            val dir = File(userDir(uid), "test_drafts")
            val safeId = noteId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, "$safeId.json")
            if (!file.exists()) return@runCatching null
            json.decodeFromString<InProgressTestAttempt>(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun clearInProgressTestAttempt(uid: String, noteId: String) {
        runCatching {
            val dir = File(userDir(uid), "test_drafts")
            val safeId = noteId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(dir, "$safeId.json")
            if (file.exists()) file.delete()
        }
    }

    fun loadRecentActivities(uid: String): List<RecentActivityItem> {
        return runCatching {
            val file = File(userDir(uid), "recent_activity.json")
            if (!file.exists()) return@runCatching emptyList()
            json.decodeFromString<List<RecentActivityItem>>(file.readText(Charsets.UTF_8))
                .sortedByDescending { it.timestampMillis }
        }.getOrDefault(emptyList())
    }

    fun pushRecentActivity(uid: String, item: RecentActivityItem): List<RecentActivityItem> {
        val updated = (listOf(item) + loadRecentActivities(uid).filterNot { it.id == item.id })
            .take(15)
        runCatching {
            File(userDir(uid), "recent_activity.json").writeText(json.encodeToString(updated), Charsets.UTF_8)
        }
        return updated
    }

    suspend fun copyUriToLocal(uid: String, noteId: String, uri: Uri, extensionFallback: String): Pair<String, File> =
        withContext(Dispatchers.IO) {
            val originalName = queryDisplayName(uri) ?: "note_${System.currentTimeMillis()}.$extensionFallback"
            val sanitized = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "${noteId}_$sanitized"
            val destFile = File(userDir(uid), fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to read selected file.")
            if (destFile.length() == 0L) {
                throw IllegalStateException("Selected file appears empty.")
            }
            fileName to destFile
        }

    suspend fun readUriBytesWithMeta(uri: Uri, fallbackExt: String): Triple<String, String, ByteArray> =
        withContext(Dispatchers.IO) {
            val displayName = (queryDisplayName(uri) ?: "upload_${System.currentTimeMillis()}.$fallbackExt")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val mimeType = context.contentResolver.getType(uri) ?: guessMimeFromName(displayName, fallbackExt)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected file.")
            if (bytes.isEmpty()) {
                throw IllegalStateException("Selected file is empty.")
            }
            Triple(displayName, mimeType, bytes)
        }

    suspend fun saveCustomLogoFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = "custom_logo_${System.currentTimeMillis()}.jpg"
        val branding = brandingDir()
        branding.listFiles()?.forEach { it.delete() }
        val destFile = File(branding, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read selected logo image.")
        fileName
    }

    fun getCustomLogoFile(fileName: String): File? {
        if (fileName.isBlank()) return null
        val file = File(brandingDir(), fileName)
        return if (file.exists()) file else null
    }

    fun clearCustomLogo() {
        brandingDir().listFiles()?.forEach { it.delete() }
    }

    suspend fun readHtmlFromFileOrWrite(
        uid: String,
        localFileName: String?,
        cloudHtmlContent: String?,
        noteId: String = "",
        isShared: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        if (isShared && noteId.isNotBlank()) {
            getSharedCachedFile(noteId, localFileName)?.let { cached ->
                return@withContext cached.readText(Charsets.UTF_8)
            }
        }
        if (!localFileName.isNullOrBlank()) {
            val file = File(userDir(uid), localFileName)
            if (file.exists()) {
                return@withContext file.readText(Charsets.UTF_8)
            }
        }
        if (!cloudHtmlContent.isNullOrBlank()) {
            if (!localFileName.isNullOrBlank()) {
                val targetDir = if (isShared) sharedCacheDir() else userDir(uid)
                val file = File(targetDir, localFileName)
                file.writeText(cloudHtmlContent, Charsets.UTF_8)
            }
            return@withContext cloudHtmlContent
        }
        null
    }

    suspend fun writeLocalHtml(uid: String, noteId: String, fileNameBase: String, html: String): String =
        withContext(Dispatchers.IO) {
            val sanitized = fileNameBase.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "${noteId}_$sanitized.html"
            val file = File(userDir(uid), fileName)
            file.writeText(html, Charsets.UTF_8)
            fileName
        }

    suspend fun writeLocalBytes(uid: String, fileName: String, bytes: ByteArray): File =
        withContext(Dispatchers.IO) {
            val file = File(userDir(uid), fileName)
            file.writeBytes(bytes)
            file
        }

    fun listAllUserFiles(uid: String): List<File> =
        userDir(uid).listFiles()?.filter { it.isFile } ?: emptyList()

    suspend fun copyOfflineFilesToUser(targetUid: String) = withContext(Dispatchers.IO) {
        val offlineDir = userDir(LocalOfflineStore.OFFLINE_UID)
        val targetDir = userDir(targetUid)
        offlineDir.listFiles()?.forEach { src ->
            if (src.isFile) {
                val dest = File(targetDir, src.name)
                if (!dest.exists()) {
                    src.copyTo(dest, overwrite = false)
                }
            }
        }
    }

    fun getLocalFile(uid: String, localFileName: String?): File? {
        if (localFileName.isNullOrBlank()) return null
        val file = File(userDir(uid), localFileName)
        return if (file.exists()) file else null
    }

    fun deleteLocalFile(uid: String, localFileName: String?) {
        if (localFileName.isNullOrBlank()) return
        val file = File(userDir(uid), localFileName)
        if (file.exists()) file.delete()
    }

    fun getFileProviderUri(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun sharePdfFile(file: File, chooserTitle: String = "Share PDF") {
        val uri = getFileProviderUri(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun openPdfExternally(file: File) {
        val uri = getFileProviderUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun guessMimeFromName(fileName: String, fallbackExt: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            fallbackExt.equals("pdf", ignoreCase = true) -> "application/pdf"
            fallbackExt.equals("html", ignoreCase = true) -> "text/html"
            else -> "image/jpeg"
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return ""
            return when {
                bytes < 1024L -> "$bytes B"
                bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
                else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }

        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun buildSampleHtmlMockTest(title: String): String = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$title</title>
            <style>
              body { font-family: sans-serif; background: #F7F9FC; color: #14243A; padding: 18px; line-height: 1.5; }
              .card { background: #FFFFFF; border: 1px solid #DCEBFA; border-radius: 14px; padding: 16px; margin-bottom: 14px; box-shadow: 0 2px 8px rgba(20,36,58,0.06); }
              h2 { color: #2456A4; margin-top: 0; }
              label { display: block; padding: 10px 12px; margin: 6px 0; background: #F7F9FC; border: 1px solid #E8EEF5; border-radius: 10px; cursor: pointer; }
              .explanation { display: none; margin-top: 8px; padding: 10px; background: #DCEBFA; border-radius: 8px; font-size: 13px; color: #14243A; }
              button { width: 100%; padding: 14px; border: none; border-radius: 12px; background: #2456A4; color: #FFFFFF; font-size: 16px; font-weight: bold; cursor: pointer; }
              #result { display: none; margin-top: 16px; padding: 16px; background: #DCEBFA; border-radius: 12px; color: #14243A; font-size: 18px; font-weight: bold; }
            </style>
            </head>
            <body>
              <h2>$title</h2>
              <form id="quizForm" onsubmit="submitTest(); return false;">
                <div class="card" data-question>
                  <p class="q-text"><strong>Q1.</strong> Which article of the Indian Constitution deals with Right to Equality?</p>
                  <label><input type="radio" name="q1" value="1" data-correct="true"> Articles 14 to 18</label>
                  <label><input type="radio" name="q1" value="0"> Articles 19 to 22</label>
                  <label><input type="radio" name="q1" value="0"> Articles 25 to 28</label>
                  <div class="explanation">Articles 14–18 guarantee equality before law, prohibition of discrimination, and abolition of untouchability & titles.</div>
                </div>
                <div class="card" data-question>
                  <p class="q-text"><strong>Q2.</strong> Which river is known as the Dakshin Ganga?</p>
                  <label><input type="radio" name="q2" value="0"> Krishna</label>
                  <label><input type="radio" name="q2" value="1" data-correct="true"> Godavari</label>
                  <label><input type="radio" name="q2" value="0"> Kaveri</label>
                  <div class="explanation">Godavari is the longest river in Peninsular India and is widely called Dakshin Ganga.</div>
                </div>
                <div class="card" data-question>
                  <p class="q-text"><strong>Q3.</strong> What is the SI unit of Force?</p>
                  <label><input type="radio" name="q3" value="0"> Joule</label>
                  <label><input type="radio" name="q3" value="0"> Watt</label>
                  <label><input type="radio" name="q3" value="1" data-correct="true"> Newton</label>
                  <div class="explanation">Force = Mass × Acceleration, measured in Newton (N).</div>
                </div>
                <div class="card" data-question>
                  <p class="q-text"><strong>Q4.</strong> Harappa was located on the banks of which river?</p>
                  <label><input type="radio" name="q4" value="1" data-correct="true"> Ravi</label>
                  <label><input type="radio" name="q4" value="0"> Indus</label>
                  <label><input type="radio" name="q4" value="0"> Sutlej</label>
                  <div class="explanation">Harappa (Punjab, Pakistan) was situated on the bank of the Ravi river, whereas Mohenjo-daro was on the Indus.</div>
                </div>
                <button type="button" id="submitBtn" onclick="submitTest()">Submit Test</button>
              </form>
              <div id="result"></div>
              <script>
                function submitTest() {
                  var score = 0;
                  var total = 4;
                  ['q1','q2','q3','q4'].forEach(function(name) {
                    var sel = document.querySelector('input[name="' + name + '"]:checked');
                    if (sel && sel.value === '1') score++;
                  });
                  var exps = document.querySelectorAll('.explanation');
                  for (var i = 0; i < exps.length; i++) exps[i].style.display = 'block';
                  var res = document.getElementById('result');
                  res.style.display = 'block';
                  res.innerText = 'Score: ' + score + ' / ' + total;
                  if (window.LearnTalentBridge && window.LearnTalentBridge.submitScore) {
                    window.LearnTalentBridge.submitScore(score, total, 'Score: ' + score + ' / ' + total);
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
