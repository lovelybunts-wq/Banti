package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.auth.GoogleSessionUser
import com.example.auth.GoogleSignInResult
import com.example.data.local.LocalAdminCatalogStore
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalFileStorage
import com.example.data.local.LocalOfflineStore
import com.example.data.local.UserPreferencesManager
import com.example.data.model.BookmarkType
import com.example.data.model.FullLengthExamDefaults
import com.example.data.model.NoteType
import com.example.data.model.StudyNote
import com.example.data.model.UiState
import com.example.data.remote.GoogleDriveBackupManager
import com.example.ui.auth.AuthScreen
import com.example.ui.components.WhatsAppRequestDialog
import com.example.ui.navigation.AdminPanelRoute
import com.example.ui.navigation.BookmarksRoute
import com.example.ui.navigation.FullLengthExamTestsRoute
import com.example.ui.navigation.FullLengthExamsRoute
import com.example.ui.navigation.HtmlTestRoute
import com.example.ui.navigation.ImageViewerRoute
import com.example.ui.navigation.McqPracticeRoute
import com.example.ui.navigation.PdfViewerRoute
import com.example.ui.navigation.PersonalNotesRoute
import com.example.ui.navigation.RevisionPlannerRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SubjectsHomeRoute
import com.example.ui.navigation.SubtopicDetailRoute
import com.example.ui.navigation.SubtopicsRoute
import com.example.ui.navigation.TestResultRoute
import com.example.ui.navigation.TopicsRoute
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BookmarksScreen
import com.example.ui.screens.FullLengthExamTestsScreen
import com.example.ui.screens.FullLengthExamsScreen
import com.example.ui.screens.HomeSubjectsScreen
import com.example.ui.screens.HtmlTestScreen
import com.example.ui.screens.ImageViewerScreen
import com.example.ui.screens.McqPracticeScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.PersonalNotesScreen
import com.example.ui.screens.RevisionPlannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubtopicDetailScreen
import com.example.ui.screens.SubtopicsScreen
import com.example.ui.screens.TestResultScreen
import com.example.ui.screens.TopicsScreen
import com.example.ui.security.BiometricLockScreen
import com.example.ui.theme.LearnTalentTheme
import com.example.ui.theme.LocalStudyTheme
import kotlinx.coroutines.launch

private data class WhatsAppRequestPrefill(
    val subject: String = "",
    val topic: String = "",
    val subtopic: String = "",
)

