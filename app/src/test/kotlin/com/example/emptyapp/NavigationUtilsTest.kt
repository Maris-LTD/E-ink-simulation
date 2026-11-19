package com.example.emptyapp

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationUtilsTest {
  @Test
  fun popBackStackSafelyRespectsSizeAndExpectedTop() {
    val stack = mutableListOf<NavKey>(Library, ReadingStats)

    // Expected top mismatch returns false, stack unchanged
    assertFalse(popBackStackSafely(stack, Library))
    assertEquals(2, stack.size)

    // Expected top match with size > 1 pops successfully
    assertTrue(popBackStackSafely(stack, ReadingStats))
    assertEquals(1, stack.size)
    assertEquals(Library, stack.lastOrNull())

    // Trying to pop when size <= 1 returns false
    assertFalse(popBackStackSafely(stack, Library))
    assertEquals(1, stack.size)
    assertEquals(Library, stack.lastOrNull())
  }

  @Test
  fun pushNavKeyIfNotTopAvoidsDuplicates() {
    val stack = mutableListOf<NavKey>(Library)

    // Push new key succeeds
    assertTrue(pushNavKeyIfNotTop(stack, ReadingStats))
    assertEquals(2, stack.size)
    assertEquals(ReadingStats, stack.lastOrNull())

    // Push same key when it's already top returns false, stack unchanged
    assertFalse(pushNavKeyIfNotTop(stack, ReadingStats))
    assertEquals(2, stack.size)
  }
}
