package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

enum class PdfToolType(val label: String) {
    PAN_ZOOM("Scroll / Zoom"),
    PEN("Pen"),
    PENCIL("Pencil"),
    HIGHLIGHTER("Highlight"),
    UNDERLINE("Underline"),
    STRIKETHROUGH("Strike"),
    ARROW("Arrow"),
    CIRCLE("Circle"),
    RECTANGLE("Box"),
    TEXT_BOX("Text"),
    ERASER("Eraser");

    val isDrawingTool: Boolean
        get() = this != PAN_ZOOM
}

@Serializable
data class NormalizedPoint(
    val x: Float = 0f,
    val y: Float = 0f,
)

@Serializable
data class PdfInkStroke(
    val id: String = "",
    val tool: String = PdfToolType.PEN.name,
    val colorArgb: Long = 0xFFEF4444L,
    val strokeWidthNorm: Float = 0.006f,
    val opacity: Float = 1f,
    val points: List<NormalizedPoint> = emptyList(),
)

@Serializable
data class PdfTextAnnotation(
    val id: String = "",
    val xNorm: Float = 0.1f,
    val yNorm: Float = 0.1f,
    val text: String = "",
    val colorArgb: Long = 0xFF14243AL,
    val fontSizeNorm: Float = 0.032f,
)

@Serializable
data class PdfPageAnnotation(
    val pageIndex: Int = 0,
    val strokes: List<PdfInkStroke> = emptyList(),
    val textBoxes: List<PdfTextAnnotation> = emptyList(),
)

@Serializable
data class PdfAnnotationDocument(
    val noteId: String = "",
    val ownerUid: String = "",
    val pages: Map<Int, PdfPageAnnotation> = emptyMap(),
    val updatedAtMillis: Long = 0L,
)

@Keep
@IgnoreExtraProperties
data class CloudPdfAnnotationDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val noteId: String = "",
    val annotationsJson: String = "",
    val updatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

object PdfInkPalette {
    val colors: List<Pair<String, Long>> = listOf(
        "Red" to 0xFFEF4444L,
        "Blue" to 0xFF2456A4L,
        "Green" to 0xFF16A34AL,
        "Yellow" to 0xFFEAB308L,
        "Orange" to 0xFFF97316L,
        "Pink" to 0xFFEC4899L,
        "Purple" to 0xFF9333EAL,
        "Black" to 0xFF111827L,
    )
}
