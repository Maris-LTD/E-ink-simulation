package com.example.emptyapp.reader.pdf

import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs

object PdfPageAnalyzer {
  private const val BODY_TOP_MIN = 0.08f
  private const val BODY_BOTTOM_MAX = 0.92f
  private const val MIN_LINES_FOR_COLUMN_CHECK = 6

  fun analyzeAllPages(
    file: File,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
  ): List<PdfPageAnalysis> {
    if (!file.exists()) return emptyList()
    return runCatching {
      PDDocument.load(file).use { document ->
        val total = document.numberOfPages
        val results = ArrayList<PdfPageAnalysis>(total)
        for (index in 0 until total) {
          results += analyzePage(document, index)
          onProgress(index + 1, total)
        }
        results
      }
    }.getOrDefault(emptyList())
  }

  fun analyzePages(file: File, pageIndices: List<Int>): List<PdfPageAnalysis> {
    if (!file.exists() || pageIndices.isEmpty()) return emptyList()
    return runCatching {
      PDDocument.load(file).use { document ->
        val total = document.numberOfPages
        pageIndices
          .asSequence()
          .filter { it in 0 until total }
          .distinct()
          .map { index -> analyzePage(document, index) }
          .toList()
      }
    }.getOrDefault(emptyList())
  }

  fun analyzePage(document: PDDocument, pageIndex: Int): PdfPageAnalysis {
    val page = document.getPage(pageIndex)
    val mediaBox = page.cropBox ?: page.mediaBox
    val pageWidth = mediaBox.width.coerceAtLeast(1f)
    val pageHeight = mediaBox.height.coerceAtLeast(1f)

    val textLines = extractTextLines(document, pageIndex, pageWidth, pageHeight)
    val graphicsStats = extractGraphicsStats(page, mediaBox.lowerLeftX, mediaBox.lowerLeftY, pageWidth, pageHeight)
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

  private fun extractTextLines(
    document: PDDocument,
    pageIndex: Int,
    pageWidth: Float,
    pageHeight: Float,
  ): List<PdfTextLine> {
    val collected = ArrayList<PdfTextLine>()
    val stripper =
      object : PDFTextStripper() {
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
    stripper.sortByPosition = true
    stripper.startPage = pageIndex + 1
    stripper.endPage = pageIndex + 1
    runCatching { stripper.getText(document) }
    return collected
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

        private fun flushStrokedPath() {
          vectorOps++
          vectorSegments.addAll(pendingSegments)
          for (rect in pendingRects) {
            vectorSegments += PdfVectorSegment(rect.left, rect.top, rect.right, rect.top)
            vectorSegments += PdfVectorSegment(rect.left, rect.bottom, rect.right, rect.bottom)
            vectorSegments += PdfVectorSegment(rect.left, rect.top, rect.left, rect.bottom)
            vectorSegments += PdfVectorSegment(rect.right, rect.top, rect.right, rect.bottom)
          }
        }

        private fun flushFilledPath() {
          if (isNearWhiteFill()) return
          for (rect in pendingRects) {
            when {
              rect.height <= 0.008f && rect.width >= 0.05f -> {
                val midY = (rect.top + rect.bottom) * 0.5f
                vectorSegments += PdfVectorSegment(rect.left, midY, rect.right, midY)
              }
              rect.width <= 0.008f && rect.height >= 0.02f -> {
                val midX = (rect.left + rect.right) * 0.5f
                vectorSegments += PdfVectorSegment(midX, rect.top, midX, rect.bottom)
              }
              rect.area > 0.0005f -> {
                filledRects += PdfFilledRect(rect)
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
              vectorSegments += PdfVectorSegment(minX, midY, maxX, midY)
            } else if (w <= 0.008f && h >= 0.02f) {
              val midX = (minX + maxX) * 0.5f
              vectorSegments += PdfVectorSegment(midX, minY, midX, maxY)
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
