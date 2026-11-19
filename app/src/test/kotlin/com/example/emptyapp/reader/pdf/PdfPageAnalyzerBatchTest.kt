package com.example.emptyapp.reader.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageAnalyzerBatchTest {

  @Test
  fun batchLineStripperExtractsPerPageTextInSinglePassIncludingEmptyPage() {
    PDDocument().use { doc ->
      addTextPage(doc, listOf("Alpha line one", "Alpha line two"))
      doc.addPage(PDPage(PDRectangle.A4))
      addTextPage(doc, listOf("Gamma heading", "Gamma paragraph"))
      addTextPage(doc, listOf("Delta final page"))

      val processPagesCalls = AtomicInteger(0)
      val stripper =
        object : PdfPageAnalyzer.BatchLineStripper() {
          override fun processPages(pages: com.tom_roush.pdfbox.pdmodel.PDPageTree) {
            processPagesCalls.incrementAndGet()
            super.processPages(pages)
          }
        }

      val result = stripper.extractAll(doc)

      assertEquals(1, processPagesCalls.get())
      assertTrue(result.failedPages.isEmpty())
      assertTrue(result.linesByPage[0].orEmpty().any { it.text.contains("Alpha line one") })
      assertTrue(result.linesByPage[0].orEmpty().any { it.text.contains("Alpha line two") })
      assertTrue(result.linesByPage[1].orEmpty().isEmpty())
      assertTrue(result.linesByPage[2].orEmpty().any { it.text.contains("Gamma heading") })
      assertTrue(result.linesByPage[2].orEmpty().any { it.text.contains("Gamma paragraph") })
      assertTrue(result.linesByPage[3].orEmpty().any { it.text.contains("Delta final page") })

      val singleStripper = PdfPageAnalyzer.ReusableLineStripper()
      for (pageIdx in 0 until doc.numberOfPages) {
        val singleLines =
          singleStripper.extract(
            document = doc,
            pageIndex = pageIdx,
            pageWidth = PDRectangle.A4.width,
            pageHeight = PDRectangle.A4.height,
          )
        assertEquals(singleLines, result.linesByPage[pageIdx].orEmpty())
      }
    }
  }

  @Test
  fun analyzeAllPagesReportsProgressAndPreservesPagesAcrossFile() {
    val tempPdf = Files.createTempFile("batch_analyzer_", ".pdf").toFile()
    try {
      PDDocument().use { doc ->
        addTextPage(doc, listOf("Page zero body text line"))
        addTextPage(doc, listOf("Page one body text line"))
        addTextPage(doc, listOf("Page two body text line"))
        doc.save(tempPdf)
      }

      val progressUpdates = mutableListOf<Pair<Int, Int>>()
      val analyses =
        PdfPageAnalyzer.analyzeAllPages(
          file = tempPdf,
          onProgress = { done, total -> progressUpdates += done to total },
        )

      assertEquals(3, analyses.size)
      assertEquals(listOf(0, 1, 2), analyses.map { it.pageIndex })
      assertTrue(analyses[0].textLines.any { it.text.contains("Page zero") })
      assertTrue(analyses[1].textLines.any { it.text.contains("Page one") })
      assertTrue(analyses[2].textLines.any { it.text.contains("Page two") })
      assertTrue(progressUpdates.isNotEmpty())
      assertEquals(3 to 3, progressUpdates.last())
      assertTrue(progressUpdates.zipWithNext().all { (a, b) -> a.first <= b.first })
    } finally {
      tempPdf.delete()
    }
  }

  @Test
  fun analyzeAllPagesStopsEarlyWhenShouldContinueBecomesFalse() {
    PDDocument().use { doc ->
      repeat(5) { idx -> addTextPage(doc, listOf("Page $idx text")) }

      var calls = 0
      val analyses =
        PdfPageAnalyzer.analyzeAllPages(
          document = doc,
          shouldContinue = {
            calls++
            calls <= 2
          },
        )

      assertTrue(analyses.isEmpty())
    }
  }

  @Test
  fun batchLineStripperFallsBackPerFailedPageWithoutAbortingRemainingPages() {
    PDDocument().use { doc ->
      addTextPage(doc, listOf("Valid first page"))
      addTextPage(doc, listOf("Recovered fallback page"))
      addTextPage(doc, listOf("Valid third page"))

      val stripper =
        object : PdfPageAnalyzer.BatchLineStripper() {
          override fun startPage(page: PDPage) {
            if (currentPageNo == 2) {
              throw IllegalStateException("Simulated corrupt stream on page 2")
            }
            super.startPage(page)
          }
        }

      val batch = stripper.extractAll(doc)
      assertEquals(setOf(1), batch.failedPages)
      assertTrue(batch.linesByPage[0].orEmpty().any { it.text.contains("Valid first page") })
      assertTrue(batch.linesByPage[2].orEmpty().any { it.text.contains("Valid third page") })

      val analyses = PdfPageAnalyzer.analyzeAllPagesWithStripper(doc, batchStripper = stripper)
      assertEquals(3, analyses.size)
      assertTrue(analyses[0].textLines.any { it.text.contains("Valid first page") })
      assertTrue(analyses[1].textLines.any { it.text.contains("Recovered fallback page") })
      assertTrue(analyses[2].textLines.any { it.text.contains("Valid third page") })
    }
  }

  @Test
  fun analyzeAllPagesFallsBackToPerPageExtractionWhenWholeBatchFails() {
    PDDocument().use { doc ->
      addTextPage(doc, listOf("Fallback page zero"))
      addTextPage(doc, listOf("Fallback page one"))

      val failingStripper =
        object : PdfPageAnalyzer.BatchLineStripper() {
          override fun extractAll(document: PDDocument): PdfPageAnalyzer.BatchExtractionResult {
            throw IllegalStateException("Simulated whole-document batch failure")
          }
        }

      val analyses = PdfPageAnalyzer.analyzeAllPagesWithStripper(doc, batchStripper = failingStripper)
      assertEquals(2, analyses.size)
      assertTrue(analyses[0].textLines.any { it.text.contains("Fallback page zero") })
      assertTrue(analyses[1].textLines.any { it.text.contains("Fallback page one") })
    }
  }

  @Test(expected = CancellationException::class)
  fun batchLineStripperRethrowsCancellationException() {
    PDDocument().use { doc ->
      addTextPage(doc, listOf("Page zero"))
      val stripper =
        object : PdfPageAnalyzer.BatchLineStripper() {
          override fun startPage(page: PDPage) {
            throw CancellationException("Cancelled")
          }
        }
      stripper.extractAll(doc)
    }
  }

  private fun addTextPage(doc: PDDocument, lines: List<String>) {
    val page = PDPage(PDRectangle.A4)
    doc.addPage(page)
    PDPageContentStream(doc, page).use { stream ->
      stream.beginText()
      stream.setFont(PDType1Font.HELVETICA, 12f)
      stream.newLineAtOffset(72f, 750f)
      lines.forEachIndexed { idx, line ->
        if (idx > 0) stream.newLineAtOffset(0f, -20f)
        stream.showText(line)
      }
      stream.endText()
    }
  }
}
