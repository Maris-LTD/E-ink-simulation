package com.example.emptyapp.data.sync

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncTombstoneStoreTest {
  private lateinit var dir: File
  private lateinit var file: File

  @Before
  fun setUp() {
    dir = createTempDirectory("tombstone_store_test").toFile()
    file = File(dir, "sync_tombstones.json")
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  @Test
  fun writeIoExceptionDoesNotCrashKeepsDirtyAndFlushesOnNoopMutate() {
    val store = SyncTombstoneStore(file, clock = { 500L })
    file.mkdirs()
    File(file, "blocker").writeText("x")

    store.markBooks(listOf("hash-1"), deleted = true)

    assertEquals(setOf("hash-1"), store.deletedBookHashes())
    assertTrue(store.lastWriteError.value)

    file.deleteRecursively()
    // Repeat exact same call (next == data); dirty flag ensures write still executes.
    store.markBooks(listOf("hash-1"), deleted = true)

    assertFalse(store.lastWriteError.value)
    val reloaded = SyncTombstoneStore(file, clock = { 500L })
    assertEquals(setOf("hash-1"), reloaded.deletedBookHashes())
  }
}
