package com.example.emptyapp.reader.pdf

import kotlin.math.abs

enum class ReflowImageKind {
  ILLUSTRATION,
  TABLE,
  SYMBOL_STACK,
}

sealed interface ReflowItem {
  val topFraction: Float

  data class Paragraph(
    val text: String,
    val isHeading: Boolean,
    override val topFraction: Float,
  ) : ReflowItem

  data class Image(
    val bounds: PdfRect,
    val pageIndex: Int,
    override val topFraction: Float,
    val kind: ReflowImageKind = ReflowImageKind.ILLUSTRATION,
  ) : ReflowItem
}

enum class ReflowFallbackReason {
  FULL_PAGE_IMAGE,
  COMPLEX_VECTOR_LAYOUT,
  NO_TEXT,
}

sealed interface ReflowPageResult {
  data class Reflowed(val items: List<ReflowItem>) : ReflowPageResult

  data class FallbackOriginal(val reason: ReflowFallbackReason) : ReflowPageResult
}

object PdfReflowEngine {
  private const val FULL_PAGE_IMAGE_THRESHOLD = 0.70f
  private const val MAX_CHARS_FOR_FULL_IMAGE_FALLBACK = 80
  private const val COMPLEX_VECTOR_THRESHOLD = 300
  private const val MAX_CHARS_FOR_VECTOR_FALLBACK = 250
  private const val HEADER_MAX_BOTTOM = 0.09f
  private const val FOOTER_MIN_TOP = 0.91f
  private const val MIN_IMAGE_AREA_FRACTION = 0.01f
  private const val MAX_INLINE_IMAGE_AREA_FRACTION = 0.85f

  private val PAGE_NUMBER_REGEX =
    Regex("""^\s*[-–—]?\s*\d{1,4}\s*[-–—]?\s*$|^\s*(page|trang)\s+\d+(\s*/\s*\d+)?\s*$""", RegexOption.IGNORE_CASE)

  fun buildReflowPage(
    analysis: PdfPageAnalysis,
    neighboringAnalyses: List<PdfPageAnalysis> = emptyList(),
  ): ReflowPageResult {
    if (analysis.totalImageAreaFraction > FULL_PAGE_IMAGE_THRESHOLD && analysis.charCount < MAX_CHARS_FOR_FULL_IMAGE_FALLBACK) {
      return ReflowPageResult.FallbackOriginal(ReflowFallbackReason.FULL_PAGE_IMAGE)
    }
    if (analysis.vectorOpCount > COMPLEX_VECTOR_THRESHOLD && analysis.charCount < MAX_CHARS_FOR_VECTOR_FALLBACK) {
      return ReflowPageResult.FallbackOriginal(ReflowFallbackReason.COMPLEX_VECTOR_LAYOUT)
    }
    if (analysis.charCount < 20 && analysis.imageBlocks.isEmpty()) {
      return ReflowPageResult.FallbackOriginal(ReflowFallbackReason.NO_TEXT)
    }

    val repeatingEdgeTexts = findRepeatingEdgeTexts(listOf(analysis) + neighboringAnalyses)
    val bodyLines =
      analysis.textLines
        .filter { line ->
          val trimmed = line.text.trim()
          if (trimmed.isEmpty()) return@filter false
          val inEdgeBand = line.bounds.bottom <= HEADER_MAX_BOTTOM || line.bounds.top >= FOOTER_MIN_TOP
          if (!inEdgeBand) return@filter true
          if (PAGE_NUMBER_REGEX.matches(trimmed)) return@filter false
          normalizeEdgeText(trimmed) !in repeatingEdgeTexts
        }
        .sortedWith(compareBy<PdfTextLine> { it.bounds.top }.thenBy { it.bounds.left })

    val masks = PdfGraphicMasker.detectMasks(analysis.copy(textLines = bodyLines))
    val isMultiColumn = analysis.columnCount >= 2
    val unmaskedLines =
      PdfGraphicMasker
        .filterMaskedLines(bodyLines, masks)
        .let { filtered ->
          if (isMultiColumn) {
            filtered.sortedWith(
              compareBy<PdfTextLine> { columnOrderBucket(it.bounds, analysis.columnCount) }
                .thenBy { it.bounds.top }
                .thenBy { it.bounds.left },
            )
          } else {
            filtered
          }
        }

    val paragraphs = mergeLinesIntoParagraphs(unmaskedLines)
    val maskedImages =
      masks.map { mask ->
        ReflowItem.Image(
          bounds = mask.bounds,
          pageIndex = analysis.pageIndex,
          topFraction = mask.bounds.top,
          kind =
            when (mask.kind) {
              PdfGraphicMaskKind.TABLE -> ReflowImageKind.TABLE
              PdfGraphicMaskKind.SYMBOL_STACK -> ReflowImageKind.SYMBOL_STACK
            },
        )
      }
    val images =
      analysis.imageBlocks
        .filter { block ->
          block.pageAreaFraction >= MIN_IMAGE_AREA_FRACTION &&
            (block.pageAreaFraction <= MAX_INLINE_IMAGE_AREA_FRACTION || analysis.charCount < MAX_CHARS_FOR_FULL_IMAGE_FALLBACK) &&
            masks.none { mask -> isCoveredBy(block.bounds, mask.bounds) }
        }
        .map { block ->
          ReflowItem.Image(
            bounds = block.bounds,
            pageIndex = analysis.pageIndex,
            topFraction = block.bounds.top,
            kind = ReflowImageKind.ILLUSTRATION,
          )
        }

    val orderedParagraphs =
      paragraphs.map { placed ->
        val bucket = if (isMultiColumn) columnOrderBucket(placed.firstLineBounds, analysis.columnCount) else 0
        OrderedItem(placed.paragraph, bucket)
      }
    val orderedImages =
      (images + maskedImages).map { image ->
        val bucket = if (isMultiColumn) columnOrderBucket(image.bounds, analysis.columnCount) else 0
        OrderedItem(image, bucket)
      }
    val items =
      (orderedParagraphs + orderedImages)
        .sortedWith(compareBy<OrderedItem> { it.columnBucket }.thenBy { it.item.topFraction })
        .map { it.item }
    if (items.isEmpty()) {
      return ReflowPageResult.FallbackOriginal(ReflowFallbackReason.NO_TEXT)
    }
    return ReflowPageResult.Reflowed(items)
  }

