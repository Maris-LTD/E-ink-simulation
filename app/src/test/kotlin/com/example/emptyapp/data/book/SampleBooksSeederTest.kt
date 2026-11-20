package com.example.emptyapp.data.book

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleBooksSeederTest {
  @Test
  fun seedsSamplesOnceOnEmptyLibraryAndSkipsSubsequentRuns() = runTest {
    var seeded = false
    val books = mutableListOf<BookEntry>()
    val importedTitles = mutableListOf<String>()
    val seeder =
      SampleBooksSeeder(
        isAlreadySeeded = { seeded },
        markSeeded = { seeded = true },
        awaitLibraryLoaded = {},
        hasAnyBooks = { books.isNotEmpty() },
        importSampleAsset = { spec, updatedAt ->
          importedTitles += spec.title
          val entry =
            BookEntry(
              id = spec.displayName,
              title = spec.title,
              author = spec.author,
              fileName = spec.displayName,
              format = spec.format,
              updatedAt = updatedAt,
            )
          books += entry
          Result.success(entry)
        },
        nowProvider = { 10_000L },
      )

    val firstCount = seeder.seedIfNeeded()
    assertEquals(2, firstCount)
    assertTrue(seeded)
    assertEquals(listOf("Welcome to Inkify", "Adaptive PDF Reflow Demo"), importedTitles)
    assertEquals(10_000L, books[0].updatedAt)
    assertEquals(9_000L, books[1].updatedAt)

    books.clear()
    importedTitles.clear()
    val secondCount = seeder.seedIfNeeded()
    assertEquals(0, secondCount)
    assertTrue(importedTitles.isEmpty())
  }

  @Test
  fun skipsSeedingAndMarksFlagWhenLibraryAlreadyHasBooks() = runTest {
    var seeded = false
    var importCalls = 0
    val seeder =
      SampleBooksSeeder(
        isAlreadySeeded = { seeded },
        markSeeded = { seeded = true },
        awaitLibraryLoaded = {},
        hasAnyBooks = { true },
        importSampleAsset = { _, _ ->
          importCalls++
          Result.failure(AssertionError("Should not import"))
        },
      )

    val count = seeder.seedIfNeeded()
    assertEquals(0, count)
    assertTrue(seeded)
    assertEquals(0, importCalls)
  }

  @Test
  fun leavesSeedFlagUnsetWhenAllSampleImportsFail() = runTest {
    var seeded = false
    val seeder =
      SampleBooksSeeder(
        isAlreadySeeded = { seeded },
        markSeeded = { seeded = true },
        awaitLibraryLoaded = {},
        hasAnyBooks = { false },
        importSampleAsset = { _, _ -> Result.failure(IllegalStateException("io error")) },
      )

    val count = seeder.seedIfNeeded()
    assertEquals(0, count)
    assertFalse(seeded)
  }

  @Test(expected = CancellationException::class)
  fun rethrowsCancellationExceptionDuringSeeding() = runTest {
    val seeder =
      SampleBooksSeeder(
        isAlreadySeeded = { false },
        markSeeded = {},
        awaitLibraryLoaded = {},
        hasAnyBooks = { false },
        importSampleAsset = { _, _ -> throw CancellationException("cancelled") },
      )

    seeder.seedIfNeeded()
  }
}
