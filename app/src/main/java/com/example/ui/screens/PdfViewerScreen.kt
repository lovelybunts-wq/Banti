package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.NormalizedPoint
import com.example.data.model.PdfAnnotationDocument
import com.example.data.model.PdfInkPalette
import com.example.data.model.PdfInkStroke
import com.example.data.model.PdfPageAnnotation
import com.example.data.model.PdfTextAnnotation
import com.example.data.model.PdfToolType
import com.example.data.model.StudyNote
import com.example.ui.theme.LocalStudyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    note: StudyNote?,
    pdfFile: File?,
    directTitle: String? = null,
    initialPage: Int = 0,
    initialAnnotationDoc: PdfAnnotationDocument = PdfAnnotationDocument(noteId = note?.id ?: directTitle ?: "pdf"),
    bookmarks: List<BookmarkItem> = emptyList(),
    onSaveAnnotations: (PdfAnnotationDocument) -> Unit = {},
    onSaveAsNewCopy: (File, StudyNote?, PdfAnnotationDocument) -> Unit = { _, _, _ -> },
    onBookmarkPage: (pageIndex: Int, title: String) -> Unit = { _, _ -> },
    onDownloadSharedPdf: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    val scope = rememberCoroutineScope()
    var pageCount by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var rendererHolder by remember { mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null) }
    val renderMutex = remember { Mutex() }
    val listState = rememberLazyListState()

    var showAnnotationBar by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf(PdfToolType.PAN_ZOOM) }
    var selectedColorArgb by remember { mutableStateOf(PdfInkPalette.colors.first().second) }
    var strokeWidthNorm by remember { mutableFloatStateOf(0.006f) }
    var opacity by remember { mutableFloatStateOf(1f) }

    var annotationDoc by remember(initialAnnotationDoc.noteId) { mutableStateOf(initialAnnotationDoc) }
    var undoStack by remember { mutableStateOf<List<PdfAnnotationDocument>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<PdfAnnotationDocument>>(emptyList()) }

    var showPageJumpDialog by remember { mutableStateOf(false) }
    var pendingTextPlacement by remember { mutableStateOf<Pair<Int, NormalizedPoint>?>(null) }

    val currentPageIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val resolvedNoteId = note?.id ?: directTitle ?: pdfFile?.name ?: "pdf"
    val isCurrentPageBookmarked = bookmarks.any {
        it.bookmarkType == BookmarkType.PDF_PAGE.name && it.refId == resolvedNoteId && it.pageIndex == currentPageIndex
    }

    fun pushAnnotationState(next: PdfAnnotationDocument) {
        undoStack = (undoStack + annotationDoc).takeLast(30)
        redoStack = emptyList()
        annotationDoc = next
    }

    LaunchedEffect(pageCount, initialPage) {
        if (pageCount > 0 && initialPage in 0 until pageCount) {
            listState.scrollToItem(initialPage)
        }
    }

    DisposableEffect(pdfFile?.absolutePath) {
        var holder: Pair<ParcelFileDescriptor, PdfRenderer>? = null
        errorMsg = null
        if (pdfFile != null && pdfFile.exists()) {
            try {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                holder = pfd to renderer
                rendererHolder = holder
                pageCount = renderer.pageCount
                if (renderer.pageCount == 0) errorMsg = "इस PDF में कोई पेज नहीं मिला।"
            } catch (e: Exception) {
                pageCount = 0
                errorMsg = "यह फाइल वैध PDF नहीं है या पासवर्ड लॉक है (${e.localizedMessage ?: "Error"})।"
            }
        }
        onDispose {
            runCatching { holder?.second?.close() }
            runCatching { holder?.first?.close() }
            rendererHolder = null
        }
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                navigationIcon = {
                    IconButton(onClick = { onSaveAnnotations(annotationDoc); onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(note?.title ?: directTitle ?: "PDF Note", style = MaterialTheme.typography.titleMedium, color = theme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (pageCount > 0) {
                            TextButton(onClick = { showPageJumpDialog = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(22.dp)) {
                                Text("Page ${currentPageIndex + 1} / $pageCount • ${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = theme.secondary)
                            }
                        }
                    }
                },
                actions = {
                    if (pageCount > 0) {
                        IconButton(onClick = { onBookmarkPage(currentPageIndex, note?.title ?: directTitle ?: "PDF") }) {
                            Icon(if (isCurrentPageBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = "Bookmark Page", tint = if (isCurrentPageBookmarked) theme.secondary else theme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            showAnnotationBar = !showAnnotationBar
                            selectedTool = if (showAnnotationBar) PdfToolType.PEN else PdfToolType.PAN_ZOOM
                        }, modifier = Modifier.testTag("pdf_annotate_toggle_btn")) {
                            Icon(Icons.Rounded.Draw, contentDescription = "Annotate", tint = if (showAnnotationBar) theme.primary else theme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onSaveAnnotations(annotationDoc) }, modifier = Modifier.testTag("pdf_save_annotations_btn")) {
                            Icon(Icons.Rounded.Save, contentDescription = "Save Annotations", tint = theme.success)
                        }
                        if (pdfFile != null) {
                            IconButton(onClick = { onSaveAsNewCopy(pdfFile, note, annotationDoc) }, modifier = Modifier.testTag("pdf_save_copy_btn")) {
                                Icon(Icons.Rounded.SaveAs, contentDescription = "Save As New Copy", tint = theme.primary)
                            }
                        }
                        IconButton(onClick = { scale = (scale - 0.25f).coerceAtLeast(1f) }) { Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out") }
                        IconButton(onClick = { scale = (scale + 0.25f).coerceAtMost(3f) }) { Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in") }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (showAnnotationBar && pageCount > 0) {
                PdfAnnotationToolbar(
                    selectedTool = selectedTool, selectedColorArgb = selectedColorArgb,
                    strokeWidthNorm = strokeWidthNorm, opacity = opacity,
                    canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty(),
                    onSelectTool = { selectedTool = it }, onSelectColor = { selectedColorArgb = it },
                    onStrokeWidthChange = { strokeWidthNorm = it }, onOpacityChange = { opacity = it },
                    onUndo = {
                        val prev = undoStack.lastOrNull() ?: return@PdfAnnotationToolbar
                        redoStack = (redoStack + annotationDoc).takeLast(30)
                        undoStack = undoStack.dropLast(1)
                        annotationDoc = prev
                    },
                    onRedo = {
                        val next = redoStack.lastOrNull() ?: return@PdfAnnotationToolbar
                        undoStack = (undoStack + annotationDoc).takeLast(30)
                        redoStack = redoStack.dropLast(1)
                        annotationDoc = next
                    },
                    onClearPage = {
                        val pages = annotationDoc.pages.toMutableMap()
                        pages.remove(currentPageIndex)
                        pushAnnotationState(annotationDoc.copy(pages = pages))
                    }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0A0D14)), contentAlignment = Alignment.Center) {
                when {
                    pdfFile == null || !pdfFile.exists() -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
                            Text("PDF इस डिवाइस पर ऑफलाइन सेव नहीं है।", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            if (onDownloadSharedPdf != null) {
                                Button(onClick = onDownloadSharedPdf, colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)) {
                                    Icon(Icons.Rounded.Download, contentDescription = null); Spacer(Modifier.widthIn(min = 6.dp)); Text("Download PDF Now")
                                }
                            } else {
                                Text("कृपया इसे दोबारा जोड़ें या Google Drive से रिस्टोर करें।", color = Color(0xFFB0BEC5), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    errorMsg != null -> Text(errorMsg ?: "", color = theme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
                    pageCount == 0 -> CircularProgressIndicator(color = theme.primary)
                    else -> {
                        val renderer = rendererHolder?.second
                        if (renderer != null) {
                            val panEnabled = !selectedTool.isDrawingTool
                            LazyColumn(
                                state = listState,
                                userScrollEnabled = panEnabled,
                                modifier = Modifier.fillMaxSize()
                                    .then(if (panEnabled) Modifier.pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 3.0f) } } else Modifier)
                                    .graphicsLayer(scaleX = scale, scaleY = scale),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                items(pageCount) { index ->
                                    val pageAnn = annotationDoc.pages[index] ?: PdfPageAnnotation(pageIndex = index)
                                    PdfPageTileWithOverlay(
                                        renderer = renderer, pageIndex = index, pageCount = pageCount, renderMutex = renderMutex,
                                        pageAnnotation = pageAnn, selectedTool = selectedTool, selectedColorArgb = selectedColorArgb,
                                        strokeWidthNorm = strokeWidthNorm, opacity = opacity,
                                        onAddStroke = { pIdx, stroke ->
                                            val pages = annotationDoc.pages.toMutableMap()
                                            val cur = pages[pIdx] ?: PdfPageAnnotation(pIdx)
                                            pages[pIdx] = cur.copy(strokes = cur.strokes + stroke)
                                            pushAnnotationState(annotationDoc.copy(pages = pages))
                                        },
                                        onEraseNear = { pIdx, pt ->
                                            val cur = annotationDoc.pages[pIdx] ?: return@PdfPageTileWithOverlay
                                            val filteredStrokes = cur.strokes.filterNot { isNearAnnotationPoint(it, pt) }
                                            val filteredTexts = cur.textBoxes.filterNot { hypot((it.xNorm - pt.x).toDouble(), (it.yNorm - pt.y).toDouble()) < 0.08 }
                                            if (filteredStrokes.size != cur.strokes.size || filteredTexts.size != cur.textBoxes.size) {
                                                val pages = annotationDoc.pages.toMutableMap()
                                                pages[pIdx] = cur.copy(strokes = filteredStrokes, textBoxes = filteredTexts)
                                                pushAnnotationState(annotationDoc.copy(pages = pages))
                                            }
                                        },
                                        onTapForTextBox = { pIdx, pt -> pendingTextPlacement = pIdx to pt }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPageJumpDialog) {
        var pageInput by remember { mutableStateOf((currentPageIndex + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showPageJumpDialog = false }, containerColor = theme.surfaceElevated,
            title = { Text("पेज नंबर पर जाएं (1 - $pageCount)", color = theme.onSurface) },
            text = { OutlinedTextField(value = pageInput, onValueChange = { pageInput = it }, label = { Text("Page Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    val p = (pageInput.toIntOrNull() ?: 1).coerceIn(1, pageCount.coerceAtLeast(1)) - 1
                    scope.launch { listState.scrollToItem(p) }
                    showPageJumpDialog = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { showPageJumpDialog = false }) { Text("Cancel") } }
        )
    }

    val textPlace = pendingTextPlacement
    if (textPlace != null) {
        var txt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingTextPlacement = null }, containerColor = theme.surfaceElevated,
            title = { Text("PDF पर टेक्स्ट नोट लिखें", color = theme.onSurface) },
            text = { OutlinedTextField(value = txt, onValueChange = { txt = it }, label = { Text("Note Text") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = {
                    if (txt.isNotBlank()) {
                        val (pIdx, pt) = textPlace
                        val pages = annotationDoc.pages.toMutableMap()
                        val cur = pages[pIdx] ?: PdfPageAnnotation(pIdx)
                        pages[pIdx] = cur.copy(textBoxes = cur.textBoxes + PdfTextAnnotation(UUID.randomUUID().toString(), pt.x, pt.y, txt.trim(), selectedColorArgb))
                        pushAnnotationState(annotationDoc.copy(pages = pages))
                    }
                    pendingTextPlacement = null
                }) { Text("Add Text") }
            },
            dismissButton = { TextButton(onClick = { pendingTextPlacement = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PdfPageTileWithOverlay(
    renderer: PdfRenderer, pageIndex: Int, pageCount: Int, renderMutex: Mutex,
    pageAnnotation: PdfPageAnnotation, selectedTool: PdfToolType, selectedColorArgb: Long,
    strokeWidthNorm: Float, opacity: Float,
    onAddStroke: (Int, PdfInkStroke) -> Unit, onEraseNear: (Int, NormalizedPoint) -> Unit, onTapForTextBox: (Int, NormalizedPoint) -> Unit,
) {
    val theme = LocalStudyTheme.current
    var bitmap by remember(renderer, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var aspect by remember(renderer, pageIndex) { mutableFloatStateOf(0.707f) }

    LaunchedEffect(renderer, pageIndex) {
        val result = withContext(Dispatchers.IO) {
            renderMutex.withLock {
                if (pageIndex !in 0 until renderer.pageCount) return@withLock null
                val page = renderer.openPage(pageIndex)
                try {
                    val ratio = if (page.height > 0) page.width.toFloat() / page.height.toFloat() else 0.707f
                    val width = (page.width * 2).coerceIn(900, 1800)
                    val height = (width / ratio).toInt().coerceAtLeast(600)
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp to ratio
                } finally { page.close() }
            }
        }
        if (result != null) { bitmap = result.first; aspect = result.second }
    }

    Surface(color = Color(0xFF151A24), shape = RoundedCornerShape(12.dp), modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(6.dp)) {
            val bmp = bitmap
            if (bmp == null) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspect), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.primary) }
            } else {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(8.dp))) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "PDF page ${pageIndex + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
                    PdfPageAnnotationLayer(
                        pageIndex = pageIndex, pageAnnotation = pageAnnotation, selectedTool = selectedTool,
                        selectedColorArgb = selectedColorArgb, strokeWidthNorm = strokeWidthNorm, opacity = opacity,
                        onAddStroke = onAddStroke, onEraseNear = onEraseNear, onTapForTextBox = onTapForTextBox,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.End) {
                Text("Page ${pageIndex + 1} / $pageCount", style = MaterialTheme.typography.labelSmall, color = theme.onSurfaceVariant)
            }
        }
    }
}
