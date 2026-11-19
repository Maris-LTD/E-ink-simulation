package com.example.emptyapp.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.emptyapp.reader.pdf.PdfDocumentCache
import com.example.emptyapp.reader.pdf.PdfPageAnalysis
import com.example.emptyapp.reader.pdf.PdfPageAnalyzer
import com.example.emptyapp.reader.pdf.PdfRect
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PdfRendererHolder(
  private val descriptor: ParcelFileDescriptor,
  private val renderer: PdfRenderer,
  private val file: File? = null,
  private val appContext: Context? = null,
  private val budget: PdfBitmapBudget = PdfBitmapBudget.fromRuntime(),
) {
  private val mutex = Mutex()
  @Volatile private var closed = false
  private val cropCache = ConcurrentHashMap<Int, NormalizedCropRect>()
  private val diskCache = appContext?.let { PdfDocumentCache(File(it.filesDir, "pdf_cache")) }
  private val analysisSession =
    PdfAnalysisSession(
      pageCount = renderer.pageCount,
      openDocument = ::openAnalysisDocument,
      loadCachedPage = ::loadCachedPageFromDisk,
    )
  private val pageBitmapCache =
    object : LruCache<String, ImageBitmap>(budget.cacheKiloBytes) {
      override fun sizeOf(key: String, value: ImageBitmap): Int = imageKiloBytes(value)
    }
  val pageCount: Int = renderer.pageCount
  val analysisVersion: StateFlow<Int>
    get() = analysisSession.analysisVersion

  fun seedAnalyses(pages: List<PdfPageAnalysis>) {
    if (!closed) {
      analysisSession.seedAnalyses(pages)
    }
  }

  fun cachedAnalysis(pageIndex: Int): PdfPageAnalysis? =
    if (closed) null else analysisSession.cachedAnalysis(pageIndex)

  suspend fun getOrAnalyzePage(pageIndex: Int): PdfPageAnalysis? =
    if (closed) null else analysisSession.getOrAnalyzePage(pageIndex)

  suspend fun prefetchAdjacentAnalyses(centerIndex: Int) {
    if (!closed) {
      analysisSession.prefetchAdjacentAnalyses(centerIndex)
    }
  }

  fun cachedCropRect(index: Int): NormalizedCropRect? = cropCache[index]

  suspend fun renderRegion(index: Int, region: PdfRect, maxWidth: Int): ImageBitmap? =
    renderRegionInternal(
      index = index,
      region = region,
      maxWidth = maxWidth,
      maxHeight = null,
      maxRequestedScale = 4f,
      cacheKey = "reg:$index:${region.left}:${region.top}:${region.right}:${region.bottom}:$maxWidth",
    )

  suspend fun renderViewportTile(
    index: Int,
    region: PdfRect,
    maxWidth: Int,
    maxHeight: Int,
  ): ImageBitmap? =
    renderRegionInternal(
      index = index,
      region = region,
      maxWidth = maxWidth.coerceAtLeast(1),
      maxHeight = maxHeight.coerceAtLeast(1),
      maxRequestedScale = null,
      cacheKey = null,
    )

  private suspend fun renderRegionInternal(
    index: Int,
    region: PdfRect,
    maxWidth: Int,
    maxHeight: Int?,
    maxRequestedScale: Float?,
    cacheKey: String?,
  ): ImageBitmap? =
    withContext(Dispatchers.IO) {
      if (closed) return@withContext null
      if (cacheKey != null) {
        pageBitmapCache.get(cacheKey)?.let { return@withContext it }
      }
      mutex.withLock {
        if (closed || index !in 0 until pageCount) return@withContext null
        if (cacheKey != null) {
          pageBitmapCache.get(cacheKey)?.let { return@withContext it }
        }
        renderWithMemoryRetry { retryScale ->
          renderer.openPage(index).use { page ->
            val leftPt = page.width * region.left
            val topPt = page.height * region.top
            val widthPt = (page.width * region.width).coerceAtLeast(1f)
            val heightPt = (page.height * region.height).coerceAtLeast(1f)
            val fitScale =
              if (maxHeight != null) {
                minOf(maxWidth.toFloat() / widthPt, maxHeight.toFloat() / heightPt)
              } else {
                maxWidth.toFloat() / widthPt
              }
            val requestedScale =
              if (maxRequestedScale != null) {
                fitScale.coerceIn(0.25f, maxRequestedScale)
              } else {
                fitScale.coerceAtLeast(0.25f)
              }
            val scale =
              requestedScale *
                budget.downscaleFactor(widthPt * requestedScale, heightPt * requestedScale) *
                retryScale
            renderScaled(page, leftPt, topPt, widthPt, heightPt, scale)
          }
        }?.also { bmp ->
          if (cacheKey != null) {
            pageBitmapCache.put(cacheKey, bmp)
          }
        }
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
        renderWithMemoryRetry { retryScale ->
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
            val scale =
              requestedScale *
                budget.downscaleFactor(widthPt * requestedScale, heightPt * requestedScale) *
                retryScale
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

  private inline fun renderWithMemoryRetry(render: (retryScale: Float) -> ImageBitmap): ImageBitmap? =
    com.example.emptyapp.ui.reader.renderWithMemoryRetry(
      onEvictCache = { pageBitmapCache.evictAll() },
      render = render,
    )

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
    analysisSession.close()
    CoroutineScope(Dispatchers.IO + NonCancellable).launch {
      mutex.withLock {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
      }
    }
  }

  private fun loadCachedPageFromDisk(pageIndex: Int): PdfPageAnalysis? {
    val targetFile = file ?: return null
    val cache = diskCache ?: return null
    return cache.loadPages(targetFile, listOf(pageIndex))[pageIndex]
  }

  private fun openAnalysisDocument(): PdfAnalysisDocument? {
    val targetFile = file ?: return null
    if (!targetFile.exists()) return null
    appContext?.let { PdfPageAnalyzer.ensureInitialized(it) }
    val document = PDDocument.load(targetFile)
    val stripper = PdfPageAnalyzer.ReusableLineStripper()
    return object : PdfAnalysisDocument {
      override fun analyzePage(pageIndex: Int): PdfPageAnalysis? {
        if (pageIndex !in 0 until document.numberOfPages) return null
        return PdfPageAnalyzer.analyzePageSafely(pageIndex) {
          PdfPageAnalyzer.analyzePage(document, pageIndex, stripper)
        }
      }

      override fun close() {
        runCatching { document.close() }
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
internal const val OOM_RETRY_SCALE_FACTOR = 0.70710677f

internal inline fun <T> renderWithMemoryRetry(
  onEvictCache: () -> Unit,
  render: (retryScale: Float) -> T,
): T? =
  try {
    render(1f)
  } catch (_: OutOfMemoryError) {
    onEvictCache()
    try {
      render(OOM_RETRY_SCALE_FACTOR)
    } catch (_: OutOfMemoryError) {
      null
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
  } catch (e: CancellationException) {
    throw e
  } catch (_: Exception) {
    null
  }

internal fun openPdfHolder(file: File, context: Context? = null): PdfRendererHolder? {
  val descriptor =
    runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
      ?: return null
  return runCatching {
    PdfRendererHolder(
      descriptor = descriptor,
      renderer = PdfRenderer(descriptor),
      file = file,
      appContext = context?.applicationContext,
    )
  }
    .onFailure { runCatching { descriptor.close() } }
    .getOrNull()
}
