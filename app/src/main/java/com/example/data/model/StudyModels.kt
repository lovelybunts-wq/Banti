package com.example.data.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@Serializable
data class StudyTimestamp(
    val seconds: Long = System.currentTimeMillis() / 1000L,
)

enum class NoteType {
    PDF,
    HTML,
    IMAGE,
    FOLDER;

    companion object {
        fun fromRaw(raw: String?): NoteType = when (raw?.uppercase()) {
            "PDF" -> PDF
            "HTML", "HTML_NOTE", "HTML_TEST" -> HTML
            "IMAGE" -> IMAGE
            "FOLDER" -> FOLDER
            else -> PDF
        }
    }
}

enum class BookmarkType {
    IMPORTANT_QUESTION,
    WRONG_QUESTION,
    FAVORITE_NOTE,
    SAVED_TOPIC,
    PDF_PAGE;

    companion object {
        fun fromRaw(raw: String?): BookmarkType =
            entries.firstOrNull { it.name == raw } ?: FAVORITE_NOTE
    }
}

enum class PersonalNoteBlockType {
    HEADING,
    PARAGRAPH,
    HIGHLIGHT,
    BULLET,
    CHECKLIST,
    TABLE_ROW,
    IMAGE;

    companion object {
        fun fromRaw(raw: String?): PersonalNoteBlockType =
            entries.firstOrNull { it.name == raw } ?: PARAGRAPH
    }
}

