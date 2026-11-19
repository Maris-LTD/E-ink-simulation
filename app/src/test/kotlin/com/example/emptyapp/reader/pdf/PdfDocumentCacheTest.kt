package com.example.emptyapp.reader.pdf

import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PdfDocumentCacheTest {
  private lateinit var tempDir: File
  private lateinit var pdfFile: File
  private lateinit var cacheDir: File

  @Before
  fun setUp() {
    PdfDocumentCache.clearInMemoryCache()
    tempDir = Files.createTempDirectory("pdf_doc_cache_unit").toFile()
    pdfFile = File(tempDir, "technical-manual.pdf").apply { writeText("dummy-pdf-content-v4") }
    cacheDir = File(tempDir, "pdf_cache")
  }

  @After
  fun tearDown() {
    PdfDocumentCache.clearInMemoryCache()
    tempDir.deleteRecursively()
  }

  @Test
  fun saves120PagesIntoThreeAtomicChunksWithoutLeavingTempFiles() {
    val pages = (0 until 120).map { samplePage(it) }
    val cache = PdfDocumentCache(cacheDir)

    cache.save(pdfFile, pages)

    val entryDirs = cacheDir.listFiles()?.filter { it.isDirectory }.orEmpty()
    assertEquals(1, entryDirs.size)
    val entryDir = entryDirs.single()
    val files = entryDir.listFiles()?.map { it.name }?.sorted().orEmpty()
    assertEquals(
      listOf("chunk_0000.json", "chunk_0001.json", "chunk_0002.json", "manifest.json"),
      files,
    )
    assertFalse(files.any { it.endsWith(".tmp") })

    val manifest = cache.loadManifest(pdfFile)
    assertNotNull(manifest)
    assertEquals(PdfDocumentCache.CURRENT_SCHEMA_VERSION, manifest?.schemaVersion)
    assertEquals(120, manifest?.pageCount)
    assertEquals(50, manifest?.chunkSize)
    assertEquals(3, manifest?.chunkCount)

    PdfDocumentCache.clearInMemoryCache()
    val reloaded = cache.load(pdfFile)
    assertEquals(120, reloaded?.size)
    assertEquals("Page 0 body", reloaded?.get(0)?.textLines?.first()?.text)
    assertEquals("Page 119 body", reloaded?.get(119)?.textLines?.first()?.text)
  }

  @Test
  fun loadPagesReadsOnlyRequestedChunksAndReusesProcessInMemoryLruCache() {
    val pages = (0 until 120).map { samplePage(it) }
    PdfDocumentCache(cacheDir).save(pdfFile, pages)
    PdfDocumentCache.clearInMemoryCache()

    val firstDiskReads = mutableListOf<String>()
    val readerCache =
      PdfDocumentCache(
        cacheDir = cacheDir,
        textReader = { file ->
          firstDiskReads += file.name
          file.readText()
        },
      )

    val loaded = readerCache.loadPages(pdfFile, listOf(52, 53))
    assertEquals(setOf(52, 53), loaded.keys)
    assertEquals("Page 52 body", loaded[52]?.textLines?.first()?.text)
    assertEquals("Page 53 body", loaded[53]?.textLines?.first()?.text)
    assertEquals(listOf("manifest.json", "chunk_0001.json"), firstDiskReads)

    val secondDiskReads = mutableListOf<String>()
    val secondInstance =
      PdfDocumentCache(
        cacheDir = cacheDir,
        textReader = { file ->
          secondDiskReads += file.name
          file.readText()
        },
      )
    val warmLoaded = secondInstance.loadPages(pdfFile, listOf(54, 99))
    assertEquals(setOf(54, 99), warmLoaded.keys)
    assertTrue("Expected process-wide LRU hit with zero disk reads, got $secondDiskReads", secondDiskReads.isEmpty())

    PdfDocumentCache.clearInMemoryCache()
    val boundaryReads = mutableListOf<String>()
    val boundaryInstance =
      PdfDocumentCache(
        cacheDir = cacheDir,
        textReader = { file ->
          boundaryReads += file.name
          file.readText()
        },
      )
    val boundaryPages = boundaryInstance.loadPages(pdfFile, listOf(49, 50))
    assertEquals(setOf(49, 50), boundaryPages.keys)
    assertEquals(listOf("manifest.json", "chunk_0000.json", "chunk_0001.json"), boundaryReads)
  }

  @Test
  fun invalidatesAndCleansUpOlderSchemaVersionAndLegacySingleFile() {
    cacheDir.mkdirs()
    val legacyFile = File(cacheDir, "${pdfFile.name}.${pdfFile.length()}.json")
    legacyFile.writeText(
      Json.encodeToString(
        CachedPdfDocument.serializer(),
        CachedPdfDocument(
          schemaVersion = 2,
          fileSize = pdfFile.length(),
          lastModified = pdfFile.lastModified(),
          pageCount = 1,
          pages = listOf(samplePage(0)),
        ),
      ),
    )

    val cache = PdfDocumentCache(cacheDir)
    assertNull(cache.load(pdfFile))
    assertFalse(legacyFile.exists())

    cache.save(pdfFile, (0 until 60).map { samplePage(it) })
    val entryDir = cacheDir.listFiles()?.single { it.isDirectory }!!
    val manifestFile = File(entryDir, "manifest.json")
    val staleManifest =
      PdfCacheManifest(
        schemaVersion = PdfDocumentCache.CURRENT_SCHEMA_VERSION - 1,
        fileSize = pdfFile.length(),
        lastModified = pdfFile.lastModified(),
        pageCount = 60,
        chunkSize = 50,
        chunkCount = 2,
      )
    AtomicJsonFile(manifestFile, PdfCacheManifest.serializer()).write(staleManifest)
    PdfDocumentCache.clearInMemoryCache()

    assertTrue(cache.loadPages(pdfFile, listOf(0, 1)).isEmpty())
    assertFalse(entryDir.exists())
  }

  @Test
  fun invalidatesWhenLastModifiedOrFileSizeChanges() {
    val cache = PdfDocumentCache(cacheDir)
    cache.save(pdfFile, (0 until 55).map { samplePage(it) })
    assertNotNull(cache.load(pdfFile))

    val originalModified = pdfFile.lastModified()
    assertTrue(pdfFile.setLastModified(originalModified + 5_000L))

    assertNull(cache.load(pdfFile))
    assertTrue(cache.loadPages(pdfFile, listOf(0)).isEmpty())
    assertTrue(cacheDir.listFiles()?.none { it.isDirectory } == true)

    cache.save(pdfFile, (0 until 55).map { samplePage(it) })
    assertEquals(55, cache.load(pdfFile)?.size)

    pdfFile.writeText("dummy-pdf-content-v4-expanded")
    assertNull(cache.load(pdfFile))
  }

  @Test
  fun recoversValidAtomicTempChunkWhenPrimaryChunkIsMissing() {
    val cache = PdfDocumentCache(cacheDir)
    cache.save(pdfFile, (0 until 75).map { samplePage(it) })
    PdfDocumentCache.clearInMemoryCache()

    val entryDir = cacheDir.listFiles()?.single { it.isDirectory }!!
    val chunk1 = File(entryDir, "chunk_0001.json")
    val chunk1Tmp = File(entryDir, "chunk_0001.json.tmp")
    assertTrue(chunk1.renameTo(chunk1Tmp))
    assertFalse(chunk1.exists())

    val loaded = cache.loadPages(pdfFile, listOf(60))
    assertEquals("Page 60 body", loaded[60]?.textLines?.first()?.text)
    assertTrue(chunk1.exists())
    assertFalse(chunk1Tmp.exists())
  }

  @Test
  fun deleteRemovesChunkDirectoryLegacyFileAndInMemoryEntries() {
    val cache = PdfDocumentCache(cacheDir)
    cache.save(pdfFile, (0 until 120).map { samplePage(it) })
    val legacyFile = File(cacheDir, "${pdfFile.name}.${pdfFile.length()}.json")
    legacyFile.writeText("{}")

    cache.delete(pdfFile)

    assertTrue(cacheDir.listFiles().orEmpty().isEmpty())
    assertNull(cache.load(pdfFile))
    assertTrue(PdfDocumentCache(cacheDir).loadPages(pdfFile, listOf(10)).isEmpty())
  }

  private fun samplePage(pageIndex: Int): PdfPageAnalysis =
    PdfPageAnalysis(
      pageIndex = pageIndex,
      pageWidthPt = 595f,
      pageHeightPt = 842f,
      charCount = 120,
      textLines =
        listOf(
          PdfTextLine(
            text = "Page $pageIndex body",
            bounds = PdfRect(0.1f, 0.2f, 0.8f, 0.23f),
            fontSizePt = 11f,
          ),
        ),
      imageBlocks = emptyList(),
      totalImageAreaFraction = 0f,
      vectorOpCount = 0,
      columnCount = 1,
    )
}
