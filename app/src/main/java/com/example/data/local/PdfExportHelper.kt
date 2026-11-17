package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.PdfAnnotationDocument
import com.example.data.model.PdfInkStroke
import com.example.data.model.PdfToolType
import com.example.data.model.PersonalNote
import com.example.data.model.PersonalNoteBlock
import com.example.data.model.PersonalNoteBlockType
import com.example.data.model.QuestionAttemptRecord
import com.example.data.model.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object PdfExportHelper {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    suspend fun exportTestResultPdf(
        context: Context,
        destDir: File,
        result: TestResult,
        studentName: String,
        questions: List<QuestionAttemptRecord>,
    ): File = withContext(Dispatchers.IO) {
        if (!destDir.exists()) destDir.mkdirs()
        val safeTitle = result.testTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
        val outFile = File(destDir, "Result_${safeTitle}_${System.currentTimeMillis()}.pdf")

        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        val headerBg = Paint().apply { color = Color.parseColor("#14243A") }
        val accentPaint = Paint().apply { color = Color.parseColor("#35BDB5") }
        val cardBg = Paint().apply { color = Color.parseColor("#F7F9FC") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#DCEBFA")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCEBFA")
            textSize = 11f
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14243A")
            textSize = 11.5f
        }
        val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14243A")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Top Navy Banner
        canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 72f), 12f, 12f, headerBg)
        canvas.drawRect(MARGIN, y + 66f, PAGE_WIDTH - MARGIN, y + 72f, accentPaint)
        canvas.drawText("LEARN TALENT by Bunts — Test Report", MARGIN + 16f, y + 28f, titlePaint)
        canvas.drawText(
            "Student: ${studentName.ifBlank { "Student" }}  •  Date: ${result.takenDate}  •  Mode: ${result.testMode}",
            MARGIN + 16f,
            y + 50f,
            subHeaderPaint
        )
        y += 88f

        // Score Summary Card
        canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 86f), 10f, 10f, cardBg)
        canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 86f), 10f, 10f, borderPaint)
        canvas.drawText(result.testTitle, MARGIN + 14f, y + 24f, boldBodyPaint)
        canvas.drawText(
            "Score: ${result.scoreObtained.toInt()} / ${result.totalMarks.toInt()} (${String.format("%.1f", result.percentage)}%)",
            MARGIN + 14f,
            y + 44f,
            bodyPaint
        )
        val totalQ = if (result.totalQuestions > 0) result.totalQuestions else questions.size
        canvas.drawText(
            "Total Q: $totalQ  |  Attempted: ${result.attemptedCount}  |  Correct: ${result.correctCount}  |  Wrong: ${result.wrongCount}  |  Unattempted: ${result.unattemptedCount}",
            MARGIN + 14f,
            y + 64f,
            bodyPaint
        )
        y += 104f

        val contentWidth = (PAGE_WIDTH - MARGIN * 2 - 20f).toInt()
        questions.forEach { q ->
            val lines = buildString {
                append("Q${q.questionNumber}. ${q.questionText}\n")
                val status = when {
                    !q.isAttempted -> "Unattempted"
                    q.isCorrect -> "Correct"
                    else -> "Wrong"
                }
                append("Status: $status")
                if (q.selectedOption.isNotBlank()) append("  |  Your Answer: ${q.selectedOption}")
                if (q.correctOption.isNotBlank()) append("  |  Correct Answer: ${q.correctOption}")
                if (q.explanation.isNotBlank()) append("\nExplanation: ${q.explanation}")
            }
            val layout = StaticLayout.Builder.obtain(lines, 0, lines.length, bodyPaint, contentWidth)
                .setLineSpacing(2f, 1.12f)
                .build()
            val neededHeight = layout.height + 24f
            if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
            val boxRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + layout.height + 16f)
            val tint = Paint().apply {
                color = when {
                    !q.isAttempted -> Color.parseColor("#F7F9FC")
                    q.isCorrect -> Color.parseColor("#ECFDF5")
                    else -> Color.parseColor("#FEF2F2")
                }
            }
            canvas.drawRoundRect(boxRect, 8f, 8f, tint)
            canvas.drawRoundRect(boxRect, 8f, 8f, borderPaint)
            canvas.save()
            canvas.translate(MARGIN + 10f, y + 8f)
            layout.draw(canvas)
            canvas.restore()
            y += neededHeight
        }

        doc.finishPage(page)
        outFile.outputStream().use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    suspend fun exportPersonalNotePdf(
        context: Context,
        destDir: File,
        userFilesDir: File,
        note: PersonalNote,
        blocks: List<PersonalNoteBlock>,
    ): File = withContext(Dispatchers.IO) {
        if (!destDir.exists()) destDir.mkdirs()
        val safeTitle = note.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40).ifBlank { "Note" }
        val outFile = File(destDir, "${safeTitle}_${System.currentTimeMillis()}.pdf")

        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas
        var y = MARGIN

        val headerBg = Paint().apply { color = Color.parseColor("#2456A4") }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCEBFA")
            textSize = 11f
        }

        canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 66f), 12f, 12f, headerBg)
        canvas.drawText(note.title.ifBlank { "Personal Note" }, MARGIN + 16f, y + 26f, titlePaint)
        canvas.drawText(
            listOf(note.subjectName, note.topicName, "LEARN TALENT by Bunts")
                .filter { it.isNotBlank() }
                .joinToString("  •  "),
            MARGIN + 16f,
            y + 48f,
            subPaint
        )
        y += 84f

        val contentWidth = (PAGE_WIDTH - MARGIN * 2 - 16f).toInt()
        blocks.forEach { block ->
            val blockType = PersonalNoteBlockType.fromRaw(block.type)
            if (blockType == PersonalNoteBlockType.IMAGE && !block.imageFileName.isNullOrBlank()) {
                val imgFile = File(userFilesDir, block.imageFileName)
                if (imgFile.exists()) {
                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                    if (bmp != null) {
                        val maxW = PAGE_WIDTH - MARGIN * 2
                        val scale = minOf(maxW / bmp.width.toFloat(), 240f / bmp.height.toFloat(), 1f)
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        if (y + drawH + 14f > PAGE_HEIGHT - MARGIN) {
                            doc.finishPage(page)
                            pageNum++
                            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                            canvas = page.canvas
                            y = MARGIN
                        }
                        canvas.drawBitmap(bmp, null, RectF(MARGIN, y, MARGIN + drawW, y + drawH), null)
                        bmp.recycle()
                        y += drawH + 14f
                    }
                }
                return@forEach
            }

            val textToRender = when (blockType) {
                PersonalNoteBlockType.HEADING -> block.text
                PersonalNoteBlockType.BULLET -> "• ${block.text}"
                PersonalNoteBlockType.CHECKLIST -> (if (block.checked) "[✓] " else "[ ] ") + block.text
                PersonalNoteBlockType.TABLE_ROW -> block.tableCells.joinToString("  |  ")
                PersonalNoteBlockType.HIGHLIGHT, PersonalNoteBlockType.PARAGRAPH, PersonalNoteBlockType.IMAGE -> block.text
            }.ifBlank { " " }

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = runCatching { Color.parseColor(block.textColorHex) }.getOrDefault(Color.parseColor("#14243A"))
                textSize = when (blockType) {
                    PersonalNoteBlockType.HEADING -> (block.fontSizeSp + 3).coerceIn(15, 24).toFloat()
                    else -> block.fontSizeSp.coerceIn(11, 20).toFloat()
                }
                typeface = if (blockType == PersonalNoteBlockType.HEADING) {
                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                } else {
                    Typeface.DEFAULT
                }
            }
            val layout = StaticLayout.Builder.obtain(textToRender, 0, textToRender.length, textPaint, contentWidth)
                .setLineSpacing(2f, 1.15f)
                .build()
            val height = layout.height + 16f
            if (y + height > PAGE_HEIGHT - MARGIN) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }
            val hlHex = block.highlightColorHex ?: if (blockType == PersonalNoteBlockType.HIGHLIGHT) "#FEF08A" else null
            if (!hlHex.isNullOrBlank()) {
                val hlPaint = Paint().apply {
                    color = runCatching { Color.parseColor(hlHex) }.getOrDefault(Color.parseColor("#FEF08A"))
                }
                canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + layout.height + 10f), 6f, 6f, hlPaint)
            }
            canvas.save()
            canvas.translate(MARGIN + 8f, y + 5f)
            layout.draw(canvas)
            canvas.restore()
            y += height
        }

        doc.finishPage(page)
        outFile.outputStream().use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    suspend fun saveAnnotatedPdfCopy(
        sourcePdf: File,
        annotationDoc: PdfAnnotationDocument,
        destFile: File,
    ): File = withContext(Dispatchers.IO) {
        destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val outDoc = PdfDocument()

        try {
            for (index in 0 until renderer.pageCount) {
                val rPage = renderer.openPage(index)
                val targetW = (rPage.width * 2).coerceIn(720, 1440)
                val targetH = ((rPage.height.toFloat() / rPage.width.coerceAtLeast(1)) * targetW).toInt().coerceAtLeast(400)
                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                rPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                rPage.close()

                val pageCanvas = Canvas(bitmap)
                val pageAnn = annotationDoc.pages[index]
                if (pageAnn != null) {
                    pageAnn.strokes.forEach { stroke ->
                        drawStrokeOnCanvas(pageCanvas, stroke, targetW.toFloat(), targetH.toFloat())
                    }
                    pageAnn.textBoxes.forEach { box ->
                        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = box.colorArgb.toInt()
                            textSize = (box.fontSizeNorm * targetW).coerceAtLeast(16f)
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        pageCanvas.drawText(box.text, box.xNorm * targetW, box.yNorm * targetH, textPaint)
                    }
                }

                val outPage = outDoc.startPage(PdfDocument.PageInfo.Builder(targetW, targetH, index + 1).create())
                outPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                outDoc.finishPage(outPage)
                bitmap.recycle()
            }
            destFile.outputStream().use { outDoc.writeTo(it) }
        } finally {
            outDoc.close()
            renderer.close()
            pfd.close()
        }
        destFile
    }

    private fun drawStrokeOnCanvas(canvas: Canvas, stroke: PdfInkStroke, width: Float, height: Float) {
        if (stroke.points.isEmpty()) return
        val tool = runCatching { PdfToolType.valueOf(stroke.tool) }.getOrDefault(PdfToolType.PEN)
        val baseColor = stroke.colorArgb.toInt()
        val alphaInt = (stroke.opacity.coerceIn(0.1f, 1f) * 255).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alphaInt, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            strokeWidth = (stroke.strokeWidthNorm * width).coerceAtLeast(2f)
            style = Paint.Style.STROKE
            strokeCap = if (tool == PdfToolType.HIGHLIGHTER) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val pts = stroke.points
        when (tool) {
            PdfToolType.RECTANGLE -> {
                if (pts.size >= 2) {
                    val f = pts.first()
                    val l = pts.last()
                    canvas.drawRect(
                        minOf(f.x, l.x) * width,
                        minOf(f.y, l.y) * height,
                        maxOf(f.x, l.x) * width,
                        maxOf(f.y, l.y) * height,
                        paint
                    )
                }
            }
            PdfToolType.CIRCLE -> {
                if (pts.size >= 2) {
                    val f = pts.first()
                    val l = pts.last()
                    canvas.drawOval(
                        minOf(f.x, l.x) * width,
                        minOf(f.y, l.y) * height,
                        maxOf(f.x, l.x) * width,
                        maxOf(f.y, l.y) * height,
                        paint
                    )
                }
            }
            PdfToolType.UNDERLINE, PdfToolType.STRIKETHROUGH -> {
                if (pts.size >= 2) {
                    val f = pts.first()
                    val l = pts.last()
                    val avgY = ((f.y + l.y) / 2f) * height
                    canvas.drawLine(f.x * width, avgY, l.x * width, avgY, paint)
                }
            }
            PdfToolType.ARROW -> {
                if (pts.size >= 2) {
                    val f = pts.first()
                    val l = pts.last()
                    val x1 = f.x * width
                    val y1 = f.y * height
                    val x2 = l.x * width
                    val y2 = l.y * height
                    canvas.drawLine(x1, y1, x2, y2, paint)
                    val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                    val headLen = (paint.strokeWidth * 3.5f).coerceAtLeast(18f)
                    val a1 = angle - Math.PI / 6.0
                    val a2 = angle + Math.PI / 6.0
                    canvas.drawLine(x2, y2, (x2 - headLen * cos(a1)).toFloat(), (y2 - headLen * sin(a1)).toFloat(), paint)
                    canvas.drawLine(x2, y2, (x2 - headLen * cos(a2)).toFloat(), (y2 - headLen * sin(a2)).toFloat(), paint)
                }
            }
            else -> {
                val path = Path()
                path.moveTo(pts.first().x * width, pts.first().y * height)
                for (i in 1 until pts.size) {
                    path.lineTo(pts[i].x * width, pts[i].y * height)
                }
                if (pts.size == 1) {
                    path.lineTo(pts.first().x * width + 1f, pts.first().y * height + 1f)
                }
                canvas.drawPath(path, paint)
            }
        }
    }
}
