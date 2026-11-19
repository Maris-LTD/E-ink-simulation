package com.example.emptyapp.data.sync

import com.example.emptyapp.data.stats.DailyReadingStat
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.data.stats.ReadingStatsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

internal data class DriveFileRevision(
  val version: String? = null,
  val md5Checksum: String? = null,
) {
  fun matches(other: DriveFileRevision): Boolean =
    when {
      version != null && other.version != null ->
        version == other.version &&
          (md5Checksum == null || other.md5Checksum == null || md5Checksum == other.md5Checksum)
      md5Checksum != null && other.md5Checksum != null ->
        md5Checksum == other.md5Checksum
      else ->
        version == other.version && md5Checksum == other.md5Checksum
    }
}

internal data class DriveFileEntry(
  val id: String,
  val name: String,
  val createdTime: String? = null,
  val version: String? = null,
  val md5Checksum: String? = null,
) {
  val revision: DriveFileRevision
    get() = DriveFileRevision(version = version, md5Checksum = md5Checksum)
}

internal data class SyncFileResolution(
  val primaryEntry: DriveFileEntry?,
  val duplicateEntries: List<DriveFileEntry>,
) {
  val allEntries: List<DriveFileEntry>
    get() = if (primaryEntry == null) emptyList() else listOf(primaryEntry) + duplicateEntries
}

internal object SyncRemotePayloadMerger {
  private val entryComparator = Comparator<DriveFileEntry> { a, b ->
    val timeCmp = compareCreatedTime(a.createdTime, b.createdTime)
    if (timeCmp != 0) timeCmp else a.id.compareTo(b.id)
  }

  fun resolveSyncFiles(entries: List<DriveFileEntry>, syncFileName: String): SyncFileResolution {
    val matching = entries.filter { it.name == syncFileName }.sortedWith(entryComparator)
    if (matching.isEmpty()) return SyncFileResolution(primaryEntry = null, duplicateEntries = emptyList())
    return SyncFileResolution(primaryEntry = matching.first(), duplicateEntries = matching.drop(1))
  }

  fun mergePayloads(
    payloads: List<SyncPayload>,
    now: Long,
    today: LocalDate = LocalDate.now(),
  ): SyncPayload {
    if (payloads.isEmpty()) return SyncPayload()
    var bookTombstones = emptyList<SyncTombstone>()
    var annotationTombstones = emptyList<SyncTombstone>()
    val booksByHash = LinkedHashMap<String, SyncedBookProgress>()
    val bookmarksById = LinkedHashMap<String, SyncedBookmark>()
    val highlightsById = LinkedHashMap<String, SyncedHighlight>()
    var mergedStats = payloads.first().stats
    var maxVersion = payloads.first().version
    var maxUpdatedAt = payloads.first().updatedAt

    for ((index, payload) in payloads.withIndex()) {
      if (payload.version > maxVersion) maxVersion = payload.version
      if (payload.updatedAt > maxUpdatedAt) maxUpdatedAt = payload.updatedAt
      bookTombstones = SyncTombstoneMerger.merge(bookTombstones, payload.bookTombstones, now)
      annotationTombstones = SyncTombstoneMerger.merge(annotationTombstones, payload.annotationTombstones, now)

      for (rawBook in payload.books) {
        val book = rawBook.copy(updatedAt = rawBook.updatedAt.coerceAtMost(now))
        val current = booksByHash[book.contentHash]
        if (current == null || isNewerBook(book, current)) {
          booksByHash[book.contentHash] = book
        }
      }
      for (bookmark in payload.bookmarks) {
        val current = bookmarksById[bookmark.id]
        if (current == null || isNewerBookmark(bookmark, current)) {
          bookmarksById[bookmark.id] = bookmark
        }
      }
      for (highlight in payload.highlights) {
        val current = highlightsById[highlight.id]
        if (current == null || isNewerHighlight(highlight, current)) {
          highlightsById[highlight.id] = highlight
        }
      }
      if (index > 0) {
        mergedStats = mergeStats(mergedStats, payload.stats, today)
      }
    }

    val rawMerged =
      SyncPayload(
        version = maxVersion,
        updatedAt = maxUpdatedAt,
        books = booksByHash.values.toList(),
        bookmarks = bookmarksById.values.toList(),
        highlights = highlightsById.values.toList(),
        stats = mergedStats,
        bookTombstones = bookTombstones,
        annotationTombstones = annotationTombstones,
      )
    val deletedBooks = SyncTombstoneMerger.deletedKeys(bookTombstones)
    val deletedAnnotations = SyncTombstoneMerger.deletedKeys(annotationTombstones)
    return SyncTombstoneMerger.withoutDeleted(rawMerged, deletedBooks, deletedAnnotations).canonicalized()
  }

