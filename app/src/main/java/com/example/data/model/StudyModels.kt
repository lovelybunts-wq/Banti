package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class NoteType {
    PDF,
    HTML,
    IMAGE,
    FOLDER;

    companion object {
        fun fromRaw(raw: String?): NoteType =
            entries.firstOrNull { it.name == raw } ?: PDF
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val examName: String? = null,
    val examDateMillis: Long? = null,
    val themeId: String = "midnight_teal",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nameEn: String = "",
    val nameHi: String? = null,
    val iconKey: String = "book",
    val colorHex: String = "#4FD1C5",
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Topic(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val name: String = "",
    val description: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Subtopic(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val name: String = "",
    val lastStudiedDate: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StudyNote(
    @DocumentId val id: String = "",
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
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun resolvedType(): NoteType = NoteType.fromRaw(noteType)
}

@Keep
@IgnoreExtraProperties
data class VideoLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val title: String = "",
    val youtubeUrl: String = "",
    val channelOrNote: String? = null,
    val lastWatchedDate: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class McqQuestion(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Long = 0L,
    val explanation: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class TestResult(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val subtopicId: String = "",
    val noteId: String? = null,
    val testTitle: String = "",
    val scoreObtained: Double = 0.0,
    val totalMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val summaryText: String? = null,
    val takenDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class StudyLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val entryType: String = "PLANNED",
    val title: String = "",
    val subjectName: String? = null,
    val subtopicId: String? = null,
    val completed: Boolean = false,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class RevisionDiaryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val title: String = "",
    val subjectName: String? = null,
    val sourceName: String = "",
    val content: String = "",
    val pdfTitle: String? = null,
    val pdfLocalFileName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
