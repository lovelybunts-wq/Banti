package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalFileStorage
import com.example.data.local.LocalOfflineStore
import com.example.data.local.PdfExportHelper
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.DefaultSubjectsSeed
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
}
