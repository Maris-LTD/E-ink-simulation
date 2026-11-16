package com.example.emptyapp.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDocumentClassifierTest {

  @Test
  fun classifiesTextNovelAsHybridReflow() {
    val lines =
      (0 until 15).map { idx ->
        PdfTextLine(
          text = "This is a full-width body line number $idx in a novel.",
          bounds = PdfRect(0.12f, 0.12f + idx * 0.04f, 0.88f, 0.14f + idx * 0.04f),
          fontSizePt = 11f,
        )
      }
    val page =
      PdfPageAnalysis(
        pageIndex = 1,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 900,
        textLines = lines,
        imageBlocks = emptyList(),
        totalImageAreaFraction = 0f,
        vectorOpCount = 4,
        columnCount = PdfPageAnalyzer.detectColumnCount(lines),
      )

    val result = PdfDocumentClassifier.classify(listOf(page))
    assertEquals(PdfDocumentCategory.NOVEL_TEXT, result.category)
    assertEquals(PdfReadingMode.HYBRID_REFLOW, result.recommendedMode)
    assertTrue(result.reflowAllowed)
    assertEquals(1, result.detectedColumns)
  }

  @Test
  fun classifiesTwoColumnPaperAsTechnicalGuidedColumn() {
    val leftLines =
      (0 until 8).map { idx ->
        PdfTextLine(
          text = "Left column line $idx",
          bounds = PdfRect(0.08f, 0.12f + idx * 0.05f, 0.46f, 0.15f + idx * 0.05f),
          fontSizePt = 9.5f,
        )
      }
    val rightLines =
      (0 until 8).map { idx ->
        PdfTextLine(
          text = "Right column line $idx",
          bounds = PdfRect(0.54f, 0.12f + idx * 0.05f, 0.92f, 0.15f + idx * 0.05f),
          fontSizePt = 9.5f,
        )
      }
    val allLines = leftLines + rightLines
    val detectedCols = PdfPageAnalyzer.detectColumnCount(allLines)
    val page =
      PdfPageAnalysis(
        pageIndex = 1,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 650,
        textLines = allLines,
        imageBlocks = emptyList(),
        totalImageAreaFraction = 0.05f,
        vectorOpCount = 40,
        columnCount = detectedCols,
      )

    val result = PdfDocumentClassifier.classify(listOf(page))
    assertEquals(2, detectedCols)
    assertEquals(PdfDocumentCategory.TECHNICAL_PAPER, result.category)
    assertEquals(PdfReadingMode.GUIDED_COLUMN, result.recommendedMode)
    assertTrue(result.reflowAllowed)
  }

  @Test
  fun classifiesImageHeavyDocumentAsMagazine() {
    val page =
      PdfPageAnalysis(
        pageIndex = 1,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 320,
        textLines = emptyList(),
        imageBlocks = listOf(PdfImageBlock(PdfRect(0.1f, 0.1f, 0.9f, 0.65f), 0.44f)),
        totalImageAreaFraction = 0.44f,
        vectorOpCount = 12,
        columnCount = 1,
      )

    val result = PdfDocumentClassifier.classify(listOf(page))
    assertEquals(PdfDocumentCategory.MAGAZINE, result.category)
    assertEquals(PdfReadingMode.SMART_ORIGINAL, result.recommendedMode)
    assertTrue(result.reflowAllowed)
  }

  @Test
  fun classifiesNoTextScanAsScannedComicAndLocksReflow() {
    val page =
      PdfPageAnalysis(
        pageIndex = 0,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 0,
        textLines = emptyList(),
        imageBlocks = listOf(PdfImageBlock(PdfRect(0f, 0f, 1f, 1f), 1f)),
        totalImageAreaFraction = 1f,
        vectorOpCount = 0,
        columnCount = 1,
      )

    val result = PdfDocumentClassifier.classify(listOf(page))
    assertEquals(PdfDocumentCategory.SCANNED_COMIC, result.category)
    assertEquals(PdfReadingMode.SMART_ORIGINAL, result.recommendedMode)
    assertFalse(result.reflowAllowed)
  }
}
