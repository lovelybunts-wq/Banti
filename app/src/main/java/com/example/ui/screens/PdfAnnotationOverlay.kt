package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NormalizedPoint
import com.example.data.model.PdfInkPalette
import com.example.data.model.PdfInkStroke
import com.example.data.model.PdfPageAnnotation
import com.example.data.model.PdfTextAnnotation
import com.example.data.model.PdfToolType
import com.example.ui.theme.LocalStudyTheme
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Composable
fun PdfPageAnnotationLayer(
    pageIndex: Int,
    pageAnnotation: PdfPageAnnotation,
    selectedTool: PdfToolType,
    selectedColorArgb: Long,
    strokeWidthNorm: Float,
    opacity: Float,
    onAddStroke: (Int, PdfInkStroke) -> Unit,
    onEraseNear: (Int, NormalizedPoint) -> Unit,
    onTapForTextBox: (Int, NormalizedPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activePoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }

    fun toNorm(offset: Offset): NormalizedPoint {
        val w = boxSize.width.coerceAtLeast(1).toFloat()
        val h = boxSize.height.coerceAtLeast(1).toFloat()
        return NormalizedPoint(
            x = (offset.x / w).coerceIn(0f, 1f),
            y = (offset.y / h).coerceIn(0f, 1f)
        )
    }

    val gestureModifier = when {
        selectedTool == PdfToolType.PAN_ZOOM -> Modifier
        selectedTool == PdfToolType.TEXT_BOX -> Modifier.pointerInput(pageIndex, selectedTool) {
            detectTapGestures { offset -> onTapForTextBox(pageIndex, toNorm(offset)) }
        }
        selectedTool == PdfToolType.ERASER -> Modifier
            .pointerInput(pageIndex, selectedTool) {
                detectTapGestures { offset -> onEraseNear(pageIndex, toNorm(offset)) }
            }
            .pointerInput(pageIndex, selectedTool) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onEraseNear(pageIndex, toNorm(change.position))
                }
            }
        else -> Modifier.pointerInput(pageIndex, selectedTool, selectedColorArgb, strokeWidthNorm, opacity) {
            detectDragGestures(
                onDragStart = { start -> activePoints = listOf(toNorm(start)) },
                onDrag = { change, _ ->
                    change.consume()
                    val pt = toNorm(change.position)
                    activePoints = when (selectedTool) {
                        PdfToolType.ARROW, PdfToolType.CIRCLE, PdfToolType.RECTANGLE,
                        PdfToolType.UNDERLINE, PdfToolType.STRIKETHROUGH -> {
                            listOf(activePoints.firstOrNull() ?: pt, pt)
                        }
                        else -> activePoints + pt
                    }
                },
                onDragEnd = {
                    if (activePoints.isNotEmpty()) {
                        val effOpacity = if (selectedTool == PdfToolType.HIGHLIGHTER) (opacity * 0.45f).coerceIn(0.15f, 0.7f) else opacity
                        val effWidth = when (selectedTool) {
                            PdfToolType.HIGHLIGHTER -> strokeWidthNorm * 2.8f
                            PdfToolType.PENCIL -> strokeWidthNorm * 0.65f
                            else -> strokeWidthNorm
                        }
                        onAddStroke(
                            pageIndex,
                            PdfInkStroke(
                                id = UUID.randomUUID().toString(),
                                tool = selectedTool.name,
                                colorArgb = selectedColorArgb,
                                strokeWidthNorm = effWidth,
                                opacity = effOpacity,
                                points = activePoints
                            )
                        )
                    }
                    activePoints = emptyList()
                },
                onDragCancel = { activePoints = emptyList() }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .then(gestureModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            pageAnnotation.strokes.forEach { stroke -> drawPdfInkStroke(stroke) }
            if (activePoints.isNotEmpty()) {
                val effOpacity = if (selectedTool == PdfToolType.HIGHLIGHTER) (opacity * 0.45f).coerceIn(0.15f, 0.7f) else opacity
                val effWidth = when (selectedTool) {
                    PdfToolType.HIGHLIGHTER -> strokeWidthNorm * 2.8f
                    PdfToolType.PENCIL -> strokeWidthNorm * 0.65f
                    else -> strokeWidthNorm
                }
                drawPdfInkStroke(
                    PdfInkStroke(
                        id = "preview",
                        tool = selectedTool.name,
                        colorArgb = selectedColorArgb,
                        strokeWidthNorm = effWidth,
                        opacity = effOpacity,
                        points = activePoints
                    )
                )
            }
        }

        pageAnnotation.textBoxes.forEach { tb ->
            TextAnnotationChip(tb = tb, containerSize = boxSize)
        }
    }
}

