package com.example.emptyapp.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.ui.eink.EInkText
import java.io.File

private val SUPPORTED_MIMES =
  arrayOf(
    "application/epub+zip",
    "application/pdf",
    "application/vnd.comicbook+zip",
    "application/x-cbz",
    "application/zip",
    "application/octet-stream",
  )

private enum class LibraryTab(val label: String) {
  ALL("Tất cả"),
  READING("Đang đọc"),
  UNREAD("Chưa đọc"),
  BOOKMARKS("Dấu trang"),
  HIGHLIGHTS("Highlights"),
}

private enum class LibraryViewMode(val label: String, val columns: Int) {
  LIST("Danh sách", 1),
  GRID_2("Lưới 2", 2),
  GRID_3("Lưới 3", 3),
  GRID_4("Lưới 4", 4);

  fun next(): LibraryViewMode = entries[(ordinal + 1) % entries.size]
}

@Composable
fun LibraryScreen(
  viewModel: LibraryViewModel,
  onOpenBook: (String, String?) -> Unit,
  onOpenStats: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val tutorialPrefs = remember { context.getSharedPreferences("eink_tutorial_prefs", android.content.Context.MODE_PRIVATE) }
  var tutorialOpen by remember { mutableStateOf(!tutorialPrefs.getBoolean("tutorial_seen", false)) }
  fun dismissTutorial() {
    tutorialOpen = false
    tutorialPrefs.edit().putBoolean("tutorial_seen", true).apply()
  }

  val books by viewModel.books.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  val importing by viewModel.importing.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
  var selectedAuthor by remember { mutableStateOf<String?>(null) }
  var query by remember { mutableStateOf("") }
  var viewMode by rememberSaveable { mutableStateOf(LibraryViewMode.LIST) }
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
            LibraryTab.BOOKMARKS, LibraryTab.HIGHLIGHTS -> false
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
    contentWindowInsets = WindowInsets(0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
      LibraryHeader(
        onOpenTutorial = { tutorialOpen = true },
        onOpenStats = onOpenStats,
      )
      EInkTabRow(selected = selectedTab, onSelect = { selectedTab = it })
      if (selectedTab != LibraryTab.HIGHLIGHTS && selectedTab != LibraryTab.BOOKMARKS && authors.isNotEmpty()) {
        AuthorFilterRow(
          totalCount = books.size,
          authors = authors,
          selectedAuthor = selectedAuthor,
          onSelect = { selectedAuthor = it },
        )
        HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
      }
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (selectedTab == LibraryTab.BOOKMARKS) {
          val bookTitlesById = remember(books) { books.associate { it.id to it.title } }
          val filteredBookmarks =
            remember(bookmarks, bookTitlesById, query) {
              if (query.isBlank()) bookmarks
              else {
                bookmarks.filter {
                  it.title.contains(query, ignoreCase = true) ||
                    bookTitlesById[it.bookId].orEmpty().contains(query, ignoreCase = true)
                }
              }
            }
          LibraryBookmarkList(
            bookmarks = filteredBookmarks,
            bookTitlesById = bookTitlesById,
            onOpenBookmark = { bookId, locatorJson -> onOpenBook(bookId, locatorJson) },
            onRemove = viewModel::removeBookmark,
          )
        } else if (selectedTab == LibraryTab.HIGHLIGHTS) {
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
        } else if (viewMode == LibraryViewMode.LIST) {
          BookList(
            books = filteredBooks,
            coverOf = viewModel::coverFileOf,
            onOpenBook = { id -> onOpenBook(id, null) },
            onRemove = viewModel::remove,
          )
        } else {
          BookGrid(
            books = filteredBooks,
            columns = viewMode.columns,
            coverOf = viewModel::coverFileOf,
            onOpenBook = { id -> onOpenBook(id, null) },
            onRemove = viewModel::remove,
          )
        }
      }
      LibraryBottomBar(
        viewMode = viewMode,
        showViewToggle = selectedTab != LibraryTab.HIGHLIGHTS && selectedTab != LibraryTab.BOOKMARKS,
        query = query,
        importing = importing,
        onCycleViewMode = { viewMode = viewMode.next() },
        onQueryChange = { query = it },
        onAddBook = { picker.launch(SUPPORTED_MIMES) },
      )
    }
    if (tutorialOpen) {
      TutorialOverlay(onDismiss = ::dismissTutorial)
    }
  }
}

@Composable
private fun LibraryHeader(
  onOpenTutorial: () -> Unit,
  onOpenStats: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    EInkText("eInk Reader", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onOpenTutorial)
            .semantics { contentDescription = "Hướng dẫn sử dụng" },
        contentAlignment = Alignment.Center,
      ) {
        EInkText("?", style = MaterialTheme.typography.titleMedium)
      }
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onOpenStats)
            .semantics { contentDescription = "Thống kê thói quen đọc" },
        contentAlignment = Alignment.Center,
      ) {
        ReaderAvatarIcon(modifier = Modifier.size(28.dp))
      }
    }
  }
}

@Composable
private fun LibraryBottomBar(
  viewMode: LibraryViewMode,
  showViewToggle: Boolean,
  query: String,
  importing: Boolean,
  onCycleViewMode: () -> Unit,
  onQueryChange: (String) -> Unit,
  onAddBook: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showViewToggle) {
        IconButton(
          onClick = onCycleViewMode,
          modifier =
            Modifier.size(40.dp).semantics { contentDescription = viewMode.label },
        ) {
          ViewModeIcon(viewMode = viewMode)
        }
      }
      GoogleStyleSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        modifier = Modifier.weight(1f),
      )
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = !importing, onClick = onAddBook)
            .semantics { contentDescription = "Thêm sách" },
        contentAlignment = Alignment.Center,
      ) {
        EInkText(
          text = if (importing) "…" else "+",
          style = MaterialTheme.typography.titleLarge,
        )
      }
    }
  }
}

