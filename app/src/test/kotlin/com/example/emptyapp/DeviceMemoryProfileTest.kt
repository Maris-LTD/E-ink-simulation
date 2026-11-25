package com.example.emptyapp

import android.content.ComponentCallbacks2
import com.example.emptyapp.reader.pdf.PdfPageAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMemoryProfileTest {
  private val mb = 1024L * 1024L

  @Test
  fun lowRamTierDetectedFromFlagOrSmallMemoryClass() {
    val byFlag = DeviceMemoryTier.from(isLowRamDevice = true, memoryClassMb = 256, maxHeapBytes = 256 * mb)
    assertTrue(byFlag.isLowRam)
    assertEquals(4 * 1024, byFlag.coverCacheSizeKb)
    assertEquals(4 * mb, byFlag.pdfBoxMainMemoryBytes)
    assertEquals(96, byFlag.launcherIconCacheCount)

    val byMemClass = DeviceMemoryTier.from(isLowRamDevice = false, memoryClassMb = 128, maxHeapBytes = 256 * mb)
    assertTrue(byMemClass.isLowRam)
    assertEquals(4 * 1024, byMemClass.coverCacheSizeKb)
  }

  @Test
  fun midAndHighRamTiersScaleCoverAndPdfBoxBudgets() {
    val mid = DeviceMemoryTier.from(isLowRamDevice = false, memoryClassMb = 256, maxHeapBytes = 256 * mb)
    assertFalse(mid.isLowRam)
    assertEquals(8 * 1024, mid.coverCacheSizeKb)
    assertEquals(8 * mb, mid.pdfBoxMainMemoryBytes)
    assertEquals(256, mid.launcherIconCacheCount)

    val high = DeviceMemoryTier.from(isLowRamDevice = false, memoryClassMb = 512, maxHeapBytes = 512 * mb)
    assertFalse(high.isLowRam)
    assertEquals(16 * 1024, high.coverCacheSizeKb)
  }

  @Test
  @Suppress("DEPRECATION")
  fun trimMemoryLevelsDistinguishHalfTrimAndFullEvict() {
    assertFalse(DeviceMemoryProfile.shouldTrimHalf(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
    assertTrue(DeviceMemoryProfile.shouldTrimHalf(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
    assertTrue(DeviceMemoryProfile.shouldTrimHalf(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
    assertFalse(DeviceMemoryProfile.shouldEvictAll(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
    assertTrue(DeviceMemoryProfile.shouldEvictAll(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
    assertTrue(DeviceMemoryProfile.shouldEvictAll(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
  }

  @Test
  fun pdfBoxMemoryUsageSettingUsesMixedTempFileAndMainMemoryCap() {
    val setting = PdfPageAnalyzer.memoryUsageSetting()
    assertTrue(setting.isMainMemoryRestricted)
    assertTrue(setting.useTempFile())
  }
}
