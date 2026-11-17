package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.UserPreferencesManager
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatsAppRequestDialog(
    studentName: String = "Student",
    initialSubject: String = "",
    initialTopic: String = "",
    initialSubtopic: String = "",
    savedAdminNumber: String = "",
    onRecordRequest: (requestType: String, subject: String, topic: String, subtopic: String, extraMessage: String) -> Unit = { _, _, _, _, _ -> },
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current

    val resolvedDigits = remember(savedAdminNumber) {
        UserPreferencesManager.normalizePhoneDigits(savedAdminNumber)
            .ifBlank { UserPreferencesManager.DEFAULT_ADMIN_WHATSAPP_DIGITS }
    }

    var senderName by remember(studentName) { mutableStateOf(studentName.ifBlank { "Student" }) }
    var subject by remember { mutableStateOf(initialSubject) }
    var topic by remember { mutableStateOf(initialTopic) }
    var subtopic by remember { mutableStateOf(initialSubtopic) }
    var requestType by remember { mutableStateOf("HTML Mock Test") }
    var extraNote by remember { mutableStateOf("") }

    val types = listOf("HTML Mock Test", "Notes (PDF / Image)", "Practice MCQs", "All Material")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.surfaceElevated,
        title = {
            Text(
                text = "एडमिन से टेस्ट / नोट्स मांगें (WhatsApp)",
                color = theme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = theme.success
                    )
                    Text(
                        text = "Official Learn Talent Support (एडमिन नंबर गोपनीय व सुरक्षित है)",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.success
                    )
                }
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Student Name (आपका नाम)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (विषय)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("whatsapp_subject_input")
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic (टॉपिक)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subtopic,
                    onValueChange = { subtopic = it },
                    label = { Text("Sub-topic (सब-टॉपिक)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("क्या चाहिए? (Request Type)", style = MaterialTheme.typography.labelMedium, color = theme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.forEach { item ->
                        FilterChip(
                            selected = requestType == item,
                            onClick = { requestType = item },
                            label = { Text(item) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = theme.primaryContainer,
                                selectedLabelColor = theme.primary
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = extraNote,
                    onValueChange = { extraNote = it },
                    label = { Text("Extra Message (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanSubj = subject.trim()
                    val cleanTop = topic.trim()
                    val cleanSub = subtopic.trim()
                    val cleanExtra = extraNote.trim()
                    val msg = buildWhatsAppRequestMessage(
                        studentName = senderName.trim().ifBlank { "Student" },
                        requestType = requestType,
                        subject = cleanSubj,
                        topic = cleanTop,
                        subtopic = cleanSub,
                        extraNote = cleanExtra
                    )
                    onRecordRequest(requestType, cleanSubj, cleanTop, cleanSub, cleanExtra)
                    openWhatsAppChat(context, resolvedDigits, msg, onShowMessage)
                    onDismiss()
                },
                enabled = subject.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onPrimary),
                modifier = Modifier.testTag("whatsapp_send_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Send WhatsApp")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun buildWhatsAppRequestMessage(
    studentName: String,
    requestType: String,
    subject: String,
    topic: String,
    subtopic: String,
    extraNote: String,
): String = buildString {
    append("नमस्ते Admin,\n")
    append("• छात्र (Student): $studentName\n")
    append("मुझे *LEARN TALENT by Bunts* ऐप के लिए *$requestType* चाहिए।\n")
    if (subject.isNotBlank()) append("• विषय (Subject): $subject\n")
    if (topic.isNotBlank()) append("• टॉपिक (Topic): $topic\n")
    if (subtopic.isNotBlank()) append("• सब-टॉपिक (Sub-topic): $subtopic\n")
    if (extraNote.isNotBlank()) append("• संदेश: $extraNote\n")
}

private fun openWhatsAppChat(
    context: Context,
    phoneDigits: String,
    message: String,
    onError: (String) -> Unit,
) {
    val encodedMsg = Uri.encode(message)
    val nativeIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("whatsapp://send?phone=$phoneDigits&text=$encodedMsg")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (nativeIntent.resolveActivity(context.packageManager) != null) {
        val launched = runCatching { context.startActivity(nativeIntent) }.isSuccess
        if (launched) return
    }

    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://api.whatsapp.com/send?phone=$phoneDigits&text=$encodedMsg")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(webIntent)
    }.onFailure {
        onError("WhatsApp या ब्राउज़र नहीं खुल सका। कृपया WhatsApp इंस्टॉल जांचें।")
    }
}