@Composable
fun LearnTalentApp(isCompactWidth: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferencesManager(context.applicationContext) }
    val localSettings by prefs.settingsFlow.collectAsStateWithLifecycle(initialValue = LocalAppSettings())

    var activeDriveToken by remember { mutableStateOf<String?>(null) }
    var forcedOfflineOverride by remember { mutableStateOf(false) }

    val sessionUser = remember(localSettings, forcedOfflineOverride) {
        if (forcedOfflineOverride) GoogleAuthHelper.offlineSession()
        else GoogleAuthHelper.sessionFromSettings(localSettings)
    }

    LearnTalentTheme(themeOption = localSettings.themeOption) {
        if (sessionUser == null) {
            AuthScreen(
                onSignedIn = { _, token ->
                    forcedOfflineOverride = false
                    activeDriveToken = token
                },
                onContinueOffline = {
                    forcedOfflineOverride = true
                }
            )
        } else {
            key(sessionUser.uid) {
                AuthenticatedAppContent(
                    sessionUser = sessionUser,
                    initialDriveToken = activeDriveToken,
                    isCompactWidth = isCompactWidth,
                    onSignedInUpdated = { _, token ->
                        forcedOfflineOverride = false
                        activeDriveToken = token
                    },
                    onSignedOutResetSession = {
                        forcedOfflineOverride = false
                        activeDriveToken = null
                        scope.launch {
                            GoogleAuthHelper.signOut(context.applicationContext, prefs, returnToAuthScreen = true)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedAppContent(
    sessionUser: GoogleSessionUser,
    initialDriveToken: String?,
    isCompactWidth: Boolean,
    onSignedInUpdated: (GoogleSessionUser, String?) -> Unit,
    onSignedOutResetSession: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val fileStorage = remember { LocalFileStorage(context.applicationContext) }
    val prefs = remember { UserPreferencesManager(context.applicationContext) }
    val driveManager = remember { GoogleDriveBackupManager() }

    val viewModel: MainViewModel = viewModel(
        key = sessionUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" } as Application
                MainViewModel(
                    appContext = app.applicationContext,
                    offlineStore = LocalOfflineStore(app.applicationContext, ownerId = sessionUser.uid),
                    adminCatalogStore = LocalAdminCatalogStore(app.applicationContext),
                    driveManager = GoogleDriveBackupManager(),
                    fileStorage = LocalFileStorage(app.applicationContext),
                    prefs = UserPreferencesManager(app.applicationContext),
                    sessionUser = sessionUser,
                    initialDriveToken = initialDriveToken,
                )
            }
        }
    )

    val isAdminUser = viewModel.isAdminUser
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    var isVaultUnlocked by rememberSaveable(sessionUser.uid) { mutableStateOf(false) }

    if (localSettings.biometricLockEnabled && !isVaultUnlocked) {
        BiometricLockScreen(customLogoFile = null, onUnlocked = { isVaultUnlocked = true })
        return
    }

    val theme = LocalStudyTheme.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var whatsAppPrefill by remember { mutableStateOf<WhatsAppRequestPrefill?>(null) }

    // Consent IntentSender launcher for Google Drive Sync / Restore / Admin Publish
    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (activity != null) {
            viewModel.onConsentIntentResult(activity, res.data)
        }
    }

    // Account Sign-In / Switch IntentSender launcher (used when upgrading from Offline -> Gmail or Switching Gmail)
    val accountSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (activity == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val out = GoogleAuthHelper.completeSignInFromIntent(activity, res.data, driveManager, prefs)) {
                is GoogleSignInResult.Success -> {
                    onSignedInUpdated(out.session, out.accessToken)
                    viewModel.showToast("Signed in as ${out.session.email} — syncing Drive...")
                }
                is GoogleSignInResult.Error -> viewModel.showToast(out.message)
                is GoogleSignInResult.NeedsConsentUi -> Unit
            }
        }
    }

    fun launchInteractiveGoogleSignIn(switchAccountFirst: Boolean) {
        if (activity == null) {
            viewModel.showToast("Google Sign-In के लिए Activity उपलब्ध नहीं है।")
            return
        }
        scope.launch {
            if (switchAccountFirst) {
                GoogleAuthHelper.signOut(context.applicationContext, prefs, returnToAuthScreen = false)
            }
            when (val res = GoogleAuthHelper.startGoogleSignIn(activity, driveManager, prefs)) {
                is GoogleSignInResult.Success -> {
                    onSignedInUpdated(res.session, res.accessToken)
                    viewModel.showToast("Gmail लॉगिन सफल: ${res.session.email}")
                }
                is GoogleSignInResult.NeedsConsentUi -> {
                    accountSignInLauncher.launch(IntentSenderRequest.Builder(res.intentSender).build())
                }
                is GoogleSignInResult.Error -> {
                    viewModel.showToast(res.message)
                }
            }
        }
    }

    val pendingConsentSender by viewModel.pendingConsentSender.collectAsStateWithLifecycle()
    LaunchedEffect(pendingConsentSender) {
        val sender = pendingConsentSender ?: return@LaunchedEffect
        viewModel.clearPendingConsentSender()
        driveConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val topicsState by viewModel.topicsState.collectAsStateWithLifecycle()
    val subtopicsState by viewModel.subtopicsState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val videosState by viewModel.videosState.collectAsStateWithLifecycle()
    val mcqsState by viewModel.mcqsState.collectAsStateWithLifecycle()
    val fullLengthExamsState by viewModel.fullLengthExamsState.collectAsStateWithLifecycle()
    val testResultsState by viewModel.testResultsState.collectAsStateWithLifecycle()
    val studyLogsState by viewModel.studyLogsState.collectAsStateWithLifecycle()
    val revisionDiaryState by viewModel.revisionDiaryState.collectAsStateWithLifecycle()
    val personalNotesState by viewModel.personalNotesState.collectAsStateWithLifecycle()
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val recentActivities by viewModel.recentActivities.collectAsStateWithLifecycle()
    val sharedDownloadsMap by viewModel.sharedDownloadsMap.collectAsStateWithLifecycle()
    val sharedDownloadProgress by viewModel.sharedDownloadProgress.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val lastReturnedScore by viewModel.lastReturnedScore.collectAsStateWithLifecycle()
    val driveSyncState by viewModel.driveSyncState.collectAsStateWithLifecycle()
    val publishedCatalogLink by viewModel.publishedCatalogLink.collectAsStateWithLifecycle()

    val subjects = (subjectsState as? UiState.Success)?.data ?: emptyList()
    val topics = (topicsState as? UiState.Success)?.data ?: emptyList()
    val subtopics = (subtopicsState as? UiState.Success)?.data ?: emptyList()
    val notes = (notesState as? UiState.Success)?.data ?: emptyList()
    val videos = (videosState as? UiState.Success)?.data ?: emptyList()
    val mcqs = (mcqsState as? UiState.Success)?.data ?: emptyList()
    val fullLengthExams = (fullLengthExamsState as? UiState.Success)?.data ?: FullLengthExamDefaults.builtInExams
    val testResults = (testResultsState as? UiState.Success)?.data ?: emptyList()
    val personalNotes = (personalNotesState as? UiState.Success)?.data ?: emptyList()
    val bookmarks = (bookmarksState as? UiState.Success)?.data ?: emptyList()
    val profile = (profileState as? UiState.Success)?.data

    val resolvedStudentName = profile?.displayName?.trim()?.takeIf {
        it.isNotBlank() && it != "Learner" && it != "Offline Learner"
    } ?: sessionUser.displayName

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isTopLevelRoute = currentDest?.let { dest ->
        dest.hasRoute<SubjectsHomeRoute>() || dest.hasRoute<RevisionPlannerRoute>() || dest.hasRoute<SettingsRoute>()
    } ?: true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openNoteDestination(note: StudyNote, subjectName: String = "Study") {
        viewModel.markNoteOpenedToday(note, subjectName)
        when (note.resolvedType()) {
            NoteType.PDF -> navController.navigate(PdfViewerRoute(noteId = note.id))
            NoteType.IMAGE -> navController.navigate(ImageViewerRoute(note.id))
            NoteType.HTML -> navController.navigate(
                HtmlTestRoute(note.id, note.subjectId, note.topicId, note.subtopicId, isTestMode = true)
            )
            NoteType.FOLDER -> Unit
        }
    }

    Scaffold(
        containerColor = theme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isCompactWidth && isTopLevelRoute) {
                NavigationBar(containerColor = theme.surface, windowInsets = WindowInsets.navigationBars) {
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<SubjectsHomeRoute>() == true,
                        onClick = { navigateTopLevel(SubjectsHomeRoute) },
                        icon = { Icon(if (currentDest?.hasRoute<SubjectsHomeRoute>() == true) Icons.Rounded.AutoStories else Icons.Outlined.AutoStories, contentDescription = stringResource(R.string.nav_subjects)) },
                        label = { Text(stringResource(R.string.nav_subjects)) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = theme.primary, selectedTextColor = theme.primary, indicatorColor = theme.primaryContainer, unselectedIconColor = theme.onSurfaceVariant, unselectedTextColor = theme.onSurfaceVariant)
                    )
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<RevisionPlannerRoute>() == true,
                        onClick = { navigateTopLevel(RevisionPlannerRoute) },
                        icon = { Icon(if (currentDest?.hasRoute<RevisionPlannerRoute>() == true) Icons.AutoMirrored.Rounded.EventNote else Icons.AutoMirrored.Outlined.EventNote, contentDescription = stringResource(R.string.nav_revision)) },
                        label = { Text(stringResource(R.string.nav_revision)) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = theme.primary, selectedTextColor = theme.primary, indicatorColor = theme.primaryContainer, unselectedIconColor = theme.onSurfaceVariant, unselectedTextColor = theme.onSurfaceVariant)
                    )
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<SettingsRoute>() == true,
                        onClick = { navigateTopLevel(SettingsRoute) },
                        icon = { Icon(if (currentDest?.hasRoute<SettingsRoute>() == true) Icons.Rounded.Tune else Icons.Outlined.Tune, contentDescription = stringResource(R.string.nav_settings)) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = theme.primary, selectedTextColor = theme.primary, indicatorColor = theme.primaryContainer, unselectedIconColor = theme.onSurfaceVariant, unselectedTextColor = theme.onSurfaceVariant)
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().background(theme.background).padding(innerPadding)) {
            if (!isCompactWidth && isTopLevelRoute) {
                NavigationRail(containerColor = theme.surface, modifier = Modifier.fillMaxHeight()) {
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<SubjectsHomeRoute>() == true,
                        onClick = { navigateTopLevel(SubjectsHomeRoute) },
                        icon = { Icon(Icons.Rounded.AutoStories, contentDescription = stringResource(R.string.nav_subjects)) },
                        label = { Text(stringResource(R.string.nav_subjects)) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = theme.primary, selectedTextColor = theme.primary, indicatorColor = theme.primaryContainer)
                    )
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<RevisionPlannerRoute>() == true,
                        onClick = { navigateTopLevel(RevisionPlannerRoute) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.EventNote, contentDescription = stringResource(R.string.nav_revision)) },
                        label = { Text(stringResource(R.string.nav_revision)) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = theme.primary, selectedTextColor = theme.primary, indicatorColor = theme.primaryContainer)
                    )
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<SettingsRoute>() == true,
                        onClick = { navigateTopLevel(SettingsRoute) },
                        icon = { Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.nav_settings)) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        colors = NavigationRailItemDefaults.colors(selectedIconColor = theme.primary, selectedTextColor = theme.primary, indicatorColor = theme.primaryContainer)
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(navController = navController, startDestination = SubjectsHomeRoute) {
                    composable<SubjectsHomeRoute> {
                        HomeSubjectsScreen(
                            subjectsState = subjectsState,
                            topicsState = topicsState,
                            notesState = notesState,
                            fullLengthExamsState = fullLengthExamsState,
                            studyLogsState = studyLogsState,
                            revisionDiaryState = revisionDiaryState,
                            personalNotesState = personalNotesState,
                            bookmarksState = bookmarksState,
                            recentActivities = recentActivities,
                            profileState = profileState,
                            displayName = resolvedStudentName,
                            photoUrl = sessionUser.photoUrl,
                            isAdminUser = isAdminUser,
                            isOfflineAccount = sessionUser.isOffline,
                            driveSyncState = driveSyncState,
                            onSignInOrSyncNow = {
                                if (sessionUser.isOffline) launchInteractiveGoogleSignIn(switchAccountFirst = false)
                                else viewModel.triggerSyncNow(activity)
                            },
                            onToggleLightDark = { viewModel.toggleLightDarkTheme() },
                            onSubjectClick = { navController.navigate(TopicsRoute(it.id)) },
                            onOpenFullLengthTests = { navController.navigate(FullLengthExamsRoute) },
                            onOpenRecentActivity = { act ->
                                val note = notes.firstOrNull { it.id == act.refId }
                                if (note != null) openNoteDestination(note, act.subtitle)
                                else if (act.subjectId == FullLengthExamDefaults.SUBJECT_ID && act.topicId.isNotBlank()) {
                                    navController.navigate(FullLengthExamTestsRoute(act.topicId))
                                } else if (act.subjectId.isNotBlank() && act.topicId.isNotBlank() && act.subtopicId.isNotBlank()) {
                                    navController.navigate(SubtopicDetailRoute(act.subjectId, act.topicId, act.subtopicId))
                                }
                            },
                            onOpenRevisionTab = { navigateTopLevel(RevisionPlannerRoute) },
                            onOpenSettingsTab = { navigateTopLevel(SettingsRoute) },
                            onOpenPersonalNotes = { navController.navigate(PersonalNotesRoute()) },
                            onOpenBookmarks = { navController.navigate(BookmarksRoute) },
                            onOpenAdminPanel = { navController.navigate(AdminPanelRoute) },
                            onOpenSupport = { whatsAppPrefill = WhatsAppRequestPrefill() },
                            onBookmarkSubject = { subj ->
                                viewModel.toggleBookmark(BookmarkType.SAVED_TOPIC, subj.nameEn, subj.nameHi ?: "Subject", subj.id, subjectId = subj.id)
                            },
                            onAddSubject = { en, hi, icon, hex, order -> viewModel.addSubject(en, hi, icon, hex, order) }
                        )
                    }

                    composable<FullLengthExamsRoute> {
                        FullLengthExamsScreen(
                            examsState = fullLengthExamsState,
                            notes = notes,
                            testResults = testResults,
                            isAdminUser = isAdminUser,
                            onBack = { navController.popBackStack() },
                            onExamClick = { exam -> navController.navigate(FullLengthExamTestsRoute(exam.id)) },
                            onCreateExam = { name, subtitle -> viewModel.addFullLengthExam(name, subtitle) },
                            onDeleteExam = { exam -> viewModel.deleteFullLengthExam(exam) }
                        )
                    }

                    composable<FullLengthExamTestsRoute> { entry ->
                        val route = entry.toRoute<FullLengthExamTestsRoute>()
                        val exam = fullLengthExams.firstOrNull { it.id == route.examId }
                        val examTests = notes.filter {
                            it.subjectId == FullLengthExamDefaults.SUBJECT_ID && it.topicId == route.examId
                        }
                        FullLengthExamTestsScreen(
                            exam = exam,
                            examId = route.examId,
                            tests = examTests,
                            testResults = testResults.filter { it.subjectId == FullLengthExamDefaults.SUBJECT_ID && it.topicId == route.examId },
                            isAdminUser = isAdminUser,
                            onBack = { navController.popBackStack() },
                            onUploadHtmlTest = { title, uri ->
                                viewModel.uploadFullLengthHtmlTest(route.examId, exam?.name ?: "Full Length Test", title, uri)
                            },
                            onCreateSampleTest = { title ->
                                viewModel.createSampleFullLengthTest(route.examId, exam?.name ?: "Full Length Test", title)
                            },
                            onStartTest = { note -> openNoteDestination(note, exam?.name ?: "Full Length Test") },
                            onOpenResult = { resultId -> navController.navigate(TestResultRoute(resultId)) },
                            onDeleteTest = { note -> viewModel.deleteNote(note) }
                        )
                    }

                    composable<TopicsRoute> { entry ->
                        val route = entry.toRoute<TopicsRoute>()
                        val subject = subjects.firstOrNull { it.id == route.subjectId }
                        TopicsScreen(
                            subject = subject,
                            topics = topics.filter { it.subjectId == route.subjectId },
                            subtopics = subtopics,
                            bookmarks = bookmarks,
                            isAdminUser = isAdminUser,
                            onBack = { navController.popBackStack() },
                            onTopicClick = { navController.navigate(SubtopicsRoute(route.subjectId, it.id)) },
                            onBookmarkTopic = { t ->
                                viewModel.toggleBookmark(BookmarkType.SAVED_TOPIC, t.name, subject?.nameEn ?: "Topic", t.id, subjectId = route.subjectId, topicId = t.id)
                            },
                            onAddTopic = { name, desc -> viewModel.addTopic(route.subjectId, name, desc) },
                            onDeleteTopic = { t -> if (t.isShared) viewModel.adminDeleteSharedTopic(t.id) else viewModel.deleteTopic(t.id) }
                        )
                    }

                    composable<SubtopicsRoute> { entry ->
                        val route = entry.toRoute<SubtopicsRoute>()
                        val subject = subjects.firstOrNull { it.id == route.subjectId }
                        val topic = topics.firstOrNull { it.id == route.topicId }
                        SubtopicsScreen(
                            subject = subject, topic = topic,
                            subtopics = subtopics.filter { it.topicId == route.topicId },
                            notes = notes, videos = videos, mcqs = mcqs, isAdminUser = isAdminUser,
                            onBack = { navController.popBackStack() },
                            onSubtopicClick = { navController.navigate(SubtopicDetailRoute(route.subjectId, route.topicId, it.id)) },
                            onAddSubtopic = { name -> viewModel.addSubtopic(route.subjectId, route.topicId, name) },
                            onDeleteSubtopic = { st -> if (st.isShared) viewModel.adminDeleteSharedSubtopic(st.id) else viewModel.deleteSubtopic(st.id) }
                        )
                    }

                    composable<SubtopicDetailRoute> { entry ->
                        val route = entry.toRoute<SubtopicDetailRoute>()
                        val subject = subjects.firstOrNull { it.id == route.subjectId }
                        val topic = topics.firstOrNull { it.id == route.topicId }
                        val subtopic = subtopics.firstOrNull { it.id == route.subtopicId }
                        val subjectName = subject?.nameEn ?: "Study"

                        SubtopicDetailScreen(
                            subject = subject, topic = topic, subtopic = subtopic,
                            notes = notes.filter { it.subtopicId == route.subtopicId },
                            videos = videos.filter { it.subtopicId == route.subtopicId },
                            mcqs = mcqs.filter { it.subtopicId == route.subtopicId },
                            testResults = testResults.filter { it.subtopicId == route.subtopicId },
                            bookmarks = bookmarks, sharedDownloadsMap = sharedDownloadsMap,
                            sharedDownloadProgress = sharedDownloadProgress, isAdminUser = isAdminUser,
                            lastReturnedScore = lastReturnedScore,
                            onDismissScoreBanner = { viewModel.clearLastReturnedScore() },
                            onBack = { navController.popBackStack() },
                            onMarkStudiedToday = { if (subtopic != null) viewModel.markSubtopicStudiedToday(subtopic, subjectName) },
                            onRequestMaterialWhatsApp = {
                                whatsAppPrefill = WhatsAppRequestPrefill(subject?.nameEn ?: "", topic?.name ?: "", subtopic?.name ?: "")
                            },
                            onCreateFolder = { viewModel.createFolder(route.subjectId, route.topicId, route.subtopicId, it) },
                            onImportFile = { title, folder, type, uri, isTest ->
                                viewModel.importFileNote(route.subjectId, route.topicId, route.subtopicId, folder, title, type, uri, isTest)
                            },
                            onCreateSampleHtmlTest = { title, folder ->
                                viewModel.createSampleHtmlTest(route.subjectId, route.topicId, route.subtopicId, folder, title)
                            },
                            onRenameOrMoveNote = { id, title, folder -> viewModel.renameOrMoveNote(id, title, folder) },
                            onOpenNote = { note -> openNoteDestination(note, subjectName) },
                            onOpenHtmlTest = { note -> openNoteDestination(note, subjectName) },
                            onDownloadShared = { viewModel.downloadSharedNote(it) },
                            onToggleBookmarkNote = { n ->
                                viewModel.toggleBookmark(BookmarkType.FAVORITE_NOTE, n.title, "$subjectName • ${n.noteType}", n.id, route.subjectId, route.topicId, route.subtopicId)
                            },
                            onOpenTestResult = { resultId -> navController.navigate(TestResultRoute(resultId)) },
                            onDeleteNote = { viewModel.deleteNote(it) },
                            onAddVideo = { title, url, noteText -> viewModel.addVideo(route.subjectId, route.topicId, route.subtopicId, title, url, noteText) },
                            onWatchVideo = { viewModel.markVideoWatched(it, subjectName) },
                            onDeleteVideo = { viewModel.deleteVideo(it) },
                            onAddMcq = { q, opts, idx, exp -> viewModel.addMcq(route.subjectId, route.topicId, route.subtopicId, q, opts, idx, exp) },
                            onDeleteMcq = { viewModel.deleteMcq(it) },
                            onStartMcqPractice = { navController.navigate(McqPracticeRoute(route.subjectId, route.topicId, route.subtopicId)) }
                        )
                    }

                    composable<PdfViewerRoute> { entry ->
                        val route = entry.toRoute<PdfViewerRoute>()
                        val note = route.noteId.takeIf { it.isNotBlank() }?.let { id -> notes.firstOrNull { it.id == id } }
                        val resolvedNoteId = note?.id ?: route.title ?: route.localFileName ?: "pdf"
                        val file = viewModel.resolveNoteFile(note, route.localFileName)
                        PdfViewerScreen(
                            note = note, pdfFile = file, directTitle = route.title, initialPage = route.initialPage,
                            initialAnnotationDoc = viewModel.loadPdfAnnotationDocument(resolvedNoteId),
                            bookmarks = bookmarks,
                            onSaveAnnotations = { doc -> viewModel.savePdfAnnotationDocument(doc, silent = false) },
                            onSaveAsNewCopy = { srcFile, n, doc -> viewModel.saveAnnotatedPdfAsNewCopy(srcFile, n, doc) },
                            onBookmarkPage = { pIdx, title ->
                                viewModel.toggleBookmark(BookmarkType.PDF_PAGE, "$title (Page ${pIdx + 1})", "PDF Bookmark", resolvedNoteId, note?.subjectId ?: "", note?.topicId ?: "", note?.subtopicId ?: "", pIdx)
                            },
                            onDownloadSharedPdf = if (note?.isShared == true) { { viewModel.downloadSharedNote(note) } } else null,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<ImageViewerRoute> { entry ->
                        val route = entry.toRoute<ImageViewerRoute>()
                        val note = notes.firstOrNull { it.id == route.noteId }
                        val file = viewModel.resolveNoteFile(note, note?.localFileName)
                        ImageViewerScreen(
                            note = note, imageFile = file,
                            onDownloadSharedImage = if (note?.isShared == true) { { viewModel.downloadSharedNote(note) } } else null,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<HtmlTestRoute> { entry ->
                        val route = entry.toRoute<HtmlTestRoute>()
                        val note = notes.firstOrNull { it.id == route.noteId }
                        val subjectName = if (route.subjectId == FullLengthExamDefaults.SUBJECT_ID) {
                            fullLengthExams.firstOrNull { it.id == route.topicId }?.name ?: "Full Length Test"
                        } else {
                            subjects.firstOrNull { it.id == route.subjectId }?.nameEn ?: "Study"
                        }
                        HtmlTestScreen(
                            note = note, subjectId = route.subjectId, topicId = route.topicId, subtopicId = route.subtopicId,
                            isTestMode = route.isTestMode, existingDraft = viewModel.loadInProgressTestAttempt(route.noteId),
                            loadHtml = { viewModel.loadHtmlContent(it) },
                            onSaveDraft = { viewModel.saveInProgressTestAttempt(it) },
                            onBack = { navController.popBackStack() },
                            onSubmitFinalResult = { obtained, total, totalQ, attempted, correct, wrong, unattempted, durSec, mode, breakdown ->
                                viewModel.saveDetailedTestResult(
                                    subjectId = route.subjectId, topicId = route.topicId, subtopicId = route.subtopicId,
                                    noteId = route.noteId, testTitle = note?.title ?: "HTML Mock Test",
                                    scoreObtained = obtained, totalMarks = total, totalQuestions = totalQ,
                                    attemptedCount = attempted, correctCount = correct, wrongCount = wrong,
                                    unattemptedCount = unattempted, durationSeconds = durSec, testMode = mode,
                                    questionsBreakdown = breakdown, subjectName = subjectName
                                ) { resultId ->
                                    navController.navigate(TestResultRoute(resultId)) {
                                        popUpTo<HtmlTestRoute> { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable<TestResultRoute> { entry ->
                        val route = entry.toRoute<TestResultRoute>()
                        val res = testResults.firstOrNull { it.id == route.resultId } ?: lastReturnedScore?.takeIf { it.id == route.resultId }
                        val questions = viewModel.parseBreakdown(res?.breakdownJson)
                        TestResultScreen(
                            result = res, questions = questions, bookmarks = bookmarks,
                            onBack = { navController.popBackStack() },
                            onRetryTest = res?.noteId?.let { nid ->
                                {
                                    navController.navigate(HtmlTestRoute(nid, res.subjectId, res.topicId, res.subtopicId, true)) {
                                        popUpTo<TestResultRoute> { inclusive = true }
                                    }
                                }
                            },
                            onExportPdf = { r, share -> viewModel.exportTestResultPdf(r, share) },
                            onBookmarkQuestion = { q, bType ->
                                viewModel.toggleBookmark(
                                    bookmarkType = bType, title = q.questionText,
                                    subtitle = "Ans: ${q.correctOption} • ${res?.testTitle ?: "Mock Test"}",
                                    refId = res?.id ?: "q_${q.questionNumber}",
                                    subjectId = res?.subjectId ?: "", topicId = res?.topicId ?: "", subtopicId = res?.subtopicId ?: ""
                                )
                            }
                        )
                    }

                    composable<McqPracticeRoute> { entry ->
                        val route = entry.toRoute<McqPracticeRoute>()
                        val subtopic = subtopics.firstOrNull { it.id == route.subtopicId }
                        val subjectName = subjects.firstOrNull { it.id == route.subjectId }?.nameEn ?: "Study"
                        val subMcqs = mcqs.filter { it.subtopicId == route.subtopicId }
                        McqPracticeScreen(
                            subtopicName = subtopic?.name ?: "Practice", mcqs = subMcqs,
                            onBack = { navController.popBackStack() },
                            onComplete = { correct, total ->
                                viewModel.saveDetailedTestResult(
                                    subjectId = route.subjectId, topicId = route.topicId, subtopicId = route.subtopicId,
                                    noteId = null, testTitle = "${subtopic?.name ?: "MCQ"} Practice",
                                    scoreObtained = correct.toDouble(), totalMarks = total.toDouble(),
                                    totalQuestions = total, attemptedCount = total, correctCount = correct,
                                    wrongCount = (total - correct).coerceAtLeast(0), unattemptedCount = 0,
                                    durationSeconds = 0L, testMode = "PRACTICE", questionsBreakdown = emptyList(),
                                    subjectName = subjectName
                                ) { resultId ->
                                    navController.navigate(TestResultRoute(resultId)) {
                                        popUpTo<McqPracticeRoute> { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable<PersonalNotesRoute> { entry ->
                        val route = entry.toRoute<PersonalNotesRoute>()
                        PersonalNotesScreen(
                            notes = personalNotes, subjects = subjects,
                            initialSubjectId = route.subjectId, initialNoteId = route.noteId,
                            userFilesDir = fileStorage.userDir(sessionUser.uid),
                            onSaveNote = { viewModel.upsertPersonalNote(it) },
                            onDeleteNote = { viewModel.deletePersonalNote(it) },
                            onAttachImage = { nId, uri, cb -> viewModel.copyPersonalNoteImage(nId, uri, cb) },
                            onExportPdf = { n, blks, share -> viewModel.exportPersonalNotePdf(n, blks, share) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<BookmarksRoute> {
                        BookmarksScreen(
                            bookmarks = bookmarks,
                            onOpenBookmark = { item ->
                                when (BookmarkType.fromRaw(item.bookmarkType)) {
                                    BookmarkType.PDF_PAGE -> navController.navigate(PdfViewerRoute(noteId = item.refId, initialPage = item.pageIndex))
                                    BookmarkType.FAVORITE_NOTE -> notes.firstOrNull { it.id == item.refId }?.let { openNoteDestination(it) }
                                    BookmarkType.SAVED_TOPIC -> {
                                        if (item.topicId.isNotBlank() && item.subjectId.isNotBlank()) navController.navigate(SubtopicsRoute(item.subjectId, item.topicId))
                                        else if (item.subjectId.isNotBlank()) navController.navigate(TopicsRoute(item.subjectId))
                                    }
                                    else -> {
                                        if (item.subjectId.isNotBlank() && item.topicId.isNotBlank() && item.subtopicId.isNotBlank()) {
                                            navController.navigate(SubtopicDetailRoute(item.subjectId, item.topicId, item.subtopicId))
                                        }
                                    }
                                }
                            },
                            onDeleteBookmark = { viewModel.deleteBookmark(it) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AdminPanelRoute> {
                        val adminContent by viewModel.adminSharedContent.collectAsStateWithLifecycle()
                        val adminTopics by viewModel.adminSharedTopics.collectAsStateWithLifecycle()
                        val adminSubtopics by viewModel.adminSharedSubtopics.collectAsStateWithLifecycle()
                        val studentSummaries by viewModel.studentSummaries.collectAsStateWithLifecycle()
                        val adminRequests by viewModel.adminWhatsAppRequests.collectAsStateWithLifecycle()
                        val uploadProgress by viewModel.adminUploadProgress.collectAsStateWithLifecycle()
                        AdminPanelScreen(
                            isAdminUser = isAdminUser, subjects = subjects,
                            allTopics = if (adminTopics.isNotEmpty()) adminTopics else topics,
                            allSubtopics = if (adminSubtopics.isNotEmpty()) adminSubtopics else subtopics,
                            sharedContent = adminContent, students = studentSummaries, requests = adminRequests,
                            uploadProgress = uploadProgress, currentWhatsAppDigits = localSettings.adminWhatsAppNumber,
                            publishedCatalogLink = publishedCatalogLink,
                            onPublishCatalogToDrive = { viewModel.adminPublishCatalogToDrive(activity) },
                            onUploadSharedContent = { sId, tId, stId, fold, title, type, isTest, uri, pub, existing ->
                                viewModel.adminUploadSharedContent(sId, tId, stId, fold, title, type, isTest, uri, pub, existing)
                            },
                            onTogglePublished = { viewModel.adminToggleSharedContentPublished(it) },
                            onDeleteSharedContent = { viewModel.adminDeleteSharedContent(it) },
                            onPreviewSharedNote = { openNoteDestination(it) },
                            onAddSharedTopic = { sId, name, desc -> viewModel.adminUpsertSharedTopic(sId, name, desc) },
                            onAddSharedSubtopic = { sId, tId, name -> viewModel.adminUpsertSharedSubtopic(sId, tId, name) },
                            onLoadStudentResults = { uid, cb -> viewModel.adminLoadStudentResults(uid, cb) },
                            onSaveWhatsAppDigits = { viewModel.saveAdminWhatsAppNumber(it) },
                            onUpdateWhatsAppRequestStatus = { rId, status -> viewModel.adminUpdateWhatsAppRequestStatus(rId, status) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<RevisionPlannerRoute> {
                        RevisionPlannerScreen(
                            studyLogsState = studyLogsState, revisionDiaryState = revisionDiaryState,
                            subjectsState = subjectsState, profileState = profileState,
                            localSettings = localSettings,
                            onAddStudyPlanItem = { dateKey, type, title, subj, noteText -> viewModel.addStudyPlanItem(dateKey, type, title, subj, noteText) },
                            onToggleCompleted = { viewModel.toggleStudyLogCompleted(it) },
                            onDeleteLog = { viewModel.deleteStudyLog(it) },
                            onAddDiaryEntry = { title, subj, src, content, pdfUri -> viewModel.addRevisionDiaryEntry(title, subj, src, content, pdfUri) },
                            onUpdateDiaryEntry = { id, title, subj, src, content, pdfUri, existingPdfTitle, existingPdfFileName, removePdf ->
                                viewModel.updateRevisionDiaryEntry(id, title, subj, src, content, pdfUri, existingPdfTitle, existingPdfFileName, removePdf)
                            },
                            onDeleteDiaryEntry = { viewModel.deleteRevisionDiaryEntry(it) },
                            onOpenDiaryPdf = { fileName, title -> navController.navigate(PdfViewerRoute(localFileName = fileName, title = title)) },
                            onSaveExamGoal = { name, millis, subjList, autoPlan -> viewModel.updateExamGoal(name, millis, subjList, autoPlan) },
                            onSaveReminderPrefs = { enabled, hour, min, summary -> viewModel.saveReminderPreferences(enabled, hour, min, summary) }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            localSettings = localSettings,
                            signedInEmail = sessionUser.email.takeIf { it.isNotBlank() },
                            signedInName = resolvedStudentName,
                            signedInPhotoUrl = sessionUser.photoUrl,
                            uidLabel = sessionUser.uid,
                            isOfflineAccount = sessionUser.isOffline,
                            isAdminUser = isAdminUser,
                            driveSyncState = driveSyncState,
                            onSignInWithGoogle = { launchInteractiveGoogleSignIn(switchAccountFirst = false) },
                            onSwitchGoogleAccount = { launchInteractiveGoogleSignIn(switchAccountFirst = true) },
                            onSyncNow = { viewModel.triggerSyncNow(activity) },
                            onRestoreFromDrive = { viewModel.triggerRestoreFromDrive(activity) },
                            onToggleAutoSync = { viewModel.setAutoSyncEnabled(it) },
                            onLoadSharedCatalogUrl = { viewModel.importSharedCatalogFromUrl(it) },
                            onToggleLightDark = { viewModel.toggleLightDarkTheme() },
                            onSelectTheme = { viewModel.selectTheme(it) },
                            onToggleBiometricLock = { viewModel.setBiometricLockEnabled(it) },
                            onLockNow = { isVaultUnlocked = false },
                            onOpenAdminPanel = { navController.navigate(AdminPanelRoute) },
                            onOpenSupport = { whatsAppPrefill = WhatsAppRequestPrefill() },
                            onSignedOut = onSignedOutResetSession
                        )
                    }
                }
            }
        }
    }

    val prefill = whatsAppPrefill
    if (prefill != null) {
        WhatsAppRequestDialog(
            studentName = resolvedStudentName,
            initialSubject = prefill.subject,
            initialTopic = prefill.topic,
            initialSubtopic = prefill.subtopic,
            savedAdminNumber = localSettings.adminWhatsAppNumber,
            onRecordRequest = { reqType, subj, top, sub, extra ->
                viewModel.recordWhatsAppRequest(reqType, subj, top, sub, extra)
            },
            onShowMessage = { viewModel.showToast(it) },
            onDismiss = { whatsAppPrefill = null }
        )
    }
}
