package com.example.emptyapp.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReflowEngineTest {

  @Test
  fun mergesWrappedLinesAndFiltersRepeatingHeaderAndPageNumber() {
    val header = PdfTextLine("Book Title", PdfRect(0.35f, 0.03f, 0.65f, 0.05f), 9f)
    val footer = PdfTextLine("42", PdfRect(0.48f, 0.94f, 0.52f, 0.96f), 9f)
    val line1 = PdfTextLine("First sentence wraps across", PdfRect(0.10f, 0.12f, 0.88f, 0.14f), 11f)
    val line2 = PdfTextLine("two lines with hyphen-", PdfRect(0.10f, 0.145f, 0.86f, 0.165f), 11f)
    val line3 = PdfTextLine("ated words cleanly.", PdfRect(0.10f, 0.17f, 0.45f, 0.19f), 11f)
    val line4 = PdfTextLine("Second paragraph starts after gap.", PdfRect(0.10f, 0.23f, 0.85f, 0.25f), 11f)

    val current =
      PdfPageAnalysis(
        pageIndex = 5,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 140,
        textLines = listOf(header, line1, line2, line3, line4, footer),
        imageBlocks = emptyList(),
        totalImageAreaFraction = 0f,
        vectorOpCount = 0,
        columnCount = 1,
      )
    val neighbor =
      current.copy(
        pageIndex = 4,
        textLines = listOf(header, PdfTextLine("41", PdfRect(0.48f, 0.94f, 0.52f, 0.96f), 9f)),
      )

    val result = PdfReflowEngine.buildReflowPage(current, listOf(neighbor))
    assertTrue(result is ReflowPageResult.Reflowed)
    val items = (result as ReflowPageResult.Reflowed).items
    assertEquals(2, items.size)
    assertEquals(
      "First sentence wraps across two lines with hyphenated words cleanly.",
      (items[0] as ReflowItem.Paragraph).text,
    )
    assertEquals(
      "Second paragraph starts after gap.",
      (items[1] as ReflowItem.Paragraph).text,
    )
  }

  @Test
  fun interleavesInlineImageBetweenParagraphsByVerticalPosition() {
    val topPara = PdfTextLine("Above illustration.", PdfRect(0.10f, 0.12f, 0.60f, 0.14f), 11f)
    val img = PdfImageBlock(PdfRect(0.15f, 0.20f, 0.85f, 0.50f), 0.21f)
    val bottomPara = PdfTextLine("Below illustration.", PdfRect(0.10f, 0.55f, 0.60f, 0.57f), 11f)

    val page =
      PdfPageAnalysis(
        pageIndex = 2,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 40,
        textLines = listOf(topPara, bottomPara),
        imageBlocks = listOf(img),
        totalImageAreaFraction = 0.21f,
        vectorOpCount = 0,
        columnCount = 1,
      )

    val result = PdfReflowEngine.buildReflowPage(page) as ReflowPageResult.Reflowed
    assertEquals(3, result.items.size)
    assertTrue(result.items[0] is ReflowItem.Paragraph)
    assertTrue(result.items[1] is ReflowItem.Image)
    assertTrue(result.items[2] is ReflowItem.Paragraph)
  }

  @Test
  fun twoColumnPageKeepsColumnReadingOrderWithImageInSecondColumn() {
    val colA1 = PdfTextLine("Left column first paragraph.", PdfRect(0.08f, 0.12f, 0.46f, 0.14f), 11f)
    val colA2 = PdfTextLine("Left column second paragraph.", PdfRect(0.08f, 0.40f, 0.46f, 0.42f), 11f)
    val colB1 = PdfTextLine("Right column first paragraph.", PdfRect(0.54f, 0.12f, 0.92f, 0.14f), 11f)
    val colB2 = PdfTextLine("Right column second paragraph.", PdfRect(0.54f, 0.40f, 0.92f, 0.42f), 11f)
    val rightImage = PdfImageBlock(PdfRect(0.56f, 0.22f, 0.90f, 0.35f), 0.04f)

    val page =
      PdfPageAnalysis(
        pageIndex = 7,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 200,
        textLines = listOf(colA1, colB1, colA2, colB2),
        imageBlocks = listOf(rightImage),
        totalImageAreaFraction = 0.04f,
        vectorOpCount = 0,
        columnCount = 2,
      )

    val result = PdfReflowEngine.buildReflowPage(page) as ReflowPageResult.Reflowed
    val order =
      result.items.map { item ->
        when (item) {
          is ReflowItem.Paragraph -> item.text
          is ReflowItem.Image -> "IMAGE"
        }
      }
    assertEquals(
      listOf(
        "Left column first paragraph.",
        "Left column second paragraph.",
        "Right column first paragraph.",
        "IMAGE",
        "Right column second paragraph.",
      ),
      order,
    )
  }

  @Test
  fun fallsBackToOriginalForFullPageImageAndComplexVectors() {
    val fullImagePage =
      PdfPageAnalysis(
        pageIndex = 0,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 50,
        textLines = emptyList(),
        imageBlocks = listOf(PdfImageBlock(PdfRect(0.05f, 0.05f, 0.95f, 0.95f), 0.81f)),
        totalImageAreaFraction = 0.81f,
        vectorOpCount = 0,
        columnCount = 1,
      )
    val fallbackImg = PdfReflowEngine.buildReflowPage(fullImagePage)
    assertEquals(
      ReflowPageResult.FallbackOriginal(ReflowFallbackReason.FULL_PAGE_IMAGE),
      fallbackImg,
    )

    val vectorHeavyPage =
      fullImagePage.copy(
        totalImageAreaFraction = 0f,
        imageBlocks = emptyList(),
        vectorOpCount = 350,
      )
    val fallbackVec = PdfReflowEngine.buildReflowPage(vectorHeavyPage)
    assertEquals(
      ReflowPageResult.FallbackOriginal(ReflowFallbackReason.COMPLEX_VECTOR_LAYOUT),
      fallbackVec,
    )
  }

  @Test
  fun masksTableAndSymbolStackIntoOrderedReflowImages() {
    val intro = PdfTextLine("Opening paragraph before graphics.", PdfRect(0.10f, 0.10f, 0.85f, 0.12f), 11f)
    val tableHeaderA = PdfTextLine("Name", PdfRect(0.15f, 0.22f, 0.28f, 0.24f), 11f)
    val tableHeaderB = PdfTextLine("Value", PdfRect(0.55f, 0.22f, 0.68f, 0.24f), 11f)
    val tableCellA = PdfTextLine("Alpha", PdfRect(0.15f, 0.28f, 0.28f, 0.30f), 11f)
    val tableCellB = PdfTextLine("42", PdfRect(0.55f, 0.28f, 0.62f, 0.30f), 11f)

    val sym1 = PdfTextLine("——————", PdfRect(0.44f, 0.42f, 0.56f, 0.435f), 11f)
    val sym2 = PdfTextLine("——  ——", PdfRect(0.44f, 0.445f, 0.56f, 0.46f), 11f)
    val sym3 = PdfTextLine("——————", PdfRect(0.44f, 0.47f, 0.56f, 0.485f), 11f)

    val outro = PdfTextLine("Closing paragraph after graphics.", PdfRect(0.10f, 0.60f, 0.85f, 0.62f), 11f)

    val page =
      PdfPageAnalysis(
        pageIndex = 3,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 120,
        textLines = listOf(intro, tableHeaderA, tableHeaderB, tableCellA, tableCellB, sym1, sym2, sym3, outro),
        imageBlocks = emptyList(),
        totalImageAreaFraction = 0f,
        vectorOpCount = 6,
        columnCount = 1,
        vectorSegments =
          listOf(
            PdfVectorSegment(0.12f, 0.20f, 0.78f, 0.20f),
            PdfVectorSegment(0.12f, 0.26f, 0.78f, 0.26f),
            PdfVectorSegment(0.12f, 0.33f, 0.78f, 0.33f),
            PdfVectorSegment(0.12f, 0.20f, 0.12f, 0.33f),
            PdfVectorSegment(0.45f, 0.20f, 0.45f, 0.33f),
            PdfVectorSegment(0.78f, 0.20f, 0.78f, 0.33f),
          ),
      )

    val result = PdfReflowEngine.buildReflowPage(page) as ReflowPageResult.Reflowed
    assertEquals(4, result.items.size)
    assertEquals("Opening paragraph before graphics.", (result.items[0] as ReflowItem.Paragraph).text)
    assertEquals(ReflowImageKind.TABLE, (result.items[1] as ReflowItem.Image).kind)
    assertEquals(ReflowImageKind.SYMBOL_STACK, (result.items[2] as ReflowItem.Image).kind)
    assertEquals("Closing paragraph after graphics.", (result.items[3] as ReflowItem.Paragraph).text)
  }

  @Test
  fun analyzePageSafelyReturnsEmptyAnalysisOnErrorAndFallsBackToOriginal() {
    val fallbackAnalysis =
      PdfPageAnalyzer.analyzePageSafely(4) {
        throw OutOfMemoryError("Simulated PDFBox OOM on page 4")
      }
    assertEquals(4, fallbackAnalysis.pageIndex)
    assertEquals(0, fallbackAnalysis.charCount)
    assertTrue(fallbackAnalysis.textLines.isEmpty())
    assertTrue(fallbackAnalysis.imageBlocks.isEmpty())

    val reflowResult = PdfReflowEngine.buildReflowPage(fallbackAnalysis)
    assertEquals(
      ReflowPageResult.FallbackOriginal(ReflowFallbackReason.NO_TEXT),
      reflowResult,
    )
  }

  @Test(expected = kotlinx.coroutines.CancellationException::class)
  fun analyzePageSafelyRethrowsCancellationException() {
    PdfPageAnalyzer.analyzePageSafely(1) {
      throw kotlinx.coroutines.CancellationException("Cancelled")
    }
  }
}
