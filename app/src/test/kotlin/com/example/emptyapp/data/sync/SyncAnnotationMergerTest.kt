package com.example.emptyapp.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncAnnotationMergerTest {
  private fun bookmark(id: String, hash: String) =
    SyncedBookmark(id = id, bookContentHash = hash, title = id, progress = 0f, locatorJson = "{}", createdAt = 0L)

  private fun highlight(id: String, hash: String) =
    SyncedHighlight(
      id = id,
      bookContentHash = hash,
      bookTitle = "t",
      quote = id,
      progress = 0f,
      locatorJson = "{}",
      createdAt = 0L,
    )

  @Test
  fun keepsRemoteBookmarksForBooksMissingOnThisDevice() {
    val local = listOf(bookmark("l1", "local-book"))
    val remote = listOf(bookmark("r1", "remote-only-book"), bookmark("r2", "local-book"))

    val result =
      SyncAnnotationMerger.bookmarksToUpload(
        local = local,
        remote = remote,
        localBookHashes = setOf("local-book"),
        deletedHashes = emptySet(),
      )

    assertEquals(listOf("l1", "r1"), result.map { it.id })
  }

  @Test
  fun dropsRemoteAnnotationsOfDeletedBooks() {
    val remote = listOf(highlight("r1", "deleted-book"), highlight("r2", "other-book"))

    val result =
      SyncAnnotationMerger.highlightsToUpload(
        local = emptyList(),
        remote = remote,
        localBookHashes = emptySet(),
        deletedHashes = setOf("deleted-book"),
      )

    assertEquals(listOf("r2"), result.map { it.id })
  }

  @Test
  fun dropsDeletedAnnotationIdsFromLocalAndRemote() {
    val local = listOf(bookmark("deleted-local", "book-a"), bookmark("kept", "book-a"))
    val remote = listOf(bookmark("deleted-remote", "remote-only-book"))

    val result =
      SyncAnnotationMerger.bookmarksToUpload(
        local = local,
        remote = remote,
        localBookHashes = setOf("book-a"),
        deletedHashes = emptySet(),
        deletedIds = setOf("deleted-local", "deleted-remote"),
      )

    assertEquals(listOf("kept"), result.map { it.id })
  }

  @Test
  fun localVersionWinsWhenSameIdExistsInRemote() {
    val local = listOf(highlight("same", "book-a").copy(note = "local note"))
    val remote = listOf(highlight("same", "book-a").copy(note = "remote note"))

    val result =
      SyncAnnotationMerger.highlightsToUpload(
        local = local,
        remote = remote,
        localBookHashes = emptySet(),
        deletedHashes = emptySet(),
      )

    assertEquals(listOf("local note"), result.map { it.note })
  }
}