@Composable
private fun TextAnnotationChip(tb: PdfTextAnnotation, containerSize: IntSize) {
    val pxX = (tb.xNorm * containerSize.width).toInt().coerceIn(0, (containerSize.width - 80).coerceAtLeast(0))
    val pxY = (tb.yNorm * containerSize.height).toInt().coerceIn(0, (containerSize.height - 32).coerceAtLeast(0))
    Surface(
        color = Color(0xEEFFFFFF),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .offset { IntOffset(pxX, pxY) }
            .border(1.dp, Color(tb.colorArgb), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = tb.text,
            color = Color(tb.colorArgb),
            fontSize = (tb.fontSizeNorm * 420f).coerceIn(11f, 22f).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun DrawScope.drawPdfInkStroke(stroke: PdfInkStroke) {
    if (stroke.points.isEmpty()) return
    val baseColor = Color(stroke.colorArgb).copy(alpha = stroke.opacity.coerceIn(0.1f, 1f))
    val widthPx = (stroke.strokeWidthNorm * size.width).coerceAtLeast(2f)
    val tool = runCatching { PdfToolType.valueOf(stroke.tool) }.getOrDefault(PdfToolType.PEN)
    val pts = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }

    when (tool) {
        PdfToolType.RECTANGLE -> {
            val a = pts.first()
            val b = pts.last()
            drawRect(
                color = baseColor,
                topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
                size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)),
                style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        PdfToolType.CIRCLE -> {
            val a = pts.first()
            val b = pts.last()
            drawOval(
                color = baseColor,
                topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
                size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)),
                style = Stroke(width = widthPx)
            )
        }
        PdfToolType.ARROW -> {
            val a = pts.first()
            val b = pts.last()
            drawLine(color = baseColor, start = a, end = b, strokeWidth = widthPx, cap = StrokeCap.Round)
            val angle = atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
            val headLen = (widthPx * 4f).coerceIn(14f, 34f)
            val wing1 = Offset((b.x - headLen * cos(angle - 0.45)).toFloat(), (b.y - headLen * sin(angle - 0.45)).toFloat())
            val wing2 = Offset((b.x - headLen * cos(angle + 0.45)).toFloat(), (b.y - headLen * sin(angle + 0.45)).toFloat())
            drawLine(color = baseColor, start = b, end = wing1, strokeWidth = widthPx, cap = StrokeCap.Round)
            drawLine(color = baseColor, start = b, end = wing2, strokeWidth = widthPx, cap = StrokeCap.Round)
        }
        PdfToolType.UNDERLINE, PdfToolType.STRIKETHROUGH -> {
            val a = pts.first()
            val b = pts.last()
            drawLine(color = baseColor, start = a, end = Offset(b.x, a.y), strokeWidth = widthPx, cap = StrokeCap.Round)
        }
        else -> {
            if (pts.size == 1) {
                drawCircle(color = baseColor, radius = widthPx / 2f, center = pts.first())
            } else {
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(path = path, color = baseColor, style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

fun isNearAnnotationPoint(stroke: PdfInkStroke, p: NormalizedPoint, threshold: Float = 0.045f): Boolean =
    stroke.points.any { hypot((it.x - p.x).toDouble(), (it.y - p.y).toDouble()) < threshold }

@Composable
fun PdfAnnotationToolbar(
    selectedTool: PdfToolType,
    selectedColorArgb: Long,
    strokeWidthNorm: Float,
    opacity: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectTool: (PdfToolType) -> Unit,
    onSelectColor: (Long) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    Surface(color = theme.surfaceElevated, tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items(PdfToolType.entries) { tool ->
                    FilterChip(
                        selected = selectedTool == tool,
                        onClick = { onSelectTool(tool) },
                        leadingIcon = { Icon(toolIconFor(tool), contentDescription = tool.label, modifier = Modifier.size(16.dp)) },
                        label = { Text(tool.label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.primaryContainer, selectedLabelColor = theme.primary)
                    )
                }
            }
            if (selectedTool.isDrawingTool) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfInkPalette.colors) { (name, argb) ->
                            val selected = selectedColorArgb == argb
                            Box(
                                modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(argb))
                                    .border(if (selected) 3.dp else 1.dp, if (selected) theme.onSurface else theme.outline, CircleShape)
                                    .clickable { onSelectColor(argb) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onUndo, enabled = canUndo) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo", tint = if (canUndo) theme.primary else theme.outline)
                        }
                        IconButton(onClick = onRedo, enabled = canRedo) {
                            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo", tint = if (canRedo) theme.primary else theme.outline)
                        }
                        IconButton(onClick = onClearPage) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Page", tint = theme.error)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Size", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                    Slider(value = strokeWidthNorm, onValueChange = onStrokeWidthChange, valueRange = 0.002f..0.02f, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    Text("Alpha", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
                    Slider(value = opacity, onValueChange = onOpacityChange, valueRange = 0.2f..1f, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun toolIconFor(tool: PdfToolType): ImageVector = when (tool) {
    PdfToolType.PAN_ZOOM -> Icons.Rounded.OpenWith
    PdfToolType.PEN -> Icons.Rounded.Edit
    PdfToolType.PENCIL -> Icons.Rounded.Brush
    PdfToolType.HIGHLIGHTER -> Icons.Rounded.BorderColor
    PdfToolType.UNDERLINE -> Icons.Rounded.FormatUnderlined
    PdfToolType.STRIKETHROUGH -> Icons.Rounded.FormatStrikethrough
    PdfToolType.ARROW -> Icons.Rounded.NorthEast
    PdfToolType.CIRCLE -> Icons.Rounded.RadioButtonUnchecked
    PdfToolType.RECTANGLE -> Icons.Rounded.CropSquare
    PdfToolType.TEXT_BOX -> Icons.Rounded.TextFields
    PdfToolType.ERASER -> Icons.Rounded.AutoFixHigh
}