  private class OrderedItem(val item: ReflowItem, val columnBucket: Int)

  private class PlacedParagraph(val paragraph: ReflowItem.Paragraph, val firstLineBounds: PdfRect)

  private fun columnOrderBucket(b: PdfRect, columnCount: Int): Int {
    if (b.left < 0.38f && b.right > 0.62f) return 0
    val cx = (b.left + b.right) * 0.5f
    return if (columnCount >= 3) {
      when {
        cx < 0.34f -> 1
        cx < 0.66f -> 2
        else -> 3
      }
    } else {
      if (cx < 0.50f) 1 else 2
    }
  }

  private fun isCoveredBy(inner: PdfRect, outer: PdfRect): Boolean {
    val left = maxOf(inner.left, outer.left)
    val top = maxOf(inner.top, outer.top)
    val right = minOf(inner.right, outer.right)
    val bottom = minOf(inner.bottom, outer.bottom)
    if (right <= left || bottom <= top) return false
    val overlap = (right - left) * (bottom - top)
    return overlap / inner.area.coerceAtLeast(1e-6f) >= 0.6f
  }

  private fun findRepeatingEdgeTexts(pages: List<PdfPageAnalysis>): Set<String> {
    if (pages.size < 2) return emptySet()
    val counts = HashMap<String, Int>()
    for (page in pages.distinctBy { it.pageIndex }) {
      val edgeStrings =
        page.textLines
          .filter { it.bounds.bottom <= HEADER_MAX_BOTTOM || it.bounds.top >= FOOTER_MIN_TOP }
          .map { normalizeEdgeText(it.text) }
          .filter { it.isNotEmpty() }
          .toSet()
      for (s in edgeStrings) {
        counts[s] = (counts[s] ?: 0) + 1
      }
    }
    return counts.filterValues { it >= 2 }.keys
  }

  private fun normalizeEdgeText(raw: String): String =
    raw.trim().replace(Regex("""\d+"""), "#").lowercase()

