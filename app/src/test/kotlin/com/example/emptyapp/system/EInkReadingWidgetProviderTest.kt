package com.example.emptyapp.system

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class EInkReadingWidgetProviderTest {
  @Test
  fun nextMidnightRefreshMillisSchedulesFiveSecondsAfterNextMidnight() {
    val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    val beforeMidnight = ZonedDateTime.of(2026, 11, 19, 23, 59, 59, 0, zone).toInstant().toEpochMilli()
    val expected = ZonedDateTime.of(2026, 11, 20, 0, 0, 5, 0, zone).toInstant().toEpochMilli()

    assertEquals(expected, EInkReadingWidgetProvider.nextMidnightRefreshMillis(beforeMidnight, zone))
  }

  @Test
  fun nextMidnightRefreshMillisJustAfterMidnightTargetsFollowingDay() {
    val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    val afterMidnight = ZonedDateTime.of(2026, 11, 20, 0, 0, 1, 0, zone).toInstant().toEpochMilli()
    val expected = ZonedDateTime.of(2026, 11, 21, 0, 0, 5, 0, zone).toInstant().toEpochMilli()

    assertEquals(expected, EInkReadingWidgetProvider.nextMidnightRefreshMillis(afterMidnight, zone))
  }

  @Test
  fun nextMidnightRefreshMillisRespectsTimezoneAndDstBoundary() {
    val nyZone = ZoneId.of("America/New_York")
    val tokyoZone = ZoneId.of("Asia/Tokyo")
    val instant = ZonedDateTime.of(2026, 3, 7, 22, 0, 0, 0, nyZone).toInstant().toEpochMilli()

    val expectedNy = ZonedDateTime.of(2026, 3, 8, 0, 0, 5, 0, nyZone).toInstant().toEpochMilli()
    assertEquals(expectedNy, EInkReadingWidgetProvider.nextMidnightRefreshMillis(instant, nyZone))

    val tokyoNow = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(instant), tokyoZone)
    val expectedTokyo = tokyoNow.toLocalDate().plusDays(1).atStartOfDay(tokyoZone).plusSeconds(5).toInstant().toEpochMilli()
    assertEquals(expectedTokyo, EInkReadingWidgetProvider.nextMidnightRefreshMillis(instant, tokyoZone))
  }
}
