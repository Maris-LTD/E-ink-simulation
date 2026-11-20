package com.example.emptyapp.data.book

import com.example.emptyapp.reader.pdf.PdfDocumentClassifier
import com.example.emptyapp.reader.pdf.PdfGraphicMaskKind
import com.example.emptyapp.reader.pdf.PdfGraphicMasker
import com.example.emptyapp.reader.pdf.PdfPageAnalyzer
import com.example.emptyapp.reader.pdf.PdfReadingMode
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleBooksAssetTest {
  private fun resolveSampleAsset(fileName: String): File {
    val moduleRelative = File("src/main/assets/samples/$fileName")
    if (moduleRelative.exists()) return moduleRelative
    val rootRelative = File("app/src/main/assets/samples/$fileName")
    if (rootRelative.exists()) return rootRelative
    error("Sample asset not found: $fileName")
  }

  @Test
  fun welcomeEpubHasValidStoredMimetypeAndRequiredEntries() {
    val epubFile = resolveSampleAsset("welcome_inkify.epub")
    ZipInputStream(epubFile.inputStream().buffered()).use { zis ->
      val firstEntry = zis.nextEntry
      assertNotNull(firstEntry)
      assertEquals("mimetype", firstEntry!!.name)
      assertEquals(ZipEntry.STORED, firstEntry.method)
      assertEquals("application/epub+zip", zis.readBytes().toString(Charsets.UTF_8))
    }
    ZipFile(epubFile).use { zip ->
      val required =
        listOf(
          "META-INF/container.xml",
          "OEBPS/content.opf",
          "OEBPS/nav.xhtml",
          "OEBPS/ch1.xhtml",
          "OEBPS/ch2.xhtml",
          "OEBPS/ch3.xhtml",
        )
      required.forEach { entryName ->
        assertNotNull("Missing EPUB entry: $entryName", zip.getEntry(entryName))
      }
    }
  }

  @Test
  fun adaptivePdfSampleHasFourPagesTwoColumnsAndVectorTable() {
    val pdfFile = resolveSampleAsset("adaptive_pdf_sample.pdf")
    val analyses = PdfPageAnalyzer.analyzeAllPages(pdfFile)
    assertEquals(4, analyses.size)

    val classification = PdfDocumentClassifier.classify(analyses)
    assertEquals(2, classification.detectedColumns)
    assertTrue(classification.reflowAllowed)
    assertEquals(PdfReadingMode.GUIDED_COLUMN, classification.recommendedMode)

    val page2 = analyses[1]
    assertTrue(page2.vectorSegments.isNotEmpty())
    val masks = PdfGraphicMasker.detectMasks(page2)
    assertTrue(masks.any { it.kind == PdfGraphicMaskKind.TABLE })
  }
}
