package com.example.emptyapp.ui.reader

import com.example.emptyapp.reader.pdf.PdfPageAnalysis
import com.example.emptyapp.reader.pdf.PdfPageAnalyzer
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface PdfAnalysisDocument : Closeable {
  fun analyzePage(pageIndex: Int): PdfPageAnalysis?
}

internal class PdfAnalysisSession(
  private val pageCount: Int,
  private val openDocument: () -> PdfAnalysisDocument?,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val analysisDispatcher: CoroutineDispatcher = ioDispatcher.limitedParallelism(1)
  private val analysisCache = ConcurrentHashMap<Int, PdfPageAnalysis>()
  private val mutableAnalysisVersion = MutableStateFlow(0)
  val analysisVersion: StateFlow<Int> = mutableAnalysisVersion.asStateFlow()

  @Volatile private var closed = false
  private var document: PdfAnalysisDocument? = null
  private var openAttempted = false

  fun seedAnalyses(pages: List<PdfPageAnalysis>) {
    if (closed || pages.isEmpty()) return
    var updated = false
    for (page in pages) {
      if (page.pageIndex in 0 until pageCount) {
        analysisCache[page.pageIndex] = page
        updated = true
      }
    }
    if (updated && !closed) {
      mutableAnalysisVersion.update { it + 1 }
    }
  }

  fun cachedAnalysis(pageIndex: Int): PdfPageAnalysis? {
    if (closed || pageIndex !in 0 until pageCount) return null
    return analysisCache[pageIndex]
  }

  suspend fun getOrAnalyzePage(pageIndex: Int): PdfPageAnalysis? {
    if (closed || pageIndex !in 0 until pageCount) return null
    analysisCache[pageIndex]?.let { return it }
    return withContext(analysisDispatcher) {
      if (closed || pageIndex !in 0 until pageCount) return@withContext null
      analysisCache[pageIndex]?.let { return@withContext it }
      val doc = ensureDocumentLocked()
      if (closed) return@withContext null
      val analyzed =
        if (doc != null) {
          try {
            doc.analyzePage(pageIndex) ?: PdfPageAnalyzer.emptyPageAnalysis(pageIndex)
          } catch (e: CancellationException) {
            throw e
          } catch (_: Throwable) {
            PdfPageAnalyzer.emptyPageAnalysis(pageIndex)
          }
        } else {
          PdfPageAnalyzer.emptyPageAnalysis(pageIndex)
        }
      if (!closed) {
        analysisCache[pageIndex] = analyzed
        mutableAnalysisVersion.update { it + 1 }
        analyzed
      } else {
        null
      }
    }
  }

  suspend fun prefetchAdjacentAnalyses(centerIndex: Int) {
    if (closed) return
    val neighbors = listOf(centerIndex + 1, centerIndex - 1)
    for (neighbor in neighbors) {
      currentCoroutineContext().ensureActive()
      if (closed) return
      if (neighbor !in 0 until pageCount) continue
      if (analysisCache.containsKey(neighbor)) continue
      getOrAnalyzePage(neighbor)
    }
  }

  fun close(): Job {
    if (closed) return Job().apply { complete() }
    closed = true
    analysisCache.clear()
    return CoroutineScope(analysisDispatcher + NonCancellable).launch {
      runCatching { document?.close() }
      document = null
    }
  }

  private fun ensureDocumentLocked(): PdfAnalysisDocument? {
    document?.let { return it }
    if (openAttempted || closed) return null
    openAttempted = true
    return try {
      openDocument()?.also { opened ->
        if (closed) {
          runCatching { opened.close() }
          return null
        }
        document = opened
      }
    } catch (e: CancellationException) {
      openAttempted = false
      throw e
    } catch (_: Throwable) {
      null
    }
  }
}
