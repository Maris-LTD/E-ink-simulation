package com.example.emptyapp.system

import com.example.emptyapp.reader.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EInkWallpaperCacheKeysTest {
  @Test
  fun localeAndFormatterKeysInvalidateOnlyWhenInputsChange() {
    val localeA = WallpaperLocaleKey(AppLanguage.VI, "vi-VN")
    val localeB = WallpaperLocaleKey(AppLanguage.VI, "vi-VN")
    val localeChanged = WallpaperLocaleKey(AppLanguage.EN, "vi-VN")
    assertEquals(localeA, localeB)
    assertNotEquals(localeA, localeChanged)

    val fmt24 = WallpaperFormatterKey("vi-VN", is24Hour = true)
    val fmt12 = WallpaperFormatterKey("vi-VN", is24Hour = false)
    assertEquals(fmt24, WallpaperFormatterKey("vi-VN", is24Hour = true))
    assertNotEquals(fmt24, fmt12)
  }

  @Test
  fun rankExcerptAndCoverKeysDetectRelevantChanges() {
    val rankBase = WallpaperRankKey(totalSeconds = 600L, totalPagesTurned = 20, completedBooksCount = 1, highlightsCount = 3, languageCode = "vi")
    assertEquals(rankBase, rankBase.copy())
    assertNotEquals(rankBase, rankBase.copy(totalSeconds = 660L))
    assertNotEquals(rankBase, rankBase.copy(languageCode = "en"))

    val excerptBase = WallpaperExcerptLayoutKey(snippet = "Hello world", bodyWidth = 500, widthPx = 1080f, fgColor = 0xFF111111.toInt())
    assertEquals(excerptBase, excerptBase.copy())
    assertNotEquals(excerptBase, excerptBase.copy(bodyWidth = 480))
    assertNotEquals(excerptBase, excerptBase.copy(snippet = "Updated quote"))

    val coverBase = WallpaperCoverKey(bookId = "book-1", filePath = "/data/covers/book-1.jpg")
    assertEquals(coverBase, WallpaperCoverKey("book-1", "/data/covers/book-1.jpg"))
    assertNotEquals(coverBase, coverBase.copy(bookId = "book-2"))
  }
}
