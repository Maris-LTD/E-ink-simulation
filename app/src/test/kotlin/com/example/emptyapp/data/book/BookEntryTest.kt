package com.example.emptyapp.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookEntryTest {
  private fun book(
    id: String,
    format: BookFormat,
    progress: Float,
    updatedAt: Long,
  ): BookEntry =
    BookEntry(
      id = id,
      title = "Same Title",
      author = "Author",
      fileName = "$id.${format.extension}",
      format = format,
      progress = progress,
      updatedAt = updatedAt,
    )

  @Test
  fun mostRecentlyReadBookPicksLatestUpdatedInProgressBookOverEarlierListItem() {
    val pdfEarlier = book("pdf-1", BookFormat.PDF, progress = 0.05f, updatedAt = 1_000L)
    val epubMoreRecent = book("epub-1", BookFormat.EPUB, progress = 0.35f, updatedAt = 5_000L)

    val picked = listOf(pdfEarlier, epubMoreRecent).mostRecentlyReadBook()
    assertEquals("epub-1", picked?.id)
  }

  @Test
  fun mostRecentlyReadBookFallsBackToLatestUpdatedWhenNoneInProgress() {
    val unreadOld = book("old", BookFormat.PDF, progress = 0f, updatedAt = 100L)
    val finishedNew = book("done", BookFormat.EPUB, progress = 1f, updatedAt = 2_000L)

    val picked = listOf(unreadOld, finishedNew).mostRecentlyReadBook()
    assertEquals("done", picked?.id)
    assertNull(emptyList<BookEntry>().mostRecentlyReadBook())
  }
}
