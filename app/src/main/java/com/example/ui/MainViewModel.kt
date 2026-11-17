package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.GoogleAuthHelper
import com.example.auth.GoogleSessionUser
import com.example.data.local.LocalAdminCatalogStore
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalFileStorage
import com.example.data.local.LocalOfflineStore
import com.example.data.local.PdfExportHelper
import com.example.data.local.SharedDownloadRecord
import com.example.data.local.StudyDateUtils
import com.example.data.local.UserPreferencesManager
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.CloudPdfAnnotationDoc
import com.example.data.model.FullLengthExam
import com.example.data.model.FullLengthExamDefaults
import com.example.data.model.InProgressTestAttempt
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.PdfAnnotationDocument
import com.example.data.model.PersonalNote
import com.example.data.model.PersonalNoteBlock
import com.example.data.model.QuestionAttemptRecord
import com.example.data.model.RecentActivityItem
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudentSummary
import com.example.data.model.StudentUploadSubmission
import com.example.data.model.StudyLog
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoLink
import com.example.data.model.WhatsAppRequestRecord
import com.example.data.remote.DriveAuthOutcome
import com.example.data.remote.GoogleDriveBackupManager
import com.example.ui.theme.StudyThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

sealed interface DriveSyncUiState {
    data object OfflineAccount : DriveSyncUiState
    data class Synced(val lastSyncedMillis: Long, val detail: String = "Google Drive Synced") : DriveSyncUiState
    data class Syncing(val message: String = "Syncing to Google Drive...") : DriveSyncUiState
    data class PendingOffline(val message: String = "Offline edits pending sync") : DriveSyncUiState
    data class NeedAuth(val message: String = "Sign in / authorize Drive to sync") : DriveSyncUiState
    data class Error(val message: String) : DriveSyncUiState
}

