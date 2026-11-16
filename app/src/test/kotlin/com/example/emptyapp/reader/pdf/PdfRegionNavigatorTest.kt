package com.example.emptyapp.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfRegionNavigatorTest {

  @Test
  fun buildsGuidedRegionsInColumnOrderWithOverlap() {
    val leftLines =
      (0 until 12).map { idx ->
        PdfTextLine(
          text = "Left $idx",
          bounds = PdfRect(0.08f, 0.10f + idx * 0.065f, 0.46f, 0.14f + idx * 0.065f),
          fontSizePt = 10f,
        )
      }
    val rightLines =
      (0 until 12).map { idx ->
        PdfTextLine(
          text = "Right $idx",
          bounds = PdfRect(0.54f, 0.10f + idx * 0.065f, 0.92f, 0.14f + idx * 0.065f),
          fontSizePt = 10f,
        )
      }
    val analysis =
      PdfPageAnalysis(
        pageIndex = 0,
        pageWidthPt = 595f,
        pageHeightPt = 842f,
        charCount = 400,
        textLines = leftLines + rightLines,
        imageBlocks = emptyList(),
        totalImageAreaFraction = 0f,
        vectorOpCount = 0,
        columnCount = 2,
      )

    val regions = PdfRegionNavigator.buildGuidedRegions(analysis, viewportAspect = 0.5f)
    assertTrue(regions.size >= 4)
    val firstLeftEnd = regions.indexOfFirst { it.left > 0.45f }
    assertTrue(firstLeftEnd >= 2)
    // Consecutive regions in the first column overlap vertically
    assertTrue(regions[1].top < regions[0].bottom)
  }

  @Test
  fun findsImageOrColumnOnTapAndComputesCameraTransform() {
    val img = PdfImageBlock(PdfRect(0.20f, 0.30f, 0.80f, 0.60f), 0.18f)
    val analysis =
      PdfPageAnalysis(
        pageIndex = 0,
        pageWidthPt = 600f,
        pageHeightPt = 800f,
        charCount = 100,
        textLines = listOf(PdfTextLine("Caption", PdfRect(0.20f, 0.65f, 0.80f, 0.68f), 10f)),
        imageBlocks = listOf(img),
        totalImageAreaFraction = 0.18f,
        vectorOpCount = 0,
        columnCount = 1,
      )

    val hit = PdfRegionNavigator.findTapTargetRect(analysis, tapNormX = 0.50f, tapNormY = 0.45f)
    assertNotNull(hit)
    val cam = PdfRegionNavigator.computeCameraTransform(hit!!, 600f, 800f, 1080f, 1920f)
    assertTrue(cam.scale > 1.2f)
  }

  @Test
  fun horizontalLockZeroesSmallHorizontalPanDuringVerticalScroll() {
    val (x, y) = PdfRegionNavigator.applyHorizontalLock(panX = 4f, panY = 25f)
    assertEquals(0f, x, 0.001f)
    assertEquals(25f, y, 0.001f)
  }
}
