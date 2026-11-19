package com.example.emptyapp.data.sync

import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.data.stats.DailyReadingStat
import com.example.emptyapp.data.stats.ReadingStatsData
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRemotePayloadMergerTest {
  private val now = 1_700_000_000_000L
  private val today = LocalDate.of(2026, 11, 19)

  @Test
  fun resolveSyncFilesPicksOldestCreatedTimeWithIdTieBreak() {
    val entries =
      listOf(
        DriveFileEntry("id-other", "book_abc.epub", createdTime = "2026-11-19T00:00:00Z"),
        DriveFileEntry("id-newer", "eink_sync_v1.json", createdTime = "2026-11-19T02:00:00Z", version = "3"),
        DriveFileEntry("id-tie-b", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00.000Z", version = "2"),
        DriveFileEntry("id-tie-a", "eink_sync_v1.json", createdTime = "2026-11-19T03:00:00+02:00", version = "1"),
        DriveFileEntry("id-missing-time", "eink_sync_v1.json", createdTime = null, version = "4"),
      )

    val resolved = SyncRemotePayloadMerger.resolveSyncFiles(entries, "eink_sync_v1.json")

    assertEquals("id-tie-a", resolved.primaryEntry?.id)
    assertEquals(listOf("id-tie-b", "id-newer", "id-missing-time"), resolved.duplicateEntries.map { it.id })
  }

  @Test
  fun resolveSyncFilesReturnsNullPrimaryWhenMissing() {
    val resolved =
      SyncRemotePayloadMerger.resolveSyncFiles(
        listOf(DriveFileEntry("b1", "book_123.epub")),
        "eink_sync_v1.json",
      )

    assertNull(resolved.primaryEntry)
    assertTrue(resolved.duplicateEntries.isEmpty())
  }

  @Test
  fun mergePayloadsAppliesLwwAcrossBooksAnnotationsStatsAndTombstones() {
    val file1 =
      SyncPayload(
        updatedAt = 500L,
        books =
          listOf(
            SyncedBookProgress("h-read", "Old Progress", progress = 0.2f, lastLocatorJson = "loc-1", updatedAt = 100L),
            SyncedBookProgress("h-deleted", "Deleted Book", progress = 0.9f, lastLocatorJson = "loc-d", updatedAt = 900L),
            SyncedBookProgress("h-imported", "Read Book", progress = 0.6f, lastLocatorJson = "loc-r", updatedAt = 150L),
          ),
        bookmarks =
          listOf(
            SyncedBookmark("bm-1", "h-read", "Old BM", 0.1f, "{}", createdAt = 100L),
            SyncedBookmark("bm-del", "h-read", "Gone BM", 0.2f, "{}", createdAt = 400L),
          ),
        highlights =
          listOf(
            SyncedHighlight("hl-1", "h-read", "Book", "Quote", note = "", progress = 0.3f, locatorJson = "{}", createdAt = 200L),
          ),
        stats =
          ReadingStatsData(
            dailyGoalMinutes = 30,
            dailyGoalUpdatedAt = 1_000L,
            longestStreak = 1,
            totalSeconds = 600L,
            totalPagesTurned = 10,
            dailyStats = listOf(DailyReadingStat("2026-11-18", secondsRead = 600, pagesTurned = 10)),
          ),
        bookTombstones = listOf(SyncTombstone("h-revived", changedAt = now - 900L, deleted = true)),
        annotationTombstones = listOf(SyncTombstone("bm-del", changedAt = now - 500L, deleted = true)),
      )

    val file2 =
      SyncPayload(
        updatedAt = 800L,
        books =
          listOf(
            SyncedBookProgress("h-read", "New Progress", progress = 0.7f, lastLocatorJson = "loc-2", updatedAt = 300L),
            SyncedBookProgress("h-imported", "Read Book", progress = 0f, lastLocatorJson = null, updatedAt = 500L),
            SyncedBookProgress("h-revived", "Revived", format = BookFormat.PDF, progress = 0.4f, lastLocatorJson = "loc-v", updatedAt = 250L),
          ),
        bookmarks =
          listOf(
            SyncedBookmark("bm-1", "h-read", "New BM", 0.5f, "{}", createdAt = 250L),
          ),
        highlights =
          listOf(
            SyncedHighlight("hl-1", "h-read", "Book", "Quote", note = "Note from B", progress = 0.3f, locatorJson = "{}", createdAt = 200L),
          ),
        stats =
          ReadingStatsData(
            dailyGoalMinutes = 15,
            dailyGoalUpdatedAt = 2_000L,
            longestStreak = 1,
            totalSeconds = 1_800L,
            totalPagesTurned = 30,
            dailyStats =
              listOf(
                DailyReadingStat("2026-11-18", secondsRead = 900, pagesTurned = 12),
                DailyReadingStat("2026-11-19", secondsRead = 900, pagesTurned = 18),
              ),
          ),
        bookTombstones =
          listOf(
            SyncTombstone("h-deleted", changedAt = now - 400L, deleted = true),
            SyncTombstone("h-revived", changedAt = now - 800L, deleted = false),
          ),
      )

    val mergedForward = SyncRemotePayloadMerger.mergePayloads(listOf(file1, file2), now, today)
    val mergedReverse = SyncRemotePayloadMerger.mergePayloads(listOf(file2, file1), now, today)

    assertEquals(mergedForward, mergedReverse)
    assertEquals(listOf("h-imported", "h-read", "h-revived"), mergedForward.books.map { it.contentHash })
    assertEquals("loc-2", mergedForward.books.first { it.contentHash == "h-read" }.lastLocatorJson)
    assertEquals("loc-r", mergedForward.books.first { it.contentHash == "h-imported" }.lastLocatorJson)
    assertEquals(listOf("bm-1"), mergedForward.bookmarks.map { it.id })
    assertEquals("New BM", mergedForward.bookmarks.single().title)
    assertEquals("Note from B", mergedForward.highlights.single().note)
    assertEquals(15, mergedForward.stats.dailyGoalMinutes)
    assertEquals(2_000L, mergedForward.stats.dailyGoalUpdatedAt)
    assertEquals(1_800L, mergedForward.stats.totalSeconds)
    assertEquals(30, mergedForward.stats.totalPagesTurned)
    assertEquals(2, mergedForward.stats.currentStreak)
    assertEquals(2, mergedForward.stats.longestStreak)
    assertEquals(listOf("2026-11-18", "2026-11-19"), mergedForward.stats.dailyStats.map { it.date })
  }

  @Test
  fun parserReadsDriveMetadataFieldsAndHandlesNullsOrInvalidJson() {
    val listJson =
      """
      {
        "nextPageToken": "page-2",
        "files": [
          {"id": "f1", "name": "eink_sync_v1.json", "createdTime": "2026-11-19T01:00:00Z", "version": "7", "md5Checksum": "abc"},
          {"id": "f2", "name": "book.epub", "createdTime": null, "version": 12},
          {"id": "", "name": "skip-me"}
        ]
      }
      """.trimIndent()

    val page = DriveSyncMetadataParser.parseFileListPage(listJson)!!
    assertEquals("page-2", page.nextPageToken)
    assertEquals(2, page.entries.size)
    assertEquals("7", page.entries[0].version)
    assertEquals("abc", page.entries[0].md5Checksum)
    assertNull(page.entries[1].createdTime)
    assertEquals("12", page.entries[1].version)

    val meta = DriveSyncMetadataParser.parseFileEntry("""{"id":"f1","version":"8","md5Checksum":"def"}""")!!
    assertEquals("f1", meta.id)
    assertEquals("8", meta.version)
    assertEquals("def", meta.md5Checksum)
    assertNull(DriveSyncMetadataParser.parseFileListPage("{bad-json"))
    assertNull(DriveSyncMetadataParser.parseFileEntry("""{"name":"no-id"}"""))
  }

  @Test
  fun revisionMatchesHandlesVersionAndMd5Combinations() {
    assertTrue(DriveFileRevision("5", "m1").matches(DriveFileRevision("5", "m1")))
    assertTrue(DriveFileRevision("5", null).matches(DriveFileRevision("5", "m1")))
    assertFalse(DriveFileRevision("5", "m1").matches(DriveFileRevision("6", "m1")))
    assertFalse(DriveFileRevision("5", "m1").matches(DriveFileRevision("5", "m2")))
    assertFalse(DriveFileRevision(null, "m1").matches(DriveFileRevision(null, "m2")))
    assertTrue(DriveFileRevision(null, null).matches(DriveFileRevision(null, null)))
  }
}
