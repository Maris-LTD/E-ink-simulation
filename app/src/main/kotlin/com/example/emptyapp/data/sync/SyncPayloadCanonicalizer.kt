package com.example.emptyapp.data.sync

import com.example.emptyapp.data.stats.DailyReadingStat

object SyncPayloadCanonicalizer {
  private val bookComparator =
    compareBy<SyncedBookProgress>(
      { it.contentHash },
      { it.title },
      { it.updatedAt },
      { it.author },
      { it.format.name },
      { it.progress },
      { it.lastLocatorJson.orEmpty() },
    )

  private val bookmarkComparator =
    compareBy<SyncedBookmark>(
      { it.id },
      { it.bookContentHash },
      { it.createdAt },
      { it.title },
      { it.progress },
      { it.locatorJson },
    )

  private val highlightComparator =
    compareBy<SyncedHighlight>(
      { it.id },
      { it.bookContentHash },
      { it.createdAt },
      { it.bookTitle },
      { it.quote },
      { it.note },
      { it.progress },
      { it.locatorJson },
    )

  private val dailyStatComparator =
    compareBy<DailyReadingStat>(
      { it.date },
      { it.secondsRead },
      { it.pagesTurned },
    )

  private val tombstoneComparator =
    compareBy<SyncTombstone>(
      { it.key },
      { it.changedAt },
      { it.deleted },
    )

  fun canonicalize(payload: SyncPayload): SyncPayload =
    payload.copy(
      books = payload.books.sortedWith(bookComparator),
      bookmarks = payload.bookmarks.sortedWith(bookmarkComparator),
      highlights = payload.highlights.sortedWith(highlightComparator),
      stats =
        payload.stats.copy(
          dailyStats = payload.stats.dailyStats.sortedWith(dailyStatComparator),
        ),
      bookTombstones = payload.bookTombstones.sortedWith(tombstoneComparator),
      annotationTombstones = payload.annotationTombstones.sortedWith(tombstoneComparator),
    )

  fun hasPayloadChanged(localMerged: SyncPayload, remote: SyncPayload?): Boolean {
    if (remote == null) return true
    val canonicalLocal = canonicalize(localMerged).copy(updatedAt = 0L)
    val canonicalRemote = canonicalize(remote).copy(updatedAt = 0L)
    return canonicalLocal != canonicalRemote
  }
}

fun SyncPayload.canonicalized(): SyncPayload = SyncPayloadCanonicalizer.canonicalize(this)
