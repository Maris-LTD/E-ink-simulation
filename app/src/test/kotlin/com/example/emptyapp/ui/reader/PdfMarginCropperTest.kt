package com.example.emptyapp.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMarginCropperTest {

  @Test
  fun blankWhitePageReturnsFullRect() {
    val width = 100
    val height = 100
    val pixels = IntArray(width * height) { WHITE }
    val rect = PdfMarginCropper.detectContentBounds(pixels, width, height)
    assertEquals(NormalizedCropRect.FULL, rect)
  }

  @Test
  fun pageWithWideMarginsCropsToContentPlusPadding() {
    val width = 100
    val height = 100
    val pixels = IntArray(width * height) { WHITE }
    for (y in 20 until 80) {
      for (x in 25 until 75) {
        pixels[y * width + x] = BLACK
      }
    }

    val rect = PdfMarginCropper.detectContentBounds(pixels, width, height, paddingFraction = 0.02f)
    assertEquals(0.23f, rect.left, 0.001f)
    assertEquals(0.18f, rect.top, 0.001f)
    assertEquals(0.77f, rect.right, 0.001f)
    assertEquals(0.82f, rect.bottom, 0.001f)
  }

  @Test
  fun tinyIsolatedMarkReturnsFullRect() {
    val width = 100
    val height = 100
    val pixels = IntArray(width * height) { WHITE }
    for (y in 90 until 94) {
      for (x in 48 until 52) {
        pixels[y * width + x] = BLACK
      }
    }

    val rect = PdfMarginCropper.detectContentBounds(pixels, width, height)
    assertEquals(NormalizedCropRect.FULL, rect)
  }

  @Test
  fun fullPageContentReturnsFullRect() {
    val width = 100
    val height = 100
    val pixels = IntArray(width * height) { WHITE }
    for (y in 1 until 99) {
      for (x in 1 until 99) {
        pixels[y * width + x] = BLACK
      }
    }

    val rect = PdfMarginCropper.detectContentBounds(pixels, width, height)
    assertEquals(NormalizedCropRect.FULL, rect)
    assertTrue(rect.width == 1f && rect.height == 1f)
  }

  private companion object {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
  }
}
