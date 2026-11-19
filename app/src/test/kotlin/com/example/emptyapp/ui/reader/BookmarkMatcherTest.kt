package com.example.emptyapp.ui.reader

import com.example.emptyapp.data.book.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkMatcherTest {

  @Test
  fun matchModeSelectsReflowableOnlyForNonFixedEpub() {
    assertEquals(BookmarkMatchMode.REFLOWABLE_EPUB, BookmarkMatcher.matchModeFor(BookFormat.EPUB, isFixedLayout = false))
    assertEquals(BookmarkMatchMode.POSITION_BASED, BookmarkMatcher.matchModeFor(BookFormat.EPUB, isFixedLayout = true))
    assertEquals(BookmarkMatchMode.POSITION_BASED, BookmarkMatcher.matchModeFor(BookFormat.PDF, isFixedLayout = false))
    assertEquals(BookmarkMatchMode.POSITION_BASED, BookmarkMatcher.matchModeFor(BookFormat.CBZ, isFixedLayout = false))
  }

  @Test
  fun reflowableEpubDoesNotMatchAdjacentPagesWithSamePosition() {
    val bookmarkOnPage2 = loc(href = "ch1.xhtml", position = 5, progression = 0.2, total = 0.22f)
    val bookmarkOnPage3 = loc(href = "ch1.xhtml", position = 5, progression = 0.3, total = 0.23f)

    val page1 = loc(href = "ch1.xhtml", position = 5, progression = 0.1, total = 0.21f)
    val page2 = loc(href = "ch1.xhtml", position = 5, progression = 0.2, total = 0.22f)
    val page3 = loc(href = "ch1.xhtml", position = 5, progression = 0.3, total = 0.23f)

    assertTrue(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = page2,
        entryLocation = bookmarkOnPage2,
        entryProgress = bookmarkOnPage2.totalProgression,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.2, totalPages = 10),
      ),
    )
    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = page1,
        entryLocation = bookmarkOnPage2,
        entryProgress = bookmarkOnPage2.totalProgression,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.1, totalPages = 10),
      ),
    )
    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = page3,
        entryLocation = bookmarkOnPage2,
        entryProgress = bookmarkOnPage2.totalProgression,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.3, totalPages = 10),
      ),
    )
    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = page2,
        entryLocation = bookmarkOnPage3,
        entryProgress = bookmarkOnPage3.totalProgression,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.2, totalPages = 10),
      ),
    )
  }

  @Test
  fun reflowableEpubMatchesBookmarkWithinCurrentPageInterval() {
    val bookmarkMidPage2 = loc(href = "ch1.xhtml", position = 5, progression = 0.26, total = 0.26f)
    val page2 = loc(href = "ch1.xhtml", position = 5, progression = 0.2, total = 0.2f)
    val page3 = loc(href = "ch1.xhtml", position = 5, progression = 0.3, total = 0.3f)

    assertTrue(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = page2,
        entryLocation = bookmarkMidPage2,
        entryProgress = bookmarkMidPage2.totalProgression,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.2, totalPages = 10),
      ),
    )
    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = page3,
        entryLocation = bookmarkMidPage2,
        entryProgress = bookmarkMidPage2.totalProgression,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.3, totalPages = 10),
      ),
    )
  }

  @Test
  fun reflowableEpubHandlesLastPageAndEndOfChapter() {
    val lastPage = loc(href = "ch1.xhtml", position = 9, progression = 0.9, total = 0.9f)
    val previousPage = loc(href = "ch1.xhtml", position = 8, progression = 0.8, total = 0.8f)
    val endOfChapter = loc(href = "ch1.xhtml", position = 9, progression = 1.0, total = 1.0f)
    val midLastPage = loc(href = "ch1.xhtml", position = 9, progression = 0.95, total = 0.95f)

    assertTrue(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = lastPage,
        entryLocation = endOfChapter,
        entryProgress = 1.0f,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.9, totalPages = 10),
      ),
    )
    assertTrue(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = lastPage,
        entryLocation = midLastPage,
        entryProgress = 0.95f,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.9, totalPages = 10),
      ),
    )
    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = previousPage,
        entryLocation = endOfChapter,
        entryProgress = 1.0f,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.8, totalPages = 10),
      ),
    )
  }

  @Test
  fun reflowableEpubRequiresSameHref() {
    val ch1 = loc(href = "ch1.xhtml", position = 5, progression = 0.2, total = 0.2f)
    val ch2 = loc(href = "ch2.xhtml", position = 5, progression = 0.2, total = 0.2f)

    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = ch2,
        entryLocation = ch1,
        entryProgress = 0.2f,
        pageWindow = EpubPageWindow("ch2.xhtml", 0.2, totalPages = 10),
      ),
    )
  }

  @Test
  fun reflowableEpubFallsBackToSmallToleranceWhenPageInfoUnavailableOrStale() {
    val bookmark = loc(href = "ch1.xhtml", position = 5, progression = 0.205, total = 0.205f)
    val nearBookmark = loc(href = "ch1.xhtml", position = 5, progression = 0.201, total = 0.201f)
    val current = loc(href = "ch1.xhtml", position = 5, progression = 0.2, total = 0.2f)

    assertTrue(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = current,
        entryLocation = nearBookmark,
        entryProgress = 0.201f,
        pageWindow = null,
      ),
    )
    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = current,
        entryLocation = bookmark,
        entryProgress = 0.205f,
        pageWindow = null,
      ),
    )
    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = current,
        entryLocation = bookmark,
        entryProgress = 0.205f,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.2, totalPages = 0),
      ),
    )
    assertFalse(
      BookmarkMatcher.matches(
        mode = BookmarkMatchMode.REFLOWABLE_EPUB,
        current = current,
        entryLocation = bookmark,
        entryProgress = 0.205f,
        pageWindow = EpubPageWindow("ch1.xhtml", 0.1, totalPages = 10),
      ),
    )
  }

  @Test
  fun positionBasedMatchesByPositionForPdfCbzAndFixedLayoutEpub() {
    val pdfBookmark = loc(href = "book.pdf", position = 12, progression = 0.0, total = 0.24f)
    val pdfSamePage = loc(href = "book.pdf", position = 12, progression = 0.0, total = 0.24f)
    val pdfNextPage = loc(href = "book.pdf", position = 13, progression = 0.0, total = 0.26f)

    assertTrue(BookmarkMatcher.matches(BookmarkMatchMode.POSITION_BASED, pdfSamePage, pdfBookmark, 0.24f))
    assertFalse(BookmarkMatcher.matches(BookmarkMatchMode.POSITION_BASED, pdfNextPage, pdfBookmark, 0.24f))

    val cbzBookmark = loc(href = "p004.jpg", position = 4, progression = null, total = 0.2f)
    val cbzSamePage = loc(href = "p004.jpg", position = 4, progression = null, total = 0.2f)
    val cbzOtherHref = loc(href = "p005.jpg", position = 4, progression = null, total = 0.25f)

    assertTrue(BookmarkMatcher.matches(BookmarkMatchMode.POSITION_BASED, cbzSamePage, cbzBookmark, 0.2f))
    assertFalse(BookmarkMatcher.matches(BookmarkMatchMode.POSITION_BASED, cbzOtherHref, cbzBookmark, 0.2f))

    val fxlBookmark = loc(href = "page8.xhtml", position = 8, progression = 0.0, total = 0.4f)
    val fxlSame = loc(href = "page8.xhtml", position = 8, progression = 0.5, total = 0.4f)
    val fxlOther = loc(href = "page8.xhtml", position = 9, progression = 0.0, total = 0.45f)

    assertTrue(BookmarkMatcher.matches(BookmarkMatchMode.POSITION_BASED, fxlSame, fxlBookmark, 0.4f))
    assertFalse(BookmarkMatcher.matches(BookmarkMatchMode.POSITION_BASED, fxlOther, fxlBookmark, 0.4f))
  }

  @Test
  fun nullLocatorOrMissingProgressionFallsBackToTotalProgression() {
    val current = loc(href = "ch1.xhtml", position = 5, progression = 0.2, total = 0.35f)
    assertTrue(BookmarkMatcher.matches(BookmarkMatchMode.REFLOWABLE_EPUB, current, null, 0.351f))
    assertFalse(BookmarkMatcher.matches(BookmarkMatchMode.REFLOWABLE_EPUB, current, null, 0.36f))

    val noProgSameHref = loc(href = "ch1.xhtml", position = 5, progression = null, total = 0.35f)
    assertTrue(BookmarkMatcher.matches(BookmarkMatchMode.REFLOWABLE_EPUB, current, noProgSameHref, 0.351f))
    assertFalse(BookmarkMatcher.matches(BookmarkMatchMode.REFLOWABLE_EPUB, current, noProgSameHref, 0.36f))
  }

  private fun loc(
    href: String,
    position: Int?,
    progression: Double?,
    total: Float,
  ): BookmarkLocation =
    BookmarkLocation(
      href = href,
      position = position,
      progression = progression,
      totalProgression = total,
    )
}
