package com.example.emptyapp.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshFlashControllerTest {
  private val every3 = EInkSettings(flashEnabled = true, flashEveryPages = 3)

  @Test
  fun flashesOnEveryNthPageTurn() {
    val controller = RefreshFlashController()
    val results = List(6) { controller.onPageTurn(every3) }
    assertEquals(listOf(false, false, true, false, false, true), results)
  }

  @Test
  fun neverFlashesWhenDisabled() {
    val controller = RefreshFlashController()
    val disabled = every3.copy(flashEnabled = false)
    assertFalse(List(10) { controller.onPageTurn(disabled) }.any { it })
  }

  @Test
  fun resetRestartsTheCount() {
    val controller = RefreshFlashController()
    controller.onPageTurn(every3)
    controller.onPageTurn(every3)
    controller.reset()
    assertFalse(controller.onPageTurn(every3))
  }

  @Test
  fun everyPageWhenIntervalIsOne() {
    val controller = RefreshFlashController()
    val settings = every3.copy(flashEveryPages = 1)
    assertTrue(controller.onPageTurn(settings))
    assertTrue(controller.onPageTurn(settings))
  }

  @Test
  fun intervalBelowOneIsTreatedAsOne() {
    val controller = RefreshFlashController()
    assertTrue(controller.onPageTurn(every3.copy(flashEveryPages = 0)))
  }

  @Test
  fun ghostingTracksPagesEvenWhenFlashDisabled() {
    val controller = RefreshFlashController()
    val ghostOnly = EInkSettings(flashEnabled = false, ghosting = true, flashEveryPages = 3)
    val s1 = controller.onPageTurnStep(ghostOnly)
    val s2 = controller.onPageTurnStep(ghostOnly)
    val s3 = controller.onPageTurnStep(ghostOnly)
    assertFalse(s1.fullRefresh)
    assertEquals(1, s1.pagesSinceFlash)
    assertEquals(2, s2.pagesSinceFlash)
    assertFalse(s3.fullRefresh)
    assertEquals(0, s3.pagesSinceFlash)
  }
}
