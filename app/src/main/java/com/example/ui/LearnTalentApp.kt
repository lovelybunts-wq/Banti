package com.example.ui

import android.app.Application
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
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalFileStorage
import com.example.data.local.LocalOfflineStore
import com.example.data.local.UserPreferencesManager
import com.example.data.model.NoteType
import com.example.data.model.UiState
import com.example.data.remote.GoogleDriveBackupManager
import com.example.data.remote.StudyRepository
import com.example.auth.GoogleAuthHelper
import com.example.ui.auth.AuthScreen
import com.example.ui.components.WhatsAppRequestDialog
import com.example.ui.navigation.HtmlTestRoute
import com.example.ui.navigation.ImageViewerRoute
import com.example.ui.navigation.McqPracticeRoute
import com.example.ui.navigation.PdfViewerRoute
import com.example.ui.navigation.RevisionPlannerRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.SubjectsHomeRoute
import com.example.ui.navigation.SubtopicDetailRoute
import com.example.ui.navigation.SubtopicsRoute
import com.example.ui.navigation.TopicsRoute
import com.example.ui.screens.HomeSubjectsScreen
import com.example.ui.screens.HtmlTestScreen
import com.example.ui.screens.ImageViewerScreen
import com.example.ui.screens.McqPracticeScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.RevisionPlannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubtopicDetailScreen
import com.example.ui.screens.SubtopicsScreen
import com.example.ui.screens.TopicsScreen
import com.example.ui.security.BiometricLockScreen
import com.example.ui.theme.LearnTalentTheme
import com.example.ui.theme.LocalStudyTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private data class WhatsAppRequestPrefill(
    val subject: String = "",
    val topic: String = "",
    val subtopic: String = "",
)

