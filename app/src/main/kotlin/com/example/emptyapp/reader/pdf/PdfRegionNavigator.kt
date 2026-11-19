package com.example.emptyapp.reader.pdf

import kotlin.math.abs

data class CameraTransform(
  val scale: Float,
  val offsetX: Float,
  val offsetY: Float,
)

object PdfRegionNavigator {
  private const val REGION_OVERLAP_FRACTION = 0.09f
  private const val MIN_REGION_WIDTH = 0.22f
  private const val MIN_REGION_HEIGHT = 0.22f
  private const val MAX_REGION_HEIGHT = 0.95f
  private const val PADDING = 0.02f

  fun buildGuidedRegions(
    analysis: PdfPageAnalysis?,
    viewportAspect: Float,
  ): List<PdfRect> {
    if (analysis == null || (analysis.textLines.isEmpty() && analysis.imageBlocks.isEmpty())) {
      return listOf(PdfRect(0f, 0f, 1f, 1f))
    }

    val safeViewportAspect = viewportAspect.coerceIn(0.35f, 2.5f)
    val pageAspect = (analysis.pageWidthPt / analysis.pageHeightPt.coerceAtLeast(1f)).coerceIn(0.35f, 2.5f)

    val bodyLines =
      analysis.textLines.filter {
        it.bounds.bottom > 0.06f && it.bounds.top < 0.94f && it.text.isNotBlank()
      }
    if (bodyLines.isEmpty()) {
      return listOf(PdfRect(0f, 0f, 1f, 1f))
    }

    val columnBoxes = splitIntoColumnBoxes(bodyLines, analysis.columnCount)
    val regions = ArrayList<PdfRect>()

    for (col in columnBoxes) {
      val colW = col.width.coerceAtLeast(MIN_REGION_WIDTH)
      val regionH = (colW * pageAspect / safeViewportAspect).coerceIn(MIN_REGION_HEIGHT, MAX_REGION_HEIGHT)
      if (col.height <= regionH * 1.08f) {
        regions += col
      } else {
        val step = (regionH * (1f - REGION_OVERLAP_FRACTION)).coerceAtLeast(0.10f)
        var top = col.top
        while (top < col.bottom - 0.02f) {
          val bottom = (top + regionH).coerceAtMost(col.bottom)
          val adjustedTop =
            if (bottom >= col.bottom && (bottom - top) < regionH * 0.75f) {
              (col.bottom - regionH).coerceAtLeast(col.top)
            } else {
              top
            }
          regions += PdfRect(col.left, adjustedTop, col.right, bottom)
          if (bottom >= col.bottom) break
          top += step
        }
      }
    }

    return if (regions.isEmpty()) listOf(PdfRect(0f, 0f, 1f, 1f)) else regions
  }

  fun findTapTargetRect(
    analysis: PdfPageAnalysis?,
    tapNormX: Float,
    tapNormY: Float,
  ): PdfRect? {
    if (analysis == null) return null

    val hitImage =
      analysis.imageBlocks
        .filter { it.pageAreaFraction in 0.02f..0.85f }
        .firstOrNull { img ->
          tapNormX in (img.bounds.left - 0.02f)..(img.bounds.right + 0.02f) &&
            tapNormY in (img.bounds.top - 0.02f)..(img.bounds.bottom + 0.02f)
        }
    if (hitImage != null) {
      return PdfRect(
        left = (hitImage.bounds.left - PADDING).coerceIn(0f, 1f),
        top = (hitImage.bounds.top - PADDING).coerceIn(0f, 1f),
        right = (hitImage.bounds.right + PADDING).coerceIn(0f, 1f),
        bottom = (hitImage.bounds.bottom + PADDING).coerceIn(0f, 1f),
      )
    }

    val columns = splitIntoColumnBoxes(analysis.textLines, analysis.columnCount)
    val hitCol =
      columns.firstOrNull { col ->
        tapNormX in (col.left - 0.03f)..(col.right + 0.03f) &&
          tapNormY in (col.top - 0.03f)..(col.bottom + 0.03f)
      } ?: return null

    // Focus vertically around the tapped region within the column
    val focusHalfHeight = 0.24f
    val top = (tapNormY - focusHalfHeight).coerceAtLeast(hitCol.top)
    val bottom = (tapNormY + focusHalfHeight).coerceAtMost(hitCol.bottom)
    return PdfRect(
      left = hitCol.left,
      top = top,
      right = hitCol.right,
      bottom = bottom.coerceAtLeast((top + 0.15f).coerceAtMost(1f)),
    )
  }