class MainViewModel(
    private val appContext: Context,
    private val offlineStore: LocalOfflineStore,
    private val adminCatalogStore: LocalAdminCatalogStore,
    private val driveManager: GoogleDriveBackupManager,
    private val fileStorage: LocalFileStorage,
    private val prefs: UserPreferencesManager,
    val sessionUser: GoogleSessionUser,
    initialDriveToken: String? = null,
) : ViewModel() {

    val storageOwnerId: String = sessionUser.uid
    val isOfflineAccount: Boolean = sessionUser.isOffline
    val signedInEmail: String? = sessionUser.email.takeIf { it.isNotBlank() }
    val initialDisplayName: String = sessionUser.displayName
    val initialPhotoUrl: String? = sessionUser.photoUrl
    val isAdminUser: Boolean = !isOfflineAccount && GoogleAuthHelper.isAdminEmail(signedInEmail, sessionUser.emailVerified)

    private val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var cachedDriveToken: String? = initialDriveToken
    private var pendingActionAfterConsent: String = "SYNC" // "SYNC" | "RESTORE" | "PUBLISH_CATALOG"
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val toastEvents = _toastEvents.asSharedFlow()

    private val _isNetworkOnline = MutableStateFlow(LocalFileStorage.isOnline(appContext))
    val isNetworkOnline: StateFlow<Boolean> = _isNetworkOnline.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = offlineStore.pendingSyncCount

    private val _lastReturnedScore = MutableStateFlow<TestResult?>(null)
    val lastReturnedScore: StateFlow<TestResult?> = _lastReturnedScore.asStateFlow()

    private val _sharedDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sharedDownloadProgress: StateFlow<Map<String, Float>> = _sharedDownloadProgress.asStateFlow()

    private val _sharedDownloadsMap = MutableStateFlow(fileStorage.loadSharedDownloadsMap())
    val sharedDownloadsMap: StateFlow<Map<String, SharedDownloadRecord>> = _sharedDownloadsMap.asStateFlow()

    private val _adminUploadProgress = MutableStateFlow<Float?>(null)
    val adminUploadProgress: StateFlow<Float?> = _adminUploadProgress.asStateFlow()

    private val _recentActivities = MutableStateFlow(fileStorage.loadRecentActivities(storageOwnerId))
    val recentActivities: StateFlow<List<RecentActivityItem>> = _recentActivities.asStateFlow()

    private val _driveSyncState = MutableStateFlow<DriveSyncUiState>(
        if (isOfflineAccount) DriveSyncUiState.OfflineAccount else DriveSyncUiState.PendingOffline("Ready to sync")
    )
    val driveSyncState: StateFlow<DriveSyncUiState> = _driveSyncState.asStateFlow()

    private val _pendingConsentSender = MutableStateFlow<IntentSender?>(null)
    val pendingConsentSender: StateFlow<IntentSender?> = _pendingConsentSender.asStateFlow()

    private val _publishedCatalogLink = MutableStateFlow<String?>(null)
    val publishedCatalogLink: StateFlow<String?> = _publishedCatalogLink.asStateFlow()

    val allowStudentPersonalUploads: StateFlow<Boolean> = adminCatalogStore.allowStudentPersonalUploads

    val localSettings: StateFlow<LocalAppSettings> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LocalAppSettings())

    val profileState: StateFlow<UiState<UserProfile?>> =
        offlineStore.profile.toUiStateFlow("observeProfile")

    private val sharedSubjectsFlow = adminCatalogStore.subjects
    private val sharedTopicsFlow = adminCatalogStore.topics
    private val sharedSubtopicsFlow = adminCatalogStore.subtopics
    private val sharedContentsFlow = adminCatalogStore.contents
    private val sharedVideosFlow = adminCatalogStore.videos
    private val sharedMcqsFlow = adminCatalogStore.mcqs
    private val sharedFullLengthExamsFlow = adminCatalogStore.fullLengthExams

    private val personalSubjectsFlow = offlineStore.subjects
    private val personalTopicsFlow = offlineStore.topics
    private val personalSubtopicsFlow = offlineStore.subtopics
    private val personalNotesFlow = offlineStore.notes
    private val personalVideosFlow = offlineStore.videos
    private val personalMcqsFlow = offlineStore.mcqs
    private val personalFullLengthExamsFlow = offlineStore.fullLengthExams

    val subjectsState: StateFlow<UiState<List<Subject>>> =
        combine(personalSubjectsFlow, sharedSubjectsFlow) { p, s ->
            SharedCatalogMergeHelper.mergeSubjects(p, s)
        }.toUiStateFlow("subjects")

    val topicsState: StateFlow<UiState<List<Topic>>> =
        combine(personalTopicsFlow, sharedTopicsFlow) { p, s ->
            SharedCatalogMergeHelper.mergeTopics(p, s)
        }.toUiStateFlow("topics")

    val subtopicsState: StateFlow<UiState<List<Subtopic>>> =
        combine(personalSubtopicsFlow, sharedSubtopicsFlow) { p, s ->
            SharedCatalogMergeHelper.mergeSubtopics(p, s)
        }.toUiStateFlow("subtopics")

    val notesState: StateFlow<UiState<List<StudyNote>>> =
        combine(personalNotesFlow, sharedContentsFlow) { p, s ->
            SharedCatalogMergeHelper.mergeNotes(p, s)
        }.toUiStateFlow("notes")

    val videosState: StateFlow<UiState<List<VideoLink>>> =
        combine(personalVideosFlow, sharedVideosFlow) { p, s ->
            SharedCatalogMergeHelper.mergeVideos(p, s)
        }.toUiStateFlow("videos")

    val mcqsState: StateFlow<UiState<List<McqQuestion>>> =
        combine(personalMcqsFlow, sharedMcqsFlow) { p, s ->
            SharedCatalogMergeHelper.mergeMcqs(p, s)
        }.toUiStateFlow("mcqs")

    val fullLengthExamsState: StateFlow<UiState<List<FullLengthExam>>> =
        combine(personalFullLengthExamsFlow, sharedFullLengthExamsFlow) { personal, shared ->
            SharedCatalogMergeHelper.mergeFullLengthExams(
                builtIn = FullLengthExamDefaults.builtInExams,
                personal = personal,
                shared = shared
            )
        }.toUiStateFlow("fullLengthExams")

    val testResultsState: StateFlow<UiState<List<TestResult>>> =
        offlineStore.testResults.toUiStateFlow("testResults")

    val studyLogsState: StateFlow<UiState<List<StudyLog>>> =
        offlineStore.studyLogs.toUiStateFlow("studyLogs")

    val revisionDiaryState: StateFlow<UiState<List<RevisionDiaryEntry>>> =
        offlineStore.revisionDiary.toUiStateFlow("revisionDiary")

    val personalNotesState: StateFlow<UiState<List<PersonalNote>>> =
        offlineStore.personalNotes.toUiStateFlow("personalNotes")

    val bookmarksState: StateFlow<UiState<List<BookmarkItem>>> =
        offlineStore.bookmarks.toUiStateFlow("bookmarks")

    val pdfAnnotationsState: StateFlow<List<CloudPdfAnnotationDoc>> =
        offlineStore.pdfAnnotations
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    // Admin-only streams
    val adminSharedContent: StateFlow<List<StudyNote>> =
        (if (isAdminUser) sharedContentsFlow else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminSharedTopics: StateFlow<List<Topic>> =
        (if (isAdminUser) sharedTopicsFlow else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminSharedSubtopics: StateFlow<List<Subtopic>> =
        (if (isAdminUser) sharedSubtopicsFlow else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val studentSummaries: StateFlow<List<StudentSummary>> =
        (if (isAdminUser) adminCatalogStore.studentSummaries else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminWhatsAppRequests: StateFlow<List<WhatsAppRequestRecord>> =
        (if (isAdminUser) adminCatalogStore.whatsAppRequests else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminPendingSubmissions: StateFlow<List<StudentUploadSubmission>> =
        (if (isAdminUser) adminCatalogStore.pendingSubmissions else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        registerConnectivityObserver()
        viewModelScope.launch {
            val role = GoogleAuthHelper.resolveRole(signedInEmail, sessionUser.emailVerified)
            offlineStore.updateProfileMeta(
                displayName = initialDisplayName,
                email = signedInEmail,
                photoUrl = initialPhotoUrl,
                role = role
            )
            val savedSettings = prefs.settingsFlow.first()
            _publishedCatalogLink.value = savedSettings.sharedCatalogUrl.takeIf { it.isNotBlank() }

            if (!isOfflineAccount) {
                // Migrate offline files & edits if user started offline and then logged in with Gmail
                fileStorage.copyOfflineFilesToUser(storageOwnerId)
                val guestStore = LocalOfflineStore(appContext, ownerId = LocalOfflineStore.OFFLINE_UID)
                val guestSnap = guestStore.currentSnapshot()
                if (guestSnap.hasUserEdits) {
                    offlineStore.adoptOfflineSnapshotIfEmpty(guestSnap)
                    guestStore.markSyncedToCloud()
                }
                prefs.markSeeded(storageOwnerId)
                syncStudentAnalyticsSummary()

                if (savedSettings.lastDriveBackupMillis > 0L && !offlineStore.hasPendingCloudChanges()) {
                    _driveSyncState.value = DriveSyncUiState.Synced(
                        savedSettings.lastDriveBackupMillis,
                        savedSettings.lastDriveSyncMessage.ifBlank { "Google Drive Synced" }
                    )
                }

                // Attempt automatic Drive restore + merge + sync + manifest check on Gmail login
                if (LocalFileStorage.isOnline(appContext)) {
                    if (savedSettings.sharedCatalogUrl.isNotBlank() && !isAdminUser) {
                        checkPublishedManifestUpdates(savedSettings.sharedCatalogUrl, silent = true)
                    }
                    val token = cachedDriveToken ?: driveManager.silentGetDriveToken(appContext)
                    cachedDriveToken = token
                    if (!token.isNullOrBlank()) {
                        performDriveRestoreAndSync(token, mergeFirst = true, silent = true)
                    } else if (savedSettings.lastDriveBackupMillis == 0L) {
                        _driveSyncState.value = DriveSyncUiState.NeedAuth("Tap Sync Now to connect Google Drive")
                    }
                } else {
                    _driveSyncState.value = DriveSyncUiState.PendingOffline("Offline — will sync when online")
                }
            } else {
                _driveSyncState.value = DriveSyncUiState.OfflineAccount
            }
        }

        viewModelScope.launch {
            combine(sharedSubjectsFlow, sharedTopicsFlow, sharedSubtopicsFlow, sharedContentsFlow, sharedVideosFlow) { s, t, st, c, v ->
                Triple(Triple(s, t, st), c, v)
            }.collect { (hier, c, v) ->
                val mcqsList = (mcqsState.value as? UiState.Success)?.data?.filter { it.isShared } ?: emptyList()
                SharedCatalogMergeHelper.saveCachedCatalog(
                    fileStorage, hier.first, hier.second, hier.third, c.filter { it.published }, v.filter { it.published }, mcqsList
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val cb = networkCallback ?: return
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        }
        networkCallback = null
    }

    private fun registerConnectivityObserver() {
        runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isNetworkOnline.value = true
                    viewModelScope.launch {
                        val settings = prefs.settingsFlow.first()
                        if (!isOfflineAccount && settings.cloudSyncEnabled && offlineStore.hasPendingCloudChanges()) {
                            val token = cachedDriveToken ?: driveManager.silentGetDriveToken(appContext)
                            cachedDriveToken = token
                            if (!token.isNullOrBlank()) {
                                performDriveUploadOnly(token, silent = true)
                            }
                        }
                        if (settings.sharedCatalogUrl.isNotBlank() && !isAdminUser) {
                            checkPublishedManifestUpdates(settings.sharedCatalogUrl, silent = true)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    _isNetworkOnline.value = LocalFileStorage.isOnline(appContext)
                    if (!_isNetworkOnline.value && !isOfflineAccount && offlineStore.hasPendingCloudChanges()) {
                        _driveSyncState.value = DriveSyncUiState.PendingOffline("Offline — saved locally (${offlineStore.pendingSyncCount.value} queued)")
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        }
    }

    fun clearSessionCaches() {
        cachedDriveToken = null
        _lastReturnedScore.value = null
    }

    private fun <T> Flow<T>.toUiStateFlow(label: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error in $label", error)
                emit(UiState.Error(error.message ?: "Failed to load data"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun showToast(message: String) { _toastEvents.tryEmit(message) }
    fun clearLastReturnedScore() { _lastReturnedScore.value = null }
    fun clearPendingConsentSender() { _pendingConsentSender.value = null }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setCloudSyncEnabled(enabled)
            _toastEvents.tryEmit(if (enabled) "Auto-Sync to Google Drive enabled" else "Auto-Sync paused (Manual Sync available)")
        }
    }

    fun triggerSyncNow(activity: Activity?) {
        if (isOfflineAccount) {
            _toastEvents.tryEmit("Google Drive sync के लिए पहले Gmail से लॉगिन करें।")
            return
        }
        if (!LocalFileStorage.isOnline(appContext)) {
            _driveSyncState.value = DriveSyncUiState.PendingOffline("No internet — saved locally")
            _toastEvents.tryEmit("इंटरनेट उपलब्ध नहीं है। आपका डेटा फोन में सुरक्षित है।")
            return
        }
        viewModelScope.launch {
            val token = obtainDriveToken(activity, actionTag = "SYNC") ?: return@launch
            performDriveRestoreAndSync(token, mergeFirst = true, silent = false)
        }
    }

    fun triggerRestoreFromDrive(activity: Activity?) {
        if (isOfflineAccount) {
            _toastEvents.tryEmit("Restore के लिए पहले Gmail से लॉगिन करें।")
            return
        }
        if (!LocalFileStorage.isOnline(appContext)) {
            _toastEvents.tryEmit("इंटरनेट कनेक्शन जांचें और फिर Restore करें।")
            return
        }
        viewModelScope.launch {
            val token = obtainDriveToken(activity, actionTag = "RESTORE") ?: return@launch
            performDriveRestoreOnly(token)
        }
    }

    fun onConsentIntentResult(activity: Activity, resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            when (val resolution = driveManager.resolveTokenFromActivityResult(activity, resultCode, data)) {
                is com.example.data.remote.IntentTokenResolution.Token -> {
                    val token = resolution.accessToken
                    cachedDriveToken = token
                    when (pendingActionAfterConsent) {
                        "RESTORE" -> performDriveRestoreOnly(token)
                        "PUBLISH_CATALOG" -> publishAdminCatalogWithToken(token)
                        else -> performDriveRestoreAndSync(token, mergeFirst = true, silent = false)
                    }
                }
                is com.example.data.remote.IntentTokenResolution.UserCancelled -> {
                    _driveSyncState.value = DriveSyncUiState.NeedAuth("Google Drive authorization cancelled")
                }
                is com.example.data.remote.IntentTokenResolution.Failed -> {
                    _driveSyncState.value = DriveSyncUiState.NeedAuth(resolution.message)
                    _toastEvents.tryEmit("Drive permission retry needed: ${resolution.message}")
                }
            }
        }
    }

    private suspend fun obtainDriveToken(activity: Activity?, actionTag: String): String? {
        cachedDriveToken?.takeIf { it.isNotBlank() }?.let { return it }
        driveManager.silentGetDriveToken(appContext)?.let {
            cachedDriveToken = it
            return it
        }
        if (activity == null) {
            _driveSyncState.value = DriveSyncUiState.NeedAuth("Tap Sync Now to authorize Drive")
            return null
        }
        pendingActionAfterConsent = actionTag
        return when (val out = driveManager.requestDriveAuthorization(activity)) {
            is DriveAuthOutcome.Authorized -> {
                cachedDriveToken = out.accessToken
                out.accessToken
            }
            is DriveAuthOutcome.NeedsResolution -> {
                _pendingConsentSender.value = out.intentSender
                null
            }
            is DriveAuthOutcome.Error -> {
                _driveSyncState.value = DriveSyncUiState.Error(out.message)
                _toastEvents.tryEmit(out.message)
                null
            }
        }
    }

    private fun scheduleAutoSyncIfEligible() {
        if (isOfflineAccount) return
        if (!localSettings.value.cloudSyncEnabled) {
            _driveSyncState.value = DriveSyncUiState.PendingOffline("Auto-Sync off • Saved locally")
            return
        }
        if (!LocalFileStorage.isOnline(appContext)) {
            _driveSyncState.value = DriveSyncUiState.PendingOffline("Offline • Saved locally, syncs when online")
            return
        }
        viewModelScope.launch {
            val token = cachedDriveToken ?: driveManager.silentGetDriveToken(appContext)
            cachedDriveToken = token
            if (token.isNullOrBlank()) {
                _driveSyncState.value = DriveSyncUiState.PendingOffline("Tap Sync Now to upload changes")
                return@launch
            }
            performDriveUploadOnly(token, silent = true)
        }
    }

    private suspend fun performDriveRestoreAndSync(token: String, mergeFirst: Boolean, silent: Boolean) {
        _driveSyncState.value = DriveSyncUiState.Syncing("Syncing with Google Drive...")
        if (mergeFirst && driveManager.hasBackupInLearnTalentFolder(token)) {
            driveManager.restoreFromLearnTalentFolder(token).onSuccess { restored ->
                restorePayloadIntoVault(restored.snapshotJson, restored.restoredBinaryFiles, mergeMode = true)
            }
        }
        performDriveUploadOnly(token, silent = silent)
    }

    private suspend fun performDriveRestoreOnly(token: String) {
        _driveSyncState.value = DriveSyncUiState.Syncing("Restoring from Google Drive...")
        driveManager.restoreFromLearnTalentFolder(token).onSuccess { restored ->
            restorePayloadIntoVault(restored.snapshotJson, restored.restoredBinaryFiles, mergeMode = true)
            val now = System.currentTimeMillis()
            val msg = "Restored ${restored.restoredBinaryFiles.size} files from Drive"
            prefs.setLastDriveBackupMillis(now, msg)
            _driveSyncState.value = DriveSyncUiState.Synced(now, msg)
            _toastEvents.tryEmit("Google Drive से सारा डेटा सफलतापूर्वक वापस आ गया!")
        }.onFailure { err ->
            val msg = err.message ?: "Restore failed"
            if (GoogleDriveBackupManager.isAuthRevokedError(msg)) {
                cachedDriveToken = null
                _driveSyncState.value = DriveSyncUiState.NeedAuth("Google Drive अनुमति समाप्त या रद्द हुई — पुनः Authorize करें")
            } else {
                _driveSyncState.value = DriveSyncUiState.Error(msg)
            }
            _toastEvents.tryEmit(msg)
        }
    }

    private suspend fun restorePayloadIntoVault(
        snapshotJson: String,
        binaryFiles: Map<String, ByteArray>,
        mergeMode: Boolean,
    ) {
        for ((name, bytes) in binaryFiles) {
            if (bytes.isNotEmpty()) {
                fileStorage.writeLocalBytes(storageOwnerId, name, bytes)
            }
        }
        if (mergeMode) {
            offlineStore.mergeDriveBackupJson(snapshotJson)
        } else {
            offlineStore.importSnapshotJson(snapshotJson)
        }
        offlineStore.updateProfileMeta(
            displayName = initialDisplayName,
            email = signedInEmail,
            photoUrl = initialPhotoUrl,
            role = GoogleAuthHelper.resolveRole(signedInEmail, sessionUser.emailVerified)
        )
    }

    private suspend fun performDriveUploadOnly(token: String, silent: Boolean) {
        _driveSyncState.value = DriveSyncUiState.Syncing("Uploading to LEARN TALENT Drive...")
        val payload = offlineStore.buildStructuredDrivePayload()
        val files = fileStorage.listAllUserFiles(storageOwnerId)
        driveManager.syncStructuredVaultToDrive(token, payload, files).onSuccess { summary ->
            offlineStore.markSyncedToCloud()
            val now = System.currentTimeMillis()
            val detail = "Google Drive Synced (${summary.uploadedFilesCount} files)"
            prefs.setLastDriveBackupMillis(now, detail)
            _driveSyncState.value = DriveSyncUiState.Synced(now, detail)
            if (!silent) {
                _toastEvents.tryEmit("Google Drive ('LEARN TALENT') पर डेटा सिंक हो गया!")
            }
        }.onFailure { err ->
            val msg = err.message ?: "Google Drive sync failed"
            if (GoogleDriveBackupManager.isAuthRevokedError(msg)) {
                cachedDriveToken = null
                _driveSyncState.value = DriveSyncUiState.NeedAuth("Google Drive अनुमति समाप्त या रद्द हुई — पुनः Authorize करें")
            } else {
                _driveSyncState.value = DriveSyncUiState.Error(msg)
            }
            if (!silent) _toastEvents.tryEmit(msg)
        }
    }

    fun toggleLightDarkTheme() {
        val current = localSettings.value
        val targetId = if (current.themeOption.isLight) {
            current.lastDarkThemeId.ifBlank { StudyThemeOption.BUNTS_NAVY_DARK.id }
        } else {
            current.lastLightThemeId.ifBlank { StudyThemeOption.BUNTS_ROYAL_LIGHT.id }
        }
        selectTheme(targetId)
    }

    fun selectTheme(themeId: String) {
        viewModelScope.launch {
            prefs.setTheme(themeId)
            offlineStore.updateTheme(themeId)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBiometricLockEnabled(enabled)
            _toastEvents.tryEmit(if (enabled) "Biometric Lock enabled" else "Biometric Lock disabled")
        }
    }

    fun saveAdminWhatsAppNumber(number: String) {
        viewModelScope.launch {
            if (!isAdminUser) {
                _toastEvents.tryEmit("Only Admin can change the support number.")
                return@launch
            }
            val normalized = UserPreferencesManager.normalizePhoneDigits(number)
            if (normalized.isBlank()) {
                _toastEvents.tryEmit("कृपया सही मोबाइल नंबर लिखें")
                return@launch
            }
            prefs.setAdminWhatsAppNumber(normalized)
            _toastEvents.tryEmit("Support number updated!")
        }
    }

    fun saveReminderPreferences(enabled: Boolean, hour: Int, minute: Int, yesterdaySummary: String) {
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute)
            prefs.setYesterdaySummary(yesterdaySummary)
        }
    }

    fun updateExamGoal(examName: String, examDateMillis: Long, subjects: List<Subject>, autoPlan: Boolean) {
        viewModelScope.launch {
            offlineStore.updateExamGoal(examName, examDateMillis)
            if (autoPlan && subjects.isNotEmpty()) {
                val days = offlineStore.generateExamRevisionPlan(subjects, examDateMillis)
                _toastEvents.tryEmit("Exam goal saved & $days-day plan created!")
            } else {
                _toastEvents.tryEmit("Exam goal updated!")
            }
            scheduleAutoSyncIfEligible()
        }
    }

    fun addSubject(nameEn: String, nameHi: String?, iconKey: String, colorHex: String, sortOrder: Long) {
        viewModelScope.launch {
            offlineStore.addSubject(nameEn, nameHi, iconKey, colorHex, sortOrder)
            _toastEvents.tryEmit("Subject added")
            scheduleAutoSyncIfEligible()
        }
    }

    fun addTopic(subjectId: String, name: String, description: String?) {
        viewModelScope.launch {
            offlineStore.addTopic(subjectId, name, description)
            _toastEvents.tryEmit("Topic added")
            scheduleAutoSyncIfEligible()
        }
    }

    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            offlineStore.deleteTopic(topicId)
            scheduleAutoSyncIfEligible()
        }
    }

    fun addSubtopic(subjectId: String, topicId: String, name: String) {
        viewModelScope.launch {
            offlineStore.addSubtopic(subjectId, topicId, name)
            _toastEvents.tryEmit("Sub-topic added")
            scheduleAutoSyncIfEligible()
        }
    }

    fun deleteSubtopic(subtopicId: String) {
        viewModelScope.launch {
            offlineStore.deleteSubtopic(subtopicId)
            scheduleAutoSyncIfEligible()
        }
    }

    fun markSubtopicStudiedToday(subtopic: Subtopic, subjectName: String) {
        viewModelScope.launch {
            offlineStore.markSubtopicStudiedToday(subtopic, subjectName)
            _toastEvents.tryEmit("आज के अध्ययन में जोड़ा गया!")
            scheduleAutoSyncIfEligible()
        }
    }

    // Full Length Exams & HTML Mock Tests
    fun addFullLengthExam(name: String, subtitle: String?) {
        viewModelScope.launch {
            offlineStore.addFullLengthExam(name, subtitle)
            _toastEvents.tryEmit("एग्जाम '$name' बनाया गया!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun deleteFullLengthExam(exam: FullLengthExam) {
        viewModelScope.launch {
            if (exam.isShared) {
                if (isAdminUser) {
                    adminCatalogStore.deleteSharedFullLengthExam(exam.id)
                    _toastEvents.tryEmit("Deleted '${exam.name}'")
                }
                return@launch
            }
            offlineStore.deleteFullLengthExam(exam.id)
            _toastEvents.tryEmit("Deleted '${exam.name}'")
            scheduleAutoSyncIfEligible()
        }
    }

    fun uploadFullLengthHtmlTest(examId: String, examName: String, title: String, uri: Uri) {
        if (!isAdminUser && !adminCatalogStore.allowStudentPersonalUploads.value) {
            _toastEvents.tryEmit("Admin द्वारा व्यक्तिगत फ़ाइल अपलोड अभी बंद किया गया है।")
            return
        }
        viewModelScope.launch {
            val noteId = "note_${StudyDateUtils.newValidId()}"
            try {
                val (fileName, file) = fileStorage.copyUriToLocal(storageOwnerId, noteId, uri, "html")
                val htmlText = if (file.length() <= 190_000L) file.readText(Charsets.UTF_8) else null
                val finalTitle = title.trim().ifBlank { fileName.substringBeforeLast(".") }
                offlineStore.addNote(
                    noteId = noteId,
                    subjectId = FullLengthExamDefaults.SUBJECT_ID,
                    topicId = examId,
                    subtopicId = FullLengthExamDefaults.SUBTOPIC_ID,
                    folderName = examName.ifBlank { "Full Length Test" },
                    title = finalTitle,
                    noteType = NoteType.HTML,
                    localFileName = fileName,
                    htmlContent = htmlText,
                    isMockTest = true
                )
                _toastEvents.tryEmit("HTML टेस्ट '$finalTitle' प्राइवेट सेव हो गया!")
                scheduleAutoSyncIfEligible()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastEvents.tryEmit(e.message ?: "HTML upload failed")
            }
        }
    }

    fun createSampleFullLengthTest(examId: String, examName: String, title: String) {
        createSampleHtmlTest(
            subjectId = FullLengthExamDefaults.SUBJECT_ID,
            topicId = examId,
            subtopicId = FullLengthExamDefaults.SUBTOPIC_ID,
            folderName = examName.ifBlank { "Full Length Test" },
            title = title
        )
    }

    fun createFolder(subjectId: String, topicId: String, subtopicId: String, folderName: String) {
        viewModelScope.launch {
            val noteId = "note_${StudyDateUtils.newValidId()}"
            offlineStore.addNote(noteId, subjectId, topicId, subtopicId, folderName, folderName, NoteType.FOLDER)
            _toastEvents.tryEmit("Folder '$folderName' created")
            scheduleAutoSyncIfEligible()
        }
    }

    fun importFileNote(
        subjectId: String, topicId: String, subtopicId: String, folderName: String,
        customTitle: String, noteType: NoteType, uri: Uri, isMockTest: Boolean = noteType == NoteType.HTML,
    ) {
        if (!isAdminUser && !adminCatalogStore.allowStudentPersonalUploads.value) {
            _toastEvents.tryEmit("Admin द्वारा व्यक्तिगत फ़ाइल अपलोड अभी बंद किया गया है।")
            return
        }
        viewModelScope.launch {
            val noteId = "note_${StudyDateUtils.newValidId()}"
            val ext = when (noteType) {
                NoteType.PDF -> "pdf"; NoteType.HTML -> "html"; NoteType.IMAGE -> "jpg"; NoteType.FOLDER -> "txt"
            }
            try {
                val (fileName, file) = fileStorage.copyUriToLocal(storageOwnerId, noteId, uri, ext)
                val htmlText = if (noteType == NoteType.HTML && file.length() <= 190_000L) file.readText(Charsets.UTF_8) else null
                val finalTitle = customTitle.ifBlank { fileName }
                offlineStore.addNote(noteId, subjectId, topicId, subtopicId, folderName, finalTitle, noteType, fileName, htmlText, isMockTest)
                _toastEvents.tryEmit("Saved privately: '$finalTitle'")
                scheduleAutoSyncIfEligible()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastEvents.tryEmit(e.message ?: "Failed to import file")
            }
        }
    }

    fun submitPersonalNoteForAdminApproval(note: StudyNote) {
        if (note.isShared) return
        viewModelScope.launch {
            val resolvedFile = fileStorage.resolveAnyNoteFile(storageOwnerId, note.id, note.localFileName, isShared = false)
            var sharedCopyName: String? = note.localFileName
            var downloadUrl: String? = note.downloadUrl
            if (resolvedFile != null && resolvedFile.exists()) {
                val destName = "subm_${note.id}_${resolvedFile.name}"
                runCatching {
                    File(fileStorage.sharedCacheDir(), destName).writeBytes(resolvedFile.readBytes())
                    sharedCopyName = destName
                }
            }
            adminCatalogStore.submitStudentUploadForApproval(
                StudentUploadSubmission(
                    id = "subm_${note.id}",
                    studentUid = storageOwnerId,
                    studentName = initialDisplayName,
                    studentEmail = signedInEmail.orEmpty(),
                    subjectId = note.subjectId,
                    topicId = note.topicId,
                    subtopicId = note.subtopicId,
                    folderName = note.folderName,
                    title = note.title,
                    noteType = note.noteType,
                    isMockTest = note.isMockTest,
                    localFileName = sharedCopyName,
                    htmlContent = note.htmlContent,
                    downloadUrl = downloadUrl,
                    fileSizeBytes = resolvedFile?.length() ?: note.fileSizeBytes,
                )
            )
            _toastEvents.tryEmit("'${note.title}' Admin स्वीकृति के लिए भेजा गया (जब तक Approve न हो, यह केवल आपके पास Private रहेगा)।")
        }
    }

    fun createSampleHtmlTest(subjectId: String, topicId: String, subtopicId: String, folderName: String, title: String) {
        viewModelScope.launch {
            val noteId = "note_${StudyDateUtils.newValidId()}"
            val html = LocalFileStorage.buildSampleHtmlMockTest(title)
            val fileName = fileStorage.writeLocalHtml(storageOwnerId, noteId, title, html)
            offlineStore.addNote(noteId, subjectId, topicId, subtopicId, folderName, title, NoteType.HTML, fileName, html, true)
            _toastEvents.tryEmit("Sample HTML Test created!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun renameOrMoveNote(noteId: String, newTitle: String, newFolderName: String) {
        viewModelScope.launch {
            offlineStore.renameOrMoveNote(noteId, newTitle, newFolderName)
            scheduleAutoSyncIfEligible()
        }
    }

    fun markNoteOpenedToday(note: StudyNote, subjectName: String) {
        _recentActivities.value = fileStorage.pushRecentActivity(
            storageOwnerId,
            RecentActivityItem(
                id = note.id, itemType = if (note.noteType == NoteType.HTML.name) "HTML_TEST" else "NOTE",
                title = note.title, subtitle = subjectName, subjectId = note.subjectId,
                topicId = note.topicId, subtopicId = note.subtopicId, refId = note.id,
                timestampMillis = System.currentTimeMillis(),
            )
        )
        viewModelScope.launch {
            if (!note.isShared) {
                offlineStore.markNoteOpenedToday(note, subjectName)
            }
        }
    }

    fun deleteNote(note: StudyNote) {
        viewModelScope.launch {
            if (note.isShared) {
                if (isAdminUser) adminDeleteSharedContent(note)
                return@launch
            }
            fileStorage.deleteLocalFile(storageOwnerId, note.localFileName)
            offlineStore.deleteNote(note.id)
            _toastEvents.tryEmit("Deleted '${note.title}'")
            scheduleAutoSyncIfEligible()
        }
    }

    fun resolveNoteFile(note: StudyNote?, directFileName: String?): File? {
        if (note != null) {
            return fileStorage.resolveAnyNoteFile(storageOwnerId, note.id, note.localFileName ?: directFileName, note.isShared)
        }
        return fileStorage.getLocalFile(storageOwnerId, directFileName)
    }

    suspend fun loadHtmlContent(note: StudyNote): String? {
        fileStorage.readHtmlFromFileOrWrite(storageOwnerId, note.localFileName, note.htmlContent, note.id, note.isShared)?.let { return it }
        if (note.isShared && !note.downloadUrl.isNullOrBlank()) {
            val bytes = driveManager.downloadPublicDriveUrl(note.downloadUrl).getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                val destName = note.localFileName ?: "${note.id}.html"
                val destFile = File(fileStorage.sharedCacheDir(), destName)
                destFile.writeBytes(bytes)
                fileStorage.saveSharedDownloadRecord(
                    SharedDownloadRecord(note.id, note.version, destName, bytes.size.toLong(), System.currentTimeMillis())
                )
                _sharedDownloadsMap.value = fileStorage.loadSharedDownloadsMap()
                return String(bytes, Charsets.UTF_8)
            }
        }
        return null
    }

    fun downloadSharedNote(note: StudyNote, onDownloaded: ((File) -> Unit)? = null) {
        viewModelScope.launch {
            _sharedDownloadProgress.value = _sharedDownloadProgress.value + (note.id to 0.15f)
            val fallbackName = note.localFileName ?: when (note.resolvedType()) {
                NoteType.PDF -> "${note.id}.pdf"; NoteType.HTML -> "${note.id}.html"; NoteType.IMAGE -> "${note.id}.jpg"; NoteType.FOLDER -> "${note.id}.txt"
            }
            val destFile = File(fileStorage.sharedCacheDir(), fallbackName)
            val result = runCatching {
                when {
                    destFile.exists() && destFile.length() > 0L -> destFile
                    !note.downloadUrl.isNullOrBlank() -> {
                        val bytes = driveManager.downloadPublicDriveUrl(note.downloadUrl).getOrThrow()
                        _sharedDownloadProgress.value = _sharedDownloadProgress.value + (note.id to 0.85f)
                        destFile.writeBytes(bytes)
                        destFile
                    }
                    !note.htmlContent.isNullOrBlank() -> {
                        destFile.writeText(note.htmlContent, Charsets.UTF_8)
                        destFile
                    }
                    else -> throw IllegalStateException("Shared file link उपलब्ध नहीं है।")
                }
            }
            _sharedDownloadProgress.value = _sharedDownloadProgress.value - note.id
            result.onSuccess { file ->
                fileStorage.saveSharedDownloadRecord(
                    SharedDownloadRecord(note.id, note.version, fallbackName, file.length(), System.currentTimeMillis())
                )
                _sharedDownloadsMap.value = fileStorage.loadSharedDownloadsMap()
                adminCatalogStore.incrementSharedDownloadCount(note.id)
                _toastEvents.tryEmit("Offline saved: ${note.title}")
                onDownloaded?.invoke(file)
            }.onFailure { err ->
                _toastEvents.tryEmit(err.message ?: "Download failed")
            }
        }
    }

    fun deleteOfflineCachedShared(noteId: String) {
        fileStorage.removeSharedDownloadRecord(noteId)
        _sharedDownloadsMap.value = fileStorage.loadSharedDownloadsMap()
        _toastEvents.tryEmit("Removed offline copy")
    }

    // PDF Annotations & Copy Export
    fun loadPdfAnnotationDocument(noteId: String): PdfAnnotationDocument {
        fileStorage.loadPdfAnnotationDoc(storageOwnerId, noteId)?.let { return it }
        val cloudDoc = pdfAnnotationsState.value.firstOrNull { it.noteId == noteId || it.id == noteId }
        if (cloudDoc != null && cloudDoc.annotationsJson.isNotBlank()) {
            val parsed = runCatching { jsonCodec.decodeFromString<PdfAnnotationDocument>(cloudDoc.annotationsJson) }.getOrNull()
            if (parsed != null) {
                fileStorage.savePdfAnnotationDoc(storageOwnerId, parsed)
                return parsed
            }
        }
        return PdfAnnotationDocument(noteId = noteId, ownerUid = storageOwnerId)
    }

    fun savePdfAnnotationDocument(doc: PdfAnnotationDocument, silent: Boolean = false) {
        val withTime = doc.copy(ownerUid = storageOwnerId, updatedAtMillis = System.currentTimeMillis())
        fileStorage.savePdfAnnotationDoc(storageOwnerId, withTime)
        val encoded = jsonCodec.encodeToString(withTime)
        viewModelScope.launch {
            offlineStore.savePdfAnnotationDoc(doc.noteId, encoded, withTime.updatedAtMillis)
            if (!silent) _toastEvents.tryEmit("PDF annotations saved!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun saveAnnotatedPdfAsNewCopy(sourceFile: File, note: StudyNote?, doc: PdfAnnotationDocument) {
        viewModelScope.launch {
            val newNoteId = "note_${StudyDateUtils.newValidId()}"
            val cleanTitle = "${note?.title ?: "Annotated"} (My Annotated Copy)"
            val destFileName = "${newNoteId}_annotated.pdf"
            val destFile = File(fileStorage.userDir(storageOwnerId), destFileName)
            runCatching {
                PdfExportHelper.saveAnnotatedPdfCopy(sourceFile, doc, destFile)
            }.onSuccess {
                val subjId = note?.subjectId?.takeIf { it.isNotBlank() } ?: "subj_other"
                val topId = note?.topicId?.takeIf { it.isNotBlank() } ?: "topic_11_0"
                val subId = note?.subtopicId?.takeIf { it.isNotBlank() } ?: "sub_11_0_0"
                offlineStore.addNote(newNoteId, subjId, topId, subId, "Annotated Copies", cleanTitle, NoteType.PDF, destFileName)
                _toastEvents.tryEmit("Annotated copy saved! Check Annotated Copies or Export.")
                fileStorage.sharePdfFile(destFile, "Share Annotated PDF Copy")
                scheduleAutoSyncIfEligible()
            }.onFailure {
                _toastEvents.tryEmit(it.message ?: "Failed to create annotated PDF copy")
            }
        }
    }

    // HTML CBT Test Drafts & Detailed Results
    fun saveInProgressTestAttempt(attempt: InProgressTestAttempt) {
        fileStorage.saveInProgressTestAttempt(storageOwnerId, attempt)
    }

    fun loadInProgressTestAttempt(noteId: String): InProgressTestAttempt? =
        fileStorage.loadInProgressTestAttempt(storageOwnerId, noteId)

    fun clearInProgressTestAttempt(noteId: String) {
        fileStorage.clearInProgressTestAttempt(storageOwnerId, noteId)
    }

    fun saveDetailedTestResult(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        noteId: String?,
        testTitle: String,
        scoreObtained: Double,
        totalMarks: Double,
        totalQuestions: Int,
        attemptedCount: Int,
        correctCount: Int,
        wrongCount: Int,
        unattemptedCount: Int,
        durationSeconds: Long,
        testMode: String,
        questionsBreakdown: List<QuestionAttemptRecord>,
        subjectName: String,
        onSaved: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            val resultId = "res_${StudyDateUtils.newValidId()}"
            val breakdownJson = runCatching { jsonCodec.encodeToString(questionsBreakdown) }.getOrNull()
            val summaryText = "$correctCount Right • $wrongCount Wrong • $unattemptedCount Left"
            if (!noteId.isNullOrBlank()) clearInProgressTestAttempt(noteId)

            val savedResult = offlineStore.recordTestResult(
                subjectId = subjectId,
                topicId = topicId,
                subtopicId = subtopicId,
                noteId = noteId,
                testTitle = testTitle,
                scoreObtained = scoreObtained,
                totalMarks = totalMarks,
                summaryText = summaryText,
                subjectName = subjectName,
                totalQuestions = totalQuestions,
                attemptedCount = attemptedCount,
                correctCount = correctCount,
                wrongCount = wrongCount,
                unattemptedCount = unattemptedCount,
                durationSeconds = durationSeconds,
                testMode = testMode,
                breakdownJson = breakdownJson,
                resultId = resultId,
            )
            _lastReturnedScore.value = savedResult
            syncStudentAnalyticsSummary()
            scheduleAutoSyncIfEligible()
            onSaved(resultId)
        }
    }

    fun exportTestResultPdf(result: TestResult, share: Boolean) {
        viewModelScope.launch {
            val questions = parseBreakdown(result.breakdownJson)
            runCatching {
                PdfExportHelper.exportTestResultPdf(
                    appContext,
                    fileStorage.exportsDir(storageOwnerId),
                    result,
                    initialDisplayName,
                    questions
                )
            }.onSuccess { file ->
                if (share) fileStorage.sharePdfFile(file, "Share Test Result PDF")
                else {
                    _toastEvents.tryEmit("Result PDF saved: ${file.name}")
                    fileStorage.openPdfExternally(file)
                }
            }.onFailure { _toastEvents.tryEmit(it.message ?: "Could not export PDF") }
        }
    }

    fun parseBreakdown(rawJson: String?): List<QuestionAttemptRecord> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching { jsonCodec.decodeFromString<List<QuestionAttemptRecord>>(rawJson) }.getOrDefault(emptyList())
    }

    // Personal Notes Maker
    fun upsertPersonalNote(note: PersonalNote, silent: Boolean = false) {
        val withTime = note.copy(
            id = note.id.ifBlank { "pnote_${StudyDateUtils.newValidId()}" },
            updatedAtMillis = System.currentTimeMillis()
        )
        viewModelScope.launch {
            offlineStore.upsertPersonalNote(withTime)
            if (!silent) _toastEvents.tryEmit("Personal note saved!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun deletePersonalNote(noteId: String) {
        viewModelScope.launch {
            offlineStore.deletePersonalNote(noteId)
            _toastEvents.tryEmit("Note deleted")
            scheduleAutoSyncIfEligible()
        }
    }

    fun copyPersonalNoteImage(noteId: String, uri: Uri, onCopied: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                fileStorage.copyUriToLocal(storageOwnerId, "pimg_$noteId", uri, "jpg").first
            }.onSuccess(onCopied)
                .onFailure { _toastEvents.tryEmit(it.message ?: "Image attach failed") }
        }
    }

    fun exportPersonalNotePdf(note: PersonalNote, blocks: List<PersonalNoteBlock>, share: Boolean) {
        viewModelScope.launch {
            runCatching {
                PdfExportHelper.exportPersonalNotePdf(
                    appContext,
                    fileStorage.exportsDir(storageOwnerId),
                    fileStorage.userDir(storageOwnerId),
                    note,
                    blocks
                )
            }.onSuccess { file ->
                if (share) fileStorage.sharePdfFile(file, "Share Note PDF")
                else {
                    _toastEvents.tryEmit("Note PDF exported: ${file.name}")
                    fileStorage.openPdfExternally(file)
                }
            }.onFailure { _toastEvents.tryEmit(it.message ?: "Could not export PDF") }
        }
    }

    // Bookmarks
    fun toggleBookmark(
        bookmarkType: BookmarkType,
        title: String,
        subtitle: String,
        refId: String,
        subjectId: String = "",
        topicId: String = "",
        subtopicId: String = "",
        pageIndex: Int = 0,
        questionJson: String? = null,
    ) {
        viewModelScope.launch {
            val currentList = (bookmarksState.value as? UiState.Success)?.data ?: emptyList()
            val existing = currentList.firstOrNull {
                it.bookmarkType == bookmarkType.name && it.refId == refId && it.pageIndex == pageIndex && it.title == title
            }
            if (existing != null) {
                offlineStore.deleteBookmark(existing.id)
                _toastEvents.tryEmit("Bookmark removed")
            } else {
                val item = BookmarkItem(
                    id = "bmk_${StudyDateUtils.newValidId()}",
                    bookmarkType = bookmarkType.name,
                    title = title,
                    subtitle = subtitle,
                    refId = refId,
                    subjectId = subjectId,
                    topicId = topicId,
                    subtopicId = subtopicId,
                    pageIndex = pageIndex,
                    questionJson = questionJson,
                    createdAtMillis = System.currentTimeMillis(),
                )
                offlineStore.upsertBookmark(item)
                _toastEvents.tryEmit("Added to Bookmarks!")
            }
            scheduleAutoSyncIfEligible()
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            offlineStore.deleteBookmark(bookmarkId)
            scheduleAutoSyncIfEligible()
        }
    }

    // Videos & MCQs
    fun addVideo(subjectId: String, topicId: String, subtopicId: String, title: String, url: String, channelOrNote: String?) {
        viewModelScope.launch {
            offlineStore.addVideo(subjectId, topicId, subtopicId, title, url, channelOrNote)
            _toastEvents.tryEmit("YouTube link saved!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun markVideoWatched(video: VideoLink, subjectName: String) {
        viewModelScope.launch {
            if (!video.isShared) {
                offlineStore.markVideoWatchedToday(video, subjectName)
                scheduleAutoSyncIfEligible()
            }
        }
    }

    fun deleteVideo(video: VideoLink) {
        viewModelScope.launch {
            if (video.isShared) {
                if (isAdminUser) adminCatalogStore.deleteSharedVideo(video.id)
                return@launch
            }
            offlineStore.deleteVideo(video.id)
            scheduleAutoSyncIfEligible()
        }
    }

    fun addMcq(subjectId: String, topicId: String, subtopicId: String, question: String, options: List<String>, correctIndex: Int, explanation: String?) {
        viewModelScope.launch {
            offlineStore.addMcq(subjectId, topicId, subtopicId, question, options, correctIndex, explanation)
            _toastEvents.tryEmit("Practice MCQ added!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun deleteMcq(mcq: McqQuestion) {
        viewModelScope.launch {
            if (mcq.isShared) {
                if (isAdminUser) adminCatalogStore.deleteSharedMcq(mcq.id)
                return@launch
            }
            offlineStore.deleteMcq(mcq.id)
            scheduleAutoSyncIfEligible()
        }
    }

    // Study Logs & Revision Diary
    fun addStudyPlanItem(dateKey: String, entryType: String, title: String, subjectName: String?, notes: String?) {
        viewModelScope.launch {
            offlineStore.addStudyLog(dateKey, entryType, title, subjectName, notes)
            _toastEvents.tryEmit("Study item saved!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun toggleStudyLogCompleted(log: StudyLog) {
        viewModelScope.launch {
            offlineStore.toggleStudyLogCompleted(log.id, !log.completed)
            scheduleAutoSyncIfEligible()
        }
    }

    fun deleteStudyLog(logId: String) {
        viewModelScope.launch {
            offlineStore.deleteStudyLog(logId)
            scheduleAutoSyncIfEligible()
        }
    }

    fun addRevisionDiaryEntry(title: String, subjectName: String?, sourceName: String, content: String, pdfUri: Uri? = null) {
        viewModelScope.launch {
            val today = StudyDateUtils.todayKey()
            val docId = "diary_${StudyDateUtils.newValidId()}"
            var pdfTitle: String? = null
            var pdfLocalFileName: String? = null
            if (pdfUri != null) {
                try {
                    val (fileName, _) = fileStorage.copyUriToLocal(storageOwnerId, docId, pdfUri, "pdf")
                    pdfTitle = fileStorage.queryDisplayName(pdfUri) ?: fileName
                    pdfLocalFileName = fileName
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _toastEvents.tryEmit(e.message ?: "Could not attach PDF"); return@launch }
            }
            offlineStore.addRevisionDiaryEntry(today, title, subjectName, sourceName, content, pdfTitle, pdfLocalFileName, docId)
            _toastEvents.tryEmit("रिवीजन डायरी में सेव हो गया!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun updateRevisionDiaryEntry(
        entryId: String, title: String, subjectName: String?, sourceName: String, content: String,
        pdfUri: Uri? = null, existingPdfTitle: String? = null, existingPdfFileName: String? = null, removePdf: Boolean = false,
    ) {
        viewModelScope.launch {
            var finalPdfTitle = if (removePdf) null else existingPdfTitle
            var finalPdfFileName = if (removePdf) null else existingPdfFileName
            if (removePdf && !existingPdfFileName.isNullOrBlank()) fileStorage.deleteLocalFile(storageOwnerId, existingPdfFileName)
            if (pdfUri != null) {
                try {
                    val (fileName, _) = fileStorage.copyUriToLocal(storageOwnerId, entryId, pdfUri, "pdf")
                    if (!existingPdfFileName.isNullOrBlank() && existingPdfFileName != fileName) {
                        fileStorage.deleteLocalFile(storageOwnerId, existingPdfFileName)
                    }
                    finalPdfTitle = fileStorage.queryDisplayName(pdfUri) ?: fileName
                    finalPdfFileName = fileName
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _toastEvents.tryEmit(e.message ?: "Could not attach PDF"); return@launch }
            }
            offlineStore.updateRevisionDiaryEntry(entryId, title, subjectName, sourceName, content, finalPdfTitle, finalPdfFileName)
            _toastEvents.tryEmit("रिवीजन डायरी अपडेट हो गई!")
            scheduleAutoSyncIfEligible()
        }
    }

    fun deleteRevisionDiaryEntry(entry: RevisionDiaryEntry) {
        viewModelScope.launch {
            fileStorage.deleteLocalFile(storageOwnerId, entry.pdfLocalFileName)
            offlineStore.deleteRevisionDiaryEntry(entry.id)
            scheduleAutoSyncIfEligible()
        }
    }

    // Support Requests (local log + WhatsApp launch handled by dialog)
    fun recordWhatsAppRequest(requestType: String, subject: String, topic: String, subtopic: String, extraMessage: String) {
        viewModelScope.launch {
            adminCatalogStore.submitWhatsAppRequest(
                WhatsAppRequestRecord(
                    userId = storageOwnerId,
                    studentName = initialDisplayName,
                    studentEmail = signedInEmail.orEmpty(),
                    requestType = requestType,
                    subject = subject,
                    topic = topic,
                    subtopic = subtopic,
                    extraMessage = extraMessage,
                )
            )
        }
    }

    // Student Catalog Sync from Admin Google Drive Share Link
    fun importSharedCatalogFromUrl(urlOrId: String) {
        viewModelScope.launch {
            checkPublishedManifestUpdates(urlOrId, silent = false)
        }
    }

    private suspend fun checkPublishedManifestUpdates(urlOrId: String, silent: Boolean) {
        if (!LocalFileStorage.isOnline(appContext)) {
            if (!silent) _toastEvents.tryEmit("इंटरनेट कनेक्शन जांचें।")
            return
        }
        driveManager.downloadPublicDriveUrl(urlOrId).onSuccess { bytes ->
            val raw = String(bytes, Charsets.UTF_8)
            runCatching {
                adminCatalogStore.importPublishedCatalogJson(raw)
            }.onSuccess { diff ->
                prefs.setSharedCatalogUrl(urlOrId)
                _publishedCatalogLink.value = urlOrId
                val lastSeen = prefs.settingsFlow.first().lastSeenCatalogVersion
                if (diff.manifestVersion > lastSeen) {
                    prefs.setLastSeenCatalogVersion(diff.manifestVersion)
                }
                if (diff.newPublishedNotes.isNotEmpty()) {
                    val sampleTitle = diff.newPublishedNotes.first().title
                    _toastEvents.tryEmit("${diff.newPublishedNotes.size} नए Admin टेस्ट/नोट्स उपलब्ध: $sampleTitle")
                } else if (!silent) {
                    _toastEvents.tryEmit("Admin Catalog अपडेट हो गया!")
                }
            }.onFailure { err ->
                if (!silent) _toastEvents.tryEmit("Invalid catalog JSON: ${err.message}")
            }
        }.onFailure { err ->
            if (!silent) _toastEvents.tryEmit(err.message ?: "Failed to download Admin Catalog")
        }
    }

    private fun resolveAdminDriveFolderForType(noteType: NoteType): String = when (noteType) {
        NoteType.HTML -> GoogleDriveBackupManager.SUBFOLDER_PUBLISHED_TESTS
        NoteType.PDF -> GoogleDriveBackupManager.SUBFOLDER_PDF_NOTES
        NoteType.IMAGE, NoteType.FOLDER -> GoogleDriveBackupManager.SUBFOLDER_STUDY_MATERIAL
    }

    // Admin Shared Catalog Operations (Local + Admin's own Google Drive Shared Catalog under LEARN TALENT/ADMIN/)
    fun adminSetAllowStudentUploads(allowed: Boolean) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.setAllowStudentPersonalUploads(allowed)
            _toastEvents.tryEmit(if (allowed) "Student Personal Uploads Enabled" else "Student Personal Uploads Disabled")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminCreateSharedSubject(nameEn: String, nameHi: String?, iconKey: String, colorHex: String, sortOrder: Long) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.upsertSharedSubject(nameEn, nameHi, iconKey, colorHex, sortOrder)
            _toastEvents.tryEmit("Shared Subject '$nameEn' created!")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminDeleteSharedSubject(subjectId: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.deleteSharedSubject(subjectId)
            _toastEvents.tryEmit("Shared Subject removed")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminCreateSharedFullLengthExam(name: String, subtitle: String?) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.upsertSharedFullLengthExam(name, subtitle)
            _toastEvents.tryEmit("Shared Full Length Exam '$name' created!")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminUploadSharedContent(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        folderName: String,
        title: String,
        noteType: NoteType,
        isMockTest: Boolean,
        fileUri: Uri?,
        published: Boolean,
        existingNote: StudyNote? = null,
    ) {
        if (!isAdminUser) return
        viewModelScope.launch {
            val uploaded = uploadSingleAdminFileInternal(subjectId, topicId, subtopicId, folderName, title, noteType, isMockTest, fileUri, published, existingNote)
            if (uploaded) {
                autoRefreshAdminManifestIfPossible()
            }
            _adminUploadProgress.value = null
        }
    }

    fun adminBatchUploadSharedContent(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        folderName: String,
        noteType: NoteType,
        isMockTest: Boolean,
        uris: List<Uri>,
        published: Boolean,
    ) {
        if (!isAdminUser || uris.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            uris.forEachIndexed { idx, uri ->
                _adminUploadProgress.value = (idx + 0.5f) / uris.size.toFloat()
                val title = fileStorage.queryDisplayName(uri)?.substringBeforeLast(".") ?: "Material ${idx + 1}"
                if (uploadSingleAdminFileInternal(subjectId, topicId, subtopicId, folderName, title, noteType, isMockTest, uri, published, null)) {
                    successCount++
                }
            }
            _adminUploadProgress.value = null
            _toastEvents.tryEmit("Batch Upload Complete: $successCount / ${uris.size} files added!")
            if (successCount > 0) autoRefreshAdminManifestIfPossible()
        }
    }

    private suspend fun uploadSingleAdminFileInternal(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        folderName: String,
        title: String,
        noteType: NoteType,
        isMockTest: Boolean,
        fileUri: Uri?,
        published: Boolean,
        existingNote: StudyNote?,
    ): Boolean {
        val docId = existingNote?.id?.takeIf { it.isNotBlank() } ?: "shared_doc_${StudyDateUtils.newValidId()}"
        val ext = when (noteType) {
            NoteType.PDF -> "pdf"; NoteType.HTML -> "html"; NoteType.IMAGE -> "jpg"; NoteType.FOLDER -> "txt"
        }
        val targetFolder = resolveAdminDriveFolderForType(noteType)
        var downloadUrl = existingNote?.downloadUrl
        var localFileName = existingNote?.localFileName
        var htmlText = existingNote?.htmlContent
        var fileSize = existingNote?.fileSizeBytes ?: 0L

        if (fileUri != null) {
            _adminUploadProgress.value = 0.1f
            try {
                val (displayName, mimeType, bytes) = fileStorage.readUriBytesWithMeta(fileUri, ext)
                localFileName = "${docId}_$displayName"
                fileSize = bytes.size.toLong()
                File(fileStorage.sharedCacheDir(), localFileName).writeBytes(bytes)
                if (noteType == NoteType.HTML && bytes.size <= 190_000) {
                    htmlText = String(bytes, Charsets.UTF_8)
                }
                _adminUploadProgress.value = 0.45f
                val token = cachedDriveToken ?: driveManager.silentGetDriveToken(appContext)
                if (!token.isNullOrBlank() && LocalFileStorage.isOnline(appContext)) {
                    driveManager.publishSharedFilePublicReadOnly(
                        accessToken = token,
                        targetAdminSubfolder = targetFolder,
                        fileName = localFileName,
                        mimeType = mimeType,
                        bytes = bytes,
                    ).onSuccess { url ->
                        downloadUrl = url
                        fileStorage.saveSharedDownloadRecord(
                            SharedDownloadRecord(docId, (existingNote?.version ?: 0L) + 1L, localFileName, fileSize, System.currentTimeMillis())
                        )
                        _sharedDownloadsMap.value = fileStorage.loadSharedDownloadsMap()
                    }
                }
                _adminUploadProgress.value = 0.9f
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _toastEvents.tryEmit(e.message ?: "Could not read file")
                return false
            }
        }

        val nextVersion = if (existingNote != null) existingNote.version + 1L else 1L
        val noteObj = StudyNote(
            id = docId,
            subjectId = subjectId,
            topicId = topicId,
            subtopicId = subtopicId,
            folderName = folderName,
            title = title,
            noteType = noteType.name,
            localFileName = localFileName,
            htmlContent = htmlText,
            isShared = true,
            storagePath = "ADMIN/$targetFolder/$localFileName",
            downloadUrl = downloadUrl,
            version = nextVersion,
            published = published,
            downloadsCount = existingNote?.downloadsCount ?: 0L,
            fileSizeBytes = fileSize,
            isMockTest = isMockTest,
        )
        adminCatalogStore.upsertSharedContent(noteObj)
        _toastEvents.tryEmit(
            if (!downloadUrl.isNullOrBlank()) "Uploaded to ADMIN/$targetFolder & Catalog (v$nextVersion)!"
            else "Saved to Catalog (v$nextVersion). Tap 'Publish to Drive' to sync manifest!"
        )
        return true
    }

    fun adminEditSharedContent(note: StudyNote, newTitle: String, newFolderName: String, isMockTest: Boolean, published: Boolean) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.editSharedContent(note.id, newTitle, newFolderName, isMockTest, published)
            _toastEvents.tryEmit("Updated '${newTitle.trim()}'")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminApproveSubmission(submissionId: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            val approved = adminCatalogStore.approveStudentSubmission(submissionId)
            if (approved != null) {
                _toastEvents.tryEmit("Approved & published '${approved.title}' for all students!")
                autoRefreshAdminManifestIfPossible()
            }
        }
    }

    fun adminRejectSubmission(submissionId: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.rejectStudentSubmission(submissionId)
            _toastEvents.tryEmit("Submission declined")
        }
    }

    private suspend fun autoRefreshAdminManifestIfPossible() {
        val token = cachedDriveToken ?: driveManager.silentGetDriveToken(appContext)
        if (!token.isNullOrBlank() && LocalFileStorage.isOnline(appContext)) {
            publishAdminCatalogWithToken(token)
        }
    }

    fun adminPublishCatalogToDrive(activity: Activity?) {
        if (!isAdminUser) return
        viewModelScope.launch {
            val token = obtainDriveToken(activity, actionTag = "PUBLISH_CATALOG") ?: return@launch
            publishAdminCatalogWithToken(token)
        }
    }

    private suspend fun publishAdminCatalogWithToken(token: String) {
        _driveSyncState.value = DriveSyncUiState.Syncing("Publishing Admin Shared Catalog...")
        val catalogJson = adminCatalogStore.exportPublishedCatalogJson()
        driveManager.publishSharedCatalogJsonToDrive(token, catalogJson).onSuccess { url ->
            prefs.setSharedCatalogUrl(url)
            _publishedCatalogLink.value = url
            _driveSyncState.value = DriveSyncUiState.Synced(System.currentTimeMillis(), "Admin Manifest Published")
            _toastEvents.tryEmit("Shared Catalog ADMIN Drive पर पब्लिश हो गया! लिंक कॉपी करें।")
        }.onFailure { err ->
            val msg = err.message ?: "Publish failed"
            if (GoogleDriveBackupManager.isAuthRevokedError(msg)) {
                cachedDriveToken = null
                _driveSyncState.value = DriveSyncUiState.NeedAuth("Google Drive अनुमति पुनः दें")
            } else {
                _driveSyncState.value = DriveSyncUiState.Error(msg)
            }
            _toastEvents.tryEmit(msg)
        }
    }

    fun adminToggleSharedContentPublished(note: StudyNote) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.toggleSharedContentPublished(note.id, !note.published)
            _toastEvents.tryEmit(if (!note.published) "Published" else "Unpublished (Draft)")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminDeleteSharedContent(note: StudyNote) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.deleteSharedContent(note.id)
            _toastEvents.tryEmit("Deleted shared item '${note.title}'")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminUpsertSharedTopic(subjectId: String, name: String, description: String?) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.upsertSharedTopic(subjectId, name, description)
            _toastEvents.tryEmit("Shared Topic created!")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminDeleteSharedTopic(topicId: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.deleteSharedTopic(topicId)
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminUpsertSharedSubtopic(subjectId: String, topicId: String, name: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.upsertSharedSubtopic(subjectId, topicId, name)
            _toastEvents.tryEmit("Shared Sub-topic created!")
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminDeleteSharedSubtopic(subtopicId: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.deleteSharedSubtopic(subtopicId)
            autoRefreshAdminManifestIfPossible()
        }
    }

    fun adminUpdateWhatsAppRequestStatus(requestId: String, status: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            adminCatalogStore.updateWhatsAppRequestStatus(requestId, status)
            _toastEvents.tryEmit("Request marked $status")
        }
    }

    fun adminLoadStudentResults(studentUid: String, onLoaded: (List<TestResult>) -> Unit) {
        if (!isAdminUser) return
        onLoaded(adminCatalogStore.getStudentResults(studentUid))
    }

    private suspend fun syncStudentAnalyticsSummary() {
        val results = offlineStore.testResults.value
        val avg = if (results.isNotEmpty()) results.map { it.percentage }.average() else 0.0
        adminCatalogStore.upsertStudentSummary(
            summary = StudentSummary(
                id = storageOwnerId,
                userId = storageOwnerId,
                displayName = initialDisplayName,
                email = signedInEmail.orEmpty(),
                photoUrl = initialPhotoUrl,
                downloadsCount = _sharedDownloadsMap.value.size.toLong(),
                testAttemptsCount = results.size.toLong(),
                averageScorePct = avg,
                lastActiveMillis = System.currentTimeMillis(),
            ),
            latestResults = results,
        )
    }

    companion object {
        private const val TAG = "MainVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun formatNum(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)
    }
}
