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
import com.example.data.remote.SharedCatalogRepository
import com.example.data.remote.StudyRepository
import com.example.ui.theme.StudyThemeOption
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    private val repository: StudyRepository,
    private val sharedRepo: SharedCatalogRepository,
    private val cloudStorage: CloudStorageService,
    private val offlineStore: LocalOfflineStore,
    private val fileStorage: LocalFileStorage,
    private val prefs: UserPreferencesManager,
    val firebaseUid: String,
    val initialDisplayName: String,
    val signedInEmail: String? = null,
    val initialPhotoUrl: String? = null,
) : ViewModel() {

    val storageOwnerId: String = firebaseUid
    val isAdminUser: Boolean = signedInEmail.equals(UserPreferencesManager.ADMIN_EMAIL, ignoreCase = true)

    private val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val toastEvents = _toastEvents.asSharedFlow()

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

    val localSettings: StateFlow<LocalAppSettings> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LocalAppSettings())

    val profileState: StateFlow<UiState<UserProfile?>> =
        repository.observeProfile(firebaseUid).toUiStateFlow("observeProfile")

    private val sharedSubjectsFlow = sharedRepo.observeSharedSubjects(isAdminUser)
    private val sharedTopicsFlow = sharedRepo.observeSharedTopics(isAdminUser)
    private val sharedSubtopicsFlow = sharedRepo.observeSharedSubtopics(isAdminUser)
    private val sharedContentsFlow = sharedRepo.observeSharedContent(isAdminUser)
    private val sharedVideosFlow = sharedRepo.observeSharedVideos(isAdminUser)
    private val sharedMcqsFlow = sharedRepo.observeSharedMcqs(isAdminUser)
    private val sharedFullLengthExamsFlow = sharedRepo.observeSharedFullLengthExams(isAdminUser)

    private val personalSubjectsFlow = repository.observeSubjects(firebaseUid)
    private val personalTopicsFlow = repository.observeTopics(firebaseUid)
    private val personalSubtopicsFlow = repository.observeSubtopics(firebaseUid)
    private val personalNotesFlow = repository.observeNotes(firebaseUid)
    private val personalVideosFlow = repository.observeVideos(firebaseUid)
    private val personalMcqsFlow = repository.observeMcqs(firebaseUid)
    private val personalFullLengthExamsFlow = repository.observeFullLengthExams(firebaseUid)

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
        repository.observeTestResults(firebaseUid).toUiStateFlow("testResults")

    val studyLogsState: StateFlow<UiState<List<StudyLog>>> =
        repository.observeStudyLogs(firebaseUid).toUiStateFlow("studyLogs")

    val revisionDiaryState: StateFlow<UiState<List<RevisionDiaryEntry>>> =
        repository.observeRevisionDiary(firebaseUid).toUiStateFlow("revisionDiary")

    val personalNotesState: StateFlow<UiState<List<PersonalNote>>> =
        repository.observePersonalNotes(firebaseUid).toUiStateFlow("personalNotes")

    val bookmarksState: StateFlow<UiState<List<BookmarkItem>>> =
        repository.observeBookmarks(firebaseUid).toUiStateFlow("bookmarks")

    val pdfAnnotationsState: StateFlow<List<CloudPdfAnnotationDoc>> =
        repository.observePdfAnnotations(firebaseUid)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    // Admin-only streams (empty for students)
    val adminSharedContent: StateFlow<List<StudyNote>> =
        (if (isAdminUser) sharedRepo.observeSharedContent(true) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminSharedTopics: StateFlow<List<Topic>> =
        (if (isAdminUser) sharedRepo.observeSharedTopics(true) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminSharedSubtopics: StateFlow<List<Subtopic>> =
        (if (isAdminUser) sharedRepo.observeSharedSubtopics(true) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val studentSummaries: StateFlow<List<StudentSummary>> =
        (if (isAdminUser) sharedRepo.observeStudentSummaries() else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val adminWhatsAppRequests: StateFlow<List<WhatsAppRequestRecord>> =
        (if (isAdminUser) sharedRepo.observeAllWhatsAppRequests() else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        viewModelScope.launch {
            val role = if (isAdminUser) "admin" else "student"
            repository.ensureUserProfile(firebaseUid, initialDisplayName, signedInEmail, initialPhotoUrl, role)
            repository.fetchSupportWhatsAppDigits()?.let { prefs.setAdminWhatsAppNumber(it) }
            fileStorage.copyOfflineFilesToUser(firebaseUid)
            val legacySnap = offlineStore.currentSnapshot()
            if (legacySnap.hasUserEdits) {
                repository.syncOfflineSnapshotToCloud(firebaseUid, legacySnap).onSuccess {
                    offlineStore.markSyncedToCloud()
                    prefs.markSeeded(firebaseUid)
                }
            } else {
                repository.seedDefaultSubjectsIfEmpty(firebaseUid).onSuccess { prefs.markSeeded(firebaseUid) }
            }
            syncStudentAnalyticsSummary()
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
            repository.updateTheme(firebaseUid, themeId)
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
            repository.updateSupportWhatsAppDigits(normalized)
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
            repository.updateExamGoal(firebaseUid, examName, examDateMillis).onSuccess {
                if (autoPlan && subjects.isNotEmpty()) {
                    repository.generateExamRevisionPlan(firebaseUid, subjects, examDateMillis)
                        .onSuccess { days -> _toastEvents.tryEmit("Exam goal saved & $days-day plan created!") }
                        .onFailure { _toastEvents.tryEmit("Exam goal saved.") }
                } else {
                    _toastEvents.tryEmit("Exam goal updated!")
                }
            }.onFailure { _toastEvents.tryEmit(it.message ?: "Failed to save exam goal") }
        }
    }

    fun addSubject(nameEn: String, nameHi: String?, iconKey: String, colorHex: String, sortOrder: Long) {
        viewModelScope.launch {
            repository.addSubject(firebaseUid, nameEn, nameHi, iconKey, colorHex, sortOrder)
                .onSuccess { _toastEvents.tryEmit("Subject added") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add subject") }
        }
    }

    fun addTopic(subjectId: String, name: String, description: String?) {
        viewModelScope.launch {
            repository.addTopic(firebaseUid, subjectId, name, description)
                .onSuccess { _toastEvents.tryEmit("Topic added") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add topic") }
        }
    }

    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            repository.deleteTopic(firebaseUid, topicId)
        }
    }

    fun addSubtopic(subjectId: String, topicId: String, name: String) {
        viewModelScope.launch {
            repository.addSubtopic(firebaseUid, subjectId, topicId, name)
                .onSuccess { _toastEvents.tryEmit("Sub-topic added") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add sub-topic") }
        }
    }

    fun deleteSubtopic(subtopicId: String) {
        viewModelScope.launch {
            repository.deleteSubtopic(firebaseUid, subtopicId)
        }
    }

    fun markSubtopicStudiedToday(subtopic: Subtopic, subjectName: String) {
        viewModelScope.launch {
            repository.markSubtopicStudiedToday(firebaseUid, subtopic, subjectName)
                .onSuccess { _toastEvents.tryEmit("आज के अध्ययन में जोड़ा गया!") }
        }
    }

    // Full Length Exams & HTML Mock Tests
    fun addFullLengthExam(name: String, subtitle: String?) {
        viewModelScope.launch {
            repository.addFullLengthExam(firebaseUid, name, subtitle)
                .onSuccess { _toastEvents.tryEmit("एग्जाम '$name' बनाया गया!") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Could not create exam") }
        }
    }

    fun deleteFullLengthExam(exam: FullLengthExam) {
        viewModelScope.launch {
            if (exam.isShared) {
                if (isAdminUser) {
                    sharedRepo.deleteSharedFullLengthExam(exam.id)
                        .onSuccess { _toastEvents.tryEmit("Deleted '${exam.name}'") }
                }
                return@launch
            }
            repository.deleteFullLengthExam(firebaseUid, exam.id)
                .onSuccess { _toastEvents.tryEmit("Deleted '${exam.name}'") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Could not delete exam") }
        }
    }

    fun uploadFullLengthHtmlTest(examId: String, examName: String, title: String, uri: Uri) {
        viewModelScope.launch {
            val noteId = "note_${StudyRepository.newValidId()}"
            try {
                val (fileName, file) = fileStorage.copyUriToLocal(storageOwnerId, noteId, uri, "html")
                val htmlText = if (file.length() <= 190_000L) file.readText(Charsets.UTF_8) else null
                val finalTitle = title.trim().ifBlank { fileName.substringBeforeLast(".") }
                repository.addNote(
                    uid = firebaseUid,
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
                ).onSuccess {
                    if (htmlText == null) {
                        _toastEvents.tryEmit("'$finalTitle' सेव हुआ (फाइल बड़ी होने के कारण लोकल से खुलेगी)")
                    } else {
                        _toastEvents.tryEmit("HTML टेस्ट '$finalTitle' अपलोड हो गया!")
                    }
                }.onFailure {
                    _toastEvents.tryEmit(it.message ?: "Cloud save failed")
                }
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
            val noteId = "note_${StudyRepository.newValidId()}"
            repository.addNote(firebaseUid, noteId, subjectId, topicId, subtopicId, folderName, folderName, NoteType.FOLDER)
                .onSuccess { _toastEvents.tryEmit("Folder '$folderName' created") }
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
                repository.addNote(firebaseUid, noteId, subjectId, topicId, subtopicId, folderName, finalTitle, noteType, fileName, htmlText, isMockTest)
                    .onSuccess { _toastEvents.tryEmit("Saved '$finalTitle'") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Cloud save failed") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _toastEvents.tryEmit(e.message ?: "Failed to import file") }
        }
    }

    fun createSampleHtmlTest(subjectId: String, topicId: String, subtopicId: String, folderName: String, title: String) {
        viewModelScope.launch {
            val noteId = "note_${StudyRepository.newValidId()}"
            val html = LocalFileStorage.buildSampleHtmlMockTest(title)
            val fileName = fileStorage.writeLocalHtml(storageOwnerId, noteId, title, html)
            repository.addNote(firebaseUid, noteId, subjectId, topicId, subtopicId, folderName, title, NoteType.HTML, fileName, html, true)
                .onSuccess { _toastEvents.tryEmit("Sample HTML Test created!") }
        }
    }

    fun renameOrMoveNote(noteId: String, newTitle: String, newFolderName: String) {
        viewModelScope.launch {
            repository.renameOrMoveNote(firebaseUid, noteId, newTitle, newFolderName)
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
                repository.markNoteOpenedToday(firebaseUid, note.id)
                repository.addStudyLog(firebaseUid, StudyRepository.todayKey(), "STUDIED", note.title, subjectName, note.subtopicId, true, "Opened ${note.noteType}")
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
            repository.deleteNote(firebaseUid, note.id)
                .onSuccess { _toastEvents.tryEmit("Deleted '${note.title}'") }
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
                if (isFirstDownload) {
                    sharedRepo.incrementSharedDownloadCount(note.id)
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
            repository.savePdfAnnotationDoc(firebaseUid, doc.noteId, encoded, withTime.updatedAtMillis)
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
                repository.addNote(firebaseUid, newNoteId, subjId, topId, subId, "Annotated Copies", cleanTitle, NoteType.PDF, destFileName)
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

            repository.recordTestResult(
                uid = firebaseUid, subjectId = subjectId, topicId = topicId, subtopicId = subtopicId,
                noteId = noteId, testTitle = testTitle, scoreObtained = scoreObtained, totalMarks = totalMarks,
                summaryText = summaryText, totalQuestions = totalQuestions, attemptedCount = attemptedCount,
                correctCount = correctCount, wrongCount = wrongCount, unattemptedCount = unattemptedCount,
                durationSeconds = durationSeconds, testMode = testMode, breakdownJson = breakdownJson, docId = resultId,
            ).onSuccess {
                val pct = if (totalMarks > 0.0) ((scoreObtained / totalMarks) * 100.0).coerceIn(0.0, 100.0) else 0.0
                _lastReturnedScore.value = TestResult(
                    id = resultId, userId = firebaseUid, subjectId = subjectId, topicId = topicId, subtopicId = subtopicId,
                    noteId = noteId, testTitle = testTitle, scoreObtained = scoreObtained, totalMarks = totalMarks,
                    percentage = pct, totalQuestions = totalQuestions, attemptedCount = attemptedCount,
                    correctCount = correctCount, wrongCount = wrongCount, unattemptedCount = unattemptedCount,
                    durationSeconds = durationSeconds, testMode = testMode, breakdownJson = breakdownJson,
                    summaryText = summaryText, takenDate = StudyRepository.todayKey(),
                )
                repository.addStudyLog(firebaseUid, StudyRepository.todayKey(), "STUDIED", "$testTitle (${formatNum(scoreObtained)}/${formatNum(totalMarks)})", subjectName, subtopicId, true, summaryText)
                syncStudentAnalyticsSummary(extraAttempt = true)
                onSaved(resultId)
            }.onFailure { _toastEvents.tryEmit(it.message ?: "Failed to save test result") }
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
            repository.savePersonalNote(firebaseUid, withTime).onSuccess {
                if (!silent) _toastEvents.tryEmit("Personal note saved!")
            }.onFailure { _toastEvents.tryEmit(it.message ?: "Save failed") }
        }
    }

    fun deletePersonalNote(noteId: String) {
        viewModelScope.launch {
            repository.deletePersonalNote(firebaseUid, noteId).onSuccess { _toastEvents.tryEmit("Note deleted") }
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
            if (existing != null) {
                repository.deleteBookmark(firebaseUid, existing.id)
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
                repository.saveBookmark(firebaseUid, item)
                _toastEvents.tryEmit("Added to Bookmarks!")
            }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            repository.deleteBookmark(firebaseUid, bookmarkId)
        }
    }

    // Videos & MCQs
    fun addVideo(subjectId: String, topicId: String, subtopicId: String, title: String, url: String, channelOrNote: String?) {
        viewModelScope.launch {
            repository.addVideo(firebaseUid, subjectId, topicId, subtopicId, title, url, channelOrNote)
                .onSuccess { _toastEvents.tryEmit("YouTube link saved!") }
        }
    }

    fun markVideoWatched(video: VideoLink, subjectName: String) {
        viewModelScope.launch {
            if (!video.isShared) {
                repository.markVideoWatchedToday(firebaseUid, video.id)
                repository.addStudyLog(firebaseUid, StudyRepository.todayKey(), "STUDIED", video.title, subjectName, video.subtopicId, true, "Watched YouTube video")
            }
        }
    }

    fun deleteVideo(video: VideoLink) {
        viewModelScope.launch {
            if (video.isShared) {
                if (isAdminUser) sharedRepo.deleteSharedVideo(video.id)
                return@launch
            }
            repository.deleteVideo(firebaseUid, video.id)
        }
    }

    fun addMcq(subjectId: String, topicId: String, subtopicId: String, question: String, options: List<String>, correctIndex: Int, explanation: String?) {
        viewModelScope.launch {
            repository.addMcq(firebaseUid, subjectId, topicId, subtopicId, question, options, correctIndex, explanation)
                .onSuccess { _toastEvents.tryEmit("Practice MCQ added!") }
        }
    }

    fun deleteMcq(mcq: McqQuestion) {
        viewModelScope.launch {
            if (mcq.isShared) {
                if (isAdminUser) sharedRepo.deleteSharedMcq(mcq.id)
                return@launch
            }
            repository.deleteMcq(firebaseUid, mcq.id)
        }
    }

    // Study Logs & Revision Diary
    fun addStudyPlanItem(dateKey: String, entryType: String, title: String, subjectName: String?, notes: String?) {
        viewModelScope.launch {
            repository.addStudyLog(firebaseUid, dateKey, entryType, title, subjectName, completed = entryType == "STUDIED", notes = notes)
            _toastEvents.tryEmit("Study item saved!")
        }
    }

    fun toggleStudyLogCompleted(log: StudyLog) {
        viewModelScope.launch {
            repository.toggleStudyLogCompleted(firebaseUid, log.id, !log.completed)
        }
    }

    fun deleteStudyLog(logId: String) {
        viewModelScope.launch {
            repository.deleteStudyLog(firebaseUid, logId)
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
            repository.addRevisionDiaryEntry(firebaseUid, today, title, subjectName, sourceName, content, pdfTitle, pdfLocalFileName, docId)
                .onSuccess { _toastEvents.tryEmit("रिवीजन डायरी में सेव हो गया!") }
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
            repository.updateRevisionDiaryEntry(firebaseUid, entryId, title, subjectName, sourceName, content, finalPdfTitle, finalPdfFileName)
                .onSuccess { _toastEvents.tryEmit("रिवीजन डायरी अपडेट हो गई!") }
        }
    }

    fun deleteRevisionDiaryEntry(entry: RevisionDiaryEntry) {
        viewModelScope.launch {
            fileStorage.deleteLocalFile(storageOwnerId, entry.pdfLocalFileName)
            repository.deleteRevisionDiaryEntry(firebaseUid, entry.id)
        }
    }

    // WhatsApp Requests
    fun recordWhatsAppRequest(requestType: String, subject: String, topic: String, subtopic: String, extraMessage: String) {
        viewModelScope.launch {
            sharedRepo.submitWhatsAppRequest(
                WhatsAppRequestRecord(
                    userId = firebaseUid,
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
        if (!isAdminUser) return
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
        if (!isAdminUser) return
        viewModelScope.launch {
            sharedRepo.toggleSharedContentPublished(note.id, note.title, !note.published)
                .onSuccess { _toastEvents.tryEmit(if (!note.published) "Published" else "Unpublished (Draft)") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Failed") }
        }
    }

    fun adminDeleteSharedContent(note: StudyNote) {
        if (!isAdminUser) return
        viewModelScope.launch {
            cloudStorage.deleteStorageObject(note.storagePath)
            sharedRepo.deleteSharedContent(note.id)
                .onSuccess { _toastEvents.tryEmit("Deleted shared item '${note.title}'") }
                .onFailure { _toastEvents.tryEmit(it.message ?: "Delete failed") }
        }
    }

    fun adminUpsertSharedTopic(subjectId: String, name: String, description: String?) {
        if (!isAdminUser) return
        viewModelScope.launch {
            sharedRepo.upsertSharedTopic(Topic(subjectId = subjectId, name = name, description = description, isShared = true, published = true))
                .onSuccess { _toastEvents.tryEmit("Shared Topic created!") }
        }
    }

    fun adminDeleteSharedTopic(topicId: String) {
        if (!isAdminUser) return
        viewModelScope.launch { sharedRepo.deleteSharedTopic(topicId) }
    }

    fun adminUpsertSharedSubtopic(subjectId: String, topicId: String, name: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            sharedRepo.upsertSharedSubtopic(Subtopic(subjectId = subjectId, topicId = topicId, name = name, isShared = true, published = true))
                .onSuccess { _toastEvents.tryEmit("Shared Sub-topic created!") }
        }
    }

    fun adminDeleteSharedSubtopic(subtopicId: String) {
        if (!isAdminUser) return
        viewModelScope.launch { sharedRepo.deleteSharedSubtopic(subtopicId) }
    }

    fun adminUpdateWhatsAppRequestStatus(requestId: String, status: String) {
        if (!isAdminUser) return
        viewModelScope.launch {
            sharedRepo.updateWhatsAppRequestStatus(requestId, status)
                .onSuccess { _toastEvents.tryEmit("Request marked $status") }
        }
    }

    fun adminLoadStudentResults(studentUid: String, onLoaded: (List<TestResult>) -> Unit) {
        if (!isAdminUser) return
        viewModelScope.launch {
            sharedRepo.fetchStudentTestResultsForAdmin(studentUid)
                .onSuccess(onLoaded)
                .onFailure { _toastEvents.tryEmit(it.message ?: "Failed to load student results") }
        }
    }

    private fun syncStudentAnalyticsSummary(extraAttempt: Boolean = false) {
        viewModelScope.launch {
            val results = (testResultsState.value as? UiState.Success)?.data ?: emptyList()
            val count = results.size.toLong() + (if (extraAttempt && results.isEmpty()) 1L else 0L)
            val avg = if (results.isNotEmpty()) results.map { it.percentage }.average() else 0.0
            sharedRepo.upsertStudentSummary(
                uid = firebaseUid,
                displayName = initialDisplayName,
                email = signedInEmail.orEmpty(),
                photoUrl = initialPhotoUrl,
                downloadsCount = _sharedDownloadsMap.value.size.toLong(),
                testAttemptsCount = count,
                averageScorePct = avg,
            )
        }
    }

    companion object {
        private const val TAG = "MainVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun formatNum(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)
    }
}
