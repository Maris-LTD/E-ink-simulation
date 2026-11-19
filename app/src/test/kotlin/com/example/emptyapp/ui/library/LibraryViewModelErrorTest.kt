package com.example.emptyapp.ui.library

import java.io.IOException
import kotlinx.coroutines.CancellationException
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
}
