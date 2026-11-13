package com.example.emptyapp.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.ui.eink.EInkText

private const val EPUB_MIME = "application/epub+zip"

private enum class LibraryTab(val label: String) {
  ALL("Tất cả"),
  READING("Đang đọc"),
  UNREAD("Chưa đọc"),
  HIGHLIGHTS("Highlights"),
}

@Composable
fun LibraryScreen(
  viewModel: LibraryViewModel,
  onOpenBook: (String, String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val books by viewModel.books.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  val importing by viewModel.importing.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
  var selectedAuthor by remember { mutableStateOf<String?>(null) }
  var searchOpen by remember { mutableStateOf(false) }
  var query by remember { mutableStateOf("") }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let(viewModel::import)
    }
  LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

  val filteredBooks =
    remember(books, selectedTab, selectedAuthor, query) {
      books.filter { book ->
        val tabMatch =
          when (selectedTab) {
            LibraryTab.ALL -> true
            LibraryTab.READING -> book.progress > 0f && book.progress < 0.99f
            LibraryTab.UNREAD -> book.progress <= 0f
            LibraryTab.HIGHLIGHTS -> false
          }
        val authorMatch = selectedAuthor == null || book.author == selectedAuthor
        val queryMatch =
          query.isBlank() ||
            book.title.contains(query, ignoreCase = true) ||
            book.author.contains(query, ignoreCase = true)
        tabMatch && authorMatch && queryMatch
      }
    }
  val authors = remember(books) { books.map { it.author }.filter { it.isNotBlank() }.distinct() }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
      LibraryHeader(
        importing = importing,
        searchOpen = searchOpen,
        onToggleSearch = {
          searchOpen = !searchOpen
          if (!searchOpen) query = ""
        },
        onAddBook = { picker.launch(arrayOf(EPUB_MIME)) },
      )
      if (searchOpen) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Tìm tên sách hoặc tác giả...") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      EInkTabRow(selected = selectedTab, onSelect = { selectedTab = it })
      if (selectedTab != LibraryTab.HIGHLIGHTS && authors.isNotEmpty()) {
        AuthorFilterRow(
          totalCount = books.size,
          authors = authors,
          selectedAuthor = selectedAuthor,
          onSelect = { selectedAuthor = it },
        )
      }
      SectionHeader(title = selectedTab.label.uppercase())
      HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
      if (selectedTab == LibraryTab.HIGHLIGHTS) {
        val filteredHighlights =
          remember(highlights, query) {
            if (query.isBlank()) highlights
            else {
              highlights.filter {
                it.quote.contains(query, ignoreCase = true) ||
                  it.note.contains(query, ignoreCase = true) ||
                  it.bookTitle.contains(query, ignoreCase = true)
              }
            }
          }
        LibraryHighlightList(
          highlights = filteredHighlights,
          onOpenHighlight = { bookId, locatorJson -> onOpenBook(bookId, locatorJson) },
          onRemove = viewModel::removeHighlight,
        )
      } else if (filteredBooks.isEmpty()) {
        EmptyLibrary(hasAnyBooks = books.isNotEmpty())
      } else {
        BookList(filteredBooks, { id -> onOpenBook(id, null) }, viewModel::remove)
      }
    }
  }
}

@Composable
private fun LibraryHeader(
  importing: Boolean,
  searchOpen: Boolean,
  onToggleSearch: () -> Unit,
  onAddBook: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EInkText("eInk Reader", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onToggleSearch) {
        EInkText(if (searchOpen) "Đóng" else "Tìm", color = MaterialTheme.colorScheme.onBackground)
      }
      Box(
        modifier =
          Modifier.clip(RoundedCornerShape(50))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .clickable(enabled = !importing, onClick = onAddBook)
            .padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        EInkText(
          text = if (importing) "Đang nhập..." else "+ Thêm sách",
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun EInkTabRow(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
  Column {
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
    Row(modifier = Modifier.fillMaxWidth()) {
      LibraryTab.entries.forEach { tab ->
        val active = tab == selected
        Box(
          modifier =
            Modifier.weight(1f)
              .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
              )
              .clickable { onSelect(tab) }
              .padding(vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = tab.label,
            style = MaterialTheme.typography.labelLarge,
            color =
              if (active) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
  }
}

@Composable
private fun AuthorFilterRow(
  totalCount: Int,
  authors: List<String>,
  selectedAuthor: String?,
  onSelect: (String?) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilterPill(label = "All $totalCount", active = selectedAuthor == null, onClick = { onSelect(null) })
    authors.forEach { author ->
      FilterPill(
        label = author,
        active = selectedAuthor == author,
        onClick = { onSelect(if (selectedAuthor == author) null else author) },
      )
    }
  }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(50))
        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 6.dp)
  ) {
    EInkText(
      text = label,
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
      color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
    )
  }
}

@Composable
private fun SectionHeader(title: String) {
  EInkText(
    text = title,
    style =
      MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
      ),
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
  )
}

@Composable
private fun EmptyLibrary(hasAnyBooks: Boolean) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    EInkText(
      text =
        if (hasAnyBooks) "Không có sách phù hợp bộ lọc."
        else "Chưa có sách nào.\nNhấn + Thêm sách để nhập tệp EPUB.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.secondary,
      strokeWidthPx = 1.5f,
    )
  }
}

@Composable
private fun BookList(
  books: List<BookEntry>,
  onOpenBook: (String) -> Unit,
  onRemove: (String) -> Unit,
) {
  LazyColumn {
    items(books, key = { it.id }) { book ->
      BookRow(book, onOpen = { onOpenBook(book.id) }, onRemove = { onRemove(book.id) })
      HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

@Composable
private fun BookRow(book: BookEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      EInkText(
        text = book.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      val meta =
        listOfNotNull(
          book.author.takeIf { it.isNotBlank() },
          "${(book.progress * 100).toInt()}%",
        ).joinToString(" · ")
      EInkText(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        strokeWidthPx = 1.5f,
      )
    }
    TextButton(onClick = onRemove) {
      EInkText("Xóa", color = MaterialTheme.colorScheme.secondary, strokeWidthPx = 1.5f)
    }
  }
}
