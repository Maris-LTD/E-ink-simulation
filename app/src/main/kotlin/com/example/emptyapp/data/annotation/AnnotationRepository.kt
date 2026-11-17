package com.example.emptyapp.data.annotation

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AnnotationRepository(context: Context) {
  private val json = Json { ignoreUnknownKeys = true }
  private val bookmarksFile = File(context.filesDir, "bookmarks.json")
  private val highlightsFile = File(context.filesDir, "highlights.json")

  private val bookmarkState = MutableStateFlow(readList(bookmarksFile, ListSerializer(BookmarkEntry.serializer())))
  private val highlightState = MutableStateFlow(readList(highlightsFile, ListSerializer(HighlightEntry.serializer())))

  val bookmarks: StateFlow<List<BookmarkEntry>> = bookmarkState.asStateFlow()
  val highlights: StateFlow<List<HighlightEntry>> = highlightState.asStateFlow()

  suspend fun addBookmark(bookId: String, title: String, progress: Float, locatorJson: String) {
    val entry =
      BookmarkEntry(
        id = UUID.randomUUID().toString(),
        bookId = bookId,
        title = title,
        progress = progress,
        locatorJson = locatorJson,
        createdAt = System.currentTimeMillis(),
      )
    commitBookmarks { listOf(entry) + it }
  }

  suspend fun removeBookmark(id: String) {
    commitBookmarks { list -> list.filterNot { it.id == id } }
  }

  suspend fun restoreBookmark(entry: BookmarkEntry) {
    commitBookmarks { list ->
      if (list.any { it.id == entry.id }) list
      else (listOf(entry) + list).sortedByDescending { it.createdAt }
    }
  }

  suspend fun addHighlight(
    bookId: String,
    bookTitle: String,
    quote: String,
    note: String,
    progress: Float,
    locatorJson: String,
  ) {
    val entry =
      HighlightEntry(
        id = UUID.randomUUID().toString(),
        bookId = bookId,
        bookTitle = bookTitle,
        quote = quote.trim(),
        note = note.trim(),
        progress = progress,
        locatorJson = locatorJson,
        createdAt = System.currentTimeMillis(),
      )
    commitHighlights { listOf(entry) + it }
  }

  suspend fun removeHighlight(id: String) {
    commitHighlights { list -> list.filterNot { it.id == id } }
  }

  suspend fun restoreHighlight(entry: HighlightEntry) {
    commitHighlights { list ->
      if (list.any { it.id == entry.id }) list
      else (listOf(entry) + list).sortedByDescending { it.createdAt }
    }
  }

  suspend fun removeAllForBook(bookId: String) {
    commitBookmarks { list -> list.filterNot { it.bookId == bookId } }
    commitHighlights { list -> list.filterNot { it.bookId == bookId } }
  }

  suspend fun mergeFromRemote(
    remoteBookmarks: List<BookmarkEntry>,
    remoteHighlights: List<HighlightEntry>,
  ) {
    if (remoteBookmarks.isNotEmpty()) {
      commitBookmarks { local ->
        val byId = LinkedHashMap<String, BookmarkEntry>()
        (local + remoteBookmarks)
          .sortedByDescending { it.createdAt }
          .forEach { byId.putIfAbsent(it.id, it) }
        byId.values.toList()
      }
    }
    if (remoteHighlights.isNotEmpty()) {
      commitHighlights { local ->
        val byId = LinkedHashMap<String, HighlightEntry>()
        (local + remoteHighlights)
          .sortedByDescending { it.createdAt }
          .forEach { byId.putIfAbsent(it.id, it) }
        byId.values.toList()
      }
    }
  }

  private suspend fun commitBookmarks(transform: (List<BookmarkEntry>) -> List<BookmarkEntry>) {
    bookmarkState.update(transform)
    val snapshot = bookmarkState.value
    withContext(Dispatchers.IO) { writeList(bookmarksFile, ListSerializer(BookmarkEntry.serializer()), snapshot) }
  }

  private suspend fun commitHighlights(transform: (List<HighlightEntry>) -> List<HighlightEntry>) {
    highlightState.update(transform)
    val snapshot = highlightState.value
    withContext(Dispatchers.IO) { writeList(highlightsFile, ListSerializer(HighlightEntry.serializer()), snapshot) }
  }

  private fun <T> readList(file: File, serializer: KSerializer<List<T>>): List<T> {
    if (!file.exists()) return emptyList()
    return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
  }

  private fun <T> writeList(file: File, serializer: KSerializer<List<T>>, items: List<T>) {
    file.writeText(json.encodeToString(serializer, items))
  }
}
