package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.NoteType
import com.example.data.model.PersonalNote
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Topic
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

@Composable
fun StudyHubScreen(
    subjects: List<Subject>,
    topics: List<Topic>,
    notes: List<StudyNote>,
    bookmarks: List<BookmarkItem>,
    personalNotes: List<PersonalNote>,
    diaryEntries: List<RevisionDiaryEntry>,
    onSubjectClick: (Subject) -> Unit,
    onBookmarkSubject: (Subject) -> Unit,
    onOpenNote: (StudyNote, String) -> Unit,
    onOpenRevisionPlanner: () -> Unit,
    onOpenPersonalNotes: () -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableIntStateOf(0) } // 0=All Subjects, 1=PDFs & eBooks, 2=Images & Notes

    val studyMaterials = remember(notes, filterTab, searchQuery) {
        val q = searchQuery.trim().lowercase()
        notes.filter { note ->
            val type = note.resolvedType()
            val matchesFilter = when (filterTab) {
                1 -> type == NoteType.PDF
                2 -> type == NoteType.IMAGE || (type == NoteType.HTML && !note.isMockTest)
                else -> type == NoteType.PDF || type == NoteType.IMAGE
            }
            val matchesSearch = q.isEmpty() || note.title.lowercase().contains(q) || note.folderName.lowercase().contains(q)
            matchesFilter && matchesSearch
        }
    }

    val filteredSubjects = remember(subjects, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) subjects
        else subjects.filter {
            it.nameEn.lowercase().contains(q) || (it.nameHi?.lowercase()?.contains(q) == true)
        }
    }

    Scaffold(containerColor = theme.background) { inner ->
        Box(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 1080.dp).fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LearnTalentBrandHeader(
                        customLogoFile = null,
                        subtitle = "Study Materials, Notes & Revision"
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search 12 subjects, PDFs, study notes...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("study_hub_search_input")
                    )
                }

                // Quick Revision & Notes Strip
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StudyQuickTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Book,
                            tint = theme.secondary,
                            title = "Revision",
                            count = "${diaryEntries.size} entries",
                            onClick = onOpenRevisionPlanner
                        )
                        StudyQuickTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.EditNote,
                            tint = theme.primary,
                            title = "My Notes",
                            count = "${personalNotes.size} notes",
                            onClick = onOpenPersonalNotes
                        )
                        StudyQuickTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Bookmarks,
                            tint = theme.goldAccent,
                            title = "Saved",
                            count = "${bookmarks.size} items",
                            onClick = onOpenBookmarks
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tabs = listOf("Subjects (${subjects.size})", "PDF Notes & eBooks", "Images & HTML")
                        items(tabs.indices.toList()) { idx ->
                            FilterChip(
                                selected = filterTab == idx,
                                onClick = { filterTab = idx },
                                label = { Text(tabs[idx]) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.primary,
                                    selectedLabelColor = theme.onPrimary
                                )
                            )
                        }
                    }
                }

                if (filterTab == 0) {
                    items(filteredSubjects, key = { it.id }) { subject ->
                        val accent = SubjectVisuals.colorFor(subject.colorHex, theme.primary)
                        val topicCount = topics.count { it.subjectId == subject.id }
                        val noteCount = notes.count { it.subjectId == subject.id }
                        val isSaved = bookmarks.any { it.bookmarkType == BookmarkType.SAVED_TOPIC.name && it.refId == subject.id }

                        Tactile3DCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = accent,
                            onClick = { onSubjectClick(subject) },
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                TactileIconOrb(icon = SubjectVisuals.iconFor(subject.iconKey), tint = accent, size = 44.dp)
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
                } else {
                    if (studyMaterials.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                                Text("इस कैटेगरी में अभी कोई Study File नहीं मिली।", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                Text("Subjects खोलकर PDF, Image या HTML नोट्स जोड़ें या Admin Shared Catalog रिफ्रेश करें।", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(studyMaterials, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { note ->
                            val subjName = subjects.firstOrNull { it.id == note.subjectId }?.nameEn ?: "Study"
                            val icon = if (note.resolvedType() == NoteType.IMAGE) Icons.Rounded.Image else Icons.Rounded.Description
                            Tactile3DCard(
                                accentColor = theme.primary,
                                onClick = { onOpenNote(note, subjName) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        TactileIconOrb(icon = icon, tint = theme.primary, size = 40.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(note.title, style = MaterialTheme.typography.titleSmall, color = theme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("$subjName • ${note.folderName.ifBlank { note.noteType }}", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                        }
                                    }
                                    Tactile3DBadge(text = note.noteType, color = if (note.isShared) theme.goldAccent else theme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyQuickTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    count: String,
    onClick: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    Tactile3DCard(
        modifier = modifier,
        accentColor = tint,
        lipDepth = 3.dp,
        onClick = onClick,
        contentPadding = PaddingValues(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            TactileIconOrb(icon = icon, tint = tint, size = 34.dp)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = theme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant, maxLines = 1)
        }
    }
}
