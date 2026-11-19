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

  @Test
  fun mapPageRectToCropSpaceConvertsTwoColumnTargetIntoCroppedBitmapSpace() {
    val crop = PdfRect(left = 0.10f, top = 0.08f, right = 0.90f, bottom = 0.92f)
    val leftColumnPageRect = PdfRect(left = 0.14f, top = 0.20f, right = 0.46f, bottom = 0.50f)

    val mapped = PdfRegionNavigator.mapPageRectToCropSpace(leftColumnPageRect, crop)

    assertEquals(0.05f, mapped.left, 0.001f)
    assertEquals(0.45f, mapped.right, 0.001f)
    assertEquals((0.20f - 0.08f) / 0.84f, mapped.top, 0.001f)
    assertEquals((0.50f - 0.08f) / 0.84f, mapped.bottom, 0.001f)

    val croppedCam =
      PdfRegionNavigator.computeCameraTransform(
        region = mapped,
        bitmapWidth = 800f,
        bitmapHeight = 1120f,
        containerWidth = 1000f,
        containerHeight = 1400f,
      )
    val unmappedCam =
      PdfRegionNavigator.computeCameraTransform(
        region = leftColumnPageRect,
        bitmapWidth = 800f,
        bitmapHeight = 1120f,
        containerWidth = 1000f,
        containerHeight = 1400f,
      )
    // In crop space the left column is 0.40 wide (scale = 2.5x, offsetX = +625px) instead of 0.32 wide
    // in page space (scale = 3.125x, offsetX = +625px -> over-zoomed and shifted ~170px relative to cropped bitmap).
    assertEquals(2.5f, croppedCam.scale, 0.01f)
    assertEquals(625f, croppedCam.offsetX, 0.5f)
    assertTrue(unmappedCam.scale > croppedCam.scale + 0.6f)
  }

  @Test
  fun mapPageRectToCropSpaceFallsBackSafelyWhenCropIsDegenerateOrDisjoint() {
    val rect = PdfRect(0.15f, 0.20f, 0.85f, 0.80f)
    val zeroCrop = PdfRect(0.50f, 0.50f, 0.50f, 0.50f)
    assertEquals(rect, PdfRegionNavigator.mapPageRectToCropSpace(rect, zeroCrop))

    val disjointCrop = PdfRect(0.92f, 0.92f, 0.99f, 0.99f)
    assertEquals(rect, PdfRegionNavigator.mapPageRectToCropSpace(rect, disjointCrop))
  }

  @Test
  fun clampOffsetForFitContentPreventsPanningIntoLetterboxDeadSpace() {
    // Container is 1000x1000, tall bitmap is 500x1000 -> ContentScale.Fit displays at 500x1000
    // At scale = 1.5x, displayed width is 750 (< 1000 container), so horizontal pan must stay 0!
    // Displayed height is 1500 (> 1000 container), so vertical pan max is (1500 - 1000) / 2 = 250.
    val (clampedX1, clampedY1) =
      PdfRegionNavigator.clampOffsetForFitContent(
        offsetX = 180f,
        offsetY = -400f,
        scale = 1.5f,
        containerWidth = 1000f,
        containerHeight = 1000f,
        bitmapWidth = 500f,
        bitmapHeight = 1000f,
      )
    assertEquals(0f, clampedX1, 0.001f)
    assertEquals(-250f, clampedY1, 0.001f)

    // At scale = 2.4x, displayed width is 1200 (> 1000 container) -> horizontal max is (1200 - 1000)/2 = 100.
    val (clampedX2, clampedY2) =
      PdfRegionNavigator.clampOffsetForFitContent(
        offsetX = 180f,
        offsetY = 300f,
        scale = 2.4f,
        containerWidth = 1000f,
        containerHeight = 1000f,
        bitmapWidth = 500f,
        bitmapHeight = 1000f,
      )
    assertEquals(100f, clampedX2, 0.001f)
    assertEquals(300f, clampedY2, 0.001f)
  }

  @Test
  fun clampOffsetForFitContentFallsBackToContainerSizeWhenBitmapMissing() {
    val (clampedX, clampedY) =
      PdfRegionNavigator.clampOffsetForFitContent(
        offsetX = 500f,
        offsetY = -900f,
        scale = 2.0f,
        containerWidth = 800f,
        containerHeight = 1200f,
        bitmapWidth = 0f,
        bitmapHeight = 0f,
      )
    assertEquals(400f, clampedX, 0.001f)
    assertEquals(-600f, clampedY, 0.001f)
  }
}
