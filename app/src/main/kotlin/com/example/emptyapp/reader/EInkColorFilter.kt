package com.example.emptyapp.reader

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object EInkColorFilter {
  fun matrixOf(settings: EInkSettings): ColorMatrix {
    val matrix = ColorMatrix()
    if (settings.grayscale) matrix.setSaturation(0f)
    val contrast = settings.contrast
    val pivot = if (contrast > 1f) 246f else 128f
    val offset = (1f - contrast) * pivot
    val contrastMatrix =
      ColorMatrix(
        floatArrayOf(
          contrast, 0f, 0f, 0f, offset,
          0f, contrast, 0f, 0f, offset,
          0f, 0f, contrast, 0f, offset,
          0f, 0f, 0f, 1f, 0f,
        )
      )
    matrix.postConcat(contrastMatrix)
    return matrix
  }

  fun paintOf(settings: EInkSettings): Paint =
    Paint().apply { colorFilter = ColorMatrixColorFilter(matrixOf(settings)) }
}
