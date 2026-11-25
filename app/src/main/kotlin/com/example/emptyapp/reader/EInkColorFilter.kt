package com.example.emptyapp.reader

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object EInkColorFilter {
  fun matrixOf(settings: EInkSettings, contentAlreadyDark: Boolean = false): ColorMatrix {
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
    when (settings.tone) {
      EInkTone.DARK, EInkTone.AMOLED -> if (!contentAlreadyDark) {
        val invert =
          ColorMatrix(
            floatArrayOf(
              -1f, 0f, 0f, 0f, 255f,
              0f, -1f, 0f, 0f, 255f,
              0f, 0f, -1f, 0f, 255f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
        matrix.postConcat(invert)
      }
      EInkTone.WARM -> {
        val warm = ColorMatrix().apply { setScale(1f, 0.92f, 0.78f, 1f) }
        matrix.postConcat(warm)
      }
      EInkTone.PAPER -> {
        val paper = ColorMatrix().apply { setScale(0.95f, 0.93f, 0.87f, 1f) }
        matrix.postConcat(paper)
      }
      EInkTone.SOFT_GRAY -> {
        val gray = ColorMatrix().apply { setScale(0.84f, 0.84f, 0.84f, 1f) }
        matrix.postConcat(gray)
      }
      EInkTone.HIGH_CONTRAST -> Unit
    }
    return matrix
  }

  fun paintOf(settings: EInkSettings, contentAlreadyDark: Boolean = false): Paint =
    Paint().apply { colorFilter = ColorMatrixColorFilter(matrixOf(settings, contentAlreadyDark)) }
}
