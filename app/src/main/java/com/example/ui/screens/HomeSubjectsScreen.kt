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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.FullLengthExam
import com.example.data.model.FullLengthExamDefaults
import com.example.data.model.PersonalNote
import com.example.data.model.RecentActivityItem
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyLog
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Topic
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.StudyRepository
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.StudentAvatar
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

@Composable
fun HomeSubjectsScreen(
    subjectsState: UiState<List<Subject>>,
    topicsState: UiState<List<Topic>>,
    notesState: UiState<List<StudyNote>>,
    fullLengthExamsState: UiState<List<FullLengthExam>>,
    studyLogsState: UiState<List<StudyLog>>,
    revisionDiaryState: UiState<List<RevisionDiaryEntry>>,
    personalNotesState: UiState<List<PersonalNote>>,
    bookmarksState: UiState<List<BookmarkItem>>,
    recentActivities: List<RecentActivityItem>,
    profileState: UiState<UserProfile?>,
    displayName: String,
    photoUrl: String?,
    isAdminUser: Boolean,
    onToggleLightDark: () -> Unit,
    onSubjectClick: (Subject) -> Unit,
    onOpenFullLengthTests: () -> Unit,
    onOpenRecentActivity: (RecentActivityItem) -> Unit,
    onOpenRevisionTab: () -> Unit,
    onOpenSettingsTab: () -> Unit,
    onOpenPersonalNotes: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onOpenSupport: () -> Unit,
    onBookmarkSubject: (Subject) -> Unit,
    onAddSubject: (String, String?, String, String, Long) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val topics = (topicsState as? UiState.Success)?.data ?: emptyList()
    val notes = (notesState as? UiState.Success)?.data ?: emptyList()
    val fullLengthExams = (fullLengthExamsState as? UiState.Success)?.data ?: FullLengthExamDefaults.builtInExams
    val fullLengthTestCount = remember(notes) {
        notes.count { it.subjectId == FullLengthExamDefaults.SUBJECT_ID }
    }
    val logs = (studyLogsState as? UiState.Success)?.data ?: emptyList()
    val diaryEntries = (revisionDiaryState as? UiState.Success)?.data ?: emptyList()
    val personalNotes = (personalNotesState as? UiState.Success)?.data ?: emptyList()
    val bookmarks = (bookmarksState as? UiState.Success)?.data ?: emptyList()
    val profile = (profileState as? UiState.Success)?.data
    val resolvedName = remember(profile?.displayName, displayName) {
        profile?.displayName?.trim()?.takeIf { it.isNotBlank() && it != "Learner" }
            ?: displayName.trim().takeIf { it.isNotBlank() }
            ?: "Student"
    }

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
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LearnTalentBrandHeader(
                        customLogoFile = null,
                        subtitle = "Hello, ${resolvedName.substringBefore(' ')}",
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onToggleLightDark, modifier = Modifier.testTag("home_theme_toggle_btn")) {
                            Icon(
                                imageVector = if (theme.isLight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                contentDescription = "Toggle Light/Dark theme",
                                tint = theme.primary
                            )
                        }
                        Box(modifier = Modifier.clickable { onOpenSettingsTab() }.testTag("home_profile_avatar_btn")) {
                            StudentAvatar(photoUrl = photoUrl, displayName = resolvedName, size = 38.dp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search subject (e.g. History, MP GK, Polity...)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("subjects_search_input")
                )
                Spacer(Modifier.height(10.dp))

                when (subjectsState) {
                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = theme.primary)
                    }
                    is UiState.Error -> Tactile3DCard(accentColor = theme.error, modifier = Modifier.fillMaxWidth()) {
                        Text(subjectsState.message, color = theme.error, style = MaterialTheme.typography.bodyMedium)
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
                            columns = GridCells.Adaptive(minSize = 158.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isAdminUser) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Tactile3DCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        accentColor = theme.tertiary,
                                        onClick = onOpenAdminPanel,
                                        testTag = "home_admin_panel_card",
                                        contentPadding = PaddingValues(14.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            TactileIconOrb(icon = Icons.Rounded.AdminPanelSettings, tint = theme.tertiary, size = 44.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Admin Control Center (Upload & Analytics)", style = MaterialTheme.typography.titleSmall, color = theme.tertiary, fontWeight = FontWeight.Bold)
                                                Text("सभी छात्रों के लिए PDF/HTML टेस्ट अपलोड, छात्र रिजल्ट व अनुरोध देखें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                            }
                                            Tactile3DBadge(text = "ADMIN", color = theme.tertiary)
                                        }
                                    }
                                }
                            }

                            // Prominent Outside Full Length Tests Card
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Tactile3DCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = theme.primary,
                                    onClick = onOpenFullLengthTests,
                                    testTag = "home_full_length_tests_card",
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            TactileIconOrb(icon = Icons.Rounded.Assignment, tint = theme.primary, size = 46.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "Full Length Tests (फुल लेंथ टेस्ट)",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = theme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "एग्जाम चुनें, नया एग्जाम बनाएं व .html मॉक टेस्ट अपलोड कर तुरंत टेस्ट दें",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = theme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Tactile3DBadge(text = "${fullLengthExams.size} Exams", color = theme.primary)
                                            Tactile3DBadge(text = "$fullLengthTestCount Tests", color = theme.secondary)
                                        }
                                    }
                                }
                            }

                            if (recentActivities.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Continue Learning (हाल ही में पढ़ा गया)", style = MaterialTheme.typography.titleSmall, color = theme.primary, fontWeight = FontWeight.SemiBold)
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            items(recentActivities.take(6), key = { it.id }) { act ->
                                                Tactile3DCard(
                                                    modifier = Modifier.width(210.dp),
                                                    accentColor = theme.secondary,
                                                    lipDepth = 3.dp,
                                                    onClick = { onOpenRecentActivity(act) },
                                                    contentPadding = PaddingValues(10.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        TactileIconOrb(icon = Icons.Rounded.PlayArrow, tint = theme.secondary, size = 34.dp)
                                                        Spacer(Modifier.width(8.dp))
                                                        Column {
                                                            Text(act.title, style = MaterialTheme.typography.labelLarge, color = theme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(act.subtitle.ifBlank { act.itemType }, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Tactile3DCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = theme.secondary,
                                    onClick = onOpenRevisionTab,
                                    testTag = "home_yesterday_revision_card",
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Image(
                                                painter = painterResource(id = R.drawable.img_study_hero_banner),
                                                contentDescription = "Revision preview",
                                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.History, contentDescription = null, tint = theme.secondary, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("कल क्या पढ़ा था • Revision", style = MaterialTheme.typography.titleSmall, color = theme.secondary)
                                                }
                                                val summary = if (yesterdayLogs.isNotEmpty()) yesterdayLogs.joinToString(", ") { it.title }
                                                else "रिवीजन डायरी (${diaryEntries.size}) • आज का प्लान देखें →"
                                                Text(summary, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        if (daysLeftText != null) Tactile3DBadge(text = daysLeftText, color = theme.primary)
                                    }
                                }
                            }

                            // Quick 2x2 Action Hub
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        QuickHubCard(Modifier.weight(1f), Icons.Rounded.EditNote, theme.primary, "Personal Notes", "${personalNotes.size} नोट्स", "home_personal_notes_card", onOpenPersonalNotes)
                                        QuickHubCard(Modifier.weight(1f), Icons.Rounded.Bookmarks, theme.tertiary, "Bookmarks", "${bookmarks.size} सेव", "home_bookmarks_card", onOpenBookmarks)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        QuickHubCard(Modifier.weight(1f), Icons.Rounded.Book, theme.secondary, "रिवीजन डायरी", "${diaryEntries.size} एंट्री", "home_revision_diary_quick_card", onOpenRevisionTab)
                                        QuickHubCard(Modifier.weight(1f), Icons.AutoMirrored.Rounded.HelpOutline, theme.success, "Support", "नोट्स / टेस्ट अनुरोध", "home_whatsapp_request_card", onOpenSupport)
                                    }
                                }
                            }

                            items(filtered, key = { it.id }) { subject ->
                                val accent = SubjectVisuals.colorFor(subject.colorHex, theme.primary)
                                val topicCount = topics.count { it.subjectId == subject.id }
                                val noteCount = notes.count { it.subjectId == subject.id }
                                val isSaved = bookmarks.any { it.bookmarkType == BookmarkType.SAVED_TOPIC.name && it.refId == subject.id }

                                Tactile3DCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = accent,
                                    onClick = { onSubjectClick(subject) },
                                    testTag = "subject_card_${subject.id}",
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                        TactileIconOrb(icon = SubjectVisuals.iconFor(subject.iconKey), tint = accent, size = 44.dp, contentDescription = subject.nameEn)
                                        IconButton(onClick = { onBookmarkSubject(subject) }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                                contentDescription = "Save subject",
                                                tint = if (isSaved) accent else theme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(subject.nameEn, style = MaterialTheme.typography.titleMedium, color = theme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!subject.nameHi.isNullOrBlank()) {
                                        Text(subject.nameHi, style = MaterialTheme.typography.bodySmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Tactile3DBadge(text = "$topicCount Topics", color = accent)
                                        if (noteCount > 0) Text("$noteCount files", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
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
        val currentCount = (subjectsState as? UiState.Success)?.data?.size?.toLong() ?: 12L
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
private fun QuickHubCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    Tactile3DCard(
        modifier = modifier,
        accentColor = color,
        lipDepth = 3.dp,
        onClick = onClick,
        testTag = testTag,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TactileIconOrb(icon = icon, tint = color, size = 36.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = theme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            }
        }
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
                OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text("Subject Name (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("new_subject_en_input"))
                OutlinedTextField(value = nameHi, onValueChange = { nameHi = it }, label = { Text("विषय का नाम (Hindi - Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Color Accent", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SubjectVisuals.presetPalette) { hex ->
                        val c = SubjectVisuals.colorFor(hex, theme.primary)
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(c)
                                .border(if (selectedColor == hex) 3.dp else 0.dp, theme.onSurface, CircleShape)
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SubjectVisuals.presetIconKeys) { (key, _) ->
                        val isSelected = selectedIcon == key
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) theme.primaryContainer else theme.surface)
                                .border(1.dp, if (isSelected) theme.primary else theme.outline, RoundedCornerShape(12.dp))
                                .clickable { selectedIcon = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(SubjectVisuals.iconFor(key), contentDescription = key, tint = if (isSelected) theme.primary else theme.onSurfaceVariant)
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
