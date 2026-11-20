package com.example.emptyapp.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.R
import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.annotation.BookmarkEntry
import com.example.emptyapp.data.annotation.HighlightEntry
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.DuplicateBookException
import com.example.emptyapp.data.book.FolderBookScanner
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.data.sync.SyncTombstoneStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatchImportProgress(val current: Int, val total: Int)

enum class BatchImportSummaryKind {
  ALL_IMPORTED,
  IMPORTED_WITH_SKIPPED,
  ALL_DUPLICATES,
  ALL_FAILED,
}

data class BatchImportSummary(
  val kind: BatchImportSummaryKind,
  val imported: Int,
  val skipped: Int,
  val failed: Int,
)

class LibraryViewModel(
  private val repository: LibraryRepository,
  private val annotations: AnnotationRepository,
  private val statsRepository: ReadingStatsRepository,
  private val context: Context? = null,
  private val tombstones: SyncTombstoneStore? = null,
) : ViewModel() {
  private val importCountLock = Any()
  private var activeImportCount = 0
  private val mutableImporting = MutableStateFlow(false)
  private val mutableBatchImportProgress = MutableStateFlow<BatchImportProgress?>(null)
  private val messageChannel = Channel<String>(Channel.BUFFERED)
  private val tombstoneErrorFlow = tombstones?.lastWriteError ?: MutableStateFlow(false)

  val books: StateFlow<List<BookEntry>> = repository.books
  val booksLoaded: StateFlow<Boolean> = repository.loaded
  val bookmarks: StateFlow<List<BookmarkEntry>> = annotations.bookmarks
  val highlights: StateFlow<List<HighlightEntry>> = annotations.highlights
  val stats: StateFlow<ReadingStatsData> = statsRepository.stats
  val importing: StateFlow<Boolean> = mutableImporting.asStateFlow()
  val batchImportProgress: StateFlow<BatchImportProgress?> = mutableBatchImportProgress.asStateFlow()
  val writeError: StateFlow<Boolean> =
    combine(
      repository.lastWriteError,
      annotations.lastWriteError,
      statsRepository.lastWriteError,
      tombstoneErrorFlow,
    ) { libErr, annErr, statsErr, tombErr -> libErr || annErr || statsErr || tombErr }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        repository.lastWriteError.value ||
          annotations.lastWriteError.value ||
          statsRepository.lastWriteError.value ||
          tombstoneErrorFlow.value,
      )
  val messages = messageChannel.receiveAsFlow()

  fun coverFileOf(book: BookEntry): File? = repository.coverFileOf(book)

  fun bookFileOf(book: BookEntry): File = repository.fileOf(book)

  fun import(uri: Uri) {
    launchImportJob { repository.import(uri) }
  }

  fun importMultiple(uris: List<Uri>) {
    if (uris.isEmpty()) return
    if (uris.size == 1) {
      import(uris.first())
      return
    }
    acquireImportSlot()
    val job =
      viewModelScope.launch {
        try {
          runBatchImport(uris)
        } catch (e: CancellationException) {
          throw e
        } catch (t: Throwable) {
          messageChannel.send(formatImportErrorMessage(t))
        }
      }
    job.invokeOnCompletion { releaseImportSlot() }
  }

  fun importFolder(treeUri: Uri) {
    val appCtx = context?.applicationContext ?: return
    acquireImportSlot()
    val job =
      viewModelScope.launch {
        try {
          val scannedUris = withContext(Dispatchers.IO) { FolderBookScanner.scanTreeUri(appCtx, treeUri) }
          when {
            scannedUris.isEmpty() ->
              messageChannel.send(localizedString(R.string.lib_import_folder_empty, "No EPUB, PDF, or CBZ files found in this folder."))
            scannedUris.size == 1 ->
              repository.import(scannedUris.first())
                .onSuccess { entry ->
                  unmarkBookDeletedInSync(entry.contentHash)
                  refreshWidget()
                }
                .onFailure { err ->
                  if (err is CancellationException) throw err
                  messageChannel.send(formatImportErrorMessage(err))
                }
            else -> runBatchImport(scannedUris)
          }
        } catch (e: CancellationException) {
          throw e
        } catch (t: Throwable) {
          messageChannel.send(formatImportErrorMessage(t))
        }
      }
    job.invokeOnCompletion { releaseImportSlot() }
  }

  fun importAndOpenExternal(uri: Uri, onBookReady: (String) -> Unit) {
    acquireImportSlot()
    val job =
      viewModelScope.launch {
        try {
          repository.importOrFindExisting(uri)
            .onSuccess { entry ->
              unmarkBookDeletedInSync(entry.contentHash)
              refreshWidget()
              onBookReady(entry.id)
            }
            .onFailure { err ->
              if (err is CancellationException) throw err
              messageChannel.send(formatImportErrorMessage(err))
            }
        } catch (e: CancellationException) {
          throw e
        } catch (t: Throwable) {
          messageChannel.send(formatImportErrorMessage(t))
        }
      }
    job.invokeOnCompletion { releaseImportSlot() }
  }

  private suspend fun runBatchImport(uris: List<Uri>) {
    var imported = 0
    var duplicates = 0
    var failed = 0
    try {
      uris.forEachIndexed { index, uri ->
        mutableBatchImportProgress.value = BatchImportProgress(current = index + 1, total = uris.size)
        val result = repository.import(uri)
        val err = result.exceptionOrNull()
        if (err is CancellationException) throw err
        val entry = result.getOrNull()
        if (entry != null) {
          imported++
          unmarkBookDeletedInSync(entry.contentHash)
        } else if (err is DuplicateBookException) {
          duplicates++
        } else {
          failed++
        }
      }
    } finally {
      mutableBatchImportProgress.value = null
    }
    if (imported > 0) {
      refreshWidget()
    }
    val summary = summarizeBatchImport(imported = imported, duplicates = duplicates, failed = failed)
    messageChannel.send(formatBatchSummaryMessage(summary))
  }

  private fun formatBatchSummaryMessage(summary: BatchImportSummary): String {
    val localized = context?.let(LocaleHelper::wrap)
    return when (summary.kind) {
      BatchImportSummaryKind.ALL_IMPORTED ->
        localized?.getString(R.string.lib_import_batch_added, summary.imported)
          ?: "Imported ${summary.imported} books"
      BatchImportSummaryKind.IMPORTED_WITH_SKIPPED ->
        localized?.getString(R.string.lib_import_batch_added_skipped, summary.imported, summary.skipped)
          ?: "Imported ${summary.imported} books (${summary.skipped} skipped)"
      BatchImportSummaryKind.ALL_DUPLICATES ->
        localized?.getString(R.string.lib_import_batch_all_duplicates)
          ?: "All selected books are already in your library."
      BatchImportSummaryKind.ALL_FAILED ->
        localized?.getString(R.string.lib_import_batch_failed, summary.failed)
          ?: "Could not import selected books (${summary.failed} failed)."
    }
  }

  private fun acquireImportSlot() {
    synchronized(importCountLock) {
      activeImportCount += 1
      mutableImporting.value = true
    }
  }

  private fun releaseImportSlot() {
    synchronized(importCountLock) {
      activeImportCount = (activeImportCount - 1).coerceAtLeast(0)
      mutableImporting.value = activeImportCount > 0
    }
  }

  private fun unmarkBookDeletedInSync(contentHash: String?) {
    val app = context?.applicationContext as? com.example.emptyapp.EmptyApplication ?: return
    app.container.appScope.launch(Dispatchers.IO) {
      app.container.sync.unmarkDeletedHash(contentHash)
    }
  }

  private fun refreshWidget() {
    val app = context?.applicationContext as? com.example.emptyapp.EmptyApplication ?: return
    com.example.emptyapp.system.EInkReadingWidgetProvider.refreshAll(app)
  }

  private fun localizedString(resId: Int, fallback: String): String =
    context?.let { LocaleHelper.wrap(it).getString(resId) } ?: fallback

  internal fun launchImportJob(performImport: suspend () -> Result<BookEntry>): Job {
    acquireImportSlot()
    val job =
      viewModelScope.launch {
        try {
          performImport()
            .onSuccess { entry ->
              unmarkBookDeletedInSync(entry.contentHash)
              refreshWidget()
            }
            .onFailure { err ->
              if (err is CancellationException) throw err
              messageChannel.send(formatImportErrorMessage(err))
            }
        } catch (e: Throwable) {
          if (e is CancellationException) throw e
          messageChannel.send(formatImportErrorMessage(e))
        }
      }
    job.invokeOnCompletion { releaseImportSlot() }
    return job
  }

  internal fun formatImportErrorMessage(error: Throwable): String {
    if (error is CancellationException) throw error
    val localized = context?.let(LocaleHelper::wrap)
    return formatImportError(
      error = error,
      knownLocalizedReasons =
        setOf(
          localized?.getString(R.string.lib_import_duplicate) ?: "this book is already in your library",
          localized?.getString(R.string.import_unsupported_format) ?: "Only EPUB, PDF, and CBZ are supported",
          localized?.getString(R.string.import_cannot_open_file) ?: "Cannot open file",
        ),
      storageFullReason = localized?.getString(R.string.lib_error_storage_full) ?: "Device storage is full",
      invalidFileReason = localized?.getString(R.string.lib_import_invalid_file) ?: "invalid file",
      genericFailedMessage = localized?.getString(R.string.lib_import_failed) ?: "Could not import book",
      formatWithReason = { reason ->
        localized?.getString(R.string.lib_import_error, reason) ?: "Could not import book: $reason"
      },
    )
  }

  fun remove(id: String, alsoDeleteFromCloud: Boolean = true) {
    viewModelScope.launch {
      repository.awaitLoaded()
      val book = repository.find(id)
      repository.remove(id)
      annotations.removeAllForBook(id)
      val app = context?.applicationContext as? com.example.emptyapp.EmptyApplication
      if (alsoDeleteFromCloud && book?.contentHash != null) {
        app?.container?.let { container ->
          container.appScope.launch(Dispatchers.IO) {
            container.sync.deleteBookFromCloudAsync(book.contentHash, book.title)
          }
        }
      }
      if (app?.container?.lockScreenReading?.state?.value?.bookId == id) {
        app.container.lockScreenReading.clear()
        com.example.emptyapp.reader.LockScreenReadingNotifier.cancel(app)
      }
      if (app != null) {
        com.example.emptyapp.system.EInkReadingWidgetProvider.refreshAll(app)
      }
    }
  }

  fun removeHighlight(id: String) {
    viewModelScope.launch { annotations.removeHighlight(id) }
  }

  fun restoreHighlight(entry: HighlightEntry) {
    viewModelScope.launch { annotations.restoreHighlight(entry) }
  }

  fun removeBookmark(id: String) {
    viewModelScope.launch { annotations.removeBookmark(id) }
  }

  fun restoreBookmark(entry: BookmarkEntry) {
    viewModelScope.launch { annotations.restoreBookmark(entry) }
  }

  fun setDailyGoalMinutes(minutes: Int) {
    viewModelScope.launch { statsRepository.setDailyGoalMinutes(minutes) }
  }

  fun setBookCompleted(id: String, completed: Boolean) {
    viewModelScope.launch {
      repository.markCompleted(id, completed)
      refreshWidget()
      val msg =
        if (completed) {
          localizedString(R.string.lib_marked_finished, "Marked as finished")
        } else {
          localizedString(R.string.lib_marked_unread, "Marked as unread")
        }
      messageChannel.send(msg)
    }
  }

  companion object {
    private const val MAX_CAUSE_DEPTH = 8

    fun summarizeBatchImport(imported: Int, duplicates: Int, failed: Int): BatchImportSummary {
      val skipped = duplicates + failed
      val kind =
        when {
          imported > 0 && skipped == 0 -> BatchImportSummaryKind.ALL_IMPORTED
          imported > 0 -> BatchImportSummaryKind.IMPORTED_WITH_SKIPPED
          duplicates > 0 && failed == 0 -> BatchImportSummaryKind.ALL_DUPLICATES
          else -> BatchImportSummaryKind.ALL_FAILED
        }
      return BatchImportSummary(kind = kind, imported = imported, skipped = skipped, failed = failed)
    }

    fun isStorageFullError(error: Throwable?): Boolean {
      var current = error
      var depth = 0
      while (current != null && depth < MAX_CAUSE_DEPTH) {
        val msg = current.message.orEmpty()
        if (
          msg.contains("ENOSPC", ignoreCase = true) ||
            msg.contains("No space left", ignoreCase = true) ||
            msg.contains("Disk full", ignoreCase = true) ||
            msg.contains("out of space", ignoreCase = true)
        ) {
          return true
        }
        if (current.cause === current) break
        current = current.cause
        depth++
      }
      return false
    }

    fun formatImportError(
      error: Throwable,
      knownLocalizedReasons: Set<String>,
      storageFullReason: String,
      invalidFileReason: String,
      genericFailedMessage: String,
      formatWithReason: (String) -> String,
    ): String {
      if (error is CancellationException) throw error
      val rawMessage = error.message?.trim()
      val reason =
        when {
          rawMessage != null && rawMessage in knownLocalizedReasons -> rawMessage
          isStorageFullError(error) -> storageFullReason
          invalidFileReason.isNotBlank() -> invalidFileReason
          else -> return genericFailedMessage
        }
      return formatWithReason(reason)
    }
  }
}
