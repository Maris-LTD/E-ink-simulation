package com.example.emptyapp.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.data.book.BookEntry

private const val EPUB_MIME = "application/epub+zip"

@Composable
fun LibraryScreen(
  viewModel: LibraryViewModel,
  onOpenBook: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val books by viewModel.books.collectAsStateWithLifecycle()
  val importing by viewModel.importing.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let(viewModel::import)
    }
  LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

  Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
    Column(modifier = Modifier.padding(padding).padding(horizontal = 20.dp).fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Thư viện", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = { picker.launch(arrayOf(EPUB_MIME)) }, enabled = !importing) {
          Text(if (importing) "Đang nhập..." else "Thêm sách")
        }
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outline)
      if (books.isEmpty()) EmptyLibrary() else BookList(books, onOpenBook, viewModel::remove)
    }
  }
}

@Composable
private fun EmptyLibrary() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      "Chưa có sách nào.\nNhấn Thêm sách để nhập tệp EPUB.",
      style = MaterialTheme.typography.bodyLarge,
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
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

@Composable
private fun BookRow(book: BookEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(book.title, style = MaterialTheme.typography.titleMedium)
      if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyMedium)
      Text("Đã đọc ${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = onRemove) { Text("Xóa") }
  }
}
