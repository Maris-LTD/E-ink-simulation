package com.example.emptyapp.reader.pdf

import kotlin.math.abs

object PdfGraphicMasker {
  private const val SEGMENT_AXIS_TOLERANCE = 0.004f
  private const val MIN_HORIZONTAL_RULE_WIDTH = 0.08f
  private const val MIN_VERTICAL_RULE_HEIGHT = 0.03f
  private const val INTERSECTION_TOLERANCE = 0.008f
  private const val ROW_Y_TOLERANCE = 0.008f
  private const val MASK_PADDING = 0.004f
  private const val SYMBOL_MAX_WIDTH_FRACTION = 0.25f
  private const val MIN_SYMBOL_STACK_ROWS = 3
  private const val MIN_TEXT_GRID_ROWS = 3

  private val MULTI_SPACE_REGEX = Regex("""\s{3,}""")
  private val GRAPHIC_CHARS =
    setOf(
      '-', '–', '—', '_', '=', '|', '+', '*', '/', '\\',
      '│', '─', '┌', '┐', '└', '┘', '├', '┤', '┬', '┴', '┼',
      '╭', '╮', '╯', '╰', '═', '║', '╔', '╗', '╚', '╝', '╠', '╣', '╦', '╩', '╬',
      '▲', '▼', '◄', '►', '●', '○', '■', '□', '▪', '▫', '☰', '☱', '☲', '☳', '☴', '☵', '☶', '☷',
    )

  fun detectMasks(analysis: PdfPageAnalysis): List<PdfGraphicMask> {
    val vectorTables = detectVectorLineTables(analysis)
    val filledTables = detectFilledRectTables(analysis.filledRects)
    val structuralTables = coalesceMasks(vectorTables + filledTables)

    val unmaskedLinesAfterStructural =
      analysis.textLines.filterNot { isLineMasked(it, structuralTables) }

    val textGridTables = detectTextGridTables(unmaskedLinesAfterStructural)
    val allTables = coalesceMasks(structuralTables + textGridTables)

    val unmaskedLinesAfterTables =
      analysis.textLines.filterNot { isLineMasked(it, allTables) }

    val symbolStacks = detectSymbolStacks(unmaskedLinesAfterTables, analysis.textLines)
    return coalesceMasks(allTables + symbolStacks).sortedBy { it.bounds.top }
  }

  fun filterMaskedLines(
    lines: List<PdfTextLine>,
    masks: List<PdfGraphicMask>,
  ): List<PdfTextLine> {
    if (masks.isEmpty()) return lines
    return lines.filterNot { isLineMasked(it, masks) }
  }

  private fun isLineMasked(line: PdfTextLine, masks: List<PdfGraphicMask>): Boolean {
    val b = line.bounds
    val cx = (b.left + b.right) * 0.5f
    val cy = (b.top + b.bottom) * 0.5f
    val lineArea = b.area.coerceAtLeast(1e-6f)
    return masks.any { mask ->
      val m = mask.bounds
      val centerInside = cx in m.left..m.right && cy in m.top..m.bottom
      centerInside || intersectionArea(b, m) / lineArea >= 0.5f
    }
  }

