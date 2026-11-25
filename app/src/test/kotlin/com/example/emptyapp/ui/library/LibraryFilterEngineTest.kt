package com.example.emptyapp.ui.library

import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.BookFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterEngineTest {
  private val comparator = LocaleHelper.nameComparator(Locale.forLanguageTag("vi"))

  private fun sampleBook(
    id: String,
    title: String,
    author: String,
    progress: Float,
    updatedAt: Long,
  ) =
    BookEntry(
      id = id,
      title = title,
      author = author,
      format = BookFormat.EPUB,
      fileName = "$id.epub",
      progress = progress,
      updatedAt = updatedAt,
    )

  @Test
  fun filtersByTabAuthorQueryAndSortsByTitleUsingCollator() {
    val books =
      listOf(
        sampleBook("1", "Đắc Nhân Tâm", "Dale Carnegie", progress = 0.5f, updatedAt = 100L),
        sampleBook("2", "Ánh Sáng", "Nguyễn Nhật Ánh", progress = 0.2f, updatedAt = 200L),
        sampleBook("3", "Đã Đọc Xong", "Nguyễn Nhật Ánh", progress = 1.0f, updatedAt = 300L),
        sampleBook("4", "Chưa Đọc", "Author B", progress = 0f, updatedAt = 400L),
      )

    val reading =
      filterAndSortLibraryBooks(
        books = books,
        selectedTab = LibraryTab.READING,
        selectedAuthor = null,
        query = "",
        sortMode = LibrarySort.TITLE,
        nameComparator = comparator,
      )
    assertEquals(listOf("2", "1"), reading.filteredBooks.map { it.id })
    assertEquals(listOf("Dale Carnegie", "Nguyễn Nhật Ánh"), reading.authors)

    val byAuthorAndQuery =
      filterAndSortLibraryBooks(
        books = books,
        selectedTab = LibraryTab.ALL,
        selectedAuthor = "Nguyễn Nhật Ánh",
        query = "sáng",
        sortMode = LibrarySort.RECENT,
        nameComparator = comparator,
      )
    assertEquals(listOf("2"), byAuthorAndQuery.filteredBooks.map { it.id })
  }
}
