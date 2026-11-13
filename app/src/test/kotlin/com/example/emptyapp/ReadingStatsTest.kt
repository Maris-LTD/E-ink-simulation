package com.example.emptyapp

import com.example.emptyapp.data.stats.DailyReadingStat
import com.example.emptyapp.data.stats.ReadingGamification
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.data.stats.ReadingStatsRepository
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
