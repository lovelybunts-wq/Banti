package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.PersonalNote
import com.example.data.model.PersonalNoteBlock
import com.example.data.model.PersonalNoteBlockType
import com.example.data.model.Subject
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.LocalStudyTheme
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalNotesScreen(
    notes: List<PersonalNote>,
    subjects: List<Subject>,
    initialSubjectId: String = "",
    initialNoteId: String = "",
    userFilesDir: File,
    onSaveNote: (PersonalNote) -> Unit,
    onDeleteNote: (String) -> Unit,
    onAttachImage: (noteId: String, uri: Uri, onCopied: (String) -> Unit) -> Unit,
    onExportPdf: (PersonalNote, List<PersonalNoteBlock>, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    val jsonCodec = remember { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    var searchQuery by remember { mutableStateOf("") }
    var filterSubjectId by remember(initialSubjectId) { mutableStateOf(initialSubjectId) }
    var editingNote by remember(initialNoteId, notes.size) {
        mutableStateOf(
            if (initialNoteId.isNotBlank()) notes.firstOrNull { it.id == initialNoteId } else null
        )
    }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val activeEditorNote = editingNote
    if (activeEditorNote != null) {
        PersonalNoteEditorView(
            note = activeEditorNote,
            subjects = subjects,
            jsonCodec = jsonCodec,
            userFilesDir = userFilesDir,
            onAttachImage = onAttachImage,
            onSave = { updated ->
                onSaveNote(updated)
                editingNote = null
            },
            onExportPdf = onExportPdf,
            onCancel = { editingNote = null }
        )
        return
    }

    val filtered = remember(notes, filterSubjectId, searchQuery) {
        val q = searchQuery.trim().lowercase()
        notes.filter {
            (filterSubjectId.isBlank() || it.subjectId == filterSubjectId) &&
                (q.isEmpty() || it.title.lowercase().contains(q) || it.topicName.lowercase().contains(q) || it.contentSummary.lowercase().contains(q))
        }
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("personal_notes_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Personal Notes Maker", style = MaterialTheme.typography.titleLarge, color = theme.primary) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val defSubj = subjects.firstOrNull { it.id == filterSubjectId } ?: subjects.firstOrNull()
                    editingNote = PersonalNote(
                        id = "pnote_${UUID.randomUUID().toString().take(8)}",
                        subjectId = defSubj?.id ?: "",
                        subjectName = defSubj?.nameEn ?: "General",
                        blocksJson = jsonCodec.encodeToString(listOf(PersonalNoteBlock(UUID.randomUUID().toString(), PersonalNoteBlockType.HEADING.name, "")))
                    )
                },
                containerColor = theme.primary,
                contentColor = theme.onPrimary,
                modifier = Modifier.testTag("add_personal_note_fab")
            ) { Icon(Icons.Rounded.Add, contentDescription = "New Note") }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 920.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search personal notes...") }, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filterSubjectId.isBlank(), onClick = { filterSubjectId = "" }, label = { Text("All") }) }
                    items(subjects, key = { it.id }) { s ->
                        FilterChip(selected = filterSubjectId == s.id, onClick = { filterSubjectId = s.id }, label = { Text(s.nameEn) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                        Text("अभी कोई पर्सनल नोट नहीं है। '+' दबाकर नया स्ट्रक्चर्ड नोट लिखें और PDF एक्सपोर्ट करें।", color = theme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { note ->
                            Tactile3DCard(accentColor = theme.primary, onClick = { editingNote = note }, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(note.title.ifBlank { "Untitled Note" }, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (note.subjectName.isNotBlank()) Tactile3DBadge(text = note.subjectName, color = theme.primary)
                                            if (note.topicName.isNotBlank()) Tactile3DBadge(text = note.topicName, color = theme.secondary)
                                        }
                                        if (note.contentSummary.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(note.contentSummary, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant, maxLines = 2)
                                        }
                                    }
                                    IconButton(onClick = { pendingDeleteId = note.id }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete note", tint = theme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val delId = pendingDeleteId
    if (delId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null }, containerColor = theme.surfaceElevated,
            title = { Text("नोट डिलीट करें?", color = theme.onSurface) },
            text = { Text("क्या आप इस पर्सनल नोट को स्थायी रूप से हटाना चाहते हैं?", color = theme.onSurfaceVariant) },
            confirmButton = {
                Button(onClick = { onDeleteNote(delId); pendingDeleteId = null }, colors = ButtonDefaults.buttonColors(containerColor = theme.error, contentColor = theme.onPrimary)) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalNoteEditorView(
    note: PersonalNote,
    subjects: List<Subject>,
    jsonCodec: Json,
    userFilesDir: File,
    onAttachImage: (noteId: String, uri: Uri, onCopied: (String) -> Unit) -> Unit,
    onSave: (PersonalNote) -> Unit,
    onExportPdf: (PersonalNote, List<PersonalNoteBlock>, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var subjectName by remember(note.id) { mutableStateOf(note.subjectName) }
    var topicName by remember(note.id) { mutableStateOf(note.topicName) }
    var blocks by remember(note.id) {
        mutableStateOf(
            runCatching { jsonCodec.decodeFromString<List<PersonalNoteBlock>>(note.blocksJson) }
                .getOrDefault(emptyList())
                .ifEmpty { listOf(PersonalNoteBlock(UUID.randomUUID().toString(), PersonalNoteBlockType.PARAGRAPH.name, "")) }
        )
    }
    var fontSizeSp by remember { mutableIntStateOf(16) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onAttachImage(note.id, uri) { fileName ->
                blocks = blocks + PersonalNoteBlock(UUID.randomUUID().toString(), PersonalNoteBlockType.IMAGE.name, "Image", imageFileName = fileName)
            }
        }
    }

    fun buildCurrentNote(): PersonalNote {
        val summary = blocks.firstOrNull { it.text.isNotBlank() }?.text?.take(120) ?: ""
        return note.copy(
            subjectName = subjectName.ifBlank { subjects.firstOrNull { it.id == note.subjectId }?.nameEn ?: "General" },
            topicName = topicName,
            title = title.ifBlank { "Untitled Study Note" },
            contentSummary = summary,
            blocksJson = jsonCodec.encodeToString(blocks),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                title = { Text("Edit Study Note", style = MaterialTheme.typography.titleMedium, color = theme.primary) },
                actions = {
                    IconButton(onClick = { onExportPdf(buildCurrentNote(), blocks, false) }) { Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export PDF", tint = theme.secondary) }
                    IconButton(onClick = { onExportPdf(buildCurrentNote(), blocks, true) }) { Icon(Icons.Rounded.Share, contentDescription = "Share PDF", tint = theme.primary) }
                    Button(onClick = { onSave(buildCurrentNote()) }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary), modifier = Modifier.padding(end = 6.dp).testTag("save_personal_note_btn")) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Note Title") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("personal_note_title_input"))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = subjectName, onValueChange = { subjectName = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = topicName, onValueChange = { topicName = it }, label = { Text("Topic") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    OutlinedButton(onClick = { blocks = blocks + PersonalNoteBlock(UUID.randomUUID().toString(), PersonalNoteBlockType.HEADING.name, "", fontSizeSp = 20) }) {
                        Icon(Icons.Rounded.FormatBold, contentDescription = null, modifier = Modifier.size(16.dp)); Text(" Heading")
                    }
                }
                item {
                    OutlinedButton(onClick = { blocks = blocks + PersonalNoteBlock(UUID.randomUUID().toString(), PersonalNoteBlockType.HIGHLIGHT.name, "", highlightColorHex = "#FEF08A", fontSizeSp = fontSizeSp) }) {
                        Icon(Icons.Rounded.Highlight, contentDescription = null, modifier = Modifier.size(16.dp)); Text(" Highlight")
                    }
                }
                item {
                    OutlinedButton(onClick = { blocks = blocks + PersonalNoteBlock(UUID.randomUUID().toString(), PersonalNoteBlockType.BULLET.name, "", fontSizeSp = fontSizeSp) }) {
                        Icon(Icons.Rounded.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp)); Text(" Bullet")
                    }
                }
                item {
                    OutlinedButton(onClick = { blocks = blocks + PersonalNoteBlock(UUID.randomUUID().toString(), PersonalNoteBlockType.CHECKLIST.name, "", fontSizeSp = fontSizeSp) }) {
                        Icon(Icons.Rounded.CheckBox, contentDescription = null, modifier = Modifier.size(16.dp)); Text(" Checklist")
                    }
                }
                item {
                    OutlinedButton(onClick = { blocks = blocks + PersonalNoteBlock(UUID.randomUUID().toString(), PersonalNoteBlockType.TABLE_ROW.name, "", tableCells = listOf("Col 1", "Col 2")) }) {
                        Icon(Icons.Rounded.TableRows, contentDescription = null, modifier = Modifier.size(16.dp)); Text(" Table Row")
                    }
                }
                item {
                    OutlinedButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp)); Text(" Image")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(blocks, key = { it.id }) { block ->
                    val bType = PersonalNoteBlockType.fromRaw(block.type)
                    Tactile3DCard(accentColor = if (bType == PersonalNoteBlockType.HIGHLIGHT) theme.secondary else theme.primary, lipDepth = 2.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(bType.name, style = MaterialTheme.typography.labelSmall, color = theme.primary, modifier = Modifier.weight(1f))
                            IconButton(onClick = { blocks = blocks.filterNot { it.id == block.id } }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove block", modifier = Modifier.size(18.dp))
                            }
                        }
                        when (bType) {
                            PersonalNoteBlockType.IMAGE -> {
                                val f = block.imageFileName?.let { File(userFilesDir, it) }
                                if (f != null && f.exists()) {
                                    AsyncImage(model = f, contentDescription = "Attached note image", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp)))
                                } else Text("Attached image: ${block.imageFileName ?: ""}", style = MaterialTheme.typography.bodySmall)
                            }
                            PersonalNoteBlockType.CHECKLIST -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = block.checked, onCheckedChange = { c ->
                                        blocks = blocks.map { if (it.id == block.id) it.copy(checked = c) else it }
                                    })
                                    OutlinedTextField(value = block.text, onValueChange = { t ->
                                        blocks = blocks.map { if (it.id == block.id) it.copy(text = t) else it }
                                    }, label = { Text("Task") }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            PersonalNoteBlockType.TABLE_ROW -> {
                                val c0 = block.tableCells.getOrElse(0) { "" }
                                val c1 = block.tableCells.getOrElse(1) { "" }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = c0, onValueChange = { v ->
                                        blocks = blocks.map { if (it.id == block.id) it.copy(tableCells = listOf(v, c1)) else it }
                                    }, label = { Text("Column 1") }, modifier = Modifier.weight(1f))
                                    OutlinedTextField(value = c1, onValueChange = { v ->
                                        blocks = blocks.map { if (it.id == block.id) it.copy(tableCells = listOf(c0, v)) else it }
                                    }, label = { Text("Column 2") }, modifier = Modifier.weight(1f))
                                }
                            }
                            else -> {
                                val bgTint = if (bType == PersonalNoteBlockType.HIGHLIGHT) Color(0x33FEF08A) else Color.Transparent
                                OutlinedTextField(
                                    value = block.text,
                                    onValueChange = { t -> blocks = blocks.map { if (it.id == block.id) it.copy(text = t) else it } },
                                    label = { Text(if (bType == PersonalNoteBlockType.HEADING) "Heading" else "Content") },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = block.fontSizeSp.sp),
                                    modifier = Modifier.fillMaxWidth().background(bgTint, RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