@Composable
fun LearnTalentApp(
    isCompactWidth: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPreferencesManager(context.applicationContext) }
    val fileStorage = remember { LocalFileStorage(context.applicationContext) }
    val localSettings by prefs.settingsFlow.collectAsStateWithLifecycle(initialValue = LocalAppSettings())
    val customLogoFile = remember(localSettings.customLogoFileName) {
        fileStorage.getCustomLogoFile(localSettings.customLogoFileName)
    }

    val firebaseReady = remember { GoogleAuthHelper.isFirebaseInitialized(context) }
    val currentUser by remember(firebaseReady) {
        if (firebaseReady) Firebase.auth.authStateFlow() else flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = GoogleAuthHelper.safeCurrentUser(context))

    var pendingInitialDriveToken by remember { mutableStateOf<String?>(null) }

    LearnTalentTheme(themeOption = localSettings.themeOption) {
        val user = currentUser
        val driveUid = localSettings.driveUserId.takeIf { it.isNotBlank() }
        if (user == null && driveUid == null && !localSettings.offlineModeAccepted) {
            AuthScreen(
                customLogoFile = customLogoFile,
                onAuthSuccess = {},
                onDriveSignInSuccess = { profile, token ->
                    pendingInitialDriveToken = token
                    scope.launch {
                        prefs.setDriveUserSession(profile.id, profile.email, profile.name)
                        prefs.setOfflineModeAccepted(false)
                    }
                },
                onContinueOffline = {
                    scope.launch { prefs.setOfflineModeAccepted(true) }
                }
            )
        } else {
            val sessionKey = user?.uid ?: driveUid ?: LocalOfflineStore.OFFLINE_UID
            val resolvedDisplayName = user?.displayName?.takeIf { it.isNotBlank() }
                ?: localSettings.driveUserName.takeIf { it.isNotBlank() }
                ?: "Offline Learner"
            val resolvedEmail = user?.email?.takeIf { it.isNotBlank() }
                ?: localSettings.driveUserEmail.takeIf { it.isNotBlank() }
            key(sessionKey) {
                AuthenticatedAppContent(
                    firebaseUid = user?.uid,
                    driveUserId = driveUid,
                    signedInEmail = resolvedEmail,
                    displayName = resolvedDisplayName,
                    initialDriveToken = pendingInitialDriveToken,
                    onConsumeInitialDriveToken = { pendingInitialDriveToken = null },
                    isCompactWidth = isCompactWidth,
                    onDriveSignInSuccess = { profile, token ->
                        pendingInitialDriveToken = token
                        scope.launch {
                            prefs.setDriveUserSession(profile.id, profile.email, profile.name)
                            prefs.setOfflineModeAccepted(false)
                        }
                    },
                    onSignedOutResetSession = {
                        pendingInitialDriveToken = null
                        scope.launch {
                            prefs.clearDriveUserSession()
                            prefs.setOfflineModeAccepted(false)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedAppContent(
    firebaseUid: String?,
    driveUserId: String?,
    signedInEmail: String?,
    displayName: String,
    initialDriveToken: String?,
    onConsumeInitialDriveToken: () -> Unit,
    isCompactWidth: Boolean,
    onDriveSignInSuccess: (profile: com.example.data.remote.GoogleDriveProfile, accessToken: String) -> Unit,
    onSignedOutResetSession: () -> Unit,
) {
    val sessionKey = firebaseUid
        ?: driveUserId?.takeIf { it.isNotBlank() }
        ?: LocalOfflineStore.OFFLINE_UID
    val driveBackupManager = remember { GoogleDriveBackupManager() }
    val isAdminUser = signedInEmail.equals(UserPreferencesManager.ADMIN_EMAIL, ignoreCase = true)

    val viewModel: MainViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" } as Application
                val repo = if (firebaseUid != null) {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    StudyRepository(FirebaseFirestore.getInstance(databaseId))
                } else null
                MainViewModel(
                    appContext = app.applicationContext,
                    repository = repo,
                    offlineStore = LocalOfflineStore(app.applicationContext, ownerId = sessionKey),
                    fileStorage = LocalFileStorage(app.applicationContext),
                    prefs = UserPreferencesManager(app.applicationContext),
                    driveBackupManager = driveBackupManager,
                    firebaseUid = firebaseUid,
                    driveUserId = driveUserId,
                    initialDisplayName = displayName,
                )
            }
        }
    )

    LaunchedEffect(initialDriveToken, viewModel) {
        if (!initialDriveToken.isNullOrBlank()) {
            viewModel.triggerDriveSyncAfterLogin(initialDriveToken)
            onConsumeInitialDriveToken()
        }
    }

    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val customLogoFile = remember(localSettings.customLogoFileName) {
        viewModel.getCustomLogoFile(localSettings.customLogoFileName)
    }
    var isVaultUnlocked by rememberSaveable(sessionKey) { mutableStateOf(false) }

    if (localSettings.biometricLockEnabled && !isVaultUnlocked) {
        BiometricLockScreen(
            customLogoFile = customLogoFile,
            onUnlocked = { isVaultUnlocked = true }
        )
        return
    }

    val theme = LocalStudyTheme.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var whatsAppPrefill by remember { mutableStateOf<WhatsAppRequestPrefill?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val topicsState by viewModel.topicsState.collectAsStateWithLifecycle()
    val subtopicsState by viewModel.subtopicsState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val videosState by viewModel.videosState.collectAsStateWithLifecycle()
    val mcqsState by viewModel.mcqsState.collectAsStateWithLifecycle()
    val testResultsState by viewModel.testResultsState.collectAsStateWithLifecycle()
    val studyLogsState by viewModel.studyLogsState.collectAsStateWithLifecycle()
    val revisionDiaryState by viewModel.revisionDiaryState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val lastReturnedScore by viewModel.lastReturnedScore.collectAsStateWithLifecycle()
    val isDriveBusy by viewModel.isDriveBusy.collectAsStateWithLifecycle()

    val subjects = (subjectsState as? UiState.Success)?.data ?: emptyList()
    val topics = (topicsState as? UiState.Success)?.data ?: emptyList()
    val subtopics = (subtopicsState as? UiState.Success)?.data ?: emptyList()
    val notes = (notesState as? UiState.Success)?.data ?: emptyList()
    val videos = (videosState as? UiState.Success)?.data ?: emptyList()
    val mcqs = (mcqsState as? UiState.Success)?.data ?: emptyList()
    val testResults = (testResultsState as? UiState.Success)?.data ?: emptyList()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isTopLevelRoute = currentDest?.let { dest ->
        dest.hasRoute<SubjectsHomeRoute>() ||
            dest.hasRoute<RevisionPlannerRoute>() ||
            dest.hasRoute<SettingsRoute>()
    } ?: true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = theme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isCompactWidth && isTopLevelRoute) {
                NavigationBar(
                    containerColor = theme.surface,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<SubjectsHomeRoute>() == true,
                        onClick = { navigateTopLevel(SubjectsHomeRoute) },
                        icon = {
                            Icon(
                                if (currentDest?.hasRoute<SubjectsHomeRoute>() == true) Icons.Rounded.AutoStories else Icons.Outlined.AutoStories,
                                contentDescription = stringResource(R.string.nav_subjects)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_subjects)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            selectedTextColor = theme.primary,
                            indicatorColor = theme.primaryContainer,
                            unselectedIconColor = theme.onSurfaceVariant,
                            unselectedTextColor = theme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<RevisionPlannerRoute>() == true,
                        onClick = { navigateTopLevel(RevisionPlannerRoute) },
                        icon = {
                            Icon(
                                if (currentDest?.hasRoute<RevisionPlannerRoute>() == true) Icons.AutoMirrored.Rounded.EventNote else Icons.AutoMirrored.Outlined.EventNote,
                                contentDescription = stringResource(R.string.nav_revision)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_revision)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            selectedTextColor = theme.primary,
                            indicatorColor = theme.primaryContainer,
                            unselectedIconColor = theme.onSurfaceVariant,
                            unselectedTextColor = theme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = currentDest?.hasRoute<SettingsRoute>() == true,
                        onClick = { navigateTopLevel(SettingsRoute) },
                        icon = {
                            Icon(
                                if (currentDest?.hasRoute<SettingsRoute>() == true) Icons.Rounded.Tune else Icons.Outlined.Tune,
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            selectedTextColor = theme.primary,
                            indicatorColor = theme.primaryContainer,
                            unselectedIconColor = theme.onSurfaceVariant,
                            unselectedTextColor = theme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(innerPadding)
        ) {
            if (!isCompactWidth && isTopLevelRoute) {
                NavigationRail(
                    containerColor = theme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<SubjectsHomeRoute>() == true,
                        onClick = { navigateTopLevel(SubjectsHomeRoute) },
                        icon = { Icon(Icons.Rounded.AutoStories, contentDescription = stringResource(R.string.nav_subjects)) },
                        label = { Text(stringResource(R.string.nav_subjects)) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            selectedTextColor = theme.primary,
                            indicatorColor = theme.primaryContainer
                        )
                    )
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<RevisionPlannerRoute>() == true,
                        onClick = { navigateTopLevel(RevisionPlannerRoute) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.EventNote, contentDescription = stringResource(R.string.nav_revision)) },
                        label = { Text(stringResource(R.string.nav_revision)) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            selectedTextColor = theme.primary,
                            indicatorColor = theme.primaryContainer
                        )
                    )
                    NavigationRailItem(
                        selected = currentDest?.hasRoute<SettingsRoute>() == true,
                        onClick = { navigateTopLevel(SettingsRoute) },
                        icon = { Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.nav_settings)) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = theme.primary,
                            selectedTextColor = theme.primary,
                            indicatorColor = theme.primaryContainer
                        )
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = SubjectsHomeRoute
                ) {
                    composable<SubjectsHomeRoute> {
                        HomeSubjectsScreen(
                            subjectsState = subjectsState,
                            topicsState = topicsState,
                            notesState = notesState,
                            studyLogsState = studyLogsState,
                            revisionDiaryState = revisionDiaryState,
                            profileState = profileState,
                            cloudSyncEnabled = localSettings.cloudSyncEnabled,
                            isOfflineSession = viewModel.isOfflineSession,
                            customLogoFile = customLogoFile,
                            onSubjectClick = { subject ->
                                navController.navigate(TopicsRoute(subjectId = subject.id))
                            },
                            onOpenRevisionTab = { navigateTopLevel(RevisionPlannerRoute) },
                            onOpenSettingsTab = { navigateTopLevel(SettingsRoute) },
                            onOpenWhatsAppRequest = { whatsAppPrefill = WhatsAppRequestPrefill() },
                            onAddSubject = { en, hi, icon, hex, order ->
                                viewModel.addSubject(en, hi, icon, hex, order)
                            }
                        )
                    }

                    composable<TopicsRoute> { entry ->
                        val route = entry.toRoute<TopicsRoute>()
                        val subject = subjects.firstOrNull { it.id == route.subjectId }
                        val subjectTopics = topics.filter { it.subjectId == route.subjectId }
                        TopicsScreen(
                            subject = subject,
                            topics = subjectTopics,
                            subtopics = subtopics,
                            onBack = { navController.popBackStack() },
                            onTopicClick = { topic ->
                                navController.navigate(SubtopicsRoute(subjectId = route.subjectId, topicId = topic.id))
                            },
                            onAddTopic = { name, desc -> viewModel.addTopic(route.subjectId, name, desc) },
                            onDeleteTopic = { viewModel.deleteTopic(it) }
                        )
                    }

                    composable<SubtopicsRoute> { entry ->
                        val route = entry.toRoute<SubtopicsRoute>()
                        val subject = subjects.firstOrNull { it.id == route.subjectId }
                        val topic = topics.firstOrNull { it.id == route.topicId }
                        val topicSubtopics = subtopics.filter { it.topicId == route.topicId }
                        SubtopicsScreen(
                            subject = subject,
                            topic = topic,
                            subtopics = topicSubtopics,
                            notes = notes,
                            videos = videos,
                            mcqs = mcqs,
                            onBack = { navController.popBackStack() },
                            onSubtopicClick = { sub ->
                                navController.navigate(
                                    SubtopicDetailRoute(
                                        subjectId = route.subjectId,
                                        topicId = route.topicId,
                                        subtopicId = sub.id
                                    )
                                )
                            },
                            onAddSubtopic = { name -> viewModel.addSubtopic(route.subjectId, route.topicId, name) },
                            onDeleteSubtopic = { viewModel.deleteSubtopic(it) }
                        )
                    }

                    composable<SubtopicDetailRoute> { entry ->
                        val route = entry.toRoute<SubtopicDetailRoute>()
                        val subject = subjects.firstOrNull { it.id == route.subjectId }
                        val topic = topics.firstOrNull { it.id == route.topicId }
                        val subtopic = subtopics.firstOrNull { it.id == route.subtopicId }
                        val subNotes = notes.filter { it.subtopicId == route.subtopicId }
                        val subVideos = videos.filter { it.subtopicId == route.subtopicId }
                        val subMcqs = mcqs.filter { it.subtopicId == route.subtopicId }
                        val subResults = testResults.filter { it.subtopicId == route.subtopicId }
                        val subjectName = subject?.nameEn ?: "Study"

                        SubtopicDetailScreen(
                            subject = subject,
                            topic = topic,
                            subtopic = subtopic,
                            notes = subNotes,
                            videos = subVideos,
                            mcqs = subMcqs,
                            testResults = subResults,
                            lastReturnedScore = lastReturnedScore,
                            onDismissScoreBanner = { viewModel.clearLastReturnedScore() },
                            onBack = { navController.popBackStack() },
                            onMarkStudiedToday = {
                                if (subtopic != null) viewModel.markSubtopicStudiedToday(subtopic, subjectName)
                            },
                            onRequestMaterialWhatsApp = {
                                whatsAppPrefill = WhatsAppRequestPrefill(
                                    subject = subject?.nameEn ?: "",
                                    topic = topic?.name ?: "",
                                    subtopic = subtopic?.name ?: ""
                                )
                            },
                            onCreateFolder = { folder ->
                                viewModel.createFolder(route.subjectId, route.topicId, route.subtopicId, folder)
                            },
                            onImportFile = { title, folder, type, uri ->
                                viewModel.importFileNote(route.subjectId, route.topicId, route.subtopicId, folder, title, type, uri)
                            },
                            onCreateSampleHtmlTest = { title, folder ->
                                viewModel.createSampleHtmlTest(route.subjectId, route.topicId, route.subtopicId, folder, title)
                            },
                            onRenameOrMoveNote = { id, title, folder ->
                                viewModel.renameOrMoveNote(id, title, folder)
                            },
                            onOpenNote = { note ->
                                viewModel.markNoteOpenedToday(note, subjectName)
                                when (note.resolvedType()) {
                                    NoteType.PDF -> navController.navigate(PdfViewerRoute(note.id))
                                    NoteType.IMAGE -> navController.navigate(ImageViewerRoute(note.id))
                                    NoteType.HTML -> navController.navigate(
                                        HtmlTestRoute(
                                            noteId = note.id,
                                            subjectId = route.subjectId,
                                            topicId = route.topicId,
                                            subtopicId = route.subtopicId,
                                            isTestMode = true
                                        )
                                    )
                                    NoteType.FOLDER -> Unit
                                }
                            },
                            onOpenHtmlTest = { note ->
                                viewModel.markNoteOpenedToday(note, subjectName)
                                navController.navigate(
                                    HtmlTestRoute(
                                        noteId = note.id,
                                        subjectId = route.subjectId,
                                        topicId = route.topicId,
                                        subtopicId = route.subtopicId,
                                        isTestMode = true
                                    )
                                )
                            },
                            onDeleteNote = { viewModel.deleteNote(it) },
                            onAddVideo = { title, url, noteText ->
                                viewModel.addVideo(route.subjectId, route.topicId, route.subtopicId, title, url, noteText)
                            },
                            onWatchVideo = { video -> viewModel.markVideoWatched(video, subjectName) },
                            onDeleteVideo = { viewModel.deleteVideo(it) },
                            onAddMcq = { q, opts, idx, exp ->
                                viewModel.addMcq(route.subjectId, route.topicId, route.subtopicId, q, opts, idx, exp)
                            },
                            onDeleteMcq = { viewModel.deleteMcq(it) },
                            onStartMcqPractice = {
                                navController.navigate(
                                    McqPracticeRoute(route.subjectId, route.topicId, route.subtopicId)
                                )
                            }
                        )
                    }

                    composable<PdfViewerRoute> { entry ->
                        val route = entry.toRoute<PdfViewerRoute>()
                        val note = route.noteId.takeIf { it.isNotBlank() }?.let { id ->
                            notes.firstOrNull { it.id == id }
                        }
                        val resolvedFileName = note?.localFileName ?: route.localFileName
                        val file = viewModel.getLocalFile(resolvedFileName)
                        PdfViewerScreen(
                            note = note,
                            pdfFile = file,
                            directTitle = route.title,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<ImageViewerRoute> { entry ->
                        val route = entry.toRoute<ImageViewerRoute>()
                        val note = notes.firstOrNull { it.id == route.noteId }
                        val file = viewModel.getLocalFile(note?.localFileName)
                        ImageViewerScreen(
                            note = note,
                            imageFile = file,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<HtmlTestRoute> { entry ->
                        val route = entry.toRoute<HtmlTestRoute>()
                        val note = notes.firstOrNull { it.id == route.noteId }
                        val subjectName = subjects.firstOrNull { it.id == route.subjectId }?.nameEn ?: "Study"
                        HtmlTestScreen(
                            note = note,
                            isTestMode = route.isTestMode,
                            loadHtml = { viewModel.loadHtmlContent(it) },
                            onBack = { navController.popBackStack() },
                            onScoreSubmitted = { obtained, total, summary ->
                                viewModel.saveTestScore(
                                    subjectId = route.subjectId,
                                    topicId = route.topicId,
                                    subtopicId = route.subtopicId,
                                    noteId = route.noteId,
                                    testTitle = note?.title ?: "HTML Mock Test",
                                    scoreObtained = obtained,
                                    totalMarks = total,
                                    summaryText = summary,
                                    subjectName = subjectName
                                )
                                navController.popBackStack()
                            }
                        )
                    }

                    composable<McqPracticeRoute> { entry ->
                        val route = entry.toRoute<McqPracticeRoute>()
                        val subtopic = subtopics.firstOrNull { it.id == route.subtopicId }
                        val subjectName = subjects.firstOrNull { it.id == route.subjectId }?.nameEn ?: "Study"
                        val subMcqs = mcqs.filter { it.subtopicId == route.subtopicId }
                        McqPracticeScreen(
                            subtopicName = subtopic?.name ?: "Practice",
                            mcqs = subMcqs,
                            onBack = { navController.popBackStack() },
                            onComplete = { correct, total ->
                                viewModel.saveTestScore(
                                    subjectId = route.subjectId,
                                    topicId = route.topicId,
                                    subtopicId = route.subtopicId,
                                    noteId = null,
                                    testTitle = "${subtopic?.name ?: "MCQ"} Practice",
                                    scoreObtained = correct.toDouble(),
                                    totalMarks = total.toDouble(),
                                    summaryText = "$correct / $total Correct",
                                    subjectName = subjectName
                                )
                                navController.popBackStack()
                            }
                        )
                    }

                    composable<RevisionPlannerRoute> {
                        RevisionPlannerScreen(
                            studyLogsState = studyLogsState,
                            revisionDiaryState = revisionDiaryState,
                            subjectsState = subjectsState,
                            profileState = profileState,
                            localSettings = localSettings,
                            customLogoFile = customLogoFile,
                            onAddStudyPlanItem = { dateKey, type, title, subj, noteText ->
                                viewModel.addStudyPlanItem(dateKey, type, title, subj, noteText)
                            },
                            onToggleCompleted = { viewModel.toggleStudyLogCompleted(it) },
                            onDeleteLog = { viewModel.deleteStudyLog(it) },
                            onAddDiaryEntry = { title, subj, src, content, pdfUri ->
                                viewModel.addRevisionDiaryEntry(title, subj, src, content, pdfUri)
                            },
                            onUpdateDiaryEntry = { id, title, subj, src, content, pdfUri, existingPdfTitle, existingPdfFileName, removePdf ->
                                viewModel.updateRevisionDiaryEntry(
                                    entryId = id,
                                    title = title,
                                    subjectName = subj,
                                    sourceName = src,
                                    content = content,
                                    pdfUri = pdfUri,
                                    existingPdfTitle = existingPdfTitle,
                                    existingPdfFileName = existingPdfFileName,
                                    removePdf = removePdf
                                )
                            },
                            onDeleteDiaryEntry = { viewModel.deleteRevisionDiaryEntry(it) },
                            onOpenDiaryPdf = { fileName, title ->
                                navController.navigate(
                                    PdfViewerRoute(localFileName = fileName, title = title)
                                )
                            },
                            onSaveExamGoal = { name, millis, subjList, autoPlan ->
                                viewModel.updateExamGoal(name, millis, subjList, autoPlan)
                            },
                            onSaveReminderPrefs = { enabled, hour, min, summary ->
                                viewModel.saveReminderPreferences(enabled, hour, min, summary)
                            }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            localSettings = localSettings,
                            isOfflineSession = viewModel.isOfflineSession,
                            signedInEmail = signedInEmail,
                            signedInName = displayName,
                            isAdminUser = isAdminUser,
                            isDriveBusy = isDriveBusy,
                            customLogoFile = customLogoFile,
                            driveBackupManager = driveBackupManager,
                            onSelectTheme = { viewModel.selectTheme(it) },
                            onToggleBiometricLock = { viewModel.setBiometricLockEnabled(it) },
                            onLockNow = { isVaultUnlocked = false },
                            onToggleCloudSync = { viewModel.setCloudSyncEnabled(it) },
                            onSaveWhatsAppNumber = { viewModel.saveAdminWhatsAppNumber(it) },
                            onOpenWhatsAppRequest = { whatsAppPrefill = WhatsAppRequestPrefill() },
                            onPickCustomLogo = { viewModel.saveCustomLogo(it) },
                            onResetCustomLogo = { viewModel.resetCustomLogo() },
                            onDriveBackupWithToken = { viewModel.runDriveBackupWithToken(it) },
                            onDriveRestoreWithToken = { viewModel.runDriveRestoreWithToken(it) },
                            onBuildBackupJson = { viewModel.buildBackupJson() },
                            onRestoreBackupJson = { viewModel.restoreFromBackupJson(it) },
                            onShowMessage = { viewModel.showToast(it) },
                            onSignedOut = onSignedOutResetSession,
                            onSignedInFromOffline = {
                                viewModel.showToast("Google लॉगिन सफल! डेटा सिंक हो रहा है...")
                            },
                            onDriveSignInSuccess = onDriveSignInSuccess
                        )
                    }
                }
            }
        }
    }

    val prefill = whatsAppPrefill
    if (prefill != null) {
        WhatsAppRequestDialog(
            initialSubject = prefill.subject,
            initialTopic = prefill.topic,
            initialSubtopic = prefill.subtopic,
            savedAdminNumber = localSettings.adminWhatsAppNumber,
            onShowMessage = { viewModel.showToast(it) },
            onDismiss = { whatsAppPrefill = null }
        )
    }
}
