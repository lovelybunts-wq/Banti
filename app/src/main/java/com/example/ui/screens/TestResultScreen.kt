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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.model.QuestionAttemptRecord
import com.example.data.model.TestResult
import com.example.ui.MainViewModel
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

private enum class ResultFilter(val label: String) {
    ALL("All"),
    WRONG("Wrong Only"),
    CORRECT("Correct"),
    UNATTEMPTED("Unattempted"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestResultScreen(
    result: TestResult?,
    questions: List<QuestionAttemptRecord>,
    bookmarks: List<BookmarkItem>,
    onBack: () -> Unit,
    onRetryTest: (() -> Unit)? = null,
    onExportPdf: (TestResult, Boolean) -> Unit,
    onBookmarkQuestion: (QuestionAttemptRecord, BookmarkType) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var filter by remember { mutableStateOf(ResultFilter.ALL) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("result_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(result?.testTitle ?: "Test Result", style = MaterialTheme.typography.titleLarge, color = theme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    ) { innerPadding ->
        if (result == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Result not found.", color = theme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val filteredQs = remember(questions, filter) {
            when (filter) {
                ResultFilter.ALL -> questions
                ResultFilter.WRONG -> questions.filter { it.isAttempted && !it.isCorrect }
                ResultFilter.CORRECT -> questions.filter { it.isCorrect }
                ResultFilter.UNATTEMPTED -> questions.filter { !it.isAttempted }
            }
        }
        val pctFraction = (result.percentage / 100.0).toFloat().coerceIn(0f, 1f)

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 920.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Score Hero Card
                item {
                    Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth(), testTag = "test_result_hero_card") {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TactileIconOrb(icon = Icons.Rounded.EmojiEvents, tint = theme.primary, size = 54.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Score: ${MainViewModel.formatNum(result.scoreObtained)} / ${MainViewModel.formatNum(result.totalMarks)}", style = MaterialTheme.typography.headlineSmall, color = theme.primary, fontWeight = FontWeight.Bold)
                                Text("Accuracy: ${MainViewModel.formatNum(result.percentage)}% • Mode: ${result.testMode} • Date: ${result.takenDate}", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { pctFraction }, modifier = Modifier.fillMaxWidth().height(8.dp), color = theme.success)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatPill("Total", "${result.totalQuestions}", theme.primary, Modifier.weight(1f))
                            StatPill("Right", "${result.correctCount}", theme.success, Modifier.weight(1f))
                            StatPill("Wrong", "${result.wrongCount}", theme.error, Modifier.weight(1f))
                            StatPill("Left", "${result.unattemptedCount}", theme.secondary, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onExportPdf(result, false) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("export_result_pdf_btn")
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Export PDF")
                            }
                            OutlinedButton(
                                onClick = { onExportPdf(result, true) },
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).testTag("share_result_pdf_btn")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Share PDF", color = theme.onSurface)
                            }
                        }
                        if (onRetryTest != null) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onRetryTest, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Replay, contentDescription = null, tint = theme.secondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp)); Text("Retry Test (पुनः प्रयास करें)", color = theme.secondary)
                            }
                        }
                    }
                }

                // Filters
                if (questions.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ResultFilter.entries) { f ->
                                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                            }
                        }
                    }

                    items(filteredQs, key = { "${it.questionNumber}_${it.questionText.hashCode()}" }) { q ->
                        val statusColor = when {
                            !q.isAttempted -> theme.secondary
                            q.isCorrect -> theme.success
                            else -> theme.error
                        }
                        val statusIcon = when {
                            !q.isAttempted -> Icons.Rounded.RadioButtonUnchecked
                            q.isCorrect -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.Cancel
                        }
                        val targetType = if (q.isAttempted && !q.isCorrect) BookmarkType.WRONG_QUESTION else BookmarkType.IMPORTANT_QUESTION
                        val isSaved = bookmarks.any { it.title == q.questionText && (it.bookmarkType == BookmarkType.IMPORTANT_QUESTION.name || it.bookmarkType == BookmarkType.WRONG_QUESTION.name) }

                        Tactile3DCard(accentColor = statusColor, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Q${q.questionNumber}. ${q.questionText}", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                }
                                IconButton(onClick = { onBookmarkQuestion(q, targetType) }) {
                                    Icon(if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = "Bookmark question", tint = if (isSaved) statusColor else theme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            if (q.selectedOption.isNotBlank()) {
                                Text("Your Answer: ${q.selectedOption}", style = MaterialTheme.typography.bodySmall, color = statusColor)
                            } else {
                                Text("Your Answer: Not Attempted", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            }
                            if (q.correctOption.isNotBlank()) {
                                Text("Correct Answer: ${q.correctOption}", style = MaterialTheme.typography.bodySmall, color = theme.success, fontWeight = FontWeight.SemiBold)
                            }
                            if (q.explanation.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("Explanation: ${q.explanation}", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    item {
                        Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                            Text("Summary: ${result.summaryText ?: "Completed"}", style = MaterialTheme.typography.bodyMedium, color = theme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Tactile3DBadge(text = "$label: $value", color = color, modifier = modifier)
}
