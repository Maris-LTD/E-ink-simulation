package com.example.emptyapp.reader.pdf

import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageTree
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlinx.coroutines.CancellationException

object PdfPageAnalyzer {
  @Volatile private var initialized = false

  fun ensureInitialized(context: Context) {
    if (initialized) return
    synchronized(this) {
      if (!initialized) {
        PDFBoxResourceLoader.init(context.applicationContext)
        initialized = true
      }
    }
  }

  private const val BODY_TOP_MIN = 0.08f
  private const val BODY_BOTTOM_MAX = 0.92f
  private const val MIN_LINES_FOR_COLUMN_CHECK = 6
  private const val MAX_VECTOR_SEGMENTS_PER_PAGE = 400
  private const val MAX_FILLED_RECTS_PER_PAGE = 100
  private const val DEFAULT_PAGE_WIDTH_PT = 595f
  private const val DEFAULT_PAGE_HEIGHT_PT = 842f

  internal fun emptyPageAnalysis(
    pageIndex: Int,
    pageWidthPt: Float = DEFAULT_PAGE_WIDTH_PT,
    pageHeightPt: Float = DEFAULT_PAGE_HEIGHT_PT,
  ): PdfPageAnalysis =
    PdfPageAnalysis(
      pageIndex = pageIndex,
      pageWidthPt = pageWidthPt.coerceAtLeast(1f),
      pageHeightPt = pageHeightPt.coerceAtLeast(1f),
      charCount = 0,
      textLines = emptyList(),
      imageBlocks = emptyList(),
      totalImageAreaFraction = 0f,
      vectorOpCount = 0,
      columnCount = 1,
      vectorSegments = emptyList(),
      filledRects = emptyList(),
    )

