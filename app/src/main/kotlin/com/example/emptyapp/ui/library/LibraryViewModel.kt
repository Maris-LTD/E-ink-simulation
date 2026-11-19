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
  private val messageChannel = Channel<String>(Channel.BUFFERED)
  private val tombstoneErrorFlow = tombstones?.lastWriteError ?: MutableStateFlow(false)

  val books: StateFlow<List<BookEntry>> = repository.books
  val booksLoaded: StateFlow<Boolean> = repository.loaded
  val bookmarks: StateFlow<List<BookmarkEntry>> = annotations.bookmarks
  val highlights: StateFlow<List<HighlightEntry>> = annotations.highlights
  val stats: StateFlow<ReadingStatsData> = statsRepository.stats
  val importing: StateFlow<Boolean> = mutableImporting.asStateFlow()
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

  fun import(uri: Uri) {
    launchImportJob { repository.import(uri) }
  }

  internal fun launchImportJob(performImport: suspend () -> Result<BookEntry>): Job {
    synchronized(importCountLock) {
      activeImportCount += 1
      mutableImporting.value = true
    }
    val job =
      viewModelScope.launch {
        try {
          performImport()
            .onSuccess { entry ->
              val app = context?.applicationContext as? com.example.emptyapp.EmptyApplication
              app?.container?.let { container ->
                container.appScope.launch(Dispatchers.IO) {
                  container.sync.unmarkDeletedHash(entry.contentHash)
                }
              }
              if (app != null) {
                com.example.emptyapp.system.EInkReadingWidgetProvider.refreshAll(app)
              }
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
    job.invokeOnCompletion {
      synchronized(importCountLock) {
        activeImportCount = (activeImportCount - 1).coerceAtLeast(0)
        mutableImporting.value = activeImportCount > 0
      }
    }
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

  companion object {
    private const val MAX_CAUSE_DEPTH = 8

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
