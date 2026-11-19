package com.example.emptyapp.data.book

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.R
import com.example.emptyapp.reader.ReadiumEngine
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookImporter(
  private val context: Context,
  private val engine: ReadiumEngine,
  private val booksDir: File,
  private val coversDir: File,
) {
  suspend fun import(uri: Uri, existingHashes: Set<String> = emptySet()): Result<BookEntry> =
    withContext(Dispatchers.IO) {
      val localized = LocaleHelper.wrap(context)
      val displayName = resolveDisplayName(uri)
      val format = detectFormat(uri, displayName)
        ?: return@withContext Result.failure(IllegalArgumentException(localized.getString(R.string.import_unsupported_format)))
      val id = UUID.randomUUID().toString()
      val fileName = "$id.${format.extension}"
      val target = File(booksDir, fileName)
      booksDir.mkdirs()
      val copied = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
          target.outputStream().use { input.copyTo(it) }
        } ?: error(localized.getString(R.string.import_cannot_open_file))
      }
      if (copied.isFailure) {
        target.delete()
        return@withContext Result.failure(copied.exceptionOrNull() ?: IllegalStateException())
      }
      val contentHash = sha256Hex(target)
      if (contentHash != null && contentHash in existingHashes) {
        target.delete()
        return@withContext Result.failure(IllegalStateException(localized.getString(R.string.lib_import_duplicate)))
      }
      val publication = engine.open(target).getOrElse {
        target.delete()
        return@withContext Result.failure(it)
      }
      val coverName = "$id.jpg"
      val coverFile = File(coversDir, coverName)
      try {
        val hasCover = CoverExtractor.extract(target, format, publication, coverFile)
        val entry =
          BookEntry(
            id = id,
            title = publication.metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle(displayName, uri),
            author = publication.metadata.authors.joinToString { it.name },
            fileName = fileName,
            format = format,
            coverFileName = if (hasCover) coverName else null,
            contentHash = contentHash,
            updatedAt = System.currentTimeMillis(),
          )
        Result.success(entry)
      } catch (t: Throwable) {
        target.delete()
        coverFile.delete()
        Result.failure(t)
      } finally {
        publication.close()
      }
    }

  private fun resolveDisplayName(uri: Uri): String? =
    runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
      }
    }.getOrNull()

  private fun detectFormat(uri: Uri, displayName: String?): BookFormat? {
    val ext = (displayName ?: uri.lastPathSegment).orEmpty().substringAfterLast('.', "").lowercase()
    when (ext) {
      "epub" -> return BookFormat.EPUB
      "pdf" -> return BookFormat.PDF
      "cbz" -> return BookFormat.CBZ
    }
    val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
    return when {
      mime == "application/epub+zip" -> BookFormat.EPUB
      mime == "application/pdf" -> BookFormat.PDF
      mime == "application/vnd.comicbook+zip" || mime == "application/x-cbz" -> BookFormat.CBZ
      else -> null
    }
  }

  private fun fallbackTitle(displayName: String?, uri: Uri): String {
    val raw = displayName ?: uri.lastPathSegment?.substringAfterLast('/')
    return raw?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
      ?: LocaleHelper.wrap(context).getString(R.string.import_default_title)
  }

  companion object {
    fun sha256Hex(file: File): String? =
      runCatching {
        if (!file.exists()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        file.inputStream().use { input ->
          while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
          }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
      }.getOrNull()
  }
}