  internal fun analyzePageSafely(
    pageIndex: Int,
    block: () -> PdfPageAnalysis,
  ): PdfPageAnalysis =
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      emptyPageAnalysis(pageIndex)
    }

  private class StopBatchException : RuntimeException()

  fun analyzeAllPages(
    file: File,
    shouldContinue: () -> Boolean = { true },
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
  ): List<PdfPageAnalysis> {
    if (!file.exists()) return emptyList()
    return try {
      PDDocument.load(file).use { document ->
        analyzeAllPages(document, shouldContinue, onProgress)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun analyzeAllPages(
    document: PDDocument,
    shouldContinue: () -> Boolean = { true },
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
  ): List<PdfPageAnalysis> {
    val total = document.numberOfPages
    if (total <= 0 || !shouldContinue()) return emptyList()
    val batchStripper =
      BatchLineStripper(
        shouldContinue = shouldContinue,
        onPageDone = { pageIndex ->
          val halfDone = ((pageIndex + 1) / 2).coerceIn(1, total)
          onProgress(halfDone, total)
        },
      )
    return analyzeAllPagesWithStripper(document, shouldContinue, onProgress, batchStripper)
  }

  internal fun analyzeAllPagesWithStripper(
    document: PDDocument,
    shouldContinue: () -> Boolean = { true },
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    batchStripper: BatchLineStripper,
  ): List<PdfPageAnalysis> {
    val total = document.numberOfPages
    if (total <= 0 || !shouldContinue()) return emptyList()

    val batchResult =
      try {
        batchStripper.extractAll(document)
      } catch (e: CancellationException) {
        throw e
      } catch (_: StopBatchException) {
        return emptyList()
      } catch (_: Throwable) {
        null
      }

    if (!shouldContinue()) return emptyList()

    val fallbackStripper by lazy(LazyThreadSafetyMode.NONE) { ReusableLineStripper() }
    val results = ArrayList<PdfPageAnalysis>(total)
    for (index in 0 until total) {
      if (!shouldContinue()) return emptyList()
      results +=
        analyzePageSafely(index) {
          val page = document.getPage(index)
          val mediaBox = page.cropBox ?: page.mediaBox
          val pageWidth = (mediaBox?.width ?: DEFAULT_PAGE_WIDTH_PT).coerceAtLeast(1f)
          val pageHeight = (mediaBox?.height ?: DEFAULT_PAGE_HEIGHT_PT).coerceAtLeast(1f)
          val originX = mediaBox?.lowerLeftX ?: 0f
          val originY = mediaBox?.lowerLeftY ?: 0f
          val textLines =
            if (batchResult == null || index in batchResult.failedPages) {
              fallbackStripper.extract(document, index, pageWidth, pageHeight)
            } else {
              batchResult.linesByPage[index].orEmpty()
            }
          buildPageAnalysis(
            page = page,
            pageIndex = index,
            originX = originX,
            originY = originY,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            textLines = textLines,
          )
        }
      val done =
        if (batchResult != null) {
          val textPhase = total / 2
          val graphicsPhase = ((index + 1) * (total - textPhase)) / total
          (textPhase + graphicsPhase).coerceIn(1, total)
        } else {
          index + 1
        }
      onProgress(done, total)
    }
    return results
  }

  fun analyzePages(file: File, pageIndices: List<Int>): List<PdfPageAnalysis> {
    if (!file.exists() || pageIndices.isEmpty()) return emptyList()
    return try {
      PDDocument.load(file).use { document ->
        analyzePages(document, pageIndices)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun analyzePages(document: PDDocument, pageIndices: List<Int>): List<PdfPageAnalysis> {
    if (pageIndices.isEmpty()) return emptyList()
    val total = document.numberOfPages
    val stripper = ReusableLineStripper()
    return pageIndices
      .asSequence()
      .filter { it in 0 until total }
      .distinct()
      .map { index -> analyzePageSafely(index) { analyzePage(document, index, stripper) } }
      .toList()
  }

  fun analyzeSamplePages(
    file: File,
    indicesForCount: (Int) -> List<Int>,
  ): List<PdfPageAnalysis> {
    if (!file.exists()) return emptyList()
    return try {
      PDDocument.load(file).use { document ->
        analyzePages(document, indicesForCount(document.numberOfPages))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun analyzePage(
    document: PDDocument,
    pageIndex: Int,
    stripper: ReusableLineStripper = ReusableLineStripper(),
  ): PdfPageAnalysis {
    val page = document.getPage(pageIndex)
    val mediaBox = page.cropBox ?: page.mediaBox
    val pageWidth = (mediaBox?.width ?: DEFAULT_PAGE_WIDTH_PT).coerceAtLeast(1f)
    val pageHeight = (mediaBox?.height ?: DEFAULT_PAGE_HEIGHT_PT).coerceAtLeast(1f)
    val originX = mediaBox?.lowerLeftX ?: 0f
    val originY = mediaBox?.lowerLeftY ?: 0f
    val textLines = stripper.extract(document, pageIndex, pageWidth, pageHeight, page)
    return buildPageAnalysis(
      page = page,
      pageIndex = pageIndex,
      originX = originX,
      originY = originY,
      pageWidth = pageWidth,
      pageHeight = pageHeight,
      textLines = textLines,
    )
  }

  private fun buildPageAnalysis(
    page: PDPage,
    pageIndex: Int,
    originX: Float,
    originY: Float,
    pageWidth: Float,
    pageHeight: Float,
    textLines: List<PdfTextLine>,
  ): PdfPageAnalysis {
    val graphicsStats = extractGraphicsStats(page, originX, originY, pageWidth, pageHeight)
    val charCount = textLines.sumOf { it.text.count { ch -> !ch.isWhitespace() } }
    val columnCount = detectColumnCount(textLines)

    return PdfPageAnalysis(
      pageIndex = pageIndex,
      pageWidthPt = pageWidth,
      pageHeightPt = pageHeight,
      charCount = charCount,
      textLines = textLines,
      imageBlocks = graphicsStats.images,
      totalImageAreaFraction = graphicsStats.images.sumOf { it.pageAreaFraction.toDouble() }.toFloat().coerceIn(0f, 1f),
      vectorOpCount = graphicsStats.vectorOpCount,
      columnCount = columnCount,
      vectorSegments = graphicsStats.vectorSegments,
      filledRects = graphicsStats.filledRects,
    )
  }

  internal fun detectColumnCount(lines: List<PdfTextLine>): Int {
    val bodyLines =
      lines.filter { line ->
        line.bounds.top >= BODY_TOP_MIN &&
          line.bounds.bottom <= BODY_BOTTOM_MAX &&
          line.text.trim().length >= 3
      }
    if (bodyLines.size < MIN_LINES_FOR_COLUMN_CHECK) return 1

    val leftOnly = bodyLines.count { it.bounds.right <= 0.49f }
    val rightOnly = bodyLines.count { it.bounds.left >= 0.51f }
    val spanningCenter = bodyLines.count { it.bounds.left < 0.42f && it.bounds.right > 0.58f }

    val total = bodyLines.size.toFloat()
    val twoColRatio = (leftOnly + rightOnly) / total
    val spanningRatio = spanningCenter / total

    if (leftOnly >= 3 && rightOnly >= 3 && twoColRatio >= 0.55f && spanningRatio < 0.30f) {
      val col1 = bodyLines.count { it.bounds.right <= 0.34f }
      val col2 = bodyLines.count { it.bounds.left >= 0.34f && it.bounds.right <= 0.66f }
      val col3 = bodyLines.count { it.bounds.left >= 0.66f }
      if (col1 >= 3 && col2 >= 3 && col3 >= 3 && (col1 + col2 + col3) / total >= 0.60f) {
        return 3
      }
      return 2
    }
    return 1
  }

  abstract class BaseLineStripper : PDFTextStripper() {
    protected var pageWidth = 1f
    protected var pageHeight = 1f
    protected var collected = ArrayList<PdfTextLine>()

    init {
      sortByPosition = true
    }

    override fun writeLineSeparator() {}

    override fun writeWordSeparator() {}

    override fun writePageStart() {}

    override fun writePageEnd() {}

    override fun writeParagraphStart() {}

    override fun writeParagraphEnd() {}

    override fun startArticle(isLTR: Boolean) {}

    override fun endArticle() {}

    override fun writeCharacters(text: TextPosition) {}

    override fun writeString(text: String) {}

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
      if (text.isBlank() || textPositions.isEmpty()) return
      val chunk = ArrayList<TextPosition>(textPositions.size)

      fun emitChunk() {
        if (chunk.isEmpty()) return
        val chunkText = chunk.joinToString("") { it.unicode.orEmpty() }.trim()
        if (chunkText.isEmpty()) {
          chunk.clear()
          return
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var fontSum = 0f
        for (pos in chunk) {
          val x = pos.xDirAdj
          val w = pos.widthDirAdj
          val h = pos.heightDir.coerceAtLeast(1f)
          val yBottom = pos.yDirAdj
          val yTop = yBottom - h
          if (x < minX) minX = x
          if (x + w > maxX) maxX = x + w
          if (yTop < minY) minY = yTop
          if (yBottom > maxY) maxY = yBottom
          fontSum += pos.fontSizeInPt
        }
        if (maxX > minX && maxY > minY) {
          val bounds =
            PdfRect(
              left = (minX / pageWidth).coerceIn(0f, 1f),
              top = (minY / pageHeight).coerceIn(0f, 1f),
              right = (maxX / pageWidth).coerceIn(0f, 1f),
              bottom = (maxY / pageHeight).coerceIn(0f, 1f),
            )
          collected +=
            PdfTextLine(
              text = chunkText,
              bounds = bounds,
              fontSizePt = fontSum / chunk.size,
            )
        }
        chunk.clear()
      }

      var prevNonSpace: TextPosition? = null
      for (pos in textPositions) {
        val isSpaceChar = pos.unicode.isNullOrBlank()
        val prev = prevNonSpace
        if (!isSpaceChar && prev != null) {
          val gap = pos.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
          val splitThreshold = maxOf(prev.widthOfSpace * 2.5f, prev.fontSizeInPt * 0.85f)
          if (gap > splitThreshold) {
            emitChunk()
          }
        }
        chunk += pos
        if (!isSpaceChar) {
          prevNonSpace = pos
        }
      }
      emitChunk()
    }
  }

  class ReusableLineStripper : BaseLineStripper() {
    private var targetPage: PDPage? = null

    fun extract(
      document: PDDocument,
      pageIndex: Int,
      pageWidth: Float,
      pageHeight: Float,
      page: PDPage? = null,
    ): List<PdfTextLine> {
      if (pageIndex !in 0 until document.numberOfPages) return emptyList()
      this.pageWidth = pageWidth
      this.pageHeight = pageHeight
      val out = ArrayList<PdfTextLine>()
      this.collected = out
      this.targetPage = page ?: document.getPage(pageIndex)
      startPage = 0
      endPage = 0
      try {
        getText(document)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
      } finally {
        targetPage = null
      }
      return out
    }

    override fun processPages(pages: PDPageTree) {
      val page = targetPage
      if (page != null) {
        if (page.hasContents()) {
          processPage(page)
        }
      } else {
        super.processPages(pages)
      }
    }
  }

  data class BatchExtractionResult(
    val linesByPage: Map<Int, List<PdfTextLine>>,
    val failedPages: Set<Int>,
  )

  open class BatchLineStripper(
    private val shouldContinue: () -> Boolean = { true },
    private val onPageDone: (pageIndex: Int) -> Unit = {},
  ) : BaseLineStripper() {
    private val linesByPage = HashMap<Int, List<PdfTextLine>>()
    private val failedPages = HashSet<Int>()
    private var activePageIndex = -1
    private var pageActive = false

    open fun extractAll(document: PDDocument): BatchExtractionResult {
      val total = document.numberOfPages
      if (total <= 0) return BatchExtractionResult(emptyMap(), emptySet())
      startPage = 1
      endPage = total
      getText(document)
      return BatchExtractionResult(HashMap(linesByPage), HashSet(failedPages))
    }

    fun snapshotLinesByPage(): Map<Int, List<PdfTextLine>> = HashMap(linesByPage)

    override fun startDocument(document: PDDocument) {
      linesByPage.clear()
      failedPages.clear()
      activePageIndex = -1
      pageActive = false
    }

    override fun processPage(page: PDPage) {
      if (!shouldContinue()) throw StopBatchException()
      val pageIndex = currentPageNo - 1
      try {
        super.processPage(page)
      } catch (e: CancellationException) {
        throw e
      } catch (e: StopBatchException) {
        throw e
      } catch (_: Throwable) {
        if (pageIndex >= 0) failedPages += pageIndex
        pageActive = false
        activePageIndex = -1
        collected = ArrayList()
      }
    }

    override fun startPage(page: PDPage) {
      if (!shouldContinue()) throw StopBatchException()
      val mediaBox = page.cropBox ?: page.mediaBox
      pageWidth = (mediaBox?.width ?: DEFAULT_PAGE_WIDTH_PT).coerceAtLeast(1f)
      pageHeight = (mediaBox?.height ?: DEFAULT_PAGE_HEIGHT_PT).coerceAtLeast(1f)
      activePageIndex = currentPageNo - 1
      collected = ArrayList()
      pageActive = activePageIndex >= 0
    }

    override fun endPage(page: PDPage) {
      if (pageActive && activePageIndex >= 0) {
        linesByPage[activePageIndex] = collected
        onPageDone(activePageIndex)
      }
      pageActive = false
      activePageIndex = -1
    }
  }

  private data class GraphicsStats(
    val images: List<PdfImageBlock>,
    val vectorOpCount: Int,
    val vectorSegments: List<PdfVectorSegment>,
    val filledRects: List<PdfFilledRect>,
  )

  private fun extractGraphicsStats(
    page: PDPage,
    originX: Float,
    originY: Float,
    pageWidth: Float,
    pageHeight: Float,
  ): GraphicsStats {
    val images = ArrayList<PdfImageBlock>()
    val vectorSegments = ArrayList<PdfVectorSegment>()
    val filledRects = ArrayList<PdfFilledRect>()
    var vectorOps = 0

    val engine =
      object : PDFGraphicsStreamEngine(page) {
        private var currentX = 0f
        private var currentY = 0f
        private var subpathStartX = 0f
        private var subpathStartY = 0f
        private val pendingSegments = ArrayList<PdfVectorSegment>()
        private val pendingRects = ArrayList<PdfRect>()

        private fun normalizePoint(x: Float, y: Float): PointF {
          val transformed = graphicsState.currentTransformationMatrix.transformPoint(x, y)
          val nx = ((transformed.x - originX) / pageWidth).coerceIn(0f, 1f)
          val ny = (1f - ((transformed.y - originY) / pageHeight)).coerceIn(0f, 1f)
          return PointF(nx, ny)
        }

        private fun isNearWhiteFill(): Boolean {
          val components = runCatching { graphicsState.nonStrokingColor?.components }.getOrNull() ?: return false
          if (components.isEmpty()) return false
          return when (components.size) {
            1 -> components[0] >= 0.96f
            3 -> components[0] >= 0.96f && components[1] >= 0.96f && components[2] >= 0.96f
            4 -> components[0] <= 0.04f && components[1] <= 0.04f && components[2] <= 0.04f && components[3] <= 0.04f
            else -> false
          }
        }

        private fun addVectorSegment(seg: PdfVectorSegment) {
          if (vectorSegments.size < MAX_VECTOR_SEGMENTS_PER_PAGE) {
            vectorSegments += seg
          }
        }

        private fun addFilledRect(rect: PdfRect) {
          if (filledRects.size < MAX_FILLED_RECTS_PER_PAGE) {
            filledRects += PdfFilledRect(rect)
          }
        }

        private fun flushStrokedPath() {
          vectorOps++
          for (seg in pendingSegments) {
            if (vectorSegments.size >= MAX_VECTOR_SEGMENTS_PER_PAGE) break
            vectorSegments += seg
          }
          for (rect in pendingRects) {
            if (vectorSegments.size >= MAX_VECTOR_SEGMENTS_PER_PAGE) break
            addVectorSegment(PdfVectorSegment(rect.left, rect.top, rect.right, rect.top))
            addVectorSegment(PdfVectorSegment(rect.left, rect.bottom, rect.right, rect.bottom))
            addVectorSegment(PdfVectorSegment(rect.left, rect.top, rect.left, rect.bottom))
            addVectorSegment(PdfVectorSegment(rect.right, rect.top, rect.right, rect.bottom))
          }
        }

        private fun flushFilledPath() {
          if (isNearWhiteFill()) return
          for (rect in pendingRects) {
            when {
              rect.height <= 0.008f && rect.width >= 0.05f -> {
                val midY = (rect.top + rect.bottom) * 0.5f
                addVectorSegment(PdfVectorSegment(rect.left, midY, rect.right, midY))
              }
              rect.width <= 0.008f && rect.height >= 0.02f -> {
                val midX = (rect.left + rect.right) * 0.5f
                addVectorSegment(PdfVectorSegment(midX, rect.top, midX, rect.bottom))
              }
              rect.area > 0.0005f -> {
                addFilledRect(rect)
              }
            }
          }
          if (pendingSegments.size in 3..6) {
            val minX = pendingSegments.minOf { it.minX }
            val maxX = pendingSegments.maxOf { it.maxX }
            val minY = pendingSegments.minOf { it.minY }
            val maxY = pendingSegments.maxOf { it.maxY }
            val w = maxX - minX
            val h = maxY - minY
            if (h <= 0.008f && w >= 0.05f) {
              val midY = (minY + maxY) * 0.5f
              addVectorSegment(PdfVectorSegment(minX, midY, maxX, midY))
            } else if (w <= 0.008f && h >= 0.02f) {
              val midX = (minX + maxX) * 0.5f
              addVectorSegment(PdfVectorSegment(midX, minY, midX, maxY))
            }
          }
        }

        private fun clearPending() {
          pendingSegments.clear()
          pendingRects.clear()
        }

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
          val pts = listOf(p0, p1, p2, p3).map { normalizePoint(it.x, it.y) }
          val rect =
            PdfRect(
              left = pts.minOf { it.x },
              top = pts.minOf { it.y },
              right = pts.maxOf { it.x },
              bottom = pts.maxOf { it.y },
            )
          if (rect.width > 0f && rect.height > 0f) {
            pendingRects += rect
          }
        }

        override fun drawImage(pdImage: PDImage) {
          val ctm = graphicsState.currentTransformationMatrix
          val corners =
            arrayOf(
              ctm.transformPoint(0f, 0f),
              ctm.transformPoint(1f, 0f),
              ctm.transformPoint(0f, 1f),
              ctm.transformPoint(1f, 1f),
            )
          val minX = corners.minOf { it.x } - originX
          val maxX = corners.maxOf { it.x } - originX
          val minY = corners.minOf { it.y } - originY
          val maxY = corners.maxOf { it.y } - originY

          val left = (minX / pageWidth).coerceIn(0f, 1f)
          val right = (maxX / pageWidth).coerceIn(0f, 1f)
          val top = (1f - (maxY / pageHeight)).coerceIn(0f, 1f)
          val bottom = (1f - (minY / pageHeight)).coerceIn(0f, 1f)
          val rect = PdfRect(left, top, right, bottom)
          if (rect.area > 0.002f) {
            images += PdfImageBlock(bounds = rect, pageAreaFraction = rect.area)
          }
        }

        override fun clip(windingRule: Path.FillType) {
          clearPending()
        }

        override fun moveTo(x: Float, y: Float) {
          currentX = x
          currentY = y
          subpathStartX = x
          subpathStartY = y
        }

        override fun lineTo(x: Float, y: Float) {
          val p0 = normalizePoint(currentX, currentY)
          val p1 = normalizePoint(x, y)
          pendingSegments += PdfVectorSegment(p0.x, p0.y, p1.x, p1.y)
          currentX = x
          currentY = y
        }

        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
          currentX = x3
          currentY = y3
        }

        override fun getCurrentPoint(): PointF = PointF(currentX, currentY)

        override fun closePath() {
          if (currentX != subpathStartX || currentY != subpathStartY) {
            val p0 = normalizePoint(currentX, currentY)
            val p1 = normalizePoint(subpathStartX, subpathStartY)
            pendingSegments += PdfVectorSegment(p0.x, p0.y, p1.x, p1.y)
            currentX = subpathStartX
            currentY = subpathStartY
          }
        }

        override fun endPath() {
          clearPending()
        }

        override fun strokePath() {
          flushStrokedPath()
          clearPending()
        }

        override fun fillPath(windingRule: Path.FillType) {
          flushFilledPath()
          clearPending()
        }

        override fun fillAndStrokePath(windingRule: Path.FillType) {
          flushFilledPath()
          flushStrokedPath()
          clearPending()
        }

        override fun shadingFill(shadingName: COSName) {}
      }

    runCatching { engine.processPage(page) }
    return GraphicsStats(
      images = images,
      vectorOpCount = vectorOps,
      vectorSegments = vectorSegments,
      filledRects = filledRects,
    )
  }
}
