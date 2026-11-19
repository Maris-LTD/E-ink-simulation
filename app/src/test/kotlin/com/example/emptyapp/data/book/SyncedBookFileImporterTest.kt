package com.example.emptyapp.data.book

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedBookFileImporterTest {
  private fun withTempRoot(block: suspend (root: File, booksDir: File, coversDir: File, tempDir: File) -> Unit) = runTest {
    val root = Files.createTempDirectory("synced_book_import_test").toFile()
    try {
      val booksDir = File(root, "books")
      val coversDir = File(root, "covers")
      val tempDir = File(root, "sync_tmp")
      booksDir.mkdirs()
      coversDir.mkdirs()
      tempDir.mkdirs()
      block(root, booksDir, coversDir, tempDir)
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun importsWhenSha256MatchesCaseInsensitiveAndMovesFileWithoutLeavingTemp() = withTempRoot { _, booksDir, coversDir, tempDir ->
    val tempFile = File(tempDir, "sync_book.tmp").apply { writeText("hello-epub-content") }
    val hashUpper = BookImporter.sha256Hex(tempFile)!!.uppercase()
    val committed = mutableListOf<BookEntry>()

    val result =
      SyncedBookFileImporter.importSyncedBook(
        sourceFile = tempFile,
        booksDir = booksDir,
        coversDir = coversDir,
        title = "Cloud Book",
        author = "Author A",
        format = BookFormat.EPUB,
        expectedContentHash = hashUpper,
        progress = 0.45f,
        lastLocatorJson = """{"href":"c1.xhtml"}""",
        updatedAt = 1234L,
        idProvider = { "book-1" },
        isDuplicateHash = { false },
        verifyAndExtractCover = { bookFile, coverFile ->
          assertEquals("hello-epub-content", bookFile.readText())
          coverFile.writeText("jpeg-bytes")
          true
        },
        commitEntry = { entry ->
          committed += entry
          true
        },
      )

    assertTrue(result is SyncedBookImportResult.Imported)
    val entry = (result as SyncedBookImportResult.Imported).entry
    assertEquals("book-1", entry.id)
    assertEquals("book-1.epub", entry.fileName)
    assertEquals("book-1.jpg", entry.coverFileName)
    assertEquals(hashUpper.lowercase(), entry.contentHash)
    assertFalse(tempFile.exists())
    assertTrue(File(booksDir, "book-1.epub").exists())
    assertTrue(File(coversDir, "book-1.jpg").exists())
    assertEquals(1, committed.size)
  }

  @Test
  fun rejectsAndCleansUpTempDestAndCoverWhenSha256Mismatches() = withTempRoot { _, booksDir, coversDir, tempDir ->
    val tempFile = File(tempDir, "corrupt_book.tmp").apply { writeText("truncated-payload") }
    var verifyCalled = false
    var commitCalled = false

    val result =
      SyncedBookFileImporter.importSyncedBook(
        sourceFile = tempFile,
        booksDir = booksDir,
        coversDir = coversDir,
        title = "Corrupt Book",
        author = "Author",
        format = BookFormat.EPUB,
        expectedContentHash = "0000000000000000000000000000000000000000000000000000000000000000",
        progress = 0.1f,
        lastLocatorJson = null,
        updatedAt = 100L,
        idProvider = { "book-corrupt" },
        isDuplicateHash = { false },
        verifyAndExtractCover = { _, _ ->
          verifyCalled = true
          true
        },
        commitEntry = {
          commitCalled = true
          true
        },
      )

    assertTrue(result is SyncedBookImportResult.Failed)
    val error = (result as SyncedBookImportResult.Failed).error
    assertTrue(error is IOException)
    assertTrue(error.message.orEmpty().contains("SHA-256 mismatch"))
    assertFalse(verifyCalled)
    assertFalse(commitCalled)
    assertFalse(tempFile.exists())
    assertTrue(booksDir.listFiles().orEmpty().isEmpty())
    assertTrue(coversDir.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun checksDuplicateBeforeCreatingDestOrExtractingCoverAndDeletesTemp() = withTempRoot { _, booksDir, coversDir, tempDir ->
    val tempFile = File(tempDir, "dup.tmp").apply { writeText("duplicate-book-bytes") }
    val hash = BookImporter.sha256Hex(tempFile)!!
    var verifyCalled = false

    val result =
      SyncedBookFileImporter.importSyncedBook(
        sourceFile = tempFile,
        booksDir = booksDir,
        coversDir = coversDir,
        title = "Dup",
        author = "Author",
        format = BookFormat.PDF,
        expectedContentHash = hash,
        progress = 0.2f,
        lastLocatorJson = null,
        updatedAt = 100L,
        idProvider = { "book-dup" },
        isDuplicateHash = { it == hash },
        verifyAndExtractCover = { _, _ ->
          verifyCalled = true
          true
        },
        commitEntry = { true },
      )

    assertEquals(SyncedBookImportResult.Duplicate, result)
    assertFalse(verifyCalled)
    assertFalse(tempFile.exists())
    assertTrue(booksDir.listFiles().orEmpty().isEmpty())
    assertTrue(coversDir.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun cleansUpDestAndCoverWhenEngineOpenOrCommitFailsOrRaceDuplicateAtCommit() = withTempRoot { _, booksDir, coversDir, tempDir ->
    val temp1 = File(tempDir, "bad_open.tmp").apply { writeText("bad-book-bytes") }
    val hash1 = BookImporter.sha256Hex(temp1)!!

    val openFailResult =
      SyncedBookFileImporter.importSyncedBook(
        sourceFile = temp1,
        booksDir = booksDir,
        coversDir = coversDir,
        title = "Unopenable",
        author = "Author",
        format = BookFormat.CBZ,
        expectedContentHash = hash1,
        progress = 0f,
        lastLocatorJson = null,
        updatedAt = 100L,
        idProvider = { "book-bad-open" },
        isDuplicateHash = { false },
        verifyAndExtractCover = { _, coverFile ->
          coverFile.writeText("partial-cover")
          throw IOException("Engine open failed")
        },
        commitEntry = { true },
      )

    assertTrue(openFailResult is SyncedBookImportResult.Failed)
    assertFalse(temp1.exists())
    assertFalse(File(booksDir, "book-bad-open.cbz").exists())
    assertFalse(File(coversDir, "book-bad-open.jpg").exists())

    val temp2 = File(tempDir, "race_dup.tmp").apply { writeText("race-duplicate-bytes") }
    val hash2 = BookImporter.sha256Hex(temp2)!!

    val raceDupResult =
      SyncedBookFileImporter.importSyncedBook(
        sourceFile = temp2,
        booksDir = booksDir,
        coversDir = coversDir,
        title = "Race Dup",
        author = "Author",
        format = BookFormat.EPUB,
        expectedContentHash = hash2,
        progress = 0f,
        lastLocatorJson = null,
        updatedAt = 200L,
        idProvider = { "book-race-dup" },
        isDuplicateHash = { false },
        verifyAndExtractCover = { _, coverFile ->
          coverFile.writeText("cover-to-clean")
          true
        },
        commitEntry = { false },
      )

    assertEquals(SyncedBookImportResult.Duplicate, raceDupResult)
    assertFalse(temp2.exists())
    assertFalse(File(booksDir, "book-race-dup.epub").exists())
    assertFalse(File(coversDir, "book-race-dup.jpg").exists())
  }

  @Test
  fun importsBookWithoutCoverWhenCoverExtractionFailsOrReturnsFalse() = withTempRoot { _, booksDir, coversDir, tempDir ->
    val tempFile = File(tempDir, "no_cover.tmp").apply { writeText("cbz-huge-cover") }
    val hash = BookImporter.sha256Hex(tempFile)!!

    val result =
      SyncedBookFileImporter.importSyncedBook(
        sourceFile = tempFile,
        booksDir = booksDir,
        coversDir = coversDir,
        title = "Large CBZ",
        author = "Artist",
        format = BookFormat.CBZ,
        expectedContentHash = hash,
        progress = 0.8f,
        lastLocatorJson = null,
        updatedAt = 500L,
        idProvider = { "book-no-cover" },
        isDuplicateHash = { false },
        verifyAndExtractCover = { _, coverFile ->
          coverFile.writeText("half-written-cover")
          false
        },
        commitEntry = { true },
      )

    assertTrue(result is SyncedBookImportResult.Imported)
    val entry = (result as SyncedBookImportResult.Imported).entry
    assertNull(entry.coverFileName)
    assertTrue(File(booksDir, "book-no-cover.cbz").exists())
    assertFalse(File(coversDir, "book-no-cover.jpg").exists())
  }

  @Test
  fun rethrowsCancellationExceptionAndCleansUpFiles() = withTempRoot { _, booksDir, coversDir, tempDir ->
    val tempFile = File(tempDir, "cancel.tmp").apply { writeText("cancel-me") }
    val hash = BookImporter.sha256Hex(tempFile)!!

    val err =
      runCatching {
        SyncedBookFileImporter.importSyncedBook(
          sourceFile = tempFile,
          booksDir = booksDir,
          coversDir = coversDir,
          title = "Cancelled",
          author = "Author",
          format = BookFormat.EPUB,
          expectedContentHash = hash,
          progress = 0f,
          lastLocatorJson = null,
          updatedAt = 100L,
          idProvider = { "book-cancel" },
          isDuplicateHash = { false },
          verifyAndExtractCover = { _, coverFile ->
            coverFile.writeText("cover")
            throw CancellationException("sync cancelled")
          },
          commitEntry = { true },
        )
      }.exceptionOrNull()

    assertTrue(err is CancellationException)
    assertFalse(tempFile.exists())
    assertFalse(File(booksDir, "book-cancel.epub").exists())
    assertFalse(File(coversDir, "book-cancel.jpg").exists())
  }

  @Test
  fun moveFileToTargetFallsBackToCopyAndDeletesSourceWhenRenameFails() = withTempRoot { _, booksDir, _, tempDir ->
    val source =
      object : File(tempDir, "fallback_src.tmp") {
        override fun renameTo(dest: File): Boolean = false
      }
    source.writeText("fallback-copy-content")
    val target = File(booksDir, "fallback_dest.epub")

    SyncedBookFileImporter.moveFileToTarget(source, target)

    assertFalse(source.exists())
    assertTrue(target.exists())
    assertEquals("fallback-copy-content", target.readText())
  }
}
