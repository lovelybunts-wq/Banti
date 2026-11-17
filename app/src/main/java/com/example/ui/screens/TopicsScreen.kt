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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderCopy
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
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.Topic
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    subject: Subject?,
    topics: List<Topic>,
    subtopics: List<Subtopic>,
    bookmarks: List<BookmarkItem> = emptyList(),
    isAdminUser: Boolean = false,
    onBack: () -> Unit,
    onTopicClick: (Topic) -> Unit,
    onBookmarkTopic: (Topic) -> Unit = {},
    onAddTopic: (String, String?) -> Unit,
    onDeleteTopic: (Topic) -> Unit,
) {
    val theme = LocalStudyTheme.current
    val accent = subject?.let { SubjectVisuals.colorFor(it.colorHex, theme.primary) } ?: theme.primary
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteTopic by remember { mutableStateOf<Topic?>(null) }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("topics_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(subject?.nameEn ?: "Topics", style = MaterialTheme.typography.titleLarge, color = accent)
                        if (!subject?.nameHi.isNullOrBlank()) {
                            Text("${subject?.nameHi} • Topics", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                        }
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
                modifier = Modifier.testTag("add_topic_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_topic))
            }
        }
    ) { innerPadding ->
        if (topics.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_notes),
                    contentDescription = "No topics yet",
                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(16.dp))
                Text("अभी कोई टॉपिक नहीं है", style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                Text("नीचे + बटन दबाकर नया टॉपिक जोड़ें", style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 920.dp).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topics, key = { it.id }) { topic ->
                        val subCount = subtopics.count { it.topicId == topic.id }
                        val isSaved = bookmarks.any { it.bookmarkType == BookmarkType.SAVED_TOPIC.name && it.refId == topic.id }
                        val canDelete = !topic.isShared || isAdminUser
                        Tactile3DCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = accent,
                            onClick = { onTopicClick(topic) },
                            testTag = "topic_card_${topic.id}"
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TactileIconOrb(icon = Icons.Rounded.FolderCopy, tint = accent, size = 44.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(topic.name, style = MaterialTheme.typography.titleMedium, color = theme.onSurface)
                                    if (!topic.description.isNullOrBlank()) {
                                        Text(topic.description, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Tactile3DBadge(text = "$subCount Sub-topics", color = accent)
                                        if (topic.isShared) Tactile3DBadge(text = "Shared", color = theme.secondary)
                                    }
                                }
                                IconButton(onClick = { onBookmarkTopic(topic) }) {
                                    Icon(
                                        imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = "Bookmark topic",
                                        tint = if (isSaved) accent else theme.onSurfaceVariant
                                    )
                                }
                                if (canDelete) {
                                    IconButton(onClick = { pendingDeleteTopic = topic }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete topic", tint = theme.onSurfaceVariant)
                                    }
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = accent)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = theme.surfaceElevated,
            title = { Text("नया टॉपिक जोड़ें (Add Topic)", color = theme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Topic Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("new_topic_name_input"))
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Short Description (Optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onAddTopic(name, desc.ifBlank { null })
                            showAddDialog = false
                        }
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = theme.onPrimary)
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    val delTopic = pendingDeleteTopic
    if (delTopic != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteTopic = null },
            containerColor = theme.surfaceElevated,
            title = { Text("टॉपिक डिलीट करें?", color = theme.onSurface) },
            text = { Text("क्या आप '${delTopic.name}' को हटाना चाहते हैं?", color = theme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTopic(delTopic)
                        pendingDeleteTopic = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.error, contentColor = theme.onPrimary)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteTopic = null }) { Text("Cancel") } }
        )
    }
}