  private fun detectVectorLineTables(analysis: PdfPageAnalysis): List<PdfGraphicMask> {
    val segments =
      analysis.vectorSegments.filter { seg ->
        !(seg.width > 0.90f && seg.height > 0.90f)
      }
    if (segments.isEmpty()) return emptyList()

    val horizontals =
      segments.filter { seg ->
        abs(seg.y1 - seg.y2) <= SEGMENT_AXIS_TOLERANCE &&
          seg.width >= MIN_HORIZONTAL_RULE_WIDTH &&
          seg.minY in 0.04f..0.96f
      }
    val verticals =
      segments.filter { seg ->
        abs(seg.x1 - seg.x2) <= SEGMENT_AXIS_TOLERANCE &&
          seg.height >= MIN_VERTICAL_RULE_HEIGHT
      }

    val masks = ArrayList<PdfGraphicMask>()
    val usedHorizontals = HashSet<Int>()

    // 1A: Connected grids of horizontal + vertical segments
    if (horizontals.size >= 2 && verticals.isNotEmpty()) {
      val hCount = horizontals.size
      val vCount = verticals.size
      val visitedH = BooleanArray(hCount)
      val visitedV = BooleanArray(vCount)

      for (startH in 0 until hCount) {
        if (visitedH[startH]) continue
        val compH = ArrayList<Int>()
        val compV = ArrayList<Int>()
        val queue = ArrayDeque<Pair<Boolean, Int>>()
        visitedH[startH] = true
        queue.add(true to startH)

        while (queue.isNotEmpty()) {
          val (isH, idx) = queue.removeFirst()
          if (isH) {
            compH += idx
            val hSeg = horizontals[idx]
            for (vIdx in 0 until vCount) {
              if (!visitedV[vIdx] && segmentsIntersect(hSeg, verticals[vIdx])) {
                visitedV[vIdx] = true
                queue.add(false to vIdx)
              }
            }
          } else {
            compV += idx
            val vSeg = verticals[idx]
            for (hIdx in 0 until hCount) {
              if (!visitedH[hIdx] && segmentsIntersect(horizontals[hIdx], vSeg)) {
                visitedH[hIdx] = true
                queue.add(true to hIdx)
              }
            }
          }
        }

        val isGrid =
          (compH.size >= 2 && compV.size >= 2) ||
            (compH.size >= 3 && compV.isNotEmpty())
        if (isGrid) {
          usedHorizontals.addAll(compH)
          val compSegs = compH.map { horizontals[it] } + compV.map { verticals[it] }
          val bounds =
            PdfRect(
              left = (compSegs.minOf { it.minX } - MASK_PADDING).coerceIn(0f, 1f),
              top = (compSegs.minOf { it.minY } - MASK_PADDING).coerceIn(0f, 1f),
              right = (compSegs.maxOf { it.maxX } + MASK_PADDING).coerceIn(0f, 1f),
              bottom = (compSegs.maxOf { it.maxY } + MASK_PADDING).coerceIn(0f, 1f),
            )
          masks += PdfGraphicMask(bounds = bounds, kind = PdfGraphicMaskKind.TABLE)
        }
      }
    }

    // 1B: Horizontal-only parallel rules enclosing multi-column text (academic/booktabs tables)
    val remainingH =
      horizontals.indices
        .filter { it !in usedHorizontals }
        .map { horizontals[it] }
        .sortedBy { it.minY }

    val visitedRem = BooleanArray(remainingH.size)
    for (i in remainingH.indices) {
      if (visitedRem[i]) continue
      val group = mutableListOf(remainingH[i])
      visitedRem[i] = true
      for (j in (i + 1) until remainingH.size) {
        if (visitedRem[j]) continue
        val candidate = remainingH[j]
        val prev = group.last()
        val sameSpan =
          abs(candidate.minX - prev.minX) <= 0.06f &&
            abs(candidate.maxX - prev.maxX) <= 0.06f
        val verticalGap = candidate.minY - prev.maxY
        if (sameSpan && verticalGap in 0.015f..0.45f) {
          group += candidate
          visitedRem[j] = true
        }
      }
      if (group.size >= 2) {
        val left = group.minOf { it.minX }
        val right = group.maxOf { it.maxX }
        val top = group.minOf { it.minY }
        val bottom = group.maxOf { it.maxY }
        val enclosedLines =
          analysis.textLines.filter { line ->
            val cx = (line.bounds.left + line.bounds.right) * 0.5f
            val cy = (line.bounds.top + line.bounds.bottom) * 0.5f
            cx in left..right && cy in top..bottom
          }
        val rows = groupIntoVisualRows(enclosedLines)
        val hasTabularRows = rows.count { row -> splitRowIntoSpans(row).size >= 2 } >= 2
        if (hasTabularRows || group.size >= 3 && rows.size >= 2) {
          val bounds =
            PdfRect(
              left = (left - MASK_PADDING).coerceIn(0f, 1f),
              top = (top - MASK_PADDING).coerceIn(0f, 1f),
              right = (right + MASK_PADDING).coerceIn(0f, 1f),
              bottom = (bottom + MASK_PADDING).coerceIn(0f, 1f),
            )
          masks += PdfGraphicMask(bounds = bounds, kind = PdfGraphicMaskKind.TABLE)
        }
      }
    }

    return masks
  }

