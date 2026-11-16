package com.example.emptyapp.reader.pdf

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
