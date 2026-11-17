package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalFileStorage
import com.example.data.local.SharedDownloadRecord
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.StudyNote
import com.example.data.model.TestResult
import com.example.data.model.VideoLink
import com.example.ui.MainViewModel
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

@Composable
internal fun SubtopicNotesSection(
    accent: Color,
    isAdminUser: Boolean,
    folderNames: List<String>,
    selectedFolder: String,
    onSelectFolder: (String) -> Unit,
    notes: List<StudyNote>,
    bookmarks: List<BookmarkItem>,
    sharedDownloadsMap: Map<String, SharedDownloadRecord>,
    sharedDownloadProgress: Map<String, Float>,
    onNewFolderClick: () -> Unit,
    onAddPdfClick: () -> Unit,
    onAddHtmlClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onRequestMaterialWhatsApp: () -> Unit,
    onOpenNote: (StudyNote) -> Unit,
    onDownloadShared: (StudyNote) -> Unit,
    onToggleBookmarkNote: (StudyNote) -> Unit,
    onEditNote: (StudyNote) -> Unit,
    onConfirmDeleteNote: (StudyNote) -> Unit,
) {
    val theme = LocalStudyTheme.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton(onClick = onAddPdfClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("add_pdf_note_btn")) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = theme.secondary)
                        Spacer(Modifier.width(6.dp)); Text("PDF", color = theme.onSurface)
                    }
                }
                item {
                    OutlinedButton(onClick = onAddHtmlClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("add_html_note_btn")) {
                        Icon(Icons.Rounded.Code, contentDescription = null, tint = accent)
                        Spacer(Modifier.width(6.dp)); Text("HTML", color = theme.onSurface)
                    }
                }
                item {
                    OutlinedButton(onClick = onAddImageClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("add_image_note_btn")) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = theme.tertiary)
                        Spacer(Modifier.width(6.dp)); Text("Image", color = theme.onSurface)
                    }
                }
                item {
                    OutlinedButton(onClick = onNewFolderClick, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("new_folder_btn")) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = theme.primary)
                        Spacer(Modifier.width(6.dp)); Text("Folder", color = theme.onSurface)
                    }
                }
                item {
                    OutlinedButton(onClick = onRequestMaterialWhatsApp, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("notes_whatsapp_request_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = theme.success)
                        Spacer(Modifier.width(6.dp)); Text("मांगें (WhatsApp)", color = theme.success)
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
                        leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(folder) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.22f), selectedLabelColor = accent)
                    )
                }
            }
        }

        if (notes.isEmpty()) {
            item {
                Tactile3DCard(accentColor = accent, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painter = painterResource(id = R.drawable.img_empty_notes), contentDescription = "Empty notes", modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.height(10.dp))
                        Text("इस फोल्डर में कोई नोट नहीं है", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                        Text("ऊपर से PDF, HTML या Image चुनकर अपना नाम दें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
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
                val downloadPct = sharedDownloadProgress[note.id]
                val downloadedRec = sharedDownloadsMap[note.id]
                val isDownloaded = downloadedRec != null
                val hasUpdate = downloadedRec != null && downloadedRec.version < note.version
                val isBookmarked = bookmarks.any { it.bookmarkType == BookmarkType.FAVORITE_NOTE.name && it.refId == note.id }
                val canModify = !note.isShared || isAdminUser

                Tactile3DCard(modifier = Modifier.fillMaxWidth(), accentColor = accent, onClick = { onOpenNote(note) }, testTag = "note_card_${note.id}") {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TactileIconOrb(icon = icon, tint = accent, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium, color = theme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tactile3DBadge(text = note.noteType, color = accent)
                                if (note.isShared) Tactile3DBadge(text = "Shared v${note.version}", color = theme.secondary)
                                if (note.folderName.isNotBlank()) Tactile3DBadge(text = note.folderName, color = theme.primary)
                                if (note.fileSizeBytes > 0L) Tactile3DBadge(text = LocalFileStorage.formatBytes(note.fileSizeBytes), color = theme.tertiary)
                            }
                            if (note.isShared) {
                                Spacer(Modifier.height(4.dp))
                                when {
                                    downloadPct != null -> {
                                        LinearProgressIndicator(progress = { downloadPct.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = theme.success)
                                        Text("Downloading ${(downloadPct * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = theme.success)
                                    }
                                    hasUpdate -> Tactile3DBadge(text = "Update v${note.version} Ready", color = theme.secondary, icon = Icons.Rounded.Update)
                                    isDownloaded -> Tactile3DBadge(text = "Offline Ready", color = theme.success, icon = Icons.Rounded.DownloadDone)
                                }
                            }
                        }
                        if (note.isShared) {
                            IconButton(onClick = { onDownloadShared(note) }, modifier = Modifier.testTag("download_shared_${note.id}")) {
                                Icon(
                                    imageVector = if (isDownloaded && !hasUpdate) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                    contentDescription = "Save Offline",
                                    tint = if (isDownloaded && !hasUpdate) theme.success else theme.primary
                                )
                            }
                        }
                        IconButton(onClick = { onToggleBookmarkNote(note) }) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Bookmark Note",
                                tint = if (isBookmarked) accent else theme.onSurfaceVariant
                            )
                        }
                        if (canModify) {
                            if (!note.isShared) {
                                IconButton(onClick = { onEditNote(note) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Rename or Move", tint = theme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onConfirmDeleteNote(note) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = theme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SubtopicTestsSection(
    accent: Color,
    isAdminUser: Boolean,
    htmlTests: List<StudyNote>,
    mcqs: List<McqQuestion>,
    testResults: List<TestResult>,
    sharedDownloadsMap: Map<String, SharedDownloadRecord>,
    sharedDownloadProgress: Map<String, Float>,
    onUploadHtmlTest: () -> Unit,
    onCreateSampleTest: () -> Unit,
    onRequestMaterialWhatsApp: () -> Unit,
    onRunHtmlTest: (StudyNote) -> Unit,
    onDownloadShared: (StudyNote) -> Unit,
    onOpenTestResult: (String) -> Unit,
    onAddMcq: () -> Unit,
    onStartMcqPractice: () -> Unit,
    onConfirmDeleteMcq: (McqQuestion) -> Unit,
) {
    val theme = LocalStudyTheme.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                Text("HTML CBT Mock Tests (मॉक टेस्ट)", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("Exam Mode या Practice Mode चुनें — सबमिट करते ही पूर्ण विश्लेषण, उत्तर समीक्षा और PDF रिजल्ट मिलेगा।", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onUploadHtmlTest,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondary, contentColor = theme.onSecondary),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("upload_html_test_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("Upload HTML")
                    }
                    OutlinedButton(onClick = onCreateSampleTest, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("create_sample_html_test_btn")) {
                        Text("Sample Test", color = theme.onSurface)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRequestMaterialWhatsApp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("tests_whatsapp_request_btn")) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = theme.success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("एडमिन से टेस्ट / MCQ WhatsApp पर मांगें", color = theme.success)
                }
            }
        }

        items(htmlTests, key = { "html_${it.id}" }) { testNote ->
            val latestResult = testResults.firstOrNull { it.noteId == testNote.id }
            val progress = sharedDownloadProgress[testNote.id]
            val isDownloaded = sharedDownloadsMap.containsKey(testNote.id)
            Tactile3DCard(modifier = Modifier.fillMaxWidth(), accentColor = theme.primary, onClick = { onRunHtmlTest(testNote) }, testTag = "html_test_card_${testNote.id}") {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(testNote.title, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                            if (testNote.isShared) Tactile3DBadge(text = "Shared", color = theme.secondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (latestResult != null) {
                            Tactile3DBadge(text = "Last: ${MainViewModel.formatNum(latestResult.scoreObtained)}/${MainViewModel.formatNum(latestResult.totalMarks)} (${MainViewModel.formatNum(latestResult.percentage)}%)", color = theme.success)
                        } else {
                            Text("Exam & Practice Mode Ready", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                        }
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = theme.success)
                        }
                    }
                    if (testNote.isShared) {
                        IconButton(onClick = { onDownloadShared(testNote) }) {
                            Icon(if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download, contentDescription = "Save Offline", tint = if (isDownloaded) theme.success else theme.primary)
                        }
                    }
                    Button(onClick = { onRunHtmlTest(testNote) }, colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Start")
                    }
                }
            }
        }

        if (testResults.isNotEmpty()) {
            item { Text("Recent Scores (टैप करके पूरा रिजल्ट देखें)", style = MaterialTheme.typography.titleSmall, color = theme.primary) }
            items(testResults.take(5), key = { "res_${it.id}" }) { res ->
                Tactile3DCard(accentColor = theme.success, lipDepth = 3.dp, onClick = { onOpenTestResult(res.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(res.testTitle, style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                            Text("${res.takenDate} • ${res.testMode}", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                        }
                        Tactile3DBadge(text = "${MainViewModel.formatNum(res.scoreObtained)} / ${MainViewModel.formatNum(res.totalMarks)}", color = theme.success)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Tactile3DCard(accentColor = accent, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Practice MCQs (${mcqs.size})", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                        Text("खुद के प्रश्न जोड़ें और प्रैक्टिस करें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onAddMcq, modifier = Modifier.testTag("add_mcq_btn")) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text("Add", color = theme.onSurface)
                        }
                        if (mcqs.isNotEmpty()) {
                            Button(onClick = onStartMcqPractice, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary), modifier = Modifier.testTag("start_mcq_btn")) {
                                Icon(Icons.Rounded.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp)); Text("Practice")
                            }
                        }
                    }
                }
            }
        }

        items(mcqs, key = { "mcq_${it.id}" }) { mcq ->
            val canDelete = !mcq.isShared || isAdminUser
            Tactile3DCard(accentColor = theme.tertiary, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(mcq.question, style = MaterialTheme.typography.bodyMedium, color = theme.onSurface, modifier = Modifier.weight(1f, fill = false))
                            if (mcq.isShared) Tactile3DBadge(text = "Shared", color = theme.secondary)
                        }
                        Spacer(Modifier.height(4.dp))
                        val ans = mcq.options.getOrNull(mcq.correctIndex.toInt()) ?: ""
                        Text("Ans: $ans", style = MaterialTheme.typography.labelMedium, color = theme.success)
                    }
                    if (canDelete) {
                        IconButton(onClick = { onConfirmDeleteMcq(mcq) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete MCQ", tint = theme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SubtopicVideosSection(
    accent: Color,
    isAdminUser: Boolean,
    videos: List<VideoLink>,
    onAddVideo: () -> Unit,
    onWatchVideo: (VideoLink) -> Unit,
    onConfirmDeleteVideo: (VideoLink) -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onAddVideo,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_youtube_link_btn")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_youtube_link))
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
                            Text("जिस वीडियो से आपने पढ़ा है, उसका लिंक यहां सेव करें और एक टैप में देखें।", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            items(videos, key = { it.id }) { video ->
                val canDelete = !video.isShared || isAdminUser
                Tactile3DCard(
                    modifier = Modifier.fillMaxWidth(), accentColor = theme.secondary,
                    onClick = {
                        onWatchVideo(video)
                        val rawUrl = video.youtubeUrl.trim()
                        val safeUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))) }
                    },
                    testTag = "video_card_${video.id}"
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TactileIconOrb(icon = Icons.Rounded.PlayCircle, tint = theme.secondary, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(video.title, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                if (video.isShared) Tactile3DBadge(text = "Shared", color = theme.primary)
                            }
                            Text(video.youtubeUrl, style = MaterialTheme.typography.bodySmall, color = theme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!video.channelOrNote.isNullOrBlank()) {
                                Text(video.channelOrNote, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                            }
                        }
                        if (canDelete) {
                            IconButton(onClick = { onConfirmDeleteVideo(video) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete video", tint = theme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
