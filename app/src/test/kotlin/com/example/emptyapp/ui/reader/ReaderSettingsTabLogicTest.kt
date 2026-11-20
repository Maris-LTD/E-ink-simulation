package com.example.emptyapp.ui.reader

import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.ui.library.resolveHeroBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSettingsTabLogicTest {
  @Test
  fun availableSettingsTabs_includesTextTabForReflowableAndPdfOnly() {
    assertEquals(
      listOf(ReaderSettingsTab.TEXT, ReaderSettingsTab.DISPLAY, ReaderSettingsTab.CONTROLS),
      availableSettingsTabs(isReflowable = true, isPdf = false),
    )
    assertEquals(
      listOf(ReaderSettingsTab.TEXT, ReaderSettingsTab.DISPLAY, ReaderSettingsTab.CONTROLS),
      availableSettingsTabs(isReflowable = true, isPdf = true),
    )
    assertEquals(
      listOf(ReaderSettingsTab.TEXT, ReaderSettingsTab.DISPLAY, ReaderSettingsTab.CONTROLS),
      availableSettingsTabs(isReflowable = false, isPdf = true),
    )
    assertEquals(
      listOf(ReaderSettingsTab.DISPLAY, ReaderSettingsTab.CONTROLS),
      availableSettingsTabs(isReflowable = false, isPdf = false),
    )
  }

  @Test
  fun resolveHeroBook_returnsMostRecentlyReadOnlyWhenUnfilteredOnPrimaryTab() {
    val older = BookEntry(
      id = "book-1",
      title = "Older",
      author = "Author A",
      fileName = "older.epub",
      progress = 0.25f,
      updatedAt = 100L,
    )
    val latest = BookEntry(
      id = "book-2",
      title = "Latest",
      author = "Author B",
      fileName = "latest.epub",
      progress = 0.6f,
      updatedAt = 250L,
    )
    val books = listOf(older, latest)

    assertEquals(
      latest,
      resolveHeroBook(
        books = books,
        isAllOrReadingTab = true,
        query = "",
        selectedAuthor = null,
      ),
    )
    assertNull(
      resolveHeroBook(
        books = books,
        isAllOrReadingTab = false,
        query = "",
        selectedAuthor = null,
      ),
    )
    assertNull(
      resolveHeroBook(
        books = books,
        isAllOrReadingTab = true,
        query = "Latest",
        selectedAuthor = null,
      ),
    )
    assertNull(
      resolveHeroBook(
        books = books,
        isAllOrReadingTab = true,
        query = "",
        selectedAuthor = "Author B",
      ),
    )
    assertNull(
      resolveHeroBook(
        books = emptyList(),
        isAllOrReadingTab = true,
        query = "",
        selectedAuthor = null,
      ),
    )
  }
}
