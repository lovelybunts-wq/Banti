package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FullLengthExamDefaults
import com.example.data.model.StudyLog
import com.example.data.model.Subject
import com.example.data.model.TestResult
import com.example.ui.MainViewModel
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.BrandColors
import com.example.ui.theme.LocalStudyTheme

@Composable
fun ResultsAnalyticsScreen(
    testResults: List<TestResult>,
    subjects: List<Subject>,
    studyLogs: List<StudyLog>,
    onOpenTestResult: (String) -> Unit,
    onExportPdf: (TestResult, share: Boolean) -> Unit,
    onGoToTests: () -> Unit,
) {
    val theme = LocalStudyTheme.current

    val avgPct = remember(testResults) {
        if (testResults.isEmpty()) 0.0 else testResults.map { it.percentage }.average()
    }
    val bestPct = remember(testResults) {
        testResults.maxOfOrNull { it.percentage } ?: 0.0
    }
    val totalQuestionsSolved = remember(testResults) {
        testResults.sumOf { it.attemptedCount.takeIf { c -> c > 0 } ?: it.totalQuestions }
    }
    val totalCorrect = remember(testResults) {
        testResults.sumOf { it.correctCount }
    }
    val studiedCount = remember(studyLogs) {
        studyLogs.count { it.entryType == "STUDIED" || it.completed }
    }

    val subjectBreakdown = remember(testResults, subjects) {
        testResults
            .groupBy { it.subjectId }
            .map { (subjId, results) ->
                val name = when (subjId) {
                    FullLengthExamDefaults.SUBJECT_ID -> "Full Length Exams"
                    else -> subjects.firstOrNull { it.id == subjId }?.nameEn ?: "General Studies"
                }
                Triple(name, results.size, results.map { it.percentage }.average())
            }
            .sortedByDescending { it.third }
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
                        subtitle = "Performance Analysis & Results"
                    )
                }

                // Hero Performance Summary Card with Circular Progress Ring
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("results_hero_card"),
                        shape = RoundedCornerShape(22.dp),
                        color = BrandColors.DeepNavy,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(BrandColors.DeepNavy, BrandColors.RoyalBlue)
                                    )
                                )
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Overall Accuracy & Progress",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BrandColors.PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "${testResults.size} Tests • $totalQuestionsSolved Questions • $studiedCount Topics Studied",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD6E8FF)
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BrandColors.PureWhite.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Best: ${MainViewModel.formatNum(bestPct)}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandColors.PremiumGold,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BrandColors.PureWhite.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Correct: $totalCorrect",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF86EFAC),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier.size(86.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val sweep = ((avgPct.coerceIn(0.0, 100.0)) / 100.0 * 360.0).toFloat()
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val stroke = 9.dp.toPx()
                                    drawArc(
                                        color = Color.White.copy(alpha = 0.18f),
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = BrandColors.PremiumGold,
                                        startAngle = -90f,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                    )
                                }
                                Text(
                                    text = "${avgPct.toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BrandColors.PureWhite,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // Quick Metric Grid
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Assessment,
                            tint = theme.primary,
                            value = "${testResults.size}",
                            label = "Tests Attempted"
                        )
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.EmojiEvents,
                            tint = theme.goldAccent,
                            value = "${MainViewModel.formatNum(avgPct)}%",
                            label = "Avg Score"
                        )
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.CheckCircle,
                            tint = theme.success,
                            value = "$studiedCount",
                            label = "Topics Studied"
                        )
                    }
                }

                // Subject Performance Analysis
                if (subjectBreakdown.isNotEmpty()) {
                    item {
                        Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TactileIconOrb(icon = Icons.Rounded.Analytics, tint = theme.secondary, size = 38.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Subject Performance Breakdown", style = MaterialTheme.typography.titleMedium, color = theme.onSurface, fontWeight = FontWeight.Bold)
                                    Text("विषयवार औसत स्कोर और सुधार क्षेत्र", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                subjectBreakdown.forEach { (name, count, pct) ->
                                    val barColor = when {
                                        pct >= 75.0 -> theme.success
                                        pct >= 50.0 -> theme.primary
                                        else -> theme.error
                                    }
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("$name ($count tests)", style = MaterialTheme.typography.labelLarge, color = theme.onSurface)
                                            Text("${MainViewModel.formatNum(pct)}%", style = MaterialTheme.typography.labelLarge, color = barColor, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                                            color = barColor,
                                            trackColor = theme.outline.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Test Results History (${testResults.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Tactile3DBadge(text = "Synced", color = theme.success, icon = Icons.Rounded.TrendingUp)
                    }
                }

                if (testResults.isEmpty()) {
                    item {
                        Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TactileIconOrb(icon = Icons.Rounded.Assessment, tint = theme.primary, size = 54.dp)
                                Spacer(Modifier.height(10.dp))
                                Text("अभी तक कोई टेस्ट रिजल्ट नहीं है", style = MaterialTheme.typography.titleMedium, color = theme.onSurface, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tests टैब से कोई भी मॉक टेस्ट या Practice Quiz शुरू करें — सबमिट करते ही पूरा विश्लेषण यहाँ दिखेगा।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = onGoToTests,
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Go to Mock Tests")
                                }
                            }
                        }
                    }
                } else {
                    items(testResults, key = { it.id }) { res ->
                        val badgeColor = when {
                            res.percentage >= 75.0 -> theme.success
                            res.percentage >= 50.0 -> theme.primary
                            else -> theme.error
                        }
                        Tactile3DCard(
                            accentColor = badgeColor,
                            onClick = { onOpenTestResult(res.id) },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "result_history_card_${res.id}"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = res.testTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = theme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${res.takenDate.ifBlank { "Recent" }} • ${res.testMode} • ${MainViewModel.formatNum(res.scoreObtained)}/${MainViewModel.formatNum(res.totalMarks)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Tactile3DBadge(text = "${MainViewModel.formatNum(res.percentage)}%", color = badgeColor)
                                    IconButton(onClick = { onExportPdf(res, false) }) {
                                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export PDF Report", tint = theme.primary)
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

@Composable
private fun StatMiniCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    value: String,
    label: String,
) {
    val theme = LocalStudyTheme.current
    Tactile3DCard(
        modifier = modifier,
        accentColor = tint,
        lipDepth = 3.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            TactileIconOrb(icon = icon, tint = tint, size = 34.dp)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = theme.onSurface, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
