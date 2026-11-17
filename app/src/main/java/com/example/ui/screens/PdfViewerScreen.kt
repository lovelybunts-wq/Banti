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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.StudyNote
import com.example.ui.theme.LocalStudyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    note: StudyNote?,
    pdfFile: File?,
    onBack: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    var pageCount by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var rendererHolder by remember { mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null) }
    val renderMutex = remember { Mutex() }

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
                if (renderer.pageCount == 0) {
                    errorMsg = "इस PDF में कोई पेज नहीं मिला।"
                }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = note?.title ?: "PDF Note",
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "$pageCount Pages • ${(scale * 100).toInt()}% Zoom",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (pageCount > 0) {
                        IconButton(onClick = { scale = (scale - 0.25f).coerceAtLeast(1f) }) {
                            Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out")
                        }
                        IconButton(onClick = { scale = (scale + 0.25f).coerceAtMost(3f) }) {
                            Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0A0D14)),
            contentAlignment = Alignment.Center
        ) {
            when {
                pdfFile == null || !pdfFile.exists() -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            "PDF इस डिवाइस पर उपलब्ध नहीं है। कृपया इसे दोबारा जोड़ें या Google Drive से रिस्टोर करें।",
                            color = theme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                errorMsg != null -> {
                    Text(
                        text = errorMsg ?: "",
                        color = theme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                pageCount == 0 -> {
                    CircularProgressIndicator(color = theme.primary)
                }
                else -> {
                    val renderer = rendererHolder?.second
                    if (renderer != null) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 3.0f)
                                    }
                                }
                                .graphicsLayer(scaleX = scale, scaleY = scale),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(pageCount) { index ->
                                PdfPageTile(
                                    renderer = renderer,
                                    pageIndex = index,
                                    pageCount = pageCount,
                                    renderMutex = renderMutex,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageTile(
    renderer: PdfRenderer,
    pageIndex: Int,
    pageCount: Int,
    renderMutex: Mutex,
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
                } finally {
                    page.close()
                }
            }
        }
        if (result != null) {
            bitmap = result.first
            aspect = result.second
        }
    }

    Surface(
        color = Color(0xFF151A24),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .widthIn(max = 860.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            val bmp = bitmap
            if (bmp == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = theme.primary)
                }
            } else {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Page ${pageIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.onSurfaceVariant
                )
            }
        }
    }
}
