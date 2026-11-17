package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.VideoLink
import com.example.ui.MainViewModel
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

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
    lastReturnedScore: TestResult?,
    onDismissScoreBanner: () -> Unit,
    onBack: () -> Unit,
    onMarkStudiedToday: () -> Unit,
    onRequestMaterialWhatsApp: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onImportFile: (customTitle: String, folderName: String, noteType: NoteType, uri: Uri) -> Unit,
    onCreateSampleHtmlTest: (title: String, folderName: String) -> Unit,
    onRenameOrMoveNote: (noteId: String, title: String, folderName: String) -> Unit,
    onOpenNote: (StudyNote) -> Unit,
    onOpenHtmlTest: (StudyNote) -> Unit,
    onDeleteNote: (StudyNote) -> Unit,
    onAddVideo: (title: String, url: String, note: String?) -> Unit,
    onWatchVideo: (VideoLink) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onAddMcq: (question: String, options: List<String>, correctIdx: Int, explanation: String?) -> Unit,
    onDeleteMcq: (String) -> Unit,
    onStartMcqPractice: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current
    val accent = subject?.let { SubjectVisuals.colorFor(it.colorHex, theme.primary) } ?: theme.primary

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf("All") }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPickedFileName by remember { mutableStateOf("") }
    var pendingImportType by remember { mutableStateOf(NoteType.PDF) }
    var pendingIsTestUpload by remember { mutableStateOf(false) }

    var showFolderDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<StudyNote?>(null) }
    var showAddVideoDialog by remember { mutableStateOf(false) }
    var showAddMcqDialog by remember { mutableStateOf(false) }
    var showCreateSampleTestDialog by remember { mutableStateOf(false) }

    fun readFileName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "selected_file"
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingPickedFileName = readFileName(uri)
            pendingImportType = NoteType.PDF
            pendingIsTestUpload = false
            pendingImportUri = uri
        }
    }

    val htmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingPickedFileName = readFileName(uri)
            pendingImportType = NoteType.HTML
            pendingImportUri = uri
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
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

    val filteredNotes = remember(notes, selectedFolder) {
        notes.filter {
            it.resolvedType() != NoteType.FOLDER &&
                (selectedFolder == "All" || it.folderName == selectedFolder)
        }
    }

    val htmlTests = remember(notes) {
        notes.filter { it.resolvedType() == NoteType.HTML }
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("subtopic_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = subtopic?.name ?: "Study Workspace",
                            style = MaterialTheme.typography.titleLarge,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${subject?.nameEn ?: ""} › ${topic?.name ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onMarkStudiedToday,
                        modifier = Modifier.testTag("mark_studied_today_btn")
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = theme.success,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("आज पढ़ा", color = theme.success)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxSize()
            ) {
                if (lastReturnedScore != null && lastReturnedScore.subtopicId == subtopic?.id) {
                    Tactile3DCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        accentColor = theme.success,
                        testTag = "returned_score_card"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                TactileIconOrb(
                                    icon = Icons.Rounded.EmojiEvents,
                                    tint = theme.success,
                                    size = 44.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Test Submitted • ${lastReturnedScore.testTitle}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = theme.success
                                    )
                                    Text(
                                        text = "प्राप्त अंक: ${MainViewModel.formatNum(lastReturnedScore.scoreObtained)} / ${MainViewModel.formatNum(lastReturnedScore.totalMarks)} (${MainViewModel.formatNum(lastReturnedScore.percentage)}%)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            TextButton(onClick = onDismissScoreBanner) {
                                Text("OK", color = theme.primary)
                            }
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = theme.background,
                    contentColor = accent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = accent
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Notes (${filteredNotes.size})") },
                        modifier = Modifier.testTag("tab_notes")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Tests & MCQ") },
                        modifier = Modifier.testTag("tab_tests")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Videos (${videos.size})") },
                        modifier = Modifier.testTag("tab_videos")
                    )
                }

                when (selectedTab) {
                    0 -> NotesSection(
                        accent = accent,
                        folderNames = folderNames,
                        selectedFolder = selectedFolder,
                        onSelectFolder = { selectedFolder = it },
                        notes = filteredNotes,
                        onNewFolderClick = { showFolderDialog = true },
                        onAddPdfClick = { pdfPicker.launch(arrayOf("*/*")) },
                        onAddHtmlClick = {
                            pendingIsTestUpload = false
                            htmlPicker.launch(arrayOf("*/*"))
                        },
                        onAddImageClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onRequestMaterialWhatsApp = onRequestMaterialWhatsApp,
                        onOpenNote = onOpenNote,
                        onEditNote = { editingNote = it },
                        onDeleteNote = onDeleteNote,
                    )

                    1 -> TestsAndMcqSection(
                        accent = accent,
                        htmlTests = htmlTests,
                        mcqs = mcqs,
                        testResults = testResults,
                        onUploadHtmlTest = {
                            pendingIsTestUpload = true
                            htmlPicker.launch(arrayOf("*/*"))
                        },
                        onCreateSampleTest = { showCreateSampleTestDialog = true },
                        onRequestMaterialWhatsApp = onRequestMaterialWhatsApp,
                        onRunHtmlTest = onOpenHtmlTest,
                        onAddMcq = { showAddMcqDialog = true },
                        onStartMcqPractice = onStartMcqPractice,
                        onDeleteMcq = onDeleteMcq,
                    )

                    2 -> VideosSection(
                        accent = accent,
                        videos = videos,
                        onAddVideo = { showAddVideoDialog = true },
                        onOpenVideo = { video ->
                            onWatchVideo(video)
                            val rawUrl = video.youtubeUrl.trim()
                            val safeUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
                            runCatching { context.startActivity(intent) }
                        },
                        onDeleteVideo = onDeleteVideo,
                    )
                }
            }
        }
    }

    if (showFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("नया फोल्डर बनाएं (New Folder)", color = theme.onSurface) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name (e.g. NCERT, Short Notes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            onCreateFolder(folderName.trim())
                            selectedFolder = folderName.trim()
                            showFolderDialog = false
                        }
                    },
                    enabled = folderName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") } }
        )
    }

    val currentPendingUri = pendingImportUri
    if (currentPendingUri != null) {
        val suggestedName = remember(pendingPickedFileName) {
            pendingPickedFileName.substringBeforeLast('.').ifBlank { "" }
        }
        var customName by remember(pendingPickedFileName) { mutableStateOf(suggestedName) }
        var folderInput by remember {
            mutableStateOf(
                when {
                    pendingIsTestUpload -> "Mock Tests"
                    selectedFolder != "All" -> selectedFolder
                    else -> "NCERT Notes"
                }
            )
        }
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            containerColor = theme.surfaceElevated,
            title = {
                Text(
                    if (pendingIsTestUpload) "HTML टेस्ट का नाम दें" else "नोट का नाम और फोल्डर चुनें",
                    color = theme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "चयनित फाइल: $pendingPickedFileName",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.primary
                    )
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Custom Name (नाम लिखें)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("import_note_name_input")
                    )
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        label = { Text("Folder Name (फोल्डर)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportFile(customName.trim(), folderInput.trim(), pendingImportType, currentPendingUri)
                        pendingImportUri = null
                    },
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
            onDismissRequest = { editingNote = null },
            containerColor = theme.surfaceElevated,
            title = { Text("नाम या फोल्डर बदलें", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Note Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editFolder,
                        onValueChange = { editFolder = it },
                        label = { Text("Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editTitle.isNotBlank()) {
                            onRenameOrMoveNote(noteToEdit.id, editTitle, editFolder)
                            editingNote = null
                        }
                    },
                    enabled = editTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("Cancel") } }
        )
    }

    if (showCreateSampleTestDialog) {
        var testTitle by remember { mutableStateOf("${subtopic?.name ?: "Practice"} Mock Test") }
        AlertDialog(
            onDismissRequest = { showCreateSampleTestDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("Sample HTML Mock Test", color = theme.onSurface) },
            text = {
                OutlinedTextField(
                    value = testTitle,
                    onValueChange = { testTitle = it },
                    label = { Text("Test Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (testTitle.isNotBlank()) {
                            onCreateSampleHtmlTest(testTitle.trim(), "Mock Tests")
                            showCreateSampleTestDialog = false
                        }
                    },
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
            onDismissRequest = { showAddVideoDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("यूट्यूब वीडियो लिंक जोड़ें", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Video Title (e.g. One-Shot Revision)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("video_title_input")
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("YouTube Link (https://youtu.be/...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("video_url_input")
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Teacher / Note (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && url.isNotBlank()) {
                            onAddVideo(title, url, note.ifBlank { null })
                            showAddVideoDialog = false
                        }
                    },
                    enabled = title.isNotBlank() && url.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddVideoDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddMcqDialog) {
        AddMcqDialog(
            accent = accent,
            onDismiss = { showAddMcqDialog = false },
            onSave = { q, opts, idx, exp ->
                onAddMcq(q, opts, idx, exp)
                showAddMcqDialog = false
            }
        )
    }
}

@Composable
private fun NotesSection(
    accent: androidx.compose.ui.graphics.Color,
    folderNames: List<String>,
    selectedFolder: String,
    onSelectFolder: (String) -> Unit,
    notes: List<StudyNote>,
    onNewFolderClick: () -> Unit,
    onAddPdfClick: () -> Unit,
    onAddHtmlClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onRequestMaterialWhatsApp: () -> Unit,
    onOpenNote: (StudyNote) -> Unit,
    onEditNote: (StudyNote) -> Unit,
    onDeleteNote: (StudyNote) -> Unit,
) {
    val theme = LocalStudyTheme.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(
                        onClick = onAddPdfClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_pdf_note_btn")
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = theme.secondary)
                        Spacer(Modifier.width(6.dp))
                        Text("PDF", color = theme.onSurface)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onAddHtmlClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_html_note_btn")
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(6.dp))
                        Text("HTML", color = theme.onSurface)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onAddImageClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_image_note_btn")
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = theme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Text("Image", color = theme.onSurface)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onNewFolderClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("new_folder_btn")
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = theme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text("Folder", color = theme.onSurface)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onRequestMaterialWhatsApp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("notes_whatsapp_request_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = theme.success)
                        Spacer(Modifier.width(6.dp))
                        Text("मांगें (WhatsApp)", color = theme.success)
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folderNames) { folder ->
                    FilterChip(
                        selected = folder == selectedFolder,
                        onClick = { onSelectFolder(folder) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(folder) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.22f),
                            selectedLabelColor = accent
                        )
                    )
                }
            }
        }

        if (notes.isEmpty()) {
            item {
                Tactile3DCard(accentColor = accent, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_notes),
                            contentDescription = "Empty notes",
                            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("इस फोल्डर में कोई नोट नहीं है", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                        Text(
                            "ऊपर से PDF, HTML या Image चुनकर अपना नाम दें",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(notes, key = { it.id }) { note ->
                val icon = when (note.resolvedType()) {
                    NoteType.PDF -> Icons.Rounded.PictureAsPdf
                    NoteType.HTML -> Icons.Rounded.Code
                    NoteType.IMAGE -> Icons.Rounded.Image
                    NoteType.FOLDER -> Icons.Rounded.Folder
                }
                Tactile3DCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    onClick = { onOpenNote(note) },
                    testTag = "note_card_${note.id}"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileIconOrb(icon = icon, tint = accent, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tactile3DBadge(text = note.noteType, color = accent)
                                if (note.folderName.isNotBlank()) {
                                    Tactile3DBadge(text = note.folderName, color = theme.secondary)
                                }
                            }
                        }
                        IconButton(onClick = { onEditNote(note) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Rename or Move", tint = theme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteNote(note) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = theme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestsAndMcqSection(
    accent: androidx.compose.ui.graphics.Color,
    htmlTests: List<StudyNote>,
    mcqs: List<McqQuestion>,
    testResults: List<TestResult>,
    onUploadHtmlTest: () -> Unit,
    onCreateSampleTest: () -> Unit,
    onRequestMaterialWhatsApp: () -> Unit,
    onRunHtmlTest: (StudyNote) -> Unit,
    onAddMcq: () -> Unit,
    onStartMcqPractice: () -> Unit,
    onDeleteMcq: (String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                Text("HTML Mock Tests (एचटीएमएल मॉक टेस्ट)", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "HTML टेस्ट फाइल अपलोड करें या खोलें। फाइल में Submit दबाते ही आपके अंक और नंबर यहां सेव हो जाएंगे।",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onUploadHtmlTest,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondary, contentColor = theme.onSecondary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("upload_html_test_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Upload HTML")
                    }
                    OutlinedButton(
                        onClick = onCreateSampleTest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("create_sample_html_test_btn")
                    ) {
                        Text("Sample Test", color = theme.onSurface)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestMaterialWhatsApp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("tests_whatsapp_request_btn")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = theme.success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("एडमिन से टेस्ट / MCQ WhatsApp पर मांगें", color = theme.success)
                }
            }
        }

        items(htmlTests, key = { "html_${it.id}" }) { testNote ->
            val latestResult = testResults.firstOrNull { it.noteId == testNote.id }
            Tactile3DCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = theme.primary,
                onClick = { onRunHtmlTest(testNote) },
                testTag = "html_test_card_${testNote.id}"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(testNote.title, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        if (latestResult != null) {
                            Tactile3DBadge(
                                text = "Last Score: ${MainViewModel.formatNum(latestResult.scoreObtained)}/${MainViewModel.formatNum(latestResult.totalMarks)} (${MainViewModel.formatNum(latestResult.percentage)}%)",
                                color = theme.success
                            )
                        } else {
                            Text("Tap to start HTML mock test", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onRunHtmlTest(testNote) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Start")
                    }
                }
            }
        }

        if (testResults.isNotEmpty()) {
            item {
                Text("Recent Scores (हाल के अंक)", style = MaterialTheme.typography.titleSmall, color = theme.primary)
            }
            items(testResults.take(5), key = { "res_${it.id}" }) { res ->
                Tactile3DCard(accentColor = theme.success, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(res.testTitle, style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                            Text(res.takenDate, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                        }
                        Tactile3DBadge(
                            text = "${MainViewModel.formatNum(res.scoreObtained)} / ${MainViewModel.formatNum(res.totalMarks)}",
                            color = theme.success
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Tactile3DCard(accentColor = accent, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Practice MCQs (${mcqs.size})", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                        Text("खुद के प्रश्न जोड़ें और प्रैक्टिस करें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onAddMcq, modifier = Modifier.testTag("add_mcq_btn")) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Add", color = theme.onSurface)
                        }
                        if (mcqs.isNotEmpty()) {
                            Button(
                                onClick = onStartMcqPractice,
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary),
                                modifier = Modifier.testTag("start_mcq_btn")
                            ) {
                                Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Practice")
                            }
                        }
                    }
                }
            }
        }

        items(mcqs, key = { "mcq_${it.id}" }) { mcq ->
            Tactile3DCard(accentColor = theme.tertiary, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mcq.question, style = MaterialTheme.typography.bodyMedium, color = theme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        val ans = mcq.options.getOrNull(mcq.correctIndex.toInt()) ?: ""
                        Text("Ans: $ans", style = MaterialTheme.typography.labelMedium, color = theme.success)
                    }
                    IconButton(onClick = { onDeleteMcq(mcq.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete MCQ", tint = theme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideosSection(
    accent: androidx.compose.ui.graphics.Color,
    videos: List<VideoLink>,
    onAddVideo: () -> Unit,
    onOpenVideo: (VideoLink) -> Unit,
    onDeleteVideo: (String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onAddVideo,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_youtube_link_btn")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_youtube_link))
            }
        }

        if (videos.isEmpty()) {
            item {
                Tactile3DCard(accentColor = accent, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TactileIconOrb(icon = Icons.Rounded.VideoLibrary, tint = theme.secondary, size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("कोई YouTube वीडियो सेव नहीं है", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                            Text(
                                "जिस वीडियो से आपने पढ़ा है, उसका लिंक यहां सेव करें और एक टैप में देखें।",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(videos, key = { it.id }) { video ->
                Tactile3DCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = theme.secondary,
                    onClick = { onOpenVideo(video) },
                    testTag = "video_card_${video.id}"
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TactileIconOrb(icon = Icons.Rounded.PlayCircle, tint = theme.secondary, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(video.title, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                            Text(
                                video.youtubeUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!video.channelOrNote.isNullOrBlank()) {
                                Text(video.channelOrNote, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onDeleteVideo(video.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete video", tint = theme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
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
        onDismissRequest = onDismiss,
        containerColor = theme.surfaceElevated,
        title = { Text("प्रैक्टिस प्रश्न जोड़ें (Add MCQ)", color = theme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Option A") }, singleLine = true)
                OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Option B") }, singleLine = true)
                OutlinedTextField(value = optC, onValueChange = { optC = it }, label = { Text("Option C") }, singleLine = true)
                OutlinedTextField(value = optD, onValueChange = { optD = it }, label = { Text("Option D") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Correct:", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                    listOf("A", "B", "C", "D").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = correctIdx == idx,
                            onClick = { correctIdx = idx },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Explanation (Optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            val opts = listOf(optA, optB, optC, optD).filter { it.isNotBlank() }
            Button(
                onClick = {
                    if (question.isNotBlank() && opts.size >= 2) {
                        onSave(question, opts, correctIdx.coerceIn(0, opts.lastIndex), explanation.ifBlank { null })
                    }
                },
                enabled = question.isNotBlank() && opts.size >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
            ) { Text("Save MCQ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
