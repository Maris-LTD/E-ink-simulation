package com.example.emptyapp.ui.library

import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.data.book.DuplicateBookException
import com.example.emptyapp.data.book.LibraryRepository
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryWorkflowRulesTest {
  @Test
  fun proBadgeStaysHiddenUntilProOrReadingEngagement() {
    assertFalse(shouldShowLibraryProBadge(isPro = false, hasBilling = false, totalPagesTurned = 50, totalReadingSeconds = 600L))
    assertFalse(shouldShowLibraryProBadge(isPro = false, hasBilling = true, totalPagesTurned = 0, totalReadingSeconds = 0L))
    assertFalse(shouldShowLibraryProBadge(isPro = false, hasBilling = true, totalPagesTurned = 4, totalReadingSeconds = 59L))
    assertTrue(shouldShowLibraryProBadge(isPro = false, hasBilling = true, totalPagesTurned = 5, totalReadingSeconds = 0L))
    assertTrue(shouldShowLibraryProBadge(isPro = false, hasBilling = true, totalPagesTurned = 0, totalReadingSeconds = 60L))
    assertTrue(shouldShowLibraryProBadge(isPro = true, hasBilling = true, totalPagesTurned = 0, totalReadingSeconds = 0L))
  }

  @Test
  fun setupBannerRequiresReadingStartAndMissingLockFeatures() {
    assertFalse(shouldShowSetupBanner(dismissed = false, lockFeaturesCount = 0, hasStartedReading = false))
    assertTrue(shouldShowSetupBanner(dismissed = false, lockFeaturesCount = 0, hasStartedReading = true))
    assertTrue(shouldShowSetupBanner(dismissed = false, lockFeaturesCount = 1, hasStartedReading = true))
    assertFalse(shouldShowSetupBanner(dismissed = false, lockFeaturesCount = 2, hasStartedReading = true))
    assertFalse(shouldShowSetupBanner(dismissed = true, lockFeaturesCount = 0, hasStartedReading = true))
  }

  @Test
  fun formatsFileSizesReadably() {
    assertEquals("0 KB", formatFileSizeBytes(0L))
    assertEquals("< 1 KB", formatFileSizeBytes(512L))
    assertEquals("12 KB", formatFileSizeBytes(12 * 1024L))
    assertEquals("2.5 MB", formatFileSizeBytes((2.5 * 1024 * 1024).toLong()))
  }

  @Test
  fun summarizesBatchImportCounts() {
    val allOk = LibraryViewModel.summarizeBatchImport(imported = 4, duplicates = 0, failed = 0)
    assertEquals(BatchImportSummaryKind.ALL_IMPORTED, allOk.kind)

    val withSkipped = LibraryViewModel.summarizeBatchImport(imported = 3, duplicates = 2, failed = 1)
    assertEquals(BatchImportSummaryKind.IMPORTED_WITH_SKIPPED, withSkipped.kind)
    assertEquals(3, withSkipped.skipped)

    val allDup = LibraryViewModel.summarizeBatchImport(imported = 0, duplicates = 2, failed = 0)
    assertEquals(BatchImportSummaryKind.ALL_DUPLICATES, allDup.kind)

    val allFail = LibraryViewModel.summarizeBatchImport(imported = 0, duplicates = 1, failed = 2)
    assertEquals(BatchImportSummaryKind.ALL_FAILED, allFail.kind)
    assertEquals(2, allFail.failed)
  }

  @Test
  fun repositoryMarkCompletedAndImportOrFindExistingBehaveCorrectly() = runTest {
    val dir = createTempDirectory("lib_workflow_repo_test").toFile()
    try {
      val existing =
        BookEntry(
          id = "book-1",
          title = "Existing Book",
          author = "Author",
          fileName = "book-1.epub",
          format = BookFormat.EPUB,
          lastLocatorJson = """{"href":"ch2"}""",
          progress = 0.45f,
          contentHash = "abc123",
        )
      var importCalls = 0
      val repo =
        LibraryRepository(
          appFilesDir = dir,
          engineProvider = { error("Not used") },
          importFromUri = { _, _ -> Result.failure(UnsupportedOperationException()) },
          verifyAndExtractSyncedCover = { _, _, _ -> false },
          extractBackfillCover = { _, _, _ -> false },
          readAttemptedCoverIds = { emptySet() },
          persistAttemptedCoverIds = {},
          autoBackfill = false,
        )

      val performImport: suspend (Set<String>) -> Result<BookEntry> = { existingHashes ->
        importCalls++
        if ("abc123" in existingHashes) {
          Result.failure(DuplicateBookException("abc123", "duplicate"))
        } else {
          Result.success(existing)
        }
      }
      val first = repo.importOrFindExistingWith(performImport)
      assertTrue(first.isSuccess)
      assertEquals("book-1", first.getOrNull()?.id)

      val second = repo.importOrFindExistingWith(performImport)
      assertTrue(second.isSuccess)
      assertEquals("book-1", second.getOrNull()?.id)
      assertEquals(1, repo.books.value.size)
      assertEquals(2, importCalls)

      repo.markCompleted("book-1", completed = true)
      assertEquals(1f, repo.find("book-1")?.progress ?: 0f, 1e-6f)
      assertEquals("""{"href":"ch2"}""", repo.find("book-1")?.lastLocatorJson)

      repo.markCompleted("book-1", completed = false)
      assertEquals(0f, repo.find("book-1")?.progress ?: -1f, 1e-6f)
      assertNull(repo.find("book-1")?.lastLocatorJson)
    } finally {
      dir.deleteRecursively()
    }
  }
}
