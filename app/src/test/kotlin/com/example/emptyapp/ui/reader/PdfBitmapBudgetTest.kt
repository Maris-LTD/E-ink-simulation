package com.example.emptyapp.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBitmapBudgetTest {
  private val mb = 1024L * 1024L

  @Test
  fun cacheIsOneEighthOfHeapWithinBounds() {
    assertEquals(32 * 1024, PdfBitmapBudget.fromHeapBytes(256 * mb).cacheKiloBytes)
    assertEquals(16 * 1024, PdfBitmapBudget.fromHeapBytes(64 * mb).cacheKiloBytes)
    assertEquals(96 * 1024, PdfBitmapBudget.fromHeapBytes(2048 * mb).cacheKiloBytes)
  }

  @Test
  fun singleBitmapCappedAt32MegabytesOrEighthOfHeap() {
    assertEquals(8L * mb, PdfBitmapBudget.fromHeapBytes(512 * mb).maxBitmapPixels)
    assertEquals(4L * mb, PdfBitmapBudget.fromHeapBytes(128 * mb).maxBitmapPixels)
  }

  @Test
  fun downscaleKeepsSmallRendersAndShrinksLargeOnesUnderCap() {
    val budget = PdfBitmapBudget(cacheKiloBytes = 1, maxBitmapPixels = 8_000_000L)

    assertEquals(1f, budget.downscaleFactor(1080f, 1527f))

    val factor = budget.downscaleFactor(2880f, 4073f)
    assertTrue(factor < 1f)
    val pixels = (2880f * factor).toDouble() * (4073f * factor).toDouble()
    assertTrue(pixels <= 8_000_000.0 * 1.001)
  }
}
