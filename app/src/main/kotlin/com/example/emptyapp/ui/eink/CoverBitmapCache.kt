package com.example.emptyapp.ui.eink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

object CoverBitmapCache {
  private val cache = LruCache<String, ImageBitmap>(64)

  fun get(path: String?): ImageBitmap? = path?.let { cache.get(it) }

  fun decodeAndCache(file: File, maxDimensionPx: Int = 320): ImageBitmap? {
    val path = file.absolutePath
    cache.get(path)?.let { return it }
    if (!file.exists()) return null
    val decoded =
      runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimensionPx)
        val opts =
          BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
          }
        BitmapFactory.decodeFile(path, opts)?.let(::toGrayscaleImageBitmap)
      }.getOrNull()
    if (decoded != null) {
      cache.put(path, decoded)
    }
    return decoded
  }

  private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / sample > maxDim * 2 || height / sample > maxDim * 2) {
      sample *= 2
    }
    return sample
  }

  private fun toGrayscaleImageBitmap(source: Bitmap): ImageBitmap {
    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint =
      Paint().apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
      }
    canvas.drawBitmap(source, 0f, 0f, paint)
    if (out !== source) source.recycle()
    return out.asImageBitmap()
  }
}
