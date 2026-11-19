package com.example.emptyapp

import com.example.emptyapp.ui.eink.CoverBitmapCache
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherHomeLayoutTest {
  @Test
  fun slicePinnedAppsForHomeKeepsAllAppsUpToMaxAndCountsOverflowAboveMax() {
    val empty = slicePinnedAppsForHome(emptyList<String>())
    assertEquals(emptyList<String>(), empty.visibleApps)
    assertEquals(0, empty.overflowCount)

    val eight = (1..8).map { "app-$it" }
    val exact = slicePinnedAppsForHome(eight)
    assertEquals(eight, exact.visibleApps)
    assertEquals(0, exact.overflowCount)

    val twelve = (1..12).map { "app-$it" }
    val capped = slicePinnedAppsForHome(twelve)
    assertEquals(eight, capped.visibleApps)
    assertEquals(4, capped.overflowCount)

    val negativeCap = slicePinnedAppsForHome(twelve, maxVisible = -3)
    assertEquals(emptyList<String>(), negativeCap.visibleApps)
    assertEquals(12, negativeCap.overflowCount)
  }

  @Test
  fun layoutHomeSectionPositionsUsesMinimumSpacingWhenOverflowingAndSpaceBetweenWhenExtraHeightExists() {
    val sizes = intArrayOf(50, 150, 120, 200)
    val spacingPx = 24

    // Compact / landscape: totalSize == contentSum + spacing * 3 = 520 + 72 = 592
    val compactOut = IntArray(sizes.size)
    layoutHomeSectionPositions(
      totalSize = 592,
      sizes = sizes,
      spacingPx = spacingPx,
      outPositions = compactOut,
    )
    assertArrayEquals(intArrayOf(0, 74, 248, 392), compactOut)

    // Tall / portrait: totalSize = 820 -> extra = 300 across 3 gaps -> 100px per gap
    val tallOut = IntArray(sizes.size)
    layoutHomeSectionPositions(
      totalSize = 820,
      sizes = sizes,
      spacingPx = spacingPx,
      outPositions = tallOut,
    )
    assertArrayEquals(intArrayOf(0, 150, 400, 620), tallOut)

    val singleOut = IntArray(1) { -1 }
    layoutHomeSectionPositions(
      totalSize = 400,
      sizes = intArrayOf(100),
      spacingPx = spacingPx,
      outPositions = singleOut,
    )
    assertArrayEquals(intArrayOf(0), singleOut)
  }

  @Test
  fun coverBitmapCacheColorFilterForMatchesGrayscaleFlag() {
    assertNull(CoverBitmapCache.colorFilterFor(grayscale = false))
    assertNotNull(CoverBitmapCache.colorFilterFor(grayscale = true))
  }
}
