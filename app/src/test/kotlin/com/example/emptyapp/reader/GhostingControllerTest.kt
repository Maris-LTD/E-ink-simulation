package com.example.emptyapp.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class GhostingControllerTest {
  private val ghostEnabled = EInkSettings(ghosting = true)

  @Test
  fun cyclesGhostingCountWhenEnabled() {
    val controller = GhostingController(cyclePages = 3)
    val results = List(6) { controller.onPageTurn(ghostEnabled) }
    assertEquals(listOf(1, 2, 0, 1, 2, 0), results)
  }

  @Test
  fun returnsZeroWhenGhostingDisabled() {
    val controller = GhostingController(cyclePages = 3)
    val disabled = ghostEnabled.copy(ghosting = false)
    assertEquals(List(10) { 0 }, List(10) { controller.onPageTurn(disabled) })
  }

  @Test
  fun resetRestartsTheCount() {
    val controller = GhostingController(cyclePages = 3)
    assertEquals(1, controller.onPageTurn(ghostEnabled))
    assertEquals(2, controller.onPageTurn(ghostEnabled))
    controller.reset()
    assertEquals(1, controller.onPageTurn(ghostEnabled))
  }

  @Test
  fun cycleOfOneOrLessAlwaysResetsToZero() {
    val cycleOne = GhostingController(cyclePages = 1)
    assertEquals(0, cycleOne.onPageTurn(ghostEnabled))
    assertEquals(0, cycleOne.onPageTurn(ghostEnabled))

    val cycleZero = GhostingController(cyclePages = 0)
    assertEquals(0, cycleZero.onPageTurn(ghostEnabled))
  }
}
