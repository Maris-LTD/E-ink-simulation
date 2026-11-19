package com.example.emptyapp.data.sync

import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.data.book.BookImporter
import com.example.emptyapp.data.book.SyncedBookFileImporter
import com.example.emptyapp.data.book.SyncedBookImportResult
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBookDownloadCoordinatorTest {
  private fun remoteBook(hash: String, title: String = "Book $hash", format: BookFormat = BookFormat.EPUB) =
    SyncedBookProgress(
      contentHash = hash,
      title = title,
      author = "Author",
      format = format,
      progress = 0.25f,
      lastLocatorJson = """{"p":0.25}""",
      updatedAt = 100L,
    )

  private fun withTempDir(block: suspend (tempDir: File) -> Unit) = runTest {
    val root = Files.createTempDirectory("drive_book_dl_test").toFile()
    try {
      block(root)
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun isolatesPerBookDownloadAndImportFailuresAndAlwaysCleansUpTempFiles() = withTempDir { root ->
    val tempDir = File(root, "sync_tmp")
    val booksDir = File(root, "books").apply { mkdirs() }
    val coversDir = File(root, "covers").apply { mkdirs() }

    val content1 = "book-1-valid-payload"
    val content2 = "book-2-corrupt-on-drive"
    val content3 = "book-3-import-crash"
    val content4 = "book-4-valid-payload"

    val hash1 = sha256(content1)
    val hash2Expected = sha256("book-2-expected-different")
    val hash3 = sha256(content3)
    val hash4 = sha256(content4)
    val hash5 = sha256("book-5-network-fail")

    val remoteBooks =
      listOf(
        remoteBook(hash1, "Book 1"),
        remoteBook(hash2Expected, "Book 2 Corrupt"),
        remoteBook(hash3, "Book 3 Crash"),
        remoteBook(hash4, "Book 4"),
        remoteBook(hash5, "Book 5 NetFail"),
      )
    val appDataFiles =
      remoteBooks.associate { book ->
        "book_${book.contentHash}.${book.format.extension}" to "id_${book.contentHash}"
      }
    val importedTitles = mutableListOf<String>()

    val summary =
      DriveBookDownloadCoordinator.downloadMissingBooks(
        remoteBooks = remoteBooks,
        appDataFiles = appDataFiles,
        initialLocalHashes = emptySet(),
        deletedHashes = emptySet(),
        tempDir = tempDir,
        downloadFileTo = { driveId, tempFile ->
          when (driveId) {
            "id_$hash1" -> {
              tempFile.writeText(content1)
              true
            }
            "id_$hash2Expected" -> {
              tempFile.writeText(content2)
              true
            }
            "id_$hash3" -> {
              tempFile.writeText(content3)
              true
            }
            "id_$hash4" -> {
              tempFile.writeText(content4)
              true
            }
            "id_$hash5" -> {
              tempFile.writeText("partial-download")
              false
            }
            else -> false
          }
        },
        importSyncedFile = { tempFile, book ->
          val res =
            SyncedBookFileImporter.importSyncedBook(
              sourceFile = tempFile,
              booksDir = booksDir,
              coversDir = coversDir,
              title = book.title,
              author = book.author,
              format = book.format,
              expectedContentHash = book.contentHash,
              progress = book.progress,
              lastLocatorJson = book.lastLocatorJson,
              updatedAt = book.updatedAt,
              isDuplicateHash = { false },
              verifyAndExtractCover = { _, _ ->
                if (book.contentHash == hash3) {
                  throw IOException("ENOSPC: No space left on device")
                }
                true
              },
              commitEntry = { entry ->
                importedTitles += entry.title
                true
              },
            )
          when (res) {
            is SyncedBookImportResult.Imported -> true
            SyncedBookImportResult.Duplicate -> false
            is SyncedBookImportResult.Failed -> throw res.error
          }
        },
      )

    assertEquals(2, summary.importedCount)
    assertEquals(3, summary.failedCount)
    assertEquals(listOf("Book 1", "Book 4"), importedTitles)
    assertTrue(tempDir.listFiles().orEmpty().isEmpty())
    assertEquals(2, booksDir.listFiles().orEmpty().size)
  }

  @Test
  fun skipsLocalAndDeletedAndDuplicateHashesInSameRun() = withTempDir { tempDir ->
    val hLocal = sha256("local")
    val hDeleted = sha256("deleted")
    val hNew = sha256("new")
    val remoteBooks =
      listOf(
        remoteBook(hLocal),
        remoteBook(hDeleted),
        remoteBook(hNew, "New 1"),
        remoteBook(hNew.uppercase(), "New 1 Duplicate Case"),
      )
    val appDataFiles =
      mapOf(
        "book_$hLocal.epub" to "id-local",
        "book_$hDeleted.epub" to "id-deleted",
        "book_$hNew.epub" to "id-new",
        "book_${hNew.uppercase()}.epub" to "id-new-upper",
      )
    val downloadedIds = mutableListOf<String>()

    val summary =
      DriveBookDownloadCoordinator.downloadMissingBooks(
        remoteBooks = remoteBooks,
        appDataFiles = appDataFiles,
        initialLocalHashes = setOf(hLocal),
        deletedHashes = setOf(hDeleted),
        tempDir = tempDir,
        downloadFileTo = { id, tempFile ->
          downloadedIds += id
          tempFile.writeText("new")
          true
        },
        importSyncedFile = { _, _ -> true },
      )

    assertEquals(1, summary.importedCount)
    assertEquals(0, summary.failedCount)
    assertEquals(listOf("id-new"), downloadedIds)
    assertTrue(tempDir.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun rethrows401AndCleansUpTempFileSoDriveAuthRetryCanRefresh() = withTempDir { tempDir ->
    val h1 = sha256("b1")
    val h2 = sha256("b2")
    val remoteBooks = listOf(remoteBook(h1), remoteBook(h2))
    val appDataFiles = mapOf("book_$h1.epub" to "id1", "book_$h2.epub" to "id2")

    val err =
      runCatching {
        DriveBookDownloadCoordinator.downloadMissingBooks(
          remoteBooks = remoteBooks,
          appDataFiles = appDataFiles,
          initialLocalHashes = emptySet(),
          deletedHashes = emptySet(),
          tempDir = tempDir,
          downloadFileTo = { _, tempFile ->
            tempFile.writeText("partial-before-401")
            throw DriveHttpException(401, "HTTP 401")
          },
          importSyncedFile = { _, _ -> true },
        )
      }.exceptionOrNull()

    assertTrue(err is DriveHttpException)
    assertEquals(401, (err as DriveHttpException).statusCode)
    assertTrue(tempDir.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun rethrowsCancellationAndSessionInvalidatedAndCleansUpTempFile() = withTempDir { tempDir ->
    val h1 = sha256("b1")
    val remoteBooks = listOf(remoteBook(h1))
    val appDataFiles = mapOf("book_$h1.epub" to "id1")

    val cancelErr =
      runCatching {
        DriveBookDownloadCoordinator.downloadMissingBooks(
          remoteBooks = remoteBooks,
          appDataFiles = appDataFiles,
          initialLocalHashes = emptySet(),
          deletedHashes = emptySet(),
          tempDir = tempDir,
          downloadFileTo = { _, tempFile ->
            tempFile.writeText("temp-data")
            true
          },
          importSyncedFile = { _, _ -> throw CancellationException("cancelled") },
        )
      }.exceptionOrNull()

    assertTrue(cancelErr is CancellationException)
    assertTrue(tempDir.listFiles().orEmpty().isEmpty())

    val sessionErr =
      runCatching {
        DriveBookDownloadCoordinator.downloadMissingBooks(
          remoteBooks = remoteBooks,
          appDataFiles = appDataFiles,
          initialLocalHashes = emptySet(),
          deletedHashes = emptySet(),
          tempDir = tempDir,
          downloadFileTo = { _, tempFile ->
            tempFile.writeText("temp-data")
            true
          },
          importSyncedFile = { _, _ -> throw SyncSessionInvalidatedException() },
        )
      }.exceptionOrNull()

    assertTrue(sessionErr is SyncSessionInvalidatedException)
    assertTrue(tempDir.listFiles().orEmpty().isEmpty())
  }

  @Test
  fun catchesOutOfMemoryErrorDuringBookImportWithoutStoppingRemainingBooks() = withTempDir { tempDir ->
    val h1 = sha256("b1")
    val h2 = sha256("b2")
    val remoteBooks = listOf(remoteBook(h1, "Huge CBZ"), remoteBook(h2, "Normal EPUB"))
    val appDataFiles = mapOf("book_$h1.epub" to "id1", "book_$h2.epub" to "id2")
    val imported = mutableListOf<String>()

    val summary =
      DriveBookDownloadCoordinator.downloadMissingBooks(
        remoteBooks = remoteBooks,
        appDataFiles = appDataFiles,
        initialLocalHashes = emptySet(),
        deletedHashes = emptySet(),
        tempDir = tempDir,
        downloadFileTo = { id, tempFile ->
          tempFile.writeText(id)
          true
        },
        importSyncedFile = { tempFile, book ->
          assertFalse(tempFile.readText().isEmpty())
          if (book.contentHash == h1) {
            throw OutOfMemoryError("OOM decoding corrupted archive")
          }
          imported += book.title
          true
        },
      )

    assertEquals(1, summary.importedCount)
    assertEquals(1, summary.failedCount)
    assertEquals(listOf("Normal EPUB"), imported)
    assertTrue(tempDir.listFiles().orEmpty().isEmpty())
  }

  private fun sha256(content: String): String {
    val tmp = Files.createTempFile("hash_calc", ".tmp").toFile()
    return try {
      tmp.writeText(content)
      BookImporter.sha256Hex(tmp)!!
    } finally {
      tmp.delete()
    }
  }
}
