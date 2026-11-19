package com.example.emptyapp.data.sync

import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.data.stats.DailyReadingStat
import com.example.emptyapp.data.stats.ReadingStatsData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadCanonicalizerTest {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private fun book(hash: String, title: String, updatedAt: Long = 100L) =
    SyncedBookProgress(
      contentHash = hash,
      title = title,
      author = "Author",
      format = BookFormat.EPUB,
      progress = 0.5f,
      lastLocatorJson = """{"href":"ch1.xhtml"}""",
      updatedAt = updatedAt,
    )

  private fun bookmark(id: String, hash: String = "h1") =
    SyncedBookmark(
      id = id,
      bookContentHash = hash,
      title = "BM $id",
      progress = 0.25f,
      locatorJson = """{"id":"$id"}""",
      createdAt = 100L,
    )

  private fun highlight(id: String, note: String = "") =
    SyncedHighlight(
      id = id,
      bookContentHash = "h1",
      bookTitle = "Book",
      quote = "Quote $id",
      note = note,
      progress = 0.4f,
      locatorJson = """{"id":"$id"}""",
      createdAt = 200L,
    )

  @Test
  fun canonicalizeSortsAllListsByStableKeys() {
    val payload =
      SyncPayload(
        updatedAt = 999L,
        books = listOf(book("h-b", "B", 20L), book("h-a", "A", 10L), book("h-a", "A", 5L)),
        bookmarks = listOf(bookmark("bm-2"), bookmark("bm-1")),
        highlights = listOf(highlight("hl-3"), highlight("hl-1"), highlight("hl-2")),
        stats =
          ReadingStatsData(
            dailyStats =
              listOf(
                DailyReadingStat("2026-11-19", secondsRead = 300, pagesTurned = 5),
                DailyReadingStat("2026-11-17", secondsRead = 600, pagesTurned = 10),
              ),
          ),
        bookTombstones = listOf(SyncTombstone("k-z", 2L), SyncTombstone("k-a", 1L)),
        annotationTombstones = listOf(SyncTombstone("a-2", 2L), SyncTombstone("a-1", 1L)),
      )

    val canonical = payload.canonicalized()

    assertEquals(listOf("h-a", "h-a", "h-b"), canonical.books.map { it.contentHash })
    assertEquals(listOf(5L, 10L, 20L), canonical.books.map { it.updatedAt })
    assertEquals(listOf("bm-1", "bm-2"), canonical.bookmarks.map { it.id })
    assertEquals(listOf("hl-1", "hl-2", "hl-3"), canonical.highlights.map { it.id })
    assertEquals(listOf("2026-11-17", "2026-11-19"), canonical.stats.dailyStats.map { it.date })
    assertEquals(listOf("k-a", "k-z"), canonical.bookTombstones.map { it.key })
    assertEquals(listOf("a-1", "a-2"), canonical.annotationTombstones.map { it.key })
  }

  @Test
  fun hasPayloadChangedIgnoresListOrderAndUpdatedAt() {
    val first =
      SyncPayload(
        updatedAt = 1_000L,
        books = listOf(book("h1", "Book 1"), book("h2", "Book 2")),
        bookmarks = listOf(bookmark("b1"), bookmark("b2")),
        highlights = listOf(highlight("hl1"), highlight("hl2")),
        stats =
          ReadingStatsData(
            dailyGoalMinutes = 25,
            dailyGoalUpdatedAt = 500L,
            dailyStats = listOf(DailyReadingStat("2026-11-18", 200, 3), DailyReadingStat("2026-11-19", 400, 7)),
          ),
        bookTombstones = listOf(SyncTombstone("del-1", 100L), SyncTombstone("del-2", 200L)),
        annotationTombstones = listOf(SyncTombstone("ann-1", 100L), SyncTombstone("ann-2", 200L)),
      )
    val reordered =
      SyncPayload(
        updatedAt = 9_999L,
        books = first.books.reversed(),
        bookmarks = first.bookmarks.reversed(),
        highlights = first.highlights.reversed(),
        stats = first.stats.copy(dailyStats = first.stats.dailyStats.reversed()),
        bookTombstones = first.bookTombstones.reversed(),
        annotationTombstones = first.annotationTombstones.reversed(),
      )

    assertFalse(SyncPayloadCanonicalizer.hasPayloadChanged(first, reordered))
    assertEquals(
      json.encodeToString(SyncPayload.serializer(), first.canonicalized().copy(updatedAt = 0L)),
      json.encodeToString(SyncPayload.serializer(), reordered.canonicalized().copy(updatedAt = 0L)),
    )
  }

  @Test
  fun hasPayloadChangedDetectsMissingRemoteOrContentDifferences() {
    val base =
      SyncPayload(
        updatedAt = 100L,
        books = listOf(book("h1", "Book 1")),
        highlights = listOf(highlight("hl1", note = "old")),
      )

    assertTrue(SyncPayloadCanonicalizer.hasPayloadChanged(base, null))
    assertTrue(
      SyncPayloadCanonicalizer.hasPayloadChanged(
        base.copy(highlights = listOf(highlight("hl1", note = "edited"))),
        base,
      ),
    )
    assertTrue(
      SyncPayloadCanonicalizer.hasPayloadChanged(
        base.copy(bookTombstones = listOf(SyncTombstone("h2", 200L, deleted = true))),
        base,
      ),
    )
  }
}
