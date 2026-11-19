package com.example.emptyapp.ui.reader

import com.example.emptyapp.data.book.BookFormat
import kotlin.math.abs

enum class BookmarkMatchMode {
  REFLOWABLE_EPUB,
  POSITION_BASED,
}

data class BookmarkLocation(
  val href: String,
  val position: Int?,
  val progression: Double?,
  val totalProgression: Float,
)

data class EpubPageWindow(
  val href: String,
  val progression: Double?,
  val totalPages: Int,
)

object BookmarkMatcher {
  private const val FALLBACK_TOLERANCE = 0.002
  private const val FALLBACK_TOTAL_TOLERANCE = 0.002f
  private const val BOUNDARY_EPSILON = 1e-6

  fun matchModeFor(format: BookFormat, isFixedLayout: Boolean): BookmarkMatchMode =
    if (format == BookFormat.EPUB && !isFixedLayout) {
      BookmarkMatchMode.REFLOWABLE_EPUB
    } else {
      BookmarkMatchMode.POSITION_BASED
    }

  fun matches(
    mode: BookmarkMatchMode,
    current: BookmarkLocation,
    entryLocation: BookmarkLocation?,
    entryProgress: Float,
    pageWindow: EpubPageWindow? = null,
  ): Boolean {
    if (entryLocation == null) {
      return abs(entryProgress - current.totalProgression) < FALLBACK_TOTAL_TOLERANCE
    }
    if (entryLocation.href != current.href) return false
    return when (mode) {
      BookmarkMatchMode.POSITION_BASED -> matchPositionBased(current, entryLocation, entryProgress)
      BookmarkMatchMode.REFLOWABLE_EPUB -> matchReflowable(current, entryLocation, entryProgress, pageWindow)
    }
  }

  private fun matchPositionBased(
    current: BookmarkLocation,
    entry: BookmarkLocation,
    entryProgress: Float,
  ): Boolean {
    val locPos = current.position
    val entryPos = entry.position
    val locProg = current.progression
    val entryProg = entry.progression
    return when {
      locPos != null && entryPos != null -> locPos == entryPos
      locProg != null && entryProg != null -> abs(locProg - entryProg) < FALLBACK_TOLERANCE
      else -> abs(entryProgress - current.totalProgression) < FALLBACK_TOTAL_TOLERANCE
    }
  }

  private fun matchReflowable(
    current: BookmarkLocation,
    entry: BookmarkLocation,
    entryProgress: Float,
    pageWindow: EpubPageWindow?,
  ): Boolean {
    val locProg = current.progression
    val entryProg = entry.progression
    if (locProg == null || entryProg == null) {
      return abs(entryProgress - current.totalProgression) < FALLBACK_TOTAL_TOLERANCE
    }
    val totalPages =
      pageWindow
        ?.takeIf { it.href == current.href && it.progression == current.progression && it.totalPages > 0 }
        ?.totalPages
        ?: return abs(locProg - entryProg) < FALLBACK_TOLERANCE
    val pageSpan = 1.0 / totalPages.toDouble()
    val eps = minOf(BOUNDARY_EPSILON, pageSpan * 0.01)
    val end = locProg + pageSpan
    val inLowerBound = entryProg >= locProg - eps
    val inUpperBound =
      if (end >= 1.0 - eps) entryProg <= 1.0 + eps
      else entryProg < end - eps
    return inLowerBound && inUpperBound
  }
}
