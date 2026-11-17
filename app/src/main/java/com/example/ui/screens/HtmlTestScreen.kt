package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.StudyNote
import com.example.ui.theme.LocalStudyTheme

private class HtmlScoreBridge(
    private val onScoreDetected: (obtained: Double, total: Double, summary: String) -> Unit,
) {
    @JavascriptInterface
    fun submitScore(obtained: Double, total: Double, summary: String?) {
        onScoreDetected(obtained, total, summary ?: "")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlTestScreen(
    note: StudyNote?,
    isTestMode: Boolean,
    loadHtml: suspend (StudyNote) -> String?,
    onBack: () -> Unit,
    onScoreSubmitted: (obtained: Double, total: Double, summary: String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showManualDialog by remember { mutableStateOf(false) }
    var detectedScore by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var detectedSummary by remember { mutableStateOf<String?>(null) }
    var jsAlertMessage by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(note?.id) {
        isLoading = true
        htmlContent = if (note != null) loadHtml(note) else null
        isLoading = false
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onBackground
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val wv = webViewRef
                            if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
                        },
                        modifier = Modifier.testTag("html_viewer_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = note?.title ?: "HTML Viewer",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val det = detectedScore
                        if (det != null) {
                            Text(
                                text = "Detected: ${formatScore(det.first)} / ${formatScore(det.second)} • ऊपर बटन से सेव करें",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.success,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (isTestMode) {
                        Button(
                            onClick = { showManualDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primary,
                                contentColor = theme.onPrimary
                            ),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("finish_html_test_btn")
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.finish_and_save_score))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = theme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                htmlContent.isNullOrBlank() -> {
                    Text(
                        text = "HTML फाइल इस डिवाइस पर उपलब्ध नहीं है। कृपया दोबारा अपलोड करें या Google Drive से रिस्टोर करें।",
                        color = theme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
                else -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = isTestMode
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.allowFileAccess = true
                                settings.defaultTextEncodingName = "UTF-8"
                                if (isTestMode) {
                                    addJavascriptInterface(
                                        HtmlScoreBridge { obtained, total, summary ->
                                            post {
                                                // Record score passively WITHOUT closing the HTML test
                                                // so the user can review all correct/wrong answers!
                                                detectedScore = obtained to total
                                                detectedSummary = summary.ifBlank { "Score: ${formatScore(obtained)} / ${formatScore(total)}" }
                                            }
                                        },
                                        "LearnTalentBridge"
                                    )
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onJsAlert(
                                        view: WebView?,
                                        url: String?,
                                        message: String?,
                                        result: JsResult?
                                    ): Boolean {
                                        if (!message.isNullOrBlank()) {
                                            jsAlertMessage = message
                                            if (isTestMode) {
                                                val parsed = tryParseScore(message)
                                                if (parsed != null) {
                                                    detectedScore = parsed
                                                    detectedSummary = message
                                                }
                                            }
                                        }
                                        result?.confirm()
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (isTestMode) {
                                            view?.evaluateJavascript(INJECTED_OBSERVER_JS, null)
                                        }
                                    }
                                }
                                loadDataWithBaseURL(
                                    "https://localhost/",
                                    htmlContent ?: "",
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (jsAlertMessage != null) {
        AlertDialog(
            onDismissRequest = { jsAlertMessage = null },
            containerColor = theme.surfaceElevated,
            title = { Text("Test Message", color = theme.onSurface) },
            text = { Text(jsAlertMessage ?: "", color = theme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { jsAlertMessage = null }) { Text("OK", color = theme.primary) }
            }
        )
    }

    if (showManualDialog) {
        var obtainedText by remember(showManualDialog) {
            mutableStateOf(detectedScore?.first?.let { formatScore(it) } ?: "")
        }
        var totalText by remember(showManualDialog) {
            mutableStateOf(detectedScore?.second?.let { formatScore(it) } ?: "")
        }
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("टेस्ट स्कोर सेव करें (Save Marks)", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "HTML टेस्ट में दिखाई दिए अपने नंबर (Marks) दर्ज करें और सबमिट करें:",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = obtainedText,
                            onValueChange = { obtainedText = it },
                            label = { Text("Obtained") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("manual_score_obtained_input")
                        )
                        OutlinedTextField(
                            value = totalText,
                            onValueChange = { totalText = it },
                            label = { Text("Total") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("manual_score_total_input")
                        )
                    }
                }
            },
            confirmButton = {
                val obtained = obtainedText.toDoubleOrNull()
                val total = totalText.toDoubleOrNull()
                Button(
                    onClick = {
                        if (obtained != null && total != null && total > 0) {
                            showManualDialog = false
                            val summary = detectedSummary ?: "Score: ${formatScore(obtained)} / ${formatScore(total)}"
                            onScoreSubmitted(obtained, total, summary)
                        }
                    },
                    enabled = obtained != null && total != null && total > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
                ) { Text("Save & Return") }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatScore(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)

private fun tryParseScore(text: String): Pair<Double, Double>? {
    val explicit = Regex("""(?:score|marks|result|obtained|प्राप्तांक|अंक)\s*[:=\-]?\s*(\d+(?:\.\d+)?)\s*(?:/|out of)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        .find(text)
    if (explicit != null) {
        val got = explicit.groupValues[1].toDoubleOrNull()
        val tot = explicit.groupValues[2].toDoubleOrNull()
        if (got != null && tot != null && tot > 0.0 && got <= tot) return got to tot
    }
    return null
}

private val INJECTED_OBSERVER_JS = """
(function() {
  if (window.__ltHooked) return;
  window.__ltHooked = true;
  function extractResultOnly() {
    var bodyText = document.body ? document.body.innerText : '';
    var m = bodyText.match(/(?:your\s*score|total\s*score|final\s*score|marks\s*obtained|result|प्राप्तांक|कुल\s*अंक)\s*[:=\-]?\s*(\d+(?:\.\d+)?)\s*(?:\/|out of)\s*(\d+(?:\.\d+)?)/i);
    if (m) {
      var got = parseFloat(m[1]);
      var tot = parseFloat(m[2]);
      if (!isNaN(got) && !isNaN(tot) && tot > 0 && got <= tot && window.LearnTalentBridge) {
        window.LearnTalentBridge.submitScore(got, tot, m[0].trim());
        return true;
      }
    }
    return false;
  }
  document.addEventListener('submit', function() {
    setTimeout(extractResultOnly, 600);
  }, true);
  document.addEventListener('click', function(e) {
    var el = e.target;
    while (el && el !== document.body) {
      var txt = (el.innerText || el.value || '').toLowerCase();
      if (el.tagName === 'BUTTON' || (el.tagName === 'INPUT' && (el.type === 'submit' || el.type === 'button'))) {
        if (txt.indexOf('submit test') !== -1 || txt.indexOf('finish test') !== -1 || txt.indexOf('final submit') !== -1 || txt.indexOf('सबमिट') !== -1) {
          setTimeout(extractResultOnly, 600);
          break;
        }
      }
      el = el.parentElement;
    }
  }, true);
})();
""".trimIndent()
