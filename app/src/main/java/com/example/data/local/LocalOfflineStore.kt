package com.example.data.local

import android.content.Context
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.CloudPdfAnnotationDoc
import com.example.data.model.DefaultSubjectsSeed
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.PersonalNote
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyLog
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.UserProfile
import com.example.data.model.VideoLink
import com.example.data.remote.StudyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LocalOfflineStore(
    private val context: Context,
    val ownerId: String = OFFLINE_UID,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val storeFile = if (ownerId == OFFLINE_UID) {
        File(context.filesDir, "learn_talent_offline_vault.json")
    } else {
        val safeOwner = ownerId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        File(context.filesDir, "learn_talent_vault_$safeOwner.json")
    }

    private var snapshot: OfflineVaultSnapshot = loadOrSeedSnapshot()

    private val _profile = MutableStateFlow<UserProfile?>(snapshot.profile.toModel())
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _subjects = MutableStateFlow(snapshot.subjects.map { it.toModel(ownerId) }.sortedBy { it.sortOrder })
    val subjects: StateFlow<List<Subject>> = _subjects.asStateFlow()

    private val _topics = MutableStateFlow(snapshot.topics.map { it.toModel(ownerId) }.sortedBy { it.name.lowercase() })
    val topics: StateFlow<List<Topic>> = _topics.asStateFlow()

    private val _subtopics = MutableStateFlow(snapshot.subtopics.map { it.toModel(ownerId) }.sortedBy { it.name.lowercase() })
    val subtopics: StateFlow<List<Subtopic>> = _subtopics.asStateFlow()

    private val _notes = MutableStateFlow(snapshot.notes.map { it.toModel(ownerId) }.sortedByDescending { it.updatedAt?.seconds ?: 0L })
    val notes: StateFlow<List<StudyNote>> = _notes.asStateFlow()

    private val _videos = MutableStateFlow(snapshot.videos.map { it.toModel(ownerId) }.sortedByDescending { it.createdAt?.seconds ?: 0L })
    val videos: StateFlow<List<VideoLink>> = _videos.asStateFlow()

    private val _mcqs = MutableStateFlow(snapshot.mcqs.map { it.toModel(ownerId) }.sortedBy { it.createdAt?.seconds ?: 0L })
    val mcqs: StateFlow<List<McqQuestion>> = _mcqs.asStateFlow()

    private val _testResults = MutableStateFlow(snapshot.testResults.map { it.toModel(ownerId) }.sortedByDescending { it.createdAt?.seconds ?: 0L })
    val testResults: StateFlow<List<TestResult>> = _testResults.asStateFlow()

    private val _studyLogs = MutableStateFlow(snapshot.studyLogs.map { it.toModel(ownerId) }.sortedByDescending { it.dateKey })
    val studyLogs: StateFlow<List<StudyLog>> = _studyLogs.asStateFlow()

    private val _revisionDiary = MutableStateFlow(snapshot.revisionDiary.map { it.toModel(ownerId) }.sortedByDescending { it.updatedAt?.seconds ?: 0L })
    val revisionDiary: StateFlow<List<RevisionDiaryEntry>> = _revisionDiary.asStateFlow()

    private val _personalNotes = MutableStateFlow(snapshot.personalNotes.map { it.toModel(ownerId) }.sortedByDescending { it.updatedAtMillis })
    val personalNotes: StateFlow<List<PersonalNote>> = _personalNotes.asStateFlow()

    private val _bookmarks = MutableStateFlow(snapshot.bookmarks.map { it.toModel(ownerId) }.sortedByDescending { it.createdAtMillis })
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks.asStateFlow()

    private val _pdfAnnotations = MutableStateFlow(snapshot.pdfAnnotations.map { it.toModel(ownerId) })
    val pdfAnnotations: StateFlow<List<CloudPdfAnnotationDoc>> = _pdfAnnotations.asStateFlow()

    fun currentSnapshot(): OfflineVaultSnapshot = snapshot

    fun exportSnapshotJson(
        subjectsList: List<Subject>,
        topicsList: List<Topic>,
        subtopicsList: List<Subtopic>,
        notesList: List<StudyNote>,
        videosList: List<VideoLink>,
        mcqsList: List<McqQuestion>,
        resultsList: List<TestResult>,
        logsList: List<StudyLog>,
        diaryList: List<RevisionDiaryEntry>,
        userProfile: UserProfile?,
        personalNotesList: List<PersonalNote> = emptyList(),
        bookmarksList: List<BookmarkItem> = emptyList(),
        pdfAnnotationsList: List<CloudPdfAnnotationDoc> = emptyList(),
    ): String {
        val snap = OfflineVaultSnapshot(
            profile = OfflineUserProfileDto(
                id = userProfile?.id ?: OFFLINE_UID,
                displayName = userProfile?.displayName ?: "Learner",
                email = userProfile?.email,
                photoUrl = userProfile?.photoUrl,
                role = userProfile?.role ?: "student",
                examName = userProfile?.examName,
                examDateMillis = userProfile?.examDateMillis,
                themeId = userProfile?.themeId ?: "bunts_royal_light",
            ),
            subjects = subjectsList.map { OfflineSubjectDto(it.id, it.nameEn, it.nameHi, it.iconKey, it.colorHex, it.sortOrder) },
            topics = topicsList.map { OfflineTopicDto(it.id, it.subjectId, it.name, it.description) },
            subtopics = subtopicsList.map { OfflineSubtopicDto(it.id, it.subjectId, it.topicId, it.name, it.lastStudiedDate) },
            notes = notesList.map {
                OfflineNoteDto(
                    id = it.id, subjectId = it.subjectId, topicId = it.topicId, subtopicId = it.subtopicId,
                    folderName = it.folderName, title = it.title, noteType = it.noteType,
                    localFileName = it.localFileName, htmlContent = it.htmlContent,
                    lastOpenedDate = it.lastOpenedDate, isMockTest = it.isMockTest,
                    updatedAtSeconds = it.updatedAt?.seconds ?: 0L,
                )
            },
            videos = videosList.map {
                OfflineVideoDto(
                    id = it.id, subjectId = it.subjectId, topicId = it.topicId, subtopicId = it.subtopicId,
                    title = it.title, youtubeUrl = it.youtubeUrl, channelOrNote = it.channelOrNote,
                    lastWatchedDate = it.lastWatchedDate, createdAtSeconds = it.createdAt?.seconds ?: 0L,
                )
            },
            mcqs = mcqsList.map {
                OfflineMcqDto(
                    id = it.id, subjectId = it.subjectId, topicId = it.topicId, subtopicId = it.subtopicId,
                    question = it.question, options = it.options, correctIndex = it.correctIndex,
                    explanation = it.explanation, createdAtSeconds = it.createdAt?.seconds ?: 0L,
                )
            },
            testResults = resultsList.map {
                OfflineTestResultDto(
                    id = it.id, subjectId = it.subjectId, topicId = it.topicId, subtopicId = it.subtopicId,
                    noteId = it.noteId, testTitle = it.testTitle, scoreObtained = it.scoreObtained,
                    totalMarks = it.totalMarks, percentage = it.percentage,
                    totalQuestions = it.totalQuestions, attemptedCount = it.attemptedCount,
                    correctCount = it.correctCount, wrongCount = it.wrongCount,
                    unattemptedCount = it.unattemptedCount, durationSeconds = it.durationSeconds,
                    testMode = it.testMode, breakdownJson = it.breakdownJson,
                    summaryText = it.summaryText, takenDate = it.takenDate,
                    createdAtSeconds = it.createdAt?.seconds ?: 0L,
                )
            },
            studyLogs = logsList.map {
                OfflineStudyLogDto(
                    id = it.id, dateKey = it.dateKey, entryType = it.entryType, title = it.title,
                    subjectName = it.subjectName, subtopicId = it.subtopicId, completed = it.completed, notes = it.notes,
                )
            },
            revisionDiary = diaryList.map {
                OfflineRevisionDiaryDto(
                    id = it.id, dateKey = it.dateKey, title = it.title, subjectName = it.subjectName,
                    sourceName = it.sourceName, content = it.content, pdfTitle = it.pdfTitle,
                    pdfLocalFileName = it.pdfLocalFileName, updatedAtSeconds = it.updatedAt?.seconds ?: 0L,
                )
            },
            personalNotes = personalNotesList.map {
                OfflinePersonalNoteDto(
                    id = it.id, subjectId = it.subjectId, subjectName = it.subjectName,
                    topicName = it.topicName, title = it.title, contentSummary = it.contentSummary,
                    blocksJson = it.blocksJson, updatedAtMillis = it.updatedAtMillis,
                )
            },
            bookmarks = bookmarksList.map {
                OfflineBookmarkDto(
                    id = it.id, bookmarkType = it.bookmarkType, title = it.title, subtitle = it.subtitle,
                    refId = it.refId, subjectId = it.subjectId, topicId = it.topicId, subtopicId = it.subtopicId,
                    pageIndex = it.pageIndex, questionJson = it.questionJson, createdAtMillis = it.createdAtMillis,
                )
            },
            pdfAnnotations = pdfAnnotationsList.map {
                OfflinePdfAnnotationDto(it.noteId, it.annotationsJson, it.updatedAtMillis)
            },
            hasUserEdits = true,
        )
        return json.encodeToString(snap)
    }

    suspend fun importSnapshotJson(rawJson: String): OfflineVaultSnapshot = update {
        mergeMissingDefaultSubjects(json.decodeFromString<OfflineVaultSnapshot>(rawJson)).copy(hasUserEdits = true)
    }

    suspend fun markSyncedToCloud() { update { it.copy(hasUserEdits = false) } }

    suspend fun updateProfileMeta(displayName: String, email: String?, photoUrl: String?, role: String) {
        update {
            it.copy(
                profile = it.profile.copy(
                    displayName = displayName.ifBlank { it.profile.displayName },
                    email = email ?: it.profile.email,
                    photoUrl = photoUrl ?: it.profile.photoUrl,
                    role = role,
                )
            )
        }
    }

    suspend fun updateTheme(themeId: String) {
        update { it.copy(profile = it.profile.copy(themeId = themeId)) }
    }

    suspend fun updateExamGoal(examName: String, examDateMillis: Long) {
        update {
            it.copy(
                profile = it.profile.copy(
                    examName = examName.trim().ifBlank { "Target Exam" }.take(100),
                    examDateMillis = examDateMillis.coerceAtLeast(0L)
                ),
                hasUserEdits = true
            )
        }
    }

    suspend fun addSubject(nameEn: String, nameHi: String?, iconKey: String, colorHex: String, sortOrder: Long) {
        val dto = OfflineSubjectDto(
            id = "subj_${StudyRepository.newValidId()}",
            nameEn = nameEn.trim().take(80),
            nameHi = nameHi?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
            iconKey = iconKey.take(40),
            colorHex = colorHex.take(20),
            sortOrder = sortOrder.coerceIn(0L, 1000L),
        )
        update { it.copy(subjects = it.subjects + dto, hasUserEdits = true) }
    }

    suspend fun addTopic(subjectId: String, name: String, description: String?) {
        val dto = OfflineTopicDto(
            id = "topic_${StudyRepository.newValidId()}",
            subjectId = subjectId,
            name = name.trim().take(120),
            description = description?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
        )
        update { it.copy(topics = it.topics + dto, hasUserEdits = true) }
    }

    suspend fun deleteTopic(topicId: String) {
        update { it.copy(topics = it.topics.filterNot { t -> t.id == topicId }, hasUserEdits = true) }
    }

    suspend fun addSubtopic(subjectId: String, topicId: String, name: String) {
        val dto = OfflineSubtopicDto(
            id = "sub_${StudyRepository.newValidId()}",
            subjectId = subjectId,
            topicId = topicId,
            name = name.trim().take(120),
        )
        update { it.copy(subtopics = it.subtopics + dto, hasUserEdits = true) }
    }

    suspend fun deleteSubtopic(subtopicId: String) {
        update { it.copy(subtopics = it.subtopics.filterNot { s -> s.id == subtopicId }, hasUserEdits = true) }
    }

    suspend fun markSubtopicStudiedToday(subtopic: Subtopic, subjectName: String) {
        val today = StudyRepository.todayKey()
        val logDto = OfflineStudyLogDto(
            id = "log_${StudyRepository.newValidId()}", dateKey = today, entryType = "STUDIED",
            title = subtopic.name, subjectName = subjectName, subtopicId = subtopic.id,
            completed = true, notes = "Marked as studied"
        )
        update { snap ->
            snap.copy(
                subtopics = snap.subtopics.map { s -> if (s.id == subtopic.id) s.copy(lastStudiedDate = today) else s },
                studyLogs = snap.studyLogs + logDto,
                hasUserEdits = true
            )
        }
    }

    suspend fun addNote(
        noteId: String,
        subjectId: String,
        topicId: String,
        subtopicId: String,
        folderName: String,
        title: String,
        noteType: NoteType,
        localFileName: String? = null,
        htmlContent: String? = null,
        isMockTest: Boolean = false,
    ) {
        val dto = OfflineNoteDto(
            id = noteId, subjectId = subjectId, topicId = topicId, subtopicId = subtopicId,
            folderName = folderName.trim().take(80), title = title.trim().take(150),
            noteType = noteType.name, localFileName = localFileName, htmlContent = htmlContent,
            isMockTest = isMockTest, updatedAtSeconds = nowSeconds(),
        )
        update { it.copy(notes = it.notes.filterNot { n -> n.id == noteId } + dto, hasUserEdits = true) }
    }

    suspend fun renameOrMoveNote(noteId: String, newTitle: String, newFolderName: String) {
        update { snap ->
            snap.copy(
                notes = snap.notes.map { n ->
                    if (n.id == noteId) {
                        n.copy(title = newTitle.trim().take(150), folderName = newFolderName.trim().take(80), updatedAtSeconds = nowSeconds())
                    } else n
                },
                hasUserEdits = true
            )
        }
    }

    suspend fun markNoteOpenedToday(note: StudyNote, subjectName: String) {
        val today = StudyRepository.todayKey()
        val logDto = OfflineStudyLogDto(
            id = "log_${StudyRepository.newValidId()}", dateKey = today, entryType = "STUDIED",
            title = note.title, subjectName = subjectName, subtopicId = note.subtopicId,
            completed = true, notes = "Opened ${note.noteType}"
        )
        update { snap ->
            snap.copy(
                notes = snap.notes.map { n -> if (n.id == note.id) n.copy(lastOpenedDate = today, updatedAtSeconds = nowSeconds()) else n },
                studyLogs = snap.studyLogs + logDto,
                hasUserEdits = true
            )
        }
    }

    suspend fun deleteNote(noteId: String) {
        update { it.copy(notes = it.notes.filterNot { n -> n.id == noteId }, hasUserEdits = true) }
    }

    suspend fun addVideo(subjectId: String, topicId: String, subtopicId: String, title: String, youtubeUrl: String, channelOrNote: String?) {
        val dto = OfflineVideoDto(
            id = "vid_${StudyRepository.newValidId()}", subjectId = subjectId, topicId = topicId, subtopicId = subtopicId,
            title = title.trim().take(150), youtubeUrl = youtubeUrl.trim().take(500),
            channelOrNote = channelOrNote?.trim()?.takeIf { it.isNotEmpty() }?.take(150),
            createdAtSeconds = nowSeconds(),
        )
        update { it.copy(videos = it.videos + dto, hasUserEdits = true) }
    }

    suspend fun markVideoWatchedToday(video: VideoLink, subjectName: String) {
        val today = StudyRepository.todayKey()
        val logDto = OfflineStudyLogDto(
            id = "log_${StudyRepository.newValidId()}", dateKey = today, entryType = "STUDIED",
            title = video.title, subjectName = subjectName, subtopicId = video.subtopicId,
            completed = true, notes = "Watched YouTube video"
        )
        update { snap ->
            snap.copy(
                videos = snap.videos.map { v -> if (v.id == video.id) v.copy(lastWatchedDate = today) else v },
                studyLogs = snap.studyLogs + logDto,
                hasUserEdits = true
            )
        }
    }

    suspend fun deleteVideo(videoId: String) {
        update { it.copy(videos = it.videos.filterNot { v -> v.id == videoId }, hasUserEdits = true) }
    }

    suspend fun addMcq(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        question: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String?,
    ) {
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.take(6)
        val dto = OfflineMcqDto(
            id = "mcq_${StudyRepository.newValidId()}", subjectId = subjectId, topicId = topicId, subtopicId = subtopicId,
            question = question.trim().take(500), options = cleanOptions,
            correctIndex = correctIndex.coerceIn(0, (cleanOptions.lastIndex).coerceAtLeast(0)).toLong(),
            explanation = explanation?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
            createdAtSeconds = nowSeconds(),
        )
        update { it.copy(mcqs = it.mcqs + dto, hasUserEdits = true) }
    }

    suspend fun deleteMcq(mcqId: String) {
        update { it.copy(mcqs = it.mcqs.filterNot { m -> m.id == mcqId }, hasUserEdits = true) }
    }

    suspend fun recordTestResult(
        subjectId: String,
        topicId: String,
        subtopicId: String,
        noteId: String?,
        testTitle: String,
        scoreObtained: Double,
        totalMarks: Double,
        summaryText: String?,
        subjectName: String,
        totalQuestions: Int = 0,
        attemptedCount: Int = 0,
        correctCount: Int = 0,
        wrongCount: Int = 0,
        unattemptedCount: Int = 0,
        durationSeconds: Long = 0L,
        testMode: String = "EXAM",
        breakdownJson: String? = null,
        resultId: String = "res_${StudyRepository.newValidId()}",
    ): TestResult {
        val safeObtained = scoreObtained.coerceAtLeast(0.0)
        val safeTotal = totalMarks.coerceAtLeast(0.0)
        val pct = if (safeTotal > 0.0) ((safeObtained / safeTotal) * 100.0).coerceIn(0.0, 100.0) else 0.0
        val today = StudyRepository.todayKey()
        val dto = OfflineTestResultDto(
            id = resultId, subjectId = subjectId, topicId = topicId, subtopicId = subtopicId, noteId = noteId,
            testTitle = testTitle.trim().ifBlank { "Mock Test" }.take(150),
            scoreObtained = safeObtained, totalMarks = safeTotal, percentage = pct,
            totalQuestions = totalQuestions, attemptedCount = attemptedCount,
            correctCount = correctCount, wrongCount = wrongCount, unattemptedCount = unattemptedCount,
            durationSeconds = durationSeconds, testMode = testMode, breakdownJson = breakdownJson,
            summaryText = summaryText, takenDate = today, createdAtSeconds = nowSeconds(),
        )
        val logDto = OfflineStudyLogDto(
            id = "log_${StudyRepository.newValidId()}", dateKey = today, entryType = "STUDIED",
            title = "$testTitle (${scoreObtained.toInt()}/${totalMarks.toInt()})",
            subjectName = subjectName, subtopicId = subtopicId, completed = true, notes = summaryText
        )
        update {
            it.copy(
                testResults = it.testResults.filterNot { existing -> existing.id == resultId } + dto,
                studyLogs = it.studyLogs + logDto,
                hasUserEdits = true
            )
        }
        return dto.toModel(ownerId)
    }

    suspend fun addStudyLog(dateKey: String, entryType: String, title: String, subjectName: String?, notes: String?) {
        val dto = OfflineStudyLogDto(
            id = "log_${StudyRepository.newValidId()}", dateKey = dateKey,
            entryType = if (entryType == "STUDIED") "STUDIED" else "PLANNED",
            title = title.trim().take(150),
            subjectName = subjectName?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
            completed = entryType == "STUDIED",
            notes = notes?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
        )
        update { it.copy(studyLogs = it.studyLogs + dto, hasUserEdits = true) }
    }

    suspend fun toggleStudyLogCompleted(logId: String, completed: Boolean) {
        update { snap ->
            snap.copy(studyLogs = snap.studyLogs.map { l -> if (l.id == logId) l.copy(completed = completed) else l }, hasUserEdits = true)
        }
    }

    suspend fun deleteStudyLog(logId: String) {
        update { it.copy(studyLogs = it.studyLogs.filterNot { l -> l.id == logId }, hasUserEdits = true) }
    }

    suspend fun generateExamRevisionPlan(subjects: List<Subject>, examDateMillis: Long): Int {
        if (subjects.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val daysUntil = ((examDateMillis - now) / (1000L * 60L * 60L * 24L)).toInt().coerceIn(1, 14)
        val planDays = daysUntil.coerceAtMost(7)
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val newLogs = mutableListOf<OfflineStudyLogDto>()
        for (dayOffset in 0 until planDays) {
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, dayOffset)
            val dateKey = fmt.format(cal.time)
            val subject = subjects[dayOffset % subjects.size]
            newLogs += OfflineStudyLogDto(
                id = "plan_${dateKey}_${subject.id}_${StudyRepository.newValidId().take(6)}",
                dateKey = dateKey, entryType = "PLANNED",
                title = "${subject.nameEn} Revision & Mock Test", subjectName = subject.nameEn,
                completed = false, notes = "Auto-scheduled for upcoming exam"
            )
        }
        update { it.copy(studyLogs = it.studyLogs + newLogs, hasUserEdits = true) }
        return planDays
    }

    suspend fun addRevisionDiaryEntry(
        dateKey: String, title: String, subjectName: String?, sourceName: String, content: String,
        pdfTitle: String? = null, pdfLocalFileName: String? = null, entryId: String = "diary_${StudyRepository.newValidId()}",
    ) {
        val dto = OfflineRevisionDiaryDto(
            id = entryId, dateKey = dateKey.take(20), title = title.trim().take(150),
            subjectName = subjectName?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
            sourceName = sourceName.trim().take(150), content = content.trim().take(5000),
            pdfTitle = pdfTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(150),
            pdfLocalFileName = pdfLocalFileName?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            updatedAtSeconds = nowSeconds(),
        )
        update { it.copy(revisionDiary = it.revisionDiary + dto, hasUserEdits = true) }
    }

    suspend fun updateRevisionDiaryEntry(
        entryId: String, title: String, subjectName: String?, sourceName: String, content: String,
        pdfTitle: String? = null, pdfLocalFileName: String? = null,
    ) {
        update { snap ->
            snap.copy(
                revisionDiary = snap.revisionDiary.map { e ->
                    if (e.id == entryId) {
                        e.copy(
                            title = title.trim().take(150),
                            subjectName = subjectName?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
                            sourceName = sourceName.trim().take(150), content = content.trim().take(5000),
                            pdfTitle = pdfTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(150),
                            pdfLocalFileName = pdfLocalFileName?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                            updatedAtSeconds = nowSeconds()
                        )
                    } else e
                },
                hasUserEdits = true
            )
        }
    }

    suspend fun deleteRevisionDiaryEntry(entryId: String) {
        update { it.copy(revisionDiary = it.revisionDiary.filterNot { e -> e.id == entryId }, hasUserEdits = true) }
    }

    suspend fun upsertPersonalNote(note: PersonalNote) {
        val dto = OfflinePersonalNoteDto(
            id = note.id.ifBlank { "pnote_${StudyRepository.newValidId()}" },
            subjectId = note.subjectId,
            subjectName = note.subjectName,
            topicName = note.topicName,
            title = note.title.trim().take(160),
            contentSummary = note.contentSummary.take(500),
            blocksJson = note.blocksJson,
            updatedAtMillis = note.updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        update { snap ->
            snap.copy(
                personalNotes = snap.personalNotes.filterNot { it.id == dto.id } + dto,
                hasUserEdits = true
            )
        }
    }

    suspend fun deletePersonalNote(noteId: String) {
        update { it.copy(personalNotes = it.personalNotes.filterNot { p -> p.id == noteId }, hasUserEdits = true) }
    }

    suspend fun upsertBookmark(item: BookmarkItem) {
        val dto = OfflineBookmarkDto(
            id = item.id.ifBlank { "bmk_${StudyRepository.newValidId()}" },
            bookmarkType = item.bookmarkType,
            title = item.title.trim().take(200),
            subtitle = item.subtitle.take(300),
            refId = item.refId,
            subjectId = item.subjectId,
            topicId = item.topicId,
            subtopicId = item.subtopicId,
            pageIndex = item.pageIndex,
            questionJson = item.questionJson,
            createdAtMillis = item.createdAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        update { snap ->
            snap.copy(
                bookmarks = snap.bookmarks.filterNot { it.id == dto.id } + dto,
                hasUserEdits = true
            )
        }
    }

    suspend fun deleteBookmark(bookmarkId: String) {
        update { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == bookmarkId }, hasUserEdits = true) }
    }

    suspend fun savePdfAnnotationDoc(noteId: String, annotationsJson: String, updatedAtMillis: Long) {
        val dto = OfflinePdfAnnotationDto(noteId = noteId, annotationsJson = annotationsJson, updatedAtMillis = updatedAtMillis)
        update { snap ->
            snap.copy(
                pdfAnnotations = snap.pdfAnnotations.filterNot { it.noteId == noteId } + dto,
                hasUserEdits = true
            )
        }
    }

    suspend fun adoptOfflineSnapshotIfEmpty(offlineSnap: OfflineVaultSnapshot) {
        if (!snapshot.hasUserEdits && offlineSnap.hasUserEdits) {
            update { mergeMissingDefaultSubjects(offlineSnap).copy(hasUserEdits = true) }
        }
    }

    private suspend fun update(transform: (OfflineVaultSnapshot) -> OfflineVaultSnapshot): OfflineVaultSnapshot {
        return mutex.withLock {
            val next = transform(snapshot)
            snapshot = next
            persist(next)
            emitAll()
            next
        }
    }

    private fun emitAll() {
        _profile.value = snapshot.profile.toModel()
        _subjects.value = snapshot.subjects.map { it.toModel(ownerId) }.sortedBy { it.sortOrder }
        _topics.value = snapshot.topics.map { it.toModel(ownerId) }.sortedBy { it.name.lowercase() }
        _subtopics.value = snapshot.subtopics.map { it.toModel(ownerId) }.sortedBy { it.name.lowercase() }
        _notes.value = snapshot.notes.map { it.toModel(ownerId) }.sortedByDescending { it.updatedAt?.seconds ?: 0L }
        _videos.value = snapshot.videos.map { it.toModel(ownerId) }.sortedByDescending { it.createdAt?.seconds ?: 0L }
        _mcqs.value = snapshot.mcqs.map { it.toModel(ownerId) }.sortedBy { it.createdAt?.seconds ?: 0L }
        _testResults.value = snapshot.testResults.map { it.toModel(ownerId) }.sortedByDescending { it.createdAt?.seconds ?: 0L }
        _studyLogs.value = snapshot.studyLogs.map { it.toModel(ownerId) }.sortedByDescending { it.dateKey }
        _revisionDiary.value = snapshot.revisionDiary.map { it.toModel(ownerId) }.sortedByDescending { it.updatedAt?.seconds ?: 0L }
        _personalNotes.value = snapshot.personalNotes.map { it.toModel(ownerId) }.sortedByDescending { it.updatedAtMillis }
        _bookmarks.value = snapshot.bookmarks.map { it.toModel(ownerId) }.sortedByDescending { it.createdAtMillis }
        _pdfAnnotations.value = snapshot.pdfAnnotations.map { it.toModel(ownerId) }
    }

    private suspend fun persist(snap: OfflineVaultSnapshot) = withContext(Dispatchers.IO) {
        runCatching { storeFile.writeText(json.encodeToString(snap), Charsets.UTF_8) }
    }

    private fun loadOrSeedSnapshot(): OfflineVaultSnapshot {
        if (storeFile.exists()) {
            val parsed = runCatching {
                json.decodeFromString<OfflineVaultSnapshot>(storeFile.readText(Charsets.UTF_8))
            }.getOrNull()
            if (parsed != null && parsed.subjects.isNotEmpty()) {
                val merged = mergeMissingDefaultSubjects(parsed)
                if (merged.subjects.size != parsed.subjects.size) {
                    runCatching { storeFile.writeText(json.encodeToString(merged), Charsets.UTF_8) }
                }
                return merged
            }
        }
        val seeded = buildInitialSeed()
        runCatching { storeFile.writeText(json.encodeToString(seeded), Charsets.UTF_8) }
        return seeded
    }

    private fun mergeMissingDefaultSubjects(existing: OfflineVaultSnapshot): OfflineVaultSnapshot {
        val existingSubjectIds = existing.subjects.map { it.id }.toSet()
        val existingTopicIds = existing.topics.map { it.id }.toSet()
        val existingSubtopicIds = existing.subtopics.map { it.id }.toSet()

        val addedSubjects = mutableListOf<OfflineSubjectDto>()
        val addedTopics = mutableListOf<OfflineTopicDto>()
        val addedSubtopics = mutableListOf<OfflineSubtopicDto>()

        DefaultSubjectsSeed.all12Subjects.forEachIndexed { sIdx, s ->
            if (s.id !in existingSubjectIds) {
                addedSubjects += OfflineSubjectDto(s.id, s.nameEn, s.nameHi, s.iconKey, s.colorHex, s.sortOrder)
                s.topics.forEachIndexed { tIdx, (tName, subList) ->
                    val tId = "topic_${sIdx}_${tIdx}"
                    if (tId !in existingTopicIds) {
                        addedTopics += OfflineTopicDto(tId, s.id, tName)
                    }
                    subList.forEachIndexed { subIdx, subName ->
                        val subId = "sub_${sIdx}_${tIdx}_${subIdx}"
                        if (subId !in existingSubtopicIds) {
                            addedSubtopics += OfflineSubtopicDto(subId, s.id, tId, subName)
                        }
                    }
                }
            }
        }
        if (addedSubjects.isEmpty()) return existing
        return existing.copy(
            subjects = existing.subjects + addedSubjects,
            topics = existing.topics + addedTopics,
            subtopics = existing.subtopics + addedSubtopics,
        )
    }

    private fun buildInitialSeed(): OfflineVaultSnapshot {
        val subjects = mutableListOf<OfflineSubjectDto>()
        val topics = mutableListOf<OfflineTopicDto>()
        val subtopics = mutableListOf<OfflineSubtopicDto>()

        DefaultSubjectsSeed.all12Subjects.forEachIndexed { sIdx, s ->
            subjects += OfflineSubjectDto(s.id, s.nameEn, s.nameHi, s.iconKey, s.colorHex, s.sortOrder)
            s.topics.forEachIndexed { tIdx, (tName, subList) ->
                val tId = "topic_${sIdx}_${tIdx}"
                topics += OfflineTopicDto(tId, s.id, tName)
                subList.forEachIndexed { subIdx, subName ->
                    val subId = "sub_${sIdx}_${tIdx}_${subIdx}"
                    subtopics += OfflineSubtopicDto(subId, s.id, tId, subName)
                }
            }
        }

        val sampleNote = OfflineNoteDto(
            id = "note_sample_ivc",
            subjectId = "subj_history",
            topicId = "topic_0_0",
            subtopicId = "sub_0_0_0",
            folderName = "Mock Tests",
            title = "Sample Practice Mock Test",
            noteType = NoteType.HTML.name,
            localFileName = null,
            htmlContent = LocalFileStorage.buildSampleHtmlMockTest("General Studies Quick Mock Test"),
            isMockTest = true,
            updatedAtSeconds = nowSeconds()
        )
        val sampleMcq = OfflineMcqDto(
            id = "mcq_sample_1",
            subjectId = "subj_history",
            topicId = "topic_0_0",
            subtopicId = "sub_0_0_0",
            question = "सिंधु घाटी सभ्यता का महान स्नानागार (Great Bath) कहाँ मिला था?",
            options = listOf("हड़प्पा (Harappa)", "मोहनजोदड़ो (Mohenjo-daro)", "लोथल (Lothal)", "कालीबंगा (Kalibangan)"),
            correctIndex = 1L,
            explanation = "महान स्नानागार मोहनजोदड़ो से प्राप्त हुआ था।",
            createdAtSeconds = nowSeconds()
        )
        val sampleDiary = OfflineRevisionDiaryDto(
            id = "diary_sample_1",
            dateKey = StudyRepository.todayKey(),
            title = "सिंधु घाटी एवं मौलिक अधिकार शॉर्ट नोट्स",
            subjectName = "History / Polity",
            sourceName = "NCERT Class 12 & Laxmikant Ch-7",
            content = "1. अनुच्छेद 14-18 समानता का अधिकार।\n2. लोथल सिंधु घाटी का प्रमुख बंदरगाह था।",
            updatedAtSeconds = nowSeconds()
        )

        return OfflineVaultSnapshot(
            profile = OfflineUserProfileDto(
                id = ownerId,
                displayName = "Offline Learner",
                themeId = "bunts_royal_light",
            ),
            subjects = subjects,
            topics = topics,
            subtopics = subtopics,
            notes = listOf(sampleNote),
            mcqs = listOf(sampleMcq),
            revisionDiary = listOf(sampleDiary),
            hasUserEdits = false,
        )
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000L

    companion object {
        const val OFFLINE_UID = "offline_local"
    }
}
