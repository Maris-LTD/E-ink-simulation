package com.example.emptyapp.data.book

import com.example.emptyapp.reader.ReadiumEngine
import com.example.emptyapp.reader.pdf.PdfReadingMode
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryRepositoryTest {
  private lateinit var dir: File
  private lateinit var booksDir: File
  private lateinit var coversDir: File

  @Before
  fun setUp() {
    dir = createTempDirectory("library_repo_test").toFile()
    booksDir = File(dir, "books").apply { mkdirs() }
    coversDir = File(dir, "covers").apply { mkdirs() }
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  private fun createRepository(
    extractBackfillCover: suspend (BookEntry, File, File) -> Boolean = { _, _, _ -> false },
    verifyAndExtractSyncedCover: suspend (File, BookFormat, File) -> Boolean = { _, _, _ -> false },
    attemptedIds: MutableSet<String> = linkedSetOf(),
    onAttemptedPersisted: (Set<String>) -> Unit = {},
  ): LibraryRepository =
    LibraryRepository(
      appFilesDir = dir,
      engineProvider = { error("Not used in pure JVM test") },
      importFromUri = { _, _ -> Result.failure(UnsupportedOperationException()) },
      verifyAndExtractSyncedCover = verifyAndExtractSyncedCover,
      extractBackfillCover = extractBackfillCover,
      readAttemptedCoverIds = { attemptedIds.toSet() },
      persistAttemptedCoverIds = { ids ->
        attemptedIds.clear()
        attemptedIds.addAll(ids)
        onAttemptedPersisted(ids)
      },
      autoBackfill = false,
    )

  @Test
  fun concurrentImportsAreSerializedByImportMutexAndRejectDuplicates() = runTest {
    val repo = createRepository()
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val bookHash = "deadbeef01"

    val firstImport =
      async {
        repo.importWithHashes { existingHashes ->
          assertFalse(bookHash in existingHashes)
          firstStarted.complete(Unit)
          releaseFirst.await()
          Result.success(
            BookEntry(
              id = "book-1",
              title = "Sample",
              author = "Author",
              fileName = "book-1.epub",
              format = BookFormat.EPUB,
              contentHash = bookHash,
            ),
          )
        }
      }
    firstStarted.await()

    var secondSawExistingHash = false
    val secondImport =
      async {
        repo.importWithHashes { existingHashes ->
          secondSawExistingHash = bookHash in existingHashes
          if (secondSawExistingHash) {
            Result.failure(IllegalStateException("duplicate"))
          } else {
            Result.success(
              BookEntry(
                id = "book-2",
                title = "Sample",
                author = "Author",
                fileName = "book-2.epub",
                format = BookFormat.EPUB,
                contentHash = bookHash,
              ),
            )
          }
        }
      }

    releaseFirst.complete(Unit)
    val firstResult = firstImport.await()
    val secondResult = secondImport.await()

    assertTrue(firstResult.isSuccess)
    assertTrue(secondResult.isFailure)
    assertTrue(secondSawExistingHash)
    assertEquals(1, repo.books.value.size)
  }

  @Test
  fun importFromSyncedFileSharesImportMutexWithLocalImport() = runTest {
    val repo = createRepository()
    val payloadFile = File(dir, "sync.epub").apply { writeText("same-book-bytes") }
    val hash = BookImporter.sha256Hex(payloadFile)!!
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()

    val localImport =
      async {
        repo.importWithHashes {
          firstStarted.complete(Unit)
          releaseFirst.await()
          Result.success(
            BookEntry(
              id = "local-1",
              title = "Sample",
              author = "Author",
              fileName = "local-1.epub",
              format = BookFormat.EPUB,
              contentHash = hash,
            ),
          )
        }
      }
    firstStarted.await()

    val syncedImport =
      async {
        repo.importFromSyncedFile(
          tempFile = payloadFile,
          title = "Sample",
          author = "Author",
          format = BookFormat.EPUB,
          contentHash = hash,
          progress = 0.5f,
          lastLocatorJson = null,
          updatedAt = 100L,
        )
      }

    releaseFirst.complete(Unit)
    assertTrue(localImport.await().isSuccess)
    assertFalse(syncedImport.await())
    assertEquals(1, repo.books.value.size)
  }

  @Test
  fun commitCatchesIoExceptionKeepsDirtySetsLastWriteErrorAndFlushesOnNextCommit() = runTest {
    val repo = createRepository()
    val initialEntry =
      BookEntry(
        id = "book-1",
        title = "Book",
        author = "Author",
        fileName = "book-1.pdf",
        format = BookFormat.PDF,
        contentHash = "hash-1",
      )
    assertTrue(repo.importWithHashes { Result.success(initialEntry) }.isSuccess)
    assertFalse(repo.lastWriteError.value)

    val libraryFile = File(dir, "library.json")
    assertTrue(libraryFile.delete())
    libraryFile.mkdirs()
    File(libraryFile, "blocker").writeText("lock")

    repo.savePdfReadingMode("book-1", PdfReadingMode.HYBRID_REFLOW)

    assertEquals(PdfReadingMode.HYBRID_REFLOW, repo.find("book-1")?.pdfReadingMode)
    assertTrue(repo.lastWriteError.value)

    libraryFile.deleteRecursively()
    // No-op applySyncedProgress still flushes because dirty == true.
    repo.applySyncedProgress(emptyMap())

    assertFalse(repo.lastWriteError.value)
    val persisted = BookIndexStore(libraryFile).read()
    assertEquals(PdfReadingMode.HYBRID_REFLOW, persisted.single().pdfReadingMode)
  }

  @Test
  fun backfillMissingCoversPersistsIdBeforeExtractCatchesThrowableAndSkipsOnNextRun() = runTest {
    val books =
      listOf(
        BookEntry(id = "b1", title = "OOM Book", author = "", fileName = "b1.epub", format = BookFormat.EPUB, contentHash = "h1"),
        BookEntry(id = "b2", title = "Bad Book", author = "", fileName = "b2.pdf", format = BookFormat.PDF, contentHash = "h2"),
        BookEntry(id = "b3", title = "Good Book", author = "", fileName = "b3.epub", format = BookFormat.EPUB, contentHash = "h3"),
      )
    books.forEach { File(booksDir, it.fileName).writeText("content-${it.id}") }
    BookIndexStore(File(dir, "library.json")).write(books)

    val attemptedIds = linkedSetOf<String>()
    val events = mutableListOf<String>()
    val extractedCalls = mutableListOf<String>()
    val repo =
      createRepository(
        attemptedIds = attemptedIds,
        onAttemptedPersisted = { ids -> events += "persisted:${ids.sorted().joinToString(",")}" },
        extractBackfillCover = { book, _, coverFile ->
          extractedCalls += book.id
          events += "extract:${book.id}:alreadyPersisted=${book.id in attemptedIds}"
          when (book.id) {
            "b1" -> {
              coverFile.writeText("partial")
              throw OutOfMemoryError("simulated OOM")
            }
            "b2" -> throw IllegalStateException("corrupt pdf")
            "b3" -> {
              coverFile.writeText("jpeg")
              true
            }
            else -> false
          }
        },
      )

    repo.backfillMissingCovers()

    assertEquals(listOf("b1", "b2", "b3"), extractedCalls)
    assertTrue(events.contains("extract:b1:alreadyPersisted=true"))
    assertTrue(events.contains("extract:b2:alreadyPersisted=true"))
    assertTrue(events.contains("extract:b3:alreadyPersisted=true"))
    assertTrue(events.indexOf("persisted:b1") < events.indexOf("extract:b1:alreadyPersisted=true"))
    assertFalse(File(coversDir, "b1.jpg").exists())
    assertNull(repo.find("b1")?.coverFileName)
    assertNull(repo.find("b2")?.coverFileName)
    assertEquals("b3.jpg", repo.find("b3")?.coverFileName)

    // Second run must skip all previously attempted IDs including those that crashed.
    extractedCalls.clear()
    repo.backfillMissingCovers()
    assertTrue(extractedCalls.isEmpty())
  }

  @Test
  fun backfillMissingCoversRethrowsCancellationException() = runTest {
    val book = BookEntry(id = "b1", title = "Cancel", author = "", fileName = "b1.epub", format = BookFormat.EPUB, contentHash = "h1")
    File(booksDir, book.fileName).writeText("data")
    BookIndexStore(File(dir, "library.json")).write(listOf(book))
    val attemptedIds = linkedSetOf<String>()
    val repo =
      createRepository(
        attemptedIds = attemptedIds,
        extractBackfillCover = { _, _, _ -> throw CancellationException("cancelled") },
      )

    val failure = runCatching { repo.backfillMissingCovers() }.exceptionOrNull()
    assertTrue(failure is CancellationException)
    assertTrue("b1" in attemptedIds)
  }

  @Test
  fun savePdfReadingModeDoesNotBumpUpdatedAtAndSkipsUnchangedWrite() = runTest {
    val repo = createRepository()
    val entry =
      BookEntry(
        id = "pdf-1",
        title = "PDF Book",
        author = "Author",
        fileName = "pdf-1.pdf",
        format = BookFormat.PDF,
        contentHash = "hash-pdf-1",
        progress = 0.25f,
        lastLocatorJson = """{"href":"page-2"}""",
        updatedAt = 12_345L,
        pdfReadingMode = PdfReadingMode.SMART_ORIGINAL,
      )
    assertTrue(repo.importWithHashes { Result.success(entry) }.isSuccess)

    repo.savePdfReadingMode("pdf-1", PdfReadingMode.HYBRID_REFLOW)

    val afterModeChange = repo.find("pdf-1")!!
    assertEquals(PdfReadingMode.HYBRID_REFLOW, afterModeChange.pdfReadingMode)
    assertEquals(12_345L, afterModeChange.updatedAt)

    // Make library.json unwritable to verify unchanged mode is a true no-op (no disk write).
    val libraryFile = File(dir, "library.json")
    assertTrue(libraryFile.delete())
    libraryFile.mkdirs()
    File(libraryFile, "blocker").writeText("lock")

    repo.savePdfReadingMode("pdf-1", PdfReadingMode.HYBRID_REFLOW)
    assertFalse(repo.lastWriteError.value)
  }

  @Test
  fun saveProgressSkipsWriteAndDoesNotBumpUpdatedAtWhenLocatorAndProgressUnchanged() = runTest {
    val repo = createRepository()
    val locator = """{"href":"ch1.xhtml","locations":{"progression":0.5}}"""
    val entry =
      BookEntry(
        id = "epub-1",
        title = "EPUB Book",
        author = "Author",
        fileName = "epub-1.epub",
        format = BookFormat.EPUB,
        contentHash = "hash-epub-1",
        progress = 0.5f,
        lastLocatorJson = locator,
        updatedAt = 50_000L,
      )
    assertTrue(repo.importWithHashes { Result.success(entry) }.isSuccess)

    val libraryFile = File(dir, "library.json")
    assertTrue(libraryFile.delete())
    libraryFile.mkdirs()
    File(libraryFile, "blocker").writeText("lock")

    // Exact same locator and progress within 1e-6f must not write to disk or change updatedAt.
    repo.saveProgress("epub-1", locator, 0.5f + 5e-7f)

    assertEquals(50_000L, repo.find("epub-1")?.updatedAt)
    assertFalse(repo.lastWriteError.value)

    // Restore writable directory and change locator -> updatedAt must advance.
    libraryFile.deleteRecursively()
    val newLocator = """{"href":"ch2.xhtml","locations":{"progression":0.1}}"""
    repo.saveProgress("epub-1", newLocator, 0.6f)

    val updated = repo.find("epub-1")!!
    assertEquals(newLocator, updated.lastLocatorJson)
    assertEquals(0.6f, updated.progress, 1e-6f)
    assertTrue(updated.updatedAt > 50_000L)
  }

  @Test
  fun applySyncedProgressClampsFutureRemoteUpdatedAtToNow() = runTest {
    val repo = createRepository()
    val entry =
      BookEntry(
        id = "book-sync",
        title = "Sync Book",
        author = "Author",
        fileName = "book-sync.epub",
        format = BookFormat.EPUB,
        contentHash = "hash-sync",
        progress = 0.1f,
        lastLocatorJson = """{"href":"ch1"}""",
        updatedAt = 1_000L,
      )
    assertTrue(repo.importWithHashes { Result.success(entry) }.isSuccess)

    val localNow = 2_000L
    val farFutureRemoteTime = 9_999_999_999_999L
    repo.applySyncedProgress(
      progressByHash = mapOf("hash-sync" to ("""{"href":"ch5"}""" to (0.5f to farFutureRemoteTime))),
      now = localNow,
    )

    val afterSync = repo.find("book-sync")!!
    assertEquals("""{"href":"ch5"}""", afterSync.lastLocatorJson)
    assertEquals(0.5f, afterSync.progress, 1e-6f)
    assertEquals(localNow, afterSync.updatedAt)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun lazyEngineNotInitializedOnPreloadAwaitOrMetadataMutation() = runTest {
    val existing =
      BookEntry(
        id = "book-lazy",
        title = "Lazy Book",
        author = "Author",
        fileName = "book-lazy.pdf",
        format = BookFormat.PDF,
        contentHash = "hash-lazy",
        progress = 0.2f,
      )
    BookIndexStore(File(dir, "library.json")).write(listOf(existing))

    var engineInitCount = 0
    val engineLazy: Lazy<ReadiumEngine> = lazy {
      engineInitCount += 1
      error("ReadiumEngine must not be constructed until opening a book")
    }
    val ioDispatcher = StandardTestDispatcher(testScheduler)
    val repo =
      LibraryRepository(
        appFilesDir = dir,
        engineLazy = engineLazy,
        autoBackfill = false,
        preloadInBackground = true,
        ioDispatcher = ioDispatcher,
      )

    assertFalse(engineLazy.isInitialized())
    assertEquals(0, engineInitCount)
    assertFalse(repo.loaded.value)
    assertTrue(repo.books.value.isEmpty())
    assertNull(repo.find("book-lazy"))

    repo.awaitLoaded()

    assertTrue(repo.loaded.value)
    assertNotNull(repo.find("book-lazy"))
    assertFalse(engineLazy.isInitialized())
    assertEquals(0, engineInitCount)

    repo.saveProgress("book-lazy", """{"href":"p2"}""", 0.5f)
    repo.savePdfReadingMode("book-lazy", PdfReadingMode.HYBRID_REFLOW)
    assertFalse(engineLazy.isInitialized())
    assertEquals(0, engineInitCount)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun commitBeforePreloadCompletesLoadsExistingBooksAndPreservesThem() = runTest {
    val bookA =
      BookEntry(
        id = "book-a",
        title = "Book A",
        author = "Author A",
        fileName = "book-a.epub",
        format = BookFormat.EPUB,
        contentHash = "hash-a",
        progress = 0.1f,
      )
    val bookB =
      BookEntry(
        id = "book-b",
        title = "Book B",
        author = "Author B",
        fileName = "book-b.epub",
        format = BookFormat.EPUB,
        contentHash = "hash-b",
        progress = 0.4f,
      )
    BookIndexStore(File(dir, "library.json")).write(listOf(bookA, bookB))

    val ioDispatcher = StandardTestDispatcher(testScheduler)
    val repo =
      LibraryRepository(
        appFilesDir = dir,
        engineLazy = lazy { error("Not used") },
        autoBackfill = false,
        preloadInBackground = true,
        ioDispatcher = ioDispatcher,
      )

    assertFalse(repo.loaded.value)

    // Mutate before background preload has executed on ioDispatcher
    repo.saveProgress("book-a", """{"href":"ch3"}""", 0.75f)

    assertTrue(repo.loaded.value)
    assertEquals(2, repo.books.value.size)
    assertEquals(0.75f, repo.find("book-a")?.progress ?: 0f, 1e-6f)
    assertEquals(0.4f, repo.find("book-b")?.progress ?: 0f, 1e-6f)

    val persisted = BookIndexStore(File(dir, "library.json")).read()
    assertEquals(2, persisted.size)
    assertEquals(0.75f, persisted.first { it.id == "book-a" }.progress, 1e-6f)
    assertEquals(0.4f, persisted.first { it.id == "book-b" }.progress, 1e-6f)
  }
}