  fun computeCameraTransform(
    region: PdfRect,
    bitmapWidth: Float,
    bitmapHeight: Float,
    containerWidth: Float,
    containerHeight: Float,
  ): CameraTransform {
    if (bitmapWidth <= 0f || bitmapHeight <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
      return CameraTransform(1f, 0f, 0f)
    }
    val fitScale = minOf(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
    val dispW = bitmapWidth * fitScale
    val dispH = bitmapHeight * fitScale

    val regionW = (region.width * dispW).coerceAtLeast(1f)
    val regionH = (region.height * dispH).coerceAtLeast(1f)
    val scale = minOf(containerWidth / regionW, containerHeight / regionH).coerceIn(1f, 4.5f)

    val centerX = (region.left + region.right) / 2f
    val centerY = (region.top + region.bottom) / 2f
    val dx = (centerX - 0.5f) * dispW
    val dy = (centerY - 0.5f) * dispH

    val (offsetX, offsetY) =
      clampOffsetForFitContent(
        offsetX = -dx * scale,
        offsetY = -dy * scale,
        scale = scale,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        bitmapWidth = bitmapWidth,
        bitmapHeight = bitmapHeight,
      )
    return CameraTransform(
      scale = scale,
      offsetX = offsetX,
      offsetY = offsetY,
    )
  }

  fun mapPageRectToCropSpace(
    rect: PdfRect,
    crop: PdfRect,
  ): PdfRect {
    val cropW = crop.width
    val cropH = crop.height
    if (cropW <= 1e-4f || cropH <= 1e-4f) return rect
    val left = ((rect.left - crop.left) / cropW).coerceIn(0f, 1f)
    val top = ((rect.top - crop.top) / cropH).coerceIn(0f, 1f)
    val right = ((rect.right - crop.left) / cropW).coerceIn(0f, 1f)
    val bottom = ((rect.bottom - crop.top) / cropH).coerceIn(0f, 1f)
    if (right - left < 0.02f || bottom - top < 0.02f) {
      return rect
    }
    return PdfRect(
      left = left,
      top = top,
      right = right,
      bottom = bottom,
    )
  }

  fun clampOffsetForFitContent(
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    containerWidth: Float,
    containerHeight: Float,
    bitmapWidth: Float = 0f,
    bitmapHeight: Float = 0f,
  ): Pair<Float, Float> {
    if (containerWidth <= 0f || containerHeight <= 0f || scale <= 1f) {
      return 0f to 0f
    }
    val (dispW, dispH) =
      if (bitmapWidth > 0f && bitmapHeight > 0f) {
        val fitScale = minOf(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
        (bitmapWidth * fitScale) to (bitmapHeight * fitScale)
      } else {
        containerWidth to containerHeight
      }
    val maxX = ((dispW * scale - containerWidth) / 2f).coerceAtLeast(0f)
    val maxY = ((dispH * scale - containerHeight) / 2f).coerceAtLeast(0f)
    return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
  }

  private fun splitIntoColumnBoxes(lines: List<PdfTextLine>, columnCount: Int): List<PdfRect> {
    if (lines.isEmpty()) return emptyList()
    if (columnCount <= 1) {
      return listOf(boundingBoxOf(lines))
    }

    val colRanges =
      if (columnCount >= 3) {
        listOf(0f..0.34f, 0.34f..0.67f, 0.67f..1.0f)
      } else {
        listOf(0f..0.50f, 0.50f..1.0f)
      }

    val boxes =
      colRanges.mapNotNull { range ->
        val colLines =
          lines.filter { line ->
            val center = (line.bounds.left + line.bounds.right) / 2f
            center in range
          }
        if (colLines.isEmpty()) null else boundingBoxOf(colLines)
      }

    return if (boxes.isEmpty()) listOf(boundingBoxOf(lines)) else boxes
  }

  private fun boundingBoxOf(lines: List<PdfTextLine>): PdfRect {
    val left = (lines.minOf { it.bounds.left } - PADDING).coerceIn(0f, 1f)
    val top = (lines.minOf { it.bounds.top } - PADDING).coerceIn(0f, 1f)
    val right = (lines.maxOf { it.bounds.right } + PADDING).coerceIn(0f, 1f)
    val bottom = (lines.maxOf { it.bounds.bottom } + PADDING).coerceIn(0f, 1f)
    return PdfRect(left, top, right, bottom)
  }

  fun applyHorizontalLock(panX: Float, panY: Float): Pair<Float, Float> {
    val lockedX = if (abs(panY) > abs(panX) * 1.5f) 0f else panX
    return lockedX to panY
  }
}
