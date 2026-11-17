package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FullLengthExam
import com.example.data.model.FullLengthExamDefaults
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.TestResult
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.BrandColors
import com.example.ui.theme.LocalStudyTheme

@Composable
fun TestsHubScreen(
    subjects: List<Subject>,
    notes: List<StudyNote>,
    mcqs: List<McqQuestion>,
    fullLengthExams: List<FullLengthExam>,
    testResults: List<TestResult>,
    onStartHtmlTest: (StudyNote, isTestMode: Boolean) -> Unit,
    onOpenFullLengthExam: (FullLengthExam) -> Unit,
    onOpenAllFullLengthExams: () -> Unit,
    onStartPracticeMcqs: (subjectId: String, topicId: String, subtopicId: String) -> Unit,
    onOpenSubjectTopics: (Subject) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=All, 1=Mock CBT, 2=Full Length, 3=Practice

    val allHtmlTests = remember(notes) {
        notes.filter {
            it.resolvedType() == NoteType.HTML &&
                (it.isMockTest || it.subjectId == FullLengthExamDefaults.SUBJECT_ID || it.title.contains("test", true) || it.title.contains("mock", true))
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    val filteredTests = remember(allHtmlTests, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) allHtmlTests
        else allHtmlTests.filter { it.title.lowercase().contains(q) || it.folderName.lowercase().contains(q) }
    }

    val practiceGroups = remember(mcqs, subjects) {
        mcqs.groupBy { Triple(it.subjectId, it.topicId, it.subtopicId) }
            .entries
            .map { (key, list) ->
                val subjName = subjects.firstOrNull { it.id == key.first }?.nameEn ?: "Subject"
                Pair(key, subjName to list.size)
            }
    }

    Scaffold(containerColor = theme.background) { inner ->
        Box(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1040.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    LearnTalentBrandHeader(
                        customLogoFile = null,
                        subtitle = "CBT Mock & Practice Tests"
                    )
                }

                // Navy-Royal Hero Banner
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = BrandColors.DeepNavy,
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(BrandColors.DeepNavy, BrandColors.RoyalBlue)
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Online Examination Hub",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = BrandColors.PureWhite,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${allHtmlTests.size} CBT Tests • ${fullLengthExams.size} Exam Categories • ${testResults.size} Attempts",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFD6E8FF)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = BrandColors.PremiumGold.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, BrandColors.PremiumGold.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "LIVE CBT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandColors.PremiumGold,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = onOpenAllFullLengthExams,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandColors.PremiumGold,
                                        contentColor = BrandColors.DeepNavy
                                    ),
                                    modifier = Modifier.testTag("tests_hub_full_length_btn")
                                ) {
                                    Icon(Icons.Rounded.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Full Length Exam Suites", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Search & Filter Pills
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search mock tests, exams, topics...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("tests_search_input")
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tabs = listOf("All", "Latest Mock Tests", "Full Length Exams", "Practice Mode")
                        items(tabs.indices.toList()) { idx ->
                            FilterChip(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                label = { Text(tabs[idx]) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = theme.primary,
                                    selectedLabelColor = theme.onPrimary
                                )
                            )
                        }
                    }
                }

                // Latest CBT Mock Tests Section
                if (selectedTab == 0 || selectedTab == 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Latest Mock Tests (${filteredTests.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Tactile3DBadge(text = "CBT Ready", color = theme.primary)
                        }
                    }

                    if (filteredTests.isEmpty()) {
                        item {
                            Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TactileIconOrb(icon = Icons.Rounded.Quiz, tint = theme.secondary, size = 44.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("कोई मॉक टेस्ट अभी उपलब्ध नहीं है", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                        Text(
                                            "Full Length Exams में जाकर नया .html मॉक टेस्ट अपलोड करें या Sample CBT टेस्ट बनाएं।",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = theme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(filteredTests, key = { it.id }) { testNote ->
                            val subjName = if (testNote.subjectId == FullLengthExamDefaults.SUBJECT_ID) {
                                fullLengthExams.firstOrNull { it.id == testNote.topicId }?.name ?: "Full Length Exam"
                            } else {
                                subjects.firstOrNull { it.id == testNote.subjectId }?.nameEn ?: "Mock Test"
                            }
                            val attempts = testResults.count { it.noteId == testNote.id }

                            Tactile3DCard(
                                accentColor = if (testNote.isShared) theme.goldAccent else theme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "mock_test_card_${testNote.id}"
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        TactileIconOrb(
                                            icon = Icons.Rounded.Quiz,
                                            tint = theme.primary,
                                            size = 44.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = testNote.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = theme.onSurface,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (testNote.isShared) {
                                                    Tactile3DBadge(text = "NEW", color = theme.goldAccent, icon = Icons.Rounded.AutoAwesome)
                                                }
                                            }
                                            Text(
                                                text = "$subjName • ${if (testNote.folderName.isNotBlank()) testNote.folderName else "CBT Exam"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = theme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Tactile3DBadge(
                                            text = if (testNote.subjectId == FullLengthExamDefaults.SUBJECT_ID) "60 min" else "30 min",
                                            color = theme.secondary,
                                            icon = Icons.Rounded.Schedule
                                        )
                                        if (attempts > 0) {
                                            Tactile3DBadge(text = "$attempts Done", color = theme.success)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onStartHtmlTest(testNote, false) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Practice", style = MaterialTheme.typography.labelMedium)
                                        }
                                        Button(
                                            onClick = { onStartHtmlTest(testNote, true) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = theme.primary,
                                                contentColor = theme.onPrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("start_mock_test_btn_${testNote.id}")
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Start Test", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Full Length Exam Categories
                if (selectedTab == 0 || selectedTab == 2) {
                    item {
                        Text(
                            text = "Full Length Exam Series (${fullLengthExams.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(fullLengthExams, key = { it.id }) { exam ->
                        val count = notes.count { it.subjectId == FullLengthExamDefaults.SUBJECT_ID && it.topicId == exam.id }
                        Tactile3DCard(
                            accentColor = theme.primary,
                            onClick = { onOpenFullLengthExam(exam) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    TactileIconOrb(icon = Icons.Rounded.Assignment, tint = theme.primary, size = 42.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(exam.name, style = MaterialTheme.typography.titleSmall, color = theme.onSurface, fontWeight = FontWeight.Bold)
                                        Text(exam.subtitle ?: "Full Length CBT Mock Papers", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Tactile3DBadge(text = "$count Tests", color = theme.secondary)
                            }
                        }
                    }
                }

                // Practice Test Subjects / MCQs
                if (selectedTab == 0 || selectedTab == 3) {
                    item {
                        Text(
                            text = "Topic-Wise Practice Quizzes",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (practiceGroups.isNotEmpty()) {
                        items(practiceGroups.take(10), key = { "${it.first.first}_${it.first.second}_${it.first.third}" }) { (key, info) ->
                            Tactile3DCard(
                                accentColor = theme.secondary,
                                onClick = { onStartPracticeMcqs(key.first, key.second, key.third) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TactileIconOrb(icon = Icons.Rounded.School, tint = theme.secondary, size = 40.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text("${info.first} Practice MCQs", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                            Text("${info.second} Questions ready offline", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                        }
                                    }
                                    Tactile3DBadge(text = "Practice", color = theme.success)
                                }
                            }
                        }
                    } else {
                        items(subjects.take(6), key = { it.id }) { subject ->
                            Tactile3DCard(
                                accentColor = SubjectVisuals.colorFor(subject.colorHex, theme.primary),
                                onClick = { onOpenSubjectTopics(subject) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TactileIconOrb(
                                            icon = SubjectVisuals.iconFor(subject.iconKey),
                                            tint = SubjectVisuals.colorFor(subject.colorHex, theme.primary),
                                            size = 38.dp
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(subject.nameEn, style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                            Text("Open subject chapters & add MCQs / HTML tests", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                        }
                                    }
                                    Tactile3DBadge(text = "Browse", color = theme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