  private fun detectFilledRectTables(filledRects: List<PdfFilledRect>): List<PdfGraphicMask> {
    val candidates =
      filledRects
        .map { it.bounds }
        .filter { b ->
          b.width >= 0.12f &&
            b.height in 0.008f..0.25f &&
            !(b.width > 0.90f && b.height > 0.90f)
        }
        .sortedBy { it.top }

    if (candidates.size < 2) return emptyList()

    val masks = ArrayList<PdfGraphicMask>()
    val visited = BooleanArray(candidates.size)
    for (i in candidates.indices) {
      if (visited[i]) continue
      val group = mutableListOf(candidates[i])
      visited[i] = true
      for (j in (i + 1) until candidates.size) {
        if (visited[j]) continue
        val next = candidates[j]
        val prev = group.last()
        val alignedX =
          abs(next.left - prev.left) <= 0.05f &&
            abs(next.right - prev.right) <= 0.05f
        val verticalGap = next.top - prev.bottom
        if (alignedX && verticalGap in -0.005f..0.08f) {
          group += next
          visited[j] = true
        }
      }
      if (group.size >= 2) {
        val bounds =
          PdfRect(
            left = (group.minOf { it.left } - MASK_PADDING).coerceIn(0f, 1f),
            top = (group.minOf { it.top } - MASK_PADDING).coerceIn(0f, 1f),
            right = (group.maxOf { it.right } + MASK_PADDING).coerceIn(0f, 1f),
            bottom = (group.maxOf { it.bottom } + MASK_PADDING).coerceIn(0f, 1f),
          )
        masks += PdfGraphicMask(bounds = bounds, kind = PdfGraphicMaskKind.TABLE)
      }
    }
    return masks
  }

  private fun detectTextGridTables(lines: List<PdfTextLine>): List<PdfGraphicMask> {
    if (lines.size < MIN_TEXT_GRID_ROWS) return emptyList()
    val rows = groupIntoVisualRows(lines)
    if (rows.size < MIN_TEXT_GRID_ROWS) return emptyList()

    data class GridRow(
      val bounds: PdfRect,
      val spans: List<PdfRect>,
      val isMultiSpan: Boolean,
    )

    val gridRows =
      rows.map { rowLines ->
        val spans = splitRowIntoSpans(rowLines)
        val rowBounds = mergeBounds(rowLines.map { it.bounds })
        val allShort = spans.all { it.text.trim().length <= 40 && it.bounds.width <= 0.42f }
        when {
          spans.size >= 2 && allShort -> GridRow(rowBounds, spans.map { it.bounds }, isMultiSpan = true)
          spans.size == 1 && allShort -> GridRow(rowBounds, spans.map { it.bounds }, isMultiSpan = false)
          else -> null
        }
      }

    val masks = ArrayList<PdfGraphicMask>()
    var idx = 0
    while (idx < gridRows.size) {
      val first = gridRows[idx]
      if (first == null || !first.isMultiSpan) {
        idx++
        continue
      }
      val run = mutableListOf(first)
      var sparseStreak = 0
      var j = idx + 1
      while (j < gridRows.size) {
        val next = gridRows[j] ?: break
        val prev = run.last()
        if ((next.bounds.top - prev.bounds.bottom) > 0.045f) break
        if (next.isMultiSpan) {
          run += next
          sparseStreak = 0
        } else if (sparseStreak < 3) {
          run += next
          sparseStreak++
        } else {
          break
        }
        j++
      }
      while (run.isNotEmpty() && !run.last().isMultiSpan) {
        run.removeAt(run.lastIndex)
      }
      val multiSpanRows = run.filter { it.isMultiSpan }
      if (multiSpanRows.size >= MIN_TEXT_GRID_ROWS && hasAlignedColumns(multiSpanRows.map { it.spans })) {
        val bounds = mergeBounds(run.map { it.bounds })
        masks +=
          PdfGraphicMask(
            bounds =
              PdfRect(
                left = (bounds.left - MASK_PADDING).coerceIn(0f, 1f),
                top = (bounds.top - MASK_PADDING).coerceIn(0f, 1f),
                right = (bounds.right + MASK_PADDING).coerceIn(0f, 1f),
                bottom = (bounds.bottom + MASK_PADDING).coerceIn(0f, 1f),
              ),
            kind = PdfGraphicMaskKind.TABLE,
          )
        idx = j
      } else {
        idx++
      }
    }
    return masks
  }

  private fun hasAlignedColumns(rowSpans: List<List<PdfRect>>): Boolean {
    val allLefts = rowSpans.flatten().map { it.left }.sorted()
    if (allLefts.size < rowSpans.size * 2) return false

    val columnAnchors = ArrayList<Float>()
    for (x in allLefts) {
      if (columnAnchors.none { abs(it - x) <= 0.045f }) {
        columnAnchors += x
      }
    }
    if (columnAnchors.size < 2) return false

    val alignedColumns =
      columnAnchors.count { anchor ->
        rowSpans.count { spans ->
          spans.any { abs(it.left - anchor) <= 0.045f }
        } >= MIN_TEXT_GRID_ROWS
      }
    return alignedColumns >= 2
  }

