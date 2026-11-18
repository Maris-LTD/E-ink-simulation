package com.example.emptyapp.data.sync

object SyncAnnotationMerger {
  fun bookmarksToUpload(
    local: List<SyncedBookmark>,
    remote: List<SyncedBookmark>,
    localBookHashes: Set<String>,
    deletedHashes: Set<String>,
    deletedIds: Set<String> = emptySet(),
  ): List<SyncedBookmark> =
    mergeKeepingRemoteOnlyBooks(local, remote, localBookHashes, deletedHashes, deletedIds, { it.id }, { it.bookContentHash })

  fun highlightsToUpload(
    local: List<SyncedHighlight>,
    remote: List<SyncedHighlight>,
    localBookHashes: Set<String>,
    deletedHashes: Set<String>,
    deletedIds: Set<String> = emptySet(),
  ): List<SyncedHighlight> =
    mergeKeepingRemoteOnlyBooks(local, remote, localBookHashes, deletedHashes, deletedIds, { it.id }, { it.bookContentHash })

  private inline fun <T> mergeKeepingRemoteOnlyBooks(
    local: List<T>,
    remote: List<T>,
    localBookHashes: Set<String>,
    deletedHashes: Set<String>,
    deletedIds: Set<String>,
    idOf: (T) -> String,
    bookHashOf: (T) -> String,
  ): List<T> {
    val result = LinkedHashMap<String, T>()
    local
      .filter { bookHashOf(it) !in deletedHashes && idOf(it) !in deletedIds }
      .forEach { result[idOf(it)] = it }
    remote
      .filter { item ->
        val hash = bookHashOf(item)
        hash !in localBookHashes && hash !in deletedHashes && idOf(item) !in deletedIds
      }
      .forEach { item -> result.putIfAbsent(idOf(item), item) }
    return result.values.toList()
  }
}
