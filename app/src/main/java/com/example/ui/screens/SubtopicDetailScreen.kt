package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SharedDownloadRecord
import com.example.data.model.BookmarkItem
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.VideoLink
import com.example.ui.MainViewModel
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

private sealed interface PendingDeleteTarget {
    data class NoteTarget(val note: StudyNote) : PendingDeleteTarget
    data class VideoTarget(val video: VideoLink) : PendingDeleteTarget
    data class McqTarget(val mcq: McqQuestion) : PendingDeleteTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtopicDetailScreen(
    subject: Subject?,
    topic: Topic?,
    subtopic: Subtopic?,
    notes: List<StudyNote>,
    videos: List<VideoLink>,
    mcqs: List<McqQuestion>,
    testResults: List<TestResult>,
    bookmarks: List<BookmarkItem> = emptyList(),
    sharedDownloadsMap: Map<String, SharedDownloadRecord> = emptyMap(),
    sharedDownloadProgress: Map<String, Float> = emptyMap(),
    isAdminUser: Boolean = false,
    lastReturnedScore: TestResult?,
    onDismissScoreBanner: () -> Unit,
    onBack: () -> Unit,
    onMarkStudiedToday: () -> Unit,
    onRequestMaterialWhatsApp: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onImportFile: (customTitle: String, folderName: String, noteType: NoteType, uri: Uri, isMockTest: Boolean) -> Unit,
    onCreateSampleHtmlTest: (title: String, folderName: String) -> Unit,
    onRenameOrMoveNote: (noteId: String, title: String, folderName: String) -> Unit,
    onOpenNote: (StudyNote) -> Unit,
    onOpenHtmlTest: (StudyNote) -> Unit,
    onDownloadShared: (StudyNote) -> Unit,
    onToggleBookmarkNote: (StudyNote) -> Unit,
    onOpenTestResult: (String) -> Unit,
    onDeleteNote: (StudyNote) -> Unit,
    onAddVideo: (title: String, url: String, note: String?) -> Unit,
    onWatchVideo: (VideoLink) -> Unit,
    onDeleteVideo: (VideoLink) -> Unit,
    onAddMcq: (question: String, options: List<String>, correctIdx: Int, explanation: String?) -> Unit,
    onDeleteMcq: (McqQuestion) -> Unit,
    onStartMcqPractice: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current
    val accent = subject?.let { SubjectVisuals.colorFor(it.colorHex, theme.primary) } ?: theme.primary

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPickedFileName by remember { mutableStateOf("") }
    var pendingImportType by remember { mutableStateOf(NoteType.PDF) }
    var pendingIsTestUpload by remember { mutableStateOf(false) }

    var showFolderDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<StudyNote?>(null) }
    var showAddVideoDialog by remember { mutableStateOf(false) }
    var showAddMcqDialog by remember { mutableStateOf(false) }
    var showCreateSampleTestDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PendingDeleteTarget?>(null) }

    fun readFileName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "selected_file"

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingPickedFileName = readFileName(uri)
            pendingImportType = NoteType.PDF
            pendingIsTestUpload = false
            pendingImportUri = uri
        }
    }
    val htmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingPickedFileName = readFileName(uri)
            pendingImportType = NoteType.HTML
            pendingImportUri = uri
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingPickedFileName = readFileName(uri)
            pendingImportType = NoteType.IMAGE
            pendingIsTestUpload = false
            pendingImportUri = uri
        }
    }

    val folderNames = remember(notes) {
        val set = linkedSetOf("All")
        notes.mapNotNull { it.folderName.takeIf { f -> f.isNotBlank() } }.forEach { set.add(it) }
        set.toList()
    }
    val qLower = searchQuery.trim().lowercase()
    val filteredNotes = remember(notes, selectedFolder, qLower) {
        notes.filter {
            it.resolvedType() != NoteType.FOLDER &&
                (selectedFolder == "All" || it.folderName == selectedFolder) &&
                (qLower.isEmpty() || it.title.lowercase().contains(qLower) || it.folderName.lowercase().contains(qLower))
        }
    }
    val htmlTests = remember(notes, qLower) {
        notes.filter {
            it.resolvedType() == NoteType.HTML && (qLower.isEmpty() || it.title.lowercase().contains(qLower))
        }
    }
    val filteredVideos = remember(videos, qLower) {
        if (qLower.isEmpty()) videos else videos.filter { it.title.lowercase().contains(qLower) }
    }
    val filteredMcqs = remember(mcqs, qLower) {
        if (qLower.isEmpty()) mcqs else mcqs.filter { it.question.lowercase().contains(qLower) }
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("subtopic_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(subtopic?.name ?: "Study Workspace", style = MaterialTheme.typography.titleLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${subject?.nameEn ?: ""} › ${topic?.name ?: ""}", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    TextButton(onClick = onMarkStudiedToday, modifier = Modifier.testTag("mark_studied_today_btn")) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = theme.success, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("आज पढ़ा", color = theme.success)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 960.dp).fillMaxSize()) {
                if (lastReturnedScore != null && lastReturnedScore.subtopicId == subtopic?.id) {
                    Tactile3DCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        accentColor = theme.success,
                        onClick = { onOpenTestResult(lastReturnedScore.id) },
                        testTag = "returned_score_card"
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                TactileIconOrb(icon = Icons.Rounded.EmojiEvents, tint = theme.success, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Test Submitted • ${lastReturnedScore.testTitle}", style = MaterialTheme.typography.titleSmall, color = theme.success)
                                    Text(
                                        "प्राप्त अंक: ${MainViewModel.formatNum(lastReturnedScore.scoreObtained)} / ${MainViewModel.formatNum(lastReturnedScore.totalMarks)} (${MainViewModel.formatNum(lastReturnedScore.percentage)}%)",
                                        style = MaterialTheme.typography.titleMedium, color = theme.onSurface, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            TextButton(onClick = onDismissScoreBanner) { Text("OK", color = theme.primary) }
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search notes, tests, videos...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = theme.background,
                    contentColor = accent,
                    indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = accent) }
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Notes (${filteredNotes.size})") }, modifier = Modifier.testTag("tab_notes"))
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Tests & MCQ") }, modifier = Modifier.testTag("tab_tests"))
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Videos (${filteredVideos.size})") }, modifier = Modifier.testTag("tab_videos"))
                }

                when (selectedTab) {
                    0 -> SubtopicNotesSection(
                        accent = accent, isAdminUser = isAdminUser, folderNames = folderNames,
                        selectedFolder = selectedFolder, onSelectFolder = { selectedFolder = it },
                        notes = filteredNotes, bookmarks = bookmarks, sharedDownloadsMap = sharedDownloadsMap,
                        sharedDownloadProgress = sharedDownloadProgress,
                        onNewFolderClick = { showFolderDialog = true },
                        onAddPdfClick = { pdfPicker.launch(arrayOf("application/pdf", "*/*")) },
                        onAddHtmlClick = { pendingIsTestUpload = false; htmlPicker.launch(arrayOf("text/html", "*/*")) },
                        onAddImageClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onRequestMaterialWhatsApp = onRequestMaterialWhatsApp,
                        onOpenNote = onOpenNote, onDownloadShared = onDownloadShared,
                        onToggleBookmarkNote = onToggleBookmarkNote, onEditNote = { editingNote = it },
                        onConfirmDeleteNote = { deleteTarget = PendingDeleteTarget.NoteTarget(it) },
                    )
                    1 -> SubtopicTestsSection(
                        accent = accent, isAdminUser = isAdminUser, htmlTests = htmlTests,
                        mcqs = filteredMcqs, testResults = testResults,
                        sharedDownloadsMap = sharedDownloadsMap, sharedDownloadProgress = sharedDownloadProgress,
                        onUploadHtmlTest = { pendingIsTestUpload = true; htmlPicker.launch(arrayOf("text/html", "*/*")) },
                        onCreateSampleTest = { showCreateSampleTestDialog = true },
                        onRequestMaterialWhatsApp = onRequestMaterialWhatsApp,
                        onRunHtmlTest = onOpenHtmlTest, onDownloadShared = onDownloadShared,
                        onOpenTestResult = onOpenTestResult, onAddMcq = { showAddMcqDialog = true },
                        onStartMcqPractice = onStartMcqPractice,
                        onConfirmDeleteMcq = { deleteTarget = PendingDeleteTarget.McqTarget(it) },
                    )
                    2 -> SubtopicVideosSection(
                        accent = accent, isAdminUser = isAdminUser, videos = filteredVideos,
                        onAddVideo = { showAddVideoDialog = true }, onWatchVideo = onWatchVideo,
                        onConfirmDeleteVideo = { deleteTarget = PendingDeleteTarget.VideoTarget(it) },
                    )
                }
            }
        }
    }

    if (showFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFolderDialog = false }, containerColor = theme.surfaceElevated,
            title = { Text("नया फोल्डर बनाएं", color = theme.onSurface) },
            text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Folder Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(
                    onClick = { if (folderName.isNotBlank()) { onCreateFolder(folderName.trim()); selectedFolder = folderName.trim(); showFolderDialog = false } },
                    enabled = folderName.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") } }
        )
    }

    val currentPendingUri = pendingImportUri
    if (currentPendingUri != null) {
        val suggestedName = remember(pendingPickedFileName) { pendingPickedFileName.substringBeforeLast('.').ifBlank { "" } }
        var customName by remember(pendingPickedFileName) { mutableStateOf(suggestedName) }
        var folderInput by remember { mutableStateOf(if (pendingIsTestUpload) "Mock Tests" else if (selectedFolder != "All") selectedFolder else "NCERT Notes") }
        AlertDialog(
            onDismissRequest = { pendingImportUri = null }, containerColor = theme.surfaceElevated,
            title = { Text(if (pendingIsTestUpload) "HTML टेस्ट का नाम दें" else "नोट का नाम और फोल्डर चुनें", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("चयनित फाइल: $pendingPickedFileName", style = MaterialTheme.typography.labelSmall, color = theme.primary)
                    OutlinedTextField(value = customName, onValueChange = { customName = it }, label = { Text("Custom Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("import_note_name_input"))
                    OutlinedTextField(value = folderInput, onValueChange = { folderInput = it }, label = { Text("Folder Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { onImportFile(customName.trim(), folderInput.trim(), pendingImportType, currentPendingUri, pendingIsTestUpload); pendingImportUri = null },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") } }
        )
    }

    val noteToEdit = editingNote
    if (noteToEdit != null) {
        var editTitle by remember(noteToEdit) { mutableStateOf(noteToEdit.title) }
        var editFolder by remember(noteToEdit) { mutableStateOf(noteToEdit.folderName) }
        AlertDialog(
            onDismissRequest = { editingNote = null }, containerColor = theme.surfaceElevated,
            title = { Text("नाम या फोल्डर बदलें", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Note Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editFolder, onValueChange = { editFolder = it }, label = { Text("Folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (editTitle.isNotBlank()) { onRenameOrMoveNote(noteToEdit.id, editTitle, editFolder); editingNote = null } },
                    enabled = editTitle.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("Cancel") } }
        )
    }

    if (showCreateSampleTestDialog) {
        var testTitle by remember { mutableStateOf("${subtopic?.name ?: "Practice"} Mock Test") }
        AlertDialog(
            onDismissRequest = { showCreateSampleTestDialog = false }, containerColor = theme.surfaceElevated,
            title = { Text("Sample HTML Mock Test", color = theme.onSurface) },
            text = { OutlinedTextField(value = testTitle, onValueChange = { testTitle = it }, label = { Text("Test Title") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(
                    onClick = { if (testTitle.isNotBlank()) { onCreateSampleHtmlTest(testTitle.trim(), "Mock Tests"); showCreateSampleTestDialog = false } },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Create Test") }
            },
            dismissButton = { TextButton(onClick = { showCreateSampleTestDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddVideoDialog) {
        var title by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddVideoDialog = false }, containerColor = theme.surfaceElevated,
            title = { Text("यूट्यूब वीडियो लिंक जोड़ें", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Video Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("video_title_input"))
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("YouTube Link") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("video_url_input"))
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Teacher / Note (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (title.isNotBlank() && url.isNotBlank()) { onAddVideo(title, url, note.ifBlank { null }); showAddVideoDialog = false } },
                    enabled = title.isNotBlank() && url.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddVideoDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddMcqDialog) {
        AddMcqDialog(accent = accent, onDismiss = { showAddMcqDialog = false }, onSave = { q, opts, idx, exp -> onAddMcq(q, opts, idx, exp); showAddMcqDialog = false })
    }

    val pendingDel = deleteTarget
    if (pendingDel != null) {
        val label = when (pendingDel) {
            is PendingDeleteTarget.NoteTarget -> pendingDel.note.title
            is PendingDeleteTarget.VideoTarget -> pendingDel.video.title
            is PendingDeleteTarget.McqTarget -> pendingDel.mcq.question
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, containerColor = theme.surfaceElevated,
            title = { Text("डिलीट की पुष्टि करें", color = theme.onSurface) },
            text = { Text("क्या आप '$label' को हटाना चाहते हैं?", color = theme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        when (pendingDel) {
                            is PendingDeleteTarget.NoteTarget -> onDeleteNote(pendingDel.note)
                            is PendingDeleteTarget.VideoTarget -> onDeleteVideo(pendingDel.video)
                            is PendingDeleteTarget.McqTarget -> onDeleteMcq(pendingDel.mcq)
                        }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.error, contentColor = theme.onPrimary)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddMcqDialog(
    accent: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, Int, String?) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var question by remember { mutableStateOf("") }
    var optA by remember { mutableStateOf("") }
    var optB by remember { mutableStateOf("") }
    var optC by remember { mutableStateOf("") }
    var optD by remember { mutableStateOf("") }
    var correctIdx by remember { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = theme.surfaceElevated,
        title = { Text("प्रैक्टिस प्रश्न जोड़ें (Add MCQ)", color = theme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Option A") }, singleLine = true)
                OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Option B") }, singleLine = true)
                OutlinedTextField(value = optC, onValueChange = { optC = it }, label = { Text("Option C") }, singleLine = true)
                OutlinedTextField(value = optD, onValueChange = { optD = it }, label = { Text("Option D") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Correct:", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                    listOf("A", "B", "C", "D").forEachIndexed { idx, label ->
                        FilterChip(selected = correctIdx == idx, onClick = { correctIdx = idx }, label = { Text(label) })
                    }
                }
                OutlinedTextField(value = explanation, onValueChange = { explanation = it }, label = { Text("Explanation (Optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            val opts = listOf(optA, optB, optC, optD).filter { it.isNotBlank() }
            Button(
                onClick = { if (question.isNotBlank() && opts.size >= 2) onSave(question, opts, correctIdx.coerceIn(0, opts.lastIndex), explanation.ifBlank { null }) },
                enabled = question.isNotBlank() && opts.size >= 2, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
            ) { Text("Save MCQ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
