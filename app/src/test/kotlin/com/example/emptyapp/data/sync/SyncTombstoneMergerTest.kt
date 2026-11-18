package com.example.emptyapp.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SyncTombstoneMergerTest {
  private val now = 1_000_000_000_000L

  @Test
  fun newerChangeWinsPerKey() {
    val local = listOf(SyncTombstone("a", changedAt = now - 10, deleted = true))
    val remote = listOf(SyncTombstone("a", changedAt = now - 5, deleted = false))

    val merged = SyncTombstoneMerger.merge(local, remote, now)

    assertEquals(listOf(SyncTombstone("a", now - 5, deleted = false)), merged)
    assertEquals(emptySet<String>(), SyncTombstoneMerger.deletedKeys(merged))
  }

  @Test
  fun deletionWinsOnTie() {
    val local = listOf(SyncTombstone("a", changedAt = now, deleted = false))
    val remote = listOf(SyncTombstone("a", changedAt = now, deleted = true))

    val merged = SyncTombstoneMerger.merge(local, remote, now)

    assertEquals(setOf("a"), SyncTombstoneMerger.deletedKeys(merged))
  }

  @Test
  fun dropsEntriesOlderThanMaxAge() {
    val old = SyncTombstone("old", changedAt = now - SyncTombstoneMerger.MAX_AGE_MS - 1)
    val fresh = SyncTombstone("fresh", changedAt = now - 1)

    val merged = SyncTombstoneMerger.merge(listOf(old), listOf(fresh), now)

    assertEquals(listOf("fresh"), merged.map { it.key })
  }

  @Test
  fun upsertKeepsListWhenStateUnchanged() {
    val existing = listOf(SyncTombstone("a", changedAt = now - 100, deleted = true))

    val result = SyncTombstoneMerger.upsert(existing, listOf("a"), deleted = true, now = now)

    assertSame(existing, result)
  }

  @Test
  fun upsertRevivesWithNewTimestamp() {
    val existing = listOf(SyncTombstone("a", changedAt = now - 100, deleted = true))

    val result = SyncTombstoneMerger.upsert(existing, listOf("a"), deleted = false, now = now)

    assertEquals(listOf(SyncTombstone("a", now, deleted = false)), result)
  }

  @Test
  fun withoutDeletedRemovesBooksAndAnnotations() {
    val payload =
      SyncPayload(
        books = listOf(SyncedBookProgress(contentHash = "gone", title = "x", progress = 0f)),
        bookmarks =
          listOf(
            SyncedBookmark("bm-deleted", "kept-book", "t", 0f, "{}", 0L),
            SyncedBookmark("bm-kept", "kept-book", "t", 0f, "{}", 0L),
            SyncedBookmark("bm-of-gone-book", "gone", "t", 0f, "{}", 0L),
          ),
      )

    val result = SyncTombstoneMerger.withoutDeleted(payload, setOf("gone"), setOf("bm-deleted"))

    assertEquals(emptyList<SyncedBookProgress>(), result.books)
    assertEquals(listOf("bm-kept"), result.bookmarks.map { it.id })
  }
}
