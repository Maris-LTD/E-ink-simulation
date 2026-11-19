package com.example.emptyapp.ui.library

import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.stats.ReadingStatsRepository
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryViewModelErrorTest {
  private val dupReason = "this book is already in your library"
  private val unsupportedReason = "Only EPUB, PDF, and CBZ are supported"
  private val cannotOpenReason = "Cannot open file"
  private val storageFullReason = "Device storage is full"
  private val invalidFileReason = "invalid file"
  private val genericFailed = "Could not import book"
  private val knownReasons = setOf(dupReason, unsupportedReason, cannotOpenReason)

  private fun format(err: Throwable): String =
    LibraryViewModel.formatImportError(
      error = err,
      knownLocalizedReasons = knownReasons,
      storageFullReason = storageFullReason,
      invalidFileReason = invalidFileReason,
      genericFailedMessage = genericFailed,
      formatWithReason = { reason -> "Could not import book: $reason" },
    )

  @Test
  fun preservesKnownLocalizedReasonsFromBookImporter() {
    assertEquals(
      "Could not import book: $dupReason",
      format(IllegalStateException(dupReason)),
    )
    assertEquals(
      "Could not import book: $unsupportedReason",
      format(IllegalArgumentException(unsupportedReason)),
    )
    assertEquals(
      "Could not import book: $cannotOpenReason",
      format(IllegalStateException(cannotOpenReason)),
    )
  }

  @Test
  fun mapsStorageFullErrorsToLocalizedStorageFullReason() {
    assertTrue(LibraryViewModel.isStorageFullError(IOException("write failed: ENOSPC (No space left on device)")))
    assertTrue(
      LibraryViewModel.isStorageFullError(
        IllegalStateException("Cannot replace library.json", IOException("No space left on device")),
      ),
    )
    assertFalse(LibraryViewModel.isStorageFullError(IOException("Corrupt zip central directory")))

    assertEquals(
      "Could not import book: $storageFullReason",
      format(IOException("write failed: ENOSPC (No space left on device)")),
    )
  }

  @Test
  fun masksRawTechnicalExceptionMessagesWithLocalizedFallback() {
    val rawReadiumError =
      RuntimeException("org.readium.r2.streamer.parser.epub.EpubPositionsService: Invalid container.xml at line 42")
    val rawAtomicWriteError = IOException("Cannot replace library.json with library.json.tmp")

    val formattedReadium = format(rawReadiumError)
    val formattedAtomic = format(rawAtomicWriteError)

    assertEquals("Could not import book: $invalidFileReason", formattedReadium)
    assertEquals("Could not import book: $invalidFileReason", formattedAtomic)
    assertFalse(formattedReadium.contains("org.readium"))
    assertFalse(formattedAtomic.contains("library.json"))
  }

  @Test(expected = CancellationException::class)
  fun rethrowsCancellationException() {
    format(CancellationException("import cancelled"))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun overlappingImportsKeepImportingTrueUntilLastJobCompletes() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    val dir = createTempDirectory("lib_vm_import_count_test").toFile()
    try {
      val repo =
        LibraryRepository(
          appFilesDir = dir,
          engineProvider = { error("Not used") },
          importFromUri = { _, _ -> Result.failure(UnsupportedOperationException()) },
          verifyAndExtractSyncedCover = { _, _, _ -> false },
          extractBackfillCover = { _, _, _ -> false },
          readAttemptedCoverIds = { emptySet() },
          persistAttemptedCoverIds = {},
          autoBackfill = false,
        )
      val annotations = AnnotationRepository(File(dir, "bookmarks.json"), File(dir, "highlights.json"))
      val stats = ReadingStatsRepository(File(dir, "reading_stats.json"))
      val vm = LibraryViewModel(repo, annotations, stats, context = null)

      val gate1 = CompletableDeferred<Unit>()
      val gate2 = CompletableDeferred<Unit>()
      val entry1 = BookEntry("1", "Book 1", "Author", "1.epub", BookFormat.EPUB)
      val entry2 = BookEntry("2", "Book 2", "Author", "2.epub", BookFormat.EPUB)

      vm.launchImportJob {
        gate1.await()
        Result.success(entry1)
      }
      assertTrue(vm.importing.value)

      vm.launchImportJob {
        gate2.await()
        Result.success(entry2)
      }
      assertTrue(vm.importing.value)

      gate1.complete(Unit)
      testScheduler.advanceUntilIdle()
      assertTrue(vm.importing.value)

      gate2.complete(Unit)
      testScheduler.advanceUntilIdle()
      assertFalse(vm.importing.value)
    } finally {
      Dispatchers.resetMain()
      dir.deleteRecursively()
    }
  }
}
