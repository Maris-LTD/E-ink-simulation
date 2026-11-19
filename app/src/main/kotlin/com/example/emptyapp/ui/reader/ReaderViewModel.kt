package com.example.emptyapp.ui.reader

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.R
import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.annotation.BookmarkEntry
import com.example.emptyapp.data.annotation.HighlightEntry
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.dictionary.DictionaryService
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.EInkSettingsStore
import com.example.emptyapp.reader.RefreshFlashController
import com.example.emptyapp.reader.pdf.PdfBookConfig
import com.example.emptyapp.reader.pdf.PdfBookConfigStore
import com.example.emptyapp.reader.pdf.PdfDocumentClassifier
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
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
  private val pdfBookConfigs: PdfBookConfigStore? = null,
  private val annotations: AnnotationRepository,
  private val dictionary: DictionaryService,
  private val statsRepository: ReadingStatsRepository,
  private val overrideLocatorJson: String? = null,
  private val overrideLocatorCreatedAt: Long = 0L,
  private val context: Context? = null,
) : ViewModel() {
  private fun str(resId: Int, fallback: String): String =
    context?.let { LocaleHelper.wrap(it).getString(resId) } ?: fallback
  private val flashController = RefreshFlashController()
  private var publication: Publication? = null
  private var searchIterator: SearchIterator? = null
  private var searchJob: Job? = null
  private var statsTickerJob: Job? = null
  private var progressSaveJob: Job? = null
  private var pendingProgress: Pair<Locator, Float>? = null
  private var lastInteractionMs: Long = 0L
  private var lastFlushMs: Long = 0L
  private var suppressPageTurnUntilMs: Long = 0L
  private var pendingPages: Int = 0
  private var lastPositionKey: String? = null
  private var currentLocator: Locator? = null
  private val currentLocatorFlow = MutableStateFlow<Locator?>(null)
  private val bookmarkMatchModeFlow = MutableStateFlow(BookmarkMatchMode.POSITION_BASED)
  private val epubPageWindowFlow = MutableStateFlow<EpubPageWindow?>(null)
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
  val writeError: StateFlow<Boolean> =
    combine(
      repository.lastWriteError,
      annotations.lastWriteError,
      statsRepository.lastWriteError,
    ) { libErr, annErr, statsErr -> libErr || annErr || statsErr }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        repository.lastWriteError.value ||
          annotations.lastWriteError.value ||
          statsRepository.lastWriteError.value,
      )

  val pdfConfig: StateFlow<PdfBookConfig> =
    (pdfBookConfigs?.configs ?: MutableStateFlow(emptyMap()))
      .map { it[bookId] ?: PdfBookConfig() }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), pdfBookConfigs?.get(bookId) ?: PdfBookConfig())

  val bookmarks: StateFlow<List<BookmarkEntry>> =
    annotations.bookmarks
      .map { list -> list.filter { it.bookId == bookId } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val parsedBookmarks: StateFlow<List<Pair<BookmarkEntry, Locator?>>> =
    bookmarks
      .map { list -> list.map { it to parseLocator(it.locatorJson) } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val highlights: StateFlow<List<HighlightEntry>> =
    annotations.highlights
      .map { list -> list.filter { it.bookId == bookId } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  val isCurrentPageBookmarked: StateFlow<Boolean> =
    combine(
      parsedBookmarks,
      currentLocatorFlow,
      bookmarkMatchModeFlow,
      epubPageWindowFlow,
    ) { list, loc, mode, pageWindow ->
      findMatchingBookmarkParsed(list, loc, mode, pageWindow) != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  init {
    viewModelScope.launch { load() }
  }

  fun updateSettings(transform: (EInkSettings) -> EInkSettings) {
    val before = settingsStore.settings.value
    settingsStore.update(transform)
    val after = settingsStore.settings.value
    val layoutChanged =
      before.font != after.font ||
        before.fontScale != after.fontScale ||
        before.fontWeight != after.fontWeight ||
        before.lineHeight != after.lineHeight ||
        before.paragraphSpacing != after.paragraphSpacing ||
        before.justifyText != after.justifyText ||
        before.pageMargins != after.pageMargins ||
        before.orientationLock != after.orientationLock ||
        before.pdfAutoCrop != after.pdfAutoCrop
    if (layoutChanged) {
      suppressPageTurnUntilMs = SystemClock.elapsedRealtime() + SETTINGS_RELAYOUT_SUPPRESS_MS
    }
    if (before.copy(brightness = after.brightness) != after) {
      flashController.reset()
      mutablePagesSinceFlash.value = 0
    }
  }

  fun previewBrightness(value: Float) {
    settingsStore.preview { it.copy(brightness = value) }
  }

  fun commitBrightness() {
    settingsStore.persist()
  }

  fun updatePdfConfig(transform: (PdfBookConfig) -> PdfBookConfig) {
    suppressPageTurnUntilMs = SystemClock.elapsedRealtime() + SETTINGS_RELAYOUT_SUPPRESS_MS
    val updated = pdfBookConfigs?.update(bookId, transform) ?: return
    val mode = updated.mode ?: return
    viewModelScope.launch {
      repository.savePdfReadingMode(bookId, mode)
    }
  }

  fun currentLocatorSnapshot(): Locator? = currentLocator

  fun onEpubPageChanged(totalPages: Int, locator: Locator) {
    epubPageWindowFlow.value =
      if (totalPages > 0) {
        EpubPageWindow(
          href = locator.href.toString(),
          progression = locator.locations.progression,
          totalPages = totalPages,
        )
      } else {
        null
      }
  }

  fun onLocatorChanged(locator: Locator) {
    notifyUserInteraction()
    currentLocator = locator
    currentLocatorFlow.value = locator
    val key = "${locator.href}#${locator.locations.progression}"
    val previous = lastPositionKey
    lastPositionKey = key
    val total = (locator.locations.totalProgression ?: 0.0).toFloat()
    mutableProgress.value = total
    val isPageTurn =
      previous != null &&
        previous != key &&
        SystemClock.elapsedRealtime() > suppressPageTurnUntilMs
    if (isPageTurn) {
      pendingPages += 1
      val step = flashController.onPageTurnStep(settings.value)
      mutablePagesSinceFlash.value = step.pagesSinceFlash
      if (step.fullRefresh) {
        mutableFlashTrigger.value += 1
      } else {
        mutablePageTurnTrigger.value += 1
      }
    }
    pendingProgress = locator to total
    if (progressSaveJob?.isActive != true) {
      progressSaveJob =
        viewModelScope.launch {
          delay(PROGRESS_SAVE_INTERVAL_MS)
          flushProgress()
        }
    }
  }

  private suspend fun flushProgress() {
    val (locator, total) = pendingProgress ?: return
    pendingProgress = null
    repository.saveProgress(bookId, locator.toJSON().toString(), total)
  }

  fun notifyUserInteraction() {
    lastInteractionMs = SystemClock.elapsedRealtime()
  }

  fun onReaderForegroundChanged(isForeground: Boolean) {
    if (isForeground) {
      val now = SystemClock.elapsedRealtime()
      lastInteractionMs = now
      lastFlushMs = now
      if (statsTickerJob?.isActive == true) return
      statsTickerJob =
        viewModelScope.launch {
          while (isActive) {
            delay(10_000L)
            flushReadingStats()
          }
        }
    } else {
      statsTickerJob?.cancel()
      statsTickerJob = null
      progressSaveJob?.cancel()
      progressSaveJob = null
      viewModelScope.launch(NonCancellable) {
        flushProgress()
        flushReadingStats()
      }
    }
  }

  private suspend fun flushReadingStats() {
    val now = SystemClock.elapsedRealtime()
    val elapsedMs = (now - lastFlushMs).coerceAtLeast(0L)
    lastFlushMs = now
    val active = (now - lastInteractionMs) < INACTIVITY_TIMEOUT_MS
    val deltaSeconds = if (active) (elapsedMs / 1000L).toInt().coerceIn(0, 60) else 0
    val deltaPages = pendingPages
    pendingPages = 0
    if (deltaSeconds > 0 || deltaPages > 0) {
      statsRepository.recordReading(deltaSeconds = deltaSeconds, deltaPages = deltaPages)
    }
  }

  fun toggleBookmark(): Boolean? {
    val locator = currentLocator ?: return null
    val existing = findMatchingBookmark(bookmarks.value, locator)
    if (existing != null) {
      viewModelScope.launch { annotations.removeBookmark(existing.id) }
      return false
    }
    val title = locator.title?.takeIf { it.isNotBlank() } ?: currentBookTitle
    val total = (locator.locations.totalProgression ?: 0.0).toFloat()
    viewModelScope.launch {
      annotations.addBookmark(bookId, title, total, locator.toJSON().toString())
    }
    return true
  }

  fun addBookmark() {
    toggleBookmark()
  }

  private fun findMatchingBookmark(list: List<BookmarkEntry>, locator: Locator?): BookmarkEntry? {
    val cached = parsedBookmarks.value
    val parsedList =
      if (cached.size == list.size && cached.indices.all { cached[it].first.id == list[it].id }) {
        cached
      } else {
        list.map { it to parseLocator(it.locatorJson) }
      }
    return findMatchingBookmarkParsed(
      list = parsedList,
      locator = locator,
      mode = bookmarkMatchModeFlow.value,
      pageWindow = epubPageWindowFlow.value,
    )
  }

  private fun findMatchingBookmarkParsed(
    list: List<Pair<BookmarkEntry, Locator?>>,
    locator: Locator?,
    mode: BookmarkMatchMode,
    pageWindow: EpubPageWindow?,
  ): BookmarkEntry? {
    if (locator == null) return null
    val current = locator.toBookmarkLocation()
    return list.firstOrNull { (entry, parsed) ->
      BookmarkMatcher.matches(
        mode = mode,
        current = current,
        entryLocation = parsed?.toBookmarkLocation(),
        entryProgress = entry.progress,
        pageWindow = pageWindow,
      )
    }?.first
  }

  private fun Locator.toBookmarkLocation(): BookmarkLocation =
    BookmarkLocation(
      href = href.toString(),
      position = locations.position,
      progression = locations.progression,
      totalProgression = (locations.totalProgression ?: 0.0).toFloat(),
    )

  fun removeBookmark(id: String) {
    viewModelScope.launch { annotations.removeBookmark(id) }
  }

  fun restoreBookmark(entry: BookmarkEntry) {
    viewModelScope.launch { annotations.restoreBookmark(entry) }
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

  fun restoreHighlight(entry: HighlightEntry) {
    viewModelScope.launch { annotations.restoreHighlight(entry) }
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
            DictionaryUiState.Error(clean, err.message ?: str(R.string.dict_not_found, "Không tìm thấy định nghĩa"))
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
      mutableSearchState.value = SearchUiState.Error(query, str(R.string.reader_search_unsupported, "Định dạng sách này không hỗ trợ tìm kiếm chữ."))
      return
    }
    mutableSearchState.value = SearchUiState.Searching(query)
    searchJob =
      viewModelScope.launch {
        try {
          val iterator = pub.search(query)
          searchIterator = iterator
          loadNextSearchPage(query, reset = true)
        } catch (e: CancellationException) {
          throw e
        } catch (err: Throwable) {
          mutableSearchState.value = SearchUiState.Error(query, formatSearchError(err))
        }
      }
  }

  fun loadMoreSearchResults() {
    val current = mutableSearchState.value as? SearchUiState.Results ?: return
    if (!current.hasMore) return
    searchJob?.cancel()
    searchJob =
      viewModelScope.launch {
        try {
          loadNextSearchPage(current.query, reset = false)
        } catch (e: CancellationException) {
          throw e
        } catch (err: Throwable) {
          mutableSearchState.value = SearchUiState.Error(current.query, formatSearchError(err))
        }
      }
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
        mutableSearchState.value = SearchUiState.Error(query, formatSearchError(err))
      }
  }

  internal fun formatSearchError(error: Any? = null): String {
    if (error is CancellationException) throw error
    return str(R.string.reader_search_error, "Error loading search results")
  }

  fun retryLoad() {
    mutableState.value = ReaderUiState.Loading
    viewModelScope.launch { load() }
  }

  private suspend fun load() {
    val book = repository.find(bookId)
    if (book == null) {
      mutableState.value = ReaderUiState.Error(str(R.string.reader_book_not_found, "Không tìm thấy sách"))
      return
    }
    currentBookTitle = book.title
    val file = repository.fileOf(book)
    if (!file.exists()) {
      mutableState.value = ReaderUiState.Error(str(R.string.reader_book_not_found, "Không tìm thấy sách"))
      return
    }
    repository
      .engine
      .open(file)
      .onSuccess { pub ->
        publication = pub
        bookmarkMatchModeFlow.value =
          BookmarkMatcher.matchModeFor(
            format = book.format,
            isFixedLayout = pub.metadata.presentation.layout == EpubLayout.FIXED,
          )
        epubPageWindowFlow.value = null
        mutableProgress.value = book.progress
        val useOverride =
          overrideLocatorJson != null &&
            (overrideLocatorCreatedAt <= 0L || overrideLocatorCreatedAt >= book.updatedAt)
        val initial =
          (if (useOverride) parseLocator(overrideLocatorJson) else null)
            ?: parseLocator(book.lastLocatorJson)
        currentLocator = initial
        currentLocatorFlow.value = initial
        val positions = runCatching { pub.positions() }.getOrDefault(emptyList())
        mutableState.value = ReaderUiState.Ready(book, file, pub, initial, pub.tableOfContents, positions)
        if (book.format == BookFormat.PDF) {
          val savedMode = book.pdfReadingMode
          if (savedMode != null && pdfBookConfigs?.get(bookId)?.mode == null) {
            pdfBookConfigs?.update(bookId) { it.copy(mode = savedMode) }
          }
          ensurePdfClassified(file)
        }
      }
      .onFailure { mutableState.value = ReaderUiState.Error(str(R.string.reader_cannot_open_book, "Không mở được sách")) }
  }

  private fun ensurePdfClassified(file: File) {
    val store = pdfBookConfigs ?: return
    if (store.get(bookId)?.category != null) return
    viewModelScope.launch {
      val result =
        withContext(Dispatchers.IO) {
          val cached =
            context?.let {
              com.example.emptyapp.reader.pdf.PdfDocumentCache(File(it.filesDir, "pdf_cache")).load(file)
            }
          if (!cached.isNullOrEmpty()) {
            PdfDocumentClassifier.classifyCached(cached)
          } else {
            context?.let { com.example.emptyapp.reader.pdf.PdfPageAnalyzer.ensureInitialized(it) }
            PdfDocumentClassifier.classifyFile(file)
          }
        } ?: return@launch
      val updated =
        store.update(bookId) { current ->
          current.copy(
            category = current.category ?: result.category,
            mode = current.mode ?: result.recommendedMode,
            reflowAllowed = result.reflowAllowed,
          )
        }
      updated.mode?.let { mode ->
        repository.savePdfReadingMode(bookId, mode)
      }
    }
  }

  private fun parseLocator(jsonText: String?): Locator? =
    jsonText?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

  override fun onCleared() {
    val modeToPersist = pdfBookConfigs?.get(bookId)?.mode
    val progressToPersist = pendingProgress
    if (modeToPersist != null || progressToPersist != null) {
      kotlinx.coroutines.CoroutineScope(Dispatchers.IO + NonCancellable).launch {
        progressToPersist?.let { (locator, total) ->
          repository.saveProgress(bookId, locator.toJSON().toString(), total)
        }
        if (modeToPersist != null) repository.savePdfReadingMode(bookId, modeToPersist)
      }
    }
    statsTickerJob?.cancel()
    searchJob?.cancel()
    runCatching { searchIterator?.close() }
    publication?.close()
  }

  private companion object {
    const val INACTIVITY_TIMEOUT_MS = 120_000L
    const val PROGRESS_SAVE_INTERVAL_MS = 1_500L
    const val SETTINGS_RELAYOUT_SUPPRESS_MS = 1_000L
  }
}
