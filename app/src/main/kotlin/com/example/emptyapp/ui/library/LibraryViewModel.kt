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
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class LibraryViewModel(
  private val repository: LibraryRepository,
  private val annotations: AnnotationRepository,
  private val statsRepository: ReadingStatsRepository,
  private val context: Context? = null,
) : ViewModel() {
  private val mutableImporting = MutableStateFlow(false)
  private val messageChannel = Channel<String>(Channel.BUFFERED)

  val books: StateFlow<List<BookEntry>> = repository.books
  val bookmarks: StateFlow<List<BookmarkEntry>> = annotations.bookmarks
  val highlights: StateFlow<List<HighlightEntry>> = annotations.highlights
  val stats: StateFlow<ReadingStatsData> = statsRepository.stats
  val importing: StateFlow<Boolean> = mutableImporting.asStateFlow()
  val messages = messageChannel.receiveAsFlow()

  fun coverFileOf(book: BookEntry): File? = repository.coverFileOf(book)

  fun import(uri: Uri) {
    viewModelScope.launch {
      mutableImporting.value = true
      repository
        .import(uri)
        .onSuccess { entry ->
          val app = context?.applicationContext as? com.example.emptyapp.EmptyApplication
          app?.container?.sync?.unmarkDeletedHash(entry.contentHash)
        }
        .onFailure {
          val localized = context?.let(LocaleHelper::wrap)
          val fallbackInvalid = localized?.getString(R.string.lib_import_invalid_file) ?: "tệp không hợp lệ"
          val reason = it.message ?: fallbackInvalid
          val msg = localized?.getString(R.string.lib_import_error, reason) ?: "Không nhập được sách: $reason"
          messageChannel.send(msg)
        }
      mutableImporting.value = false
    }
  }

  fun remove(id: String, alsoDeleteFromCloud: Boolean = true) {
    viewModelScope.launch {
      val book = repository.find(id)
      repository.remove(id)
      annotations.removeAllForBook(id)
      val app = context?.applicationContext as? com.example.emptyapp.EmptyApplication
      if (alsoDeleteFromCloud && book?.contentHash != null) {
        app?.container?.sync?.deleteBookFromCloudAsync(book.contentHash, book.title)
      }
      if (app?.container?.lockScreenReading?.state?.value?.bookId == id) {
        app.container.lockScreenReading.clear()
        com.example.emptyapp.reader.LockScreenReadingNotifier.cancel(app)
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
}
