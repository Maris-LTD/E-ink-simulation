package com.example.emptyapp.ui.reader

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import com.example.emptyapp.reader.EInkColorFilter
import com.example.emptyapp.reader.EInkSettings
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PdfPagerController {
  internal var onPrev: (() -> Unit)? = null
  internal var onNext: (() -> Unit)? = null
  internal var onJumpTo: ((Int) -> Unit)? = null

  fun goBackward() {
    onPrev?.invoke()
  }

  fun goForward() {
    onNext?.invoke()
  }

  fun jumpToPage(pageIndex: Int) {
    onJumpTo?.invoke(pageIndex)
  }
}

@Composable
fun PdfPagerReader(
  file: File,
  initialPage: Int,
  settings: EInkSettings,
  controller: PdfPagerController,
  onPageChanged: (pageIndex: Int, pageCount: Int) -> Unit,
  onCenterTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val holder = remember(file) { openPdfHolder(file) }
  DisposableEffect(holder) {
    onDispose { holder?.close() }
  }

  if (holder == null || holder.pageCount <= 0) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Không mở được tệp PDF", style = MaterialTheme.typography.bodyLarge)
    }
    return
  }

  val pageCount = holder.pageCount
  val pagerState =
    rememberPagerState(
      initialPage = initialPage.coerceIn(0, pageCount - 1),
      pageCount = { pageCount },
    )
  val scope = rememberCoroutineScope()
  var isZoomed by remember { mutableStateOf(false) }
  val colorFilter =
    remember(settings.grayscale, settings.contrast) {
      ColorFilter.colorMatrix(ColorMatrix(EInkColorFilter.matrixOf(settings).array))
    }

  DisposableEffect(controller, pagerState, pageCount) {
    controller.onPrev = {
      val target = (pagerState.currentPage - 1).coerceAtLeast(0)
      if (target != pagerState.currentPage) scope.launch { pagerState.scrollToPage(target) }
    }
    controller.onNext = {
      val target = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
      if (target != pagerState.currentPage) scope.launch { pagerState.scrollToPage(target) }
    }
    controller.onJumpTo = { index ->
      val target = index.coerceIn(0, pageCount - 1)
      scope.launch { pagerState.scrollToPage(target) }
    }
    onDispose {
      controller.onPrev = null
      controller.onNext = null
      controller.onJumpTo = null
    }
  }

  LaunchedEffect(pagerState, pageCount) {
    snapshotFlow { pagerState.settledPage }
      .distinctUntilChanged()
      .collect { page ->
        isZoomed = false
        onPageChanged(page, pageCount)
      }
  }

  BoxWithConstraints(
    modifier = modifier.fillMaxSize().background(Color(settings.tone.background)),
  ) {
    val targetWidthPx = constraints.maxWidth.coerceAtLeast(1)
    val targetHeightPx = constraints.maxHeight.coerceAtLeast(1)

    HorizontalPager(
      state = pagerState,
      beyondViewportPageCount = 1,
      userScrollEnabled = !isZoomed,
      flingBehavior =
        PagerDefaults.flingBehavior(
          state = pagerState,
          pagerSnapDistance = PagerSnapDistance.atMost(1),
          snapPositionalThreshold = 0.2f,
        ),
      modifier = Modifier.fillMaxSize(),
    ) { pageIndex ->
      var scale by remember(pageIndex) { mutableFloatStateOf(1f) }
      var offset by remember(pageIndex) { mutableStateOf(Offset.Zero) }
      val renderMultiplier = if (scale > 1.35f) 2 else 1
      var bitmap by remember(holder, pageIndex, targetWidthPx, targetHeightPx, renderMultiplier) {
        mutableStateOf<ImageBitmap?>(null)
      }

      LaunchedEffect(pagerState.settledPage, pageIndex) {
        if (pagerState.settledPage != pageIndex && (scale != 1f || offset != Offset.Zero)) {
          scale = 1f
          offset = Offset.Zero
        }
      }

      LaunchedEffect(scale, pagerState.currentPage, pageIndex) {
        if (pagerState.currentPage == pageIndex) {
          isZoomed = scale > 1.01f
        }
      }

      LaunchedEffect(holder, pageIndex, targetWidthPx, targetHeightPx, renderMultiplier) {
        bitmap =
          holder.renderPage(
            index = pageIndex,
            maxWidth = targetWidthPx * renderMultiplier,
            maxHeight = targetHeightPx * renderMultiplier,
          )
      }

      Box(
        modifier =
          Modifier.fillMaxSize()
            .clipToBounds()
            .pointerInput(pageIndex, targetWidthPx, targetHeightPx) {
              detectTapGestures(
                onDoubleTap = { tapOffset ->
                  if (scale > 1.01f) {
                    scale = 1f
                    offset = Offset.Zero
                  } else {
                    val targetScale = 2.2f
                    val centerX = targetWidthPx / 2f
                    val centerY = targetHeightPx / 2f
                    val rawOffset =
                      Offset(
                        x = (centerX - tapOffset.x) * (targetScale - 1f),
                        y = (centerY - tapOffset.y) * (targetScale - 1f),
                      )
                    scale = targetScale
                    offset = clampOffset(rawOffset, targetScale, targetWidthPx, targetHeightPx)
                  }
                },
                onTap = { tapOffset ->
                  if (scale > 1.01f) {
                    onCenterTap()
                  } else {
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    when {
                      tapOffset.x < width * 0.25f -> controller.goBackward()
                      tapOffset.x > width * 0.75f -> controller.goForward()
                      else -> onCenterTap()
                    }
                  }
                },
              )
            }
            .pointerInput(pageIndex, targetWidthPx, targetHeightPx) {
              awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                  val event = awaitPointerEvent()
                  val pointerCount = event.changes.count { it.pressed }
                  if (pointerCount > 1 || scale > 1.01f) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    val newOffset =
                      if (newScale <= 1.01f) Offset.Zero
                      else clampOffset(offset + pan, newScale, targetWidthPx, targetHeightPx)
                    val changed = abs(newScale - scale) > 0.001f || newOffset != offset
                    scale = if (newScale <= 1.01f) 1f else newScale
                    offset = newOffset
                    if (pointerCount > 1 || (scale > 1.01f && changed)) {
                      event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                  }
                } while (event.changes.any { it.pressed })
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        val current = bitmap
        if (current != null) {
          Image(
            bitmap = current,
            contentDescription = "Trang ${pageIndex + 1}",
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            modifier =
              Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
              },
          )
        }
      }
    }
  }
}

