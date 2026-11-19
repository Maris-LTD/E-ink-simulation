package com.example.emptyapp.reader.pdf

import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBookConfigStoreTest {

  @Test
  fun persistsAndReloadsPerBookPdfConfig() {
    val tempDir = Files.createTempDirectory("pdf_config_test").toFile()
    val file = tempDir.resolve("pdf_book_configs.json")
    try {
      val store = PdfBookConfigStore(file)
      assertNull(store.get("book-1"))

      store.update("book-1") {
        it.copy(
          category = PdfDocumentCategory.NOVEL_TEXT,
          mode = PdfReadingMode.HYBRID_REFLOW,
          autoCrop = true,
          reflowFontScale = 1.35f,
          reflowAllowed = true,
        )
      }

      val reloaded = PdfBookConfigStore(file)
      val loaded = reloaded.get("book-1")
      assertEquals(PdfDocumentCategory.NOVEL_TEXT, loaded?.category)
      assertEquals(PdfReadingMode.HYBRID_REFLOW, loaded?.mode)
      assertEquals(true, loaded?.autoCrop)
      assertEquals(1.35f, loaded?.reflowFontScale)
      assertEquals(true, loaded?.reflowAllowed)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun updatesStateImmediatelyAndPersistsAsynchronouslyOnIoDispatcher() = runTest {
    val tempDir = Files.createTempDirectory("pdf_config_async_test").toFile()
    val file = tempDir.resolve("pdf_book_configs.json")
    try {
      val seed = PdfBookConfigStore(file)
      seed.update("book-seed") { it.copy(mode = PdfReadingMode.SMART_ORIGINAL, autoCrop = true) }

      val ioDispatcher = StandardTestDispatcher(testScheduler)
      val store = PdfBookConfigStore(file, preloadInBackground = true, ioDispatcher = ioDispatcher)
      assertFalse(store.loaded.value)
      assertNull(store.get("book-seed"))

      store.awaitLoaded()
      assertTrue(store.loaded.value)
      assertEquals(true, store.get("book-seed")?.autoCrop)

      store.update("book-1") { it.copy(mode = PdfReadingMode.HYBRID_REFLOW) }
      store.update("book-1") { it.copy(reflowFontScale = 1.6f) }

      // In-memory state reflects updates immediately before IO dispatcher runs
      assertEquals(PdfReadingMode.HYBRID_REFLOW, store.get("book-1")?.mode)
      assertEquals(1.6f, store.get("book-1")?.reflowFontScale)
      assertNull(PdfBookConfigStore(file).get("book-1"))

      store.flushForTest()

      val reloaded = PdfBookConfigStore(file)
      assertEquals(true, reloaded.get("book-seed")?.autoCrop)
      assertEquals(PdfReadingMode.HYBRID_REFLOW, reloaded.get("book-1")?.mode)
      assertEquals(1.6f, reloaded.get("book-1")?.reflowFontScale)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun updateBeforePreloadCompletesMergesWithDiskEntriesWithoutOverwriting() = runTest {
    val tempDir = Files.createTempDirectory("pdf_config_merge_test").toFile()
    val file = tempDir.resolve("pdf_book_configs.json")
    try {
      val seed = PdfBookConfigStore(file)
      seed.update("book-0") {
        it.copy(
          category = PdfDocumentCategory.TECHNICAL_PAPER,
          mode = PdfReadingMode.GUIDED_COLUMN,
          autoCrop = true,
        )
      }

      val ioDispatcher = StandardTestDispatcher(testScheduler)
      val store = PdfBookConfigStore(file, preloadInBackground = true, ioDispatcher = ioDispatcher)
      assertFalse(store.loaded.value)

      // Update before preload has run on ioDispatcher
      store.update("book-0") { it.copy(reflowFontScale = 1.4f) }
      store.update("book-1") { it.copy(mode = PdfReadingMode.HYBRID_REFLOW) }

      assertEquals(1.4f, store.get("book-0")?.reflowFontScale)
      assertEquals(PdfReadingMode.HYBRID_REFLOW, store.get("book-1")?.mode)

      store.flushForTest()
      assertTrue(store.loaded.value)

      val reloaded = PdfBookConfigStore(file)
      assertEquals(PdfDocumentCategory.TECHNICAL_PAPER, reloaded.get("book-0")?.category)
      assertEquals(PdfReadingMode.GUIDED_COLUMN, reloaded.get("book-0")?.mode)
      assertEquals(true, reloaded.get("book-0")?.autoCrop)
      assertEquals(1.4f, reloaded.get("book-0")?.reflowFontScale)
      assertEquals(PdfReadingMode.HYBRID_REFLOW, reloaded.get("book-1")?.mode)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun rapidUpdatesOnSingleLaneIoDispatcherPersistLatestSnapshotDeterministically() = runTest {
    val tempDir = Files.createTempDirectory("pdf_config_serial_test").toFile()
    val file = tempDir.resolve("pdf_book_configs.json")
    try {
      val store =
        PdfBookConfigStore(
          file,
          preloadInBackground = true,
          ioDispatcher = Dispatchers.IO.limitedParallelism(1),
        )
      store.awaitLoaded()
      for (i in 1..20) {
        val scale = 1.0f + i * 0.05f
        store.update("book-rapid") {
          it.copy(mode = PdfReadingMode.HYBRID_REFLOW, reflowFontScale = scale)
        }
      }
      store.flushForTest()

      val reloaded = PdfBookConfigStore(file)
      assertEquals(2.0f, reloaded.get("book-rapid")?.reflowFontScale ?: 0f, 1e-4f)
      assertEquals(PdfReadingMode.HYBRID_REFLOW, reloaded.get("book-rapid")?.mode)
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
