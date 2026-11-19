package com.example.emptyapp.data.sync

object SyncTombstoneMerger {
  const val MAX_AGE_MS = 365L * 24L * 60L * 60L * 1000L

  fun merge(
    local: List<SyncTombstone>,
    remote: List<SyncTombstone>,
    now: Long,
  ): List<SyncTombstone> {
    val byKey = LinkedHashMap<String, SyncTombstone>()
    (local + remote).forEach { candidate ->
      val current = byKey[candidate.key]
      if (current == null || candidate.isNewerThan(current)) {
        byKey[candidate.key] = candidate
      }
    }
    val cutoff = now - MAX_AGE_MS
    return byKey.values.filter { it.changedAt >= cutoff }
  }

  fun upsert(
    existing: List<SyncTombstone>,
    keys: Collection<String>,
    deleted: Boolean,
    now: Long,
  ): List<SyncTombstone> {
    if (keys.isEmpty()) return existing
    val currentByKey = existing.associateBy { it.key }
    val changedKeys =
      keys.filterTo(LinkedHashSet()) { key ->
        val current = currentByKey[key]
        current == null || current.deleted != deleted || (!deleted && current.changedAt < now)
      }
    if (changedKeys.isEmpty()) return existing
    return existing.filterNot { it.key in changedKeys } +
      changedKeys.map { SyncTombstone(key = it, changedAt = now, deleted = deleted) }
  }

  fun deletedKeys(tombstones: List<SyncTombstone>): Set<String> =
    tombstones.filter { it.deleted }.mapTo(HashSet()) { it.key }

  fun withoutDeleted(
    payload: SyncPayload,
    deletedBookHashes: Set<String>,
    deletedAnnotationIds: Set<String>,
  ): SyncPayload =
    payload.copy(
      books = payload.books.filterNot { it.contentHash in deletedBookHashes },
      bookmarks =
        payload.bookmarks.filterNot {
          it.bookContentHash in deletedBookHashes || it.id in deletedAnnotationIds
        },
      highlights =
        payload.highlights.filterNot {
          it.bookContentHash in deletedBookHashes || it.id in deletedAnnotationIds
        },
    )

  private fun SyncTombstone.isNewerThan(other: SyncTombstone): Boolean =
    changedAt > other.changedAt || (changedAt == other.changedAt && deleted && !other.deleted)
}
