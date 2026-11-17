package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.GoogleAuthHelper
import com.example.data.local.LocalAdminCatalogStore
import com.example.data.local.LocalAppSettings
import com.example.data.local.LocalFileStorage
import com.example.data.local.LocalOfflineStore
import com.example.data.local.PdfExportHelper
import com.example.data.remote.GoogleDriveBackupManager
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.DefaultSubjectsSeed
import com.example.data.model.FullLengthExam
import com.example.data.model.FullLengthExamDefaults
import com.example.data.model.InProgressTestAttempt
import com.example.data.model.NormalizedPoint
import com.example.data.model.PdfAnnotationDocument
import com.example.data.model.PdfInkStroke
import com.example.data.model.PdfPageAnnotation
import com.example.data.model.PdfTextAnnotation
import com.example.data.model.PdfToolType
import com.example.data.model.PersonalNote
import com.example.data.model.PersonalNoteBlock
import com.example.data.model.PersonalNoteBlockType
import com.example.data.model.QuestionAttemptRecord
import com.example.data.model.StudentUploadSubmission
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.TestResult
import com.example.ui.SharedCatalogMergeHelper
import com.example.ui.SharedCatalogMergeHelper.toSharedContents
import com.example.ui.SharedCatalogMergeHelper.toSharedSubjects
import com.example.ui.components.buildWhatsAppRequestMessage
import com.example.ui.screens.HtmlTestJsHelper
import com.example.ui.screens.isNearAnnotationPoint
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlatformFeaturesUnitTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `default seed contains all 12 subjects including MP GK Computer Current Affairs and GS`() {
        val seeds = DefaultSubjectsSeed.all12Subjects
        assertEquals(12, seeds.size)
        val names = seeds.map { it.nameEn }.toSet()
        assertTrue(names.containsAll(listOf("History", "Polity", "Geography", "English", "Maths", "Science", "Reasoning", "Computer", "Current Affairs", "MP GK", "General Studies", "Other Subjects")))
        assertTrue(seeds.all { it.topics.isNotEmpty() && it.topics.all { t -> t.second.isNotEmpty() } })
    }

    @Test
    fun `offline store preserves custom subjects while ensuring 12 seeded subjects`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalOfflineStore(context, ownerId = "migration_test_user")
        store.addSubject("Ethics Paper 4", "नीतिशास्त्र", "book", "#2456A4", 12L)

        val allSubjects = store.subjects.value
        assertEquals(13, allSubjects.size)
        assertTrue(allSubjects.any { it.nameEn == "Ethics Paper 4" })
        assertTrue(allSubjects.any { it.id == "subj_mp_gk" })
    }

    @Test
    fun `pdf annotation document round trips and eraser geometry detects nearby strokes`() {
        val stroke = PdfInkStroke(
            id = "s1",
            tool = PdfToolType.PEN.name,
            colorArgb = 0xFFEF4444L,
            strokeWidthNorm = 0.008f,
            opacity = 1f,
            points = listOf(NormalizedPoint(0.2f, 0.3f), NormalizedPoint(0.4f, 0.3f))
        )
        val doc = PdfAnnotationDocument(
            noteId = "note_1",
            ownerUid = "uid_123",
            pages = mapOf(
                0 to PdfPageAnnotation(
                    pageIndex = 0,
                    strokes = listOf(stroke),
                    textBoxes = listOf(PdfTextAnnotation(id = "tb1", xNorm = 0.5f, yNorm = 0.6f, text = "IMP Article 21"))
                )
            ),
            updatedAtMillis = 1700000000L
        )
        val encoded = json.encodeToString(doc)
        val decoded = json.decodeFromString<PdfAnnotationDocument>(encoded)
        assertEquals("note_1", decoded.noteId)
        assertEquals(1, decoded.pages[0]?.strokes?.size)
        assertEquals("IMP Article 21", decoded.pages[0]?.textBoxes?.firstOrNull()?.text)

        assertTrue(isNearAnnotationPoint(stroke, NormalizedPoint(0.21f, 0.31f)))
        assertFalse(isNearAnnotationPoint(stroke, NormalizedPoint(0.85f, 0.85f)))
    }

    @Test
    fun `personal note blocks round trip and storage export dir initializes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val blocks = listOf(
            PersonalNoteBlock(id = "b1", type = PersonalNoteBlockType.HEADING.name, text = "Fundamental Rights"),
            PersonalNoteBlock(id = "b2", type = PersonalNoteBlockType.HIGHLIGHT.name, text = "Article 14-18: Right to Equality"),
            PersonalNoteBlock(id = "b3", type = PersonalNoteBlockType.CHECKLIST.name, text = "Revise Writ Jurisdiction", checked = true),
            PersonalNoteBlock(id = "b4", type = PersonalNoteBlockType.TABLE_ROW.name, tableCells = listOf("Art 32", "Supreme Court", "5 Writs"))
        )
        val blocksJson = json.encodeToString(blocks)
        val decodedBlocks = json.decodeFromString<List<PersonalNoteBlock>>(blocksJson)
        assertEquals(4, decodedBlocks.size)
        assertTrue(decodedBlocks[2].checked)
        assertEquals("5 Writs", decodedBlocks[3].tableCells.last())

        val note = PersonalNote(
            id = "pn_1",
            userId = "uid_1",
            subjectId = "subj_polity",
            subjectName = "Polity",
            topicName = "Constitution",
            title = "Polity Quick Revision",
            contentSummary = "Fundamental Rights & Writs",
            blocksJson = blocksJson,
            updatedAtMillis = 1700000000L
        )
        assertEquals("Polity Quick Revision", note.title)

        val storage = LocalFileStorage(context)
        assertTrue(storage.exportsDir("uid_1").exists())
        assertTrue(storage.userDir("uid_1").exists())
    }

    @Test
    fun `html test dom parser computes accurate CBT metrics and builds TestResult`() {
        val raw = """
            {
              "scoreObtained": 2.0,
              "totalMarks": 3.0,
              "summary": "Score: 2 / 3",
              "questions": [
                {"questionNumber":1,"questionText":"Capital of MP?","options":["Indore","Bhopal"],"selectedOption":"Bhopal","correctOption":"Bhopal","explanation":"Bhopal is capital.","isCorrect":true,"isAttempted":true},
                {"questionNumber":2,"questionText":"Narmada origin?","options":["Amarkantak","Indore"],"selectedOption":"Indore","correctOption":"Amarkantak","explanation":"Amarkantak plateau.","isCorrect":false,"isAttempted":true},
                {"questionNumber":3,"questionText":"Article 21?","options":["Life","Property"],"selectedOption":"","correctOption":"Life","explanation":"Right to Life.","isCorrect":false,"isAttempted":false}
              ]
            }
        """.trimIndent()
        val parsed = HtmlTestJsHelper.parseDomPayload(raw)
        assertNotNull(parsed)
        val metrics = HtmlTestJsHelper.computeMetrics(parsed, fallbackObtained = null, fallbackTotal = null)
        assertEquals(3, metrics.totalQuestions)
        assertEquals(2, metrics.attempted)
        assertEquals(1, metrics.correct)
        assertEquals(1, metrics.wrong)
        assertEquals(1, metrics.unattempted)
        assertEquals(2.0, metrics.obtained, 0.001)
        assertEquals(3.0, metrics.total, 0.001)

        val breakdownJson = json.encodeToString(metrics.breakdown)
        val decodedQuestions = json.decodeFromString<List<QuestionAttemptRecord>>(breakdownJson)
        assertEquals(3, decodedQuestions.size)
        assertEquals("Bhopal", decodedQuestions[0].correctOption)

        val result = TestResult(
            id = "res_1",
            userId = "uid_1",
            subjectId = "subj_mp_gk",
            topicId = "top_1",
            subtopicId = "sub_1",
            testTitle = "MP GK CBT #1",
            scoreObtained = metrics.obtained,
            totalMarks = metrics.total,
            percentage = (metrics.obtained / metrics.total) * 100.0,
            totalQuestions = metrics.totalQuestions,
            attemptedCount = metrics.attempted,
            correctCount = metrics.correct,
            wrongCount = metrics.wrong,
            unattemptedCount = metrics.unattempted,
            breakdownJson = breakdownJson,
            testMode = "EXAM",
            takenDate = "2026-11-17"
        )
        assertEquals(3, result.totalQuestions)
        assertEquals(1, result.wrongCount)
    }

    @Test
    fun `shared catalog merge deduplicates and caches offline catalog`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LocalFileStorage(context)
        val personal = listOf(Subject(id = "subj_polity", nameEn = "Polity", sortOrder = 1L))
        val shared = listOf(
            Subject(id = "subj_polity", nameEn = "Polity (Shared)", sortOrder = 1L, isShared = true, published = true),
            Subject(id = "subj_draft", nameEn = "Draft Subject", sortOrder = 99L, isShared = true, published = false)
        )
        val merged = SharedCatalogMergeHelper.mergeSubjects(personal, shared)
        assertEquals(1, merged.size)
        assertEquals("Polity (Shared)", merged.first().nameEn)
        assertTrue(merged.first().isShared)

        val sharedNotes = listOf(
            StudyNote(
                id = "sn_1", subjectId = "subj_polity", topicId = "t1", subtopicId = "st1",
                title = "Constitution PDF", noteType = "PDF", isShared = true, published = true, version = 2L
            )
        )
        SharedCatalogMergeHelper.saveCachedCatalog(
            fileStorage = storage,
            subjects = shared.filter { it.published },
            topics = emptyList(),
            subtopics = emptyList(),
            contents = sharedNotes,
            videos = emptyList(),
            mcqs = emptyList()
        )
        val loaded = SharedCatalogMergeHelper.loadCachedCatalog(storage)
        assertEquals(1, loaded.toSharedSubjects().size)
        assertEquals(1, loaded.toSharedContents().size)
        assertEquals(2L, loaded.toSharedContents().first().version)
        SharedCatalogMergeHelper.cacheFile(storage).delete()
    }

    @Test
    fun `whatsapp message includes student name and test draft bookmarks round trip`() {
        val msg = buildWhatsAppRequestMessage(
            studentName = "Priya Sharma",
            requestType = "HTML Mock Test",
            subject = "MP GK",
            topic = "Rivers",
            subtopic = "Narmada",
            extraNote = "Hindi medium preferred"
        )
        assertTrue(msg.contains("Priya Sharma"))
        assertTrue(msg.contains("HTML Mock Test"))
        assertTrue(msg.contains("MP GK"))

        val draft = InProgressTestAttempt(
            noteId = "html_1",
            testTitle = "GS Mock",
            testMode = "PRACTICE",
            elapsedSeconds = 125L,
            currentQuestionIndex = 4,
            answers = mapOf(1 to "A", 2 to "C")
        )
        val decodedDraft = json.decodeFromString<InProgressTestAttempt>(json.encodeToString(draft))
        assertEquals(125L, decodedDraft.elapsedSeconds)
        assertEquals("C", decodedDraft.answers[2])

        val bookmarks = listOf(
            BookmarkItem(id = "b1", bookmarkType = BookmarkType.IMPORTANT_QUESTION.name, title = "Q1"),
            BookmarkItem(id = "b2", bookmarkType = BookmarkType.PDF_PAGE.name, title = "Page 5", pageIndex = 4)
        )
        assertEquals(1, bookmarks.count { it.bookmarkType == BookmarkType.PDF_PAGE.name })
    }

    @Test
    fun `full length exam defaults and merge preserve built in custom and published shared exams`() {
        assertEquals("full_length_exam", FullLengthExamDefaults.SUBJECT_ID)
        assertEquals("full_length_html", FullLengthExamDefaults.SUBTOPIC_ID)
        val builtIn = FullLengthExamDefaults.builtInExams
        assertEquals(7, builtIn.size)

        val custom = listOf(
            FullLengthExam(id = "fl_custom_1", userId = "uid_1", name = "MPPSC Mains Mock", isShared = false, published = true)
        )
        val shared = listOf(
            FullLengthExam(id = "fl_shared_pub", userId = "admin", name = "SSC Special Test", isShared = true, published = true),
            FullLengthExam(id = "fl_shared_draft", userId = "admin", name = "Draft Exam", isShared = true, published = false)
        )

        val merged = SharedCatalogMergeHelper.mergeFullLengthExams(builtIn, custom, shared)
        assertEquals(9, merged.size)
        assertTrue(merged.any { it.id == "exam_mppsc_prelims" })
        assertTrue(merged.any { it.id == "fl_custom_1" && !it.isShared })
        assertTrue(merged.any { it.id == "fl_shared_pub" && it.isShared })
        assertFalse(merged.any { it.id == "fl_shared_draft" })
    }

    @Test
    fun `google auth helper resolves admin vs student role and session from settings`() {
        assertEquals("admin", GoogleAuthHelper.resolveRole("lovely.bunts@gmail.com"))
        assertEquals("admin", GoogleAuthHelper.resolveRole("LOVELY.BUNTS@GMAIL.COM"))
        assertEquals("student", GoogleAuthHelper.resolveRole("student@gmail.com"))
        assertFalse(GoogleAuthHelper.isAdminEmail(null))

        val offline = GoogleAuthHelper.sessionFromSettings(LocalAppSettings(offlineModeAccepted = true))
        assertNotNull(offline)
        assertTrue(offline!!.isOffline)

        val gmail = GoogleAuthHelper.sessionFromSettings(
            LocalAppSettings(
                driveUserId = "sub_123",
                driveUserEmail = "learner@gmail.com",
                driveUserName = "Aarav"
            )
        )
        assertNotNull(gmail)
        assertFalse(gmail!!.isOffline)
        assertEquals("learner@gmail.com", gmail.email)
        assertEquals("Aarav", gmail.displayName)
    }

    @Test
    fun `drive backup manager defines exact ADMIN and STUDENTS subfolders and resolves public share links`() {
        val adminFolders = GoogleDriveBackupManager.ALL_ADMIN_SUBFOLDERS
        assertEquals(4, adminFolders.size)
        assertTrue(
            adminFolders.containsAll(
                listOf("PUBLISHED TESTS", "PDF NOTES", "STUDY MATERIAL", "SUBJECTS")
            )
        )

        val studentFolders = GoogleDriveBackupManager.ALL_STUDENT_SUBFOLDERS
        assertEquals(4, studentFolders.size)
        assertTrue(
            studentFolders.containsAll(
                listOf("STUDENT DATA", "TEST RESULTS", "PROGRESS REPORTS", "PERSONAL UPLOADS")
            )
        )

        assertTrue(GoogleDriveBackupManager.isAuthRevokedError("HTTP (401) Unauthorized"))
        assertTrue(GoogleDriveBackupManager.isAuthRevokedError("invalid_grant token expired"))
        assertFalse(GoogleDriveBackupManager.isAuthRevokedError("Network timeout"))

        assertEquals(
            "https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrSt",
            GoogleDriveBackupManager.resolvePublicDriveDownloadUrl("https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrSt/view?usp=sharing")
        )
        assertEquals(
            "https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrSt",
            GoogleDriveBackupManager.resolvePublicDriveDownloadUrl("1AbCdEfGhIjKlMnOpQrSt")
        )
    }

    @Test
    fun `offline store structured drive payload and backup merge preserve local and remote data`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val remoteStore = LocalOfflineStore(context, ownerId = "merge_remote_user")
        remoteStore.addFullLengthExam("MPPSC Prelims 2026", "GS + CSAT")
        remoteStore.addStudyLog("2026-11-16", "STUDIED", "Polity Articles", "Polity", null)
        val remoteJson = remoteStore.exportCurrentSnapshotJson()

        val localStore = LocalOfflineStore(context, ownerId = "merge_local_user")
        localStore.addFullLengthExam("SSC CGL Tier 1", "Full Mock")
        localStore.mergeDriveBackupJson(remoteJson)

        val exams = localStore.fullLengthExams.value
        assertTrue(exams.any { it.name == "MPPSC Prelims 2026" })
        assertTrue(exams.any { it.name == "SSC CGL Tier 1" })
        assertTrue(localStore.studyLogs.value.any { it.title == "Polity Articles" })

        val structured = localStore.buildStructuredDrivePayload()
        assertTrue(structured.studentProfileJson.contains("Offline Learner") || structured.studentProfileJson.contains("student"))
        assertTrue(structured.progressReportJson.contains("subjectBreakdown"))
        assertTrue(structured.fullBackupJson.contains("MPPSC Prelims 2026"))
    }

    @Test
    fun `admin catalog store exports only published items and imports clean shared bundle`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adminStore = LocalAdminCatalogStore(context)
        adminStore.upsertSharedContent(
            StudyNote(
                id = "pub_note_1", subjectId = "subj_history", topicId = "t1", subtopicId = "st1",
                title = "Published History PDF", noteType = "PDF", published = true, isShared = true
            )
        )
        adminStore.upsertSharedContent(
            StudyNote(
                id = "draft_note_1", subjectId = "subj_history", topicId = "t1", subtopicId = "st1",
                title = "Draft Secret PDF", noteType = "PDF", published = false, isShared = true
            )
        )
        val exported = adminStore.exportPublishedCatalogJson()
        assertTrue(exported.contains("Published History PDF"))
        assertFalse(exported.contains("Draft Secret PDF"))

        adminStore.importPublishedCatalogJson(exported)
        assertTrue(adminStore.contents.value.any { it.id == "pub_note_1" })
        assertFalse(adminStore.contents.value.any { it.id == "draft_note_1" })
    }

    @Test
    fun `acceptance scenarios 1 and 2 verify admin gmail claim and prevent student privilege escalation`() {
        assertTrue(GoogleAuthHelper.isAdminEmail("lovely.bunts@gmail.com", emailVerified = true))
        assertEquals("admin", GoogleAuthHelper.resolveRole("lovely.bunts@gmail.com", emailVerified = true))
        // Security check: unverified email must NOT grant admin access
        assertFalse(GoogleAuthHelper.isAdminEmail("lovely.bunts@gmail.com", emailVerified = false))
        assertEquals("student", GoogleAuthHelper.resolveRole("lovely.bunts@gmail.com", emailVerified = false))
        // Any other verified Gmail becomes Student
        assertEquals("student", GoogleAuthHelper.resolveRole("student.one@gmail.com", emailVerified = true))
        assertEquals("student", GoogleAuthHelper.resolveRole("student.two@gmail.com", emailVerified = true))
    }

    @Test
    fun `acceptance scenarios 3 4 and 9 publish edit manifest version and detect newly published admin tests while keeping student history`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "test_admin_catalog_s3.json").delete()
        File(context.filesDir, "test_student_catalog_s3.json").delete()
        val adminStore = LocalAdminCatalogStore(context, "test_admin_catalog_s3.json")
        val studentCatalogStore = LocalAdminCatalogStore(context, "test_student_catalog_s3.json")

        val test1 = StudyNote(
            id = "admin_cbt_1", subjectId = "subj_mp_gk", topicId = "top_1", subtopicId = "sub_1",
            title = "MP GK Mock #1", noteType = "HTML", htmlContent = LocalFileStorage.buildSampleHtmlMockTest("MP GK Mock #1"),
            isShared = true, published = true, isMockTest = true
        )
        adminStore.upsertSharedContent(test1)
        adminStore.editSharedContent("admin_cbt_1", "MP GK Mock #1 (Revised)", "Admin Tests", isMockTest = true, published = true)
        val manifestV1 = adminStore.exportPublishedCatalogJson()

        // Student imports manifest v1 and completes test
        val studentStore = LocalOfflineStore(context, ownerId = "drive_student_manifest_test")
        val diff1 = studentCatalogStore.importPublishedCatalogJson(manifestV1)
        assertTrue(diff1.manifestVersion >= 1L)
        assertTrue(diff1.newPublishedNotes.any { it.id == "admin_cbt_1" })
        studentStore.recordTestResult(
            subjectId = "subj_mp_gk", topicId = "top_1", subtopicId = "sub_1",
            noteId = "admin_cbt_1", testTitle = "MP GK Mock #1 (Revised)",
            scoreObtained = 8.0, totalMarks = 10.0, summaryText = "8/10", subjectName = "MP GK"
        )
        assertEquals(1, studentStore.testResults.value.size)

        // Admin publishes a second test
        val test2 = StudyNote(
            id = "admin_cbt_2", subjectId = "subj_mp_gk", topicId = "top_1", subtopicId = "sub_1",
            title = "MP GK Mock #2", noteType = "HTML", htmlContent = LocalFileStorage.buildSampleHtmlMockTest("MP GK Mock #2"),
            isShared = true, published = true, isMockTest = true
        )
        adminStore.upsertSharedContent(test2)
        val manifestV2 = adminStore.exportPublishedCatalogJson()
        val diff2 = studentCatalogStore.importPublishedCatalogJson(manifestV2)
        assertTrue(diff2.newPublishedNotes.any { it.id == "admin_cbt_2" })
        // Old test history must remain completely intact
        assertEquals(1, studentStore.testResults.value.size)
        assertEquals(8.0, studentStore.testResults.value.first().scoreObtained, 0.001)
    }

    @Test
    fun `acceptance scenarios 5 and 6 isolate student data by vault and restore same gmail on new device`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val studentA = LocalOfflineStore(context, ownerId = "drive_sub_student_A")
        val studentB = LocalOfflineStore(context, ownerId = "drive_sub_student_B")

        studentA.recordTestResult(
            subjectId = "subj_polity", topicId = "t_p", subtopicId = "st_p",
            noteId = "n_p", testTitle = "Polity Test A",
            scoreObtained = 18.0, totalMarks = 20.0, summaryText = "18/20", subjectName = "Polity",
            resultId = "res_student_A_only"
        )
        studentA.upsertBookmark(
            BookmarkItem(id = "bmk_A", bookmarkType = BookmarkType.IMPORTANT_QUESTION.name, title = "Article 32")
        )

        // Student B must never see Student A's results or bookmarks
        assertFalse(studentB.testResults.value.any { it.id == "res_student_A_only" })
        assertFalse(studentB.bookmarks.value.any { it.id == "bmk_A" })

        // Device 2 login with same Google account restores Drive snapshot
        val driveBackupJson = studentA.buildStructuredDrivePayload().fullBackupJson
        val studentADevice2 = LocalOfflineStore(context, ownerId = "drive_sub_student_A_device2_sim")
        studentADevice2.mergeDriveBackupJson(driveBackupJson)

        assertTrue(studentADevice2.testResults.value.any { it.id == "res_student_A_only" })
        assertTrue(studentADevice2.bookmarks.value.any { it.title == "Article 32" })
    }

    @Test
    fun `acceptance scenarios 7 and 8 run sample HTML locally and persist deduplicated pendingSyncQueue offline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LocalFileStorage(context)
        val html = LocalFileStorage.buildSampleHtmlMockTest("Offline Polity CBT")
        assertTrue(html.contains("Offline Polity CBT"))
        val fileName = storage.writeLocalHtml("drive_offline_q", "note_html_q", "Offline Polity CBT", html)
        assertNotNull(storage.getLocalFile("drive_offline_q", fileName))

        val store = LocalOfflineStore(context, ownerId = "drive_offline_q")
        store.recordTestResult(
            subjectId = "subj_polity", topicId = "t1", subtopicId = "st1", noteId = "note_html_q",
            testTitle = "Offline Polity CBT", scoreObtained = 9.0, totalMarks = 10.0,
            summaryText = "9/10", subjectName = "Polity", resultId = "res_q1"
        )
        // Updating same result ID deduplicates in pendingSyncQueue
        store.recordTestResult(
            subjectId = "subj_polity", topicId = "t1", subtopicId = "st1", noteId = "note_html_q",
            testTitle = "Offline Polity CBT", scoreObtained = 10.0, totalMarks = 10.0,
            summaryText = "10/10", subjectName = "Polity", resultId = "res_q1"
        )
        assertTrue(store.hasPendingCloudChanges())
        assertEquals(1, store.pendingSyncCount.value)

        // After cloud sync succeeds, queue clears while data stays preserved
        store.markSyncedToCloud()
        assertFalse(store.hasPendingCloudChanges())
        assertEquals(0, store.pendingSyncCount.value)
        assertEquals(1, store.testResults.value.size)
    }

    @Test
    fun `acceptance scenario 10 keeps student uploads private unless submitted and approved by admin`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adminStore = LocalAdminCatalogStore(context)
        val studentStore = LocalOfflineStore(context, ownerId = "drive_priv_student")
        studentStore.addNote(
            noteId = "priv_note_1", subjectId = "subj_history", topicId = "t1", subtopicId = "st1",
            folderName = "My Notes", title = "Student Secret Notes",
            noteType = com.example.data.model.NoteType.PDF, localFileName = "secret.pdf"
        )
        // Private note must NOT be in shared catalog
        assertFalse(adminStore.contents.value.any { it.title == "Student Secret Notes" })

        // Toggle student upload permission
        adminStore.setAllowStudentPersonalUploads(false)
        assertFalse(adminStore.allowStudentPersonalUploads.value)
        adminStore.setAllowStudentPersonalUploads(true)
        assertTrue(adminStore.allowStudentPersonalUploads.value)

        // Student submits for admin approval -> still hidden from shared catalog until approved
        adminStore.submitStudentUploadForApproval(
            StudentUploadSubmission(
                id = "subm_priv_note_1",
                studentUid = "drive_priv_student",
                studentName = "Rohan",
                studentEmail = "rohan@gmail.com",
                subjectId = "subj_history",
                topicId = "t1",
                subtopicId = "st1",
                folderName = "My Notes",
                title = "Student Secret Notes",
                noteType = "PDF",
                localFileName = "secret.pdf"
            )
        )
        assertFalse(adminStore.contents.value.any { it.title == "Student Secret Notes" })

        // Admin approves -> becomes visible in shared published catalog
        val approved = adminStore.approveStudentSubmission("subm_priv_note_1")
        assertNotNull(approved)
        assertTrue(adminStore.contents.value.any { it.title == "Student Secret Notes" && it.published && it.isShared })
    }

    @Test
    fun `oauth token resolver returns UserCancelled on RESULT_CANCELED and fallback intent builds cleanly`() = runTest {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val manager = GoogleDriveBackupManager()

        val cancelledRes = manager.resolveTokenFromActivityResult(
            activity = activity,
            resultCode = android.app.Activity.RESULT_CANCELED,
            data = null
        )
        assertTrue(cancelledRes is com.example.data.remote.IntentTokenResolution.UserCancelled)

        val fallbackIntent = manager.buildFallbackGoogleSignInIntent(activity)
        assertNotNull(fallbackIntent)
    }

    @Test
    fun `brand color tokens and study themes match LEARN TALENT identity palette`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF071B3A), com.example.ui.theme.BrandColors.DeepNavy)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0756C9), com.example.ui.theme.BrandColors.RoyalBlue)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF55B8F2), com.example.ui.theme.BrandColors.SkyBlue)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF5C542), com.example.ui.theme.BrandColors.PremiumGold)
        assertEquals(com.example.ui.theme.BrandColors.RoyalBlue, com.example.ui.theme.StudyThemeOption.BUNTS_ROYAL_LIGHT.primary)
        assertEquals(com.example.ui.theme.BrandColors.DeepNavy, com.example.ui.theme.StudyThemeOption.BUNTS_NAVY_DARK.background)
    }
}
