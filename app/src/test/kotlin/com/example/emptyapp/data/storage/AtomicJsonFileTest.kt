package com.example.emptyapp.data.storage

import java.io.File
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
    val store = AtomicJsonFile(file, serializer)
    store.write(listOf("a"))
    store.write(listOf("b", "c"))

    assertEquals(listOf("b", "c"), store.read(emptyList()))
    assertFalse(File(dir, "data.json.tmp").exists())
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
}
