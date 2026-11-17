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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatsAppRequestDialog(
    initialSubject: String = "",
    initialTopic: String = "",
    initialSubtopic: String = "",
    savedAdminNumber: String = "",
    onSaveAdminNumber: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current

    var adminNumber by remember { mutableStateOf(savedAdminNumber) }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = adminNumber,
                    onValueChange = { adminNumber = it },
                    label = { Text("Admin WhatsApp No. (e.g. +9198XXXXXXXX)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("whatsapp_admin_number_input")
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
                    val cleanDigits = adminNumber.filter { it.isDigit() }
                    if (cleanDigits.length < 10) {
                        onShowMessage("कृपया सही एडमिन WhatsApp नंबर डालें (कम से कम 10 अंक)")
                        return@Button
                    }
                    val finalDigits = if (cleanDigits.length == 10) "91$cleanDigits" else cleanDigits
                    onSaveAdminNumber(adminNumber.trim())

                    val msg = buildString {
                        append("नमस्ते Admin,\n")
                        append("मुझे Learn Talent ऐप के लिए *$requestType* चाहिए।\n")
                        if (subject.isNotBlank()) append("• विषय (Subject): ${subject.trim()}\n")
                        if (topic.isNotBlank()) append("• टॉपिक (Topic): ${topic.trim()}\n")
                        if (subtopic.isNotBlank()) append("• सब-टॉपिक (Sub-topic): ${subtopic.trim()}\n")
                        if (extraNote.isNotBlank()) append("• संदेश: ${extraNote.trim()}\n")
                    }
                    openWhatsAppChat(context, finalDigits, msg, onShowMessage)
                    onDismiss()
                },
                enabled = subject.isNotBlank() && adminNumber.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = theme.success, contentColor = theme.onPrimary),
                modifier = Modifier.testTag("whatsapp_send_btn")
            ) {
                Row {
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

private fun openWhatsAppChat(
    context: Context,
    phoneDigits: String,
    message: String,
    onError: (String) -> Unit,
) {
    val url = "https://wa.me/$phoneDigits?text=${Uri.encode(message)}"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        onError("WhatsApp या ब्राउज़र नहीं खुल सका।")
    }
}