  private fun detectSymbolStacks(
    unmaskedLines: List<PdfTextLine>,
    allLines: List<PdfTextLine>,
  ): List<PdfGraphicMask> {
    if (unmaskedLines.size < MIN_SYMBOL_STACK_ROWS) return emptyList()
    val rows = groupIntoVisualRows(unmaskedLines)
    if (rows.size < MIN_SYMBOL_STACK_ROWS) return emptyList()

    val contentWidth =
      if (allLines.isNotEmpty()) {
        (allLines.maxOf { it.bounds.right } - allLines.minOf { it.bounds.left }).coerceAtLeast(0.4f)
      } else {
        0.8f
      }

    data class RowSummary(
      val bounds: PdfRect,
      val isCandidate: Boolean,
    )

    val summaries =
      rows.map { rowLines ->
        val bounds = mergeBounds(rowLines.map { it.bounds })
        val combinedText = rowLines.sortedBy { it.bounds.left }.joinToString(" ") { it.text.trim() }
        val narrow = bounds.width > 0f && bounds.width < contentWidth * SYMBOL_MAX_WIDTH_FRACTION
        val candidate = narrow && isGraphicSymbolLine(combinedText)
        RowSummary(bounds = bounds, isCandidate = candidate)
      }

    val masks = ArrayList<PdfGraphicMask>()
    var runStart = -1
    for (idx in 0..summaries.size) {
      val current = if (idx < summaries.size) summaries[idx] else null
      val prev = if (idx > 0) summaries[idx - 1] else null
      val aligned =
        current != null &&
          prev != null &&
          current.isCandidate &&
          prev.isCandidate &&
          (abs(centerX(current.bounds) - centerX(prev.bounds)) <= 0.08f ||
            abs(current.bounds.left - prev.bounds.left) <= 0.08f) &&
          (current.bounds.top - prev.bounds.bottom) in -0.005f..0.045f

      if (current != null && current.isCandidate && runStart == -1) {
        runStart = idx
      } else if (!aligned && runStart != -1) {
        val runEnd = idx
        if (runEnd - runStart >= MIN_SYMBOL_STACK_ROWS) {
          val runBounds = summaries.subList(runStart, runEnd).map { it.bounds }
          if (hasConsistentVerticalSpacing(runBounds)) {
            val merged = mergeBounds(runBounds)
            masks +=
              PdfGraphicMask(
                bounds =
                  PdfRect(
                    left = (merged.left - MASK_PADDING).coerceIn(0f, 1f),
                    top = (merged.top - MASK_PADDING).coerceIn(0f, 1f),
                    right = (merged.right + MASK_PADDING).coerceIn(0f, 1f),
                    bottom = (merged.bottom + MASK_PADDING).coerceIn(0f, 1f),
                  ),
                kind = PdfGraphicMaskKind.SYMBOL_STACK,
              )
          }
        }
        runStart = if (current != null && current.isCandidate) idx else -1
      }
    }
    return masks
  }

  private fun isGraphicSymbolLine(text: String): Boolean {
    val nonWs = text.filter { !it.isWhitespace() }
    if (nonWs.isEmpty()) return false
    val graphicCount = nonWs.count { it in GRAPHIC_CHARS }
    val graphicRatio = graphicCount.toFloat() / nonWs.length
    if (graphicRatio >= 0.50f) return true

    val tokens = text.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    val allSingleCharTokens = tokens.size >= 2 && tokens.all { it.length == 1 }
    val hasNoLongWord = tokens.none { token -> token.count { it.isLetter() } >= 3 }
    return (allSingleCharTokens && hasNoLongWord) || (graphicCount >= 1 && hasNoLongWord && nonWs.length <= 6)
  }

  private fun hasConsistentVerticalSpacing(boundsList: List<PdfRect>): Boolean {
    if (boundsList.size < MIN_SYMBOL_STACK_ROWS) return true
    val gaps =
      (1 until boundsList.size).map { idx ->
        (boundsList[idx].top - boundsList[idx - 1].bottom).coerceAtLeast(0f)
      }
    val minGap = gaps.minOrNull() ?: 0f
    val maxGap = gaps.maxOrNull() ?: 0f
    return maxGap <= (minGap * 2.2f + 0.012f)
  }

  private data class TextSpan(
    val text: String,
    val bounds: PdfRect,
  )

  private fun groupIntoVisualRows(lines: List<PdfTextLine>): List<List<PdfTextLine>> {
    if (lines.isEmpty()) return emptyList()
    val sorted = lines.sortedWith(compareBy<PdfTextLine> { centerY(it.bounds) }.thenBy { it.bounds.left })
    val rows = ArrayList<MutableList<PdfTextLine>>()
    for (line in sorted) {
      val cy = centerY(line.bounds)
      val targetRow =
        rows.lastOrNull()?.takeIf { row ->
          val rowCy = row.map { centerY(it.bounds) }.average().toFloat()
          abs(cy - rowCy) <= ROW_Y_TOLERANCE
        }
      if (targetRow != null) {
        targetRow += line
      } else {
        rows += mutableListOf(line)
      }
    }
    return rows
  }

