package com.example.emptyapp

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleHelperTest {
  @Test
  fun formatClockTimeRespects24HourAndLocaleForAmPm() {
    val afternoon = LocalDateTime.of(2026, 11, 19, 14, 5)
    assertEquals("14:05", LocaleHelper.formatClockTime(afternoon, is24Hour = true, locale = Locale.ENGLISH))
    assertEquals("14:05", LocaleHelper.formatClockTime(afternoon, is24Hour = true, locale = Locale.forLanguageTag("vi-VN")))

    assertEquals("2:05 PM", LocaleHelper.formatClockTime(afternoon, is24Hour = false, locale = Locale.ENGLISH))
    val viLocale = Locale.forLanguageTag("vi-VN")
    val expectedVi12h = afternoon.format(DateTimeFormatter.ofPattern("h:mm a", viLocale))
    assertEquals(expectedVi12h, LocaleHelper.formatClockTime(afternoon, is24Hour = false, locale = viLocale))
  }

  @Test
  fun nameComparatorSortsVietnameseAccentedLettersBeforeZ() {
    val viComparator = LocaleHelper.nameComparator(Locale.forLanguageTag("vi-VN"))
    val titles = listOf("Zorro", "Đắc Nhân Tâm", "Dế Mèn", "Âm Nhạc", "Ăn Mày", "An")
    val sorted = titles.sortedWith(viComparator)

    val indexDac = sorted.indexOf("Đắc Nhân Tâm")
    val indexDe = sorted.indexOf("Dế Mèn")
    val indexZorro = sorted.indexOf("Zorro")
    assertTrue("Expected Đắc Nhân Tâm before Zorro, got $sorted", indexDac < indexZorro)
    assertTrue("Expected Dế Mèn before Đắc Nhân Tâm, got $sorted", indexDe < indexDac)
    assertTrue("Expected An before Ăn Mày, got $sorted", sorted.indexOf("An") < sorted.indexOf("Ăn Mày"))
    assertTrue("Expected Ăn Mày before Âm Nhạc, got $sorted", sorted.indexOf("Ăn Mày") < sorted.indexOf("Âm Nhạc"))
  }
}
