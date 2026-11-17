package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.BookmarkItem
import com.example.data.model.BookmarkType
import com.example.ui.components.Tactile3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.components.TactileIconOrb
import com.example.ui.theme.LocalStudyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkItem>,
    onOpenBookmark: (BookmarkItem) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalStudyTheme.current
    var selectedType by remember { mutableStateOf<BookmarkType?>(null) }

    val filtered = remember(bookmarks, selectedType) {
        if (selectedType == null) bookmarks else bookmarks.filter { it.bookmarkType == selectedType?.name }
    }

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background, titleContentColor = theme.onBackground),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("bookmarks_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Saved Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.titleLarge, color = theme.primary) }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 920.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("All (${bookmarks.size})") }) }
                    items(BookmarkType.entries) { t ->
                        val count = bookmarks.count { it.bookmarkType == t.name }
                        val label = when (t) {
                            BookmarkType.IMPORTANT_QUESTION -> "Important Qs ($count)"
                            BookmarkType.WRONG_QUESTION -> "Wrong Qs ($count)"
                            BookmarkType.FAVORITE_NOTE -> "Notes ($count)"
                            BookmarkType.SAVED_TOPIC -> "Topics ($count)"
                            BookmarkType.PDF_PAGE -> "PDF Pages ($count)"
                        }
                        FilterChip(selected = selectedType == t, onClick = { selectedType = t }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    Tactile3DCard(accentColor = theme.primary, modifier = Modifier.fillMaxWidth()) {
                        Text("इस श्रेणी में कोई बुकमार्क नहीं है। नोट्स, PDF पेज, टॉपिक या टेस्ट के महत्वपूर्ण/गलत प्रश्नों पर बुकमार्क आइकन दबाएं।", color = theme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { item ->
                            val bType = BookmarkType.fromRaw(item.bookmarkType)
                            val accent = when (bType) {
                                BookmarkType.WRONG_QUESTION -> theme.error
                                BookmarkType.IMPORTANT_QUESTION -> theme.secondary
                                BookmarkType.PDF_PAGE -> theme.tertiary
                                else -> theme.primary
                            }
                            val icon = when (bType) {
                                BookmarkType.WRONG_QUESTION -> Icons.Rounded.ReportProblem
                                BookmarkType.IMPORTANT_QUESTION -> Icons.Rounded.Quiz
                                BookmarkType.PDF_PAGE -> Icons.Rounded.PictureAsPdf
                                BookmarkType.SAVED_TOPIC -> Icons.Rounded.Star
                                BookmarkType.FAVORITE_NOTE -> Icons.Rounded.Bookmark
                            }
                            Tactile3DCard(accentColor = accent, onClick = { onOpenBookmark(item) }, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    TactileIconOrb(icon = icon, tint = accent, size = 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleSmall, color = theme.onSurface, maxLines = 2)
                                        if (item.subtitle.isNotBlank()) {
                                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = theme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Tactile3DBadge(
                                            text = if (bType == BookmarkType.PDF_PAGE) "Page ${item.pageIndex + 1}" else bType.name.replace('_', ' '),
                                            color = accent
                                        )
                                    }
                                    IconButton(onClick = { onDeleteBookmark(item.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove bookmark", tint = theme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
