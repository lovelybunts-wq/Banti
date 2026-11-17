package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.local.LocalFileStorage
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyLog
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Topic
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.StudyRepository
import com.example.ui.components.LearnTalentLogo
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme
import java.io.File

@Composable
fun HomeSubjectsScreen(
    subjectsState: UiState<List<Subject>>,
    topicsState: UiState<List<Topic>>,
    notesState: UiState<List<StudyNote>>,
    studyLogsState: UiState<List<StudyLog>>,
    revisionDiaryState: UiState<List<RevisionDiaryEntry>>,
    profileState: UiState<UserProfile?>,
    cloudSyncEnabled: Boolean,
    isOfflineSession: Boolean,
    customLogoFile: File?,
    onSubjectClick: (Subject) -> Unit,
    onOpenRevisionTab: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onOpenWhatsAppRequest: () -> Unit,
    onAddSubject: (String, String?, String, String, Long) -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current
    val isOnline = remember { LocalFileStorage.isOnline(context) }

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val topics = (topicsState as? UiState.Success)?.data ?: emptyList()
    val notes = (notesState as? UiState.Success)?.data ?: emptyList()
    val logs = (studyLogsState as? UiState.Success)?.data ?: emptyList()
    val diaryEntries = (revisionDiaryState as? UiState.Success)?.data ?: emptyList()
    val profile = (profileState as? UiState.Success)?.data

    val yesterdayKey = remember { StudyRepository.yesterdayKey() }
    val yesterdayLogs = remember(logs, yesterdayKey) {
        logs.filter { it.dateKey == yesterdayKey && it.entryType == "STUDIED" }
    }

    val daysLeftText = remember(profile) {
        val target = profile?.examDateMillis ?: return@remember null
        val diff = ((target - System.currentTimeMillis()) / (1000L * 60L * 60L * 24L)).toInt()
        if (diff >= 0) "$diff Days Left" else "Exam Day Passed"
    }

    Scaffold(
        containerColor = theme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = theme.primary,
                contentColor = theme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("add_subject_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_subject))
            }
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
                    .widthIn(max = 1100.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                // Mandatory English Brand Header + IMG Logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        LearnTalentLogo(
                            customLogoFile = customLogoFile,
                            size = 42.dp,
                            cornerRadius = 11.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.brand_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = theme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("top_brand_title")
                            )
                            Text(
                                text = "नोट्स • मॉक टेस्ट • रिवीजन डायरी",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    val synced = !isOfflineSession && cloudSyncEnabled && isOnline
                    Box(modifier = Modifier.clickable { onOpenSettingsTab() }) {
                        Tactile3DBadge(
                            text = when {
                                synced -> stringResource(R.string.cloud_synced)
                                isOfflineSession -> "Offline Mode"
                                else -> stringResource(R.string.offline_mode)
                            },
                            color = if (synced) theme.success else theme.secondary,
                            icon = if (synced) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search subject (e.g. History, Science...)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subjects_search_input")
                )

                Spacer(Modifier.height(12.dp))

                when (subjectsState) {
                    is UiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = theme.primary)
                        }
                    }
                    is UiState.Error -> {
                        Tactile3DCard(accentColor = theme.error, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = subjectsState.message,
                                color = theme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is UiState.Success -> {
                        val filtered = remember(subjectsState.data, searchQuery) {
                            val q = searchQuery.trim().lowercase()
                            if (q.isEmpty()) subjectsState.data
                            else subjectsState.data.filter {
                                it.nameEn.lowercase().contains(q) || (it.nameHi?.lowercase()?.contains(q) == true)
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Top Revision Banner
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Tactile3DCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = theme.secondary,
                                    onClick = onOpenRevisionTab,
                                    testTag = "home_yesterday_revision_card",
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.img_study_hero_banner),
                                                contentDescription = "Revision preview",
                                                modifier = Modifier
                                                    .size(54.dp)
                                                    .clip(RoundedCornerShape(14.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Rounded.History,
                                                        contentDescription = null,
                                                        tint = theme.secondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = "कल क्या पढ़ा था • Revision Diary",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = theme.secondary
                                                    )
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                val summary = if (yesterdayLogs.isNotEmpty()) {
                                                    yesterdayLogs.joinToString(", ") { it.title }
                                                } else {
                                                    "रिवीजन डायरी (${diaryEntries.size}) • आज का प्लान देखें →"
                                                }
                                                Text(
                                                    text = summary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = theme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (daysLeftText != null) {
                                            Spacer(Modifier.width(8.dp))
                                            Tactile3DBadge(text = daysLeftText, color = theme.primary)
                                        }
                                    }
                                }
                            }

                            // Quick Action Row: Revision Diary Folder & WhatsApp Test Request
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Tactile3DCard(
                                        modifier = Modifier.weight(1f),
                                        accentColor = theme.tertiary,
                                        lipDepth = 3.dp,
                                        onClick = onOpenRevisionTab,
                                        testTag = "home_revision_diary_quick_card",
                                        contentPadding = PaddingValues(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TactileIconOrb(
                                                icon = Icons.Rounded.Book,
                                                tint = theme.tertiary,
                                                size = 36.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "रिवीजन डायरी",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = theme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${diaryEntries.size} सोर्स व नोट्स",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = theme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Tactile3DCard(
                                        modifier = Modifier.weight(1f),
                                        accentColor = theme.success,
                                        lipDepth = 3.dp,
                                        onClick = onOpenWhatsAppRequest,
                                        testTag = "home_whatsapp_request_card",
                                        contentPadding = PaddingValues(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TactileIconOrb(
                                                icon = Icons.AutoMirrored.Rounded.Send,
                                                tint = theme.success,
                                                size = 36.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "टेस्ट / नोट्स मांगें",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = theme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "WhatsApp Admin",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = theme.success
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            items(filtered, key = { it.id }) { subject ->
                                val accent = SubjectVisuals.colorFor(subject.colorHex, theme.primary)
                                val topicCount = topics.count { it.subjectId == subject.id }
                                val noteCount = notes.count { it.subjectId == subject.id }

                                Tactile3DCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = accent,
                                    onClick = { onSubjectClick(subject) },
                                    testTag = "subject_card_${subject.id}",
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    TactileIconOrb(
                                        icon = SubjectVisuals.iconFor(subject.iconKey),
                                        tint = accent,
                                        size = 46.dp,
                                        contentDescription = subject.nameEn
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = subject.nameEn,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!subject.nameHi.isNullOrBlank()) {
                                        Text(
                                            text = subject.nameHi,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Tactile3DBadge(
                                            text = "$topicCount Topics",
                                            color = accent
                                        )
                                        if (noteCount > 0) {
                                            Text(
                                                text = "$noteCount files",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = theme.onSurfaceVariant
                                            )
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

    if (showAddDialog) {
        val currentCount = (subjectsState as? UiState.Success)?.data?.size?.toLong() ?: 7L
        AddSubjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { nameEn, nameHi, iconKey, colorHex ->
                onAddSubject(nameEn, nameHi, iconKey, colorHex, currentCount)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String, String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var nameEn by remember { mutableStateOf("") }
    var nameHi by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("book") }
    var selectedColor by remember { mutableStateOf(SubjectVisuals.presetPalette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.surfaceElevated,
        title = { Text("नया विषय जोड़ें (Add Subject)", color = theme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text("Subject Name (English)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_subject_en_input")
                )
                OutlinedTextField(
                    value = nameHi,
                    onValueChange = { nameHi = it },
                    label = { Text("विषय का नाम (Hindi - Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color Accent", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SubjectVisuals.presetPalette) { hex ->
                        val c = SubjectVisuals.colorFor(hex, theme.primary)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 0.dp,
                                    color = theme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SubjectVisuals.presetIconKeys) { (key, _) ->
                        val isSelected = selectedIcon == key
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) theme.primaryContainer else theme.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.primary else theme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedIcon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = SubjectVisuals.iconFor(key),
                                contentDescription = key,
                                tint = if (isSelected) theme.primary else theme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nameEn.isNotBlank()) onConfirm(nameEn, nameHi.ifBlank { null }, selectedIcon, selectedColor) },
                enabled = nameEn.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
