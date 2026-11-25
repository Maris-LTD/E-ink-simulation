package com.example.emptyapp.ui.eink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.emptyapp.DeviceMemoryProfile
import java.io.File

object CoverBitmapCache {
  private val grayscaleColorFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

  private val cache =
    object : LruCache<String, ImageBitmap>(DeviceMemoryProfile.current.coverCacheSizeKb) {
      override fun sizeOf(key: String, value: ImageBitmap): Int = computeBitmapKb(value)
    }

  fun configureForDevice() {
    cache.resize(DeviceMemoryProfile.current.coverCacheSizeKb)
  }

  fun trimMemory(level: Int) {
    when {
      DeviceMemoryProfile.shouldEvictAll(level) -> cache.evictAll()
      DeviceMemoryProfile.shouldTrimHalf(level) -> cache.trimToSize(cache.maxSize() / 2)
    }
  }

  fun clear() {
    cache.evictAll()
  }

  fun colorFilterFor(grayscale: Boolean): ColorFilter? =
    if (grayscale) grayscaleColorFilter else null

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
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
      }.getOrNull()
    if (decoded != null) {
      cache.put(path, decoded)
    }
    return decoded
  }

  internal fun computeBitmapKb(image: ImageBitmap): Int {
    val bytes =
      runCatching { image.asAndroidBitmap().allocationByteCount.toLong() }
        .getOrElse { image.width.toLong() * image.height.toLong() * 4L }
    return (bytes / 1024L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
  }

  private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= maxDim || height / (sample * 2) >= maxDim) {
      sample *= 2
    }
    return sample
  }
}
