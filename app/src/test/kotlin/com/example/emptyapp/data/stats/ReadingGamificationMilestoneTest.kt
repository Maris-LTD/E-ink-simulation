package com.example.emptyapp.data.stats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingGamificationMilestoneTest {
  @Test
  fun hiddenWhenPro() {
    assertFalse(
      ReadingGamification.shouldShowMilestoneNudge(
        isPro = true,
        dismissed = false,
        stats = ReadingStatsData(currentStreak = 5, longestStreak = 5),
        completedBooksCount = 2,
      ),
    )
  }

  @Test
  fun hiddenWhenDismissed() {
    assertFalse(
      ReadingGamification.shouldShowMilestoneNudge(
        isPro = false,
        dismissed = true,
        stats = ReadingStatsData(currentStreak = 5, longestStreak = 5),
        completedBooksCount = 2,
      ),
    )
  }

  @Test
  fun hiddenBeforeAnyMilestone() {
    assertFalse(
      ReadingGamification.shouldShowMilestoneNudge(
        isPro = false,
        dismissed = false,
        stats = ReadingStatsData(currentStreak = 2, longestStreak = 2),
        completedBooksCount = 0,
      ),
    )
  }

  @Test
  fun shownWhenAtLeastOneBookCompleted() {
    assertTrue(
      ReadingGamification.shouldShowMilestoneNudge(
        isPro = false,
        dismissed = false,
        stats = ReadingStatsData(currentStreak = 0, longestStreak = 0),
        completedBooksCount = 1,
      ),
    )
  }

  @Test
  fun shownWhenThreeDayStreakReached() {
    assertTrue(
      ReadingGamification.shouldShowMilestoneNudge(
        isPro = false,
        dismissed = false,
        stats = ReadingStatsData(currentStreak = 3, longestStreak = 3),
        completedBooksCount = 0,
      ),
    )
  }
}
