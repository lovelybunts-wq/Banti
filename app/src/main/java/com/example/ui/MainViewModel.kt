package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalFileStorage
import com.example.data.local.LocalOfflineStore
import com.example.data.local.PdfExportHelper
import com.example.data.local.SharedDownloadRecord
import com.example.data.local.UserPreferencesManager
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.CloudPdfAnnotationDoc
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
import com.example.data.remote.CloudStorageService
import com.example.data.remote.GoogleDriveBackupManager
import com.example.data.remote.SharedCatalogRepository
import com.example.data.remote.StudyRepository
import com.example.ui.SharedCatalogMergeHelper.toSharedContents
import com.example.ui.SharedCatalogMergeHelper.toSharedMcqs
import com.example.ui.SharedCatalogMergeHelper.toSharedSubjects
import com.example.ui.SharedCatalogMergeHelper.toSharedSubtopics
import com.example.ui.SharedCatalogMergeHelper.toSharedTopics
import com.example.ui.SharedCatalogMergeHelper.toSharedVideos
import com.example.ui.theme.StudyThemeOption
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

class MainViewModel(
    private val appContext: Context,
    private val repository: StudyRepository?,
    private val sharedRepo: SharedCatalogRepository?,
    private val cloudStorage: CloudStorageService,
    private val offlineStore: LocalOfflineStore,
    private val fileStorage: LocalFileStorage,
    private val prefs: UserPreferencesManager,
    private val driveBackupManager: GoogleDriveBackupManager,
    val firebaseUid: String?,
    val driveUserId: String? = null,
    val initialDisplayName: String,
    val signedInEmail: String? = null,
    val initialPhotoUrl: String? = null,
) : ViewModel() {

    val storageOwnerId: String = firebaseUid
        ?: driveUserId?.takeIf { it.isNotBlank() }
        ?: LocalOfflineStore.OFFLINE_UID
    val isOfflineSession: Boolean = firebaseUid == null && driveUserId.isNullOrBlank()
    val isAdminUser: Boolean = signedInEmail.equals(UserPreferencesManager.ADMIN_EMAIL, ignoreCase = true)

    private val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var autoSyncJob: Job? = null
    @Volatile
    private var lastKnownDriveToken: String? = null

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val toastEvents = _toastEvents.asSharedFlow()

    private val _lastReturnedScore = MutableStateFlow<TestResult?>(null)
    val lastReturnedScore: StateFlow<TestResult?> = _lastReturnedScore.asStateFlow()

    private val _isDriveBusy = MutableStateFlow(false)
    val isDriveBusy: StateFlow<Boolean> = _isDriveBusy.asStateFlow()

    private val _sharedDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sharedDownloadProgress: StateFlow<Map<String, Float>> = _sharedDownloadProgress.asStateFlow()

    private val _sharedDownloadsMap = MutableStateFlow(fileStorage.loadSharedDownloadsMap())
    val sharedDownloadsMap: StateFlow<Map<String, SharedDownloadRecord>> = _sharedDownloadsMap.asStateFlow()

    private val _adminUploadProgress = MutableStateFlow<Float?>(null)
    val adminUploadProgress: StateFlow<Float?> = _adminUploadProgress.asStateFlow()

    private val _recentActivities = MutableStateFlow(fileStorage.loadRecentActivities(storageOwnerId))
    val recentActivities: StateFlow<List<RecentActivityItem>> = _recentActivities.asStateFlow()

    val localSettings: StateFlow<LocalAppSettings> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LocalAppSettings())

    private val cachedShared = SharedCatalogMergeHelper.loadCachedCatalog(fileStorage)

    val profileState: StateFlow<UiState<UserProfile?>> =
        if (firebaseUid != null && repository != null) repository.observeProfile(firebaseUid).toUiStateFlow("observeProfile")
        else offlineStore.profile.toUiStateFlow("offlineProfile")

    private val sharedSubjectsFlow = sharedRepo?.observeSharedSubjects(isAdminUser)
        ?: flowOf(cachedShared.toSharedSubjects())
    private val sharedTopicsFlow = sharedRepo?.observeSharedTopics(isAdminUser)
        ?: flowOf(cachedShared.toSharedTopics())
    private val sharedSubtopicsFlow = sharedRepo?.observeSharedSubtopics(isAdminUser)
        ?: flowOf(cachedShared.toSharedSubtopics())
    private val sharedContentsFlow = sharedRepo?.observeSharedContent(isAdminUser)
        ?: flowOf(cachedShared.toSharedContents())
    private val sharedVideosFlow = sharedRepo?.observeSharedVideos(isAdminUser)
        ?: flowOf(cachedShared.toSharedVideos())
    private val sharedMcqsFlow = sharedRepo?.observeSharedMcqs(isAdminUser)
        ?: flowOf(cachedShared.toSharedMcqs())

    private val personalSubjectsFlow =
        if (firebaseUid != null && repository != null) repository.observeSubjects(firebaseUid) else offlineStore.subjects
    private val personalTopicsFlow =
        if (firebaseUid != null && repository != null) repository.observeTopics(firebaseUid) else offlineStore.topics
    private val personalSubtopicsFlow =
        if (firebaseUid != null && repository != null) repository.observeSubtopics(firebaseUid) else offlineStore.subtopics
    private val personalNotesFlow =
        if (firebaseUid != null && repository != null) repository.observeNotes(firebaseUid) else offlineStore.notes
    private val personalVideosFlow =
        if (firebaseUid != null && repository != null) repository.observeVideos(firebaseUid) else offlineStore.videos
    private val personalMcqsFlow =
        if (firebaseUid != null && repository != null) repository.observeMcqs(firebaseUid) else offlineStore.mcqs

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

    val testResultsState: StateFlow<UiState<List<TestResult>>> =
        (if (firebaseUid != null && repository != null) repository.observeTestResults(firebaseUid) else offlineStore.testResults)
            .toUiStateFlow("testResults")

    val studyLogsState: StateFlow<UiState<List<StudyLog>>> =
        (if (firebaseUid != null && repository != null) repository.observeStudyLogs(firebaseUid) else offlineStore.studyLogs)
            .toUiStateFlow("studyLogs")

    val revisionDiaryState: StateFlow<UiState<List<RevisionDiaryEntry>>> =
        (if (firebaseUid != null && repository != null) repository.observeRevisionDiary(firebaseUid) else offlineStore.revisionDiary)
            .toUiStateFlow("revisionDiary")

    val personalNotesState: StateFlow<UiState<List<PersonalNote>>> =
        (if (firebaseUid != null && repository != null) repository.observePersonalNotes(firebaseUid) else offlineStore.personalNotes)
            .toUiStateFlow("personalNotes")

    val bookmarksState: StateFlow<UiState<List<BookmarkItem>>> =
        (if (firebaseUid != null && repository != null) repository.observeBookmarks(firebaseUid) else offlineStore.bookmarks)
            .toUiStateFlow("bookmarks")

    val pdfAnnotationsState: StateFlow<List<CloudPdfAnnotationDoc>> =
        (if (firebaseUid != null && repository != null) repository.observePdfAnnotations(firebaseUid) else offlineStore.pdfAnnotations)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    // Admin-only streams (empty for students)
    val adminSharedContent: StateFlow<List<StudyNote>> =
        (if (isAdminUser && sharedRepo != null) sharedRepo.observeSharedContent(true) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminSharedSubjects: StateFlow<List<Subject>> =
        (if (isAdminUser && sharedRepo != null) sharedRepo.observeSharedSubjects(true) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminSharedTopics: StateFlow<List<Topic>> =
        (if (isAdminUser && sharedRepo != null) sharedRepo.observeSharedTopics(true) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminSharedSubtopics: StateFlow<List<Subtopic>> =
        (if (isAdminUser && sharedRepo != null) sharedRepo.observeSharedSubtopics(true) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val studentSummaries: StateFlow<List<StudentSummary>> =
        (if (isAdminUser && sharedRepo != null) sharedRepo.observeStudentSummaries() else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminWhatsAppRequests: StateFlow<List<WhatsAppRequestRecord>> =
        (if (isAdminUser && sharedRepo != null) sharedRepo.observeAllWhatsAppRequests() else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        val uid = firebaseUid
        val repo = repository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                val role = if (isAdminUser) "admin" else "student"
                repo.ensureUserProfile(uid, initialDisplayName, signedInEmail, initialPhotoUrl, role)
                repo.fetchSupportWhatsAppDigits()?.let { prefs.setAdminWhatsAppNumber(it) }
                fileStorage.copyOfflineFilesToUser(uid)
                val offlineSnap = offlineStore.currentSnapshot()
                if (offlineSnap.hasUserEdits) {
                    repo.syncOfflineSnapshotToCloud(uid, offlineSnap).onSuccess {
                        offlineStore.markSyncedToCloud()
                        prefs.markSeeded(uid)
                        _toastEvents.tryEmit("ऑफ़लाइन डेटा क्लाउड पर सिंक हो गया!")
                    }
                } else {
                    repo.seedDefaultSubjectsIfEmpty(uid).onSuccess { prefs.markSeeded(uid) }
                }
                syncStudentAnalyticsSummary()
                driveBackupManager.silentGetDriveToken(appContext)?.let { lastKnownDriveToken = it }
            }
            viewModelScope.launch {
                // Keep offline cache of shared catalog fresh whenever online
                combine(sharedSubjectsFlow, sharedTopicsFlow, sharedSubtopicsFlow, sharedContentsFlow, sharedVideosFlow) { s, t, st, c, v ->
                    Triple(Triple(s, t, st), c, v)
                }.collect { (hier, c, v) ->
                    val mcqsList = (mcqsState.value as? UiState.Success)?.data?.filter { it.isShared } ?: emptyList()
                    SharedCatalogMergeHelper.saveCachedCatalog(
                        fileStorage, hier.first, hier.second, hier.third, c.filter { it.published }, v.filter { it.published }, mcqsList
                    )
                }
            }
        } else if (!driveUserId.isNullOrBlank()) {
            viewModelScope.launch {
                val rawOfflineStore = LocalOfflineStore(appContext)
                val rawSnap = rawOfflineStore.currentSnapshot()
                if (rawSnap.hasUserEdits) {
                    fileStorage.copyOfflineFilesToUser(storageOwnerId)
                    offlineStore.adoptOfflineSnapshotIfEmpty(rawSnap)
                    rawOfflineStore.markSyncedToCloud()
                }
                offlineStore.updateProfileMeta(initialDisplayName, signedInEmail, initialPhotoUrl, if (isAdminUser) "admin" else "student")
                driveBackupManager.silentGetDriveToken(appContext)?.let {
                    lastKnownDriveToken = it
                    syncOrRestoreWithTokenInternal(it, showMessages = false)
                }
            }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(label: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error in $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load data"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    fun showToast(message: String) { _toastEvents.tryEmit(message) }
    fun clearLastReturnedScore() { _lastReturnedScore.value = null }

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
            val uid = firebaseUid
            if (uid != null && repository != null) repository.updateTheme(uid, themeId)
            else offlineStore.updateTheme(themeId)
            scheduleAutoDriveSync()
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBiometricLockEnabled(enabled)
            _toastEvents.tryEmit(if (enabled) "Biometric Lock enabled" else "Biometric Lock disabled")
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setCloudSyncEnabled(enabled)
            if (enabled) scheduleAutoDriveSync()
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
            if (firebaseUid != null && repository != null) repository.updateSupportWhatsAppDigits(normalized)
            _toastEvents.tryEmit("Admin WhatsApp support number updated!")
        }
    }

    fun saveCustomLogo(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val fileName = fileStorage.saveCustomLogoFromUri(uri)
                prefs.setCustomLogoFileName(fileName)
            }.onSuccess { _toastEvents.tryEmit("Custom IMG Logo updated!") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Could not save logo image") }
        }
    }

    fun resetCustomLogo() {
        viewModelScope.launch {
            fileStorage.clearCustomLogo()
            prefs.setCustomLogoFileName("")
            _toastEvents.tryEmit("Default app logo restored")
        }
    }

    fun getCustomLogoFile(fileName: String): File? = fileStorage.getCustomLogoFile(fileName)

    fun saveReminderPreferences(enabled: Boolean, hour: Int, minute: Int, yesterdaySummary: String) {
        viewModelScope.launch {
            prefs.setReminder(enabled, hour, minute)
            prefs.setYesterdaySummary(yesterdaySummary)
        }
    }

    fun updateExamGoal(examName: String, examDateMillis: Long, subjects: List<Subject>, autoPlan: Boolean) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.updateExamGoal(uid, examName, examDateMillis).onSuccess {
                    if (autoPlan && subjects.isNotEmpty()) {
                        repository.generateExamRevisionPlan(uid, subjects, examDateMillis)
                            .onSuccess { days -> _toastEvents.tryEmit("Exam goal saved & $days-day plan created!") }
                            .onFailure { _toastEvents.tryEmit("Exam goal saved.") }
                    } else _toastEvents.tryEmit("Exam goal updated!")
                }.onFailure { _toastEvents.tryEmit(it.message ?: "Failed to save exam goal") }
            } else {
                offlineStore.updateExamGoal(examName, examDateMillis)
                if (autoPlan && subjects.isNotEmpty()) {
                    val days = offlineStore.generateExamRevisionPlan(subjects, examDateMillis)
                    _toastEvents.tryEmit("Exam goal saved & $days-day plan created!")
                } else _toastEvents.tryEmit("Exam goal updated!")
            }
            scheduleAutoDriveSync()
        }
    }

    fun addSubject(nameEn: String, nameHi: String?, iconKey: String, colorHex: String, sortOrder: Long) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addSubject(uid, nameEn, nameHi, iconKey, colorHex, sortOrder)
                    .onSuccess { _toastEvents.tryEmit("Subject added"); scheduleAutoDriveSync() }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add subject") }
            } else {
                offlineStore.addSubject(nameEn, nameHi, iconKey, colorHex, sortOrder)
                _toastEvents.tryEmit("Subject added"); scheduleAutoDriveSync()
            }
        }
    }

    fun addTopic(subjectId: String, name: String, description: String?) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addTopic(uid, subjectId, name, description)
                    .onSuccess { _toastEvents.tryEmit("Topic added"); scheduleAutoDriveSync() }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add topic") }
            } else {
                offlineStore.addTopic(subjectId, name, description)
                _toastEvents.tryEmit("Topic added"); scheduleAutoDriveSync()
            }
        }
    }

    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) repository.deleteTopic(uid, topicId).onSuccess { scheduleAutoDriveSync() }
            else { offlineStore.deleteTopic(topicId); scheduleAutoDriveSync() }
        }
    }

    fun addSubtopic(subjectId: String, topicId: String, name: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addSubtopic(uid, subjectId, topicId, name)
                    .onSuccess { _toastEvents.tryEmit("Sub-topic added"); scheduleAutoDriveSync() }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add sub-topic") }
            } else {
                offlineStore.addSubtopic(subjectId, topicId, name)
                _toastEvents.tryEmit("Sub-topic added"); scheduleAutoDriveSync()
            }
        }
    }

    fun deleteSubtopic(subtopicId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) repository.deleteSubtopic(uid, subtopicId).onSuccess { scheduleAutoDriveSync() }
            else { offlineStore.deleteSubtopic(subtopicId); scheduleAutoDriveSync() }
        }
    }

    fun markSubtopicStudiedToday(subtopic: Subtopic, subjectName: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.markSubtopicStudiedToday(uid, subtopic, subjectName)
                    .onSuccess { _toastEvents.tryEmit("आज के अध्ययन में जोड़ा गया!"); scheduleAutoDriveSync() }
            } else {
                offlineStore.markSubtopicStudiedToday(subtopic, subjectName)
                _toastEvents.tryEmit("आज के अध्ययन में जोड़ा गया!"); scheduleAutoDriveSync()
            }
        }
    }

    fun createFolder(subjectId: String, topicId: String, subtopicId: String, folderName: String) {
        viewModelScope.launch {
            val noteId = "note_${StudyRepository.newValidId()}"
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addNote(uid, noteId, subjectId, topicId, subtopicId, folderName, folderName, NoteType.FOLDER)
                    .onSuccess { _toastEvents.tryEmit("Folder '$folderName' created"); scheduleAutoDriveSync() }
            } else {
                offlineStore.addNote(noteId, subjectId, topicId, subtopicId, folderName, folderName, NoteType.FOLDER)
                _toastEvents.tryEmit("Folder '$folderName' created"); scheduleAutoDriveSync()
            }
        }
    }

    fun importFileNote(
        subjectId: String, topicId: String, subtopicId: String, folderName: String,
        customTitle: String, noteType: NoteType, uri: Uri, isMockTest: Boolean = noteType == NoteType.HTML,
    ) {
        viewModelScope.launch {
            val noteId = "note_${StudyRepository.newValidId()}"
            val ext = when (noteType) {
                NoteType.PDF -> "pdf"; NoteType.HTML -> "html"; NoteType.IMAGE -> "jpg"; NoteType.FOLDER -> "txt"
            }
            try {
                val (fileName, file) = fileStorage.copyUriToLocal(storageOwnerId, noteId, uri, ext)
                val htmlText = if (noteType == NoteType.HTML && file.length() <= 190_000L) file.readText(Charsets.UTF_8) else null
                val finalTitle = customTitle.ifBlank { fileName }
                val uid = firebaseUid
                if (uid != null && repository != null) {
                    repository.addNote(uid, noteId, subjectId, topicId, subtopicId, folderName, finalTitle, noteType, fileName, htmlText, isMockTest)
                        .onSuccess { _toastEvents.tryEmit("Saved '$finalTitle'"); scheduleAutoDriveSync() }
                        .onFailure { _toastEvents.tryEmit(it.message ?: "Cloud save failed") }
                } else {
                    offlineStore.addNote(noteId, subjectId, topicId, subtopicId, folderName, finalTitle, noteType, fileName, htmlText, isMockTest)
                    _toastEvents.tryEmit("Saved '$finalTitle'")
                    scheduleAutoDriveSync()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _toastEvents.tryEmit(e.message ?: "Failed to import file") }
        }
    }

    fun createSampleHtmlTest(subjectId: String, topicId: String, subtopicId: String, folderName: String, title: String) {
        viewModelScope.launch {
            val noteId = "note_${StudyRepository.newValidId()}"
            val html = LocalFileStorage.buildSampleHtmlMockTest(title)
            val fileName = fileStorage.writeLocalHtml(storageOwnerId, noteId, title, html)
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addNote(uid, noteId, subjectId, topicId, subtopicId, folderName, title, NoteType.HTML, fileName, html, true)
                    .onSuccess { _toastEvents.tryEmit("Sample HTML Test created!"); scheduleAutoDriveSync() }
            } else {
                offlineStore.addNote(noteId, subjectId, topicId, subtopicId, folderName, title, NoteType.HTML, fileName, html, true)
                _toastEvents.tryEmit("Sample HTML Test created!"); scheduleAutoDriveSync()
            }
        }
    }

    fun renameOrMoveNote(noteId: String, newTitle: String, newFolderName: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) repository.renameOrMoveNote(uid, noteId, newTitle, newFolderName).onSuccess { scheduleAutoDriveSync() }
            else { offlineStore.renameOrMoveNote(noteId, newTitle, newFolderName); scheduleAutoDriveSync() }
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
            val uid = firebaseUid
            if (uid != null && repository != null && !note.isShared) {
                repository.markNoteOpenedToday(uid, note.id)
                repository.addStudyLog(uid, StudyRepository.todayKey(), "STUDIED", note.title, subjectName, note.subtopicId, true, "Opened ${note.noteType}")
            } else if (!note.isShared) {
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
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deleteNote(uid, note.id).onSuccess { _toastEvents.tryEmit("Deleted '${note.title}'"); scheduleAutoDriveSync() }
            } else {
                offlineStore.deleteNote(note.id); _toastEvents.tryEmit("Deleted '${note.title}'"); scheduleAutoDriveSync()
            }
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
        if (note.isShared && (!note.downloadUrl.isNullOrBlank() || !note.storagePath.isNullOrBlank())) {
            val downloaded = cloudStorage.downloadSharedFileToCache(
                note.id, note.version, note.localFileName ?: "${note.id}.html", note.downloadUrl, note.storagePath, note.htmlContent, fileStorage
            ).getOrNull()
            if (downloaded != null && downloaded.exists()) {
                _sharedDownloadsMap.value = fileStorage.loadSharedDownloadsMap()
                return downloaded.readText(Charsets.UTF_8)
            }
        }
        return null
    }

    fun downloadSharedNote(note: StudyNote, onDownloaded: ((File) -> Unit)? = null) {
        viewModelScope.launch {
            _sharedDownloadProgress.value = _sharedDownloadProgress.value + (note.id to 0.05f)
            val fallbackName = note.localFileName ?: when (note.resolvedType()) {
                NoteType.PDF -> "${note.id}.pdf"; NoteType.HTML -> "${note.id}.html"; NoteType.IMAGE -> "${note.id}.jpg"; NoteType.FOLDER -> "${note.id}.txt"
            }
            val existingRec = _sharedDownloadsMap.value[note.id]
            val isFirstDownload = existingRec == null
            cloudStorage.downloadSharedFileToCache(
                contentId = note.id,
                version = note.version,
                localFileName = fallbackName,
                downloadUrl = note.downloadUrl,
                storagePath = note.storagePath,
                inlineHtml = note.htmlContent,
                fileStorage = fileStorage,
                onProgress = { pct -> _sharedDownloadProgress.value = _sharedDownloadProgress.value + (note.id to pct) }
            ).onSuccess { file ->
                _sharedDownloadProgress.value = _sharedDownloadProgress.value - note.id
                _sharedDownloadsMap.value = fileStorage.loadSharedDownloadsMap()
                if (isFirstDownload && firebaseUid != null) {
                    sharedRepo?.incrementSharedDownloadCount(note.id)
                    syncStudentAnalyticsSummary()
                }
                _toastEvents.tryEmit("Offline saved: ${note.title}")
                onDownloaded?.invoke(file)
            }.onFailure { err ->
                _sharedDownloadProgress.value = _sharedDownloadProgress.value - note.id
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
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.savePdfAnnotationDoc(uid, doc.noteId, encoded, withTime.updatedAtMillis)
            } else {
                offlineStore.savePdfAnnotationDoc(doc.noteId, encoded, withTime.updatedAtMillis)
            }
            if (!silent) _toastEvents.tryEmit("PDF annotations saved!")
        }
    }

    fun saveAnnotatedPdfAsNewCopy(sourceFile: File, note: StudyNote?, doc: PdfAnnotationDocument) {
        viewModelScope.launch {
            val newNoteId = "note_${StudyRepository.newValidId()}"
            val cleanTitle = "${note?.title ?: "Annotated"} (My Annotated Copy)"
            val destFileName = "${newNoteId}_annotated.pdf"
            val destFile = File(fileStorage.userDir(storageOwnerId), destFileName)
            runCatching {
                PdfExportHelper.saveAnnotatedPdfCopy(sourceFile, doc, destFile)
            }.onSuccess {
                val subjId = note?.subjectId?.takeIf { it.isNotBlank() } ?: "subj_other"
                val topId = note?.topicId?.takeIf { it.isNotBlank() } ?: "topic_11_0"
                val subId = note?.subtopicId?.takeIf { it.isNotBlank() } ?: "sub_11_0_0"
                val uid = firebaseUid
                if (uid != null && repository != null) {
                    repository.addNote(uid, newNoteId, subjId, topId, subId, "Annotated Copies", cleanTitle, NoteType.PDF, destFileName)
                } else {
                    offlineStore.addNote(newNoteId, subjId, topId, subId, "Annotated Copies", cleanTitle, NoteType.PDF, destFileName)
                }
                _toastEvents.tryEmit("Annotated copy saved! Check Personal/Annotated Copies or Export.")
                fileStorage.sharePdfFile(destFile, "Share Annotated PDF Copy")
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
            val resultId = "res_${StudyRepository.newValidId()}"
            val breakdownJson = runCatching { jsonCodec.encodeToString(questionsBreakdown) }.getOrNull()
            val summaryText = "$correctCount Right • $wrongCount Wrong • $unattemptedCount Left"
            if (!noteId.isNullOrBlank()) clearInProgressTestAttempt(noteId)

            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.recordTestResult(
                    uid = uid, subjectId = subjectId, topicId = topicId, subtopicId = subtopicId,
                    noteId = noteId, testTitle = testTitle, scoreObtained = scoreObtained, totalMarks = totalMarks,
                    summaryText = summaryText, totalQuestions = totalQuestions, attemptedCount = attemptedCount,
                    correctCount = correctCount, wrongCount = wrongCount, unattemptedCount = unattemptedCount,
                    durationSeconds = durationSeconds, testMode = testMode, breakdownJson = breakdownJson, docId = resultId,
                ).onSuccess {
                    val pct = if (totalMarks > 0.0) ((scoreObtained / totalMarks) * 100.0).coerceIn(0.0, 100.0) else 0.0
                    _lastReturnedScore.value = TestResult(
                        id = resultId, userId = uid, subjectId = subjectId, topicId = topicId, subtopicId = subtopicId,
                        noteId = noteId, testTitle = testTitle, scoreObtained = scoreObtained, totalMarks = totalMarks,
                        percentage = pct, totalQuestions = totalQuestions, attemptedCount = attemptedCount,
                        correctCount = correctCount, wrongCount = wrongCount, unattemptedCount = unattemptedCount,
                        durationSeconds = durationSeconds, testMode = testMode, breakdownJson = breakdownJson,
                        summaryText = summaryText, takenDate = StudyRepository.todayKey(),
                    )
                    repository.addStudyLog(uid, StudyRepository.todayKey(), "STUDIED", "$testTitle (${formatNum(scoreObtained)}/${formatNum(totalMarks)})", subjectName, subtopicId, true, summaryText)
                    syncStudentAnalyticsSummary(extraAttempt = true)
                    scheduleAutoDriveSync()
                    onSaved(resultId)
                }.onFailure { _toastEvents.tryEmit(it.message ?: "Failed to save test result") }
            } else {
                val res = offlineStore.recordTestResult(
                    subjectId = subjectId, topicId = topicId, subtopicId = subtopicId, noteId = noteId,
                    testTitle = testTitle, scoreObtained = scoreObtained, totalMarks = totalMarks,
                    summaryText = summaryText, subjectName = subjectName, totalQuestions = totalQuestions,
                    attemptedCount = attemptedCount, correctCount = correctCount, wrongCount = wrongCount,
                    unattemptedCount = unattemptedCount, durationSeconds = durationSeconds,
                    testMode = testMode, breakdownJson = breakdownJson, resultId = resultId,
                )
                _lastReturnedScore.value = res
                scheduleAutoDriveSync()
                onSaved(resultId)
            }
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
            id = note.id.ifBlank { "pnote_${StudyRepository.newValidId()}" },
            updatedAtMillis = System.currentTimeMillis()
        )
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.savePersonalNote(uid, withTime).onSuccess {
                    if (!silent) _toastEvents.tryEmit("Personal note saved!")
                    scheduleAutoDriveSync()
                }.onFailure { _toastEvents.tryEmit(it.message ?: "Save failed") }
            } else {
                offlineStore.upsertPersonalNote(withTime)
                if (!silent) _toastEvents.tryEmit("Personal note saved!")
                scheduleAutoDriveSync()
            }
        }
    }

    fun deletePersonalNote(noteId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deletePersonalNote(uid, noteId).onSuccess { _toastEvents.tryEmit("Note deleted"); scheduleAutoDriveSync() }
            } else {
                offlineStore.deletePersonalNote(noteId); _toastEvents.tryEmit("Note deleted"); scheduleAutoDriveSync()
            }
        }
    }

    fun copyPersonalNoteImage(noteId: String, uri: Uri, onCopied: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                fileStorage.copyUriToLocal(storageOwnerId, "pimg_${noteId}", uri, "jpg").first
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
            val uid = firebaseUid
            if (existing != null) {
                if (uid != null && repository != null) repository.deleteBookmark(uid, existing.id)
                else offlineStore.deleteBookmark(existing.id)
                _toastEvents.tryEmit("Bookmark removed")
            } else {
                val item = BookmarkItem(
                    id = "bmk_${StudyRepository.newValidId()}",
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
                if (uid != null && repository != null) repository.saveBookmark(uid, item)
                else offlineStore.upsertBookmark(item)
                _toastEvents.tryEmit("Added to Bookmarks!")
            }
            scheduleAutoDriveSync()
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) repository.deleteBookmark(uid, bookmarkId)
            else offlineStore.deleteBookmark(bookmarkId)
        }
    }

    // Videos & MCQs
    fun addVideo(subjectId: String, topicId: String, subtopicId: String, title: String, url: String, channelOrNote: String?) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addVideo(uid, subjectId, topicId, subtopicId, title, url, channelOrNote)
                    .onSuccess { _toastEvents.tryEmit("YouTube link saved!"); scheduleAutoDriveSync() }
            } else {
                offlineStore.addVideo(subjectId, topicId, subtopicId, title, url, channelOrNote)
                _toastEvents.tryEmit("YouTube link saved!"); scheduleAutoDriveSync()
            }
        }
    }

    fun markVideoWatched(video: VideoLink, subjectName: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null && !video.isShared) {
                repository.markVideoWatchedToday(uid, video.id)
                repository.addStudyLog(uid, StudyRepository.todayKey(), "STUDIED", video.title, subjectName, video.subtopicId, true, "Watched YouTube video")
            } else if (!video.isShared) offlineStore.markVideoWatchedToday(video, subjectName)
        }
    }

    fun deleteVideo(video: VideoLink) {
        viewModelScope.launch {
            if (video.isShared) { if (isAdminUser) sharedRepo?.deleteSharedVideo(video.id); return@launch }
            val uid = firebaseUid
            if (uid != null && repository != null) repository.deleteVideo(uid, video.id).onSuccess { scheduleAutoDriveSync() }
            else { offlineStore.deleteVideo(video.id); scheduleAutoDriveSync() }
        }
    }

    fun addMcq(subjectId: String, topicId: String, subtopicId: String, question: String, options: List<String>, correctIndex: Int, explanation: String?) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addMcq(uid, subjectId, topicId, subtopicId, question, options, correctIndex, explanation)
                    .onSuccess { _toastEvents.tryEmit("Practice MCQ added!"); scheduleAutoDriveSync() }
            } else {
                offlineStore.addMcq(subjectId, topicId, subtopicId, question, options, correctIndex, explanation)
                _toastEvents.tryEmit("Practice MCQ added!"); scheduleAutoDriveSync()
            }
        }
    }

    fun deleteMcq(mcq: McqQuestion) {
        viewModelScope.launch {
            if (mcq.isShared) { if (isAdminUser) sharedRepo?.deleteSharedMcq(mcq.id); return@launch }
            val uid = firebaseUid
            if (uid != null && repository != null) repository.deleteMcq(uid, mcq.id).onSuccess { scheduleAutoDriveSync() }
            else { offlineStore.deleteMcq(mcq.id); scheduleAutoDriveSync() }
        }
    }

    // Study Logs & Revision Diary
    fun addStudyPlanItem(dateKey: String, entryType: String, title: String, subjectName: String?, notes: String?) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) repository.addStudyLog(uid, dateKey, entryType, title, subjectName, completed = entryType == "STUDIED", notes = notes)
            else offlineStore.addStudyLog(dateKey, entryType, title, subjectName, notes)
            _toastEvents.tryEmit("Study item saved!"); scheduleAutoDriveSync()
        }
    }

    fun toggleStudyLogCompleted(log: StudyLog) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) repository.toggleStudyLogCompleted(uid, log.id, !log.completed)
            else offlineStore.toggleStudyLogCompleted(log.id, !log.completed)
            scheduleAutoDriveSync()
        }
    }

    fun deleteStudyLog(logId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) repository.deleteStudyLog(uid, logId)
            else offlineStore.deleteStudyLog(logId)
            scheduleAutoDriveSync()
        }
    }

    fun addRevisionDiaryEntry(title: String, subjectName: String?, sourceName: String, content: String, pdfUri: Uri? = null) {
        viewModelScope.launch {
            val today = StudyRepository.todayKey()
            val docId = "diary_${StudyRepository.newValidId()}"
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
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addRevisionDiaryEntry(uid, today, title, subjectName, sourceName, content, pdfTitle, pdfLocalFileName, docId)
                    .onSuccess { _toastEvents.tryEmit("रिवीजन डायरी में सेव हो गया!"); scheduleAutoDriveSync() }
            } else {
                offlineStore.addRevisionDiaryEntry(today, title, subjectName, sourceName, content, pdfTitle, pdfLocalFileName, docId)
                _toastEvents.tryEmit("रिवीजन डायरी में सेव हो गया!"); scheduleAutoDriveSync()
            }
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
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.updateRevisionDiaryEntry(uid, entryId, title, subjectName, sourceName, content, finalPdfTitle, finalPdfFileName)
                    .onSuccess { _toastEvents.tryEmit("रिवीजन डायरी अपडेट हो गई!"); scheduleAutoDriveSync() }
            } else {
                offlineStore.updateRevisionDiaryEntry(entryId, title, subjectName, sourceName, content, finalPdfTitle, finalPdfFileName)
                _toastEvents.tryEmit("रिवीजन डायरी अपडेट हो गई!"); scheduleAutoDriveSync()
            }
        }
    }

    fun deleteRevisionDiaryEntry(entry: RevisionDiaryEntry) {
        viewModelScope.launch {
            fileStorage.deleteLocalFile(storageOwnerId, entry.pdfLocalFileName)
            val uid = firebaseUid
            if (uid != null && repository != null) repository.deleteRevisionDiaryEntry(uid, entry.id).onSuccess { scheduleAutoDriveSync() }
            else { offlineStore.deleteRevisionDiaryEntry(entry.id); scheduleAutoDriveSync() }
        }
    }

    // WhatsApp Requests
    fun recordWhatsAppRequest(requestType: String, subject: String, topic: String, subtopic: String, extraMessage: String) {
        viewModelScope.launch {
            val uid = firebaseUid ?: return@launch
            sharedRepo?.submitWhatsAppRequest(
                WhatsAppRequestRecord(
                    userId = uid,
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

    // Admin Shared Catalog Operations
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
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            val docId = existingNote?.id?.takeIf { it.isNotBlank() } ?: "shared_doc_${StudyRepository.newValidId()}"
            val ext = when (noteType) {
                NoteType.PDF -> "pdf"; NoteType.HTML -> "html"; NoteType.IMAGE -> "jpg"; NoteType.FOLDER -> "txt"
            }
            var storagePath = existingNote?.storagePath
            var downloadUrl = existingNote?.downloadUrl
            var localFileName = existingNote?.localFileName
            var htmlText = existingNote?.htmlContent
            var fileSize = existingNote?.fileSizeBytes ?: 0L

            if (fileUri != null) {
                _adminUploadProgress.value = 0.05f
                try {
                    val (displayName, mimeType, bytes) = fileStorage.readUriBytesWithMeta(fileUri, ext)
                    localFileName = "${docId}_$displayName"
                    fileSize = bytes.size.toLong()
                    if (noteType == NoteType.HTML && bytes.size <= 190_000) {
                        htmlText = String(bytes, Charsets.UTF_8)
                    }
                    val cloudPath = "shared_materials/$subjectId/$docId/$displayName"
                    val uploadRes = cloudStorage.uploadSharedFile(cloudPath, bytes, mimeType) { pct ->
                        _adminUploadProgress.value = pct
                    }
                    uploadRes.onSuccess { res ->
                        storagePath = res.storagePath
                        downloadUrl = res.downloadUrl
                        fileSize = res.fileSizeBytes
                    }.onFailure { err ->
                        // Allow small HTML fallback even if Firebase Storage bucket rules are not yet enabled
                        if (noteType == NoteType.HTML && !htmlText.isNullOrBlank()) {
                            _toastEvents.tryEmit("Storage unavailable (${err.message}); saving HTML directly to Firestore!")
                        } else {
                            _adminUploadProgress.value = null
                            _toastEvents.tryEmit(err.message ?: "Cloud upload failed")
                            return@launch
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    _adminUploadProgress.value = null
                    _toastEvents.tryEmit(e.message ?: "Could not read file")
                    return@launch
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
                storagePath = storagePath,
                downloadUrl = downloadUrl,
                version = nextVersion,
                published = published,
                downloadsCount = existingNote?.downloadsCount ?: 0L,
                fileSizeBytes = fileSize,
                isMockTest = isMockTest,
            )
            sharedRepo.upsertSharedContent(noteObj, isNew = existingNote == null)
                .onSuccess { _toastEvents.tryEmit("Published to all students (v$nextVersion)!") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Failed to save shared item") }
            _adminUploadProgress.value = null
        }
    }

    fun adminToggleSharedContentPublished(note: StudyNote) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            sharedRepo.toggleSharedContentPublished(note.id, note.title, !note.published)
                .onSuccess { _toastEvents.tryEmit(if (!note.published) "Published" else "Unpublished (Draft)") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Failed") }
        }
    }

    fun adminDeleteSharedContent(note: StudyNote) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            cloudStorage.deleteStorageObject(note.storagePath)
            sharedRepo.deleteSharedContent(note.id)
                .onSuccess { _toastEvents.tryEmit("Deleted shared item '${note.title}'") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Delete failed") }
        }
    }

    fun adminUpsertSharedTopic(subjectId: String, name: String, description: String?) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            sharedRepo.upsertSharedTopic(Topic(subjectId = subjectId, name = name, description = description, isShared = true, published = true))
                .onSuccess { _toastEvents.tryEmit("Shared Topic created!") }
        }
    }

    fun adminDeleteSharedTopic(topicId: String) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch { sharedRepo.deleteSharedTopic(topicId) }
    }

    fun adminUpsertSharedSubtopic(subjectId: String, topicId: String, name: String) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            sharedRepo.upsertSharedSubtopic(Subtopic(subjectId = subjectId, topicId = topicId, name = name, isShared = true, published = true))
                .onSuccess { _toastEvents.tryEmit("Shared Sub-topic created!") }
        }
    }

    fun adminDeleteSharedSubtopic(subtopicId: String) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch { sharedRepo.deleteSharedSubtopic(subtopicId) }
    }

    fun adminUpsertSharedVideo(subjectId: String, topicId: String, subtopicId: String, title: String, url: String, note: String?) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            sharedRepo.upsertSharedVideo(VideoLink(subjectId = subjectId, topicId = topicId, subtopicId = subtopicId, title = title, youtubeUrl = url, channelOrNote = note, isShared = true, published = true))
                .onSuccess { _toastEvents.tryEmit("Shared Video added for all students!") }
        }
    }

    fun adminUpsertSharedMcq(subjectId: String, topicId: String, subtopicId: String, q: String, opts: List<String>, idx: Int, exp: String?) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            sharedRepo.upsertSharedMcq(McqQuestion(subjectId = subjectId, topicId = topicId, subtopicId = subtopicId, question = q, options = opts, correctIndex = idx.toLong(), explanation = exp, isShared = true, published = true))
                .onSuccess { _toastEvents.tryEmit("Shared MCQ added for all students!") }
        }
    }

    fun adminUpdateWhatsAppRequestStatus(requestId: String, status: String) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            sharedRepo.updateWhatsAppRequestStatus(requestId, status)
                .onSuccess { _toastEvents.tryEmit("Request marked $status") }
        }
    }

    fun adminLoadStudentResults(studentUid: String, onLoaded: (List<TestResult>) -> Unit) {
        if (!isAdminUser || sharedRepo == null) return
        viewModelScope.launch {
            sharedRepo.fetchStudentTestResultsForAdmin(studentUid)
                .onSuccess(onLoaded)
                .onFailure { _toastEvents.tryEmit(it.message ?: "Failed to load student results") }
        }
    }

    private fun syncStudentAnalyticsSummary(extraAttempt: Boolean = false) {
        val uid = firebaseUid ?: return
        val repo = sharedRepo ?: return
        viewModelScope.launch {
            val results = (testResultsState.value as? UiState.Success)?.data ?: emptyList()
            val count = results.size.toLong() + (if (extraAttempt && results.isEmpty()) 1L else 0L)
            val avg = if (results.isNotEmpty()) results.map { it.percentage }.average() else 0.0
            repo.upsertStudentSummary(
                uid = uid,
                displayName = initialDisplayName,
                email = signedInEmail.orEmpty(),
                photoUrl = initialPhotoUrl,
                downloadsCount = _sharedDownloadsMap.value.size.toLong(),
                testAttemptsCount = count,
                averageScorePct = avg,
            )
        }
    }

    // JSON Backup & Google Drive Sync
    fun buildBackupJson(): String = offlineStore.exportSnapshotJson(
        subjectsList = (subjectsState.value as? UiState.Success)?.data ?: emptyList(),
        topicsList = (topicsState.value as? UiState.Success)?.data ?: emptyList(),
        subtopicsList = (subtopicsState.value as? UiState.Success)?.data ?: emptyList(),
        notesList = ((notesState.value as? UiState.Success)?.data ?: emptyList()).filter { !it.isShared },
        videosList = ((videosState.value as? UiState.Success)?.data ?: emptyList()).filter { !it.isShared },
        mcqsList = ((mcqsState.value as? UiState.Success)?.data ?: emptyList()).filter { !it.isShared },
        resultsList = (testResultsState.value as? UiState.Success)?.data ?: emptyList(),
        logsList = (studyLogsState.value as? UiState.Success)?.data ?: emptyList(),
        diaryList = (revisionDiaryState.value as? UiState.Success)?.data ?: emptyList(),
        userProfile = (profileState.value as? UiState.Success)?.data,
        personalNotesList = (personalNotesState.value as? UiState.Success)?.data ?: emptyList(),
        bookmarksList = (bookmarksState.value as? UiState.Success)?.data ?: emptyList(),
        pdfAnnotationsList = pdfAnnotationsState.value,
    )

    fun restoreFromBackupJson(rawJson: String, attachedFiles: Map<String, ByteArray> = emptyMap()) {
        viewModelScope.launch { restoreFromBackupJsonSuspend(rawJson, attachedFiles, showToast = true) }
    }

    private suspend fun restoreFromBackupJsonSuspend(rawJson: String, attachedFiles: Map<String, ByteArray>, showToast: Boolean) {
        try {
            attachedFiles.forEach { (name, bytes) -> fileStorage.writeLocalBytes(storageOwnerId, name, bytes) }
            val snap = offlineStore.importSnapshotJson(rawJson)
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.syncOfflineSnapshotToCloud(uid, snap).onSuccess { offlineStore.markSyncedToCloud() }
            }
            if (showToast) _toastEvents.tryEmit("बैकअप सफलतापूर्वक रिस्टोर हो गया!")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _toastEvents.tryEmit(e.message ?: "Restore failed") }
    }

    fun triggerDriveSyncAfterLogin(accessToken: String) {
        lastKnownDriveToken = accessToken
        viewModelScope.launch { syncOrRestoreWithTokenInternal(accessToken, showMessages = true) }
    }

    private suspend fun syncOrRestoreWithTokenInternal(accessToken: String, showMessages: Boolean) {
        if (!localSettings.value.cloudSyncEnabled) return
        _isDriveBusy.value = true
        try {
            val hasCloudBackup = driveBackupManager.hasBackupInLearnTalentFolder(accessToken)
            val hasLocalEdits = offlineStore.currentSnapshot().hasUserEdits
            val localFiles = fileStorage.listAllUserFiles(storageOwnerId)
            if (hasCloudBackup && (!hasLocalEdits || localFiles.isEmpty())) {
                driveBackupManager.restoreFromLearnTalentFolder(accessToken).onSuccess { restored ->
                    restoreFromBackupJsonSuspend(restored.snapshotJson, restored.restoredBinaryFiles, showToast = showMessages)
                    if (showMessages) _toastEvents.tryEmit("Google Drive से आपका सारा डेटा और फाइलें रिस्टोर हो गईं!")
                }
            } else if (hasLocalEdits || localFiles.isNotEmpty()) {
                driveBackupManager.backupToLearnTalentFolder(accessToken, buildBackupJson(), localFiles).onSuccess {
                    prefs.setLastDriveBackupMillis(System.currentTimeMillis())
                    if (showMessages) _toastEvents.tryEmit("Google Drive 'Learn Talent' फोल्डर से सिंक हो गया!")
                }
            }
        } finally { _isDriveBusy.value = false }
    }

    private fun scheduleAutoDriveSync() {
        if (isOfflineSession || !localSettings.value.cloudSyncEnabled) return
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(1500L)
            if (!localSettings.value.cloudSyncEnabled) return@launch
            val token = lastKnownDriveToken ?: driveBackupManager.silentGetDriveToken(appContext) ?: return@launch
            lastKnownDriveToken = token
            driveBackupManager.backupToLearnTalentFolder(token, buildBackupJson(), fileStorage.listAllUserFiles(storageOwnerId))
                .onSuccess { prefs.setLastDriveBackupMillis(System.currentTimeMillis()) }
        }
    }

    fun runDriveBackupWithToken(accessToken: String) {
        lastKnownDriveToken = accessToken
        viewModelScope.launch {
            _isDriveBusy.value = true
            try {
                driveBackupManager.backupToLearnTalentFolder(accessToken, buildBackupJson(), fileStorage.listAllUserFiles(storageOwnerId))
                    .onSuccess {
                        prefs.setLastDriveBackupMillis(System.currentTimeMillis())
                        _toastEvents.tryEmit("Google Drive 'Learn Talent' फोल्डर में ${it.uploadedFilesCount} फाइलें सेव हुईं!")
                    }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Google Drive backup failed") }
            } finally { _isDriveBusy.value = false }
        }
    }

    fun runDriveRestoreWithToken(accessToken: String) {
        lastKnownDriveToken = accessToken
        viewModelScope.launch {
            _isDriveBusy.value = true
            try {
                driveBackupManager.restoreFromLearnTalentFolder(accessToken)
                    .onSuccess { restoreFromBackupJsonSuspend(it.snapshotJson, it.restoredBinaryFiles, showToast = true) }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Google Drive restore failed") }
            } finally { _isDriveBusy.value = false }
        }
    }

    companion object {
        private const val TAG = "MainVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun formatNum(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)
    }
}
