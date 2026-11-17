package com.example.data.local

import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.CloudPdfAnnotationDoc
import com.example.data.model.FullLengthExam
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.PersonalNote
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyLog
import com.example.data.model.StudyNote
import com.example.data.model.StudyTimestamp
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.UserProfile
import com.example.data.model.VideoLink
import kotlinx.serialization.Serializable

@Serializable
data class OfflineUserProfileDto(
    val id: String = LocalOfflineStore.OFFLINE_UID,
    val displayName: String = "Offline Learner",
    val email: String? = null,
    val photoUrl: String? = null,
    val role: String = "student",
    val examName: String? = null,
    val examDateMillis: Long? = null,
    val themeId: String = "bunts_royal_light",
)

@Serializable
data class OfflineSubjectDto(
    val id: String,
    val nameEn: String,
    val nameHi: String? = null,
    val iconKey: String = "book",
    val colorHex: String = "#2456A4",
    val sortOrder: Long = 0L,
)

@Serializable
data class OfflineTopicDto(
    val id: String,
    val subjectId: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class OfflineSubtopicDto(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val name: String,
    val lastStudiedDate: String? = null,
)

@Serializable
data class OfflineFullLengthExamDto(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val updatedAtSeconds: Long = 0L,
)

@Serializable
data class OfflineNoteDto(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val subtopicId: String,
    val folderName: String = "",
    val title: String,
    val noteType: String = NoteType.PDF.name,
    val localFileName: String? = null,
    val htmlContent: String? = null,
    val lastOpenedDate: String? = null,
    val isMockTest: Boolean = false,
    val updatedAtSeconds: Long = 0L,
)

@Serializable
data class OfflineVideoDto(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val subtopicId: String,
    val title: String,
    val youtubeUrl: String,
    val channelOrNote: String? = null,
    val lastWatchedDate: String? = null,
    val createdAtSeconds: Long = 0L,
)

@Serializable
data class OfflineMcqDto(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val subtopicId: String,
    val question: String,
    val options: List<String> = emptyList(),
    val correctIndex: Long = 0L,
    val explanation: String? = null,
    val createdAtSeconds: Long = 0L,
)

@Serializable
data class OfflineTestResultDto(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val subtopicId: String,
    val noteId: String? = null,
    val testTitle: String,
    val scoreObtained: Double = 0.0,
    val totalMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val totalQuestions: Int = 0,
    val attemptedCount: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val unattemptedCount: Int = 0,
    val durationSeconds: Long = 0L,
    val testMode: String = "EXAM",
    val breakdownJson: String? = null,
    val summaryText: String? = null,
    val takenDate: String = "",
    val createdAtSeconds: Long = 0L,
)

@Serializable
data class OfflineStudyLogDto(
    val id: String,
    val dateKey: String,
    val entryType: String = "PLANNED",
    val title: String,
    val subjectName: String? = null,
    val subtopicId: String? = null,
    val completed: Boolean = false,
    val notes: String? = null,
)

@Serializable
data class OfflineRevisionDiaryDto(
    val id: String,
    val dateKey: String,
    val title: String,
    val subjectName: String? = null,
    val sourceName: String = "",
    val content: String = "",
    val pdfTitle: String? = null,
    val pdfLocalFileName: String? = null,
    val updatedAtSeconds: Long = 0L,
)

@Serializable
data class OfflinePersonalNoteDto(
    val id: String,
    val subjectId: String = "",
    val subjectName: String = "",
    val topicName: String = "",
    val title: String = "",
    val contentSummary: String = "",
    val blocksJson: String = "[]",
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class OfflineBookmarkDto(
    val id: String,
    val bookmarkType: String = BookmarkType.FAVORITE_NOTE.name,
    val title: String = "",
    val subtitle: String = "",
    val refId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val pageIndex: Int = 0,
    val questionJson: String? = null,
    val createdAtMillis: Long = 0L,
)

@Serializable
data class OfflinePdfAnnotationDto(
    val noteId: String,
    val annotationsJson: String = "",
    val updatedAtMillis: Long = 0L,
)

@Serializable
data class OfflineVaultSnapshot(
    val profile: OfflineUserProfileDto = OfflineUserProfileDto(),
    val subjects: List<OfflineSubjectDto> = emptyList(),
    val topics: List<OfflineTopicDto> = emptyList(),
    val subtopics: List<OfflineSubtopicDto> = emptyList(),
    val fullLengthExams: List<OfflineFullLengthExamDto> = emptyList(),
    val notes: List<OfflineNoteDto> = emptyList(),
    val videos: List<OfflineVideoDto> = emptyList(),
    val mcqs: List<OfflineMcqDto> = emptyList(),
    val testResults: List<OfflineTestResultDto> = emptyList(),
    val studyLogs: List<OfflineStudyLogDto> = emptyList(),
    val revisionDiary: List<OfflineRevisionDiaryDto> = emptyList(),
    val personalNotes: List<OfflinePersonalNoteDto> = emptyList(),
    val bookmarks: List<OfflineBookmarkDto> = emptyList(),
    val pdfAnnotations: List<OfflinePdfAnnotationDto> = emptyList(),
    val hasUserEdits: Boolean = false,
)

@Serializable
data class DriveSubjectProgressItem(
    val subjectId: String,
    val subjectName: String,
    val attemptsCount: Int,
    val averagePercentage: Double,
    val bestPercentage: Double,
)

@Serializable
data class DriveProgressReportPayload(
    val userId: String,
    val studentName: String,
    val email: String,
    val role: String,
    val examName: String? = null,
    val examDateMillis: Long? = null,
    val totalTestsAttempted: Int,
    val averagePercentage: Double,
    val bestPercentage: Double,
    val totalNotesCount: Int,
    val personalNotesCount: Int,
    val bookmarksCount: Int,
    val revisionDiaryCount: Int,
    val completedStudyTasks: Int,
    val totalStudyTasks: Int,
    val subjectBreakdown: List<DriveSubjectProgressItem> = emptyList(),
    val generatedAtMillis: Long = System.currentTimeMillis(),
)

internal fun OfflineUserProfileDto.toModel() = UserProfile(
    id = id,
    userId = id,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl,
    role = role,
    examName = examName,
    examDateMillis = examDateMillis,
    themeId = themeId,
)

internal fun OfflineSubjectDto.toModel(ownerId: String) = Subject(
    id = id,
    userId = ownerId,
    nameEn = nameEn,
    nameHi = nameHi,
    iconKey = iconKey,
    colorHex = colorHex,
    sortOrder = sortOrder,
)

internal fun OfflineTopicDto.toModel(ownerId: String) = Topic(
    id = id,
    userId = ownerId,
    subjectId = subjectId,
    name = name,
    description = description,
)

internal fun OfflineSubtopicDto.toModel(ownerId: String) = Subtopic(
    id = id,
    userId = ownerId,
    subjectId = subjectId,
    topicId = topicId,
    name = name,
    lastStudiedDate = lastStudiedDate,
)

internal fun OfflineFullLengthExamDto.toModel(ownerId: String) = FullLengthExam(
    id = id,
    userId = ownerId,
    name = name,
    subtitle = subtitle,
    isShared = false,
    published = true,
    updatedAt = if (updatedAtSeconds > 0) StudyTimestamp(updatedAtSeconds) else null,
)

internal fun OfflineNoteDto.toModel(ownerId: String) = StudyNote(
    id = id,
    userId = ownerId,
    subjectId = subjectId,
    topicId = topicId,
    subtopicId = subtopicId,
    folderName = folderName,
    title = title,
    noteType = noteType,
    localFileName = localFileName,
    htmlContent = htmlContent,
    lastOpenedDate = lastOpenedDate,
    isMockTest = isMockTest,
    updatedAt = if (updatedAtSeconds > 0) StudyTimestamp(updatedAtSeconds) else null,
)

internal fun OfflineVideoDto.toModel(ownerId: String) = VideoLink(
    id = id,
    userId = ownerId,
    subjectId = subjectId,
    topicId = topicId,
    subtopicId = subtopicId,
    title = title,
    youtubeUrl = youtubeUrl,
    channelOrNote = channelOrNote,
    lastWatchedDate = lastWatchedDate,
    createdAt = if (createdAtSeconds > 0) StudyTimestamp(createdAtSeconds) else null,
)

internal fun OfflineMcqDto.toModel(ownerId: String) = McqQuestion(
    id = id,
    userId = ownerId,
    subjectId = subjectId,
    topicId = topicId,
    subtopicId = subtopicId,
    question = question,
    options = options,
    correctIndex = correctIndex,
    explanation = explanation,
    createdAt = if (createdAtSeconds > 0) StudyTimestamp(createdAtSeconds) else null,
)

internal fun OfflineTestResultDto.toModel(ownerId: String) = TestResult(
    id = id,
    userId = ownerId,
    subjectId = subjectId,
    topicId = topicId,
    subtopicId = subtopicId,
    noteId = noteId,
    testTitle = testTitle,
    scoreObtained = scoreObtained,
    totalMarks = totalMarks,
    percentage = percentage,
    totalQuestions = totalQuestions,
    attemptedCount = attemptedCount,
    correctCount = correctCount,
    wrongCount = wrongCount,
    unattemptedCount = unattemptedCount,
    durationSeconds = durationSeconds,
    testMode = testMode,
    breakdownJson = breakdownJson,
    summaryText = summaryText,
    takenDate = takenDate,
    createdAt = if (createdAtSeconds > 0) StudyTimestamp(createdAtSeconds) else null,
)

internal fun OfflineStudyLogDto.toModel(ownerId: String) = StudyLog(
    id = id,
    userId = ownerId,
    dateKey = dateKey,
    entryType = entryType,
    title = title,
    subjectName = subjectName,
    subtopicId = subtopicId,
    completed = completed,
    notes = notes,
)

internal fun OfflineRevisionDiaryDto.toModel(ownerId: String) = RevisionDiaryEntry(
    id = id,
    userId = ownerId,
    dateKey = dateKey,
    title = title,
    subjectName = subjectName,
    sourceName = sourceName,
    content = content,
    pdfTitle = pdfTitle,
    pdfLocalFileName = pdfLocalFileName,
    updatedAt = if (updatedAtSeconds > 0) StudyTimestamp(updatedAtSeconds) else null,
)

internal fun OfflinePersonalNoteDto.toModel(ownerId: String) = PersonalNote(
    id = id,
    userId = ownerId,
    subjectId = subjectId,
    subjectName = subjectName,
    topicName = topicName,
    title = title,
    contentSummary = contentSummary,
    blocksJson = blocksJson,
    updatedAtMillis = updatedAtMillis,
    updatedAt = if (updatedAtMillis > 0L) StudyTimestamp(updatedAtMillis / 1000L) else null,
)

internal fun OfflineBookmarkDto.toModel(ownerId: String) = BookmarkItem(
    id = id,
    userId = ownerId,
    bookmarkType = bookmarkType,
    title = title,
    subtitle = subtitle,
    refId = refId,
    subjectId = subjectId,
    topicId = topicId,
    subtopicId = subtopicId,
    pageIndex = pageIndex,
    questionJson = questionJson,
    createdAtMillis = createdAtMillis,
    createdAt = if (createdAtMillis > 0L) StudyTimestamp(createdAtMillis / 1000L) else null,
)

internal fun OfflinePdfAnnotationDto.toModel(ownerId: String) = CloudPdfAnnotationDoc(
    id = noteId,
    userId = ownerId,
    noteId = noteId,
    annotationsJson = annotationsJson,
    updatedAtMillis = updatedAtMillis,
)
