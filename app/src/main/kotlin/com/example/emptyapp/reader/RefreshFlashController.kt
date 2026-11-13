package com.example.emptyapp.reader

class RefreshFlashController {
  private var pagesSinceFlash = 0

  fun onPageTurn(settings: EInkSettings): Boolean {
    if (!settings.flashEnabled) {
      pagesSinceFlash = 0
      return false
    }
    pagesSinceFlash++
    if (pagesSinceFlash < settings.flashEveryPages.coerceAtLeast(1)) return false
    pagesSinceFlash = 0
    return true
  }

  fun reset() {
    pagesSinceFlash = 0
  }
}
