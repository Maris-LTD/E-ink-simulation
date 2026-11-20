package com.example.emptyapp.data.annotation

import com.example.emptyapp.data.sync.SyncTombstoneStore
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnnotationRepositoryTest {
  private lateinit var dir: File
  private lateinit var bookmarksFile: File
  private lateinit var highlightsFile: File
  private lateinit var tombstonesFile: File

  @Before
  fun setUp() {
    dir = createTempDirectory("annotation_repo_test").toFile()
    bookmarksFile = File(dir, "bookmarks.json")
    highlightsFile = File(dir, "highlights.json")
    tombstonesFile = File(dir, "sync_tombstones.json")
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun removeAndRestoreBookmarkRecordTombstoneBeforeUpdatingStateOrCommitting() = runTest {
    val tombstones = SyncTombstoneStore(tombstonesFile, clock = { 1_000L })
    val repo = AnnotationRepository(bookmarksFile, highlightsFile, tombstones)
    repo.addBookmark("book-1", "Page 1", 0.1f, """{"href":"c1.xhtml"}""")
    val entry = repo.bookmarks.value.single()

    var sawDeletionAfterTombstone = false
    val removeObserver =
      launch(UnconfinedTestDispatcher(testScheduler)) {
        repo.bookmarks.drop(1).collect { list ->
          if (list.isEmpty()) {
            assertTrue(entry.id in tombstones.deletedAnnotationIds())
            assertTrue(tombstonesFile.readText().contains(entry.id))
            sawDeletionAfterTombstone = true
          }
        }
      }
    repo.removeBookmark(entry.id)
    removeObserver.cancel()
    assertTrue(sawDeletionAfterTombstone)

    var sawRestoreAfterTombstone = false
    val restoreObserver =
      launch(UnconfinedTestDispatcher(testScheduler)) {
        repo.bookmarks.drop(1).collect { list ->
          if (list.any { it.id == entry.id }) {
            assertFalse(entry.id in tombstones.deletedAnnotationIds())
            val record = tombstones.snapshot().annotations.first { it.key == entry.id }
            assertFalse(record.deleted)
            sawRestoreAfterTombstone = true
          }
        }
      }
    repo.restoreBookmark(entry)
    restoreObserver.cancel()
    assertTrue(sawRestoreAfterTombstone)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun removeAndRestoreHighlightRecordTombstoneBeforeUpdatingStateOrCommitting() = runTest {
    val tombstones = SyncTombstoneStore(tombstonesFile, clock = { 2_000L })
    val repo = AnnotationRepository(bookmarksFile, highlightsFile, tombstones)
    repo.addHighlight("book-1", "Title", "Quote", "Note", 0.2f, """{"href":"c2.xhtml"}""")
    val entry = repo.highlights.value.single()

    var sawDeletionAfterTombstone = false
    val removeObserver =
      launch(UnconfinedTestDispatcher(testScheduler)) {
        repo.highlights.drop(1).collect { list ->
          if (list.isEmpty()) {
            assertTrue(entry.id in tombstones.deletedAnnotationIds())
            assertTrue(tombstonesFile.readText().contains(entry.id))
            sawDeletionAfterTombstone = true
          }
        }
      }
    repo.removeHighlight(entry.id)
    removeObserver.cancel()
    assertTrue(sawDeletionAfterTombstone)

    var sawRestoreAfterTombstone = false
    val restoreObserver =
      launch(UnconfinedTestDispatcher(testScheduler)) {
        repo.highlights.drop(1).collect { list ->
          if (list.any { it.id == entry.id }) {
            assertFalse(entry.id in tombstones.deletedAnnotationIds())
            val record = tombstones.snapshot().annotations.first { it.key == entry.id }
            assertFalse(record.deleted)
            sawRestoreAfterTombstone = true
          }
        }
      }
    repo.restoreHighlight(entry)
    restoreObserver.cancel()
    assertTrue(sawRestoreAfterTombstone)
  }

  @Test
  fun bookmarkAndHighlightWriteErrorsDoNotCrashKeepDirtyAndFlushOnNextNoopCommit() = runTest {
    val tombstones = SyncTombstoneStore(tombstonesFile, clock = { 3_000L })
    val repo = AnnotationRepository(bookmarksFile, highlightsFile, tombstones)
    repo.addBookmark("book-1", "Ch 1", 0.1f, "{}")
    repo.addHighlight("book-1", "Book 1", "Line", "", 0.2f, "{}")
    val initialBookmarkId = repo.bookmarks.value.single().id

    assertTrue(bookmarksFile.delete())
    bookmarksFile.mkdirs()
    File(bookmarksFile, "blocker").writeText("x")

    repo.removeBookmark(initialBookmarkId)

    assertTrue(repo.bookmarks.value.isEmpty())
    assertTrue(repo.lastWriteError.value)

    // Subsequent highlight write succeeds, yet bookmark write error must stay active while dirty.
    repo.addHighlight("book-1", "Book 1", "Line 2", "", 0.3f, "{}")
    assertEquals(2, repo.highlights.value.size)
    assertTrue(repo.lastWriteError.value)

    bookmarksFile.deleteRecursively()
    // No-op transform still flushes bookmarks because bookmarksDirty == true.
    repo.removeAllForBook("non-existent-book")
    assertFalse(repo.lastWriteError.value)

    assertTrue(highlightsFile.delete())
    highlightsFile.mkdirs()
    File(highlightsFile, "blocker").writeText("x")
    val highlightToRemove = repo.highlights.value.first().id
    repo.removeHighlight(highlightToRemove)
    assertEquals(1, repo.highlights.value.size)
    assertTrue(repo.lastWriteError.value)

    highlightsFile.deleteRecursively()
    repo.removeAllForBook("non-existent-book")
    assertFalse(repo.lastWriteError.value)

    val reloaded = AnnotationRepository(bookmarksFile, highlightsFile, tombstones)
    assertTrue(reloaded.bookmarks.value.isEmpty())
    assertEquals(1, reloaded.highlights.value.size)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun asyncPreloadStartsEmptyAndLoadsBeforeCommitOrDropDeleted() = runTest {
    val seed = AnnotationRepository(bookmarksFile, highlightsFile, null)
    seed.addBookmark("book-1", "Existing BM", 0.3f, "{}")
    seed.addHighlight("book-1", "Book", "Quote", "Note", 0.4f, "{}")
    val existingBmId = seed.bookmarks.value.single().id

    val ioDispatcher = StandardTestDispatcher(testScheduler)
    val repo =
      AnnotationRepository(
        bookmarksFile = bookmarksFile,
        highlightsFile = highlightsFile,
        tombstones = null,
        preloadInBackground = true,
        ioDispatcher = ioDispatcher,
      )

    assertFalse(repo.loaded.value)
    assertTrue(repo.bookmarks.value.isEmpty())
    assertTrue(repo.highlights.value.isEmpty())

    // Mutate before preload runs: must load existing annotations first so existing highlight isn't lost
    repo.addBookmark("book-1", "New BM", 0.5f, "{}")
    assertTrue(repo.loaded.value)
    assertEquals(2, repo.bookmarks.value.size)
    assertEquals(1, repo.highlights.value.size)

    repo.dropDeleted(setOf(existingBmId))
    assertEquals(1, repo.bookmarks.value.size)
    assertEquals("New BM", repo.bookmarks.value.single().title)
  }
}
