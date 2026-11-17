package com.example.ui

import com.example.data.local.LocalFileStorage
import com.example.data.local.OfflineMcqDto
import com.example.data.local.OfflineNoteDto
import com.example.data.local.OfflineSubjectDto
import com.example.data.local.OfflineSubtopicDto
import com.example.data.local.OfflineTopicDto
import com.example.data.local.OfflineVideoDto
import com.example.data.local.toModel
import com.example.data.model.FullLengthExam
import com.example.data.model.McqQuestion
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.Subtopic
import com.example.data.model.Topic
import com.example.data.model.VideoLink
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedSharedCatalog(
    val subjects: List<OfflineSubjectDto> = emptyList(),
    val topics: List<OfflineTopicDto> = emptyList(),
    val subtopics: List<OfflineSubtopicDto> = emptyList(),
    val contents: List<SharedContentCacheDto> = emptyList(),
    val videos: List<OfflineVideoDto> = emptyList(),
    val mcqs: List<OfflineMcqDto> = emptyList(),
)

@Serializable
data class SharedContentCacheDto(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val subtopicId: String,
    val folderName: String = "",
    val title: String,
    val noteType: String,
    val localFileName: String? = null,
    val htmlContent: String? = null,
    val storagePath: String? = null,
    val downloadUrl: String? = null,
    val version: Long = 1L,
    val downloadsCount: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val isMockTest: Boolean = false,
)

object SharedCatalogMergeHelper {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun cacheFile(fileStorage: LocalFileStorage): File =
        File(fileStorage.sharedCacheDir(), "shared_catalog_cache.json")

    fun loadCachedCatalog(fileStorage: LocalFileStorage): CachedSharedCatalog {
        val file = cacheFile(fileStorage)
        if (!file.exists()) return CachedSharedCatalog()
        return runCatching {
            json.decodeFromString<CachedSharedCatalog>(file.readText(Charsets.UTF_8))
        }.getOrDefault(CachedSharedCatalog())
    }

    fun saveCachedCatalog(
        fileStorage: LocalFileStorage,
        subjects: List<Subject>,
        topics: List<Topic>,
        subtopics: List<Subtopic>,
        contents: List<StudyNote>,
        videos: List<VideoLink>,
        mcqs: List<McqQuestion>,
    ) {
        runCatching {
            val dto = CachedSharedCatalog(
                subjects = subjects.map { OfflineSubjectDto(it.id, it.nameEn, it.nameHi, it.iconKey, it.colorHex, it.sortOrder) },
                topics = topics.map { OfflineTopicDto(it.id, it.subjectId, it.name, it.description) },
                subtopics = subtopics.map { OfflineSubtopicDto(it.id, it.subjectId, it.topicId, it.name, it.lastStudiedDate) },
                contents = contents.map {
                    SharedContentCacheDto(
                        id = it.id, subjectId = it.subjectId, topicId = it.topicId, subtopicId = it.subtopicId,
                        folderName = it.folderName, title = it.title, noteType = it.noteType,
                        localFileName = it.localFileName, htmlContent = it.htmlContent,
                        storagePath = it.storagePath, downloadUrl = it.downloadUrl,
                        version = it.version, downloadsCount = it.downloadsCount,
                        fileSizeBytes = it.fileSizeBytes, isMockTest = it.isMockTest,
                    )
                },
                videos = videos.map {
                    OfflineVideoDto(it.id, it.subjectId, it.topicId, it.subtopicId, it.title, it.youtubeUrl, it.channelOrNote, it.lastWatchedDate)
                },
                mcqs = mcqs.map {
                    OfflineMcqDto(it.id, it.subjectId, it.topicId, it.subtopicId, it.question, it.options, it.correctIndex, it.explanation)
                },
            )
            cacheFile(fileStorage).writeText(json.encodeToString(dto), Charsets.UTF_8)
        }
    }

    fun CachedSharedCatalog.toSharedSubjects(): List<Subject> =
        subjects.map { it.toModel("shared").copy(isShared = true, published = true) }

