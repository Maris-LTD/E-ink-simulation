package com.example.emptyapp.ui.reader

import com.example.emptyapp.reader.pdf.PdfPageAnalysis
import com.example.emptyapp.reader.pdf.PdfPageAnalyzer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnalysisSessionTest {

  @Test
  fun opensSingleDocumentLazilyAndCachesPageAnalyses() = runTest {
    val openCount = AtomicInteger(0)
    val analyzeCalls = mutableListOf<Int>()
    val closeCount = AtomicInteger(0)

    val session =
      PdfAnalysisSession(
        pageCount = 5,
        openDocument = {
          openCount.incrementAndGet()
          object : PdfAnalysisDocument {
            override fun analyzePage(pageIndex: Int): PdfPageAnalysis {
              analyzeCalls += pageIndex
              return sampleAnalysis(pageIndex)
            }

            override fun close() {
              closeCount.incrementAndGet()
            }
          }
        },
        ioDispatcher = Dispatchers.Default,
      )

    assertEquals(0, openCount.get())
    assertNull(session.cachedAnalysis(2))

    val first = session.getOrAnalyzePage(2)
    assertNotNull(first)
    assertEquals(1, openCount.get())
    assertEquals(listOf(2), analyzeCalls)
    assertSame(first, session.cachedAnalysis(2))

    val cachedAgain = session.getOrAnalyzePage(2)
    assertSame(first, cachedAgain)
    assertEquals(1, openCount.get())
    assertEquals(listOf(2), analyzeCalls)

    session.prefetchAdjacentAnalyses(2)
    assertEquals(1, openCount.get())
    assertEquals(listOf(2, 3, 1), analyzeCalls)
    assertNotNull(session.cachedAnalysis(1))
    assertNotNull(session.cachedAnalysis(3))

    session.close().join()
    assertEquals(1, closeCount.get())
    assertNull(session.cachedAnalysis(2))
    assertNull(session.getOrAnalyzePage(4))
    assertEquals(1, openCount.get())
  }

  @Test
  fun seedAnalysesPopulatesCacheWithoutOpeningDocument() = runTest {
    val openCount = AtomicInteger(0)
    val session =
      PdfAnalysisSession(
        pageCount = 3,
        openDocument = {
          openCount.incrementAndGet()
          null
        },
      )

    val page1 = sampleAnalysis(1)
    val beforeVersion = session.analysisVersion.value
    session.seedAnalyses(listOf(page1))

    assertTrue(session.analysisVersion.value > beforeVersion)
    assertSame(page1, session.cachedAnalysis(1))
    assertSame(page1, session.getOrAnalyzePage(1))
    assertEquals(0, openCount.get())
    session.close().join()
  }

  @Test
  fun serializesAnalysisAndCloseThroughSingleConcurrencyDispatcher() = runBlocking {
    val activeAnalyze = AtomicInteger(0)
    val maxConcurrentAnalyze = AtomicInteger(0)
    val closedDuringAnalyze = AtomicBoolean(false)
    val closed = AtomicBoolean(false)

    val session =
      PdfAnalysisSession(
        pageCount = 6,
        openDocument = {
          object : PdfAnalysisDocument {
            override fun analyzePage(pageIndex: Int): PdfPageAnalysis {
              val concurrent = activeAnalyze.incrementAndGet()
              maxConcurrentAnalyze.updateAndGet { maxOf(it, concurrent) }
              Thread.sleep(20)
              activeAnalyze.decrementAndGet()
              return sampleAnalysis(pageIndex)
            }

            override fun close() {
              if (activeAnalyze.get() > 0) closedDuringAnalyze.set(true)
              closed.set(true)
            }
          }
        },
        ioDispatcher = Dispatchers.IO,
      )

    val jobs = (0 until 5).map { idx -> async(Dispatchers.Default) { session.getOrAnalyzePage(idx) } }
    jobs.awaitAll()
    session.close().join()

    assertEquals(1, maxConcurrentAnalyze.get())
    assertEquals(false, closedDuringAnalyze.get())
    assertTrue(closed.get())
  }

  @Test
  fun documentOpenFailureReturnsEmptyAnalysisInsteadOfHangingGuidedNavigation() = runTest {
    val session =
      PdfAnalysisSession(
        pageCount = 3,
        openDocument = { null },
      )

    val fallback = session.getOrAnalyzePage(0)
    assertEquals(PdfPageAnalyzer.emptyPageAnalysis(0), fallback)
    assertEquals(fallback, session.cachedAnalysis(0))
    session.close().join()
  }

  @Test(expected = CancellationException::class)
  fun rethrowsCancellationFromOpenDocument() = runTest {
    val session =
      PdfAnalysisSession(
        pageCount = 3,
        openDocument = { throw CancellationException("cancelled") },
      )
    session.getOrAnalyzePage(0)
  }

  @Test
  fun loadsPageFromDiskCacheWithoutOpeningDocumentAndFallsBackOnMiss() = runTest {
    val openCount = AtomicInteger(0)
    val analyzedPages = mutableListOf<Int>()
    val session =
      PdfAnalysisSession(
        pageCount = 4,
        openDocument = {
          openCount.incrementAndGet()
          object : PdfAnalysisDocument {
            override fun analyzePage(pageIndex: Int): PdfPageAnalysis {
              analyzedPages += pageIndex
              return sampleAnalysis(pageIndex)
            }

            override fun close() = Unit
          }
        },
        loadCachedPage = { pageIndex -> if (pageIndex == 1) sampleAnalysis(1).copy(charCount = 777) else null },
      )

    val fromCache = session.getOrAnalyzePage(1)
    assertEquals(777, fromCache?.charCount)
    assertEquals(0, openCount.get())

    val fromFallback = session.getOrAnalyzePage(2)
    assertEquals(120, fromFallback?.charCount)
    assertEquals(1, openCount.get())
    assertEquals(listOf(2), analyzedPages)
    session.close().join()
  }

  private fun sampleAnalysis(pageIndex: Int): PdfPageAnalysis =
    PdfPageAnalyzer.emptyPageAnalysis(pageIndex).copy(charCount = 120)
}
