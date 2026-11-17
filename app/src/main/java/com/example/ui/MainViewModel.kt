package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalFileStorage
import com.example.data.local.LocalOfflineStore
import com.example.data.local.UserPreferencesManager
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyLog
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.VideoLink
import com.example.data.remote.GoogleDriveBackupManager
import com.example.data.remote.StudyRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class MainViewModel(
    private val repository: StudyRepository?,
    private val offlineStore: LocalOfflineStore,
    private val fileStorage: LocalFileStorage,
    private val prefs: UserPreferencesManager,
    private val driveBackupManager: GoogleDriveBackupManager,
    val firebaseUid: String?,
    val initialDisplayName: String,
) : ViewModel() {

    val storageOwnerId: String = firebaseUid ?: LocalOfflineStore.OFFLINE_UID
    val isOfflineSession: Boolean = firebaseUid == null

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    private val _lastReturnedScore = MutableStateFlow<TestResult?>(null)
    val lastReturnedScore: StateFlow<TestResult?> = _lastReturnedScore.asStateFlow()

    private val _isDriveBusy = MutableStateFlow(false)
    val isDriveBusy: StateFlow<Boolean> = _isDriveBusy.asStateFlow()

    val localSettings: StateFlow<LocalAppSettings> = prefs.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LocalAppSettings()
        )

    val profileState: StateFlow<UiState<UserProfile?>> =
        if (firebaseUid != null && repository != null) {
            repository.observeProfile(firebaseUid).toUiStateFlow("observeProfile")
        } else {
            offlineStore.profile.toUiStateFlow("offlineProfile")
        }

    val subjectsState: StateFlow<UiState<List<Subject>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeSubjects(firebaseUid).toUiStateFlow("observeSubjects")
        } else {
            offlineStore.subjects.toUiStateFlow("offlineSubjects")
        }

    val topicsState: StateFlow<UiState<List<Topic>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeTopics(firebaseUid).toUiStateFlow("observeTopics")
        } else {
            offlineStore.topics.toUiStateFlow("offlineTopics")
        }

    val subtopicsState: StateFlow<UiState<List<Subtopic>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeSubtopics(firebaseUid).toUiStateFlow("observeSubtopics")
        } else {
            offlineStore.subtopics.toUiStateFlow("offlineSubtopics")
        }

    val notesState: StateFlow<UiState<List<StudyNote>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeNotes(firebaseUid).toUiStateFlow("observeNotes")
        } else {
            offlineStore.notes.toUiStateFlow("offlineNotes")
        }

    val videosState: StateFlow<UiState<List<VideoLink>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeVideos(firebaseUid).toUiStateFlow("observeVideos")
        } else {
            offlineStore.videos.toUiStateFlow("offlineVideos")
        }

    val mcqsState: StateFlow<UiState<List<McqQuestion>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeMcqs(firebaseUid).toUiStateFlow("observeMcqs")
        } else {
            offlineStore.mcqs.toUiStateFlow("offlineMcqs")
        }

    val testResultsState: StateFlow<UiState<List<TestResult>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeTestResults(firebaseUid).toUiStateFlow("observeTestResults")
        } else {
            offlineStore.testResults.toUiStateFlow("offlineTestResults")
        }

    val studyLogsState: StateFlow<UiState<List<StudyLog>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeStudyLogs(firebaseUid).toUiStateFlow("observeStudyLogs")
        } else {
            offlineStore.studyLogs.toUiStateFlow("offlineStudyLogs")
        }

    val revisionDiaryState: StateFlow<UiState<List<RevisionDiaryEntry>>> =
        if (firebaseUid != null && repository != null) {
            repository.observeRevisionDiary(firebaseUid).toUiStateFlow("observeRevisionDiary")
        } else {
            offlineStore.revisionDiary.toUiStateFlow("offlineRevisionDiary")
        }

    init {
        val uid = firebaseUid
        val repo = repository
        if (uid != null && repo != null) {
            viewModelScope.launch {
                repo.ensureUserProfile(uid, initialDisplayName)
                fileStorage.copyOfflineFilesToUser(uid)
                val offlineSnap = offlineStore.currentSnapshot()
                if (offlineSnap.hasUserEdits) {
                    repo.syncOfflineSnapshotToCloud(uid, offlineSnap)
                        .onSuccess {
                            offlineStore.markSyncedToCloud()
                            prefs.markSeeded(uid)
                            _toastEvents.tryEmit("ऑफ़लाइन डेटा क्लाउड पर सिंक हो गया!")
                        }
                } else {
                    val alreadySeeded = prefs.isSeededFlow(uid).first()
                    if (!alreadySeeded) {
                        repo.seedDefaultSubjectsIfEmpty(uid)
                            .onSuccess { prefs.markSeeded(uid) }
                    }
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
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    fun showToast(message: String) {
        _toastEvents.tryEmit(message)
    }

    fun clearLastReturnedScore() {
        _lastReturnedScore.value = null
    }

    fun selectTheme(themeId: String) {
        viewModelScope.launch {
            prefs.setTheme(themeId)
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.updateTheme(uid, themeId)
            } else {
                offlineStore.updateTheme(themeId)
            }
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBiometricLockEnabled(enabled)
            _toastEvents.tryEmit(
                if (enabled) "Biometric Lock enabled" else "Biometric Lock disabled"
            )
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setCloudSyncEnabled(enabled)
        }
    }

    fun saveAdminWhatsAppNumber(number: String) {
        viewModelScope.launch {
            prefs.setAdminWhatsAppNumber(number)
            _toastEvents.tryEmit("Admin WhatsApp number saved!")
        }
    }

    fun saveCustomLogo(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val fileName = fileStorage.saveCustomLogoFromUri(uri)
                prefs.setCustomLogoFileName(fileName)
            }.onSuccess {
                _toastEvents.tryEmit("Custom IMG Logo updated!")
            }.onFailure {
                _toastEvents.tryEmit(it.message ?: "Could not save logo image")
            }
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
                repository.updateExamGoal(uid, examName, examDateMillis)
                    .onSuccess {
                        if (autoPlan && subjects.isNotEmpty()) {
                            repository.generateExamRevisionPlan(uid, subjects, examDateMillis)
                                .onSuccess { days -> _toastEvents.tryEmit("Exam goal saved & $days-day plan created!") }
                                .onFailure { _toastEvents.tryEmit("Exam goal saved.") }
                        } else {
                            _toastEvents.tryEmit("Exam goal updated!")
                        }
                    }
                    .onFailure { e -> _toastEvents.tryEmit(e.message ?: "Failed to save exam goal") }
            } else {
                offlineStore.updateExamGoal(examName, examDateMillis)
                if (autoPlan && subjects.isNotEmpty()) {
                    val days = offlineStore.generateExamRevisionPlan(subjects, examDateMillis)
                    _toastEvents.tryEmit("Exam goal saved & $days-day plan created!")
                } else {
                    _toastEvents.tryEmit("Exam goal updated!")
                }
            }
        }
    }

    fun addSubject(nameEn: String, nameHi: String?, iconKey: String, colorHex: String, sortOrder: Long) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addSubject(uid, nameEn, nameHi, iconKey, colorHex, sortOrder)
                    .onSuccess { _toastEvents.tryEmit("Subject added") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add subject") }
            } else {
                offlineStore.addSubject(nameEn, nameHi, iconKey, colorHex, sortOrder)
                _toastEvents.tryEmit("Subject added")
            }
        }
    }

    fun addTopic(subjectId: String, name: String, description: String?) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addTopic(uid, subjectId, name, description)
                    .onSuccess { _toastEvents.tryEmit("Topic added") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add topic") }
            } else {
                offlineStore.addTopic(subjectId, name, description)
                _toastEvents.tryEmit("Topic added")
            }
        }
    }

    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deleteTopic(uid, topicId)
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not delete topic") }
            } else {
                offlineStore.deleteTopic(topicId)
            }
        }
    }

    fun addSubtopic(subjectId: String, topicId: String, name: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addSubtopic(uid, subjectId, topicId, name)
                    .onSuccess { _toastEvents.tryEmit("Sub-topic added") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add sub-topic") }
            } else {
                offlineStore.addSubtopic(subjectId, topicId, name)
                _toastEvents.tryEmit("Sub-topic added")
            }
        }
    }

    fun deleteSubtopic(subtopicId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deleteSubtopic(uid, subtopicId)
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not delete sub-topic") }
            } else {
                offlineStore.deleteSubtopic(subtopicId)
            }
        }
    }

    fun markSubtopicStudiedToday(subtopic: Subtopic, subjectName: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.markSubtopicStudiedToday(uid, subtopic, subjectName)
                    .onSuccess { _toastEvents.tryEmit("आज के अध्ययन में जोड़ा गया!") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Update failed") }
            } else {
                offlineStore.markSubtopicStudiedToday(subtopic, subjectName)
                _toastEvents.tryEmit("आज के अध्ययन में जोड़ा गया!")
            }
        }
    }

    fun createFolder(subjectId: String, topicId: String, subtopicId: String, folderName: String) {
        viewModelScope.launch {
            val noteId = "note_${StudyRepository.newValidId()}"
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addNote(
                    uid = uid,
                    noteId = noteId,
                    subjectId = subjectId,
                    topicId = topicId,
                    subtopicId = subtopicId,
                    folderName = folderName,
                    title = folderName,
                    noteType = NoteType.FOLDER,
                ).onSuccess {
                    _toastEvents.tryEmit("Folder '$folderName' created")
                }.onFailure {
                    _toastEvents.tryEmit(it.message ?: "Failed to create folder")
                }
            } else {
                offlineStore.addNote(
                    noteId = noteId,
                    subjectId = subjectId,
                    topicId = topicId,
                    subtopicId = subtopicId,
                    folderName = folderName,
                    title = folderName,
                    noteType = NoteType.FOLDER,
                )
                _toastEvents.tryEmit("Folder '$folderName' created")
            }
        }
    }

    fun importFileNote(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        folderName: String,
        customTitle: String,
        noteType: NoteType,
        uri: Uri,
    ) {
        viewModelScope.launch {
            val noteId = "note_${StudyRepository.newValidId()}"
            val ext = when (noteType) {
                NoteType.PDF -> "pdf"
                NoteType.HTML -> "html"
                NoteType.IMAGE -> "jpg"
                NoteType.FOLDER -> "txt"
            }
            try {
                val (fileName, file) = fileStorage.copyUriToLocal(storageOwnerId, noteId, uri, ext)
                val htmlText = if (noteType == NoteType.HTML && file.length() <= 190_000L) {
                    file.readText(Charsets.UTF_8)
                } else null
                val finalTitle = customTitle.ifBlank { fileName }
                val uid = firebaseUid
                if (uid != null && repository != null) {
                    repository.addNote(
                        uid = uid,
                        noteId = noteId,
                        subjectId = subjectId,
                        topicId = topicId,
                        subtopicId = subtopicId,
                        folderName = folderName,
                        title = finalTitle,
                        noteType = noteType,
                        localFileName = fileName,
                        htmlContent = htmlText,
                    ).onSuccess {
                        _toastEvents.tryEmit("Saved '$finalTitle'")
                    }.onFailure {
                        _toastEvents.tryEmit(it.message ?: "Cloud save failed")
                    }
                } else {
                    offlineStore.addNote(
                        noteId = noteId,
                        subjectId = subjectId,
                        topicId = topicId,
                        subtopicId = subtopicId,
                        folderName = folderName,
                        title = finalTitle,
                        noteType = noteType,
                        localFileName = fileName,
                        htmlContent = htmlText,
                    )
                    _toastEvents.tryEmit("Saved '$finalTitle' offline")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _toastEvents.tryEmit(e.message ?: "Failed to import file")
            }
        }
    }

    fun createSampleHtmlTest(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        folderName: String,
        title: String,
    ) {
        viewModelScope.launch {
            val noteId = "note_${StudyRepository.newValidId()}"
            val html = LocalFileStorage.buildSampleHtmlMockTest(title)
            val fileName = fileStorage.writeLocalHtml(storageOwnerId, noteId, title, html)
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addNote(
                    uid = uid,
                    noteId = noteId,
                    subjectId = subjectId,
                    topicId = topicId,
                    subtopicId = subtopicId,
                    folderName = folderName,
                    title = title,
                    noteType = NoteType.HTML,
                    localFileName = fileName,
                    htmlContent = html,
                ).onSuccess {
                    _toastEvents.tryEmit("Sample HTML Test created!")
                }.onFailure {
                    _toastEvents.tryEmit(it.message ?: "Failed to save test")
                }
            } else {
                offlineStore.addNote(
                    noteId = noteId,
                    subjectId = subjectId,
                    topicId = topicId,
                    subtopicId = subtopicId,
                    folderName = folderName,
                    title = title,
                    noteType = NoteType.HTML,
                    localFileName = fileName,
                    htmlContent = html,
                )
                _toastEvents.tryEmit("Sample HTML Test created!")
            }
        }
    }

    fun renameOrMoveNote(noteId: String, newTitle: String, newFolderName: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.renameOrMoveNote(uid, noteId, newTitle, newFolderName)
                    .onSuccess { _toastEvents.tryEmit("Updated note") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Update failed") }
            } else {
                offlineStore.renameOrMoveNote(noteId, newTitle, newFolderName)
                _toastEvents.tryEmit("Updated note")
            }
        }
    }

    fun markNoteOpenedToday(note: StudyNote, subjectName: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.markNoteOpenedToday(uid, note.id)
                repository.addStudyLog(
                    uid = uid,
                    dateKey = StudyRepository.todayKey(),
                    entryType = "STUDIED",
                    title = note.title,
                    subjectName = subjectName,
                    subtopicId = note.subtopicId,
                    completed = true,
                    notes = "Opened ${note.noteType}"
                )
            } else {
                offlineStore.markNoteOpenedToday(note, subjectName)
            }
        }
    }

    fun deleteNote(note: StudyNote) {
        viewModelScope.launch {
            fileStorage.deleteLocalFile(storageOwnerId, note.localFileName)
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deleteNote(uid, note.id)
                    .onSuccess { _toastEvents.tryEmit("Deleted '${note.title}'") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Delete failed") }
            } else {
                offlineStore.deleteNote(note.id)
                _toastEvents.tryEmit("Deleted '${note.title}'")
            }
        }
    }

    fun getLocalFile(localFileName: String?): File? =
        fileStorage.getLocalFile(storageOwnerId, localFileName)

    suspend fun loadHtmlContent(note: StudyNote): String? =
        fileStorage.readHtmlFromFileOrWrite(storageOwnerId, note.localFileName, note.htmlContent)

    fun addVideo(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        title: String,
        url: String,
        channelOrNote: String?,
    ) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addVideo(uid, subjectId, topicId, subtopicId, title, url, channelOrNote)
                    .onSuccess { _toastEvents.tryEmit("YouTube link saved!") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Failed to save link") }
            } else {
                offlineStore.addVideo(subjectId, topicId, subtopicId, title, url, channelOrNote)
                _toastEvents.tryEmit("YouTube link saved!")
            }
        }
    }

    fun markVideoWatched(video: VideoLink, subjectName: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.markVideoWatchedToday(uid, video.id)
                repository.addStudyLog(
                    uid = uid,
                    dateKey = StudyRepository.todayKey(),
                    entryType = "STUDIED",
                    title = video.title,
                    subjectName = subjectName,
                    subtopicId = video.subtopicId,
                    completed = true,
                    notes = "Watched YouTube video"
                )
            } else {
                offlineStore.markVideoWatchedToday(video, subjectName)
            }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deleteVideo(uid, videoId)
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Delete failed") }
            } else {
                offlineStore.deleteVideo(videoId)
            }
        }
    }

    fun addMcq(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        question: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String?,
    ) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addMcq(uid, subjectId, topicId, subtopicId, question, options, correctIndex, explanation)
                    .onSuccess { _toastEvents.tryEmit("Practice MCQ added!") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not add MCQ") }
            } else {
                offlineStore.addMcq(subjectId, topicId, subtopicId, question, options, correctIndex, explanation)
                _toastEvents.tryEmit("Practice MCQ added!")
            }
        }
    }

    fun deleteMcq(mcqId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deleteMcq(uid, mcqId)
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Delete failed") }
            } else {
                offlineStore.deleteMcq(mcqId)
            }
        }
    }

    fun saveTestScore(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        noteId: String?,
        testTitle: String,
        scoreObtained: Double,
        totalMarks: Double,
        summaryText: String?,
        subjectName: String,
    ) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.recordTestResult(
                    uid = uid,
                    subjectId = subjectId,
                    topicId = topicId,
                    subtopicId = subtopicId,
                    noteId = noteId,
                    testTitle = testTitle,
                    scoreObtained = scoreObtained,
                    totalMarks = totalMarks,
                    summaryText = summaryText,
                ).onSuccess { resultId ->
                    val pct = if (totalMarks > 0.0) ((scoreObtained / totalMarks) * 100.0).coerceIn(0.0, 100.0) else 0.0
                    _lastReturnedScore.value = TestResult(
                        id = resultId,
                        userId = uid,
                        subjectId = subjectId,
                        topicId = topicId,
                        subtopicId = subtopicId,
                        noteId = noteId,
                        testTitle = testTitle,
                        scoreObtained = scoreObtained,
                        totalMarks = totalMarks,
                        percentage = pct,
                        summaryText = summaryText,
                        takenDate = StudyRepository.todayKey(),
                    )
                    repository.addStudyLog(
                        uid = uid,
                        dateKey = StudyRepository.todayKey(),
                        entryType = "STUDIED",
                        title = "$testTitle (${formatNum(scoreObtained)}/${formatNum(totalMarks)})",
                        subjectName = subjectName,
                        subtopicId = subtopicId,
                        completed = true,
                        notes = summaryText
                    )
                    _toastEvents.tryEmit("Score saved: ${formatNum(scoreObtained)} / ${formatNum(totalMarks)}")
                }.onFailure {
                    _toastEvents.tryEmit(it.message ?: "Failed to save score")
                }
            } else {
                val res = offlineStore.recordTestResult(
                    subjectId = subjectId,
                    topicId = topicId,
                    subtopicId = subtopicId,
                    noteId = noteId,
                    testTitle = testTitle,
                    scoreObtained = scoreObtained,
                    totalMarks = totalMarks,
                    summaryText = summaryText,
                    subjectName = subjectName,
                )
                _lastReturnedScore.value = res
                _toastEvents.tryEmit("Score saved: ${formatNum(scoreObtained)} / ${formatNum(totalMarks)}")
            }
        }
    }

    fun addStudyPlanItem(
        dateKey: String,
        entryType: String,
        title: String,
        subjectName: String?,
        notes: String?,
    ) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addStudyLog(
                    uid = uid,
                    dateKey = dateKey,
                    entryType = entryType,
                    title = title,
                    subjectName = subjectName,
                    completed = entryType == "STUDIED",
                    notes = notes,
                ).onSuccess {
                    _toastEvents.tryEmit("Study item saved!")
                }.onFailure {
                    _toastEvents.tryEmit(it.message ?: "Failed to save study item")
                }
            } else {
                offlineStore.addStudyLog(dateKey, entryType, title, subjectName, notes)
                _toastEvents.tryEmit("Study item saved!")
            }
        }
    }

    fun toggleStudyLogCompleted(log: StudyLog) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.toggleStudyLogCompleted(uid, log.id, !log.completed)
            } else {
                offlineStore.toggleStudyLogCompleted(log.id, !log.completed)
            }
        }
    }

    fun deleteStudyLog(logId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deleteStudyLog(uid, logId)
            } else {
                offlineStore.deleteStudyLog(logId)
            }
        }
    }

    fun addRevisionDiaryEntry(
        title: String,
        subjectName: String?,
        sourceName: String,
        content: String,
    ) {
        viewModelScope.launch {
            val today = StudyRepository.todayKey()
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.addRevisionDiaryEntry(uid, today, title, subjectName, sourceName, content)
                    .onSuccess { _toastEvents.tryEmit("रिवीजन डायरी में सेव हो गया!") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Could not save diary") }
            } else {
                offlineStore.addRevisionDiaryEntry(today, title, subjectName, sourceName, content)
                _toastEvents.tryEmit("रिवीजन डायरी में सेव हो गया!")
            }
        }
    }

    fun updateRevisionDiaryEntry(
        entryId: String,
        title: String,
        subjectName: String?,
        sourceName: String,
        content: String,
    ) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.updateRevisionDiaryEntry(uid, entryId, title, subjectName, sourceName, content)
                    .onSuccess { _toastEvents.tryEmit("रिवीजन डायरी अपडेट हो गई!") }
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Update failed") }
            } else {
                offlineStore.updateRevisionDiaryEntry(entryId, title, subjectName, sourceName, content)
                _toastEvents.tryEmit("रिवीजन डायरी अपडेट हो गई!")
            }
        }
    }

    fun deleteRevisionDiaryEntry(entryId: String) {
        viewModelScope.launch {
            val uid = firebaseUid
            if (uid != null && repository != null) {
                repository.deleteRevisionDiaryEntry(uid, entryId)
                    .onFailure { _toastEvents.tryEmit(it.message ?: "Delete failed") }
            } else {
                offlineStore.deleteRevisionDiaryEntry(entryId)
            }
        }
    }

    fun buildBackupJson(): String {
        return offlineStore.exportSnapshotJson(
            subjectsList = (subjectsState.value as? UiState.Success)?.data ?: emptyList(),
            topicsList = (topicsState.value as? UiState.Success)?.data ?: emptyList(),
            subtopicsList = (subtopicsState.value as? UiState.Success)?.data ?: emptyList(),
            notesList = (notesState.value as? UiState.Success)?.data ?: emptyList(),
            videosList = (videosState.value as? UiState.Success)?.data ?: emptyList(),
            mcqsList = (mcqsState.value as? UiState.Success)?.data ?: emptyList(),
            resultsList = (testResultsState.value as? UiState.Success)?.data ?: emptyList(),
            logsList = (studyLogsState.value as? UiState.Success)?.data ?: emptyList(),
            diaryList = (revisionDiaryState.value as? UiState.Success)?.data ?: emptyList(),
            userProfile = (profileState.value as? UiState.Success)?.data,
        )
    }

    fun restoreFromBackupJson(rawJson: String, attachedFiles: Map<String, ByteArray> = emptyMap()) {
        viewModelScope.launch {
            try {
                attachedFiles.forEach { (name, bytes) ->
                    fileStorage.writeLocalBytes(storageOwnerId, name, bytes)
                }
                val snap = offlineStore.importSnapshotJson(rawJson)
                val uid = firebaseUid
                if (uid != null && repository != null) {
                    repository.syncOfflineSnapshotToCloud(uid, snap)
                        .onSuccess { offlineStore.markSyncedToCloud() }
                }
                _toastEvents.tryEmit("बैकअप सफलतापूर्वक रिस्टोर हो गया!")
            } catch (e: Exception) {
                _toastEvents.tryEmit(e.message ?: "Restore failed")
            }
        }
    }

    fun runDriveBackupWithToken(accessToken: String) {
        viewModelScope.launch {
            _isDriveBusy.value = true
            try {
                val json = buildBackupJson()
                val files = fileStorage.listAllUserFiles(storageOwnerId)
                driveBackupManager.backupToLearnTalentFolder(accessToken, json, files)
                    .onSuccess { summary ->
                        prefs.setLastDriveBackupMillis(System.currentTimeMillis())
                        _toastEvents.tryEmit("Google Drive 'Learn Talent' फोल्डर में ${summary.uploadedFilesCount} फाइलें सेव हुईं!")
                    }
                    .onFailure { e ->
                        _toastEvents.tryEmit(e.message ?: "Google Drive backup failed")
                    }
            } finally {
                _isDriveBusy.value = false
            }
        }
    }

    fun runDriveRestoreWithToken(accessToken: String) {
        viewModelScope.launch {
            _isDriveBusy.value = true
            try {
                driveBackupManager.restoreFromLearnTalentFolder(accessToken)
                    .onSuccess { restored ->
                        restoreFromBackupJson(restored.snapshotJson, restored.restoredBinaryFiles)
                    }
                    .onFailure { e ->
                        _toastEvents.tryEmit(e.message ?: "Google Drive restore failed")
                    }
            } finally {
                _isDriveBusy.value = false
            }
        }
    }

    companion object {
        private const val TAG = "MainVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun formatNum(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)
    }
}
