package com.example.emptyapp.data.book

import android.content.Context
import android.net.Uri
import com.example.emptyapp.reader.ReadiumEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookImporter(
  private val context: Context,
  private val engine: ReadiumEngine,
  private val booksDir: File,
) {
  suspend fun import(uri: Uri): Result<BookEntry> =
    withContext(Dispatchers.IO) {
      val id = UUID.randomUUID().toString()
      val fileName = "$id.epub"
      val target = File(booksDir, fileName)
      booksDir.mkdirs()
      val copied = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
          target.outputStream().use { input.copyTo(it) }
        } ?: error("Không mở được tệp")
      }
      if (copied.isFailure) {
        target.delete()
        return@withContext Result.failure(copied.exceptionOrNull() ?: IllegalStateException())
      }
      val publication = engine.open(target).getOrElse {
        target.delete()
        return@withContext Result.failure(it)
      }
      val entry =
        BookEntry(
          id = id,
          title = publication.metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle(uri),
          author = publication.metadata.authors.joinToString { it.name },
          fileName = fileName,
        )
      publication.close()
      Result.success(entry)
    }

  private fun fallbackTitle(uri: Uri): String =
    uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Sách mới"
}
