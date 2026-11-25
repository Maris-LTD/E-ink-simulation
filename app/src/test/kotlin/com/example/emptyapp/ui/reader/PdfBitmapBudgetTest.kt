package com.example.emptyapp.ui.reader

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBitmapBudgetTest {
  private val mb = 1024L * 1024L

  @Test
  fun cacheIsOneEighthOfHeapWithinBounds() {
    assertEquals(32 * 1024, PdfBitmapBudget.fromHeapBytes(256 * mb, isLowRam = false).cacheKiloBytes)
    assertEquals(16 * 1024, PdfBitmapBudget.fromHeapBytes(64 * mb, isLowRam = false).cacheKiloBytes)
    assertEquals(96 * 1024, PdfBitmapBudget.fromHeapBytes(2048 * mb, isLowRam = false).cacheKiloBytes)
  }

  @Test
  fun lowRamBudgetScalesDownCacheAndSingleBitmap() {
    val lowRam = PdfBitmapBudget.fromHeapBytes(256 * mb, isLowRam = true)
    val normal = PdfBitmapBudget.fromHeapBytes(256 * mb, isLowRam = false)
    assertTrue(lowRam.cacheKiloBytes < normal.cacheKiloBytes)
    assertTrue(lowRam.maxBitmapPixels < normal.maxBitmapPixels)
    assertEquals(8 * 1024, PdfBitmapBudget.fromHeapBytes(64 * mb, isLowRam = true).cacheKiloBytes)
    assertEquals(32 * 1024, PdfBitmapBudget.fromHeapBytes(1024 * mb, isLowRam = true).cacheKiloBytes)
  }

  @Test
  fun singleBitmapCappedAt32MegabytesOrEighthOfHeap() {
    assertEquals(8L * mb, PdfBitmapBudget.fromHeapBytes(512 * mb, isLowRam = false).maxBitmapPixels)
    assertEquals(4L * mb, PdfBitmapBudget.fromHeapBytes(128 * mb, isLowRam = false).maxBitmapPixels)
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

  @Test
  fun renderWithMemoryRetryEvictsCacheAndDownscalesBySqrtHalfOnOom() {
    var evictCount = 0
    val scales = mutableListOf<Float>()

    val result =
      renderWithMemoryRetry(onEvictCache = { evictCount++ }) { retryScale ->
        scales += retryScale
        if (scales.size == 1) {
          throw OutOfMemoryError("Simulated OOM")
        }
        "ok:$retryScale"
      }

    assertEquals(1, evictCount)
    assertEquals(2, scales.size)
    assertEquals(1.0f, scales[0], 0.0001f)
    assertEquals(OOM_RETRY_SCALE_FACTOR, scales[1], 0.0001f)
    assertEquals(0.5f, scales[1] * scales[1], 0.0001f)
    assertEquals("ok:$OOM_RETRY_SCALE_FACTOR", result)
  }

  @Test
  fun renderWithMemoryRetryReturnsNullWhenRetryAlsoThrowsOom() {
    var evictCount = 0
    val scales = mutableListOf<Float>()

    val result =
      renderWithMemoryRetry<String>(onEvictCache = { evictCount++ }) { retryScale ->
        scales += retryScale
        throw OutOfMemoryError("Persistent OOM")
      }

    assertNull(result)
    assertEquals(1, evictCount)
    assertEquals(listOf(1.0f, OOM_RETRY_SCALE_FACTOR), scales)
  }

  @Test(expected = CancellationException::class)
  fun renderWithMemoryRetryRethrowsCancellationException() {
    renderWithMemoryRetry<String>(onEvictCache = {}) {
      throw CancellationException("Cancelled")
    }
  }
}
