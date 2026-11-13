package com.example.emptyapp.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.LibraryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {
  private val mutableImporting = MutableStateFlow(false)
  private val messageChannel = Channel<String>(Channel.BUFFERED)

  val books: StateFlow<List<BookEntry>> = repository.books
  val importing: StateFlow<Boolean> = mutableImporting.asStateFlow()
  val messages = messageChannel.receiveAsFlow()

  fun import(uri: Uri) {
    viewModelScope.launch {
      mutableImporting.value = true
      repository
        .import(uri)
        .onFailure { messageChannel.send("Không nhập được sách: ${it.message ?: "tệp không hợp lệ"}") }
      mutableImporting.value = false
    }
  }

  fun remove(id: String) {
    viewModelScope.launch { repository.remove(id) }
  }
}