  private fun isNewerBook(candidate: SyncedBookProgress, current: SyncedBookProgress): Boolean {
    val candidateHasProgress = candidate.lastLocatorJson != null || candidate.progress > 0f
    val currentHasProgress = current.lastLocatorJson != null || current.progress > 0f
    if (candidateHasProgress != currentHasProgress) return candidateHasProgress
    if (candidate.updatedAt != current.updatedAt) return candidate.updatedAt > current.updatedAt
    val candidateHasLocator = candidate.lastLocatorJson != null
    val currentHasLocator = current.lastLocatorJson != null
    if (candidateHasLocator != currentHasLocator) return candidateHasLocator
    if (candidate.progress != current.progress) return candidate.progress > current.progress
    return compareValuesBy(
      candidate,
      current,
      { it.title },
      { it.author },
      { it.format.name },
      { it.lastLocatorJson.orEmpty() },
    ) > 0
  }

  private fun isNewerBookmark(candidate: SyncedBookmark, current: SyncedBookmark): Boolean {
    if (candidate.createdAt != current.createdAt) return candidate.createdAt > current.createdAt
    return compareValuesBy(
      candidate,
      current,
      { it.title },
      { it.locatorJson },
      { it.progress },
      { it.bookContentHash },
    ) > 0
  }

  private fun isNewerHighlight(candidate: SyncedHighlight, current: SyncedHighlight): Boolean {
    if (candidate.createdAt != current.createdAt) return candidate.createdAt > current.createdAt
    val candidateHasNote = candidate.note.isNotBlank()
    val currentHasNote = current.note.isNotBlank()
    if (candidateHasNote != currentHasNote) return candidateHasNote
    return compareValuesBy(
      candidate,
      current,
      { it.note },
      { it.quote },
      { it.bookTitle },
      { it.locatorJson },
      { it.progress },
      { it.bookContentHash },
    ) > 0
  }

  private fun mergeStats(
    first: ReadingStatsData,
    second: ReadingStatsData,
    today: LocalDate,
  ): ReadingStatsData {
    val byDate = LinkedHashMap<String, DailyReadingStat>()
    for (item in first.dailyStats) {
      val prev = byDate[item.date]
      byDate[item.date] = mergeDailyStat(prev, item)
    }
    for (item in second.dailyStats) {
      val prev = byDate[item.date]
      byDate[item.date] = mergeDailyStat(prev, item)
    }
    val mergedDaily = byDate.values.sortedBy { it.date }
    val totalSec = maxOf(first.totalSeconds, second.totalSeconds, mergedDaily.sumOf { it.secondsRead.toLong() })
    val totalPages = maxOf(first.totalPagesTurned, second.totalPagesTurned, mergedDaily.sumOf { it.pagesTurned })
    val (mergedGoal, mergedGoalUpdatedAt) =
      when {
        second.dailyGoalUpdatedAt > first.dailyGoalUpdatedAt ->
          second.dailyGoalMinutes.coerceIn(5, 180) to second.dailyGoalUpdatedAt
        first.dailyGoalUpdatedAt > second.dailyGoalUpdatedAt ->
          first.dailyGoalMinutes.coerceIn(5, 180) to first.dailyGoalUpdatedAt
        else ->
          maxOf(first.dailyGoalMinutes, second.dailyGoalMinutes).coerceIn(5, 180) to first.dailyGoalUpdatedAt
      }
    val (currentStreak, historicalBest) = ReadingStatsRepository.computeStreaks(mergedDaily, mergedGoal, today)
    return ReadingStatsData(
      dailyGoalMinutes = mergedGoal,
      dailyGoalUpdatedAt = mergedGoalUpdatedAt,
      currentStreak = currentStreak,
      longestStreak = maxOf(first.longestStreak, second.longestStreak, currentStreak, historicalBest),
      totalSeconds = totalSec,
      totalPagesTurned = totalPages,
      dailyStats = mergedDaily,
    )
  }

  private fun mergeDailyStat(prev: DailyReadingStat?, item: DailyReadingStat): DailyReadingStat =
    if (prev == null) item
    else DailyReadingStat(
      date = item.date,
      secondsRead = maxOf(prev.secondsRead, item.secondsRead),
      pagesTurned = maxOf(prev.pagesTurned, item.pagesTurned),
    )

  private fun compareCreatedTime(first: String?, second: String?): Int {
    val firstInstant = parseInstant(first)
    val secondInstant = parseInstant(second)
    if (firstInstant != null && secondInstant != null) return firstInstant.compareTo(secondInstant)
    if (firstInstant != null) return -1
    if (secondInstant != null) return 1
    val firstRaw = first?.trim()?.takeIf { it.isNotEmpty() }
    val secondRaw = second?.trim()?.takeIf { it.isNotEmpty() }
    if (firstRaw != null && secondRaw != null) return firstRaw.compareTo(secondRaw)
    if (firstRaw != null) return -1
    if (secondRaw != null) return 1
    return 0
  }

  private fun parseInstant(raw: String?): Instant? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Instant.parse(value) }.getOrNull()
      ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
  }
}
