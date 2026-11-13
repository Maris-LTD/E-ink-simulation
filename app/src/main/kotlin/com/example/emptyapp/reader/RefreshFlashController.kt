package com.example.emptyapp.reader

data class PageTurnRefresh(val fullRefresh: Boolean, val pagesSinceFlash: Int)

class RefreshFlashController {
  private var pagesSinceFlash = 0

  fun onPageTurn(settings: EInkSettings): Boolean = onPageTurnStep(settings).fullRefresh

  fun onPageTurnStep(settings: EInkSettings): PageTurnRefresh {
    if (!settings.flashEnabled) {
      pagesSinceFlash = 0
      return PageTurnRefresh(fullRefresh = false, pagesSinceFlash = 0)
    }
    pagesSinceFlash++
    if (pagesSinceFlash < settings.flashEveryPages.coerceAtLeast(1)) {
      return PageTurnRefresh(fullRefresh = false, pagesSinceFlash = pagesSinceFlash)
    }
    pagesSinceFlash = 0
    return PageTurnRefresh(fullRefresh = true, pagesSinceFlash = 0)
  }

  fun reset() {
    pagesSinceFlash = 0
  }
}
