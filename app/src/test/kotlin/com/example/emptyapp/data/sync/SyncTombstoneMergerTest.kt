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
  fun upsertCreatesRestoreTombstoneWhenAbsentAndBeatsOlderRemoteDeletion() {
    val localAfterImport =
      SyncTombstoneMerger.upsert(
        existing = emptyList(),
        keys = listOf("book-x"),
        deleted = false,
        now = 200L,
      )
    val remoteDeletion = listOf(SyncTombstone("book-x", changedAt = 100L, deleted = true))

    val merged = SyncTombstoneMerger.merge(localAfterImport, remoteDeletion, now = 300L)

    assertEquals(listOf(SyncTombstone("book-x", changedAt = 200L, deleted = false)), merged)
    assertEquals(emptySet<String>(), SyncTombstoneMerger.deletedKeys(merged))
  }

  @Test
  fun upsertRefreshesRestoreTimestampWhenAlreadyNotDeleted() {
    val existing = listOf(SyncTombstone("book-x", changedAt = 50L, deleted = false))

    val localAfterReimport =
      SyncTombstoneMerger.upsert(
        existing = existing,
        keys = listOf("book-x"),
        deleted = false,
        now = 200L,
      )
    val remoteDeletion = listOf(SyncTombstone("book-x", changedAt = 100L, deleted = true))
    val merged = SyncTombstoneMerger.merge(localAfterReimport, remoteDeletion, now = 300L)

    assertEquals(listOf(SyncTombstone("book-x", changedAt = 200L, deleted = false)), merged)
    assertEquals(emptySet<String>(), SyncTombstoneMerger.deletedKeys(merged))
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

  @Test
  fun mergeClampsFarFutureTombstoneAndAllowsSubsequentLocalRevive() {
    val farFuture = now + 365L * 24L * 60L * 60L * 1000L * 5L
    val remoteFutureDelete = listOf(SyncTombstone("book-future", changedAt = farFuture, deleted = true))

    val mergedAfterSync = SyncTombstoneMerger.merge(emptyList(), remoteFutureDelete, now)
    assertEquals(now, mergedAfterSync.single().changedAt)
    assertEquals(setOf("book-future"), SyncTombstoneMerger.deletedKeys(mergedAfterSync))

    // Slight clock skew within MAX_FUTURE_SKEW_MS is preserved:
    val slightSkew = now + SyncTombstoneMerger.MAX_FUTURE_SKEW_MS - 1_000L
    val mergedSlightSkew =
      SyncTombstoneMerger.merge(emptyList(), listOf(SyncTombstone("skew", changedAt = slightSkew, deleted = true)), now)
    assertEquals(slightSkew, mergedSlightSkew.single().changedAt)

    // Later (laterNow = now + 1_000L), user re-imports the book on a normal clock;
    // the remote payload now holds the clamped tombstone from the previous sync.
    val laterNow = now + 1_000L
    val revived = SyncTombstoneMerger.upsert(mergedAfterSync, listOf("book-future"), deleted = false, now = laterNow)
    val mergedAgain = SyncTombstoneMerger.merge(revived, mergedAfterSync, now = laterNow)

    assertEquals(emptySet<String>(), SyncTombstoneMerger.deletedKeys(mergedAgain))
    assertEquals(laterNow, mergedAgain.single().changedAt)
  }
}
