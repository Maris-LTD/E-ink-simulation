package com.example.emptyapp.reader

data class PageTurnRefresh(val fullRefresh: Boolean, val pagesSinceFlash: Int)

class RefreshFlashController {
  private var pagesSinceFlash = 0

  fun onPageTurn(settings: EInkSettings): Boolean = onPageTurnStep(settings).fullRefresh

  fun onPageTurnStep(settings: EInkSettings): PageTurnRefresh {
    if (!settings.flashEnabled && !settings.ghosting) {
      pagesSinceFlash = 0
      return PageTurnRefresh(fullRefresh = false, pagesSinceFlash = 0)
    }
    pagesSinceFlash++
    val interval = settings.flashEveryPages.coerceAtLeast(1)
    if (pagesSinceFlash < interval) {
      return PageTurnRefresh(fullRefresh = false, pagesSinceFlash = pagesSinceFlash)
    }
    pagesSinceFlash = 0
    return PageTurnRefresh(fullRefresh = settings.flashEnabled, pagesSinceFlash = 0)
  }

  fun reset() {
    pagesSinceFlash = 0
  }
}
