package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.model.FullLengthExam
import com.example.data.model.FullLengthExamDefaults
import com.example.data.model.StudyNote
import com.example.data.model.TestResult
import com.example.data.model.UiState
import com.example.ui.MainViewModel
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullLengthExamsScreen(
    examsState: UiState<List<FullLengthExam>>,
    notes: List<StudyNote>,
    testResults: List<TestResult>,
    isAdminUser: Boolean,
    onBack: () -> Unit,
    onExamClick: (FullLengthExam) -> Unit,
    onCreateExam: (name: String, subtitle: String?) -> Unit,
    onDeleteExam: (FullLengthExam) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var showCreateExamDialog by remember { mutableStateOf(false) }
    var pendingDeleteExam by remember { mutableStateOf<FullLengthExam?>(null) }

    val fullLengthNotes = remember(notes) {
        notes.filter { it.subjectId == FullLengthExamDefaults.SUBJECT_ID }
    }
    val fullLengthResults = remember(testResults) {
        testResults.filter { it.subjectId == FullLengthExamDefaults.SUBJECT_ID }
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("full_length_exams_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Full Length Tests", style = MaterialTheme.typography.titleLarge, color = theme.primary, fontWeight = FontWeight.Bold)
                        Text("एग्जाम चुनें या नया एग्जाम बनाएं • HTML मॉक टेस्ट", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateExamDialog = true },
                containerColor = theme.primary,
                contentColor = theme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("create_exam_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create Exam")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 960.dp).fillMaxSize().padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search exam (e.g. MPPSC, SSC, UPSC, Patwari...)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("full_length_exam_search_input")
                )
                Spacer(Modifier.height(10.dp))

                when (examsState) {
                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.primary)
                    }
                    is UiState.Error -> Tactile3DCard(accentColor = theme.error, modifier = Modifier.fillMaxWidth()) {
                        Text(examsState.message, color = theme.error)
                    }
                    is UiState.Success -> {
                        val filtered = remember(examsState.data, searchQuery) {
                            val q = searchQuery.trim().lowercase()
                            if (q.isEmpty()) examsState.data
                            else examsState.data.filter {
                                it.name.lowercase().contains(q) || (it.subtitle?.lowercase()?.contains(q) == true)
                            }
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 92.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            TactileIconOrb(icon = Icons.AutoMirrored.Rounded.Assignment, tint = theme.primary, size = 44.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text("एग्जाम के अनुसार फुल लेंथ टेस्ट", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                                Text(
                                                    "${examsState.data.size} Exams • ${fullLengthNotes.size} HTML Tests • ${fullLengthResults.size} Attempts",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = theme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = { showCreateExamDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("create_exam_btn")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("नया एग्जाम नाम जोड़ें (Create Custom Exam)")
                                    }
                                }
                            }

                            items(filtered, key = { it.id }) { exam ->
                                val examTests = fullLengthNotes.filter { it.topicId == exam.id }
                                val latestResult = fullLengthResults.firstOrNull { it.topicId == exam.id }
                                val isBuiltIn = FullLengthExamDefaults.builtInExams.any { it.id == exam.id }
                                val canDelete = !isBuiltIn && (!exam.isShared || isAdminUser)
                                val accent = if (exam.isShared) theme.tertiary else if (isBuiltIn) theme.primary else theme.secondary

                                Tactile3DCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = accent,
                                    onClick = { onExamClick(exam) },
                                    testTag = "exam_card_${exam.id}"
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            TactileIconOrb(icon = Icons.Rounded.School, tint = accent, size = 44.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = exam.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = theme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!exam.subtitle.isNullOrBlank()) {
                                                    Text(
                                                        text = exam.subtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = theme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Tactile3DBadge(text = "${examTests.size} HTML Tests", color = accent)
                                                    if (exam.isShared) {
                                                        Tactile3DBadge(text = "ADMIN", color = theme.tertiary)
                                                    } else if (!isBuiltIn) {
                                                        Tactile3DBadge(text = "CUSTOM", color = theme.secondary)
                                                    }
                                                    if (latestResult != null) {
                                                        Tactile3DBadge(
                                                            text = "Last: ${latestResult.percentage.toInt()}%",
                                                            color = theme.success
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (canDelete) {
                                            IconButton(
                                                onClick = { pendingDeleteExam = exam },
                                                modifier = Modifier.testTag("delete_exam_${exam.id}")
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Exam", tint = theme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateExamDialog) {
        var examName by remember { mutableStateOf("") }
        var examSubtitle by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateExamDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("नया एग्जाम बनाएं (Create Exam)", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = examName,
                        onValueChange = { examName = it },
                        label = { Text("Exam Name (e.g. MPPSC Mains, CTET, GATE)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_exam_name_input")
                    )
                    OutlinedTextField(
                        value = examSubtitle,
                        onValueChange = { examSubtitle = it },
                        label = { Text("Description / Paper Name (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (examName.isNotBlank()) {
                            onCreateExam(examName.trim(), examSubtitle.trim().ifBlank { null })
                            showCreateExamDialog = false
                        }
                    },
                    enabled = examName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    modifier = Modifier.testTag("confirm_create_exam_btn")
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateExamDialog = false }) { Text("Cancel") }
            }
        )
    }

    val del = pendingDeleteExam
    if (del != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteExam = null },
            containerColor = theme.surfaceElevated,
            title = { Text("एग्जाम डिलीट करें?", color = theme.onSurface) },
            text = { Text("'${del.name}' लिस्ट से हटा दिया जाएगा। जारी रखें?", color = theme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteExam(del)
                        pendingDeleteExam = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.error, contentColor = theme.onPrimary)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteExam = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullLengthExamTestsScreen(
    exam: FullLengthExam?,
    examId: String,
    tests: List<StudyNote>,
    testResults: List<TestResult>,
    isAdminUser: Boolean,
    onBack: () -> Unit,
    onUploadHtmlTest: (title: String, uri: Uri) -> Unit,
    onCreateSampleTest: (title: String) -> Unit,
    onStartTest: (StudyNote) -> Unit,
    onOpenResult: (resultId: String) -> Unit,
    onDeleteTest: (StudyNote) -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current
    var pendingHtmlUri by remember { mutableStateOf<Uri?>(null) }
    var pendingHtmlTitle by remember { mutableStateOf("") }
    var showSampleDialog by remember { mutableStateOf(false) }
    var pendingDeleteNote by remember { mutableStateOf<StudyNote?>(null) }

    val htmlFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val fileName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) c.getString(idx) else null
                    } else null
                }
            }.getOrNull() ?: "Full_Length_Test.html"
            pendingHtmlTitle = fileName.substringBeforeLast(".")
            pendingHtmlUri = uri
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("full_length_tests_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(exam?.name ?: "Full Length Tests", style = MaterialTheme.typography.titleLarge, color = theme.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(exam?.subtitle ?: "HTML मॉक टेस्ट अपलोड करें और तुरंत टेस्ट दें", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TactileIconOrb(icon = Icons.Rounded.UploadFile, tint = theme.secondary, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("नया HTML फुल लेंथ टेस्ट अपलोड करें", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                Text("अपने फोन या कंप्यूटर से बनी .html / .htm टेस्ट फाइल चुनें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { htmlFilePicker.launch(arrayOf("text/html", "application/xhtml+xml", "*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("upload_full_length_html_btn")
                            ) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Upload .html")
                            }
                            OutlinedButton(
                                onClick = { showSampleDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).testTag("sample_full_length_html_btn")
                            ) {
                                Icon(Icons.Rounded.Quiz, contentDescription = null, tint = theme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sample Test", color = theme.onSurface)
                            }
                        }
                    }
                }

                if (tests.isEmpty()) {
                    item {
                        Tactile3DCard(accentColor = theme.outline, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Quiz, contentDescription = null, tint = theme.primary, modifier = Modifier.size(36.dp))
                                Text("इस एग्जाम में अभी कोई HTML टेस्ट नहीं जुड़ा है", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                Text(
                                    "ऊपर 'Upload .html' दबाकर अपना फुल लेंथ टेस्ट अपलोड करें या 'Sample Test' से तुरंत प्रयास करें।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(tests, key = { it.id }) { note ->
                        val latestRes = testResults.firstOrNull { it.noteId == note.id }
                        val canDelete = !note.isShared || isAdminUser
                        val accent = if (note.isShared) theme.tertiary else theme.primary

                        Tactile3DCard(
                            accentColor = accent,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "full_length_test_card_${note.id}"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.title, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Tactile3DBadge(text = "HTML CBT", color = accent)
                                        if (note.isShared) {
                                            Tactile3DBadge(text = "ADMIN TEST", color = theme.tertiary)
                                        }
                                        if (latestRes != null) {
                                            Tactile3DBadge(
                                                text = "${MainViewModel.formatNum(latestRes.scoreObtained)}/${MainViewModel.formatNum(latestRes.totalMarks)} (${latestRes.percentage.toInt()}%)",
                                                color = theme.success
                                            )
                                        }
                                    }
                                    if (!note.localFileName.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(note.localFileName, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (canDelete) {
                                    IconButton(
                                        onClick = { pendingDeleteNote = note },
                                        modifier = Modifier.testTag("delete_full_length_test_${note.id}")
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete Test", tint = theme.error)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { onStartTest(note) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("start_full_length_test_${note.id}")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("टेस्ट शुरू करें")
                                }
                                if (latestRes != null) {
                                    OutlinedButton(
                                        onClick = { onOpenResult(latestRes.id) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("result_full_length_test_${note.id}")
                                    ) {
                                        Icon(Icons.Rounded.Assessment, contentDescription = null, tint = theme.success, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Result", color = theme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pickedUri = pendingHtmlUri
    if (pickedUri != null) {
        AlertDialog(
            onDismissRequest = { pendingHtmlUri = null },
            containerColor = theme.surfaceElevated,
            title = { Text("HTML टेस्ट का नाम लिखें", color = theme.onSurface) },
            text = {
                OutlinedTextField(
                    value = pendingHtmlTitle,
                    onValueChange = { pendingHtmlTitle = it },
                    label = { Text("Test Title (e.g. MPPSC FLT-01)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("upload_html_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUploadHtmlTest(pendingHtmlTitle.trim().ifBlank { "Full Length Test" }, pickedUri)
                        pendingHtmlUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                    modifier = Modifier.testTag("confirm_upload_html_btn")
                ) { Text("Save & Upload") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHtmlUri = null }) { Text("Cancel") }
            }
        )
    }

    if (showSampleDialog) {
        var sampleTitle by remember { mutableStateOf("${exam?.name ?: "Exam"} Full Length Mock Test #1") }
        AlertDialog(
            onDismissRequest = { showSampleDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("Sample Full Length Test बनाएं", color = theme.onSurface) },
            text = {
                OutlinedTextField(
                    value = sampleTitle,
                    onValueChange = { sampleTitle = it },
                    label = { Text("Test Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateSampleTest(sampleTitle.trim().ifBlank { "Full Length Sample Test" })
                        showSampleDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.secondary, contentColor = theme.onSecondary)
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showSampleDialog = false }) { Text("Cancel") } }
        )
    }

    val delNote = pendingDeleteNote
    if (delNote != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteNote = null },
            containerColor = theme.surfaceElevated,
            title = { Text("टेस्ट डिलीट करें?", color = theme.onSurface) },
            text = { Text("'${delNote.title}' हटा दिया जाएगा। जारी रखें?", color = theme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTest(delNote)
                        pendingDeleteNote = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.error, contentColor = theme.onPrimary)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteNote = null }) { Text("Cancel") } }
        )
    }
}
