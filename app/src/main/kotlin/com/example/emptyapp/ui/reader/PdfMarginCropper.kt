package com.example.emptyapp.ui.reader

import android.graphics.Bitmap

data class NormalizedCropRect(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  val width: Float
    get() = (right - left).coerceAtLeast(0f)

  val height: Float
    get() = (bottom - top).coerceAtLeast(0f)

  companion object {
    val FULL = NormalizedCropRect(0f, 0f, 1f, 1f)
  }
}

object PdfMarginCropper {
  private const val DEFAULT_PADDING_FRACTION = 0.02f
  private const val DEFAULT_LUMA_THRESHOLD = 235
  private const val MIN_CONTENT_FRACTION = 0.10f
  private const val MAX_FULL_PAGE_FRACTION = 0.96f

  fun detectContentBounds(
    bitmap: Bitmap,
    paddingFraction: Float = DEFAULT_PADDING_FRACTION,
    lumaThreshold: Int = DEFAULT_LUMA_THRESHOLD,
  ): NormalizedCropRect {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return NormalizedCropRect.FULL
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    return detectContentBounds(
      pixels = pixels,
      width = width,
      height = height,
      paddingFraction = paddingFraction,
      lumaThreshold = lumaThreshold,
    )
  }

  fun detectContentBounds(
    pixels: IntArray,
    width: Int,
    height: Int,
    paddingFraction: Float = DEFAULT_PADDING_FRACTION,
    lumaThreshold: Int = DEFAULT_LUMA_THRESHOLD,
  ): NormalizedCropRect {
    if (width <= 0 || height <= 0 || pixels.size < width * height) {
      return NormalizedCropRect.FULL
    }

    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1

    for (y in 0 until height) {
      val rowOffset = y * width
      for (x in 0 until width) {
        val color = pixels[rowOffset + x]
        val alpha = (color ushr 24) and 0xFF
        if (alpha < 128) continue
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        val luma = (r * 299 + g * 587 + b * 114) / 1000
        if (luma < lumaThreshold) {
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
        }
      }
    }

    if (maxX < minX || maxY < minY) {
      return NormalizedCropRect.FULL
    }

    val rawLeft = minX.toFloat() / width
    val rawTop = minY.toFloat() / height
    val rawRight = (maxX + 1).toFloat() / width
    val rawBottom = (maxY + 1).toFloat() / height

    if ((rawRight - rawLeft) < MIN_CONTENT_FRACTION && (rawBottom - rawTop) < MIN_CONTENT_FRACTION) {
      return NormalizedCropRect.FULL
    }

    val left = (rawLeft - paddingFraction).coerceIn(0f, 1f)
    val top = (rawTop - paddingFraction).coerceIn(0f, 1f)
    val right = (rawRight + paddingFraction).coerceIn(0f, 1f)
    val bottom = (rawBottom + paddingFraction).coerceIn(0f, 1f)

    if ((right - left) > MAX_FULL_PAGE_FRACTION && (bottom - top) > MAX_FULL_PAGE_FRACTION) {
      return NormalizedCropRect.FULL
    }

    return NormalizedCropRect(
      left = left,
      top = top,
      right = right,
      bottom = bottom,
    )
  }
}
