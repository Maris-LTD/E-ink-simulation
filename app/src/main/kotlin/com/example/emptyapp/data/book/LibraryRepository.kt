package com.example.emptyapp.data.book

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.example.emptyapp.reader.ReadiumEngine
import com.example.emptyapp.reader.pdf.PdfReadingMode
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

class LibraryRepository internal constructor(
  private val appFilesDir: File,
  private val engineProvider: () -> ReadiumEngine,
  private val importFromUri: suspend (Uri, Set<String>) -> Result<BookEntry>,
  private val verifyAndExtractSyncedCover: suspend (File, BookFormat, File) -> Boolean,
  private val extractBackfillCover: suspend (BookEntry, File, File) -> Boolean,
  private val readAttemptedCoverIds: () -> Set<String>,
  private val persistAttemptedCoverIds: (Set<String>) -> Unit,
  autoBackfill: Boolean = true,
  preloadInBackground: Boolean = false,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val importFromFile: suspend (
    sourceFile: File,
    format: BookFormat,
    displayName: String,
    existingHashes: Set<String>,
    overrideTitle: String?,
    overrideAuthor: String?,
    updatedAt: Long,
  ) -> Result<BookEntry> = { _, _, _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
) {
  internal constructor(
    appFilesDir: File,
    engineLazy: Lazy<ReadiumEngine>,
    importFromUri: suspend (Uri, Set<String>) -> Result<BookEntry> = { _, _ ->
      Result.failure(UnsupportedOperationException())
    },
    verifyAndExtractSyncedCover: suspend (File, BookFormat, File) -> Boolean = { _, _, _ -> false },
    extractBackfillCover: suspend (BookEntry, File, File) -> Boolean = { _, _, _ -> false },
    readAttemptedCoverIds: () -> Set<String> = { emptySet() },
    persistAttemptedCoverIds: (Set<String>) -> Unit = {},
    autoBackfill: Boolean = true,
    preloadInBackground: Boolean = false,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    importFromFile: suspend (
      sourceFile: File,
      format: BookFormat,
      displayName: String,
      existingHashes: Set<String>,
      overrideTitle: String?,
      overrideAuthor: String?,
      updatedAt: Long,
    ) -> Result<BookEntry> = { _, _, _, _, _, _, _ -> Result.failure(UnsupportedOperationException()) },
  ) : this(
    appFilesDir = appFilesDir,
    engineProvider = { engineLazy.value },
    importFromUri = importFromUri,
    verifyAndExtractSyncedCover = verifyAndExtractSyncedCover,
    extractBackfillCover = extractBackfillCover,
    readAttemptedCoverIds = readAttemptedCoverIds,
    persistAttemptedCoverIds = persistAttemptedCoverIds,
    autoBackfill = autoBackfill,
    preloadInBackground = preloadInBackground,
    ioDispatcher = ioDispatcher,
    importFromFile = importFromFile,
  )

  constructor(context: Context, engineLazy: Lazy<ReadiumEngine>) : this(
    appFilesDir = context.applicationContext.filesDir,
    engineProvider = { engineLazy.value },
    importFromUri = { uri, existingHashes ->
      val appCtx = context.applicationContext
      BookImporter(
        appCtx,
        engineLazy.value,
        File(appCtx.filesDir, "books"),
        File(appCtx.filesDir, "covers"),
      ).import(uri, existingHashes)
    },
    verifyAndExtractSyncedCover = { bookFile, format, coverFile ->
      val pub = if (format == BookFormat.PDF) null else engineLazy.value.open(bookFile).getOrThrow()
      try {
        try {
          CoverExtractor.extract(bookFile, format, pub, coverFile)
        } catch (e: CancellationException) {
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
    extractBackfillCover = { book, bookFile, coverFile ->
      var pub: Publication? = null
      try {
        pub = if (book.format == BookFormat.PDF) null else engineLazy.value.open(bookFile).getOrNull()
        CoverExtractor.extract(bookFile, book.format, pub, coverFile)
      } finally {
        try {
          pub?.close()
        } catch (_: Throwable) {
        }
      }
    },
    readAttemptedCoverIds = {
      context.applicationContext
        .getSharedPreferences("eink_cover_backfill", Context.MODE_PRIVATE)
        .getStringSet("attempted_ids", emptySet())
        .orEmpty()
        .toSet()
    },
    persistAttemptedCoverIds = { ids ->
      @SuppressLint("ApplySharedPref")
      val ignored =
        context.applicationContext
          .getSharedPreferences("eink_cover_backfill", Context.MODE_PRIVATE)
          .edit()
          .putStringSet("attempted_ids", ids.toSet())
          .commit()
    },
    autoBackfill = true,
    preloadInBackground = true,
    importFromFile = { sourceFile, format, displayName, existingHashes, overrideTitle, overrideAuthor, updatedAt ->
      val appCtx = context.applicationContext
      BookImporter(
        appCtx,
        engineLazy.value,
        File(appCtx.filesDir, "books"),
        File(appCtx.filesDir, "covers"),
      ).importLocalFile(
        sourceFile = sourceFile,
        format = format,
        displayName = displayName,
        existingHashes = existingHashes,
        overrideTitle = overrideTitle,
        overrideAuthor = overrideAuthor,
        updatedAt = updatedAt,
      )
    },
  )

  constructor(context: Context, engine: ReadiumEngine) : this(context, lazyOf(engine))

  val engine: ReadiumEngine
    get() = engineProvider()

  private val booksDir = File(appFilesDir, "books")
  private val coversDir = File(appFilesDir, "covers")
  private val store = BookIndexStore(File(appFilesDir, "library.json"))
  private val state = MutableStateFlow(if (preloadInBackground) emptyList() else store.read())
  private val mutableLoaded = MutableStateFlow(!preloadInBackground)
  @Volatile private var isLoaded = !preloadInBackground
  private val backgroundScope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private val writeMutex = Mutex()
  private val importMutex = Mutex()
  @Volatile private var dirty = false
  private val mutableLastWriteError = MutableStateFlow(false)

  val books: StateFlow<List<BookEntry>> = state.asStateFlow()
  val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()
  val lastWriteError: StateFlow<Boolean> = mutableLastWriteError.asStateFlow()

  init {
    if (preloadInBackground) {
      backgroundScope.launch {
        awaitLoaded()
        if (autoBackfill) {
          backfillMissingCovers()
        }
      }
    } else if (autoBackfill) {
      backgroundScope.launch { backfillMissingCovers() }
    }
  }

  suspend fun awaitLoaded() {
    if (isLoaded) return
    withContext(ioDispatcher) {
      writeMutex.withLock {
        ensureLoadedLocked()
      }
    }
  }

  private fun ensureLoadedLocked() {
    if (isLoaded) return
    state.value = store.read()
    isLoaded = true
    mutableLoaded.value = true
  }

  fun find(id: String): BookEntry? = state.value.firstOrNull { it.id == id }

  fun findByContentHash(hash: String): BookEntry? =
    state.value.firstOrNull { it.contentHash?.equals(hash, ignoreCase = true) == true }

  fun fileOf(book: BookEntry): File = File(booksDir, book.fileName)

  fun coverFileOf(book: BookEntry): File? = book.coverFileName?.let { File(coversDir, it) }

  suspend fun import(uri: Uri): Result<BookEntry> =
    importWithHashes { existingHashes -> importFromUri(uri, existingHashes) }

  suspend fun importOrFindExisting(uri: Uri): Result<BookEntry> =
    importOrFindExistingWith { existingHashes -> importFromUri(uri, existingHashes) }

  internal suspend fun importOrFindExistingWith(
    performImport: suspend (Set<String>) -> Result<BookEntry>,
  ): Result<BookEntry> =
    importMutex.withLock {
      awaitLoaded()
      ensureContentHashes()
      val existingHashes = state.value.mapNotNull { it.contentHash?.lowercase() }.toSet()
      val result = performImport(existingHashes)
      val err = result.exceptionOrNull()
      if (err is CancellationException) throw err
      if (err is DuplicateBookException) {
        val existing = findByContentHash(err.contentHash)
        if (existing != null) return@withLock Result.success(existing)
      }
      result.onSuccess { entry -> commit { it + entry } }
    }

  suspend fun importAssetFile(
    tempFile: File,
    format: BookFormat,
    displayName: String,
    overrideTitle: String? = null,
    overrideAuthor: String? = null,
    updatedAt: Long = System.currentTimeMillis(),
  ): Result<BookEntry> =
    importWithHashes { existingHashes ->
      importFromFile(tempFile, format, displayName, existingHashes, overrideTitle, overrideAuthor, updatedAt)
    }

  suspend fun markCompleted(id: String, completed: Boolean) {
    awaitLoaded()
    val now = System.currentTimeMillis()
    commit { list ->
      list.map { book ->
        if (book.id == id) {
          book.copy(
            progress = if (completed) 1f else 0f,
            lastLocatorJson = if (completed) book.lastLocatorJson else null,
            updatedAt = now,
          )
        } else {
          book
        }
      }
    }
  }

  internal suspend fun importWithHashes(
    performImport: suspend (Set<String>) -> Result<BookEntry>,
  ): Result<BookEntry> =
    importMutex.withLock {
      awaitLoaded()
      ensureContentHashes()
      val existingHashes = state.value.mapNotNull { it.contentHash?.lowercase() }.toSet()
      performImport(existingHashes).onSuccess { entry -> commit { it + entry } }
    }

  suspend fun remove(id: String) {
    awaitLoaded()
    val book = find(id) ?: return
    withContext(ioDispatcher) {
      val bookFile = fileOf(book)
      com.example.emptyapp.reader.pdf.PdfDocumentCache(File(appFilesDir, "pdf_cache")).delete(bookFile)
      bookFile.delete()
      coverFileOf(book)?.delete()
    }
    commit { list -> list.filterNot { it.id == id } }
  }

  internal suspend fun backfillMissingCovers() {
    awaitLoaded()
    val attempted = readAttemptedCoverIds().toHashSet()
    val missing = state.value.filter { it.coverFileName == null && it.id !in attempted }
    if (missing.isEmpty()) return
    for (book in missing) {
      attempted += book.id
      persistAttemptedCoverIds(attempted.toSet())
      val bookFile = fileOf(book)
      if (!bookFile.exists()) continue
      val coverName = "${book.id}.jpg"
      val coverFile = File(coversDir, coverName)
      try {
        val extracted = extractBackfillCover(book, bookFile, coverFile)
        if (extracted && coverFile.exists()) {
          commit { list ->
            list.map { if (it.id == book.id && it.coverFileName == null) it.copy(coverFileName = coverName) else it }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        coverFile.delete()
      }
    }
  }

  suspend fun saveProgress(id: String, locatorJson: String, progress: Float, syncToDisk: Boolean = true) {
    awaitLoaded()
    val clamped = progress.coerceIn(0f, 1f)
    val current = find(id)
    if (current != null &&
      current.lastLocatorJson == locatorJson &&
      abs(current.progress - clamped) < PROGRESS_EPSILON &&
      !dirty
    ) {
      return
    }
    val now = System.currentTimeMillis()
    commit(syncToDisk = syncToDisk) { list ->
      list.map {
        if (it.id == id) {
          if (it.lastLocatorJson == locatorJson && abs(it.progress - clamped) < PROGRESS_EPSILON) {
            it
          } else {
            it.copy(lastLocatorJson = locatorJson, progress = clamped, updatedAt = now)
          }
        } else {
          it
        }
      }
    }
  }

  suspend fun savePdfReadingMode(id: String, mode: PdfReadingMode) {
    awaitLoaded()
    val current = find(id)
    if (current != null && current.pdfReadingMode == mode && !dirty) {
      return
    }
    commit { list ->
      list.map {
        if (it.id == id && it.pdfReadingMode != mode) it.copy(pdfReadingMode = mode) else it
      }
    }
  }

  suspend fun ensureContentHashes() {
    awaitLoaded()
    val missing = state.value.filter { it.contentHash == null }
    if (missing.isEmpty()) {
      if (dirty) commit { it }
      return
    }
    val computed =
      withContext(ioDispatcher) {
        missing.mapNotNull { book ->
          BookImporter.sha256Hex(fileOf(book))?.let { hash -> book.id to hash }
        }.toMap()
      }
    if (computed.isEmpty()) {
      if (dirty) commit { it }
      return
    }
    commit { list ->
      list.map { book ->
        val hash = computed[book.id]
        if (book.contentHash == null && hash != null) book.copy(contentHash = hash) else book
      }
    }
  }

  suspend fun applySyncedProgress(
    progressByHash: Map<String, Pair<String?, Pair<Float, Long>>>,
    now: Long = System.currentTimeMillis(),
  ) {
    if (progressByHash.isEmpty()) {
      if (dirty) commit { it }
      return
    }
    commit { list ->
      list.map { book ->
        val remote = book.contentHash?.let { progressByHash[it] } ?: return@map book
        val (remoteLocator, progressAndTime) = remote
        val (remoteProgress, rawRemoteUpdatedAt) = progressAndTime
        val remoteUpdatedAt = rawRemoteUpdatedAt.coerceAtMost(now)
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
    importMutex.withLock {
      awaitLoaded()
      withContext(ioDispatcher) {
        val clampedUpdatedAt = updatedAt.coerceAtMost(System.currentTimeMillis())
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
            updatedAt = clampedUpdatedAt,
            isDuplicateHash = { hash ->
              state.value.any { it.contentHash?.equals(hash, ignoreCase = true) == true }
            },
            verifyAndExtractCover = { bookFile, coverFile ->
              verifyAndExtractSyncedCover(bookFile, format, coverFile)
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
    }

  internal suspend fun flushPendingWrite() {
    if (dirty) commit { it }
  }

  private suspend fun commit(
    syncToDisk: Boolean = true,
    transform: (List<BookEntry>) -> List<BookEntry>,
  ) {
    writeMutex.withLock {
      if (!isLoaded) {
        withContext(ioDispatcher) { ensureLoadedLocked() }
      }
      val previous = state.value
      val next = transform(previous)
      val changed = next != previous
      if (!changed && !dirty) return
      if (changed) {
        state.value = next
        dirty = true
      }
      try {
        withContext(ioDispatcher) { store.write(state.value, syncToDisk = syncToDisk) }
        dirty = false
        mutableLastWriteError.value = false
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        dirty = true
        mutableLastWriteError.value = true
      }
    }
  }

  private companion object {
    const val PROGRESS_EPSILON = 1e-6f
  }
}
