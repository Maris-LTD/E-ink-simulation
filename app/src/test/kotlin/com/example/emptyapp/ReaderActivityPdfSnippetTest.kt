package com.example.emptyapp

import com.example.emptyapp.reader.pdf.PdfDocumentCache
import com.example.emptyapp.reader.pdf.PdfPageAnalysis
import com.example.emptyapp.reader.pdf.PdfRect
import com.example.emptyapp.reader.pdf.PdfTextLine
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReaderActivityPdfSnippetTest {
  private lateinit var tempDir: File
  private lateinit var pdfFile: File
  private lateinit var cacheDir: File

  @Before
  fun setUp() {
    PdfDocumentCache.clearInMemoryCache()
    tempDir = Files.createTempDirectory("pdf_snippet_cache_test").toFile()
    pdfFile = File(tempDir, "book.pdf").apply { writeText("pdf-bytes") }
    cacheDir = File(tempDir, "pdf_cache")
  }

  @After
  fun tearDown() {
    PdfDocumentCache.clearInMemoryCache()
    tempDir.deleteRecursively()
  }

  @Test
  fun cachesSnippetsByBookIdAndPageAndReadsOnlyTargetChunkFromDisk() {
    val pages =
      (0 until 120).map { idx ->
        PdfPageAnalysis(
          pageIndex = idx,
          pageWidthPt = 595f,
          pageHeightPt = 842f,
          charCount = 120,
          textLines =
            listOf(
              PdfTextLine(
                text = "Lockscreen snippet page $idx with enough readable text to extract.",
                bounds = PdfRect(0.1f, 0.2f, 0.85f, 0.24f),
                fontSizePt = 11f,
              ),
            ),
          imageBlocks = emptyList(),
          totalImageAreaFraction = 0f,
          vectorOpCount = 0,
          columnCount = 1,
        )
      }
    PdfDocumentCache(cacheDir).save(pdfFile, pages)
    PdfDocumentCache.clearInMemoryCache()

    val diskReads = mutableListOf<String>()
    val trackingCache =
      PdfDocumentCache(
        cacheDir = cacheDir,
        textReader = { file ->
          diskReads += file.name
          file.readText()
        },
      )
    val snippetCache = PdfSnippetMemoryCache()
    val fallbackCalls = AtomicInteger(0)

    val (firstSnippets, firstIdx) =
      extractPdfLockScreenSnippets(
        bookId = "book-1",
        file = pdfFile,
        pageIdx = 75,
        totalPages = 120,
        snippetCache = snippetCache,
        documentCache = trackingCache,
        analyzeFallback = { _, _ ->
          fallbackCalls.incrementAndGet()
          emptyList()
        },
      )

    assertEquals(0, firstIdx)
    assertTrue(firstSnippets.any { it.contains("Lockscreen snippet page 75") })
    assertEquals(0, fallbackCalls.get())
    assertEquals(listOf("manifest.json", "chunk_0001.json"), diskReads)

    diskReads.clear()
    PdfDocumentCache.clearInMemoryCache()
    val (cachedSnippets, cachedIdx) =
      extractPdfLockScreenSnippets(
        bookId = "book-1",
        file = pdfFile,
        pageIdx = 75,
        totalPages = 120,
        snippetCache = snippetCache,
        documentCache = trackingCache,
        analyzeFallback = { _, _ ->
          fallbackCalls.incrementAndGet()
          emptyList()
        },
      )

    assertEquals(0, cachedIdx)
    assertEquals(firstSnippets, cachedSnippets)
    assertTrue("Expected snippet memory hit without disk reads, got $diskReads", diskReads.isEmpty())
    assertEquals(0, fallbackCalls.get())

    val (otherBookSnippets, _) =
      extractPdfLockScreenSnippets(
        bookId = "book-2",
        file = pdfFile,
        pageIdx = 75,
        totalPages = 120,
        snippetCache = snippetCache,
        documentCache = trackingCache,
        analyzeFallback = { _, _ ->
          fallbackCalls.incrementAndGet()
          emptyList()
        },
      )

    assertEquals(firstSnippets, otherBookSnippets)
    assertEquals(listOf("manifest.json", "chunk_0001.json"), diskReads)
    assertEquals(0, fallbackCalls.get())

    val missingPdf = File(tempDir, "uncached.pdf").apply { writeText("uncached") }
    val (fallbackSnippets, _) =
      extractPdfLockScreenSnippets(
        bookId = "book-3",
        file = missingPdf,
        pageIdx = 2,
        totalPages = 10,
        snippetCache = snippetCache,
        documentCache = trackingCache,
        analyzeFallback = { _, requested ->
          fallbackCalls.incrementAndGet()
          requested.map { idx -> pages[idx] }
        },
      )

    assertEquals(1, fallbackCalls.get())
    assertTrue(fallbackSnippets.any { it.contains("Lockscreen snippet page 2") })

    val (repeatFallbackSnippets, _) =
      extractPdfLockScreenSnippets(
        bookId = "book-3",
        file = missingPdf,
        pageIdx = 2,
        totalPages = 10,
        snippetCache = snippetCache,
        documentCache = trackingCache,
        analyzeFallback = { _, _ ->
          fallbackCalls.incrementAndGet()
          emptyList()
        },
      )

    assertEquals(1, fallbackCalls.get())
    assertEquals(fallbackSnippets, repeatFallbackSnippets)
  }

  @Test
  fun extractPdfLockScreenSnippetsAndWindowRespectProPageBoundary() {
    val pdfFile = File(tempDir, "gated.pdf").apply { writeText("pdf") }
    val snippetCache = PdfSnippetMemoryCache()
    val docCache = PdfDocumentCache(File(tempDir, "doc_cache_gated"))
    val pages =
      (0..6).map { idx ->
        PdfPageAnalysis(
          pageIndex = idx,
          pageWidthPt = 595f,
          pageHeightPt = 842f,
          charCount = 120,
          textLines =
            listOf(
              PdfTextLine(
                text = "Lockscreen gated page $idx paragraph content for testing.",
                bounds = PdfRect(0.1f, 0.2f, 0.85f, 0.24f),
                fontSizePt = 11f,
              ),
            ),
          imageBlocks = emptyList(),
          totalImageAreaFraction = 0f,
          vectorOpCount = 0,
          columnCount = 1,
        )
      }

    // Direct call on page 5 (>= 5) must be blocked when canAccessPage returns false
    val (blockedSnippets, _) =
      extractPdfLockScreenSnippets(
        bookId = "gated-book",
        file = pdfFile,
        pageIdx = 5,
        totalPages = 10,
        snippetCache = snippetCache,
        documentCache = docCache,
        analyzeFallback = { _, requested -> requested.map { pages[it] } },
        canAccessPage = { idx -> idx in 0 until 5 },
      )
    assertTrue(blockedSnippets.isEmpty())

    // Window starting at page 3 (0-based) with free 5-page limit (0..4) stops before page 5
    val window =
      extractPdfLockScreenWindow(
        bookId = "gated-book",
        file = pdfFile,
        startPageIdx = 3,
        totalPages = 10,
        snippetCache = snippetCache,
        documentCache = docCache,
        canAccessPage = { idx -> idx in 0 until 5 },
        analyzeFallback = { _, requested -> requested.map { pages[it] } },
        maxPages = 3,
      )
    assertTrue(window.snippets.any { it.contains("page 3") })
    assertTrue(window.snippets.any { it.contains("page 4") })
    assertTrue(window.snippets.none { it.contains("page 5") })
    assertEquals(listOf(3, 4), window.snippetPages?.distinct())
    assertEquals(0.3f, window.progressStart, 0.001f)
    assertEquals(0.5f, window.progressEnd, 0.001f)
  }
}