  private fun splitRowIntoSpans(rowLines: List<PdfTextLine>): List<TextSpan> {
    val spans = ArrayList<TextSpan>()
    for (line in rowLines.sortedBy { it.bounds.left }) {
      val raw = line.text.trim()
      if (raw.isEmpty()) continue
      val parts = raw.split(MULTI_SPACE_REGEX).filter { it.isNotBlank() }
      if (parts.size <= 1) {
        spans += TextSpan(raw, line.bounds)
      } else {
        val totalLen = raw.length.coerceAtLeast(1)
        var searchFrom = 0
        for (part in parts) {
          val startIdx = raw.indexOf(part, searchFrom).coerceAtLeast(0)
          val endIdx = startIdx + part.length
          searchFrom = endIdx
          val left = line.bounds.left + line.bounds.width * (startIdx.toFloat() / totalLen)
          val right = line.bounds.left + line.bounds.width * (endIdx.toFloat() / totalLen)
          spans +=
            TextSpan(
              text = part,
              bounds = PdfRect(left, line.bounds.top, right, line.bounds.bottom),
            )
        }
      }
    }

    // Merge spans that are horizontally touching/very close
    val merged = ArrayList<TextSpan>()
    for (span in spans.sortedBy { it.bounds.left }) {
      val last = merged.lastOrNull()
      if (last != null && (span.bounds.left - last.bounds.right) < 0.012f) {
        merged[merged.lastIndex] =
          TextSpan(
            text = "${last.text} ${span.text}",
            bounds = mergeBounds(listOf(last.bounds, span.bounds)),
          )
      } else {
        merged += span
      }
    }
    return merged
  }

  private fun coalesceMasks(masks: List<PdfGraphicMask>): List<PdfGraphicMask> {
    if (masks.size <= 1) return masks
    val remaining = masks.toMutableList()
    val result = ArrayList<PdfGraphicMask>()
    while (remaining.isNotEmpty()) {
      var current = remaining.removeAt(0)
      var mergedAny: Boolean
      do {
        mergedAny = false
        val iter = remaining.iterator()
        while (iter.hasNext()) {
          val other = iter.next()
          val sameKind = current.kind == other.kind
          val overlap = intersectionArea(current.bounds, other.bounds) > 0f
          if (overlap && (sameKind || current.kind == PdfGraphicMaskKind.TABLE)) {
            val kind =
              if (current.kind == PdfGraphicMaskKind.TABLE || other.kind == PdfGraphicMaskKind.TABLE) {
                PdfGraphicMaskKind.TABLE
              } else {
                current.kind
              }
            current = PdfGraphicMask(mergeBounds(listOf(current.bounds, other.bounds)), kind)
            iter.remove()
            mergedAny = true
          }
        }
      } while (mergedAny)
      result += current
    }
    return result
  }

  private fun segmentsIntersect(h: PdfVectorSegment, v: PdfVectorSegment): Boolean {
    val hx1 = h.minX - INTERSECTION_TOLERANCE
    val hx2 = h.maxX + INTERSECTION_TOLERANCE
    val hy = (h.minY + h.maxY) * 0.5f
    val vx = (v.minX + v.maxX) * 0.5f
    val vy1 = v.minY - INTERSECTION_TOLERANCE
    val vy2 = v.maxY + INTERSECTION_TOLERANCE
    return vx in hx1..hx2 && hy in vy1..vy2
  }

  private fun intersectionArea(a: PdfRect, b: PdfRect): Float {
    val left = maxOf(a.left, b.left)
    val top = maxOf(a.top, b.top)
    val right = minOf(a.right, b.right)
    val bottom = minOf(a.bottom, b.bottom)
    return if (right > left && bottom > top) (right - left) * (bottom - top) else 0f
  }

  private fun mergeBounds(rects: List<PdfRect>): PdfRect =
    PdfRect(
      left = rects.minOf { it.left },
      top = rects.minOf { it.top },
      right = rects.maxOf { it.right },
      bottom = rects.maxOf { it.bottom },
    )

  private fun centerX(r: PdfRect): Float = (r.left + r.right) * 0.5f

  private fun centerY(r: PdfRect): Float = (r.top + r.bottom) * 0.5f
}
