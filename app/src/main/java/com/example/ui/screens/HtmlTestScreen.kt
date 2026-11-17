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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.InProgressTestAttempt
import com.example.data.model.QuestionAttemptRecord
import com.example.data.model.StudyNote
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalStudyTheme
import kotlinx.coroutines.delay

private class HtmlCbtBridge(
    private val onScoreDetected: (obtained: Double, total: Double, summary: String) -> Unit,
    private val onSnapshotJson: (String) -> Unit,
    private val onHtmlSubmitClick: () -> Unit,
) {
    @JavascriptInterface
    fun submitScore(obtained: Double, total: Double, summary: String?) {
        onScoreDetected(obtained, total, summary ?: "")
    }

    @JavascriptInterface
    fun onQuizSnapshot(jsonStr: String) {
        onSnapshotJson(jsonStr)
    }

    @JavascriptInterface
    fun onHtmlSubmitTriggered() {
        onHtmlSubmitClick()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlTestScreen(
    note: StudyNote?,
    subjectId: String,
    topicId: String,
    subtopicId: String,
    isTestMode: Boolean,
    existingDraft: InProgressTestAttempt? = null,
    loadHtml: suspend (StudyNote) -> String?,
    onSaveDraft: (InProgressTestAttempt) -> Unit = {},
    onBack: () -> Unit,
    onSubmitFinalResult: (
        obtained: Double,
        total: Double,
        totalQ: Int,
        attempted: Int,
        correct: Int,
        wrong: Int,
        unattempted: Int,
        durationSeconds: Long,
        testMode: String,
        breakdown: List<QuestionAttemptRecord>,
    ) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var showModeDialog by remember(note?.id) { mutableStateOf(isTestMode) }
    var selectedMode by remember { mutableStateOf(existingDraft?.testMode ?: "EXAM") }
    var elapsedSeconds by remember { mutableLongStateOf(existingDraft?.elapsedSeconds ?: 0L) }
    var readerTheme by remember { mutableStateOf(HtmlReaderTheme.DEFAULT) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    var detectedScore by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var latestPayload by remember { mutableStateOf<DomQuizExtractionPayload?>(null) }
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var jsAlertMessage by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(note?.id) {
        isLoading = true
        htmlContent = if (note != null) loadHtml(note) else null
        isLoading = false
    }

    LaunchedEffect(showModeDialog, showSubmitConfirmDialog, isLoading) {
        if (!showModeDialog && !showSubmitConfirmDialog && !isLoading && isTestMode) {
            while (true) {
                delay(1000L)
                elapsedSeconds += 1L
                if (elapsedSeconds % 15L == 0L && note != null) {
                    onSaveDraft(
                        InProgressTestAttempt(
                            noteId = note.id, testTitle = note.title, subjectId = subjectId,
                            topicId = topicId, subtopicId = subtopicId, testMode = selectedMode,
                            elapsedSeconds = elapsedSeconds, savedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(readerTheme, webViewRef) {
        webViewRef?.evaluateJavascript(HtmlTestJsHelper.buildThemeCssJs(readerTheme), null)
    }

    LaunchedEffect(selectedMode, showModeDialog, webViewRef) {
        if (!showModeDialog && isTestMode) {
            webViewRef?.evaluateJavascript(HtmlTestJsHelper.buildPracticeHighlightJs(selectedMode == "PRACTICE"), null)
        }
    }

    fun persistDraftAndExit() {
        if (note != null && isTestMode && elapsedSeconds > 2L) {
            onSaveDraft(
                InProgressTestAttempt(
                    noteId = note.id, testTitle = note.title, subjectId = subjectId,
                    topicId = topicId, subtopicId = subtopicId, testMode = selectedMode,
                    elapsedSeconds = elapsedSeconds, savedAtMillis = System.currentTimeMillis()
                )
            )
        }
        onBack()
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else persistDraftAndExit()
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                    navigationIcon = {
                        IconButton(onClick = { persistDraftAndExit() }, modifier = Modifier.testTag("html_viewer_back_btn")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(note?.title ?: "HTML CBT Test", style = MaterialTheme.typography.titleMedium, color = theme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isTestMode) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = theme.secondary, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(formatDuration(elapsedSeconds), style = MaterialTheme.typography.labelSmall, color = theme.secondary, fontWeight = FontWeight.Bold)
                                    }
                                    Text(selectedMode, style = MaterialTheme.typography.labelSmall, color = theme.primary)
                                }
                            }
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showThemeMenu = true }) {
                                Icon(Icons.Rounded.Palette, contentDescription = "Reader theme", tint = theme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                                HtmlReaderTheme.entries.forEach { t ->
                                    DropdownMenuItem(text = { Text(t.label) }, onClick = { readerTheme = t; showThemeMenu = false })
                                }
                            }
                        }
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = "Fullscreen")
                        }
                        if (isTestMode) {
                            Button(
                                onClick = {
                                    webViewRef?.evaluateJavascript("window.__ltExtractQuizSnapshot && window.__ltExtractQuizSnapshot();", null)
                                    showSubmitConfirmDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                modifier = Modifier.padding(end = 6.dp).testTag("finish_html_test_btn")
                            ) {
                                Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text("Submit")
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                isLoading -> CircularProgressIndicator(color = theme.primary, modifier = Modifier.align(Alignment.Center))
                htmlContent.isNullOrBlank() -> Text(
                    text = "HTML फाइल इस डिवाइस पर उपलब्ध नहीं है। कृपया ऑनलाइन डाउनलोड करें या Google Drive से रिस्टोर करें।",
                    color = theme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.allowFileAccess = true
                                settings.defaultTextEncodingName = "UTF-8"
                                if (isTestMode) {
                                    addJavascriptInterface(
                                        HtmlCbtBridge(
                                            onScoreDetected = { obtained, total, _ ->
                                                post { detectedScore = obtained to total }
                                            },
                                            onSnapshotJson = { raw ->
                                                post {
                                                    HtmlTestJsHelper.parseDomPayload(raw)?.let { p ->
                                                        latestPayload = p
                                                        if (p.scoreObtained != null && p.totalMarks != null) {
                                                            detectedScore = p.scoreObtained to p.totalMarks
                                                        }
                                                    }
                                                }
                                            },
                                            onHtmlSubmitClick = {
                                                post {
                                                    // NEVER auto-exit on HTML submit: ask user confirmation to view rich Result Screen!
                                                    showSubmitConfirmDialog = true
                                                }
                                            }
                                        ),
                                        "LearnTalentBridge"
                                    )
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                        if (!message.isNullOrBlank()) {
                                            jsAlertMessage = message
                                            if (isTestMode) tryParseScore(message)?.let { detectedScore = it }
                                        }
                                        result?.confirm()
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (isTestMode) view?.evaluateJavascript(HtmlTestJsHelper.INJECTED_CBT_OBSERVER_JS, null)
                                        view?.evaluateJavascript(HtmlTestJsHelper.buildThemeCssJs(readerTheme), null)
                                        if (selectedMode == "PRACTICE") view?.evaluateJavascript(HtmlTestJsHelper.buildPracticeHighlightJs(true), null)
                                    }
                                }
                                loadDataWithBaseURL("https://localhost/", htmlContent ?: "", "text/html", "UTF-8", null)
                            }
                        }
                    )
                }
            }
            if (isFullscreen) {
                Surface(color = theme.surfaceElevated.copy(alpha = 0.9f), shape = MaterialTheme.shapes.medium, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    IconButton(onClick = { isFullscreen = false }) {
                        Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit fullscreen", tint = theme.primary)
                    }
                }
            }
        }
    }

    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("टेस्ट मोड चुनें (Select Mode)", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exam Mode में टाइमर चलेगा और अंत में स्कोर व सभी उत्तर मिलेंगे। Practice Mode में आप व्याख्या देख सकते हैं।", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = selectedMode == "EXAM", onClick = { selectedMode = "EXAM" }, label = { Text("Exam Mode") })
                        FilterChip(selected = selectedMode == "PRACTICE", onClick = { selectedMode = "PRACTICE" }, label = { Text("Practice Mode") })
                    }
                    if (existingDraft != null) {
                        OutlinedButton(onClick = { selectedMode = existingDraft.testMode; elapsedSeconds = existingDraft.elapsedSeconds; showModeDialog = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Resume Previous Attempt (${formatDuration(existingDraft.elapsedSeconds)})")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showModeDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)) {
                    Text("Start Test")
                }
            }
        )
    }

    if (jsAlertMessage != null) {
        AlertDialog(
            onDismissRequest = { jsAlertMessage = null }, containerColor = theme.surfaceElevated,
            title = { Text("Test Message", color = theme.onSurface) },
            text = { Text(jsAlertMessage ?: "", color = theme.onSurfaceVariant) },
            confirmButton = { TextButton(onClick = { jsAlertMessage = null }) { Text("OK", color = theme.primary) } }
        )
    }

    if (showSubmitConfirmDialog) {
        val computed = HtmlTestJsHelper.computeMetrics(latestPayload, detectedScore?.first, detectedScore?.second)
        var obtainedText by remember(showSubmitConfirmDialog, computed.obtained) { mutableStateOf(MainViewModel.formatNum(computed.obtained)) }
        var totalText by remember(showSubmitConfirmDialog, computed.total) { mutableStateOf(MainViewModel.formatNum(computed.total)) }

        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("टेस्ट सबमिट करें? (Confirm Submit)", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Attempted: ${computed.attempted} / ${computed.totalQuestions} • Time: ${formatDuration(elapsedSeconds)}",
                        style = MaterialTheme.typography.bodyMedium, color = theme.primary, fontWeight = FontWeight.SemiBold
                    )
                    Text("स्कोर जांचें (जरूरत हो तो संपादित करें) और पूरा रिजल्ट व व्याख्या देखने के लिए Confirm दबाएं:", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = obtainedText, onValueChange = { obtainedText = it }, label = { Text("Score") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f).testTag("manual_score_obtained_input"))
                        OutlinedTextField(value = totalText, onValueChange = { totalText = it }, label = { Text("Total") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f).testTag("manual_score_total_input"))
                    }
                }
            },
            confirmButton = {
                val got = obtainedText.toDoubleOrNull() ?: computed.obtained
                val tot = (totalText.toDoubleOrNull() ?: computed.total).coerceAtLeast(1.0)
                Button(
                    onClick = {
                        showSubmitConfirmDialog = false
                        onSubmitFinalResult(got, tot, computed.totalQuestions, computed.attempted, computed.correct, computed.wrong, computed.unattempted, elapsedSeconds, selectedMode, computed.breakdown)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onPrimary),
                    modifier = Modifier.testTag("confirm_submit_html_test_btn")
                ) { Text("Confirm & View Result") }
            },
            dismissButton = { TextButton(onClick = { showSubmitConfirmDialog = false }) { Text("Continue Test") } }
        )
    }
}

private fun formatDuration(sec: Long): String {
    val m = sec / 60L
    val s = sec % 60L
    return String.format("%02d:%02d", m, s)
}

private fun tryParseScore(text: String): Pair<Double, Double>? {
    val explicit = Regex("""(?:score|marks|result|obtained|प्राप्तांक|अंक)\s*[:=\-]?\s*(\d+(?:\.\d+)?)\s*(?:/|out of)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(text)
        ?: return null
    val got = explicit.groupValues[1].toDoubleOrNull()
    val tot = explicit.groupValues[2].toDoubleOrNull()
    return if (got != null && tot != null && tot > 0.0 && got <= tot) got to tot else null
}
