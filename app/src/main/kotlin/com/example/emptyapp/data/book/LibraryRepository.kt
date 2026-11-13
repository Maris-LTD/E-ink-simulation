package com.example.emptyapp.data.book

import android.content.Context
import android.net.Uri
import com.example.emptyapp.reader.ReadiumEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class LibraryRepository(context: Context, val engine: ReadiumEngine) {
  private val booksDir = File(context.filesDir, "books")
  private val store = BookIndexStore(File(context.filesDir, "library.json"))
  private val importer = BookImporter(context.applicationContext, engine, booksDir)
  private val state = MutableStateFlow(store.read())

  val books: StateFlow<List<BookEntry>> = state.asStateFlow()

  fun find(id: String): BookEntry? = state.value.firstOrNull { it.id == id }

  fun fileOf(book: BookEntry): File = File(booksDir, book.fileName)

  suspend fun import(uri: Uri): Result<BookEntry> =
    importer.import(uri).onSuccess { entry -> commit { it + entry } }

  suspend fun remove(id: String) {
    val book = find(id) ?: return
    withContext(Dispatchers.IO) { fileOf(book).delete() }
    commit { list -> list.filterNot { it.id == id } }
  }

  suspend fun saveProgress(id: String, locatorJson: String, progress: Float) {
    commit { list ->
      list.map {
        if (it.id == id) it.copy(lastLocatorJson = locatorJson, progress = progress) else it
      }
    }
  }

  private suspend fun commit(transform: (List<BookEntry>) -> List<BookEntry>) {
    val updated = state.updateAndGet(transform)
    withContext(Dispatchers.IO) { store.write(updated) }
  }

  private fun <T> MutableStateFlow<T>.updateAndGet(transform: (T) -> T): T {
    update(transform)
    return value
  }
}
