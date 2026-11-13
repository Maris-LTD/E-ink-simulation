package com.example.emptyapp.data.book

import android.content.Context
import android.net.Uri
import com.example.emptyapp.reader.ReadiumEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryRepository(context: Context, val engine: ReadiumEngine) {
  private val booksDir = File(context.filesDir, "books")
  private val coversDir = File(context.filesDir, "covers")
  private val store = BookIndexStore(File(context.filesDir, "library.json"))
  private val importer = BookImporter(context.applicationContext, engine, booksDir, coversDir)
  private val state = MutableStateFlow(store.read())
  private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val books: StateFlow<List<BookEntry>> = state.asStateFlow()

  init {
    backgroundScope.launch { backfillMissingCovers() }
  }

  fun find(id: String): BookEntry? = state.value.firstOrNull { it.id == id }

  fun fileOf(book: BookEntry): File = File(booksDir, book.fileName)

  fun coverFileOf(book: BookEntry): File? = book.coverFileName?.let { File(coversDir, it) }

  suspend fun import(uri: Uri): Result<BookEntry> =
    importer.import(uri).onSuccess { entry -> commit { it + entry } }

  suspend fun remove(id: String) {
    val book = find(id) ?: return
    withContext(Dispatchers.IO) {
      fileOf(book).delete()
      coverFileOf(book)?.delete()
    }
    commit { list -> list.filterNot { it.id == id } }
  }

  private suspend fun backfillMissingCovers() {
    val missing = state.value.filter { it.coverFileName == null }
    for (book in missing) {
      val bookFile = fileOf(book)
      if (!bookFile.exists()) continue
      val coverName = "${book.id}.jpg"
      val coverFile = File(coversDir, coverName)
      val pub = if (book.format == BookFormat.PDF) null else engine.open(bookFile).getOrNull()
      val extracted = CoverExtractor.extract(bookFile, book.format, pub, coverFile)
      pub?.close()
      if (extracted) {
        commit { list ->
          list.map { if (it.id == book.id && it.coverFileName == null) it.copy(coverFileName = coverName) else it }
        }
      }
    }
  }

  suspend fun saveProgress(id: String, locatorJson: String, progress: Float) {
    val now = System.currentTimeMillis()
    commit { list ->
      list.map {
        if (it.id == id) it.copy(lastLocatorJson = locatorJson, progress = progress, updatedAt = now) else it
      }
    }
  }

  suspend fun ensureContentHashes() {
    val missing = state.value.filter { it.contentHash == null }
    if (missing.isEmpty()) return
    val computed =
      withContext(Dispatchers.IO) {
        missing.mapNotNull { book ->
          BookImporter.sha256Hex(fileOf(book))?.let { hash -> book.id to hash }
        }.toMap()
      }
    if (computed.isEmpty()) return
    commit { list ->
      list.map { book ->
        val hash = computed[book.id]
        if (book.contentHash == null && hash != null) book.copy(contentHash = hash) else book
      }
    }
  }

  suspend fun applySyncedProgress(
    progressByHash: Map<String, Pair<String?, Pair<Float, Long>>>,
  ) {
    if (progressByHash.isEmpty()) return
    commit { list ->
      list.map { book ->
        val remote = book.contentHash?.let { progressByHash[it] } ?: return@map book
        val (remoteLocator, progressAndTime) = remote
        val (remoteProgress, remoteUpdatedAt) = progressAndTime
        val shouldApply =
          (remoteUpdatedAt > book.updatedAt && remoteLocator != null) ||
            (book.updatedAt == 0L && remoteProgress > book.progress && remoteLocator != null)
        if (shouldApply) {
          book.copy(
            lastLocatorJson = remoteLocator,
            progress = remoteProgress,
            updatedAt = remoteUpdatedAt,
          )
        } else {
          book
        }
      }
    }
  }

  suspend fun importFromSyncedFile(
    tempFile: File,
    title: String,
    author: String,
    format: BookFormat,
    contentHash: String,
    progress: Float,
    lastLocatorJson: String?,
    updatedAt: Long,
  ): Boolean {
    if (state.value.any { it.contentHash == contentHash }) return false
    val id = UUID.randomUUID().toString()
    val fileName = "$id.${format.extension}"
    val target = File(booksDir, fileName)
    val coverName = "$id.jpg"
    val coverFile = File(coversDir, coverName)
    val hasCover =
      withContext(Dispatchers.IO) {
        booksDir.mkdirs()
        tempFile.inputStream().use { input ->
          target.outputStream().use { output -> input.copyTo(output) }
        }
        val pub = if (format == BookFormat.PDF) null else engine.open(target).getOrNull()
        val extracted = CoverExtractor.extract(target, format, pub, coverFile)
        pub?.close()
        extracted
      }
    val entry =
      BookEntry(
        id = id,
        title = title,
        author = author,
        fileName = fileName,
        format = format,
        coverFileName = if (hasCover) coverName else null,
        lastLocatorJson = lastLocatorJson,
        progress = progress,
        contentHash = contentHash,
        updatedAt = updatedAt,
      )
    commit { list ->
      if (list.any { it.contentHash == contentHash }) list else list + entry
    }
    return true
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
