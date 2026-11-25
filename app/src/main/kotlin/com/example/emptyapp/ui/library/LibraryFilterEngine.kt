package com.example.emptyapp.ui.library

import com.example.emptyapp.data.book.BookEntry

internal data class LibraryFilteredState(
  val tabBooks: List<BookEntry> = emptyList(),
  val filteredBooks: List<BookEntry> = emptyList(),
  val authors: List<String> = emptyList(),
)

internal fun filterAndSortLibraryBooks(
  books: List<BookEntry>,
  selectedTab: LibraryTab,
  selectedAuthor: String?,
  query: String,
  sortMode: LibrarySort,
  nameComparator: Comparator<String>,
): LibraryFilteredState {
  val tabBooks =
    books.filter { book ->
      when (selectedTab) {
        LibraryTab.ALL -> true
        LibraryTab.READING -> book.progress > 0f && book.progress < 0.99f
        LibraryTab.UNREAD -> book.progress <= 0f
        LibraryTab.COMPLETED -> book.progress >= 0.99f
        LibraryTab.BOOKMARKS, LibraryTab.HIGHLIGHTS -> false
      }
    }
  val matching =
    tabBooks.filter { book ->
      val authorMatch = selectedAuthor == null || book.author == selectedAuthor
      val queryMatch =
        query.isBlank() ||
          book.title.contains(query, ignoreCase = true) ||
          book.author.contains(query, ignoreCase = true)
      authorMatch && queryMatch
    }
  val filteredBooks =
    when (sortMode) {
      LibrarySort.RECENT -> matching.sortedByDescending { it.updatedAt }
      LibrarySort.TITLE -> matching.sortedWith { a, b -> nameComparator.compare(a.title, b.title) }
      LibrarySort.PROGRESS -> matching.sortedByDescending { it.progress }
    }
  val authors =
    tabBooks.map { it.author }.filter { it.isNotBlank() }.distinct().sortedWith(nameComparator)
  return LibraryFilteredState(
    tabBooks = tabBooks,
    filteredBooks = filteredBooks,
    authors = authors,
  )
}
