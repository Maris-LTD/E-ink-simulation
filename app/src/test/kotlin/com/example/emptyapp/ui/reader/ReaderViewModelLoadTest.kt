package com.example.emptyapp.ui.reader

import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.data.book.BookIndexStore
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.dictionary.DictionaryService
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.EInkSettingsStore
import com.example.emptyapp.reader.pdf.PdfBookConfigStore
import com.example.emptyapp.reader.pdf.PdfReadingMode
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelLoadTest {

  @Test
  fun loadWaitsForRepositoriesBeforeFindingBookAndEvaluatingEngine() = runTest {
    val dir = createTempDirectory("reader_vm_load_test").toFile()
    val booksDir = File(dir, "books").apply { mkdirs() }
    val ioDispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    var vmRef: ReaderViewModel? = null
    try {
      val bookFile = File(booksDir, "book-cold.epub").apply { writeText("epub-data") }
      assertTrue(bookFile.exists())
      BookIndexStore(File(dir, "library.json")).write(
        listOf(
          BookEntry(
            id = "book-cold",
            title = "Cold Start Book",
            author = "Author",
            fileName = "book-cold.epub",
            format = BookFormat.EPUB,
            contentHash = "hash-cold",
          ),
        ),
      )
      val pdfConfigFile = File(dir, "pdf_configs.json")
      PdfBookConfigStore(pdfConfigFile).update("book-cold") {
        it.copy(mode = PdfReadingMode.HYBRID_REFLOW)
      }
      val bookmarksFile = File(dir, "bookmarks.json")
      val highlightsFile = File(dir, "highlights.json")
      AnnotationRepository(bookmarksFile, highlightsFile).addBookmark(
        bookId = "book-cold",
        title = "Ch 1",
        progress = 0.2f,
        locatorJson = "{}",
      )

      var engineEvaluated = false
      val repo =
        LibraryRepository(
          appFilesDir = dir,
          engineProvider = {
            engineEvaluated = true
            error("Stub engine open reached")
          },
          importFromUri = { _, _ -> Result.failure(UnsupportedOperationException()) },
          verifyAndExtractSyncedCover = { _, _, _ -> false },
          extractBackfillCover = { _, _, _ -> false },
          readAttemptedCoverIds = { emptySet() },
          persistAttemptedCoverIds = {},
          autoBackfill = false,
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )
      val pdfConfigs = PdfBookConfigStore(pdfConfigFile, preloadInBackground = true, ioDispatcher = ioDispatcher)
      val annotations =
        AnnotationRepository(
          bookmarksFile = bookmarksFile,
          highlightsFile = highlightsFile,
          tombstones = null,
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )
      val stats =
        ReadingStatsRepository(
          statsFile = File(dir, "reading_stats.json"),
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )

      assertFalse(repo.loaded.value)
      assertFalse(pdfConfigs.loaded.value)
      assertFalse(annotations.loaded.value)
      assertFalse(stats.loaded.value)
      assertFalse(engineEvaluated)

      val vm =
        ReaderViewModel(
          bookId = "book-cold",
          repository = repo,
          settingsStore = EInkSettingsStore(EInkSettings()),
          pdfBookConfigs = pdfConfigs,
          annotations = annotations,
          dictionary = DictionaryService(),
          statsRepository = stats,
          ioDispatcher = ioDispatcher,
        )
      vmRef = vm

      assertEquals(ReaderUiState.Loading, vm.state.value)
      assertFalse(engineEvaluated)

      testScheduler.advanceUntilIdle()

      assertTrue(repo.loaded.value)
      assertTrue(pdfConfigs.loaded.value)
      assertTrue(annotations.loaded.value)
      assertTrue(stats.loaded.value)
      assertTrue(engineEvaluated)
      assertEquals(PdfReadingMode.HYBRID_REFLOW, pdfConfigs.get("book-cold")?.mode)
      assertEquals(1, annotations.bookmarks.value.size)

      val errorState = vm.state.value as? ReaderUiState.Error
      assertEquals("Không mở được sách", errorState?.message)
    } finally {
      vmRef?.onReaderForegroundChanged(false)
      Dispatchers.resetMain()
      dir.deleteRecursively()
    }
  }

  @Test
  fun loadReportsBookNotFoundOnlyAfterRepositoryAwaitLoaded() = runTest {
    val dir = createTempDirectory("reader_vm_missing_test").toFile()
    val ioDispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    var vmRef: ReaderViewModel? = null
    try {
      BookIndexStore(File(dir, "library.json")).write(
        listOf(
          BookEntry(
            id = "other-book",
            title = "Other",
            author = "Author",
            fileName = "other.epub",
            format = BookFormat.EPUB,
          ),
        ),
      )

      var engineEvaluated = false
      val repo =
        LibraryRepository(
          appFilesDir = dir,
          engineProvider = {
            engineEvaluated = true
            error("Should not evaluate engine when book is missing")
          },
          importFromUri = { _, _ -> Result.failure(UnsupportedOperationException()) },
          verifyAndExtractSyncedCover = { _, _, _ -> false },
          extractBackfillCover = { _, _, _ -> false },
          readAttemptedCoverIds = { emptySet() },
          persistAttemptedCoverIds = {},
          autoBackfill = false,
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )
      val annotations =
        AnnotationRepository(
          bookmarksFile = File(dir, "bookmarks.json"),
          highlightsFile = File(dir, "highlights.json"),
          tombstones = null,
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )
      val stats =
        ReadingStatsRepository(
          statsFile = File(dir, "reading_stats.json"),
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )

      val vm =
        ReaderViewModel(
          bookId = "missing-book",
          repository = repo,
          settingsStore = EInkSettingsStore(EInkSettings()),
          pdfBookConfigs = null,
          annotations = annotations,
          dictionary = DictionaryService(),
          statsRepository = stats,
          ioDispatcher = ioDispatcher,
        )
      vmRef = vm

      assertEquals(ReaderUiState.Loading, vm.state.value)

      testScheduler.advanceUntilIdle()

      assertTrue(repo.loaded.value)
      assertFalse(engineEvaluated)
      val errorState = vm.state.value as? ReaderUiState.Error
      assertEquals("Không tìm thấy sách", errorState?.message)
    } finally {
      vmRef?.onReaderForegroundChanged(false)
      Dispatchers.resetMain()
      dir.deleteRecursively()
    }
  }

  @Test
  fun retryLoadIgnoresDuplicateCallsWhileLoadingIsAlreadyActive() = runTest {
    val dir = createTempDirectory("reader_vm_retry_load_test").toFile()
    val booksDir = File(dir, "books").apply { mkdirs() }
    val ioDispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    var vmRef: ReaderViewModel? = null
    try {
      File(booksDir, "book-retry.epub").writeText("epub-data")
      BookIndexStore(File(dir, "library.json")).write(
        listOf(
          BookEntry(
            id = "book-retry",
            title = "Retry Book",
            author = "Author",
            fileName = "book-retry.epub",
            format = BookFormat.EPUB,
          ),
        ),
      )

      var engineOpenAttempts = 0
      val repo =
        LibraryRepository(
          appFilesDir = dir,
          engineProvider = {
            engineOpenAttempts += 1
            error("Simulated engine open failure")
          },
          importFromUri = { _, _ -> Result.failure(UnsupportedOperationException()) },
          verifyAndExtractSyncedCover = { _, _, _ -> false },
          extractBackfillCover = { _, _, _ -> false },
          readAttemptedCoverIds = { emptySet() },
          persistAttemptedCoverIds = {},
          autoBackfill = false,
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )
      val annotations =
        AnnotationRepository(
          bookmarksFile = File(dir, "bookmarks.json"),
          highlightsFile = File(dir, "highlights.json"),
          tombstones = null,
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )
      val stats =
        ReadingStatsRepository(
          statsFile = File(dir, "reading_stats.json"),
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )

      val vm =
        ReaderViewModel(
          bookId = "book-retry",
          repository = repo,
          settingsStore = EInkSettingsStore(EInkSettings()),
          pdfBookConfigs = null,
          annotations = annotations,
          dictionary = DictionaryService(),
          statsRepository = stats,
          ioDispatcher = ioDispatcher,
        )
      vmRef = vm

      // Initial load is active; calling retryLoad() while Loading must be ignored
      assertEquals(ReaderUiState.Loading, vm.state.value)
      vm.retryLoad()
      vm.retryLoad()

      testScheduler.advanceUntilIdle()
      assertEquals(1, engineOpenAttempts)
      assertTrue(vm.state.value is ReaderUiState.Error)

      // Now in Error state: rapid double/triple tap on "Retry" must only start 1 load()
      vm.retryLoad()
      assertEquals(ReaderUiState.Loading, vm.state.value)
      vm.retryLoad()
      vm.retryLoad()

      testScheduler.advanceUntilIdle()
      assertEquals(2, engineOpenAttempts)
      assertTrue(vm.state.value is ReaderUiState.Error)
    } finally {
      vmRef?.onReaderForegroundChanged(false)
      Dispatchers.resetMain()
      dir.deleteRecursively()
    }
  }
}
