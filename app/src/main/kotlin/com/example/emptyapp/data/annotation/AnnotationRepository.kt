package com.example.emptyapp.data.annotation

import android.content.Context
import com.example.emptyapp.data.storage.AtomicJsonFile
import com.example.emptyapp.data.sync.SyncTombstoneStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

class AnnotationRepository(
  bookmarksFile: File,
  highlightsFile: File,
  private val tombstones: SyncTombstoneStore? = null,
) {
  constructor(
    context: Context,
    tombstones: SyncTombstoneStore,
  ) : this(
    bookmarksFile = File(context.filesDir, "bookmarks.json"),
    highlightsFile = File(context.filesDir, "highlights.json"),
    tombstones = tombstones,
  )

  private val bookmarksData =
    PersistedList(AtomicJsonFile(bookmarksFile, ListSerializer(BookmarkEntry.serializer())))
  private val highlightsData =
    PersistedList(AtomicJsonFile(highlightsFile, ListSerializer(HighlightEntry.serializer())))
  private val writeMutex = Mutex()
  private val mutableLastWriteError = MutableStateFlow(false)

  val bookmarks: StateFlow<List<BookmarkEntry>> = bookmarksData.state.asStateFlow()
  val highlights: StateFlow<List<HighlightEntry>> = highlightsData.state.asStateFlow()
  val lastWriteError: StateFlow<Boolean> = mutableLastWriteError.asStateFlow()

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
    commitBookmarks(beforeStateChange = { markSyncDeleted(id, deleted = true) }) { list ->
      list.filterNot { it.id == id }
    }
  }

  suspend fun restoreBookmark(entry: BookmarkEntry) {
    commitBookmarks(beforeStateChange = { markSyncDeleted(entry.id, deleted = false) }) { list ->
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
    commitHighlights(beforeStateChange = { markSyncDeleted(id, deleted = true) }) { list ->
      list.filterNot { it.id == id }
    }
  }

  suspend fun restoreHighlight(entry: HighlightEntry) {
    commitHighlights(beforeStateChange = { markSyncDeleted(entry.id, deleted = false) }) { list ->
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
    if (remoteBookmarks.isNotEmpty() || bookmarksData.dirty) {
      commitBookmarks { local ->
        val byId = LinkedHashMap<String, BookmarkEntry>()
        (local + remoteBookmarks)
          .sortedByDescending { it.createdAt }
          .forEach { byId.putIfAbsent(it.id, it) }
        byId.values.toList()
      }
    }
    if (remoteHighlights.isNotEmpty() || highlightsData.dirty) {
      commitHighlights { local ->
        val byId = LinkedHashMap<String, HighlightEntry>()
        (local + remoteHighlights)
          .sortedByDescending { it.createdAt }
          .forEach { byId.putIfAbsent(it.id, it) }
        byId.values.toList()
      }
    }
  }

  suspend fun dropDeleted(ids: Set<String>) {
    if (ids.isEmpty() && !bookmarksData.dirty && !highlightsData.dirty) return
    if (bookmarksData.dirty || bookmarksData.state.value.any { it.id in ids }) {
      commitBookmarks { list -> list.filterNot { it.id in ids } }
    }
    if (highlightsData.dirty || highlightsData.state.value.any { it.id in ids }) {
      commitHighlights { list -> list.filterNot { it.id in ids } }
    }
  }

  private suspend fun markSyncDeleted(id: String, deleted: Boolean) {
    withContext(Dispatchers.IO) { tombstones?.markAnnotations(listOf(id), deleted) }
  }

  private suspend fun commitBookmarks(
    beforeStateChange: (suspend () -> Unit)? = null,
    transform: (List<BookmarkEntry>) -> List<BookmarkEntry>,
  ) {
    writeMutex.withLock {
      bookmarksData.commit(beforeStateChange, transform)
      mutableLastWriteError.value = bookmarksData.writeError || highlightsData.writeError
    }
  }

  private suspend fun commitHighlights(
    beforeStateChange: (suspend () -> Unit)? = null,
    transform: (List<HighlightEntry>) -> List<HighlightEntry>,
  ) {
    writeMutex.withLock {
      highlightsData.commit(beforeStateChange, transform)
      mutableLastWriteError.value = bookmarksData.writeError || highlightsData.writeError
    }
  }

  private class PersistedList<E>(
    private val store: AtomicJsonFile<List<E>>,
  ) {
    val state = MutableStateFlow(store.read(emptyList()))
    @Volatile var dirty: Boolean = false
    var writeError: Boolean = false

    suspend fun commit(
      beforeStateChange: (suspend () -> Unit)? = null,
      transform: (List<E>) -> List<E>,
    ) {
      beforeStateChange?.invoke()
      val previous = state.value
      val next = transform(previous)
      val changed = next != previous
      if (!changed && !dirty) return
      if (changed) {
        state.value = next
        dirty = true
      }
      try {
        withContext(Dispatchers.IO) { store.write(state.value) }
        dirty = false
        writeError = false
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        dirty = true
        writeError = true
      }
    }
  }
}