  private fun mergeLinesIntoParagraphs(lines: List<PdfTextLine>): List<PlacedParagraph> {
    if (lines.isEmpty()) return emptyList()

    val sortedHeights = lines.map { it.bounds.height }.filter { it > 0f }.sorted()
    val medianHeight =
      if (sortedHeights.isNotEmpty()) sortedHeights[sortedHeights.size / 2] else 0.015f

    val sortedFontSizes = lines.map { it.fontSizePt }.filter { it > 0f }.sorted()
    val medianFontSize =
      if (sortedFontSizes.isNotEmpty()) sortedFontSizes[sortedFontSizes.size / 2] else 11f

    val lefts = lines.map { it.bounds.left }.sorted()
    val baseLeft = if (lefts.isNotEmpty()) lefts[(lefts.size * 0.15f).toInt().coerceIn(0, lefts.lastIndex)] else 0.1f

    val rights = lines.map { it.bounds.right }.sorted()
    val baseRight = if (rights.isNotEmpty()) rights[(rights.size * 0.85f).toInt().coerceIn(0, rights.lastIndex)] else 0.9f

    val paragraphs = ArrayList<PlacedParagraph>()
    val currentText = StringBuilder()
    var paragraphTop = lines.first().bounds.top
    var paragraphFirstBounds = lines.first().bounds
    var fontSizeSum = 0f
    var lineCount = 0
    var prevLine: PdfTextLine? = null

    fun flushParagraph() {
      val text = currentText.toString().trim()
      if (text.isNotEmpty()) {
        val avgFont = if (lineCount > 0) fontSizeSum / lineCount else medianFontSize
        val isHeading = avgFont >= medianFontSize * 1.18f && text.length <= 140
        paragraphs +=
          PlacedParagraph(
            paragraph =
              ReflowItem.Paragraph(
                text = text,
                isHeading = isHeading,
                topFraction = paragraphTop,
              ),
            firstLineBounds = paragraphFirstBounds,
          )
      }
      currentText.clear()
      fontSizeSum = 0f
      lineCount = 0
    }

    for (line in lines) {
      val cleaned = line.text.trim()
      if (cleaned.isEmpty()) continue

      val prev = prevLine
      if (prev == null) {
        paragraphTop = line.bounds.top
        paragraphFirstBounds = line.bounds
        currentText.append(cleaned)
        fontSizeSum += line.fontSizePt
        lineCount++
        prevLine = line
        continue
      }

      val verticalGap = line.bounds.top - prev.bounds.bottom
      val largeVerticalGap = verticalGap > (medianHeight * 1.08f).coerceAtLeast(0.014f)
      val fontChanged = abs(line.fontSizePt - prev.fontSizePt) > 1.5f
      val lastCharIdx = prev.text.lastNonWhitespaceIndex()
      val lastChar = if (lastCharIdx >= 0) prev.text[lastCharIdx] else null
      val prevEndedSentence = lastChar != null && lastChar in charArrayOf('.', '!', '?', ':', '…', '”', '"')
      val indentedStart = (line.bounds.left - baseLeft) > 0.008f
      val shortPrevLine = prev.bounds.right < (baseRight - 0.08f)
      val startsNewParagraph =
        largeVerticalGap || fontChanged || (prevEndedSentence && (indentedStart || shortPrevLine))

      if (startsNewParagraph) {
        flushParagraph()
        paragraphTop = line.bounds.top
        paragraphFirstBounds = line.bounds
        currentText.append(cleaned)
      } else {
        if (currentText.endsWithHyphenBeforeTrailingSpaces() && cleaned.firstOrNull()?.isLowerCase() == true) {
          val hyphenIdx = currentText.lastNonWhitespaceIndex()
          if (hyphenIdx >= 0) currentText.setLength(hyphenIdx)
          currentText.append(cleaned)
        } else {
          if (currentText.isNotEmpty()) currentText.append(' ')
          currentText.append(cleaned)
        }
      }

      fontSizeSum += line.fontSizePt
      lineCount++
      prevLine = line
    }
    flushParagraph()
    return paragraphs
  }
}

internal fun CharSequence.lastNonWhitespaceIndex(): Int {
  var index = length - 1
  while (index >= 0 && this[index].isWhitespace()) {
    index--
  }
  return index
}

internal fun CharSequence.endsWithHyphenBeforeTrailingSpaces(): Boolean {
  val idx = lastNonWhitespaceIndex()
  return idx >= 0 && this[idx] == '-'
}
