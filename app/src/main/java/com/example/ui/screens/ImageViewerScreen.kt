package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.StudyNote
import com.example.ui.theme.LocalStudyTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    note: StudyNote?,
    imageFile: File?,
    onDownloadSharedImage: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    var scale by remember { mutableFloatStateOf(1f) }

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
                    Text(note?.title ?: "Image Note", style = MaterialTheme.typography.titleMedium, color = theme.primary)
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
            if (imageFile == null || !imageFile.exists()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (onDownloadSharedImage != null) "यह इमेज ऑफलाइन डाउनलोड नहीं है।" else "Image file is stored locally on the upload device.",
                        color = Color.White
                    )
                    if (onDownloadSharedImage != null) {
                        Button(
                            onClick = onDownloadSharedImage,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary, contentColor = theme.onPrimary)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(Modifier.widthIn(min = 6.dp))
                            Text("Download Image")
                        }
                    }
                }
            } else {
                AsyncImage(
                    model = imageFile,
                    contentDescription = note?.title ?: "Study image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                )
            }
        }
    }
}