    fun CachedSharedCatalog.toSharedTopics(): List<Topic> =
        topics.map { it.toModel("shared").copy(isShared = true, published = true) }

    fun CachedSharedCatalog.toSharedSubtopics(): List<Subtopic> =
        subtopics.map { it.toModel("shared").copy(isShared = true, published = true) }

    fun CachedSharedCatalog.toSharedContents(): List<StudyNote> =
        contents.map {
            StudyNote(
                id = it.id, userId = "shared", subjectId = it.subjectId, topicId = it.topicId,
                subtopicId = it.subtopicId, folderName = it.folderName, title = it.title,
                noteType = it.noteType, localFileName = it.localFileName, htmlContent = it.htmlContent,
                isShared = true, storagePath = it.storagePath, downloadUrl = it.downloadUrl,
                version = it.version, published = true, downloadsCount = it.downloadsCount,
                fileSizeBytes = it.fileSizeBytes, isMockTest = it.isMockTest,
            )
        }

    fun CachedSharedCatalog.toSharedVideos(): List<VideoLink> =
        videos.map { it.toModel("shared").copy(isShared = true, published = true) }

    fun CachedSharedCatalog.toSharedMcqs(): List<McqQuestion> =
        mcqs.map { it.toModel("shared").copy(isShared = true, published = true) }

    fun mergeSubjects(personal: List<Subject>, shared: List<Subject>): List<Subject> {
        val map = LinkedHashMap<String, Subject>()
        personal.forEach { map[it.id] = it }
        shared.filter { it.published }.forEach { s -> map[s.id] = s.copy(isShared = true) }
        return map.values.sortedBy { it.sortOrder }
    }

    fun mergeTopics(personal: List<Topic>, shared: List<Topic>): List<Topic> {
        val map = LinkedHashMap<String, Topic>()
        personal.forEach { map[it.id] = it }
        shared.filter { it.published }.forEach { t -> map[t.id] = t.copy(isShared = true) }
        return map.values.sortedBy { it.name.lowercase() }
    }

    fun mergeSubtopics(personal: List<Subtopic>, shared: List<Subtopic>): List<Subtopic> {
        val map = LinkedHashMap<String, Subtopic>()
        personal.forEach { map[it.id] = it }
        shared.filter { it.published }.forEach { s -> map[s.id] = s.copy(isShared = true) }
        return map.values.sortedBy { it.name.lowercase() }
    }

    fun mergeNotes(personal: List<StudyNote>, shared: List<StudyNote>): List<StudyNote> {
        val map = LinkedHashMap<String, StudyNote>()
        personal.forEach { map[it.id] = it }
        shared.filter { it.published }.forEach { n -> map[n.id] = n.copy(isShared = true) }
        return map.values.sortedByDescending { it.updatedAt?.seconds ?: 0L }
    }

    fun mergeVideos(personal: List<VideoLink>, shared: List<VideoLink>): List<VideoLink> {
        val map = LinkedHashMap<String, VideoLink>()
        personal.forEach { map[it.id] = it }
        shared.filter { it.published }.forEach { v -> map[v.id] = v.copy(isShared = true) }
        return map.values.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }

    fun mergeMcqs(personal: List<McqQuestion>, shared: List<McqQuestion>): List<McqQuestion> {
        val map = LinkedHashMap<String, McqQuestion>()
        personal.forEach { map[it.id] = it }
        shared.filter { it.published }.forEach { m -> map[m.id] = m.copy(isShared = true) }
        return map.values.sortedBy { it.createdAt?.seconds ?: 0L }
    }

    fun mergeFullLengthExams(
        builtIn: List<FullLengthExam>,
        personal: List<FullLengthExam>,
        shared: List<FullLengthExam>,
    ): List<FullLengthExam> {
        val map = LinkedHashMap<String, FullLengthExam>()
        builtIn.forEach { map[it.id] = it }
        personal.forEach { map[it.id] = it }
        shared.filter { it.published }.forEach { e -> map[e.id] = e.copy(isShared = true) }
        return map.values.toList()
    }
}
