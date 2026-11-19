package com.example.emptyapp.data.book

import android.content.Context
import android.net.Uri
import com.example.emptyapp.reader.ReadiumEngine
import com.example.emptyapp.reader.pdf.PdfReadingMode
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LibraryRepository(context: Context, val engine: ReadiumEngine) {
  private val appContext = context.applicationContext
  private val appFilesDir = appContext.filesDir
  private val booksDir = File(appFilesDir, "books")
  private val coversDir = File(appFilesDir, "covers")
  private val store = BookIndexStore(File(appFilesDir, "library.json"))
  private val importer = BookImporter(context.applicationContext, engine, booksDir, coversDir)
  private val state = MutableStateFlow(store.read())
  private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val writeMutex = Mutex()

  val books: StateFlow<List<BookEntry>> = state.asStateFlow()

  init {
    backgroundScope.launch { backfillMissingCovers() }
  }

  fun find(id: String): BookEntry? = state.value.firstOrNull { it.id == id }

  fun fileOf(book: BookEntry): File = File(booksDir, book.fileName)

  fun coverFileOf(book: BookEntry): File? = book.coverFileName?.let { File(coversDir, it) }

  suspend fun import(uri: Uri): Result<BookEntry> {
    ensureContentHashes()
    val existingHashes = state.value.mapNotNull { it.contentHash }.toSet()
    return importer.import(uri, existingHashes).onSuccess { entry -> commit { it + entry } }
  }

  suspend fun remove(id: String) {
    val book = find(id) ?: return
    withContext(Dispatchers.IO) {
      val bookFile = fileOf(book)
      com.example.emptyapp.reader.pdf.PdfDocumentCache(File(appFilesDir, "pdf_cache")).delete(bookFile)
      bookFile.delete()
      coverFileOf(book)?.delete()
    }
    commit { list -> list.filterNot { it.id == id } }
  }

  private suspend fun backfillMissingCovers() {
    val prefs = appContext.getSharedPreferences("eink_cover_backfill", Context.MODE_PRIVATE)
    val attempted = prefs.getStringSet("attempted_ids", emptySet()).orEmpty().toHashSet()
    val missing = state.value.filter { it.coverFileName == null && it.id !in attempted }
    if (missing.isEmpty()) return
    for (book in missing) {
      attempted += book.id
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
    prefs.edit().putStringSet("attempted_ids", attempted).apply()
  }

  suspend fun saveProgress(id: String, locatorJson: String, progress: Float) {
    val now = System.currentTimeMillis()
    commit { list ->
      list.map {
        if (it.id == id) it.copy(lastLocatorJson = locatorJson, progress = progress, updatedAt = now) else it
      }
    }
  }

  suspend fun savePdfReadingMode(id: String, mode: PdfReadingMode) {
    val now = System.currentTimeMillis()
    commit { list ->
      list.map {
        if (it.id == id) it.copy(pdfReadingMode = mode, updatedAt = now) else it
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
  ): Boolean =
    withContext(Dispatchers.IO) {
      val result =
        SyncedBookFileImporter.importSyncedBook(
          sourceFile = tempFile,
          booksDir = booksDir,
          coversDir = coversDir,
          title = title,
          author = author,
          format = format,
          expectedContentHash = contentHash,
          progress = progress,
          lastLocatorJson = lastLocatorJson,
          updatedAt = updatedAt,
          isDuplicateHash = { hash ->
            state.value.any { it.contentHash?.equals(hash, ignoreCase = true) == true }
          },
          verifyAndExtractCover = { bookFile, coverFile ->
            val pub =
              if (format == BookFormat.PDF) {
                null
              } else {
                engine.open(bookFile).getOrThrow()
              }
            try {
              try {
                CoverExtractor.extract(bookFile, format, pub, coverFile)
              } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
              } catch (_: Throwable) {
                coverFile.delete()
                false
              }
            } finally {
              try {
                pub?.close()
              } catch (_: Throwable) {
              }
            }
          },
          commitEntry = { entry ->
            var added = false
            commit { list ->
              val exists = list.any { it.contentHash?.equals(entry.contentHash, ignoreCase = true) == true }
              if (exists) {
                list
              } else {
                added = true
                list + entry
              }
            }
            added
          },
        )
      when (result) {
        is SyncedBookImportResult.Imported -> true
        SyncedBookImportResult.Duplicate -> false
        is SyncedBookImportResult.Failed -> throw result.error
      }
    }

  private suspend fun commit(transform: (List<BookEntry>) -> List<BookEntry>) {
    writeMutex.withLock {
      state.update(transform)
      val snapshot = state.value
      withContext(Dispatchers.IO) { store.write(snapshot) }
    }
  }
}
