package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.OfflineVaultSnapshot
import com.example.data.model.BookmarkItem
import com.example.data.model.CloudPdfAnnotationDoc
import com.example.data.model.DefaultSubjectsSeed
import com.example.data.model.McqQuestion
import com.example.data.model.NoteType
import com.example.data.model.PersonalNote
import com.example.data.model.RevisionDiaryEntry
import com.example.data.model.StudyLog
import com.example.data.model.StudyNote
import com.example.data.model.Subtopic
import com.example.data.model.Subject
import com.example.data.model.TestResult
import com.example.data.model.Topic
import com.example.data.model.UserProfile
import com.example.data.model.VideoLink
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class StudyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUid: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == expectedUid) { "Signed-in user changed; refusing cross-user access." }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> observeCollection(
        uid: String,
        collectionName: String,
        clazz: Class<T>,
    ): Flow<List<T>> = flow {
        val verifiedUid = requireUserId(uid)
        val path = "users/$verifiedUid/$collectionName"
        emitAll(
            userDoc(verifiedUid).collection(collectionName)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == verifiedUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProfile(uid: String): Flow<UserProfile?> = flow {
        val verifiedUid = requireUserId(uid)
        val path = "users/$verifiedUid"
        emitAll(
            userDoc(verifiedUid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == verifiedUid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeSubjects(uid: String): Flow<List<Subject>> =
        observeCollection(uid, "subjects", Subject::class.java)
            .map { list -> list.sortedBy { it.sortOrder } }

    fun observeTopics(uid: String): Flow<List<Topic>> =
        observeCollection(uid, "topics", Topic::class.java)
            .map { list -> list.sortedBy { it.name.lowercase() } }

    fun observeSubtopics(uid: String): Flow<List<Subtopic>> =
        observeCollection(uid, "subtopics", Subtopic::class.java)
            .map { list -> list.sortedBy { it.name.lowercase() } }

    fun observeNotes(uid: String): Flow<List<StudyNote>> =
        observeCollection(uid, "notes", StudyNote::class.java)
            .map { list -> list.sortedByDescending { it.updatedAt?.seconds ?: 0L } }

    fun observeVideos(uid: String): Flow<List<VideoLink>> =
        observeCollection(uid, "videos", VideoLink::class.java)
            .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    fun observeMcqs(uid: String): Flow<List<McqQuestion>> =
        observeCollection(uid, "mcqs", McqQuestion::class.java)
            .map { list -> list.sortedBy { it.createdAt?.seconds ?: 0L } }

    fun observeTestResults(uid: String): Flow<List<TestResult>> =
        observeCollection(uid, "testResults", TestResult::class.java)
            .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    fun observeStudyLogs(uid: String): Flow<List<StudyLog>> =
        observeCollection(uid, "studyLogs", StudyLog::class.java)
            .map { list -> list.sortedByDescending { it.dateKey } }

    fun observeRevisionDiary(uid: String): Flow<List<RevisionDiaryEntry>> =
        observeCollection(uid, "revisionDiary", RevisionDiaryEntry::class.java)
            .map { list -> list.sortedByDescending { it.updatedAt?.seconds ?: 0L } }

    fun observePersonalNotes(uid: String): Flow<List<PersonalNote>> =
        observeCollection(uid, "personalNotes", PersonalNote::class.java)
            .map { list -> list.sortedByDescending { it.updatedAtMillis } }

    fun observePdfAnnotations(uid: String): Flow<List<CloudPdfAnnotationDoc>> =
        observeCollection(uid, "pdfAnnotations", CloudPdfAnnotationDoc::class.java)

    fun observeBookmarks(uid: String): Flow<List<BookmarkItem>> =
        observeCollection(uid, "bookmarks", BookmarkItem::class.java)
            .map { list -> list.sortedByDescending { it.createdAtMillis } }

    suspend fun ensureUserProfile(
        uid: String,
        displayName: String,
        email: String? = null,
        photoUrl: String? = null,
        role: String = "student",
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId(uid)
            val snap = userDoc(uid).get().await()
            val safeName = displayName.trim().ifBlank { "Learner" }.take(100)
            val safeEmail = email?.trim()?.takeIf { it.isNotBlank() }?.take(160)
            val safePhoto = photoUrl?.trim()?.takeIf { it.isNotBlank() }?.take(600)
            val safeRole = if (role == "admin") "admin" else "student"

            if (!snap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "role" to safeRole,
                    "themeId" to "bunts_royal_light",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                safeEmail?.let { payload["email"] = it }
                safePhoto?.let { payload["photoUrl"] = it }
                userDoc(uid).set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "role" to safeRole,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (snap.getString("displayName").isNullOrBlank()) updates["displayName"] = safeName
                safeEmail?.let { updates["email"] = it }
                safePhoto?.let { updates["photoUrl"] = it }
                userDoc(uid).update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTheme(uid: String, themeId: String): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId(uid)
            userDoc(uid).update(
                mapOf(
                    "themeId" to themeId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateExamGoal(uid: String, examName: String, examDateMillis: Long): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId(uid)
            val safeName = examName.trim().ifBlank { "Target Exam" }.take(100)
            require(examDateMillis >= 0L)
            userDoc(uid).update(
                mapOf(
                    "examName" to safeName,
                    "examDateMillis" to examDateMillis,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultSubjectsIfEmpty(uid: String): Result<Unit> {
        val path = "users/$uid/subjects"
        return try {
            requireUserId(uid)
            val existingSubjects = userDoc(uid).collection("subjects").get().await()
            val existingIds = existingSubjects.documents.map { it.id }.toSet()
            val missing = DefaultSubjectsSeed.all12Subjects.filter { it.id !in existingIds }
            if (missing.isEmpty()) return Result.success(Unit)

            val batch = db.batch()
            DefaultSubjectsSeed.all12Subjects.forEachIndexed { sIdx, s ->
                if (s.id !in existingIds) {
                    val sRef = userDoc(uid).collection("subjects").document(s.id)
                    batch.set(sRef, mapOf(
                        "userId" to uid,
                        "nameEn" to s.nameEn,
                        "nameHi" to s.nameHi,
                        "iconKey" to s.iconKey,
                        "colorHex" to s.colorHex,
                        "sortOrder" to s.sortOrder,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ))
                    s.topics.forEachIndexed { tIdx, (tName, subList) ->
                        val tId = "topic_${sIdx}_${tIdx}"
                        val tRef = userDoc(uid).collection("topics").document(tId)
                        batch.set(tRef, mapOf(
                            "userId" to uid,
                            "subjectId" to s.id,
                            "name" to tName,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ))
                        subList.forEachIndexed { subIdx, subName ->
                            val subId = "sub_${sIdx}_${tIdx}_${subIdx}"
                            val subRef = userDoc(uid).collection("subtopics").document(subId)
                            batch.set(subRef, mapOf(
                                "userId" to uid,
                                "subjectId" to s.id,
                                "topicId" to tId,
                                "name" to subName,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            ))
                        }
                    }
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addSubject(
        uid: String,
        nameEn: String,
        nameHi: String?,
        iconKey: String,
        colorHex: String,
        sortOrder: Long
    ): Result<String> {
        val docId = "subj_${newValidId()}"
        val path = "users/$uid/subjects/$docId"
        return try {
            requireUserId(uid)
            val safeEn = nameEn.trim().take(80)
            require(safeEn.isNotEmpty()) { "Subject name cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "nameEn" to safeEn,
                "nameHi" to nameHi?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
                "iconKey" to iconKey.take(40),
                "colorHex" to colorHex.take(20),
                "sortOrder" to sortOrder.coerceIn(0L, 1000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            userDoc(uid).collection("subjects").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addTopic(uid: String, subjectId: String, name: String, description: String?): Result<String> {
        val docId = "topic_${newValidId()}"
        val path = "users/$uid/topics/$docId"
        return try {
            requireUserId(uid)
            val safeName = name.trim().take(120)
            require(safeName.isNotEmpty()) { "Topic name cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subjectId,
                "name" to safeName,
                "description" to description?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            userDoc(uid).collection("topics").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTopic(uid: String, topicId: String): Result<Unit> {
        val path = "users/$uid/topics/$topicId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("topics").document(topicId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addSubtopic(uid: String, subjectId: String, topicId: String, name: String): Result<String> {
        val docId = "sub_${newValidId()}"
        val path = "users/$uid/subtopics/$docId"
        return try {
            requireUserId(uid)
            val safeName = name.trim().take(120)
            require(safeName.isNotEmpty()) { "Sub-topic name cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subjectId,
                "topicId" to topicId,
                "name" to safeName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            userDoc(uid).collection("subtopics").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markSubtopicStudiedToday(
        uid: String,
        subtopic: Subtopic,
        subjectName: String,
    ): Result<Unit> {
        val path = "users/$uid/subtopics/${subtopic.id}"
        return try {
            requireUserId(uid)
            val today = todayKey()
            userDoc(uid).collection("subtopics").document(subtopic.id).update(
                mapOf(
                    "lastStudiedDate" to today,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            addStudyLog(
                uid = uid,
                dateKey = today,
                entryType = "STUDIED",
                title = subtopic.name,
                subjectName = subjectName,
                subtopicId = subtopic.id,
                completed = true,
                notes = "Marked as studied"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubtopic(uid: String, subtopicId: String): Result<Unit> {
        val path = "users/$uid/subtopics/$subtopicId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("subtopics").document(subtopicId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addNote(
        uid: String,
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
    ): Result<String> {
        val path = "users/$uid/notes/$noteId"
        return try {
            requireUserId(uid)
            val safeTitle = title.trim().take(150)
            require(safeTitle.isNotEmpty()) { "Note title cannot be empty." }
            val safeHtml = htmlContent?.take(195_000)
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subjectId,
                "topicId" to topicId,
                "subtopicId" to subtopicId,
                "folderName" to folderName.trim().take(80),
                "title" to safeTitle,
                "noteType" to noteType.name,
                "localFileName" to localFileName?.take(200),
                "htmlContent" to safeHtml,
                "isMockTest" to isMockTest,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            userDoc(uid).collection("notes").document(noteId).set(payload).await()
            Result.success(noteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameOrMoveNote(uid: String, noteId: String, newTitle: String, newFolderName: String): Result<Unit> {
        val path = "users/$uid/notes/$noteId"
        return try {
            requireUserId(uid)
            val safeTitle = newTitle.trim().take(150)
            require(safeTitle.isNotEmpty()) { "Title cannot be empty." }
            userDoc(uid).collection("notes").document(noteId).update(
                mapOf(
                    "title" to safeTitle,
                    "folderName" to newFolderName.trim().take(80),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markNoteOpenedToday(uid: String, noteId: String): Result<Unit> {
        val path = "users/$uid/notes/$noteId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("notes").document(noteId).update(
                mapOf(
                    "lastOpenedDate" to todayKey(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(uid: String, noteId: String): Result<Unit> {
        val path = "users/$uid/notes/$noteId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("notes").document(noteId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addVideo(
        uid: String,
        subjectId: String,
        topicId: String,
        subtopicId: String,
        title: String,
        youtubeUrl: String,
        channelOrNote: String?,
    ): Result<String> {
        val docId = "vid_${newValidId()}"
        val path = "users/$uid/videos/$docId"
        return try {
            requireUserId(uid)
            val safeTitle = title.trim().take(150)
            val safeUrl = youtubeUrl.trim().take(500)
            require(safeTitle.isNotEmpty() && safeUrl.isNotEmpty()) { "Title and YouTube link are required." }
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subjectId,
                "topicId" to topicId,
                "subtopicId" to subtopicId,
                "title" to safeTitle,
                "youtubeUrl" to safeUrl,
                "channelOrNote" to channelOrNote?.trim()?.takeIf { it.isNotEmpty() }?.take(150),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            userDoc(uid).collection("videos").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markVideoWatchedToday(uid: String, videoId: String): Result<Unit> {
        val path = "users/$uid/videos/$videoId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("videos").document(videoId).update(
                mapOf(
                    "lastWatchedDate" to todayKey(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVideo(uid: String, videoId: String): Result<Unit> {
        val path = "users/$uid/videos/$videoId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("videos").document(videoId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addMcq(
        uid: String,
        subjectId: String,
        topicId: String,
        subtopicId: String,
        question: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String?,
    ): Result<String> {
        val docId = "mcq_${newValidId()}"
        val path = "users/$uid/mcqs/$docId"
        return try {
            requireUserId(uid)
            val cleanQuestion = question.trim().take(500)
            val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.take(6)
            require(cleanQuestion.isNotEmpty()) { "Question is required." }
            require(cleanOptions.size >= 2) { "At least 2 options are required." }
            val safeIndex = correctIndex.coerceIn(0, cleanOptions.lastIndex)
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subjectId,
                "topicId" to topicId,
                "subtopicId" to subtopicId,
                "question" to cleanQuestion,
                "options" to cleanOptions,
                "correctIndex" to safeIndex.toLong(),
                "explanation" to explanation?.trim()?.takeIf { it.isNotEmpty() }?.take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            userDoc(uid).collection("mcqs").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMcq(uid: String, mcqId: String): Result<Unit> {
        val path = "users/$uid/mcqs/$mcqId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("mcqs").document(mcqId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun recordTestResult(
        uid: String,
        subjectId: String,
        topicId: String,
        subtopicId: String,
        noteId: String?,
        testTitle: String,
        scoreObtained: Double,
        totalMarks: Double,
        summaryText: String?,
        totalQuestions: Int = 0,
        attemptedCount: Int = 0,
        correctCount: Int = 0,
        wrongCount: Int = 0,
        unattemptedCount: Int = 0,
        durationSeconds: Long = 0L,
        testMode: String = "EXAM",
        breakdownJson: String? = null,
        docId: String = "res_${newValidId()}",
    ): Result<String> {
        val path = "users/$uid/testResults/$docId"
        return try {
            requireUserId(uid)
            val safeObtained = scoreObtained.coerceAtLeast(0.0)
            val safeTotal = totalMarks.coerceAtLeast(0.0)
            val pct = if (safeTotal > 0.0) ((safeObtained / safeTotal) * 100.0).coerceIn(0.0, 100.0) else 0.0
            val safeMode = if (testMode == "PRACTICE") "PRACTICE" else "EXAM"
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to subjectId,
                "topicId" to topicId,
                "subtopicId" to subtopicId,
                "noteId" to noteId?.takeIf { it.isNotBlank() }?.take(128),
                "testTitle" to testTitle.trim().ifBlank { "Mock Test" }.take(150),
                "scoreObtained" to safeObtained,
                "totalMarks" to safeTotal,
                "percentage" to pct,
                "totalQuestions" to totalQuestions.coerceAtLeast(0),
                "attemptedCount" to attemptedCount.coerceAtLeast(0),
                "correctCount" to correctCount.coerceAtLeast(0),
                "wrongCount" to wrongCount.coerceAtLeast(0),
                "unattemptedCount" to unattemptedCount.coerceAtLeast(0),
                "durationSeconds" to durationSeconds.coerceAtLeast(0L),
                "testMode" to safeMode,
                "breakdownJson" to breakdownJson?.take(340_000),
                "summaryText" to summaryText?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "takenDate" to todayKey(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            userDoc(uid).collection("testResults").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addStudyLog(
        uid: String,
        dateKey: String,
        entryType: String,
        title: String,
        subjectName: String? = null,
        subtopicId: String? = null,
        completed: Boolean = false,
        notes: String? = null,
    ): Result<String> {
        val docId = "log_${newValidId()}"
        val path = "users/$uid/studyLogs/$docId"
        return try {
            requireUserId(uid)
            val safeTitle = title.trim().take(150)
            require(safeTitle.isNotEmpty()) { "Plan title cannot be empty." }
            val safeType = if (entryType == "STUDIED") "STUDIED" else "PLANNED"
            val payload = mapOf(
                "userId" to uid,
                "dateKey" to dateKey.take(20),
                "entryType" to safeType,
                "title" to safeTitle,
                "subjectName" to subjectName?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
                "subtopicId" to subtopicId?.takeIf { it.isNotBlank() }?.take(128),
                "completed" to completed,
                "notes" to notes?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            userDoc(uid).collection("studyLogs").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleStudyLogCompleted(uid: String, logId: String, completed: Boolean): Result<Unit> {
        val path = "users/$uid/studyLogs/$logId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("studyLogs").document(logId).update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStudyLog(uid: String, logId: String): Result<Unit> {
        val path = "users/$uid/studyLogs/$logId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("studyLogs").document(logId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun generateExamRevisionPlan(
        uid: String,
        subjects: List<Subject>,
        examDateMillis: Long,
    ): Result<Int> {
        val path = "users/$uid/studyLogs"
        return try {
            requireUserId(uid)
            if (subjects.isEmpty()) return Result.success(0)
            val now = System.currentTimeMillis()
            val daysUntil = ((examDateMillis - now) / (1000L * 60L * 60L * 24L)).toInt().coerceIn(1, 14)
            val planDays = daysUntil.coerceAtMost(7)
            val batch = db.batch()
            val cal = Calendar.getInstance()
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            for (dayOffset in 0 until planDays) {
                cal.timeInMillis = now
                cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                val dateKey = fmt.format(cal.time)
                val subject = subjects[dayOffset % subjects.size]
                val docId = "plan_${dateKey}_${subject.id}_${newValidId().take(6)}"
                val ref = userDoc(uid).collection("studyLogs").document(docId)
                batch.set(ref, mapOf(
                    "userId" to uid,
                    "dateKey" to dateKey,
                    "entryType" to "PLANNED",
                    "title" to "${subject.nameEn} Revision & Mock Test",
                    "subjectName" to subject.nameEn,
                    "completed" to false,
                    "notes" to "Auto-scheduled for upcoming exam",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ))
            }
            batch.commit().await()
            Result.success(planDays)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addRevisionDiaryEntry(
        uid: String,
        dateKey: String,
        title: String,
        subjectName: String?,
        sourceName: String,
        content: String,
        pdfTitle: String? = null,
        pdfLocalFileName: String? = null,
        docId: String = "diary_${newValidId()}",
    ): Result<String> {
        val path = "users/$uid/revisionDiary/$docId"
        return try {
            requireUserId(uid)
            val safeTitle = title.trim().take(150)
            require(safeTitle.isNotEmpty()) { "Title cannot be empty." }
            val payload = mapOf(
                "userId" to uid,
                "dateKey" to dateKey.take(20),
                "title" to safeTitle,
                "subjectName" to subjectName?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
                "sourceName" to sourceName.trim().ifBlank { "Self Study" }.take(150),
                "content" to content.trim().take(5000),
                "pdfTitle" to pdfTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(150),
                "pdfLocalFileName" to pdfLocalFileName?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null } as Map<String, Any>
            userDoc(uid).collection("revisionDiary").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateRevisionDiaryEntry(
        uid: String,
        entryId: String,
        title: String,
        subjectName: String?,
        sourceName: String,
        content: String,
        pdfTitle: String? = null,
        pdfLocalFileName: String? = null,
    ): Result<Unit> {
        val path = "users/$uid/revisionDiary/$entryId"
        return try {
            requireUserId(uid)
            val safeTitle = title.trim().take(150)
            require(safeTitle.isNotEmpty()) { "Title cannot be empty." }
            val payload = mutableMapOf<String, Any>(
                "title" to safeTitle,
                "sourceName" to sourceName.trim().ifBlank { "Self Study" }.take(150),
                "content" to content.trim().take(5000),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            subjectName?.trim()?.takeIf { it.isNotEmpty() }?.take(80)?.let {
                payload["subjectName"] = it
            }
            pdfTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(150)?.let {
                payload["pdfTitle"] = it
            }
            pdfLocalFileName?.trim()?.takeIf { it.isNotEmpty() }?.take(200)?.let {
                payload["pdfLocalFileName"] = it
            }
            userDoc(uid).collection("revisionDiary").document(entryId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteRevisionDiaryEntry(uid: String, entryId: String): Result<Unit> {
        val path = "users/$uid/revisionDiary/$entryId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("revisionDiary").document(entryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun savePersonalNote(uid: String, note: PersonalNote): Result<String> {
        val docId = note.id.ifBlank { "pnote_${newValidId()}" }
        val path = "users/$uid/personalNotes/$docId"
        return try {
            requireUserId(uid)
            val ref = userDoc(uid).collection("personalNotes").document(docId)
            val existing = ref.get().await()
            val updatedMillis = note.updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "subjectId" to note.subjectId.take(128),
                    "subjectName" to note.subjectName.take(100),
                    "topicName" to note.topicName.take(120),
                    "title" to note.title.trim().ifBlank { "Untitled Note" }.take(160),
                    "contentSummary" to note.contentSummary.take(500),
                    "blocksJson" to note.blocksJson.take(340_000),
                    "updatedAtMillis" to updatedMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                ref.set(payload).await()
            } else {
                ref.update(
                    mapOf(
                        "subjectId" to note.subjectId.take(128),
                        "subjectName" to note.subjectName.take(100),
                        "topicName" to note.topicName.take(120),
                        "title" to note.title.trim().ifBlank { "Untitled Note" }.take(160),
                        "contentSummary" to note.contentSummary.take(500),
                        "blocksJson" to note.blocksJson.take(340_000),
                        "updatedAtMillis" to updatedMillis,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePersonalNote(uid: String, noteId: String): Result<Unit> {
        val path = "users/$uid/personalNotes/$noteId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("personalNotes").document(noteId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun savePdfAnnotationDoc(uid: String, noteId: String, annotationsJson: String, updatedAtMillis: Long): Result<Unit> {
        val safeDocId = noteId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(128)
        val path = "users/$uid/pdfAnnotations/$safeDocId"
        return try {
            requireUserId(uid)
            val ref = userDoc(uid).collection("pdfAnnotations").document(safeDocId)
            val existing = ref.get().await()
            if (!existing.exists()) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "noteId" to safeDocId,
                        "annotationsJson" to annotationsJson.take(580_000),
                        "updatedAtMillis" to updatedAtMillis.coerceAtLeast(0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "annotationsJson" to annotationsJson.take(580_000),
                        "updatedAtMillis" to updatedAtMillis.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveBookmark(uid: String, item: BookmarkItem): Result<String> {
        val docId = item.id.ifBlank { "bmk_${newValidId()}" }
        val path = "users/$uid/bookmarks/$docId"
        return try {
            requireUserId(uid)
            val createdMillis: Long = if (item.createdAtMillis > 0L) item.createdAtMillis else System.currentTimeMillis()
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "bookmarkType" to item.bookmarkType,
                "title" to item.title.trim().ifBlank { "Bookmark" }.take(200),
                "subtitle" to item.subtitle.take(300),
                "refId" to item.refId.take(128),
                "subjectId" to item.subjectId.take(128),
                "topicId" to item.topicId.take(128),
                "subtopicId" to item.subtopicId.take(128),
                "pageIndex" to item.pageIndex.coerceAtLeast(0),
                "createdAtMillis" to createdMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            item.questionJson?.take(9800)?.let { payload["questionJson"] = it }
            userDoc(uid).collection("bookmarks").document(docId).set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBookmark(uid: String, bookmarkId: String): Result<Unit> {
        val path = "users/$uid/bookmarks/$bookmarkId"
        return try {
            requireUserId(uid)
            userDoc(uid).collection("bookmarks").document(bookmarkId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun syncOfflineSnapshotToCloud(uid: String, snapshot: OfflineVaultSnapshot): Result<Unit> {
        val path = "users/$uid"
        return try {
            requireUserId(uid)
            val writes = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

            snapshot.subjects.forEach { s ->
                writes += userDoc(uid).collection("subjects").document(s.id) to mapOf(
                    "userId" to uid,
                    "nameEn" to s.nameEn.take(80),
                    "nameHi" to s.nameHi?.take(80),
                    "iconKey" to s.iconKey.take(40),
                    "colorHex" to s.colorHex.take(20),
                    "sortOrder" to s.sortOrder,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.topics.forEach { t ->
                writes += userDoc(uid).collection("topics").document(t.id) to mapOf(
                    "userId" to uid,
                    "subjectId" to t.subjectId,
                    "name" to t.name.take(120),
                    "description" to t.description?.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.subtopics.forEach { sub ->
                writes += userDoc(uid).collection("subtopics").document(sub.id) to mapOf(
                    "userId" to uid,
                    "subjectId" to sub.subjectId,
                    "topicId" to sub.topicId,
                    "name" to sub.name.take(120),
                    "lastStudiedDate" to sub.lastStudiedDate,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.notes.forEach { n ->
                writes += userDoc(uid).collection("notes").document(n.id) to mapOf(
                    "userId" to uid,
                    "subjectId" to n.subjectId,
                    "topicId" to n.topicId,
                    "subtopicId" to n.subtopicId,
                    "folderName" to n.folderName.take(80),
                    "title" to n.title.take(150),
                    "noteType" to n.noteType,
                    "localFileName" to n.localFileName?.take(200),
                    "htmlContent" to n.htmlContent?.take(195_000),
                    "lastOpenedDate" to n.lastOpenedDate,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.videos.forEach { v ->
                writes += userDoc(uid).collection("videos").document(v.id) to mapOf(
                    "userId" to uid,
                    "subjectId" to v.subjectId,
                    "topicId" to v.topicId,
                    "subtopicId" to v.subtopicId,
                    "title" to v.title.take(150),
                    "youtubeUrl" to v.youtubeUrl.take(500),
                    "channelOrNote" to v.channelOrNote?.take(150),
                    "lastWatchedDate" to v.lastWatchedDate,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.mcqs.forEach { m ->
                writes += userDoc(uid).collection("mcqs").document(m.id) to mapOf(
                    "userId" to uid,
                    "subjectId" to m.subjectId,
                    "topicId" to m.topicId,
                    "subtopicId" to m.subtopicId,
                    "question" to m.question.take(500),
                    "options" to m.options.take(6),
                    "correctIndex" to m.correctIndex,
                    "explanation" to m.explanation?.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.testResults.forEach { r ->
                writes += userDoc(uid).collection("testResults").document(r.id) to mapOf(
                    "userId" to uid,
                    "subjectId" to r.subjectId,
                    "topicId" to r.topicId,
                    "subtopicId" to r.subtopicId,
                    "noteId" to r.noteId?.take(128),
                    "testTitle" to r.testTitle.take(150),
                    "scoreObtained" to r.scoreObtained,
                    "totalMarks" to r.totalMarks,
                    "percentage" to r.percentage,
                    "totalQuestions" to r.totalQuestions,
                    "attemptedCount" to r.attemptedCount,
                    "correctCount" to r.correctCount,
                    "wrongCount" to r.wrongCount,
                    "unattemptedCount" to r.unattemptedCount,
                    "durationSeconds" to r.durationSeconds,
                    "testMode" to r.testMode,
                    "breakdownJson" to r.breakdownJson?.take(340_000),
                    "summaryText" to r.summaryText?.take(300),
                    "takenDate" to r.takenDate.take(20),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.studyLogs.forEach { l ->
                writes += userDoc(uid).collection("studyLogs").document(l.id) to mapOf(
                    "userId" to uid,
                    "dateKey" to l.dateKey.take(20),
                    "entryType" to l.entryType,
                    "title" to l.title.take(150),
                    "subjectName" to l.subjectName?.take(80),
                    "subtopicId" to l.subtopicId?.take(128),
                    "completed" to l.completed,
                    "notes" to l.notes?.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.revisionDiary.forEach { d ->
                writes += userDoc(uid).collection("revisionDiary").document(d.id) to mapOf(
                    "userId" to uid,
                    "dateKey" to d.dateKey.take(20),
                    "title" to d.title.take(150),
                    "subjectName" to d.subjectName?.take(80),
                    "sourceName" to d.sourceName.ifBlank { "Self Study" }.take(150),
                    "content" to d.content.take(5000),
                    "pdfTitle" to d.pdfTitle?.take(150),
                    "pdfLocalFileName" to d.pdfLocalFileName?.take(200),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.personalNotes.forEach { p ->
                writes += userDoc(uid).collection("personalNotes").document(p.id) to mapOf(
                    "userId" to uid,
                    "subjectId" to p.subjectId.take(128),
                    "subjectName" to p.subjectName.take(100),
                    "topicName" to p.topicName.take(120),
                    "title" to p.title.take(160),
                    "contentSummary" to p.contentSummary.take(500),
                    "blocksJson" to p.blocksJson.take(340_000),
                    "updatedAtMillis" to p.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            }
            snapshot.bookmarks.forEach { b ->
                writes += userDoc(uid).collection("bookmarks").document(b.id) to mapOf(
                    "userId" to uid,
                    "bookmarkType" to b.bookmarkType,
                    "title" to b.title.take(200),
                    "subtitle" to b.subtitle.take(300),
                    "refId" to b.refId.take(128),
                    "subjectId" to b.subjectId.take(128),
                    "topicId" to b.topicId.take(128),
                    "subtopicId" to b.subtopicId.take(128),
                    "pageIndex" to b.pageIndex,
                    "questionJson" to b.questionJson?.take(9800),
                    "createdAtMillis" to b.createdAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
            }
            snapshot.pdfAnnotations.forEach { a ->
                val safeId = a.noteId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(128)
                writes += userDoc(uid).collection("pdfAnnotations").document(safeId) to mapOf(
                    "userId" to uid,
                    "noteId" to safeId,
                    "annotationsJson" to a.annotationsJson.take(580_000),
                    "updatedAtMillis" to a.updatedAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            }

            writes.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (ref, payload) -> batch.set(ref, payload, SetOptions.merge()) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchSupportWhatsAppDigits(): String? {
        return try {
            val snap = db.collection("appConfig").document("support").get().await()
            snap.getString("whatsappDigits")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateSupportWhatsAppDigits(digits: String): Result<Unit> {
        val path = "appConfig/support"
        return try {
            val payload = mapOf(
                "whatsappDigits" to digits.take(20),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            db.collection("appConfig").document("support").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    companion object {
        fun newValidId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

        fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        fun yesterdayKey(): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }

        fun tomorrowKey(): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }
    }
}
