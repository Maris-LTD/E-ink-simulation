package com.example.emptyapp

import com.example.emptyapp.data.stats.DailyReadingStat
import com.example.emptyapp.data.stats.ReadingGamification
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.data.stats.ReadingStatsRepository
import java.io.File
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatsTest {
  @Test
  fun recordReadingUpdatesTotalsAndStreak(): Unit = runBlocking {
    val tempFile = File.createTempFile("reading_stats_test", ".json")
    tempFile.delete()
    val today = LocalDate.of(2026, 11, 14)
    val repo = ReadingStatsRepository(tempFile, todayProvider = { today })

    repo.setDailyGoalMinutes(10)
    repo.recordReading(deltaSeconds = 600, deltaPages = 15)

    val data = repo.stats.value
    assertEquals(600L, data.totalSeconds)
    assertEquals(15, data.totalPagesTurned)
    assertEquals(1, data.currentStreak)
    assertEquals(1, data.longestStreak)
    tempFile.delete()
  }

  @Test
  fun computeStreaksPreservesYesterdayUntilTodayGoalMet() {
    val today = LocalDate.of(2026, 11, 14)
    val stats =
      listOf(
        DailyReadingStat("2026-11-12", secondsRead = 1200),
        DailyReadingStat("2026-11-13", secondsRead = 1500),
        DailyReadingStat("2026-11-14", secondsRead = 300),
      )
    val (current, best) = ReadingStatsRepository.computeStreaks(stats, goalMinutes = 20, today = today)
    assertEquals(2, current)
    assertEquals(2, best)
  }

  @Test
  fun computeStreaksResetsWhenDayMissed() {
    val today = LocalDate.of(2026, 11, 14)
    val stats =
      listOf(
        DailyReadingStat("2026-11-10", secondsRead = 1200),
        DailyReadingStat("2026-11-11", secondsRead = 1200),
        DailyReadingStat("2026-11-12", secondsRead = 1200),
      )
    val (current, best) = ReadingStatsRepository.computeStreaks(stats, goalMinutes = 20, today = today)
    assertEquals(0, current)
    assertEquals(3, best)
  }

  @Test
  fun recentDaysSeriesFillsMissingDatesInOrder() {
    val today = LocalDate.of(2026, 11, 14)
    val series =
      ReadingStatsRepository.recentDaysSeries(
        dailyStats = listOf(DailyReadingStat("2026-11-12", secondsRead = 300, pagesTurned = 4)),
        days = 7,
        today = today,
      )
    assertEquals(7, series.size)
    assertEquals("2026-11-08", series.first().date)
    assertEquals("2026-11-14", series.last().date)
    assertEquals(300, series[4].secondsRead)
    assertEquals(0, series[0].secondsRead)
  }

  @Test
  fun gamificationComputesXpRankAndBadges() {
    val stats = ReadingStatsData(totalSeconds = 3600L, totalPagesTurned = 120, longestStreak = 7)
    val rank = ReadingGamification.rankFor(stats, completedBooksCount = 1, highlightsCount = 5)
    // 60m*10 + 120*2 + 1*100 + 5*25 = 600 + 240 + 100 + 125 = 1065 XP -> Cấp 3 (Mọt sách)
    assertEquals(1065, rank.xp)
    assertEquals(3, rank.level)
    assertEquals("Mọt sách", rank.title)

    val badges = ReadingGamification.badgesFor(stats, completedBooksCount = 1, highlightsCount = 5)
    val unlockedIds = badges.filter { it.unlocked }.map { it.id }.toSet()
    assertEquals(
      setOf("first_page", "streak_3", "streak_7", "pages_100", "book_done_1", "annotator"),
      unlockedIds,
    )
  }

  @Test
  fun mergeFromRemoteUsesNewerDailyGoalTimestampEvenWhenLower(): Unit = runBlocking {
    val tempFile = File.createTempFile("reading_stats_merge", ".json")
    tempFile.delete()
    var now = 2000L
    val today = LocalDate.of(2026, 11, 14)
    val repo = ReadingStatsRepository(tempFile, todayProvider = { today }, nowProvider = { now })

    repo.setDailyGoalMinutes(15)
    repo.mergeFromRemote(ReadingStatsData(dailyGoalMinutes = 45, dailyGoalUpdatedAt = 1000L))
    assertEquals(15, repo.stats.value.dailyGoalMinutes)
    assertEquals(2000L, repo.stats.value.dailyGoalUpdatedAt)

    repo.mergeFromRemote(ReadingStatsData(dailyGoalMinutes = 10, dailyGoalUpdatedAt = 3000L))
    assertEquals(10, repo.stats.value.dailyGoalMinutes)
    assertEquals(3000L, repo.stats.value.dailyGoalUpdatedAt)
    tempFile.delete()
  }

  @Test
  fun mergeFromRemoteLegacyZeroTimestampsFallBackToMax(): Unit = runBlocking {
    val tempFile = File.createTempFile("reading_stats_legacy", ".json")
    tempFile.delete()
    val today = LocalDate.of(2026, 11, 14)
    val repo = ReadingStatsRepository(tempFile, todayProvider = { today }, nowProvider = { 0L })

    repo.mergeFromRemote(ReadingStatsData(dailyGoalMinutes = 35, dailyGoalUpdatedAt = 0L))
    assertEquals(35, repo.stats.value.dailyGoalMinutes)
    tempFile.delete()
  }

  @Test
  fun writeIoExceptionDoesNotCrashKeepsDirtyAndFlushesOnZeroDelta(): Unit = runBlocking {
    val dir = createTempDirectory("stats_write_error_test").toFile()
    try {
      val statsFile = File(dir, "reading_stats.json")
      val today = LocalDate.of(2026, 11, 14)
      val repo = ReadingStatsRepository(statsFile, todayProvider = { today })

      statsFile.mkdirs()
      File(statsFile, "blocker").writeText("x")

      repo.recordReading(deltaSeconds = 120, deltaPages = 5)
      assertEquals(120L, repo.stats.value.totalSeconds)
      assertEquals(5, repo.stats.value.totalPagesTurned)
      assertTrue(repo.lastWriteError.value)

      statsFile.deleteRecursively()
      repo.recordReading(deltaSeconds = 0, deltaPages = 0)
      assertFalse(repo.lastWriteError.value)

      val reloaded = ReadingStatsRepository(statsFile, todayProvider = { today })
      assertEquals(120L, reloaded.stats.value.totalSeconds)
      assertEquals(5, reloaded.stats.value.totalPagesTurned)
    } finally {
      dir.deleteRecursively()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun asyncPreloadLoadsDiskStatsBeforeRecordReading() = runTest {
    val dir = createTempDirectory("stats_async_preload_test").toFile()
    try {
      val statsFile = File(dir, "reading_stats.json")
      val today = LocalDate.of(2026, 11, 14)
      val seed = ReadingStatsRepository(statsFile, todayProvider = { today })
      seed.recordReading(deltaSeconds = 300, deltaPages = 10)

      val ioDispatcher = StandardTestDispatcher(testScheduler)
      val repo =
        ReadingStatsRepository(
          statsFile = statsFile,
          todayProvider = { today },
          nowProvider = { 1_000L },
          preloadInBackground = true,
          ioDispatcher = ioDispatcher,
        )

      assertFalse(repo.loaded.value)
      assertEquals(0L, repo.stats.value.totalSeconds)

      repo.recordReading(deltaSeconds = 120, deltaPages = 3)
      assertTrue(repo.loaded.value)
      assertEquals(420L, repo.stats.value.totalSeconds)
      assertEquals(13, repo.stats.value.totalPagesTurned)
    } finally {
      dir.deleteRecursively()
    }
  }
}
