package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SubjectsHomeRoute

@Serializable
data class TopicsRoute(val subjectId: String)

@Serializable
data class SubtopicsRoute(val subjectId: String, val topicId: String)

@Serializable
data class SubtopicDetailRoute(val subjectId: String, val topicId: String, val subtopicId: String)

@Serializable
data class PdfViewerRoute(
    val noteId: String = "",
    val localFileName: String? = null,
    val title: String? = null,
    val initialPage: Int = 0,
)

@Serializable
data class ImageViewerRoute(val noteId: String)

@Serializable
data class HtmlTestRoute(
    val noteId: String,
    val subjectId: String,
    val topicId: String,
    val subtopicId: String,
    val isTestMode: Boolean = true,
)

@Serializable
data class TestResultRoute(
    val resultId: String,
)

@Serializable
data class McqPracticeRoute(
    val subjectId: String,
    val topicId: String,
    val subtopicId: String,
)

@Serializable
data class PersonalNotesRoute(
    val subjectId: String = "",
    val noteId: String = "",
)

@Serializable
data object BookmarksRoute

@Serializable
data object AdminPanelRoute

@Serializable
data object RevisionPlannerRoute

@Serializable
data object SettingsRoute
