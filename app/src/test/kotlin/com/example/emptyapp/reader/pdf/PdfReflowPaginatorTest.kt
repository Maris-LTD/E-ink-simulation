package com.example.emptyapp.reader.pdf

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

  @Test
  fun paginateAttachesExactItemIndexAndCharOffsetsForSlicesAndNonTextElements() {
    val paragraph0 = "  alpha beta gamma delta epsilon zeta eta theta"
    val paragraph1 = "iota kappa lambda mu nu xi omicron pi"
    val page0 =
      ReflowPageMeta(
        pageIndex = 0,
        hasSpecialObjects = true,
        result =
          ReflowPageResult.Reflowed(
            listOf(
              ReflowItem.Paragraph(text = paragraph0, isHeading = false, topFraction = 0.1f),
              ReflowItem.Image(
                bounds = PdfRect(0.1f, 0.4f, 0.9f, 0.6f),
                pageIndex = 0,
                topFraction = 0.4f,
              ),
              ReflowItem.Paragraph(text = paragraph1, isHeading = false, topFraction = 0.7f),
            ),
          ),
      )

    val screens =
      PdfReflowPaginator.paginate(
        pages = listOf(page0),
        maxContentWidthPx = 300,
        maxContentHeightPx = 55,
        paragraphSpacingPx = 10,
      ) { text, _, _ ->
        val lines = ((text.length + 14) / 15).coerceAtLeast(1)
        lines * 25
      }

    val allElements = screens.flatMap { it.elements }
    val p0Slices = allElements.filterIsInstance<PaginatedReflowElement.ParagraphSlice>().filter { it.itemIndex == 0 }
    assertTrue(p0Slices.size >= 2)
    p0Slices.forEach { slice ->
      assertEquals(slice.text, paragraph0.substring(slice.charOffset, slice.charEndOffset))
    }
    assertEquals(2, p0Slices.first().charOffset)

    val img = allElements.filterIsInstance<PaginatedReflowElement.InlineImage>().single()
    assertEquals(1, img.itemIndex)
    assertEquals(0, img.charOffset)

    val p1Slices = allElements.filterIsInstance<PaginatedReflowElement.ParagraphSlice>().filter { it.itemIndex == 2 }
    assertTrue(p1Slices.isNotEmpty())
    p1Slices.forEach { slice ->
      assertEquals(slice.text, paragraph1.substring(slice.charOffset, slice.charEndOffset))
    }
    assertEquals(allElements.first().toReadingAnchor(), screens.first().startAnchor)
  }

  @Test
  fun findScreenIndexForPdfPagePrefersScreenStartingWithTargetPage() {
    val screen0 =
      ReflowScreenPage(
        screenIndex = 0,
        elements = listOf(PaginatedReflowElement.ParagraphSlice("tail 8", false, sourcePage = 8)),
        sourcePages = listOf(8, 9),
        specialObjectPages = emptySet(),
      )
    val screen1 =
      ReflowScreenPage(
        screenIndex = 1,
        elements = listOf(PaginatedReflowElement.ParagraphSlice("head 9", false, sourcePage = 9)),
        sourcePages = listOf(9, 10),
        specialObjectPages = emptySet(),
      )
    val screen2 =
      ReflowScreenPage(
        screenIndex = 2,
        elements = listOf(PaginatedReflowElement.ParagraphSlice("tail 10", false, sourcePage = 10)),
        sourcePages = listOf(10),
        specialObjectPages = emptySet(),
      )

    val screens = listOf(screen0, screen1, screen2)
    assertEquals(1, PdfReflowPaginator.findScreenIndexForPdfPage(screens, 9))
    assertEquals(0, PdfReflowPaginator.findScreenIndexForPdfPage(screens, 8))
    // When page 11 only appears mid-screen in [10, 11], still falls back to containing screen
    val onlyMid =
      listOf(
        ReflowScreenPage(
          screenIndex = 0,
          elements = emptyList(),
          sourcePages = listOf(10, 11),
          specialObjectPages = emptySet(),
        ),
      )
    assertEquals(0, PdfReflowPaginator.findScreenIndexForPdfPage(onlyMid, 11))
  }

  @Test
  fun relayoutAcrossMultipleFontSizesPreservesReadingAnchorWithoutBackwardDrift() {
    val pages =
      (0..11).map { pageIdx ->
        val text =
          List(36) { wordIdx -> "p${pageIdx}w$wordIdx" }.joinToString(" ")
        ReflowPageMeta(
          pageIndex = pageIdx,
          hasSpecialObjects = false,
          result =
            ReflowPageResult.Reflowed(
              listOf(
                ReflowItem.Paragraph(
                  text = text,
                  isHeading = false,
                  topFraction = 0.1f,
                ),
              ),
            ),
        )
      }

    fun layoutWith(charsPerLine: Int, linesPerScreen: Int): List<ReflowScreenPage> =
      PdfReflowPaginator.paginate(
        pages = pages,
        maxContentWidthPx = 400,
        maxContentHeightPx = linesPerScreen * 20,
        paragraphSpacingPx = 8,
      ) { text, _, _ ->
        val lines = ((text.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
        lines * 20
      }

    val initialScreens = layoutWith(charsPerLine = 24, linesPerScreen = 4)
    val page9Screens = initialScreens.filter { it.startAnchor.sourcePageIndex == 9 }
    assertTrue("Expected multiple screens starting on page 9", page9Screens.size >= 2)
    val secondScreenStartingOnPage9 = page9Screens[1]
    val readingAnchor = secondScreenStartingOnPage9.startAnchor
    assertEquals(9, readingAnchor.sourcePageIndex)
    assertTrue(readingAnchor.charOffset > 0)

    // Simulate 3 consecutive font/layout changes (larger -> smaller -> larger again)
    val layouts =
      listOf(
        layoutWith(charsPerLine = 16, linesPerScreen = 3),
        layoutWith(charsPerLine = 32, linesPerScreen = 6),
        layoutWith(charsPerLine = 20, linesPerScreen = 4),
      )
    for (screens in layouts) {
      val targetIndex = PdfReflowPaginator.findScreenIndexForAnchor(screens, readingAnchor)
      val targetScreen = screens[targetIndex]
      val matchingRange =
        targetScreen.elementRanges.firstOrNull {
          it.sourcePageIndex == readingAnchor.sourcePageIndex && it.itemIndex == readingAnchor.itemIndex
        }
      assertTrue(
        "Expected target screen $targetIndex to contain anchor $readingAnchor, but got ${targetScreen.elementRanges}",
        matchingRange != null && readingAnchor.charOffset in matchingRange.charRange,
      )
      assertTrue(
        "Anchor must never drift backward to page 8; screen sourcePages=${targetScreen.sourcePages}",
        targetScreen.sourcePages.all { it >= 9 } || 9 in targetScreen.sourcePages,
      )
      assertTrue(PdfReflowPaginator.resolveSnapBackPage(targetScreen) >= 9)
    }
  }

  @Test
  fun findScreenIndexForAnchorFallsBackCleanlyWhenItemMissing() {
    val screens =
      listOf(
        ReflowScreenPage(
          screenIndex = 0,
          elements = listOf(PaginatedReflowElement.ParagraphSlice("p0", false, sourcePage = 0, itemIndex = 0)),
          sourcePages = listOf(0),
          specialObjectPages = emptySet(),
        ),
        ReflowScreenPage(
          screenIndex = 1,
          elements = listOf(PaginatedReflowElement.ParagraphSlice("p4", false, sourcePage = 4, itemIndex = 0)),
          sourcePages = listOf(4, 5),
          specialObjectPages = emptySet(),
        ),
        ReflowScreenPage(
          screenIndex = 2,
          elements = listOf(PaginatedReflowElement.ParagraphSlice("p5", false, sourcePage = 5, itemIndex = 0)),
          sourcePages = listOf(5, 6),
          specialObjectPages = emptySet(),
        ),
      )

    // Missing itemIndex on page 5 -> prefer screen starting with page 5 (index 2) over [4, 5] (index 1)
    assertEquals(2, PdfReflowPaginator.findScreenIndexForAnchor(screens, ReflowReadingAnchor(5, itemIndex = 9)))
    // Missing page 1 -> nearest page is 0 (distance 1 vs distance 3)
    assertEquals(0, PdfReflowPaginator.findScreenIndexForAnchor(screens, ReflowReadingAnchor(1, itemIndex = 0)))
    // Missing page 3 -> nearest page is 4 (distance 1 vs distance 3)
    assertEquals(1, PdfReflowPaginator.findScreenIndexForAnchor(screens, ReflowReadingAnchor(3, itemIndex = 0)))
  }

  @Test
  fun evaluateScreenSettleDistinguishesSubPageTurnsFromRelayoutAndPageBoundaries() {
    val screen0Page5 =
      ReflowScreenPage(
        screenIndex = 0,
        elements = listOf(PaginatedReflowElement.ParagraphSlice("part 1", false, sourcePage = 5, itemIndex = 0, charOffset = 0)),
        sourcePages = listOf(5),
        specialObjectPages = emptySet(),
      )
    val screen1Page5 =
      ReflowScreenPage(
        screenIndex = 1,
        elements = listOf(PaginatedReflowElement.ParagraphSlice("part 2", false, sourcePage = 5, itemIndex = 0, charOffset = 7)),
        sourcePages = listOf(5),
        specialObjectPages = emptySet(),
      )
    val screen2Page6 =
      ReflowScreenPage(
        screenIndex = 2,
        elements = listOf(PaginatedReflowElement.ParagraphSlice("page 6", false, sourcePage = 6, itemIndex = 0, charOffset = 0)),
        sourcePages = listOf(6),
        specialObjectPages = emptySet(),
      )

    // Initial / relayout settle preserves anchor and never counts as sub-page turn
    val relayoutSettle =
      PdfReflowPaginator.evaluateScreenSettle(
        screen = screen1Page5,
        previousScreenIndex = null,
        previousPdfPage = 5,
        isExplicitJump = false,
      )
    assertNull(relayoutSettle.updatedAnchor)
    assertFalse(relayoutSettle.isSubPageTurn)

    // Turn within same PDF page (5 -> 5) updates anchor and signals sub-page turn
    val samePageTurn =
      PdfReflowPaginator.evaluateScreenSettle(
        screen = screen1Page5,
        previousScreenIndex = 0,
        previousPdfPage = 5,
        isExplicitJump = false,
      )
    assertEquals(screen1Page5.startAnchor, samePageTurn.updatedAnchor)
    assertTrue(samePageTurn.isSubPageTurn)

    // Turn crossing to new PDF page (5 -> 6) updates anchor but does NOT trigger sub-page turn (handled by onPageChanged)
    val nextPageTurn =
      PdfReflowPaginator.evaluateScreenSettle(
        screen = screen2Page6,
        previousScreenIndex = 1,
        previousPdfPage = 5,
        isExplicitJump = false,
      )
    assertEquals(screen2Page6.startAnchor, nextPageTurn.updatedAnchor)
    assertFalse(nextPageTurn.isSubPageTurn)

    // Explicit jump does not overwrite jump anchor or count as sub-page turn
    val explicitJump =
      PdfReflowPaginator.evaluateScreenSettle(
        screen = screen0Page5,
        previousScreenIndex = 1,
        previousPdfPage = 5,
        isExplicitJump = true,
      )
    assertNull(explicitJump.updatedAnchor)
    assertFalse(explicitJump.isSubPageTurn)
  }
}
