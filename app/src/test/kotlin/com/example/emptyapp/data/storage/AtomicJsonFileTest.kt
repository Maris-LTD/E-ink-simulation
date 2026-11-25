package com.example.emptyapp.data.storage

import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AtomicJsonFileTest {
  private lateinit var dir: File
  private lateinit var file: File
  private val serializer = ListSerializer(String.serializer())

  @Before
  fun setUp() {
    dir = createTempDirectory().toFile()
    file = File(dir, "data.json")
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  @Test
  fun missingFileReturnsDefault() {
    assertEquals(listOf("x"), AtomicJsonFile(file, serializer).read(listOf("x")))
  }

  @Test
  fun writeOverwritesExistingFileAndLeavesNoTemp() {
    val syncedDirs = mutableListOf<File?>()
    val store = AtomicJsonFile(file, serializer, directorySync = { syncedDirs += it })
    store.write(listOf("a"))
    store.write(listOf("b", "c"))

    assertEquals(listOf("b", "c"), store.read(emptyList()))
    assertFalse(File(dir, "data.json.tmp").exists())
    assertEquals(listOf<File?>(dir, dir), syncedDirs)
  }

  @Test
  fun corruptFileIsKeptAsBackupAndNotOverwritten() {
    file.writeText("[\"a\", \"b\"")
    val store = AtomicJsonFile(file, serializer, clock = { 42L })

    val loaded = store.read(emptyList())
    store.write(listOf("new"))

    assertEquals(emptyList<String>(), loaded)
    val backup = File(dir, "data.json.corrupt-42")
    assertTrue(backup.exists())
    assertEquals("[\"a\", \"b\"", backup.readText())
    assertEquals(listOf("new"), store.read(emptyList()))
  }

  @Test
  fun readRecoversValidTempFileWhenPrimaryMissing() {
    val tempFile = File(dir, "data.json.tmp")
    tempFile.writeText("""["recovered","ok"]""")
    val syncedDirs = mutableListOf<File?>()
    val store = AtomicJsonFile(file, serializer, directorySync = { syncedDirs += it })

    val loaded = store.read(emptyList())

    assertEquals(listOf("recovered", "ok"), loaded)
    assertTrue(file.exists())
    assertFalse(tempFile.exists())
    assertEquals(listOf<File?>(dir), syncedDirs)
    assertEquals(listOf("recovered", "ok"), AtomicJsonFile(file, serializer).read(emptyList()))
  }

  @Test
  fun readReturnsDefaultWhenPrimaryMissingAndTempIsCorrupt() {
    File(dir, "data.json.tmp").writeText("[\"broken\"")
    val store = AtomicJsonFile(file, serializer)

    val loaded = store.read(listOf("fallback"))

    assertEquals(listOf("fallback"), loaded)
    assertFalse(file.exists())
  }

  @Test
  fun readQuarantinesFileOnReadIoErrorAndAllowsLaterWrite() {
    file.writeText("""["preserved"]""")
    val syncedDirs = mutableListOf<File?>()
    var failRead = true
    val store =
      AtomicJsonFile(
        file = file,
        serializer = serializer,
        clock = { 99L },
        textReader = { target ->
          if (failRead) throw IOException("EIO read failure")
          target.readText()
        },
        directorySync = { syncedDirs += it },
      )

    val loaded = store.read(listOf("default"))

    assertEquals(listOf("default"), loaded)
    assertFalse(file.exists())
    val backup = File(dir, "data.json.corrupt-99")
    assertTrue(backup.exists())
    assertEquals("""["preserved"]""", backup.readText())
    assertEquals(listOf<File?>(dir), syncedDirs)

    failRead = false
    store.write(listOf("fresh"))
    assertEquals(listOf("fresh"), store.read(emptyList()))
  }

  @Test
  fun writeRefusesToOverwriteWhenCorruptFileCouldNotBeQuarantined() {
    file.writeText("""["important"]""")
    val blockerDir = File(dir, "data.json.corrupt-42").apply {
      mkdirs()
      File(this, "lock").writeText("block")
    }
    var failRead = true
    val store =
      AtomicJsonFile(
        file = file,
        serializer = serializer,
        clock = { 42L },
        textReader = { target ->
          if (failRead) throw IOException("unreadable")
          target.readText()
        },
      )

    val loaded = store.read(emptyList())
    assertEquals(emptyList<String>(), loaded)
    assertTrue(file.exists())

    val writeFailure = runCatching { store.write(listOf("wiped")) }.exceptionOrNull()
    assertTrue(writeFailure is IOException)
    assertEquals("""["important"]""", file.readText())
    assertFalse(File(dir, "data.json.tmp").exists())

    blockerDir.deleteRecursively()
    failRead = false
    store.write(listOf("updated"))

    assertEquals("""["important"]""", File(dir, "data.json.corrupt-42").readText())
    assertEquals(listOf("updated"), store.read(emptyList()))
  }

  @Test
  fun writeWithoutSyncToDiskSkipsDirectorySyncWhileRemainingAtomic() {
    val syncedDirs = mutableListOf<File?>()
    val store =
      AtomicJsonFile(
        file = file,
        serializer = serializer,
        directorySync = { syncedDirs += it },
      )

    store.write(listOf("fast"), syncToDisk = false)
    assertEquals(emptyList<File?>(), syncedDirs)
    assertEquals(listOf("fast"), store.read(emptyList()))

    store.write(listOf("durable"), syncToDisk = true)
    assertEquals(listOf<File?>(dir), syncedDirs)
    assertEquals(listOf("durable"), store.read(emptyList()))
  }
}
