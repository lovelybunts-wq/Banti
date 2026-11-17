package com.example.data.local

import android.content.Context
import com.example.data.model.FullLengthExam
import com.example.data.model.McqQuestion
import com.example.data.model.StudentSummary
import com.example.data.model.StudentUploadSubmission
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
    val manifestVersion: Long = 1L,
    val allowStudentPersonalUploads: Boolean = true,
    val subjects: List<Subject> = emptyList(),
    val topics: List<Topic> = emptyList(),
    val subtopics: List<Subtopic> = emptyList(),
    val contents: List<StudyNote> = emptyList(),
    val videos: List<VideoLink> = emptyList(),
    val mcqs: List<McqQuestion> = emptyList(),
    val fullLengthExams: List<FullLengthExam> = emptyList(),
    val pendingSubmissions: List<StudentUploadSubmission> = emptyList(),
    val studentSummaries: List<StudentSummary> = emptyList(),
    val whatsAppRequests: List<WhatsAppRequestRecord> = emptyList(),
    val studentResultsMap: Map<String, List<TestResult>> = emptyMap(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

data class CatalogImportDiff(
    val manifestVersion: Long,
    val newPublishedNotes: List<StudyNote>,
)

class LocalAdminCatalogStore(
    private val context: Context,
    storeFileName: String = "learn_talent_admin_catalog.json",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val storeFile = File(context.filesDir, storeFileName)

    private var bundle: SharedAdminCatalogBundle = loadBundle()

    private val _manifestVersion = MutableStateFlow(bundle.manifestVersion)
    val manifestVersion: StateFlow<Long> = _manifestVersion.asStateFlow()

    private val _allowStudentPersonalUploads = MutableStateFlow(bundle.allowStudentPersonalUploads)
    val allowStudentPersonalUploads: StateFlow<Boolean> = _allowStudentPersonalUploads.asStateFlow()

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

    private val _pendingSubmissions = MutableStateFlow(bundle.pendingSubmissions)
    val pendingSubmissions: StateFlow<List<StudentUploadSubmission>> = _pendingSubmissions.asStateFlow()

    private val _studentSummaries = MutableStateFlow(bundle.studentSummaries)
    val studentSummaries: StateFlow<List<StudentSummary>> = _studentSummaries.asStateFlow()

    private val _whatsAppRequests = MutableStateFlow(bundle.whatsAppRequests)
    val whatsAppRequests: StateFlow<List<WhatsAppRequestRecord>> = _whatsAppRequests.asStateFlow()

    fun exportPublishedCatalogJson(): String {
        val publishable = bundle.copy(
            manifestVersion = bundle.manifestVersion,
            allowStudentPersonalUploads = bundle.allowStudentPersonalUploads,
            subjects = bundle.subjects.filter { it.published },
            topics = bundle.topics.filter { it.published },
            subtopics = bundle.subtopics.filter { it.published },
            contents = bundle.contents.filter { it.published },
            videos = bundle.videos.filter { it.published },
            mcqs = bundle.mcqs.filter { it.published },
            fullLengthExams = bundle.fullLengthExams.filter { it.published },
            pendingSubmissions = emptyList(),
            studentSummaries = emptyList(),
            whatsAppRequests = emptyList(),
            studentResultsMap = emptyMap(),
            updatedAtMillis = System.currentTimeMillis(),
        )
        return json.encodeToString(publishable)
    }

    suspend fun importPublishedCatalogJson(rawJson: String): CatalogImportDiff {
        val parsed = json.decodeFromString<SharedAdminCatalogBundle>(rawJson)
        val previousIds = bundle.contents.filter { it.published }.map { it.id }.toSet()
        val incomingPublishedNotes = parsed.contents.filter { it.published }.map { it.copy(isShared = true, published = true) }
        val newNotes = incomingPublishedNotes.filter { it.id !in previousIds }

        update { current ->
            current.copy(
                manifestVersion = parsed.manifestVersion.coerceAtLeast(current.manifestVersion),
                allowStudentPersonalUploads = parsed.allowStudentPersonalUploads,
                subjects = parsed.subjects.filter { it.published }.map { it.copy(isShared = true, published = true) },
                topics = parsed.topics.filter { it.published }.map { it.copy(isShared = true, published = true) },
                subtopics = parsed.subtopics.filter { it.published }.map { it.copy(isShared = true, published = true) },
                contents = incomingPublishedNotes,
                videos = parsed.videos.filter { it.published }.map { it.copy(isShared = true, published = true) },
                mcqs = parsed.mcqs.filter { it.published }.map { it.copy(isShared = true, published = true) },
                fullLengthExams = parsed.fullLengthExams.filter { it.published }.map { it.copy(isShared = true, published = true) },
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        return CatalogImportDiff(manifestVersion = parsed.manifestVersion, newPublishedNotes = newNotes)
    }

    suspend fun setAllowStudentPersonalUploads(allowed: Boolean) {
        update { b ->
            b.copy(
                allowStudentPersonalUploads = allowed,
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun upsertSharedSubject(
        nameEn: String,
        nameHi: String?,
        iconKey: String,
        colorHex: String,
        sortOrder: Long,
    ) {
        val subj = Subject(
            id = "shared_subj_${StudyDateUtils.newValidId()}",
            userId = "admin",
            nameEn = nameEn.trim().take(80),
            nameHi = nameHi?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
            iconKey = iconKey.take(40),
            colorHex = colorHex.take(20),
            sortOrder = sortOrder,
            isShared = true,
            published = true,
        )
        update { b ->
            b.copy(
                subjects = b.subjects + subj,
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun deleteSharedSubject(subjectId: String) {
        update { b ->
            b.copy(
                subjects = b.subjects.filterNot { it.id == subjectId },
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun upsertSharedContent(note: StudyNote) {
        update { b ->
            b.copy(
                contents = b.contents.filterNot { it.id == note.id } + note.copy(isShared = true),
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun editSharedContent(
        noteId: String,
        title: String,
        folderName: String,
        isMockTest: Boolean,
        published: Boolean,
    ) {
        update { b ->
            b.copy(
                contents = b.contents.map { item ->
                    if (item.id == noteId) {
                        item.copy(
                            title = title.trim().take(160),
                            folderName = folderName.trim().take(80),
                            isMockTest = isMockTest,
                            published = published,
                            version = item.version + 1L,
                        )
                    } else item
                },
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun toggleSharedContentPublished(noteId: String, published: Boolean) {
        update { b ->
            b.copy(
                contents = b.contents.map { if (it.id == noteId) it.copy(published = published) else it },
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun deleteSharedContent(noteId: String) {
        update { b ->
            b.copy(
                contents = b.contents.filterNot { it.id == noteId },
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
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
        update { b ->
            b.copy(
                topics = b.topics + topic,
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun deleteSharedTopic(topicId: String) {
        update { b ->
            b.copy(
                topics = b.topics.filterNot { it.id == topicId },
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
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
        update { b ->
            b.copy(
                subtopics = b.subtopics + sub,
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun deleteSharedSubtopic(subtopicId: String) {
        update { b ->
            b.copy(
                subtopics = b.subtopics.filterNot { it.id == subtopicId },
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun upsertSharedFullLengthExam(name: String, subtitle: String?) {
        val exam = FullLengthExam(
            id = "shared_fl_${StudyDateUtils.newValidId()}",
            userId = "admin",
            name = name.trim().take(120),
            subtitle = subtitle?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            isShared = true,
            published = true,
        )
        update { b ->
            b.copy(
                fullLengthExams = b.fullLengthExams + exam,
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun deleteSharedVideo(videoId: String) {
        update { b -> b.copy(videos = b.videos.filterNot { it.id == videoId }, manifestVersion = b.manifestVersion + 1L) }
    }

    suspend fun deleteSharedMcq(mcqId: String) {
        update { b -> b.copy(mcqs = b.mcqs.filterNot { it.id == mcqId }, manifestVersion = b.manifestVersion + 1L) }
    }

    suspend fun deleteSharedFullLengthExam(examId: String) {
        update { b -> b.copy(fullLengthExams = b.fullLengthExams.filterNot { it.id == examId }, manifestVersion = b.manifestVersion + 1L) }
    }

    suspend fun submitStudentUploadForApproval(submission: StudentUploadSubmission) {
        val clean = submission.copy(
            id = submission.id.ifBlank { "subm_${StudyDateUtils.newValidId()}" },
            status = "PENDING_APPROVAL",
            submittedAtMillis = System.currentTimeMillis(),
        )
        update { b ->
            b.copy(
                pendingSubmissions = listOf(clean) + b.pendingSubmissions.filterNot { it.id == clean.id }
            )
        }
    }

    suspend fun approveStudentSubmission(submissionId: String): StudyNote? {
        val sub = bundle.pendingSubmissions.firstOrNull { it.id == submissionId } ?: return null
        val approvedNote = StudyNote(
            id = "shared_from_${sub.id}",
            userId = "admin",
            subjectId = sub.subjectId,
            topicId = sub.topicId,
            subtopicId = sub.subtopicId,
            folderName = sub.folderName.ifBlank { "Approved Student Contributions" },
            title = sub.title,
            noteType = sub.noteType,
            localFileName = sub.localFileName,
            htmlContent = sub.htmlContent,
            downloadUrl = sub.downloadUrl,
            isShared = true,
            published = true,
            version = 1L,
            fileSizeBytes = sub.fileSizeBytes,
            isMockTest = sub.isMockTest,
        )
        update { b ->
            b.copy(
                contents = b.contents.filterNot { it.id == approvedNote.id } + approvedNote,
                pendingSubmissions = b.pendingSubmissions.map {
                    if (it.id == submissionId) it.copy(status = "APPROVED") else it
                },
                manifestVersion = b.manifestVersion + 1L,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
        return approvedNote
    }

    suspend fun rejectStudentSubmission(submissionId: String) {
        update { b ->
            b.copy(
                pendingSubmissions = b.pendingSubmissions.map {
                    if (it.id == submissionId) it.copy(status = "REJECTED") else it
                }
            )
        }
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
            _manifestVersion.value = next.manifestVersion
            _allowStudentPersonalUploads.value = next.allowStudentPersonalUploads
            _subjects.value = next.subjects
            _topics.value = next.topics
            _subtopics.value = next.subtopics
            _contents.value = next.contents
            _videos.value = next.videos
            _mcqs.value = next.mcqs
            _fullLengthExams.value = next.fullLengthExams
            _pendingSubmissions.value = next.pendingSubmissions
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
