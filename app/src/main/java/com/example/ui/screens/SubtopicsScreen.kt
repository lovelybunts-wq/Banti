package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.McqQuestion
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.Topic
import com.example.data.model.VideoLink
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtopicsScreen(
    subject: Subject?,
    topic: Topic?,
    subtopics: List<Subtopic>,
    notes: List<StudyNote>,
    videos: List<VideoLink>,
    mcqs: List<McqQuestion>,
    onBack: () -> Unit,
    onSubtopicClick: (Subtopic) -> Unit,
    onAddSubtopic: (String) -> Unit,
    onDeleteSubtopic: (String) -> Unit,
) {
    val theme = LocalStudyTheme.current
    val accent = subject?.let { SubjectVisuals.colorFor(it.colorHex, theme.primary) } ?: theme.primary
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.background,
                    titleContentColor = theme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("subtopics_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = topic?.name ?: "Sub-topics",
                            style = MaterialTheme.typography.titleLarge,
                            color = accent
                        )
                        Text(
                            text = "${subject?.nameEn ?: ""} • Sub-topics",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = accent,
                contentColor = theme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("add_subtopic_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_subtopic))
            }
        }
    ) { innerPadding ->
        if (subtopics.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_notes),
                    contentDescription = "Empty subtopics",
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "कोई सब-टॉपिक नहीं है",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.onSurface
                )
                Text(
                    text = "नोट्स, वीडियो और टेस्ट के लिए सब-टॉपिक बनाएं",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceVariant
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 920.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                items(subtopics, key = { it.id }) { subtopic ->
                    val nCount = notes.count { it.subtopicId == subtopic.id }
                    val vCount = videos.count { it.subtopicId == subtopic.id }
                    val mCount = mcqs.count { it.subtopicId == subtopic.id }

                    Tactile3DCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accent,
                        onClick = { onSubtopicClick(subtopic) },
                        testTag = "subtopic_card_${subtopic.id}"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TactileIconOrb(
                                icon = Icons.Rounded.Bookmarks,
                                tint = accent,
                                size = 44.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subtopic.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.onSurface
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Tactile3DBadge(text = "$nCount Notes", color = accent)
                                    Tactile3DBadge(text = "$vCount Videos", color = theme.secondary)
                                    Tactile3DBadge(text = "$mCount MCQs", color = theme.tertiary)
                                }
                                if (!subtopic.lastStudiedDate.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = theme.success,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Studied: ${subtopic.lastStudiedDate}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = theme.success
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onDeleteSubtopic(subtopic.id) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete sub-topic",
                                    tint = theme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = accent
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("नया सब-टॉपिक जोड़ें (Add Sub-topic)", color = theme.onSurface) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Sub-topic Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_subtopic_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onAddSubtopic(name)
                            showAddDialog = false
                        }
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
