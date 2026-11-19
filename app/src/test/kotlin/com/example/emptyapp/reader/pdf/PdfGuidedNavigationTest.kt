package com.example.emptyapp.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfGuidedNavigationTest {

  @Test
  fun guidedModeBlocksPageTurnsWhenPageAnalysisIsMissingOrLandingPending() {
    val nextWhileAnalyzing =
      PdfGuidedNavigation.stepNext(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        hasAnalysis = false,
        currentRegionIndex = 0,
        regionCount = 1,
      )
    assertTrue(nextWhileAnalyzing.consumed)
    assertEquals(0, nextWhileAnalyzing.nextRegionIndex)

    val prevWhileAnalyzing =
      PdfGuidedNavigation.stepPrev(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        hasAnalysis = false,
        currentRegionIndex = 0,
      )
    assertTrue(prevWhileAnalyzing.consumed)
    assertEquals(0, prevWhileAnalyzing.nextRegionIndex)

    val prevWhileLanding =
      PdfGuidedNavigation.stepPrev(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        hasAnalysis = true,
        currentRegionIndex = 0,
        awaitingBackwardLanding = true,
      )
    assertTrue(prevWhileLanding.consumed)
    assertEquals(0, prevWhileLanding.nextRegionIndex)
  }

  @Test
  fun guidedModeStepsAcrossRegionsAndTurnsPageOnlyAtBoundaries() {
    val step1 =
      PdfGuidedNavigation.stepNext(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        hasAnalysis = true,
        currentRegionIndex = 0,
        regionCount = 3,
      )
    assertTrue(step1.consumed)
    assertEquals(1, step1.nextRegionIndex)

    val stepEnd =
      PdfGuidedNavigation.stepNext(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        hasAnalysis = true,
        currentRegionIndex = 2,
        regionCount = 3,
      )
    assertFalse(stepEnd.consumed)
    assertEquals(2, stepEnd.nextRegionIndex)

    val backMid =
      PdfGuidedNavigation.stepPrev(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        hasAnalysis = true,
        currentRegionIndex = 2,
      )
    assertTrue(backMid.consumed)
    assertEquals(1, backMid.nextRegionIndex)

    val backStart =
      PdfGuidedNavigation.stepPrev(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        hasAnalysis = true,
        currentRegionIndex = 0,
      )
    assertFalse(backStart.consumed)
    assertEquals(0, backStart.nextRegionIndex)
  }

  @Test
  fun smartOriginalDoesNotConsumeRegionNavigation() {
    assertFalse(
      PdfGuidedNavigation.stepNext(
        readingMode = PdfReadingMode.SMART_ORIGINAL,
        hasAnalysis = false,
        currentRegionIndex = 0,
        regionCount = 3,
      ).consumed,
    )
    assertFalse(
      PdfGuidedNavigation.stepPrev(
        readingMode = PdfReadingMode.SMART_ORIGINAL,
        hasAnalysis = false,
        currentRegionIndex = 1,
      ).consumed,
    )
  }

  @Test
  fun pendingBackwardLandingIsPreservedUntilRealAnalysisArrives() {
    val regions =
      listOf(
        PdfRect(0f, 0f, 0.5f, 0.5f),
        PdfRect(0f, 0.5f, 0.5f, 1f),
        PdfRect(0.5f, 0f, 1f, 1f),
      )
    var consumedCount = 0

    val beforeAnalysis =
      PdfGuidedNavigation.resolveRegionIndex(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        pageAnalysis = null,
        guidedRegions = regions,
        currentRegionIndex = 0,
        pendingBackwardLandingPage = 4,
        pageIndex = 4,
        onConsumeBackwardLanding = { consumedCount++ },
      )
    assertEquals(0, beforeAnalysis)
    assertEquals(0, consumedCount)

    val realAnalysis = PdfPageAnalyzer.emptyPageAnalysis(pageIndex = 4)
    val afterAnalysis =
      PdfGuidedNavigation.resolveRegionIndex(
        readingMode = PdfReadingMode.GUIDED_COLUMN,
        pageAnalysis = realAnalysis,
        guidedRegions = regions,
        currentRegionIndex = 0,
        pendingBackwardLandingPage = 4,
        pageIndex = 4,
        onConsumeBackwardLanding = { consumedCount++ },
      )
    assertEquals(2, afterAnalysis)
    assertEquals(1, consumedCount)
  }
}
