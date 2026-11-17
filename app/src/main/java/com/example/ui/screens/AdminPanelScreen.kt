package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PublishedWithChanges
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.model.NoteType
import com.example.data.model.StudentSummary
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.WhatsAppRequestRecord
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    isAdminUser: Boolean,
    subjects: List<Subject>,
    allTopics: List<Topic>,
    allSubtopics: List<Subtopic>,
    sharedContent: List<StudyNote>,
    students: List<StudentSummary>,
    requests: List<WhatsAppRequestRecord>,
    uploadProgress: Float?,
    currentWhatsAppDigits: String,
    publishedCatalogLink: String?,
    onPublishCatalogToDrive: () -> Unit,
    onUploadSharedContent: (
        subjectId: String, topicId: String, subtopicId: String, folderName: String,
        title: String, noteType: NoteType, isMockTest: Boolean, fileUri: Uri?, published: Boolean, existingNote: StudyNote?
    ) -> Unit,
    onTogglePublished: (StudyNote) -> Unit,
    onDeleteSharedContent: (StudyNote) -> Unit,
    onPreviewSharedNote: (StudyNote) -> Unit,
    onAddSharedTopic: (subjectId: String, name: String, desc: String?) -> Unit,
    onAddSharedSubtopic: (subjectId: String, topicId: String, name: String) -> Unit,
    onLoadStudentResults: (studentUid: String, onLoaded: (List<TestResult>) -> Unit) -> Unit,
    onSaveWhatsAppDigits: (String) -> Unit,
    onUpdateWhatsAppRequestStatus: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalStudyTheme.current

    if (!isAdminUser) {
        Scaffold(containerColor = theme.background) { inner ->
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Tactile3DCard(accentColor = theme.error, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = theme.error, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Access Denied: Admin Only", style = MaterialTheme.typography.titleMedium, color = theme.error)
                    Text("Only lovely.bunts@gmail.com can access the Admin Panel.", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Back") }
                }
            }
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Admin Control Center", style = MaterialTheme.typography.titleLarge, color = theme.tertiary) }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 980.dp).fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab, containerColor = theme.background, contentColor = theme.tertiary) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Analytics") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Upload & Manage") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Students") })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Requests") })
                }

                when (selectedTab) {
                    0 -> AdminAnalyticsTab(students = students, sharedContent = sharedContent)
                    1 -> AdminUploadCatalogTab(
                        subjects = subjects, allTopics = allTopics, allSubtopics = allSubtopics,
                        sharedContent = sharedContent, uploadProgress = uploadProgress,
                        publishedCatalogLink = publishedCatalogLink,
                        onPublishCatalogToDrive = onPublishCatalogToDrive,
                        onUploadSharedContent = onUploadSharedContent, onTogglePublished = onTogglePublished,
                        onDeleteSharedContent = onDeleteSharedContent, onPreviewSharedNote = onPreviewSharedNote,
                        onAddSharedTopic = onAddSharedTopic, onAddSharedSubtopic = onAddSharedSubtopic
                    )
                    2 -> AdminStudentsTab(students = students, onLoadStudentResults = onLoadStudentResults)
                    3 -> AdminRequestsTab(
                        currentWhatsAppDigits = currentWhatsAppDigits, requests = requests,
                        onSaveWhatsAppDigits = onSaveWhatsAppDigits, onUpdateStatus = onUpdateWhatsAppRequestStatus
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun AdminUploadCatalogTab(
    subjects: List<Subject>, allTopics: List<Topic>, allSubtopics: List<Subtopic>,
    sharedContent: List<StudyNote>, uploadProgress: Float?,
    publishedCatalogLink: String?,
    onPublishCatalogToDrive: () -> Unit,
    onUploadSharedContent: (String, String, String, String, String, NoteType, Boolean, Uri?, Boolean, StudyNote?) -> Unit,
    onTogglePublished: (StudyNote) -> Unit, onDeleteSharedContent: (StudyNote) -> Unit, onPreviewSharedNote: (StudyNote) -> Unit,
    onAddSharedTopic: (String, String, String?) -> Unit, onAddSharedSubtopic: (String, String, String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    val clipboard = LocalClipboardManager.current
    var selectedSubjectId by remember(subjects.size) { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    val topicsForSubj = remember(allTopics, selectedSubjectId) { allTopics.filter { it.subjectId == selectedSubjectId } }
    var selectedTopicId by remember(topicsForSubj) { mutableStateOf(topicsForSubj.firstOrNull()?.id ?: "") }
    val subsForTopic = remember(allSubtopics, selectedTopicId) { allSubtopics.filter { it.topicId == selectedTopicId } }
    var selectedSubId by remember(subsForTopic) { mutableStateOf(subsForTopic.firstOrNull()?.id ?: "") }

    var title by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("Admin Notes") }
    var noteType by remember { mutableStateOf(NoteType.PDF) }
    var isMockTest by remember { mutableStateOf(false) }
    var published by remember { mutableStateOf(true) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var replacingNote by remember { mutableStateOf<StudyNote?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingDeleteNote by remember { mutableStateOf<StudyNote?>(null) }
    var showNewTopicDialog by remember { mutableStateOf(false) }
    var showNewSubtopicDialog by remember { mutableStateOf(false) }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (replacingNote != null) {
                val rep = replacingNote!!
                onUploadSharedContent(rep.subjectId, rep.topicId, rep.subtopicId, rep.folderName, rep.title, rep.resolvedType(), rep.isMockTest, uri, rep.published, rep)
                replacingNote = null
            } else {
                pickedUri = uri
            }
        }
    }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedUri = uri
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                Text("Publish Shared Catalog to Google Drive", style = MaterialTheme.typography.titleMedium, color = theme.primary)
                Text(
                    "अपने Google Drive (LEARN TALENT/Shared Catalog/) में shared_catalog.json पब्लिश करें ताकि छात्र इसे Settings से इम्पोर्ट कर सकें।",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onPublishCatalogToDrive,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                        modifier = Modifier.weight(1f).testTag("admin_publish_drive_catalog_btn")
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Publish to Drive")
                    }
                    if (!publishedCatalogLink.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(publishedCatalogLink)) },
                            modifier = Modifier.testTag("admin_copy_catalog_link_btn")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Link")
                        }
                    }
                }
                if (!publishedCatalogLink.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Published Link: $publishedCatalogLink", style = MaterialTheme.typography.labelSmall, color = theme.success)
                }
            }
        }

        item {
            Tactile3DCard(accentColor = theme.tertiary, modifier = Modifier.fillMaxWidth()) {
                Text("Upload Shared Material (1 Upload → All Students)", style = MaterialTheme.typography.titleMedium, color = theme.tertiary)
                Spacer(Modifier.height(6.dp))
                Text("Subject:", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(subjects, key = { it.id }) { s ->
                        FilterChip(selected = selectedSubjectId == s.id, onClick = { selectedSubjectId = s.id }, label = { Text(s.nameEn) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Topic (${topicsForSubj.size}):", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                    TextButton(onClick = { showNewTopicDialog = true }) { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Text("New Shared Topic") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(topicsForSubj, key = { it.id }) { t ->
                        FilterChip(selected = selectedTopicId == t.id, onClick = { selectedTopicId = t.id }, label = { Text(t.name) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Subtopic (${subsForTopic.size}):", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                    TextButton(onClick = { showNewSubtopicDialog = true }, enabled = selectedTopicId.isNotBlank()) { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Text("New Subtopic") }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(subsForTopic, key = { it.id }) { st ->
                        FilterChip(selected = selectedSubId == st.id, onClick = { selectedSubId = st.id }, label = { Text(st.name) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Content Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("admin_upload_title_input"))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Folder Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(NoteType.PDF, NoteType.HTML, NoteType.IMAGE).forEach { nt ->
                        FilterChip(selected = noteType == nt, onClick = { noteType = nt; if (nt == NoteType.HTML) isMockTest = true }, label = { Text(nt.name) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Published (visible to students)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = published, onCheckedChange = { published = it })
                }
                if (noteType == NoteType.HTML) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Treat as Mock Test", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = isMockTest, onCheckedChange = { isMockTest = it })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        when (noteType) {
                            NoteType.IMAGE -> imgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            NoteType.PDF -> docPicker.launch(arrayOf("application/pdf", "*/*"))
                            else -> docPicker.launch(arrayOf("text/html", "*/*"))
                        }
                    }, modifier = Modifier.weight(1f).testTag("admin_pick_file_btn")) {
                        Text(if (pickedUri == null) "Select File" else "File Selected ✓")
                    }
                    Button(
                        onClick = {
                            onUploadSharedContent(selectedSubjectId, selectedTopicId, selectedSubId, folderName, title, noteType, isMockTest, pickedUri, published, null)
                            title = ""; pickedUri = null
                        },
                        enabled = title.isNotBlank() && pickedUri != null && selectedSubId.isNotBlank() && uploadProgress == null,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.tertiary, contentColor = theme.onPrimary),
                        modifier = Modifier.weight(1f).testTag("admin_publish_btn")
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("Upload")
                    }
                }
                if (uploadProgress != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { uploadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = theme.success)
                    Text("Uploading ${(uploadProgress * 100).toInt()}%...", style = MaterialTheme.typography.labelSmall, color = theme.success)
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Search shared uploads...") }, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
            )
        }

        val q = searchQuery.trim().lowercase()
        val list = sharedContent.filter { q.isEmpty() || it.title.lowercase().contains(q) }
        items(list, key = { it.id }) { item ->
            Tactile3DCard(accentColor = if (item.published) theme.success else theme.secondary, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Text("${item.title} (v${item.version})", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tactile3DBadge(text = item.noteType, color = theme.primary)
                    Tactile3DBadge(text = if (item.published) "Published" else "Draft", color = if (item.published) theme.success else theme.secondary)
                    Tactile3DBadge(text = "${item.downloadsCount} DLs", color = theme.tertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onPreviewSharedNote(item) }) { Icon(Icons.Rounded.Visibility, contentDescription = "Preview") }
                    IconButton(onClick = { onTogglePublished(item) }) { Icon(Icons.Rounded.PublishedWithChanges, contentDescription = "Toggle Publish") }
                    IconButton(onClick = {
                        replacingNote = item
                        docPicker.launch(arrayOf("*/*"))
                    }) { Icon(Icons.Rounded.Refresh, contentDescription = "Replace File") }
                    IconButton(onClick = { pendingDeleteNote = item }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = theme.error) }
                }
            }
        }
    }

    val del = pendingDeleteNote
    if (del != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteNote = null }, containerColor = theme.surfaceElevated,
            title = { Text("Shared Content डिलीट करें?", color = theme.onSurface) },
            text = { Text("'${del.title}' सभी छात्रों के लिए हट जाएगा। जारी रखें?", color = theme.onSurfaceVariant) },
            confirmButton = { Button(onClick = { onDeleteSharedContent(del); pendingDeleteNote = null }, colors = ButtonDefaults.buttonColors(containerColor = theme.error, contentColor = theme.onPrimary)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDeleteNote = null }) { Text("Cancel") } }
        )
    }

    if (showNewTopicDialog) {
        var tName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTopicDialog = false }, containerColor = theme.surfaceElevated,
            title = { Text("New Shared Topic", color = theme.onSurface) },
            text = { OutlinedTextField(value = tName, onValueChange = { tName = it }, label = { Text("Topic Name") }, singleLine = true) },
            confirmButton = { Button(onClick = { if (tName.isNotBlank()) { onAddSharedTopic(selectedSubjectId, tName, null); showNewTopicDialog = false } }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showNewTopicDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNewSubtopicDialog) {
        var stName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewSubtopicDialog = false }, containerColor = theme.surfaceElevated,
            title = { Text("New Shared Subtopic", color = theme.onSurface) },
            text = { OutlinedTextField(value = stName, onValueChange = { stName = it }, label = { Text("Subtopic Name") }, singleLine = true) },
            confirmButton = { Button(onClick = { if (stName.isNotBlank()) { onAddSharedSubtopic(selectedSubjectId, selectedTopicId, stName); showNewSubtopicDialog = false } }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showNewSubtopicDialog = false }) { Text("Cancel") } }
        )
    }
}
