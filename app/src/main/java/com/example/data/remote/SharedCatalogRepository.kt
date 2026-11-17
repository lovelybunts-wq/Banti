package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.FullLengthExam
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.StudentSummary
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.VideoLink
import com.example.data.model.WhatsAppRequestRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class SharedCatalogRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun <T> observeShared(collection: String, clazz: Class<T>): Flow<List<T>> = flow {
        if (auth.currentUser == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection(collection)
                .snapshots()
                .map { snap -> snap.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    fun observeSharedSubjects(includeUnpublished: Boolean): Flow<List<Subject>> =
        observeShared("sharedSubjects", Subject::class.java).map { list ->
            list.filter { includeUnpublished || it.published }
                .map { it.copy(isShared = true) }
                .sortedBy { it.sortOrder }
        }

    fun observeSharedTopics(includeUnpublished: Boolean): Flow<List<Topic>> =
        observeShared("sharedTopics", Topic::class.java).map { list ->
            list.filter { includeUnpublished || it.published }
                .map { it.copy(isShared = true) }
                .sortedBy { it.name.lowercase() }
        }

    fun observeSharedSubtopics(includeUnpublished: Boolean): Flow<List<Subtopic>> =
        observeShared("sharedSubtopics", Subtopic::class.java).map { list ->
            list.filter { includeUnpublished || it.published }
                .map { it.copy(isShared = true) }
                .sortedBy { it.name.lowercase() }
        }

    fun observeSharedContent(includeUnpublished: Boolean): Flow<List<StudyNote>> =
        observeShared("sharedContent", StudyNote::class.java).map { list ->
            list.filter { includeUnpublished || it.published }
                .map { it.copy(isShared = true) }
                .sortedByDescending { it.updatedAt?.seconds ?: 0L }
        }

    fun observeSharedVideos(includeUnpublished: Boolean): Flow<List<VideoLink>> =
        observeShared("sharedVideos", VideoLink::class.java).map { list ->
            list.filter { includeUnpublished || it.published }
                .map { it.copy(isShared = true) }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    fun observeSharedMcqs(includeUnpublished: Boolean): Flow<List<McqQuestion>> =
        observeShared("sharedMcqs", McqQuestion::class.java).map { list ->
            list.filter { includeUnpublished || it.published }
                .map { it.copy(isShared = true) }
                .sortedBy { it.createdAt?.seconds ?: 0L }
        }

    fun observeSharedFullLengthExams(includeUnpublished: Boolean): Flow<List<FullLengthExam>> =
        observeShared("sharedFullLengthExams", FullLengthExam::class.java).map { list ->
            list.filter { includeUnpublished || it.published }
                .map { it.copy(isShared = true) }
                .sortedBy { it.createdAt?.seconds ?: 0L }
        }

    fun observeAllWhatsAppRequests(): Flow<List<WhatsAppRequestRecord>> =
        observeShared("whatsappRequests", WhatsAppRequestRecord::class.java).map { list ->
            list.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    fun observeStudentSummaries(): Flow<List<StudentSummary>> =
        observeShared("studentSummaries", StudentSummary::class.java).map { list ->
            list.sortedByDescending { it.lastActiveMillis }
        }

    suspend fun upsertSharedSubject(subject: Subject): Result<Unit> = runCatching {
        val docId = subject.id.ifBlank { "shared_subj_${StudyRepository.newValidId()}" }
        db.collection("sharedSubjects").document(docId).set(
            mapOf(
                "userId" to (auth.currentUser?.uid ?: "admin"),
                "nameEn" to subject.nameEn.trim().take(80),
                "nameHi" to subject.nameHi?.trim()?.take(80),
                "iconKey" to subject.iconKey.take(40),
                "colorHex" to subject.colorHex.take(20),
                "sortOrder" to subject.sortOrder,
                "isShared" to true,
                "published" to subject.published,
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null },
            SetOptions.merge()
        ).await()
    }

    suspend fun deleteSharedSubject(subjectId: String): Result<Unit> = runCatching {
        db.collection("sharedSubjects").document(subjectId).delete().await()
    }

    suspend fun upsertSharedTopic(topic: Topic): Result<String> = runCatching {
        val docId = topic.id.ifBlank { "shared_topic_${StudyRepository.newValidId()}" }
        db.collection("sharedTopics").document(docId).set(
            mapOf(
                "userId" to (auth.currentUser?.uid ?: "admin"),
                "subjectId" to topic.subjectId,
                "name" to topic.name.trim().take(120),
                "description" to topic.description?.trim()?.take(300),
                "isShared" to true,
                "published" to topic.published,
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null },
            SetOptions.merge()
        ).await()
        docId
    }

    suspend fun deleteSharedTopic(topicId: String): Result<Unit> = runCatching {
        db.collection("sharedTopics").document(topicId).delete().await()
    }

    suspend fun upsertSharedSubtopic(subtopic: Subtopic): Result<String> = runCatching {
        val docId = subtopic.id.ifBlank { "shared_sub_${StudyRepository.newValidId()}" }
        db.collection("sharedSubtopics").document(docId).set(
            mapOf(
                "userId" to (auth.currentUser?.uid ?: "admin"),
                "subjectId" to subtopic.subjectId,
                "topicId" to subtopic.topicId,
                "name" to subtopic.name.trim().take(120),
                "isShared" to true,
                "published" to subtopic.published,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge()
        ).await()
        docId
    }

    suspend fun deleteSharedSubtopic(subtopicId: String): Result<Unit> = runCatching {
        db.collection("sharedSubtopics").document(subtopicId).delete().await()
    }

    suspend fun upsertSharedContent(note: StudyNote, isNew: Boolean): Result<String> = runCatching {
        val docId = note.id.ifBlank { "shared_doc_${StudyRepository.newValidId()}" }
        val payload = mutableMapOf<String, Any>(
            "userId" to (auth.currentUser?.uid ?: "admin"),
            "subjectId" to note.subjectId,
            "topicId" to note.topicId,
            "subtopicId" to note.subtopicId,
            "folderName" to note.folderName.trim().take(80),
            "title" to note.title.trim().take(150),
            "noteType" to NoteType.fromRaw(note.noteType).name,
            "isShared" to true,
            "version" to note.version.coerceAtLeast(1L),
            "published" to note.published,
            "downloadsCount" to note.downloadsCount.coerceAtLeast(0L),
            "fileSizeBytes" to note.fileSizeBytes.coerceAtLeast(0L),
            "isMockTest" to note.isMockTest,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        note.localFileName?.let { payload["localFileName"] = it.take(200) }
        note.htmlContent?.take(195_000)?.let { payload["htmlContent"] = it }
        note.storagePath?.let { payload["storagePath"] = it.take(300) }
        note.downloadUrl?.let { payload["downloadUrl"] = it.take(1000) }
        if (isNew) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            db.collection("sharedContent").document(docId).set(payload).await()
        } else {
            db.collection("sharedContent").document(docId).set(payload, SetOptions.merge()).await()
        }
        docId
    }

    suspend fun toggleSharedContentPublished(contentId: String, title: String, published: Boolean): Result<Unit> =
        runCatching {
            db.collection("sharedContent").document(contentId).update(
                mapOf(
                    "title" to title,
                    "published" to published,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }

    suspend fun deleteSharedContent(contentId: String): Result<Unit> = runCatching {
        db.collection("sharedContent").document(contentId).delete().await()
    }

    suspend fun incrementSharedDownloadCount(contentId: String) {
        runCatching {
            db.collection("sharedContent").document(contentId).update(
                mapOf(
                    "downloadsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }
    }

    suspend fun upsertSharedVideo(video: VideoLink): Result<String> = runCatching {
        val docId = video.id.ifBlank { "shared_vid_${StudyRepository.newValidId()}" }
        db.collection("sharedVideos").document(docId).set(
            mapOf(
                "userId" to (auth.currentUser?.uid ?: "admin"),
                "subjectId" to video.subjectId,
                "topicId" to video.topicId,
                "subtopicId" to video.subtopicId,
                "title" to video.title.trim().take(150),
                "youtubeUrl" to video.youtubeUrl.trim().take(500),
                "channelOrNote" to video.channelOrNote?.trim()?.take(150),
                "isShared" to true,
                "published" to video.published,
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null },
            SetOptions.merge()
        ).await()
        docId
    }

    suspend fun deleteSharedVideo(videoId: String): Result<Unit> = runCatching {
        db.collection("sharedVideos").document(videoId).delete().await()
    }

    suspend fun upsertSharedMcq(mcq: McqQuestion): Result<String> = runCatching {
        val docId = mcq.id.ifBlank { "shared_mcq_${StudyRepository.newValidId()}" }
        val cleanOpts = mcq.options.map { it.trim() }.filter { it.isNotEmpty() }.take(6)
        db.collection("sharedMcqs").document(docId).set(
            mapOf(
                "userId" to (auth.currentUser?.uid ?: "admin"),
                "subjectId" to mcq.subjectId,
                "topicId" to mcq.topicId,
                "subtopicId" to mcq.subtopicId,
                "question" to mcq.question.trim().take(500),
                "options" to cleanOpts,
                "correctIndex" to mcq.correctIndex.coerceIn(0L, (cleanOpts.lastIndex).coerceAtLeast(0).toLong()),
                "explanation" to mcq.explanation?.trim()?.take(500),
                "isShared" to true,
                "published" to mcq.published,
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null },
            SetOptions.merge()
        ).await()
        docId
    }

    suspend fun deleteSharedMcq(mcqId: String): Result<Unit> = runCatching {
        db.collection("sharedMcqs").document(mcqId).delete().await()
    }

    suspend fun upsertSharedFullLengthExam(exam: FullLengthExam): Result<String> = runCatching {
        val docId = exam.id.ifBlank { "shared_fl_${StudyRepository.newValidId()}" }
        db.collection("sharedFullLengthExams").document(docId).set(
            mapOf(
                "userId" to (auth.currentUser?.uid ?: "admin"),
                "name" to exam.name.trim().take(120),
                "subtitle" to exam.subtitle?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                "isShared" to true,
                "published" to exam.published,
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null },
            SetOptions.merge()
        ).await()
        docId
    }

    suspend fun deleteSharedFullLengthExam(examId: String): Result<Unit> = runCatching {
        db.collection("sharedFullLengthExams").document(examId).delete().await()
    }

    suspend fun upsertStudentSummary(
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String?,
        downloadsCount: Long,
        testAttemptsCount: Long,
        averageScorePct: Double,
    ) {
        runCatching {
            if (auth.currentUser?.uid != uid) return@runCatching
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to displayName.trim().ifBlank { "Student" }.take(100),
                "email" to email.trim().take(160),
                "downloadsCount" to downloadsCount.coerceAtLeast(0L),
                "testAttemptsCount" to testAttemptsCount.coerceAtLeast(0L),
                "averageScorePct" to averageScorePct.coerceIn(0.0, 100.0),
                "lastActiveMillis" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            photoUrl?.takeIf { it.isNotBlank() }?.let { payload["photoUrl"] = it.take(600) }
            db.collection("studentSummaries").document(uid).set(payload, SetOptions.merge()).await()
        }
    }

    suspend fun fetchStudentTestResultsForAdmin(studentUid: String): Result<List<TestResult>> = runCatching {
        db.collection("users").document(studentUid).collection("testResults").get().await()
            .toObjects(TestResult::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    suspend fun submitWhatsAppRequest(record: WhatsAppRequestRecord): Result<String> = runCatching {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Please sign in to submit request.")
        val docId = record.id.ifBlank { "req_${StudyRepository.newValidId()}" }
        db.collection("whatsappRequests").document(docId).set(
            mapOf(
                "userId" to uid,
                "studentName" to record.studentName.trim().ifBlank { "Student" }.take(100),
                "studentEmail" to record.studentEmail.trim().take(160),
                "requestType" to record.requestType.trim().ifBlank { "Notes / Test Request" }.take(80),
                "subject" to record.subject.trim().ifBlank { "General" }.take(100),
                "topic" to record.topic.trim().take(120),
                "subtopic" to record.subtopic.trim().take(120),
                "extraMessage" to record.extraMessage.trim().take(500),
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        docId
    }

    suspend fun updateWhatsAppRequestStatus(requestId: String, status: String): Result<Unit> = runCatching {
        val safeStatus = if (status in listOf("PENDING", "FULFILLED", "REPLIED")) status else "PENDING"
        db.collection("whatsappRequests").document(requestId).update(
            mapOf(
                "status" to safeStatus,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }
}
