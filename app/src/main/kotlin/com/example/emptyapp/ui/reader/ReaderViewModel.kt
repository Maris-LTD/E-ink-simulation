package com.example.emptyapp.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.EInkSettingsStore
import com.example.emptyapp.reader.RefreshFlashController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  data class Error(val message: String) : ReaderUiState

  data class Ready(
    val book: BookEntry,
    val publication: Publication,
    val initialLocator: Locator?,
  ) : ReaderUiState
}

class ReaderViewModel(
  private val bookId: String,
  private val repository: LibraryRepository,
  private val settingsStore: EInkSettingsStore,
) : ViewModel() {
  private val flashController = RefreshFlashController()
  private var publication: Publication? = null
  private var lastPositionKey: String? = null
  private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  private val mutableProgress = MutableStateFlow(0f)
  private val mutableFlashTrigger = MutableStateFlow(0)
  private val mutablePageTurnTrigger = MutableStateFlow(0)
  private val mutablePagesSinceFlash = MutableStateFlow(0)

  val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()
  val progress: StateFlow<Float> = mutableProgress.asStateFlow()
  val flashTrigger: StateFlow<Int> = mutableFlashTrigger.asStateFlow()
  val pageTurnTrigger: StateFlow<Int> = mutablePageTurnTrigger.asStateFlow()
  val pagesSinceFlash: StateFlow<Int> = mutablePagesSinceFlash.asStateFlow()
  val settings: StateFlow<EInkSettings> = settingsStore.settings

  init {
    viewModelScope.launch { load() }
  }

  fun updateSettings(transform: (EInkSettings) -> EInkSettings) {
    settingsStore.update(transform)
    flashController.reset()
    mutablePagesSinceFlash.value = 0
  }

  fun onLocatorChanged(locator: Locator) {
    val key = "${locator.href}#${locator.locations.progression}"
    val previous = lastPositionKey
    lastPositionKey = key
    val total = (locator.locations.totalProgression ?: 0.0).toFloat()
    mutableProgress.value = total
    if (previous != null && previous != key) {
      val step = flashController.onPageTurnStep(settings.value)
      mutablePagesSinceFlash.value = step.pagesSinceFlash
      if (step.fullRefresh) {
        mutableFlashTrigger.value += 1
      } else {
        mutablePageTurnTrigger.value += 1
      }
    }
    viewModelScope.launch {
      repository.saveProgress(bookId, locator.toJSON().toString(), total)
    }
  }

  private suspend fun load() {
    val book = repository.find(bookId)
    if (book == null) {
      mutableState.value = ReaderUiState.Error("Không tìm thấy sách")
      return
    }
    repository
      .engine
      .open(repository.fileOf(book))
      .onSuccess {
        publication = it
        mutableProgress.value = book.progress
        mutableState.value = ReaderUiState.Ready(book, it, restoreLocator(book))
      }
      .onFailure { mutableState.value = ReaderUiState.Error(it.message ?: "Không mở được sách") }
  }

  private fun restoreLocator(book: BookEntry): Locator? =
    book.lastLocatorJson?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

  override fun onCleared() {
    publication?.close()
  }
}