@Composable
private fun GoogleStyleSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val ink = MaterialTheme.colorScheme.onBackground
  Row(
    modifier =
      modifier
        .height(40.dp)
        .clip(RoundedCornerShape(50))
        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Canvas(modifier = Modifier.size(16.dp)) {
      val radius = size.minDimension * 0.34f
      val center = Offset(size.width * 0.4f, size.height * 0.4f)
      drawCircle(color = ink, radius = radius, center = center, style = Stroke(width = 1.8.dp.toPx()))
      drawLine(
        color = ink,
        start = Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
        end = Offset(size.width * 0.92f, size.height * 0.92f),
        strokeWidth = 1.8.dp.toPx(),
      )
    }
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (query.isEmpty()) {
        EInkText(
          text = "Tìm kiếm...",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
          strokeWidthPx = 1.3f,
        )
      }
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
        cursorBrush = SolidColor(ink),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (query.isNotEmpty()) {
      Box(
        modifier =
          Modifier.size(24.dp)
            .clip(CircleShape)
            .clickable { onQueryChange("") }
            .semantics { contentDescription = "Xóa tìm kiếm" },
        contentAlignment = Alignment.Center,
      ) {
        EInkText("×", style = MaterialTheme.typography.titleMedium, strokeWidthPx = 1.5f)
      }
    }
  }
}

@Composable
private fun ViewModeIcon(viewMode: LibraryViewMode) {
  val ink = MaterialTheme.colorScheme.onBackground
  Canvas(modifier = Modifier.size(18.dp)) {
    if (viewMode == LibraryViewMode.LIST) {
      listOf(0.22f, 0.5f, 0.78f).forEach { frac ->
        val y = size.height * frac
        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
      }
    } else {
      val cols = viewMode.columns.coerceIn(2, 4)
      val stroke = Stroke(width = 1.5.dp.toPx())
      val gap = size.width * 0.12f
      val cell = (size.width - gap * (cols - 1)) / cols
      for (r in 0 until cols) {
        for (c in 0 until cols) {
          drawRect(
            color = ink,
            topLeft = Offset(c * (cell + gap), r * (cell + gap)),
            size = Size(cell, cell),
            style = stroke,
          )
        }
      }
    }
  }
}

@Composable
private fun EInkTabRow(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
  Column {
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
      LibraryTab.entries.forEach { tab ->
        val active = tab == selected
        Box(
          modifier =
            Modifier
              .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
              )
              .clickable { onSelect(tab) }
              .padding(horizontal = 14.dp, vertical = 12.dp),
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
private fun EmptyLibrary(hasAnyBooks: Boolean) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    EInkText(
      text =
        if (hasAnyBooks) "Không có sách phù hợp bộ lọc."
        else "Chưa có sách nào.\nNhấn + Thêm sách để nhập tệp EPUB, PDF hoặc CBZ.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.secondary,
      strokeWidthPx = 1.5f,
    )
  }
}

@Composable
private fun BookList(
  books: List<BookEntry>,
  coverOf: (BookEntry) -> File?,
  onOpenBook: (String) -> Unit,
  onRemove: (String) -> Unit,
) {
  LazyColumn {
    items(books, key = { it.id }) { book ->
      BookRow(
        book = book,
        coverFile = coverOf(book),
        onOpen = { onOpenBook(book.id) },
        onRemove = { onRemove(book.id) },
      )
      HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

@Composable
private fun BookGrid(
  books: List<BookEntry>,
  columns: Int,
  coverOf: (BookEntry) -> File?,
  onOpenBook: (String) -> Unit,
  onRemove: (String) -> Unit,
) {
  val spacing = if (columns >= 4) 10.dp else 14.dp
  LazyVerticalGrid(
    columns = GridCells.Fixed(columns),
    contentPadding = PaddingValues(if (columns >= 4) 12.dp else 16.dp),
    horizontalArrangement = Arrangement.spacedBy(spacing),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    gridItems(books, key = { it.id }) { book ->
      BookGridCard(
        book = book,
        columns = columns,
        coverFile = coverOf(book),
        onOpen = { onOpenBook(book.id) },
        onRemove = { onRemove(book.id) },
      )
    }
  }
}

@Composable
private fun BookGridCard(
  book: BookEntry,
  columns: Int,
  coverFile: File?,
  onOpen: () -> Unit,
  onRemove: () -> Unit,
) {
  val compact = columns >= 3
  Column(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    BookCover(
      book = book,
      coverFile = coverFile,
      compact = compact,
      modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
    )
    EInkText(
      text = book.title,
      style = if (columns >= 4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
      minLines = 2,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      strokeWidthPx = if (columns >= 4) 1.5f else 2.0f,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EInkText(
        text =
          if (columns >= 4) "${(book.progress * 100).toInt()}%"
          else "${book.format.label} · ${(book.progress * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        strokeWidthPx = 1.3f,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onRemove,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
      ) {
        EInkText(
          text = "Xóa",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
          strokeWidthPx = 1.3f,
        )
      }
    }
  }
}

@Composable
private fun BookRow(
  book: BookEntry,
  coverFile: File?,
  onOpen: () -> Unit,
  onRemove: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onOpen)
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BookCover(
      book = book,
      coverFile = coverFile,
      compact = true,
      modifier = Modifier.size(width = 48.dp, height = 68.dp),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      EInkText(
        text = book.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      val meta =
        listOfNotNull(
          book.format.label,
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
