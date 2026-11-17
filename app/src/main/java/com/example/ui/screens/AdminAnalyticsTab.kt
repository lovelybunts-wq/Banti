package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyNote
import com.example.data.model.StudentSummary
import com.example.data.model.StudentUploadSubmission
import com.example.data.model.TestResult
import com.example.data.model.WhatsAppRequestRecord
import com.example.ui.MainViewModel
import com.example.ui.components.StudentAvatar
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.LocalStudyTheme

@Composable
internal fun AdminAnalyticsTab(
    students: List<StudentSummary>,
    sharedContent: List<StudyNote>,
) {
    val theme = LocalStudyTheme.current
    val weekAgo = System.currentTimeMillis() - (7L * 24L * 60L * 60L * 1000L)
    val activeStudents = students.count { it.lastActiveMillis >= weekAgo }
    val totalMockTests = sharedContent.count { it.isMockTest || it.noteType == "HTML" }
    val totalNotes = sharedContent.size - totalMockTests
    val totalDownloads = sharedContent.sumOf { it.downloadsCount } + students.sumOf { it.downloadsCount }
    val totalAttempts = students.sumOf { it.testAttemptsCount }
    val avgPct = if (students.isNotEmpty()) students.map { it.averageScorePct }.average() else 0.0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Total Students", "${students.size}", theme.primary, Modifier.weight(1f))
                MetricTile("Active (7d)", "$activeStudents", theme.success, Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Shared Notes", "$totalNotes", theme.secondary, Modifier.weight(1f))
                MetricTile("Mock Tests", "$totalMockTests", theme.tertiary, Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile("Downloads", "$totalDownloads", theme.primary, Modifier.weight(1f))
                MetricTile("Test Attempts", "$totalAttempts", theme.success, Modifier.weight(1f))
            }
        }
        item {
            Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = theme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Platform Average Score", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                    }
                    Text("${MainViewModel.formatNum(avgPct)}%", style = MaterialTheme.typography.titleLarge, color = theme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { (avgPct / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), color = theme.success)
            }
        }
        item { Text("Top Downloaded Materials", style = MaterialTheme.typography.titleSmall, color = theme.primary) }
        items(sharedContent.sortedByDescending { it.downloadsCount }.take(6), key = { it.id }) { item ->
            Tactile3DCard(accentColor = theme.secondary, lipDepth = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                        Text("${item.noteType} • v${item.version}", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                    }
                    Tactile3DBadge(text = "${item.downloadsCount} downloads", color = theme.secondary)
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val theme = LocalStudyTheme.current
    Tactile3DCard(accentColor = color, lipDepth = 3.dp, modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
    }
}

@Composable
internal fun AdminStudentsTab(
    students: List<StudentSummary>,
    onLoadStudentResults: (studentUid: String, onLoaded: (List<TestResult>) -> Unit) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var inspectedStudent by remember { mutableStateOf<StudentSummary?>(null) }
    var inspectedResults by remember { mutableStateOf<List<TestResult>>(emptyList()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (students.isEmpty()) {
            item { Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) { Text("अभी कोई छात्र रिकॉर्ड नहीं मिला।", color = theme.onSurfaceVariant) } }
        } else {
            items(students, key = { it.id.ifBlank { it.userId } }) { student ->
                Tactile3DCard(
                    accentColor = theme.primary,
                    onClick = {
                        inspectedStudent = student
                        inspectedResults = emptyList()
                        onLoadStudentResults(student.userId) { inspectedResults = it }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        StudentAvatar(photoUrl = student.photoUrl, displayName = student.displayName, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(student.displayName.ifBlank { "Student" }, style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                            Text(student.email, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tactile3DBadge(text = "${student.testAttemptsCount} Tests", color = theme.primary)
                                Tactile3DBadge(text = "Avg ${MainViewModel.formatNum(student.averageScorePct)}%", color = theme.success)
                            }
                        }
                    }
                }
            }
        }
    }

    val sel = inspectedStudent
    if (sel != null) {
        AlertDialog(
            onDismissRequest = { inspectedStudent = null }, containerColor = theme.surfaceElevated,
            title = { Text("${sel.displayName} • Results", color = theme.onSurface) },
            text = {
                if (inspectedResults.isEmpty()) {
                    Text("No submitted test results yet.", color = theme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        inspectedResults.take(10).forEach { r ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.testTitle, style = MaterialTheme.typography.bodySmall, color = theme.onSurface, modifier = Modifier.weight(1f))
                                Text("${MainViewModel.formatNum(r.scoreObtained)}/${MainViewModel.formatNum(r.totalMarks)} (${MainViewModel.formatNum(r.percentage)}%)", style = MaterialTheme.typography.labelMedium, color = theme.success)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { inspectedStudent = null }) { Text("Close") } }
        )
    }
}

@Composable
internal fun AdminRequestsTab(
    currentWhatsAppDigits: String,
    requests: List<WhatsAppRequestRecord>,
    allowStudentUploads: Boolean = true,
    pendingSubmissions: List<StudentUploadSubmission> = emptyList(),
    onToggleAllowStudentUploads: (Boolean) -> Unit = {},
    onApproveSubmission: (String) -> Unit = {},
    onRejectSubmission: (String) -> Unit = {},
    onSaveWhatsAppDigits: (String) -> Unit,
    onUpdateStatus: (requestId: String, status: String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var phoneInput by remember(currentWhatsAppDigits) { mutableStateOf(currentWhatsAppDigits) }
    val pendingApprovals = remember(pendingSubmissions) {
        pendingSubmissions.filter { it.status == "PENDING_APPROVAL" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Tactile3DCard(accentColor = theme.tertiary, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Student Personal Uploads", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                        Text(
                            "छात्रों के अपलोड डिफ़ॉल्ट रूप से उनके निजी Drive/डिवाइस में रहते हैं; Admin स्वीकृति के बाद ही सभी को दिखते हैं।",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = allowStudentUploads,
                        onCheckedChange = onToggleAllowStudentUploads,
                        modifier = Modifier.testTag("admin_allow_student_uploads_switch")
                    )
                }
            }
        }

        if (pendingApprovals.isNotEmpty()) {
            item {
                Text(
                    "Pending Student Upload Approvals (${pendingApprovals.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.tertiary
                )
            }
            items(pendingApprovals, key = { it.id }) { sub ->
                Tactile3DCard(accentColor = theme.tertiary, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.title, style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                            Text("By: ${sub.studentName} (${sub.studentEmail})", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                        }
                        Tactile3DBadge(text = sub.noteType, color = theme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onRejectSubmission(sub.id) },
                            modifier = Modifier.weight(1f).testTag("reject_submission_${sub.id}")
                        ) { Text("Decline") }
                        Button(
                            onClick = { onApproveSubmission(sub.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onPrimary),
                            modifier = Modifier.weight(1f).testTag("approve_submission_${sub.id}")
                        ) { Text("Approve & Publish") }
                    }
                }
            }
        }

        item {
            Tactile3DCard(accentColor = theme.secondary, modifier = Modifier.fillMaxWidth()) {
                Text("Official Admin WhatsApp Support Number", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = phoneInput, onValueChange = { phoneInput = it }, label = { Text("+91...") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onSaveWhatsAppDigits(phoneInput) }, colors = ButtonDefaults.buttonColors(containerColor = theme.secondary, contentColor = theme.onSecondary), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Save Support Number")
                }
            }
        }
        item { Text("Student WhatsApp Requests (${requests.size})", style = MaterialTheme.typography.titleSmall, color = theme.primary) }
        items(requests, key = { it.id }) { req ->
            Tactile3DCard(accentColor = if (req.status == "FULFILLED") theme.success else theme.primary, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${req.studentName} • ${req.requestType}", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                    Tactile3DBadge(text = req.status, color = if (req.status == "FULFILLED") theme.success else theme.secondary)
                }
                Text("Subject: ${req.subject} | Topic: ${req.topic} | Subtopic: ${req.subtopic}", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                if (req.extraMessage.isNotBlank()) Text("Msg: ${req.extraMessage}", style = MaterialTheme.typography.bodySmall, color = theme.onSurface)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onUpdateStatus(req.id, "REPLIED") }) { Text("Replied") }
                    Button(onClick = { onUpdateStatus(req.id, "FULFILLED") }, colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onPrimary)) { Text("Fulfilled") }
                }
            }
        }
    }
}