private fun clampOffset(offset: Offset, scale: Float, widthPx: Int, heightPx: Int): Offset {
  val maxX = ((widthPx * (scale - 1f)) / 2f).coerceAtLeast(0f)
  val maxY = ((heightPx * (scale - 1f)) / 2f).coerceAtLeast(0f)
  return Offset(
    x = offset.x.coerceIn(-maxX, maxX),
    y = offset.y.coerceIn(-maxY, maxY),
  )
}

private class PdfRendererHolder(
  private val descriptor: ParcelFileDescriptor,
  private val renderer: PdfRenderer,
) {
  private val mutex = Mutex()
  val pageCount: Int = renderer.pageCount

  suspend fun renderPage(index: Int, maxWidth: Int, maxHeight: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        if (index !in 0 until pageCount) return@withContext null
        runCatching {
          renderer.openPage(index).use { page ->
            val scale = minOf(maxWidth.toFloat() / page.width, maxHeight.toFloat() / page.height)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
          }
        }.getOrNull()
      }
    }

  fun close() {
    runCatching { renderer.close() }
    runCatching { descriptor.close() }
  }
}

private fun openPdfHolder(file: File): PdfRendererHolder? =
  runCatching {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(descriptor)
    PdfRendererHolder(descriptor, renderer)
  }.getOrNull()
