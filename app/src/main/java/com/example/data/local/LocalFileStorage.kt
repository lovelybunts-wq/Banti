package com.example.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalFileStorage(private val context: Context) {

    fun userDir(uid: String): File {
        val dir = File(context.filesDir, "learn_talent/$uid")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun brandingDir(): File {
        val dir = File(context.filesDir, "learn_talent/_branding")
        if (!dir.exists()) dir.mkdirs()
        return dir
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
        cloudHtmlContent: String?
    ): String? = withContext(Dispatchers.IO) {
        if (!localFileName.isNullOrBlank()) {
            val file = File(userDir(uid), localFileName)
            if (file.exists()) {
                return@withContext file.readText(Charsets.UTF_8)
            }
        }
        if (!cloudHtmlContent.isNullOrBlank()) {
            if (!localFileName.isNullOrBlank()) {
                val file = File(userDir(uid), localFileName)
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

    companion object {
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
              body { font-family: sans-serif; background: #0D121B; color: #E2E8F0; padding: 18px; line-height: 1.5; }
              .card { background: #171F2E; border: 1px solid #2B3852; border-radius: 14px; padding: 16px; margin-bottom: 14px; }
              h2 { color: #4FD1C5; margin-top: 0; }
              label { display: block; padding: 10px 12px; margin: 6px 0; background: #1E293B; border-radius: 10px; cursor: pointer; }
              button { width: 100%; padding: 14px; border: none; border-radius: 12px; background: #4FD1C5; color: #072926; font-size: 16px; font-weight: bold; cursor: pointer; }
              #result { display: none; margin-top: 16px; padding: 16px; background: #163D3B; border-radius: 12px; color: #A7F3EB; font-size: 18px; font-weight: bold; }
            </style>
            </head>
            <body>
              <h2>$title</h2>
              <form id="quizForm" onsubmit="submitTest(); return false;">
                <div class="card">
                  <p><strong>Q1.</strong> Which article of the Indian Constitution deals with Right to Equality?</p>
                  <label><input type="radio" name="q1" value="1"> Articles 14 to 18</label>
                  <label><input type="radio" name="q1" value="0"> Articles 19 to 22</label>
                  <label><input type="radio" name="q1" value="0"> Articles 25 to 28</label>
                </div>
                <div class="card">
                  <p><strong>Q2.</strong> Which river is known as the Dakshin Ganga?</p>
                  <label><input type="radio" name="q2" value="0"> Krishna</label>
                  <label><input type="radio" name="q2" value="1"> Godavari</label>
                  <label><input type="radio" name="q2" value="0"> Kaveri</label>
                </div>
                <div class="card">
                  <p><strong>Q3.</strong> What is the SI unit of Force?</p>
                  <label><input type="radio" name="q3" value="0"> Joule</label>
                  <label><input type="radio" name="q3" value="0"> Watt</label>
                  <label><input type="radio" name="q3" value="1"> Newton</label>
                </div>
                <div class="card">
                  <p><strong>Q4.</strong> Harappa was located on the banks of which river?</p>
                  <label><input type="radio" name="q4" value="1"> Ravi</label>
                  <label><input type="radio" name="q4" value="0"> Indus</label>
                  <label><input type="radio" name="q4" value="0"> Sutlej</label>
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
