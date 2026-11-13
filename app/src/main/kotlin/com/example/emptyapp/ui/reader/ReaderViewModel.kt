package com.example.emptyapp.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.annotation.BookmarkEntry
import com.example.emptyapp.data.annotation.HighlightEntry
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.dictionary.DictionaryService
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.EInkSettingsStore
import com.example.emptyapp.reader.RefreshFlashController
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search

sealed interface SearchUiState {
  data object Idle : SearchUiState

  data class Searching(val query: String) : SearchUiState

  data class Results(
    val query: String,
    val items: List<Locator>,
    val hasMore: Boolean,
  ) : SearchUiState

  data class Error(val query: String, val message: String) : SearchUiState
}

sealed interface ReaderUiState {
  data object Loading : ReaderUiState

  data class Error(val message: String) : ReaderUiState

  data class Ready(
    val book: BookEntry,
    val file: File,
    val publication: Publication,
    val initialLocator: Locator?,
    val toc: List<Link>,
    val positions: List<Locator>,
  ) : ReaderUiState
}

class ReaderViewModel(
  private val bookId: String,
  private val repository: LibraryRepository,
  private val settingsStore: EInkSettingsStore,
  private val annotations: AnnotationRepository,
  private val dictionary: DictionaryService,
  private val overrideLocatorJson: String? = null,
) : ViewModel() {
  private val flashController = RefreshFlashController()
  private var publication: Publication? = null
  private var searchIterator: SearchIterator? = null
  private var searchJob: Job? = null
  private var lastPositionKey: String? = null
  private var currentLocator: Locator? = null
  private var currentBookTitle: String = ""

  private val mutableState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
  private val mutableProgress = MutableStateFlow(0f)
  private val mutableFlashTrigger = MutableStateFlow(0)
  private val mutablePageTurnTrigger = MutableStateFlow(0)
  private val mutablePagesSinceFlash = MutableStateFlow(0)
  private val mutableDictionaryState = MutableStateFlow<DictionaryUiState?>(null)
  private val mutableSearchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)

  val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()
  val progress: StateFlow<Float> = mutableProgress.asStateFlow()
  val flashTrigger: StateFlow<Int> = mutableFlashTrigger.asStateFlow()
  val pageTurnTrigger: StateFlow<Int> = mutablePageTurnTrigger.asStateFlow()
  val pagesSinceFlash: StateFlow<Int> = mutablePagesSinceFlash.asStateFlow()
  val dictionaryState: StateFlow<DictionaryUiState?> = mutableDictionaryState.asStateFlow()
  val searchState: StateFlow<SearchUiState> = mutableSearchState.asStateFlow()
  val settings: StateFlow<EInkSettings> = settingsStore.settings

  val bookmarks: StateFlow<List<BookmarkEntry>> =
    annotations.bookmarks
      .map { list -> list.filter { it.bookId == bookId } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val highlights: StateFlow<List<HighlightEntry>> =
    annotations.highlights
      .map { list -> list.filter { it.bookId == bookId } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  init {
    viewModelScope.launch { load() }
  }

  fun updateSettings(transform: (EInkSettings) -> EInkSettings) {
    settingsStore.update(transform)
    flashController.reset()
    mutablePagesSinceFlash.value = 0
  }

  fun onLocatorChanged(locator: Locator) {
    currentLocator = locator
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

  fun addBookmark() {
    val locator = currentLocator ?: return
    val title = locator.title?.takeIf { it.isNotBlank() } ?: currentBookTitle
    val total = (locator.locations.totalProgression ?: 0.0).toFloat()
    viewModelScope.launch {
      annotations.addBookmark(bookId, title, total, locator.toJSON().toString())
    }
  }

  fun removeBookmark(id: String) {
    viewModelScope.launch { annotations.removeBookmark(id) }
  }

  fun addHighlight(locator: Locator, note: String = "") {
    val quote = locator.text.highlight?.takeIf { it.isNotBlank() } ?: return
    val total = (locator.locations.totalProgression ?: 0.0).toFloat()
    viewModelScope.launch {
      annotations.addHighlight(
        bookId = bookId,
        bookTitle = currentBookTitle,
        quote = quote,
        note = note,
        progress = total,
        locatorJson = locator.toJSON().toString(),
      )
    }
  }

  fun removeHighlight(id: String) {
    viewModelScope.launch { annotations.removeHighlight(id) }
  }

  fun lookupWord(rawText: String) {
    val clean = DictionaryService.normalizeQuery(rawText)
    if (clean.isEmpty()) return
    mutableDictionaryState.value = DictionaryUiState.Loading(clean)
    viewModelScope.launch {
      dictionary
        .lookup(clean)
        .onSuccess { entry -> mutableDictionaryState.value = DictionaryUiState.Success(clean, entry) }
        .onFailure { err ->
          mutableDictionaryState.value =
            DictionaryUiState.Error(clean, err.message ?: "Không tìm thấy định nghĩa")
        }
    }
  }

  fun dismissDictionary() {
    mutableDictionaryState.value = null
  }

  fun searchInBook(rawQuery: String) {
    val query = rawQuery.trim()
    searchJob?.cancel()
    runCatching { searchIterator?.close() }
    searchIterator = null
    if (query.isEmpty()) {
      mutableSearchState.value = SearchUiState.Idle
      return
    }
    val pub = publication ?: return
    if (!pub.isSearchable) {
      mutableSearchState.value = SearchUiState.Error(query, "Định dạng sách này không hỗ trợ tìm kiếm chữ.")
      return
    }
    mutableSearchState.value = SearchUiState.Searching(query)
    searchJob =
      viewModelScope.launch {
        val iterator = pub.search(query)
        searchIterator = iterator
        loadNextSearchPage(query, reset = true)
      }
  }

  fun loadMoreSearchResults() {
    val current = mutableSearchState.value as? SearchUiState.Results ?: return
    if (!current.hasMore) return
    searchJob?.cancel()
    searchJob = viewModelScope.launch { loadNextSearchPage(current.query, reset = false) }
  }

  private suspend fun loadNextSearchPage(query: String, reset: Boolean) {
    val iterator = searchIterator ?: return
    iterator
      .next()
      .onSuccess { collection ->
        val existing =
          if (reset) emptyList()
          else (mutableSearchState.value as? SearchUiState.Results)?.items.orEmpty()
        val newItems = collection?.locators.orEmpty()
        mutableSearchState.value =
          SearchUiState.Results(
            query = query,
            items = existing + newItems,
            hasMore = collection != null && newItems.isNotEmpty(),
          )
      }
      .onFailure { err ->
        mutableSearchState.value = SearchUiState.Error(query, err.message ?: "Lỗi khi tải kết quả")
      }
  }

  private suspend fun load() {
    val book = repository.find(bookId)
    if (book == null) {
      mutableState.value = ReaderUiState.Error("Không tìm thấy sách")
      return
    }
    currentBookTitle = book.title
    val file = repository.fileOf(book)
    repository
      .engine
      .open(file)
      .onSuccess { pub ->
        publication = pub
        mutableProgress.value = book.progress
        val initial = parseLocator(overrideLocatorJson) ?: parseLocator(book.lastLocatorJson)
        val positions = runCatching { pub.positions() }.getOrDefault(emptyList())
        mutableState.value = ReaderUiState.Ready(book, file, pub, initial, pub.tableOfContents, positions)
      }
      .onFailure { mutableState.value = ReaderUiState.Error(it.message ?: "Không mở được sách") }
  }

  private fun parseLocator(jsonText: String?): Locator? =
    jsonText?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

  override fun onCleared() {
    searchJob?.cancel()
    runCatching { searchIterator?.close() }
    publication?.close()
  }
}
