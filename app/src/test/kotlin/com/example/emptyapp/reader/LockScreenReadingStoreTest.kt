package com.example.emptyapp.reader

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LockScreenReadingStoreTest {
  private lateinit var dir: File
  private lateinit var file: File

  @Before
  fun setUp() {
    dir = createTempDirectory("lockscreen_store_test").toFile()
    file = File(dir, "lockscreen_reading.json")
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  @Test
  fun updateSessionClampsIndexPersistsCapturedAtAndReloads() {
    var now = 1_700_000_000_000L
    val store =
      LockScreenReadingStore(
        file = file,
        ioDispatcher = Dispatchers.Unconfined,
        nowProvider = { now },
      )

    store.updateSession(
      LockScreenReadingState(
        bookId = "book-1",
        title = "Sách 1",
        author = "Tác giả",
        snippets = listOf("Đoạn 1", "Đoạn 2", "Đoạn 3"),
        currentSnippetIndex = 99,
        progress = 0.4f,
        locatorJson = """{"href":"chap1.xhtml","locations":{"progression":0.2}}""",
        capturedAt = 123_456L,
      )
    )

    val state = store.state.value
    assertNotNull(state)
    assertEquals(2, state?.currentSnippetIndex)
    assertEquals("Đoạn 3", state?.currentSnippet)
    assertEquals(123_456L, state?.capturedAt)
    assertTrue(file.exists())

    val reloaded =
      LockScreenReadingStore(
        file = file,
        ioDispatcher = Dispatchers.Unconfined,
        nowProvider = { now },
      )
    assertEquals(state, reloaded.state.value)
  }

  @Test
  fun stepSnippetReturnsNullAtBoundariesAndWhenSnippetsEmpty() {
    var now = 1_000L
    val store =
      LockScreenReadingStore(
        file = file,
        ioDispatcher = Dispatchers.Unconfined,
        nowProvider = { now },
      )

    assertNull(store.stepSnippet(1))

    store.updateSession(
      LockScreenReadingState(
        bookId = "book-1",
        title = "Sách 1",
        snippets = emptyList(),
        currentSnippetIndex = 0,
        capturedAt = 500L,
      )
    )
    assertNull(store.stepSnippet(1))
    assertNull(store.stepSnippet(-1))

    store.updateSession(
      LockScreenReadingState(
        bookId = "book-1",
        title = "Sách 1",
        snippets = listOf("A", "B", "C"),
        currentSnippetIndex = 0,
        locatorJson = """{"href":"chap1.xhtml","locations":{"progression":0.0}}""",
        capturedAt = 600L,
      )
    )

    // At first snippet, stepping backward returns null and does not overwrite capturedAt
    now = 2_000L
    assertNull(store.stepSnippet(-1))
    assertEquals(0, store.state.value?.currentSnippetIndex)
    assertEquals(600L, store.state.value?.capturedAt)

    // Stepping forward updates index, progression, and capturedAt
    now = 3_000L
    val stepped1 = store.stepSnippet(1)
    assertNotNull(stepped1)
    assertEquals(1, stepped1?.currentSnippetIndex)
    assertEquals("B", stepped1?.currentSnippet)
    assertEquals(3_000L, stepped1?.capturedAt)
    assertTrue(stepped1?.locatorJson.orEmpty().contains("\"progression\":0.5"))

    now = 4_000L
    val stepped2 = store.stepSnippet(1)
    assertNotNull(stepped2)
    assertEquals(2, stepped2?.currentSnippetIndex)
    assertEquals("C", stepped2?.currentSnippet)
    assertEquals(4_000L, stepped2?.capturedAt)
    assertTrue(stepped2?.locatorJson.orEmpty().contains("\"progression\":1.0"))

    // At last snippet, stepping forward returns null and keeps state unchanged
    now = 5_000L
    assertNull(store.stepSnippet(1))
    assertEquals(2, store.state.value?.currentSnippetIndex)
    assertEquals(4_000L, store.state.value?.capturedAt)
  }

  @Test
  fun clearDeletesPersistedSessionAndDoesNotResurrectAfterUpdate() {
    val store =
      LockScreenReadingStore(
        file = file,
        ioDispatcher = Dispatchers.Unconfined,
        nowProvider = { 10_000L },
      )

    store.updateSession(
      LockScreenReadingState(
        bookId = "book-1",
        title = "Sách 1",
        snippets = listOf("Đoạn 1"),
        capturedAt = 10_000L,
      )
    )
    assertTrue(file.exists())

    store.clear()
    assertNull(store.state.value)
    assertFalse(file.exists())

    val reloaded =
      LockScreenReadingStore(
        file = file,
        ioDispatcher = Dispatchers.Unconfined,
      )
    assertNull(reloaded.state.value)
  }
}
