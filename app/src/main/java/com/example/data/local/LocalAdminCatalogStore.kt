package com.example.data.local

import android.content.Context
import com.example.data.model.FullLengthExam
import com.example.data.model.McqQuestion
import com.example.data.model.StudentSummary
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.VideoLink
import com.example.data.model.WhatsAppRequestRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SharedAdminCatalogBundle(
    val subjects: List<Subject> = emptyList(),
    val topics: List<Topic> = emptyList(),
    val subtopics: List<Subtopic> = emptyList(),
    val contents: List<StudyNote> = emptyList(),
    val videos: List<VideoLink> = emptyList(),
    val mcqs: List<McqQuestion> = emptyList(),
    val fullLengthExams: List<FullLengthExam> = emptyList(),
    val studentSummaries: List<StudentSummary> = emptyList(),
    val whatsAppRequests: List<WhatsAppRequestRecord> = emptyList(),
    val studentResultsMap: Map<String, List<TestResult>> = emptyMap(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

class LocalAdminCatalogStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val storeFile = File(context.filesDir, "learn_talent_admin_catalog.json")

    private var bundle: SharedAdminCatalogBundle = loadBundle()

    private val _subjects = MutableStateFlow(bundle.subjects)
    val subjects: StateFlow<List<Subject>> = _subjects.asStateFlow()

    private val _topics = MutableStateFlow(bundle.topics)
    val topics: StateFlow<List<Topic>> = _topics.asStateFlow()

    private val _subtopics = MutableStateFlow(bundle.subtopics)
    val subtopics: StateFlow<List<Subtopic>> = _subtopics.asStateFlow()

    private val _contents = MutableStateFlow(bundle.contents)
    val contents: StateFlow<List<StudyNote>> = _contents.asStateFlow()

    private val _videos = MutableStateFlow(bundle.videos)
    val videos: StateFlow<List<VideoLink>> = _videos.asStateFlow()

    private val _mcqs = MutableStateFlow(bundle.mcqs)
    val mcqs: StateFlow<List<McqQuestion>> = _mcqs.asStateFlow()

    private val _fullLengthExams = MutableStateFlow(bundle.fullLengthExams)
    val fullLengthExams: StateFlow<List<FullLengthExam>> = _fullLengthExams.asStateFlow()

    private val _studentSummaries = MutableStateFlow(bundle.studentSummaries)
    val studentSummaries: StateFlow<List<StudentSummary>> = _studentSummaries.asStateFlow()

    private val _whatsAppRequests = MutableStateFlow(bundle.whatsAppRequests)
    val whatsAppRequests: StateFlow<List<WhatsAppRequestRecord>> = _whatsAppRequests.asStateFlow()

    fun exportPublishedCatalogJson(): String {
        val publishable = bundle.copy(
            subjects = bundle.subjects.filter { it.published },
            topics = bundle.topics.filter { it.published },
            subtopics = bundle.subtopics.filter { it.published },
            contents = bundle.contents.filter { it.published },
            videos = bundle.videos.filter { it.published },
            mcqs = bundle.mcqs.filter { it.published },
            fullLengthExams = bundle.fullLengthExams.filter { it.published },
            studentSummaries = emptyList(),
            whatsAppRequests = emptyList(),
            studentResultsMap = emptyMap(),
            updatedAtMillis = System.currentTimeMillis(),
        )
        return json.encodeToString(publishable)
    }

    suspend fun importPublishedCatalogJson(rawJson: String) {
        val parsed = json.decodeFromString<SharedAdminCatalogBundle>(rawJson)
        update { current ->
            current.copy(
                subjects = parsed.subjects.map { it.copy(isShared = true, published = true) },
                topics = parsed.topics.map { it.copy(isShared = true, published = true) },
                subtopics = parsed.subtopics.map { it.copy(isShared = true, published = true) },
                contents = parsed.contents.map { it.copy(isShared = true, published = true) },
                videos = parsed.videos.map { it.copy(isShared = true, published = true) },
                mcqs = parsed.mcqs.map { it.copy(isShared = true, published = true) },
                fullLengthExams = parsed.fullLengthExams.map { it.copy(isShared = true, published = true) },
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun upsertSharedContent(note: StudyNote) {
        update { b ->
            b.copy(
                contents = b.contents.filterNot { it.id == note.id } + note.copy(isShared = true),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun toggleSharedContentPublished(noteId: String, published: Boolean) {
        update { b ->
            b.copy(
                contents = b.contents.map { if (it.id == noteId) it.copy(published = published) else it },
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun deleteSharedContent(noteId: String) {
        update { b ->
            b.copy(contents = b.contents.filterNot { it.id == noteId })
        }
    }

    suspend fun incrementSharedDownloadCount(noteId: String) {
        update { b ->
            b.copy(contents = b.contents.map { if (it.id == noteId) it.copy(downloadsCount = it.downloadsCount + 1L) else it })
        }
    }

    suspend fun upsertSharedTopic(subjectId: String, name: String, description: String?) {
        val topic = Topic(
            id = "shared_topic_${StudyDateUtils.newValidId()}",
            userId = "admin",
            subjectId = subjectId,
            name = name.trim().take(120),
            description = description?.trim(),
            isShared = true,
            published = true,
        )
        update { b -> b.copy(topics = b.topics + topic) }
    }

    suspend fun deleteSharedTopic(topicId: String) {
        update { b -> b.copy(topics = b.topics.filterNot { it.id == topicId }) }
    }

    suspend fun upsertSharedSubtopic(subjectId: String, topicId: String, name: String) {
        val sub = Subtopic(
            id = "shared_sub_${StudyDateUtils.newValidId()}",
            userId = "admin",
            subjectId = subjectId,
            topicId = topicId,
            name = name.trim().take(120),
            isShared = true,
            published = true,
        )
        update { b -> b.copy(subtopics = b.subtopics + sub) }
    }

    suspend fun deleteSharedSubtopic(subtopicId: String) {
        update { b -> b.copy(subtopics = b.subtopics.filterNot { it.id == subtopicId }) }
    }

    suspend fun deleteSharedVideo(videoId: String) {
        update { b -> b.copy(videos = b.videos.filterNot { it.id == videoId }) }
    }

    suspend fun deleteSharedMcq(mcqId: String) {
        update { b -> b.copy(mcqs = b.mcqs.filterNot { it.id == mcqId }) }
    }

    suspend fun deleteSharedFullLengthExam(examId: String) {
        update { b -> b.copy(fullLengthExams = b.fullLengthExams.filterNot { it.id == examId }) }
    }

    suspend fun submitWhatsAppRequest(record: WhatsAppRequestRecord) {
        val withId = record.copy(id = record.id.ifBlank { "req_${StudyDateUtils.newValidId()}" })
        update { b -> b.copy(whatsAppRequests = listOf(withId) + b.whatsAppRequests) }
    }

    suspend fun updateWhatsAppRequestStatus(requestId: String, status: String) {
        update { b ->
            b.copy(whatsAppRequests = b.whatsAppRequests.map { if (it.id == requestId) it.copy(status = status) else it })
        }
    }

    suspend fun upsertStudentSummary(summary: StudentSummary, latestResults: List<TestResult>) {
        update { b ->
            val updatedMap = b.studentResultsMap.toMutableMap()
            updatedMap[summary.userId] = latestResults
            b.copy(
                studentSummaries = b.studentSummaries.filterNot { it.userId == summary.userId } + summary,
                studentResultsMap = updatedMap,
            )
        }
    }

    fun getStudentResults(studentUid: String): List<TestResult> =
        bundle.studentResultsMap[studentUid] ?: emptyList()

    private suspend fun update(transform: (SharedAdminCatalogBundle) -> SharedAdminCatalogBundle) {
        mutex.withLock {
            val next = transform(bundle)
            bundle = next
            withContext(Dispatchers.IO) {
                runCatching { storeFile.writeText(json.encodeToString(next), Charsets.UTF_8) }
            }
            _subjects.value = next.subjects
            _topics.value = next.topics
            _subtopics.value = next.subtopics
            _contents.value = next.contents
            _videos.value = next.videos
            _mcqs.value = next.mcqs
            _fullLengthExams.value = next.fullLengthExams
            _studentSummaries.value = next.studentSummaries
            _whatsAppRequests.value = next.whatsAppRequests
        }
    }

    private fun loadBundle(): SharedAdminCatalogBundle {
        if (!storeFile.exists()) return SharedAdminCatalogBundle()
        return runCatching {
            json.decodeFromString<SharedAdminCatalogBundle>(storeFile.readText(Charsets.UTF_8))
        }.getOrDefault(SharedAdminCatalogBundle())
    }
}
