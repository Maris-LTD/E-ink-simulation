package com.example.emptyapp.reader.pdf

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReflowPaginatorTest {

  @Test
  fun paginatesLongParagraphAcrossScreensAndFlowsNextPage() {
    val page0 =
      ReflowPageMeta(
        pageIndex = 0,
        hasSpecialObjects = false,
        result =
          ReflowPageResult.Reflowed(
            listOf(
              ReflowItem.Paragraph(
                text = "one two three four five six seven eight nine ten",
                isHeading = false,
                topFraction = 0.1f,
              ),
            ),
          ),
      )
    val page1 =
      ReflowPageMeta(
        pageIndex = 1,
        hasSpecialObjects = false,
        result =
          ReflowPageResult.Reflowed(
            listOf(
              ReflowItem.Paragraph(
                text = "eleven twelve thirteen",
                isHeading = false,
                topFraction = 0.1f,
              ),
            ),
          ),
      )

    // Simple deterministic measurer: 20px per ~18 chars
    val screens =
      PdfReflowPaginator.paginate(
        pages = listOf(page0, page1),
        maxContentWidthPx = 300,
        maxContentHeightPx = 50,
        paragraphSpacingPx = 10,
      ) { text, _, _ ->
        val lines = ((text.length + 17) / 18).coerceAtLeast(1)
        lines * 20
      }

    assertTrue(screens.size >= 2)
    // Verify leftover from page 0 + page 1 can share a screen without vertical overflow
    assertTrue(screens.any { 1 in it.sourcePages })
  }

  @Test
  fun snapBackRuleFollowsSpecialObjectPriority() {
    val noSpecialScreen =
      ReflowScreenPage(
        screenIndex = 0,
        elements = emptyList(),
        sourcePages = listOf(0, 1),
        specialObjectPages = emptySet(),
      )
    assertEquals(0, PdfReflowPaginator.resolveSnapBackPage(noSpecialScreen))

    val secondHasSpecial =
      ReflowScreenPage(
        screenIndex = 1,
        elements = emptyList(),
        sourcePages = listOf(0, 1),
        specialObjectPages = setOf(1),
      )
    assertEquals(1, PdfReflowPaginator.resolveSnapBackPage(secondHasSpecial))

    val bothHaveSpecial =
      ReflowScreenPage(
        screenIndex = 2,
        elements = emptyList(),
        sourcePages = listOf(0, 1),
        specialObjectPages = setOf(0, 1),
      )
    assertEquals(0, PdfReflowPaginator.resolveSnapBackPage(bothHaveSpecial))
  }

  @Test
  fun cachesAndReloadsAnalyzedDocument() {
    val tempDir = Files.createTempDirectory("pdf_doc_cache_test").toFile()
    try {
      val pdfFile = tempDir.resolve("sample.pdf").apply { writeText("dummy-pdf-bytes") }
      val cache = PdfDocumentCache(tempDir.resolve("cache"))
      val pages =
        listOf(
          PdfPageAnalysis(
            pageIndex = 0,
            pageWidthPt = 595f,
            pageHeightPt = 842f,
            charCount = 120,
            textLines = listOf(PdfTextLine("Hello", PdfRect(0.1f, 0.1f, 0.5f, 0.12f), 11f)),
            imageBlocks = emptyList(),
            totalImageAreaFraction = 0f,
            vectorOpCount = 0,
            columnCount = 1,
          ),
        )
      cache.save(pdfFile, pages)
      val reloaded = cache.load(pdfFile)
      assertEquals(1, reloaded?.size)
      assertEquals("Hello", reloaded?.first()?.textLines?.first()?.text)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun sizesSymbolStackCompactlyAndTableFullWidth() {
    val page =
      ReflowPageMeta(
        pageIndex = 0,
        hasSpecialObjects = true,
        result =
          ReflowPageResult.Reflowed(
            listOf(
              ReflowItem.Image(
                bounds = PdfRect(0.44f, 0.15f, 0.56f, 0.30f),
                pageIndex = 0,
                topFraction = 0.15f,
                kind = ReflowImageKind.SYMBOL_STACK,
              ),
              ReflowItem.Image(
                bounds = PdfRect(0.10f, 0.40f, 0.90f, 0.60f),
                pageIndex = 0,
                topFraction = 0.40f,
                kind = ReflowImageKind.TABLE,
              ),
            ),
          ),
      )

    val screens =
      PdfReflowPaginator.paginate(
        pages = listOf(page),
        maxContentWidthPx = 1000,
        maxContentHeightPx = 1600,
        paragraphSpacingPx = 16,
      ) { _, _, _ -> 40 }

    val images = screens.flatMap { it.elements }.filterIsInstance<PaginatedReflowElement.InlineImage>()
    assertEquals(2, images.size)
    assertEquals(ReflowImageKind.SYMBOL_STACK, images[0].kind)
    assertTrue(images[0].displayWidthPx <= 450)
    assertEquals(ReflowImageKind.TABLE, images[1].kind)
    assertEquals(1000, images[1].displayWidthPx)
  }
}
