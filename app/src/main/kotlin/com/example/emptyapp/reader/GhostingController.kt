package com.example.emptyapp.reader

class GhostingController(private val cyclePages: Int = DEFAULT_CYCLE_PAGES) {
  private var pagesSinceReset = 0

  fun onPageTurn(settings: EInkSettings): Int {
    if (!settings.ghosting) {
      pagesSinceReset = 0
      return 0
    }
    pagesSinceReset++
    if (pagesSinceReset >= cyclePages.coerceAtLeast(1)) {
      pagesSinceReset = 0
    }
    return pagesSinceReset
  }

  fun reset() {
    pagesSinceReset = 0
  }

  companion object {
    const val DEFAULT_CYCLE_PAGES = 6
  }
}
