package com.example.emptyapp.ui.library

import com.example.emptyapp.data.stats.DailyReadingStat
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStatsAccessibilityTest {
  @Test
  fun buildSevenDayChartAccessibilityDescriptionReturnsHeaderOnlyWhenSeriesEmpty() {
    val result =
      buildSevenDayChartAccessibilityDescription(
        chartTitle = "7-Day Chart",
        chartSubtitle = "Avg 0m/day · 0 pages/hr",
        weekSeries = emptyList(),
        minUnit = "m",
        isEn = true,
      )

    assertEquals("7-Day Chart. Avg 0m/day · 0 pages/hr", result)
  }

  @Test
  fun buildSevenDayChartAccessibilityDescriptionFormatsEntriesForEnglishAndVietnamese() {
    // 2026-11-16 is Monday, 2026-11-22 is Sunday
    val series =
      listOf(
        DailyReadingStat("2026-11-16", secondsRead = 900),
        DailyReadingStat("2026-11-17", secondsRead = 45),
        DailyReadingStat("2026-11-22", secondsRead = 1800),
      )

    val enDesc =
      buildSevenDayChartAccessibilityDescription(
        chartTitle = "7-Day Chart",
        chartSubtitle = "Avg 15m/day · 30 pages/hr",
        weekSeries = series,
        minUnit = "m",
        isEn = true,
      )
    assertEquals(
      "7-Day Chart. Avg 15m/day · 30 pages/hr. Mon: 15m, Tue: 0m, Sun: 30m",
      enDesc,
    )

    val viDesc =
      buildSevenDayChartAccessibilityDescription(
        chartTitle = "Biểu đồ 7 ngày",
        chartSubtitle = "TB 15p/ngày · 30 trang/giờ",
        weekSeries = series,
        minUnit = "p",
        isEn = false,
      )
    assertEquals(
      "Biểu đồ 7 ngày. TB 15p/ngày · 30 trang/giờ. T2: 15p, T3: 0p, CN: 30p",
      viDesc,
    )
  }

  @Test
  fun buildHeatmapAccessibilityDescriptionCountsActiveDaysAndClampsGoalToMinimumOneMinute() {
    val days =
      listOf(
        DailyReadingStat("2026-11-01", secondsRead = 0),
        DailyReadingStat("2026-11-02", secondsRead = 30),
        DailyReadingStat("2026-11-03", secondsRead = 60),
        DailyReadingStat("2026-11-04", secondsRead = 600),
      )

    val clampedGoalDesc =
      buildHeatmapAccessibilityDescription(
        heatmapTitle = "12-Week History",
        activeDaysSummary = "3 active days",
        days = days,
        goalSeconds = 10,
      )
    assertEquals("12-Week History. 3 active days (3/4, 2 ≥ 100%)", clampedGoalDesc)

    val standardGoalDesc =
      buildHeatmapAccessibilityDescription(
        heatmapTitle = "Lịch sử 12 tuần",
        activeDaysSummary = "3 ngày đã đọc",
        days = days,
        goalSeconds = 600,
      )
    assertEquals("Lịch sử 12 tuần. 3 ngày đã đọc (3/4, 1 ≥ 100%)", standardGoalDesc)
  }
}