@Keep
@Serializable
data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val role: String = "student",
    val examName: String? = null,
    val examDateMillis: Long? = null,
    val themeId: String = "bunts_royal_light",
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class Subject(
    val id: String = "",
    val userId: String = "",
    val nameEn: String = "",
    val nameHi: String? = null,
    val iconKey: String = "book",
    val colorHex: String = "#2456A4",
    val sortOrder: Long = 0L,
    val isShared: Boolean = false,
    val published: Boolean = true,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class Topic(
    val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val name: String = "",
    val description: String? = null,
    val isShared: Boolean = false,
    val published: Boolean = true,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class Subtopic(
    val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val name: String = "",
    val lastStudiedDate: String? = null,
    val isShared: Boolean = false,
    val published: Boolean = true,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class StudyNote(
    val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val folderName: String = "",
    val title: String = "",
    val noteType: String = NoteType.PDF.name,
    val localFileName: String? = null,
    val htmlContent: String? = null,
    val lastOpenedDate: String? = null,
    val isShared: Boolean = false,
    val storagePath: String? = null,
    val downloadUrl: String? = null,
    val version: Long = 1L,
    val published: Boolean = true,
    val downloadsCount: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val isMockTest: Boolean = false,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
) {
    fun resolvedType(): NoteType = NoteType.fromRaw(noteType)
}

@Keep
@Serializable
data class VideoLink(
    val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val title: String = "",
    val youtubeUrl: String = "",
    val channelOrNote: String? = null,
    val lastWatchedDate: String? = null,
    val isShared: Boolean = false,
    val published: Boolean = true,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class McqQuestion(
    val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Long = 0L,
    val explanation: String? = null,
    val isShared: Boolean = false,
    val published: Boolean = true,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Serializable
data class QuestionAttemptRecord(
    val questionNumber: Int = 1,
    val questionText: String = "",
    val options: List<String> = emptyList(),
    val selectedOption: String = "",
    val correctOption: String = "",
    val explanation: String = "",
    val isCorrect: Boolean = false,
    val isAttempted: Boolean = false,
    val isMarkedForReview: Boolean = false,
    val subjectName: String = "",
)

@Serializable
data class InProgressTestAttempt(
    val noteId: String = "",
    val testTitle: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val testMode: String = "EXAM",
    val elapsedSeconds: Long = 0L,
    val currentQuestionIndex: Int = 0,
    val answers: Map<Int, String> = emptyMap(),
    val markedForReview: List<Int> = emptyList(),
    val savedAtMillis: Long = 0L,
)

@Keep
@Serializable
data class TestResult(
    val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val noteId: String? = null,
    val testTitle: String = "",
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
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class StudyLog(
    val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val entryType: String = "PLANNED",
    val title: String = "",
    val subjectName: String? = null,
    val subtopicId: String? = null,
    val completed: Boolean = false,
    val notes: String? = null,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class RevisionDiaryEntry(
    val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val title: String = "",
    val subjectName: String? = null,
    val sourceName: String = "",
    val content: String = "",
    val pdfTitle: String? = null,
    val pdfLocalFileName: String? = null,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Serializable
data class PersonalNoteBlock(
    val id: String = "",
    val type: String = PersonalNoteBlockType.PARAGRAPH.name,
    val text: String = "",
    val checked: Boolean = false,
    val textColorHex: String = "#14243A",
    val highlightColorHex: String? = null,
    val fontSizeSp: Int = 16,
    val tableCells: List<String> = emptyList(),
    val imageFileName: String? = null,
)

@Keep
@Serializable
data class PersonalNote(
    val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val topicName: String = "",
    val title: String = "",
    val contentSummary: String = "",
    val blocksJson: String = "[]",
    val updatedAtMillis: Long = 0L,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class BookmarkItem(
    val id: String = "",
    val userId: String = "",
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
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Serializable
data class RecentActivityItem(
    val id: String = "",
    val itemType: String = "NOTE",
    val title: String = "",
    val subtitle: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val refId: String = "",
    val timestampMillis: Long = 0L,
)

@Keep
@Serializable
data class WhatsAppRequestRecord(
    val id: String = "",
    val userId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val requestType: String = "",
    val subject: String = "",
    val topic: String = "",
    val subtopic: String = "",
    val extraMessage: String = "",
    val status: String = "PENDING",
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class StudentSummary(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val downloadsCount: Long = 0L,
    val testAttemptsCount: Long = 0L,
    val averageScorePct: Double = 0.0,
    val lastActiveMillis: Long = 0L,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

@Keep
@Serializable
data class FullLengthExam(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val subtitle: String? = null,
    val isShared: Boolean = false,
    val published: Boolean = true,
    val createdAt: StudyTimestamp? = null,
    val updatedAt: StudyTimestamp? = null,
)

object FullLengthExamDefaults {
    const val SUBJECT_ID = "full_length_exam"
    const val SUBTOPIC_ID = "full_length_html"

    val builtInExams: List<FullLengthExam> = listOf(
        FullLengthExam(id = "exam_mppsc_prelims", name = "MPPSC Prelims", subtitle = "GS Paper 1 & CSAT Full Mock Tests", isShared = false, published = true),
        FullLengthExam(id = "exam_mp_patwari", name = "MP Patwari / Vyapam", subtitle = "ESB Combined Group 2 & Patwari Tests", isShared = false, published = true),
        FullLengthExam(id = "exam_ssc_cgl", name = "SSC CGL / CHSL", subtitle = "Tier 1 & Tier 2 Full Length Tests", isShared = false, published = true),
        FullLengthExam(id = "exam_upsc_prelims", name = "UPSC CSE Prelims", subtitle = "General Studies & CSAT Full Papers", isShared = false, published = true),
        FullLengthExam(id = "exam_mp_police", name = "MP Police / SI", subtitle = "Constable & Sub-Inspector Full Tests", isShared = false, published = true),
        FullLengthExam(id = "exam_banking_ibps", name = "Banking (IBPS / SBI)", subtitle = "PO & Clerk Prelims/Mains Mock Tests", isShared = false, published = true),
        FullLengthExam(id = "exam_railway_rrb", name = "Railway RRB (NTPC / Group D)", subtitle = "CBT-1 & CBT-2 Full Length Mock Tests", isShared = false, published = true),
    )
}
