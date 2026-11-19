package com.example.emptyapp.ui.reader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.emptyapp.reader.pdf.PdfPageAnalysis
import com.example.emptyapp.reader.pdf.PdfRect
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PdfRendererHolder(
  private val descriptor: ParcelFileDescriptor,
  private val renderer: PdfRenderer,
  private val budget: PdfBitmapBudget = PdfBitmapBudget.fromRuntime(),
) {
  private val mutex = Mutex()
  @Volatile private var closed = false
  private val cropCache = HashMap<Int, NormalizedCropRect>()
  private val analysisCache = HashMap<Int, PdfPageAnalysis>()
  private val pageBitmapCache =
    object : LruCache<String, ImageBitmap>(budget.cacheKiloBytes) {
      override fun sizeOf(key: String, value: ImageBitmap): Int = imageKiloBytes(value)
    }
  val pageCount: Int = renderer.pageCount

  fun seedAnalyses(pages: List<PdfPageAnalysis>) {
    pages.forEach { analysisCache[it.pageIndex] = it }
  }

  fun cachedCropRect(index: Int): NormalizedCropRect? = cropCache[index]

  suspend fun prefetchAdjacent(
    centerIndex: Int,
    maxWidth: Int,
    maxHeight: Int,
    autoCrop: Boolean,
  ) {
    for (neighbor in listOf(centerIndex - 1, centerIndex + 1)) {
      if (neighbor in 0 until pageCount) {
        renderPage(neighbor, maxWidth, maxHeight, autoCrop)
      }
    }
  }

  suspend fun renderRegion(index: Int, region: PdfRect, maxWidth: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
      if (closed) return@withContext null
      val cacheKey = "reg:$index:${region.left}:${region.top}:${region.right}:${region.bottom}:$maxWidth"
      pageBitmapCache.get(cacheKey)?.let { return@withContext it }
      mutex.withLock {
        if (closed || index !in 0 until pageCount) return@withContext null
        pageBitmapCache.get(cacheKey)?.let { return@withContext it }
        renderWithMemoryRetry {
          renderer.openPage(index).use { page ->
            val leftPt = page.width * region.left
            val topPt = page.height * region.top
            val widthPt = (page.width * region.width).coerceAtLeast(1f)
            val heightPt = (page.height * region.height).coerceAtLeast(1f)
            val requestedScale = (maxWidth.toFloat() / widthPt).coerceIn(0.25f, 4f)
            val scale = requestedScale * budget.downscaleFactor(widthPt * requestedScale, heightPt * requestedScale)
            renderScaled(page, leftPt, topPt, widthPt, heightPt, scale)
          }
        }?.also { pageBitmapCache.put(cacheKey, it) }
      }
    }

  suspend fun renderPage(
    index: Int,
    maxWidth: Int,
    maxHeight: Int,
    autoCrop: Boolean = false,
  ): ImageBitmap? =
    withContext(Dispatchers.IO) {
      if (closed) return@withContext null
      val cacheKey = "$index:$maxWidth:$maxHeight:$autoCrop"
      pageBitmapCache.get(cacheKey)?.let { return@withContext it }
      mutex.withLock {
        if (closed) return@withContext null
        pageBitmapCache.get(cacheKey)?.let { return@withContext it }
        if (index !in 0 until pageCount) return@withContext null
        renderWithMemoryRetry {
          renderer.openPage(index).use { page ->
            val fitPageScale = minOf(maxWidth.toFloat() / page.width, maxHeight.toFloat() / page.height)
            val crop = if (autoCrop) getOrDetectCropRect(index, page) else NormalizedCropRect.FULL
            val leftPt = page.width * crop.left
            val topPt = page.height * crop.top
            val widthPt = (page.width * crop.width).coerceAtLeast(1f)
            val heightPt = (page.height * crop.height).coerceAtLeast(1f)
            val requestedScale =
              if (crop == NormalizedCropRect.FULL) {
                fitPageScale
              } else {
                minOf(maxWidth.toFloat() / widthPt, maxHeight.toFloat() / heightPt)
                  .coerceAtMost(fitPageScale * 3.5f)
              }
            val scale = requestedScale * budget.downscaleFactor(widthPt * requestedScale, heightPt * requestedScale)
            renderScaled(page, leftPt, topPt, widthPt, heightPt, scale)
          }
        }?.also { pageBitmapCache.put(cacheKey, it) }
      }
    }

  private fun renderScaled(
    page: PdfRenderer.Page,
    leftPt: Float,
    topPt: Float,
    widthPt: Float,
    heightPt: Float,
    scale: Float,
  ): ImageBitmap {
    val width = (widthPt * scale).toInt().coerceAtLeast(1)
    val height = (heightPt * scale).toInt().coerceAtLeast(1)
    val transform =
      Matrix().apply {
        setScale(scale, scale)
        postTranslate(-leftPt * scale, -topPt * scale)
      }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(AndroidColor.WHITE)
    page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return bitmap.asImageBitmap()
  }

  private inline fun renderWithMemoryRetry(render: () -> ImageBitmap): ImageBitmap? =
    try {
      render()
    } catch (_: OutOfMemoryError) {
      pageBitmapCache.evictAll()
      runCatching { render() }.getOrNull()
    } catch (_: Exception) {
      null
    }

  private fun getOrDetectCropRect(index: Int, page: PdfRenderer.Page): NormalizedCropRect {
    cropCache[index]?.let { return it }
    val thumbScale = minOf(320f / page.width, 320f / page.height).coerceAtMost(1f)
    val thumbWidth = (page.width * thumbScale).toInt().coerceAtLeast(1)
    val thumbHeight = (page.height * thumbScale).toInt().coerceAtLeast(1)
    val thumb = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
    return try {
      thumb.eraseColor(AndroidColor.WHITE)
      page.render(thumb, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      PdfMarginCropper.detectContentBounds(thumb).also { cropCache[index] = it }
    } finally {
      thumb.recycle()
    }
  }

  fun close() {
    if (closed) return
    closed = true
    pageBitmapCache.evictAll()
    CoroutineScope(Dispatchers.IO + NonCancellable).launch {
      mutex.withLock {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
      }
    }
  }

  private fun imageKiloBytes(image: ImageBitmap): Int =
    ((image.width.toLong() * image.height.toLong() * BYTES_PER_PIXEL) / 1024L)
      .coerceIn(1L, Int.MAX_VALUE.toLong())
      .toInt()
}

internal data class PdfBitmapBudget(
  val cacheKiloBytes: Int,
  val maxBitmapPixels: Long,
) {
  fun downscaleFactor(widthPx: Float, heightPx: Float): Float {
    val pixels = widthPx.toDouble() * heightPx.toDouble()
    if (pixels <= maxBitmapPixels) return 1f
    return sqrt(maxBitmapPixels / pixels).toFloat()
  }

  companion object {
    private const val MAX_CACHE_BYTES = 96L * 1024L * 1024L
    private const val MIN_CACHE_BYTES = 16L * 1024L * 1024L
    private const val MAX_SINGLE_BITMAP_BYTES = 32L * 1024L * 1024L

    fun fromHeapBytes(maxHeapBytes: Long): PdfBitmapBudget {
      val cacheBytes = (maxHeapBytes / 8L).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
      val singleBytes = minOf(MAX_SINGLE_BITMAP_BYTES, maxHeapBytes / 8L)
      return PdfBitmapBudget(
        cacheKiloBytes = (cacheBytes / 1024L).toInt(),
        maxBitmapPixels = (singleBytes / BYTES_PER_PIXEL).coerceAtLeast(1L),
      )
    }

    fun fromRuntime(): PdfBitmapBudget = fromHeapBytes(Runtime.getRuntime().maxMemory())
  }
}

private const val BYTES_PER_PIXEL = 4L

internal fun openPdfHolder(file: File): PdfRendererHolder? {
  val descriptor =
    runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
      ?: return null
  return runCatching { PdfRendererHolder(descriptor, PdfRenderer(descriptor)) }
    .onFailure { runCatching { descriptor.close() } }
    .getOrNull()
}
