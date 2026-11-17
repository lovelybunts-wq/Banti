package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.LocalAppSettings
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyLog
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.StudyRepository
import com.example.notifications.RevisionReminderReceiver
import com.example.notifications.RevisionReminderScheduler
import com.example.ui.components.LearnTalentLogo
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionPlannerScreen(
    studyLogsState: UiState<List<StudyLog>>,
    revisionDiaryState: UiState<List<RevisionDiaryEntry>>,
    subjectsState: UiState<List<Subject>>,
    profileState: UiState<UserProfile?>,
    localSettings: LocalAppSettings,
    customLogoFile: File?,
    onAddStudyPlanItem: (dateKey: String, entryType: String, title: String, subjectName: String?, notes: String?) -> Unit,
    onToggleCompleted: (StudyLog) -> Unit,
    onDeleteLog: (String) -> Unit,
    onAddDiaryEntry: (title: String, subjectName: String?, sourceName: String, content: String, pdfUri: Uri?) -> Unit,
    onUpdateDiaryEntry: (
        entryId: String,
        title: String,
        subjectName: String?,
        sourceName: String,
        content: String,
        pdfUri: Uri?,
        existingPdfTitle: String?,
        existingPdfFileName: String?,
        removePdf: Boolean,
    ) -> Unit,
    onDeleteDiaryEntry: (RevisionDiaryEntry) -> Unit,
    onOpenDiaryPdf: (localFileName: String, title: String) -> Unit,
    onSaveExamGoal: (examName: String, examDateMillis: Long, subjects: List<Subject>, autoPlan: Boolean) -> Unit,
    onSaveReminderPrefs: (enabled: Boolean, hour: Int, minute: Int, yesterdaySummary: String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    val context = LocalContext.current

    val logs = (studyLogsState as? UiState.Success)?.data ?: emptyList()
    val diaryEntries = (revisionDiaryState as? UiState.Success)?.data ?: emptyList()
    val subjects = (subjectsState as? UiState.Success)?.data ?: emptyList()
    val profile = (profileState as? UiState.Success)?.data

    val todayKey = remember { StudyRepository.todayKey() }
    val yesterdayKey = remember { StudyRepository.yesterdayKey() }
    val tomorrowKey = remember { StudyRepository.tomorrowKey() }

    var selectedPlanDateKey by remember { mutableStateOf(todayKey) }
    var showAddPlanDialog by remember { mutableStateOf(false) }
    var showExamGoalDialog by remember { mutableStateOf(false) }
    var showPlanDatePicker by remember { mutableStateOf(false) }
    var showAddDiaryDialog by remember { mutableStateOf(false) }
    var editingDiaryEntry by remember { mutableStateOf<RevisionDiaryEntry?>(null) }

    val yesterdayStudied = remember(logs, yesterdayKey) {
        logs.filter { it.dateKey == yesterdayKey && it.entryType == "STUDIED" }
    }
    val todayStudied = remember(logs, todayKey) {
        logs.filter { it.dateKey == todayKey && it.entryType == "STUDIED" }
    }
    val plannedForSelectedDate = remember(logs, selectedPlanDateKey) {
        logs.filter { it.dateKey == selectedPlanDateKey && it.entryType == "PLANNED" }
    }

    val yesterdaySummaryText = remember(yesterdayStudied, todayStudied) {
        when {
            yesterdayStudied.isNotEmpty() -> yesterdayStudied.joinToString(", ") { it.title }
            todayStudied.isNotEmpty() -> "आज पढ़ा: " + todayStudied.joinToString(", ") { it.title }
            else -> "आज का कोई एक टॉपिक 'आज पढ़ा' मार्क करें ताकि कल रिवीजन नोटिफिकेशन मिले।"
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            RevisionReminderReceiver.showRevisionNotification(context, yesterdaySummaryText)
        }
    }

    fun triggerNotification(scheduleDaily: Boolean) {
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (scheduleDaily) {
                RevisionReminderScheduler.scheduleDailyReminder(context, 8, 0, yesterdaySummaryText)
            } else {
                RevisionReminderReceiver.showRevisionNotification(context, yesterdaySummaryText)
            }
        }
    }

    Scaffold(
        containerColor = theme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 920.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LearnTalentLogo(customLogoFile = customLogoFile, size = 38.dp, cornerRadius = 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.brand_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = theme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "रिवीजन डायरी • कल क्या पढ़ा था • एग्जाम प्लानर",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 0. Revision Diary Folder Card (रिवीजन डायरी + सोर्स नाम)
                item {
                    Tactile3DCard(
                        accentColor = theme.tertiary,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "revision_diary_folder_card"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                TactileIconOrb(icon = Icons.Rounded.Book, tint = theme.tertiary, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "रिवीजन डायरी (Revision Diary)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.tertiary
                                    )
                                    Text(
                                        text = "रिवीजन सामग्री एवं सोर्स (Book/Video/Teacher) लिखें",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = { showAddDiaryDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.tertiary, contentColor = theme.onPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("add_diary_entry_btn")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("लिखें")
                            }
                        }

                        if (diaryEntries.isEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "डायरी खाली है। '+ लिखें' दबाकर रिवीजन के पॉइंट्स और सोर्स का नाम सेव करें।",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(diaryEntries, key = { "diary_${it.id}" }) { entry ->
                    Tactile3DCard(
                        accentColor = theme.tertiary,
                        lipDepth = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "diary_entry_card_${entry.id}"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!entry.subjectName.isNullOrBlank()) {
                                        Tactile3DBadge(text = entry.subjectName, color = theme.primary)
                                    }
                                    if (entry.sourceName.isNotBlank()) {
                                        Tactile3DBadge(text = "Source: ${entry.sourceName}", color = theme.secondary)
                                    }
                                }
                                if (entry.content.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = entry.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.onSurfaceVariant
                                    )
                                }
                                if (!entry.pdfLocalFileName.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            onOpenDiaryPdf(
                                                entry.pdfLocalFileName,
                                                entry.pdfTitle ?: entry.title
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("open_diary_pdf_${entry.id}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.PictureAsPdf,
                                            contentDescription = "Open Revision PDF",
                                            tint = theme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = entry.pdfTitle ?: "Open Attached PDF",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = theme.onSurface
                                        )
                                    }
                                }
                            }
                            Row {
                                IconButton(onClick = { editingDiaryEntry = entry }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit diary", tint = theme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteDiaryEntry(entry) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete diary", tint = theme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // 1. Yesterday's Revision Card
                item {
                    Tactile3DCard(
                        accentColor = theme.secondary,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "yesterday_studied_card"
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TactileIconOrb(icon = Icons.Rounded.History, tint = theme.secondary, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.studied_yesterday_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.secondary
                                )
                                Text(
                                    text = yesterdayKey,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (yesterdayStudied.isEmpty()) {
                            Text(
                                text = "कल ($yesterdayKey) का कोई रिकॉर्ड नहीं मिला। आज के टॉपिक्स पर 'आज पढ़ा' दबाएं या नीचे लॉग जोड़ें।",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                yesterdayStudied.forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = theme.success,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "${item.title}${if (!item.subjectName.isNullOrBlank()) " (${item.subjectName})" else ""}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = theme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Exam Countdown & Auto-Plan Card
                item {
                    val examMillis = profile?.examDateMillis
                    val daysLeft = examMillis?.let {
                        ((it - System.currentTimeMillis()) / (1000L * 60L * 60L * 24L)).toInt()
                    }
                    val dateFormatted = examMillis?.let {
                        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(it))
                    }
                    Tactile3DCard(
                        accentColor = theme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "exam_countdown_card"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                TactileIconOrb(icon = Icons.Rounded.EventAvailable, tint = theme.primary, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = profile?.examName ?: "अपना एग्जाम और डेट सेट करें",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onSurface
                                    )
                                    Text(
                                        text = if (dateFormatted != null) "Target: $dateFormatted" else "एग्जाम डेट के हिसाब से पूरा प्लान बनाएं",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.onSurfaceVariant
                                    )
                                }
                            }
                            if (daysLeft != null) {
                                Tactile3DBadge(
                                    text = if (daysLeft >= 0) "$daysLeft Days" else "Done",
                                    color = theme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { showExamGoalDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("set_exam_goal_btn")
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Set Exam Date & Auto-Create Plan")
                        }
                    }
                }

                // 3. Revision Notification Reminder Card
                item {
                    Tactile3DCard(accentColor = theme.tertiary, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                TactileIconOrb(icon = Icons.Rounded.NotificationsActive, tint = theme.tertiary, size = 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Daily Revision Reminder", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                    Text(
                                        "सुबह 8:00 बजे नोटिफिकेशन: कल तुमने यह पढ़ा था",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = localSettings.reminderEnabled,
                                onCheckedChange = { enabled ->
                                    onSaveReminderPrefs(enabled, 8, 0, yesterdaySummaryText)
                                    if (enabled) triggerNotification(scheduleDaily = true)
                                    else RevisionReminderScheduler.cancelReminder(context)
                                },
                                modifier = Modifier.testTag("reminder_toggle")
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { triggerNotification(scheduleDaily = false) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("test_notification_btn")
                        ) {
                            Text(stringResource(R.string.test_notification_btn), color = theme.onSurface)
                        }
                    }
                }

                // 4. Today / Tomorrow / Custom Date Plan Selector
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.today_tomorrow_plan_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.onSurface
                        )
                        OutlinedButton(
                            onClick = { showAddPlanDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("add_plan_topic_btn")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Topic", color = theme.onSurface)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedPlanDateKey == todayKey,
                            onClick = { selectedPlanDateKey = todayKey },
                            label = { Text("Today") }
                        )
                        FilterChip(
                            selected = selectedPlanDateKey == tomorrowKey,
                            onClick = { selectedPlanDateKey = tomorrowKey },
                            label = { Text("Tomorrow (कल)") }
                        )
                        FilterChip(
                            selected = selectedPlanDateKey != todayKey && selectedPlanDateKey != tomorrowKey,
                            onClick = { showPlanDatePicker = true },
                            label = {
                                Text(
                                    if (selectedPlanDateKey != todayKey && selectedPlanDateKey != tomorrowKey) selectedPlanDateKey
                                    else "Pick Date"
                                )
                            }
                        )
                    }
                }

                if (plannedForSelectedDate.isEmpty()) {
                    item {
                        Tactile3DCard(accentColor = theme.outline, lipDepth = 3.dp, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "$selectedPlanDateKey के लिए कोई प्लान नहीं जुड़ा है। ऊपर '+ Add Topic' से जोड़ें।",
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(plannedForSelectedDate, key = { it.id }) { log ->
                        Tactile3DCard(
                            accentColor = if (log.completed) theme.success else theme.primary,
                            lipDepth = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { onToggleCompleted(log) }) {
                                    Icon(
                                        imageVector = if (log.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = "Toggle done",
                                        tint = if (log.completed) theme.success else theme.onSurfaceVariant
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.title, style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                    if (!log.subjectName.isNullOrBlank()) {
                                        Text(log.subjectName, style = MaterialTheme.typography.labelSmall, color = theme.primary)
                                    }
                                    if (!log.notes.isNullOrBlank()) {
                                        Text(log.notes, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { onDeleteLog(log.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete plan item", tint = theme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDiaryDialog || editingDiaryEntry != null) {
        val editing = editingDiaryEntry
        var title by remember(editing) { mutableStateOf(editing?.title ?: "") }
        var subjectName by remember(editing) { mutableStateOf(editing?.subjectName ?: "") }
        var sourceName by remember(editing) { mutableStateOf(editing?.sourceName ?: "") }
        var content by remember(editing) { mutableStateOf(editing?.content ?: "") }
        var pickedPdfUri by remember(editing) { mutableStateOf<Uri?>(null) }
        var pickedPdfDisplayName by remember(editing) { mutableStateOf(editing?.pdfTitle) }
        var removeExistingPdf by remember(editing) { mutableStateOf(false) }

        val pdfPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                pickedPdfUri = uri
                removeExistingPdf = false
                val displayName = runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) c.getString(idx) else null
                        } else null
                    }
                }.getOrNull() ?: "Revision.pdf"
                pickedPdfDisplayName = displayName
                if (title.isBlank()) {
                    title = displayName.substringBeforeLast(".")
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showAddDiaryDialog = false
                editingDiaryEntry = null
            },
            containerColor = theme.surfaceElevated,
            title = {
                Text(
                    if (editing != null) "रिवीजन डायरी अपडेट करें" else "रिवीजन डायरी में लिखें / PDF जोड़ें",
                    color = theme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("टॉपिक / शीर्षक (Title)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("diary_title_input")
                    )
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject (विषय - Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        label = { Text("सोर्स का नाम (Book / NCERT / Video)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("diary_source_input")
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("रिवीजन सामग्री (Notes / Key Points - Optional if PDF)") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().testTag("diary_content_input")
                    )
                    OutlinedButton(
                        onClick = { pdfPicker.launch(arrayOf("application/pdf", "*/*")) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("pick_diary_pdf_btn")
                    ) {
                        Icon(
                            Icons.Rounded.UploadFile,
                            contentDescription = null,
                            tint = theme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (pickedPdfDisplayName.isNullOrBlank()) "Upload PDF (पीडीएफ जोड़ें)" else "Change PDF (PDF बदलें)",
                            color = theme.onSurface
                        )
                    }
                    if (!pickedPdfDisplayName.isNullOrBlank() && !removeExistingPdf) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    tint = theme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = pickedPdfDisplayName ?: "PDF Attached",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    pickedPdfUri = null
                                    pickedPdfDisplayName = null
                                    removeExistingPdf = true
                                }
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove PDF", tint = theme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            if (editing != null) {
                                onUpdateDiaryEntry(
                                    editing.id,
                                    title,
                                    subjectName.ifBlank { null },
                                    sourceName,
                                    content,
                                    pickedPdfUri,
                                    editing.pdfTitle,
                                    editing.pdfLocalFileName,
                                    removeExistingPdf
                                )
                            } else {
                                onAddDiaryEntry(
                                    title,
                                    subjectName.ifBlank { null },
                                    sourceName,
                                    content,
                                    pickedPdfUri
                                )
                            }
                            showAddDiaryDialog = false
                            editingDiaryEntry = null
                        }
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.tertiary, contentColor = theme.onPrimary),
                    modifier = Modifier.testTag("save_diary_btn")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDiaryDialog = false
                    editingDiaryEntry = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showPlanDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showPlanDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        selectedPlanDateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
                    }
                    showPlanDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showPlanDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }

    if (showAddPlanDialog) {
        var title by remember { mutableStateOf("") }
        var subjectName by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var markAsStudiedToday by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddPlanDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("स्टडी प्लान जोड़ें ($selectedPlanDateKey)", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Topic to Read / Revise") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("plan_topic_input")
                    )
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes / Target Pages (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = markAsStudiedToday, onCheckedChange = { markAsStudiedToday = it })
                        Text("Log as 'Already Studied'", style = MaterialTheme.typography.bodySmall, color = theme.onSurface)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onAddStudyPlanItem(
                                selectedPlanDateKey,
                                if (markAsStudiedToday) "STUDIED" else "PLANNED",
                                title,
                                subjectName.ifBlank { null },
                                notes.ifBlank { null }
                            )
                            showAddPlanDialog = false
                        }
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddPlanDialog = false }) { Text("Cancel") } }
        )
    }

    if (showExamGoalDialog) {
        var examName by remember { mutableStateOf(profile?.examName ?: "") }
        var pickedMillis by remember { mutableStateOf(profile?.examDateMillis ?: (System.currentTimeMillis() + 86400000L * 30)) }
        var showDatePicker by remember { mutableStateOf(false) }
        var autoPlan by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showExamGoalDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("एग्जाम डेट सेट करें", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = examName,
                        onValueChange = { examName = it },
                        label = { Text("Exam Name (e.g. SSC CGL, UPSC, Board)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Date: ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(pickedMillis))}",
                            color = theme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoPlan, onCheckedChange = { autoPlan = it })
                        Text("Auto-generate 7-day subject revision plan", style = MaterialTheme.typography.bodySmall, color = theme.onSurface)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveExamGoal(examName.ifBlank { "Target Exam" }, pickedMillis, subjects, autoPlan)
                        showExamGoalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
                ) { Text("Save Plan") }
            },
            dismissButton = { TextButton(onClick = { showExamGoalDialog = false }) { Text("Cancel") } }
        )

        if (showDatePicker) {
            val state = rememberDatePickerState(initialSelectedDateMillis = pickedMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { pickedMillis = it }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
            ) {
                DatePicker(state = state)
            }
        }
    }
}
