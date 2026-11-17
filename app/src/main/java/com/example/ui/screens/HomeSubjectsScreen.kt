package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.StudyDateUtils
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.FullLengthExam
import com.example.data.model.FullLengthExamDefaults
import com.example.data.model.NoteType
import com.example.data.model.PersonalNote
import com.example.data.model.RecentActivityItem
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyLog
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.DriveSyncUiState
import com.example.ui.MainViewModel
import com.example.ui.components.LearnTalentBrandHeader
import com.example.ui.components.StudentAvatar
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.BrandColors
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSubjectsScreen(
    subjectsState: UiState<List<Subject>>,
    topicsState: UiState<List<Topic>>,
    notesState: UiState<List<StudyNote>>,
    fullLengthExamsState: UiState<List<FullLengthExam>>,
    testResults: List<TestResult> = emptyList(),
    studyLogsState: UiState<List<StudyLog>>,
    revisionDiaryState: UiState<List<RevisionDiaryEntry>>,
    personalNotesState: UiState<List<PersonalNote>>,
    bookmarksState: UiState<List<BookmarkItem>>,
    recentActivities: List<RecentActivityItem>,
    profileState: UiState<UserProfile?>,
    displayName: String,
    photoUrl: String?,
    isAdminUser: Boolean,
    isOfflineAccount: Boolean,
    isNetworkOnline: Boolean = true,
    pendingSyncCount: Int = 0,
    driveSyncState: DriveSyncUiState,
    onSignInOrSyncNow: () -> Unit,
    onToggleLightDark: () -> Unit,
    onSubjectClick: (Subject) -> Unit,
    onOpenFullLengthTests: () -> Unit,
    onStartMockTest: (StudyNote) -> Unit = {},
    onOpenRecentActivity: (RecentActivityItem) -> Unit,
    onOpenTestsTab: () -> Unit = onOpenFullLengthTests,
    onOpenStudyTab: () -> Unit = {},
    onOpenResultsTab: () -> Unit = {},
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
    var showDrawerSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

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

    val latestMockTests = remember(notes) {
        notes.filter {
            it.resolvedType() == NoteType.HTML &&
                (it.isMockTest || it.subjectId == FullLengthExamDefaults.SUBJECT_ID || it.title.contains("mock", true) || it.title.contains("test", true))
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    val avgScorePct = remember(testResults) {
        if (testResults.isEmpty()) 0.0 else testResults.map { it.percentage }.average()
    }
    val studiedTopicsCount = remember(logs) {
        logs.count { it.entryType == "STUDIED" || it.completed }
    }
    val overallProgressPct = remember(testResults, studiedTopicsCount, avgScorePct) {
        if (testResults.isEmpty() && studiedTopicsCount == 0) 12
        else ((avgScorePct * 0.65) + ((studiedTopicsCount.coerceAtMost(20) / 20.0) * 35.0)).toInt().coerceIn(5, 100)
    }

    val yesterdayKey = remember { StudyDateUtils.yesterdayKey() }
    val yesterdayLogs = remember(logs, yesterdayKey) {
        logs.filter { it.dateKey == yesterdayKey && it.entryType == "STUDIED" }
    }
    val daysLeftText = remember(profile) {
        val target = profile?.examDateMillis ?: return@remember null
        val diff = ((target - System.currentTimeMillis()) / (1000L * 60L * 60L * 24L)).toInt()
        if (diff >= 0) "$diff Days Left" else "Exam Day Passed"
    }
    val newPublishedTests = remember(latestMockTests) {
        latestMockTests.filter { it.isShared }.take(5)
    }
    val hasUnreadNotifications = newPublishedTests.isNotEmpty() || pendingSyncCount > 0

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
                Spacer(Modifier.height(8.dp))

                // Top Header Bar (Menu + LT Brand Logo + Notification Bell + Profile Avatar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { showDrawerSheet = true },
                            modifier = Modifier.testTag("home_drawer_menu_btn")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open Menu", tint = theme.primary)
                        }
                        LearnTalentBrandHeader(
                            customLogoFile = null,
                            subtitle = "",
                            logoSize = 40.dp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = onToggleLightDark, modifier = Modifier.testTag("home_theme_toggle_btn")) {
                            Icon(
                                imageVector = if (theme.isLight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                contentDescription = "Toggle Light/Dark theme",
                                tint = theme.primary
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showNotificationsSheet = true },
                                modifier = Modifier.testTag("home_notifications_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Notifications,
                                    contentDescription = "Notifications",
                                    tint = theme.primary
                                )
                            }
                            if (hasUnreadNotifications) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 10.dp, end = 10.dp)
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(BrandColors.PremiumGold)
                                        .border(1.dp, theme.background, CircleShape)
                                )
                            }
                        }
                        Box(modifier = Modifier.clickable { onOpenSettingsTab() }.testTag("home_profile_avatar_btn")) {
                            StudentAvatar(photoUrl = photoUrl, displayName = resolvedName, size = 36.dp)
                        }
                    }
                }

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
                            contentPadding = PaddingValues(bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 1. Greeting + Preparation Progress Hero Card
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().testTag("home_progress_card"),
                                    shape = RoundedCornerShape(22.dp),
                                    color = BrandColors.DeepNavy,
                                    shadowElevation = 6.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(BrandColors.DeepNavy, Color(0xFF0A2E66), BrandColors.RoyalBlue)
                                                )
                                            )
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Welcome back, ${resolvedName.substringBefore(' ')}!",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = BrandColors.PureWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = profile?.examName?.takeIf { it.isNotBlank() }?.let { "Target: $it" }
                                                        ?: "FLT – KEY OF SUCCESS • Ready to study",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFFCDE2FF),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(99.dp),
                                                color = if (isNetworkOnline) Color(0xFF16A34A).copy(alpha = 0.25f) else BrandColors.PremiumGold.copy(alpha = 0.22f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isNetworkOnline) Color(0xFF4ADE80).copy(alpha = 0.7f) else BrandColors.PremiumGold.copy(alpha = 0.7f)
                                                )
                                            ) {
                                                Text(
                                                    text = if (isNetworkOnline) "ONLINE" else "OFFLINE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isNetworkOnline) Color(0xFF86EFAC) else BrandColors.PremiumGold,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(14.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Preparation Progress",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = BrandColors.SkyBlue
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                LinearProgressIndicator(
                                                    progress = { overallProgressPct / 100f },
                                                    color = BrandColors.PremiumGold,
                                                    trackColor = BrandColors.PureWhite.copy(alpha = 0.2f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(99.dp))
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = "${testResults.size} Tests • Avg ${MainViewModel.formatNum(avgScorePct)}% • $studiedTopicsCount Studied",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFE0EEFF)
                                                )
                                            }
                                            Spacer(Modifier.width(14.dp))
                                            Box(
                                                modifier = Modifier.size(64.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    val stroke = 6.dp.toPx()
                                                    drawArc(
                                                        color = Color.White.copy(alpha = 0.2f),
                                                        startAngle = 0f,
                                                        sweepAngle = 360f,
                                                        useCenter = false,
                                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                                    )
                                                    drawArc(
                                                        color = BrandColors.PremiumGold,
                                                        startAngle = -90f,
                                                        sweepAngle = (overallProgressPct / 100f) * 360f,
                                                        useCenter = false,
                                                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                                                    )
                                                }
                                                Text(
                                                    text = "$overallProgressPct%",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = BrandColors.PureWhite,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Admin Control Center Card (Strictly visible to Admin only)
                            if (isAdminUser) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Tactile3DCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        accentColor = theme.goldAccent,
                                        onClick = onOpenAdminPanel,
                                        testTag = "home_admin_panel_card",
                                        contentPadding = PaddingValues(14.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            TactileIconOrb(icon = Icons.Rounded.AdminPanelSettings, tint = theme.goldAccent, size = 44.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Admin Control Center (Upload & Analytics)", style = MaterialTheme.typography.titleSmall, color = theme.onSurface, fontWeight = FontWeight.Bold)
                                                Text("सभी छात्रों के लिए PDF/HTML टेस्ट अपलोड, छात्र रिजल्ट व अनुरोध देखें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                            }
                                            Tactile3DBadge(text = "ADMIN", color = theme.goldAccent)
                                        }
                                    }
                                }
                            }

                            // 2. Sync Status Bar
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                val (syncColor, syncIcon, syncTitle, syncSubtitle, actionLabel) = when (driveSyncState) {
                                    is DriveSyncUiState.OfflineAccount -> SyncBannerVisual(
                                        theme.secondary, Icons.Rounded.CloudOff,
                                        "Offline Mode Active (ऑफलाइन मोड)",
                                        "सारा डेटा फोन में सेव है। क्लाउड सिंक के लिए Google लॉगिन करें।",
                                        "Google Login"
                                    )
                                    is DriveSyncUiState.Syncing -> SyncBannerVisual(
                                        theme.primary, Icons.Rounded.CloudSync,
                                        "Google Drive Syncing...",
                                        driveSyncState.message,
                                        "Syncing"
                                    )
                                    is DriveSyncUiState.Synced -> SyncBannerVisual(
                                        theme.success, Icons.Rounded.CloudDone,
                                        "Google Drive Backed Up",
                                        driveSyncState.detail,
                                        "Sync Now"
                                    )
                                    is DriveSyncUiState.PendingOffline -> SyncBannerVisual(
                                        theme.primary, Icons.Rounded.CloudQueue,
                                        "Pending Cloud Sync",
                                        driveSyncState.message,
                                        "Sync Now"
                                    )
                                    is DriveSyncUiState.NeedAuth -> SyncBannerVisual(
                                        theme.goldAccent, Icons.Rounded.Sync,
                                        "Connect Google Drive",
                                        driveSyncState.message,
                                        "Authorize"
                                    )
                                    is DriveSyncUiState.Error -> SyncBannerVisual(
                                        theme.error, Icons.Rounded.ErrorOutline,
                                        "Drive Sync Paused (Offline Safe)",
                                        driveSyncState.message,
                                        "Retry Sync"
                                    )
                                }
                                Tactile3DCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = syncColor,
                                    lipDepth = 3.dp,
                                    testTag = "home_drive_sync_card",
                                    contentPadding = PaddingValues(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            TactileIconOrb(icon = syncIcon, tint = syncColor, size = 36.dp)
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(syncTitle, style = MaterialTheme.typography.titleSmall, color = syncColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                                    if (pendingSyncCount > 0 && !isOfflineAccount) {
                                                        Tactile3DBadge(text = "$pendingSyncCount Queued", color = theme.goldAccent)
                                                    }
                                                }
                                                Text(syncSubtitle, style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = onSignInOrSyncNow,
                                            enabled = driveSyncState !is DriveSyncUiState.Syncing,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = syncColor, contentColor = BrandColors.PureWhite),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.testTag("home_drive_sync_action_btn")
                                        ) {
                                            Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }

                            // 3. 3-Column Interactive Feature Grid (9 Core Platform Tools)
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                val tiles = remember(testResults.size, personalNotes.size, bookmarks.size, diaryEntries.size) {
                                    listOf(
                                        DashboardGridTile("Mock Test", "CBT Exam", Icons.Rounded.Quiz, BrandColors.RoyalBlue, "tile_mock_test", onOpenTestsTab),
                                        DashboardGridTile("Practice Test", "Quick MCQ", Icons.Rounded.School, BrandColors.SkyBlue, "tile_practice_test", onOpenTestsTab),
                                        DashboardGridTile("Study Material", "PDFs & Books", Icons.Rounded.MenuBook, Color(0xFF4F46E5), "tile_study_material", onOpenStudyTab),
                                        DashboardGridTile("Subject Notes", "${personalNotes.size} Notes", Icons.Rounded.EditNote, Color(0xFF059669), "home_personal_notes_card", onOpenPersonalNotes),
                                        DashboardGridTile("Test Results", "${testResults.size} Done", Icons.Rounded.Assessment, Color(0xFFD97706), "tile_test_results", onOpenResultsTab),
                                        DashboardGridTile("Performance", "Analytics", Icons.Rounded.Analytics, Color(0xFF7C3AED), "tile_performance", onOpenResultsTab),
                                        DashboardGridTile("Bookmarks", "${bookmarks.size} Saved", Icons.Rounded.Bookmarks, Color(0xFFE11D48), "home_bookmarks_card", onOpenBookmarks),
                                        DashboardGridTile("My Progress", "Revision (${diaryEntries.size})", Icons.Rounded.TrendingUp, Color(0xFF0D9488), "home_yesterday_revision_card", onOpenRevisionTab),
                                        DashboardGridTile("More", "Tools & Sync", Icons.Rounded.GridView, BrandColors.DeepNavy, "tile_more_tools") { showDrawerSheet = true },
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Quick Learning Hub",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                    tiles.chunked(3).forEach { rowTiles ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            rowTiles.forEach { tile ->
                                                FeatureGridCard(
                                                    modifier = Modifier.weight(1f),
                                                    tile = tile
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Latest Mock Tests Section + Full Length Suites Shortcut
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Latest Mock Tests",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = theme.onBackground,
                                            fontWeight = FontWeight.Bold
                                        )
                                        TextButton(onClick = onOpenTestsTab) {
                                            Text("View All →", color = theme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    if (latestMockTests.isNotEmpty()) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(latestMockTests.take(6), key = { it.id }) { testNote ->
                                                Tactile3DCard(
                                                    modifier = Modifier.width(248.dp),
                                                    accentColor = if (testNote.isShared) theme.goldAccent else theme.primary,
                                                    lipDepth = 4.dp,
                                                    contentPadding = PaddingValues(14.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        TactileIconOrb(icon = Icons.Rounded.Quiz, tint = theme.primary, size = 34.dp)
                                                        if (testNote.isShared) {
                                                            Tactile3DBadge(text = "NEW", color = theme.goldAccent, icon = Icons.Rounded.AutoAwesome)
                                                        } else {
                                                            Tactile3DBadge(text = "CBT", color = theme.secondary)
                                                        }
                                                    }
                                                    Spacer(Modifier.height(10.dp))
                                                    Text(
                                                        text = testNote.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = theme.onSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = theme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text = if (testNote.subjectId == FullLengthExamDefaults.SUBJECT_ID) "100 Qs • 60 min" else "Practice • 30 min",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = theme.onSurfaceVariant
                                                        )
                                                    }
                                                    Spacer(Modifier.height(10.dp))
                                                    Button(
                                                        onClick = { onStartMockTest(testNote) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(4.dp))
                                                        Text("Start Test", fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }

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
                                                TactileIconOrb(icon = Icons.Rounded.Assignment, tint = theme.primary, size = 44.dp)
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text("Full Length Exam Suites", style = MaterialTheme.typography.titleSmall, color = theme.primary, fontWeight = FontWeight.Bold)
                                                    Text("MPPSC, UPSC, SSC, Banking, Railway व .html मॉक टेस्ट", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
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
                            }

                            // Continue Learning Strip
                            if (recentActivities.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Continue Learning", style = MaterialTheme.typography.titleSmall, color = theme.primary, fontWeight = FontWeight.SemiBold)
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

                            // Revision Banner
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Tactile3DCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = theme.secondary,
                                    onClick = onOpenRevisionTab,
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            TactileIconOrb(icon = Icons.Rounded.History, tint = theme.secondary, size = 42.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text("कल क्या पढ़ा था • Revision Diary", style = MaterialTheme.typography.titleSmall, color = theme.secondary, fontWeight = FontWeight.Bold)
                                                val summary = if (yesterdayLogs.isNotEmpty()) yesterdayLogs.joinToString(", ") { it.title }
                                                else "रिवीजन डायरी (${diaryEntries.size}) • आज का प्लान देखें →"
                                                Text(summary, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        if (daysLeftText != null) Tactile3DBadge(text = daysLeftText, color = theme.primary)
                                    }
                                }
                            }

                            // 5. Subjects Header + Search Bar + 12 Subjects Grid
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Browse All Subjects (${filtered.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search subject (e.g. History, MP GK, Polity...)") },
                                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("subjects_search_input")
                                    )
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

    if (showDrawerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDrawerSheet = false },
            containerColor = theme.surfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LearnTalentBrandHeader(customLogoFile = null, subtitle = resolvedName)
                Spacer(Modifier.height(4.dp))
                DrawerMenuItem(Icons.Rounded.Assignment, "Full Length Exams", "${fullLengthExams.size} Exam Categories", theme.primary) {
                    showDrawerSheet = false; onOpenFullLengthTests()
                }
                DrawerMenuItem(Icons.Rounded.Book, "Revision Diary & Study Plan", "${diaryEntries.size} diary entries", theme.secondary) {
                    showDrawerSheet = false; onOpenRevisionTab()
                }
                DrawerMenuItem(Icons.Rounded.EditNote, "Personal Notes Maker", "${personalNotes.size} saved notes", theme.primary) {
                    showDrawerSheet = false; onOpenPersonalNotes()
                }
                DrawerMenuItem(Icons.Rounded.Bookmarks, "Saved Bookmarks", "${bookmarks.size} items", theme.goldAccent) {
                    showDrawerSheet = false; onOpenBookmarks()
                }
                if (isAdminUser) {
                    DrawerMenuItem(Icons.Rounded.AdminPanelSettings, "Admin Control Center", "Upload tests, PDFs & view students", theme.goldAccent) {
                        showDrawerSheet = false; onOpenAdminPanel()
                    }
                }
                DrawerMenuItem(Icons.AutoMirrored.Rounded.HelpOutline, "Support & Request Content", "WhatsApp Admin Support", theme.success) {
                    showDrawerSheet = false; onOpenSupport()
                }
                DrawerMenuItem(Icons.Rounded.Settings, "Profile, Drive Sync & Themes", "Manage account & security", theme.primary) {
                    showDrawerSheet = false; onOpenSettingsTab()
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            containerColor = theme.surfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Notifications & Alerts", style = MaterialTheme.typography.titleLarge, color = theme.onSurface, fontWeight = FontWeight.Bold)
                if (pendingSyncCount > 0 && !isOfflineAccount) {
                    Tactile3DCard(accentColor = theme.goldAccent, lipDepth = 3.dp, contentPadding = PaddingValues(12.dp)) {
                        Text("$pendingSyncCount local items waiting for Google Drive sync", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                        Text("Tap Sync Now when online to upload securely to LEARN TALENT/", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                    }
                }
                if (newPublishedTests.isEmpty()) {
                    Tactile3DCard(accentColor = theme.secondary, lipDepth = 3.dp, contentPadding = PaddingValues(12.dp)) {
                        Text("You're all caught up!", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                        Text("Check Full Length Tests or refresh Shared Admin Catalog in Profile for new papers.", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                    }
                } else {
                    newPublishedTests.forEach { testNote ->
                        Tactile3DCard(
                            accentColor = theme.primary,
                            lipDepth = 3.dp,
                            onClick = { showNotificationsSheet = false; onStartMockTest(testNote) },
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("New Published Test: ${testNote.title}", style = MaterialTheme.typography.titleSmall, color = theme.onSurface)
                                    Text("Tap to start CBT examination", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                }
                                Tactile3DBadge(text = "NEW", color = theme.goldAccent)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
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

private data class DashboardGridTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val testTag: String,
    val onClick: () -> Unit,
)

@Composable
private fun FeatureGridCard(
    modifier: Modifier,
    tile: DashboardGridTile,
) {
    val theme = LocalStudyTheme.current
    Tactile3DCard(
        modifier = modifier,
        accentColor = tile.accent,
        cornerRadius = 18.dp,
        lipDepth = 4.dp,
        onClick = tile.onClick,
        testTag = tile.testTag,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TactileIconOrb(icon = tile.icon, tint = tile.accent, size = 40.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.labelLarge,
                color = theme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = theme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    Tactile3DCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = tint,
        lipDepth = 3.dp,
        onClick = onClick,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TactileIconOrb(icon = icon, tint = tint, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = theme.onSurface, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
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

private data class SyncBannerVisual(
    val color: Color,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val actionLabel: String,
)
