package com.example.emptyapp.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emptyapp.R
import com.example.emptyapp.reader.EInkColorFilter
import com.example.emptyapp.reader.EInkFont
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.pdf.PaginatedReflowElement
import com.example.emptyapp.reader.pdf.PdfDocumentCache
import com.example.emptyapp.reader.pdf.PdfPageAnalysis
import com.example.emptyapp.reader.pdf.PdfPageAnalyzer
import com.example.emptyapp.reader.pdf.PdfReadingMode
import com.example.emptyapp.reader.pdf.PdfRect
import com.example.emptyapp.reader.pdf.PdfReflowEngine
import com.example.emptyapp.reader.pdf.PdfReflowPaginator
import com.example.emptyapp.reader.pdf.PdfRegionNavigator
import com.example.emptyapp.reader.pdf.ReflowImageKind
import com.example.emptyapp.reader.pdf.ReflowPageMeta
import com.example.emptyapp.reader.pdf.ReflowScreenPage
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalTextApi::class)
@Composable
fun PdfPagerReader(
  file: File,
  initialPage: Int,
  settings: EInkSettings,
  readingMode: PdfReadingMode = PdfReadingMode.SMART_ORIGINAL,
  controller: PdfPagerController,
  onPageChanged: (pageIndex: Int, pageCount: Int) -> Unit,
  onCenterTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val holder = remember(file) { openPdfHolder(file) }
  DisposableEffect(holder) {
    onDispose { holder?.close() }
  }

  if (holder == null || holder.pageCount <= 0) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(stringResource(R.string.pdf_load_error), style = MaterialTheme.typography.bodyLarge)
    }
    return
  }

  val pageCount = holder.pageCount
  val cache = remember(context) { PdfDocumentCache(File(context.filesDir, "pdf_cache")) }
  var allAnalyses by remember(file) { mutableStateOf<List<PdfPageAnalysis>?>(null) }
  var analyzeDone by remember(file) { mutableIntStateOf(0) }
  var analyzeTotal by remember(file) { mutableIntStateOf(pageCount) }

  LaunchedEffect(file, holder) {
    val cached = withContext(Dispatchers.IO) { cache.load(file) }
    if (cached != null && cached.size == pageCount) {
      holder.seedAnalyses(cached)
      allAnalyses = cached
    } else {
      val computed =
        withContext(Dispatchers.IO) {
          PdfPageAnalyzer.analyzeAllPages(file) { done, total ->
            analyzeDone = done
            analyzeTotal = total
          }.also { pages ->
            if (pages.isNotEmpty()) cache.save(file, pages)
          }
        }
      holder.seedAnalyses(computed)
      allAnalyses = computed
    }
  }

  val loadedAnalyses = allAnalyses
  if (loadedAnalyses == null) {
    Box(
      modifier = modifier.fillMaxSize().background(Color(settings.tone.background)),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CircularProgressIndicator(color = Color(settings.tone.text))
        Text(
          text =
            if (analyzeDone > 0) {
              stringResource(R.string.pdf_analyzing_progress, analyzeDone, analyzeTotal)
            } else {
              stringResource(R.string.reader_opening)
            },
          color = Color(settings.tone.text),
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    return
  }

  val colorFilter =
    remember(settings.grayscale, settings.contrast, settings.tone) {
      ColorFilter.colorMatrix(ColorMatrix(EInkColorFilter.matrixOf(settings).array))
    }

  var currentPdfPage by remember(file) { mutableIntStateOf(initialPage.coerceIn(0, pageCount - 1)) }

  val isReflowMode = readingMode == PdfReadingMode.HYBRID_REFLOW
  val reflowMetas by
    produceState<List<ReflowPageMeta>?>(null, loadedAnalyses, isReflowMode) {
      value =
        if (isReflowMode) {
          withContext(Dispatchers.Default) { buildReflowMetas(loadedAnalyses) }
        } else {
          null
        }
    }

  val bodyWeightValue = (settings.fontWeight * 400.0).roundToInt().coerceIn(100, 900)
  val headingWeightValue = (bodyWeightValue + 150).coerceIn(100, 900)
  val bodyFontWeight = FontWeight(bodyWeightValue)
  val headingFontWeight = FontWeight(headingWeightValue)

  val reflowFontFamily =
    remember(settings.font, bodyWeightValue, headingWeightValue) {
      if (settings.font == EInkFont.SERIF) {
        FontFamily(
          Font(
            path = "fonts/literata.ttf",
            assetManager = context.assets,
            weight = bodyFontWeight,
            variationSettings = FontVariation.Settings(FontVariation.weight(bodyWeightValue)),
          ),
          Font(
            path = "fonts/literata.ttf",
            assetManager = context.assets,
            weight = headingFontWeight,
            variationSettings = FontVariation.Settings(FontVariation.weight(headingWeightValue)),
          ),
        )
      } else {
        FontFamily(
          Font(
            resId = R.font.rubik,
            weight = bodyFontWeight,
            variationSettings = FontVariation.Settings(FontVariation.weight(bodyWeightValue)),
          ),
          Font(
            resId = R.font.rubik,
            weight = headingFontWeight,
            variationSettings = FontVariation.Settings(FontVariation.weight(headingWeightValue)),
          ),
        )
      }
    }

  val baseFontSp = (16f * settings.fontScale.toFloat()).coerceIn(11f, 64f)
  val lineHeightSp = baseFontSp * settings.lineHeight.toFloat().coerceIn(1.0f, 2.5f)
  val marginBase = settings.pageMargins.toFloat().coerceIn(0f, 3f)
  val horizontalPaddingDp = (marginBase * 16f).dp
  val topPaddingDp = (16f + marginBase * 8f).dp
  val bottomPaddingDp = (32f + marginBase * 8f).dp
  val paragraphSpacingDp = (settings.paragraphSpacing.toFloat().coerceAtLeast(0f) * 16f).dp
  val textAlign = if (settings.justifyText) TextAlign.Justify else TextAlign.Start

  BoxWithConstraints(
    modifier = modifier.fillMaxSize().background(Color(settings.tone.background)),
  ) {
    val targetWidthPx = constraints.maxWidth.coerceAtLeast(1)
    val targetHeightPx = constraints.maxHeight.coerceAtLeast(1)

    val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    val safeLeftPx = safeInsets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val safeRightPx = safeInsets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val safeTopPx = safeInsets.getTop(density)
    val safeBottomPx = safeInsets.getBottom(density)
    val contentWidthPx =
      (targetWidthPx - safeLeftPx - safeRightPx - with(density) { (horizontalPaddingDp * 2).roundToPx() })
        .coerceAtLeast(100)
    val contentHeightPx =
      (targetHeightPx - safeTopPx - safeBottomPx - with(density) { (topPaddingDp + bottomPaddingDp).roundToPx() })
        .coerceAtLeast(120)
    val paragraphSpacingPx = with(density) { paragraphSpacingDp.roundToPx() }

    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    val backgroundTextMeasurer =
      remember(fontFamilyResolver, density, layoutDirection) {
        TextMeasurer(fontFamilyResolver, density, layoutDirection, cacheSize = 0)
      }
    val bodyTextStyle =
      remember(reflowFontFamily, bodyFontWeight, baseFontSp, lineHeightSp, textAlign) {
        TextStyle(
          fontFamily = reflowFontFamily,
          fontWeight = bodyFontWeight,
          fontSize = baseFontSp.sp,
          lineHeight = lineHeightSp.sp,
          textAlign = textAlign,
        )
      }
    val headingTextStyle =
      remember(reflowFontFamily, headingFontWeight, baseFontSp, lineHeightSp) {
        TextStyle(
          fontFamily = reflowFontFamily,
          fontWeight = headingFontWeight,
          fontSize = (baseFontSp * 1.25f).sp,
          lineHeight = (lineHeightSp * 1.2f).sp,
          textAlign = TextAlign.Start,
        )
      }

    val reflowScreensState by
      produceState<List<ReflowScreenPage>?>(
        null,
        reflowMetas,
        contentWidthPx,
        contentHeightPx,
        paragraphSpacingPx,
        bodyTextStyle,
        headingTextStyle,
        backgroundTextMeasurer,
      ) {
        val metas = reflowMetas
        value =
          if (metas == null) {
            null
          } else {
            withContext(Dispatchers.Default) {
              val job = coroutineContext[Job]
              PdfReflowPaginator.paginate(
                pages = metas,
                maxContentWidthPx = contentWidthPx,
                maxContentHeightPx = contentHeightPx,
                paragraphSpacingPx = paragraphSpacingPx,
              ) { text, isHeading, maxW ->
                if (job?.isActive == false) throw CancellationException()
                backgroundTextMeasurer.measure(
                  text = text,
                  style = if (isHeading) headingTextStyle else bodyTextStyle,
                  constraints = Constraints(maxWidth = maxW),
                ).size.height
              }
            }
          }
      }

    if (isReflowMode && reflowScreensState == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          CircularProgressIndicator(color = Color(settings.tone.text))
          Text(
            text = stringResource(R.string.pdf_reflow_preparing),
            color = Color(settings.tone.text),
            style = MaterialTheme.typography.bodyLarge,
          )
        }
      }
      return@BoxWithConstraints
    }
    val reflowScreens = reflowScreensState.orEmpty()

    // Pre-process & pre-warm adjacent pages (page - 1 and page + 1)
    LaunchedEffect(currentPdfPage, targetWidthPx, targetHeightPx, settings.pdfAutoCrop, readingMode) {
      val autoCrop = settings.pdfAutoCrop && readingMode != PdfReadingMode.GUIDED_COLUMN
      val mult = if (readingMode == PdfReadingMode.GUIDED_COLUMN) 2 else 1
      holder.prefetchAdjacent(
        centerIndex = currentPdfPage,
        maxWidth = targetWidthPx * mult,
        maxHeight = targetHeightPx * mult,
        autoCrop = autoCrop,
      )
    }

    if (readingMode == PdfReadingMode.HYBRID_REFLOW && reflowScreens.isNotEmpty()) {
      var zoomedImageElement by remember { mutableStateOf<PaginatedReflowElement.InlineImage?>(null) }
      val startScreen = remember(reflowScreens) {
        PdfReflowPaginator.findScreenIndexForPdfPage(reflowScreens, currentPdfPage)
      }
      val reflowPagerState =
        rememberPagerState(
          initialPage = startScreen.coerceIn(0, reflowScreens.lastIndex),
          pageCount = { reflowScreens.size },
        )
      val scope = rememberCoroutineScope()

      DisposableEffect(controller, reflowPagerState, reflowScreens) {
        controller.onPrev = {
          val target = (reflowPagerState.currentPage - 1).coerceAtLeast(0)
          if (target != reflowPagerState.currentPage) scope.launch { reflowPagerState.scrollToPage(target) }
        }
        controller.onNext = {
          val target = (reflowPagerState.currentPage + 1).coerceAtMost(reflowScreens.lastIndex)
          if (target != reflowPagerState.currentPage) scope.launch { reflowPagerState.scrollToPage(target) }
        }
        controller.onJumpTo = { pdfIdx ->
          val screenIdx = PdfReflowPaginator.findScreenIndexForPdfPage(reflowScreens, pdfIdx)
          scope.launch { reflowPagerState.scrollToPage(screenIdx) }
        }
        onDispose {
          controller.onPrev = null
          controller.onNext = null
          controller.onJumpTo = null
        }
      }

      LaunchedEffect(reflowPagerState, reflowScreens) {
        snapshotFlow { reflowPagerState.settledPage }
          .distinctUntilChanged()
          .collect { screenIdx ->
            val screen = reflowScreens.getOrNull(screenIdx) ?: return@collect
            val snapPage = PdfReflowPaginator.resolveSnapBackPage(screen)
            currentPdfPage = snapPage
            onPageChanged(snapPage, pageCount)
          }
      }

      HorizontalPager(
        state = reflowPagerState,
        beyondViewportPageCount = 1,
        flingBehavior =
          PagerDefaults.flingBehavior(
            state = reflowPagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
            snapPositionalThreshold = 0.2f,
          ),
        modifier = Modifier.fillMaxSize(),
      ) { screenIndex ->
        val screen = reflowScreens[screenIndex]
        val singleFallback = screen.elements.singleOrNull() as? PaginatedReflowElement.FallbackPage
        if (singleFallback != null) {
          var fallbackBitmap by remember(holder, singleFallback.sourcePage, targetWidthPx, targetHeightPx, settings.pdfAutoCrop) {
            mutableStateOf<ImageBitmap?>(null)
          }
          LaunchedEffect(holder, singleFallback.sourcePage, targetWidthPx, targetHeightPx, settings.pdfAutoCrop) {
            fallbackBitmap =
              holder.renderPage(
                index = singleFallback.sourcePage,
                maxWidth = targetWidthPx,
                maxHeight = targetHeightPx,
                autoCrop = settings.pdfAutoCrop,
              )
          }
          Box(
            modifier =
              Modifier.fillMaxSize()
                .pointerInput(controller, onCenterTap) {
                  detectTapGestures(
                    onTap = { tapOffset ->
                      val w = size.width.toFloat().coerceAtLeast(1f)
                      when {
                        tapOffset.x < w * 0.25f -> controller.goBackward()
                        tapOffset.x > w * 0.75f -> controller.goForward()
                        else -> onCenterTap()
                      }
                    },
                  )
                },
            contentAlignment = Alignment.Center,
          ) {
            fallbackBitmap?.let { bmp ->
              Image(
                bitmap = bmp,
                contentDescription = "Trang ${singleFallback.sourcePage + 1}",
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
              )
            }
          }
        } else {
          PdfPaginatedReflowScreen(
            screen = screen,
            holder = holder,
            maxWidthPx = contentWidthPx,
            settings = settings,
            bodyTextStyle = bodyTextStyle,
            headingTextStyle = headingTextStyle,
            horizontalPaddingDp = horizontalPaddingDp,
            topPaddingDp = topPaddingDp,
            bottomPaddingDp = bottomPaddingDp,
            paragraphSpacingDp = paragraphSpacingDp,
            colorFilter = colorFilter,
            onPrevPage = { controller.goBackward() },
            onNextPage = { controller.goForward() },
            onCenterTap = onCenterTap,
            onImageTap = { element -> zoomedImageElement = element },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }

      zoomedImageElement?.let { zoomed ->
        BackHandler { zoomedImageElement = null }
        PdfReflowImageZoomOverlay(
          element = zoomed,
          holder = holder,
          maxRenderWidthPx = targetWidthPx * 2,
          settings = settings,
          colorFilter = colorFilter,
          onDismiss = { zoomedImageElement = null },
          modifier = Modifier.fillMaxSize(),
        )
      }
    } else {
      val pagerState =
        rememberPagerState(
          initialPage = currentPdfPage.coerceIn(0, pageCount - 1),
          pageCount = { pageCount },
        )
      val scope = rememberCoroutineScope()
      var isZoomed by remember { mutableStateOf(false) }
      var resetActivePageZoom by remember { mutableStateOf<(() -> Unit)?>(null) }
      var activePageRegionPrev by remember { mutableStateOf<(() -> Boolean)?>(null) }
      var activePageRegionNext by remember { mutableStateOf<(() -> Boolean)?>(null) }
      var pendingBackwardLandingPage by remember { mutableStateOf<Int?>(null) }

      BackHandler(enabled = isZoomed) {
        resetActivePageZoom?.invoke()
      }

      DisposableEffect(controller, pagerState, pageCount) {
        controller.onPrev = {
          if (activePageRegionPrev?.invoke() != true) {
            val target = (pagerState.currentPage - 1).coerceAtLeast(0)
            if (target != pagerState.currentPage) {
              pendingBackwardLandingPage = target
              scope.launch { pagerState.scrollToPage(target) }
            }
          }
        }
        controller.onNext = {
          if (activePageRegionNext?.invoke() != true) {
            val target = (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
            if (target != pagerState.currentPage) {
              pendingBackwardLandingPage = null
              scope.launch { pagerState.scrollToPage(target) }
            }
          }
        }
        controller.onJumpTo = { index ->
          pendingBackwardLandingPage = null
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
            currentPdfPage = page
            onPageChanged(page, pageCount)
          }
      }

      HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = !isZoomed && readingMode != PdfReadingMode.GUIDED_COLUMN,
        flingBehavior =
          PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
            snapPositionalThreshold = 0.2f,
          ),
        modifier = Modifier.fillMaxSize(),
      ) { pageIndex ->
        var scale by remember(pageIndex, readingMode) { mutableFloatStateOf(1f) }
        var offset by remember(pageIndex, readingMode) { mutableStateOf(Offset.Zero) }
        val renderMultiplier = if (scale > 1.35f || readingMode == PdfReadingMode.GUIDED_COLUMN) 2 else 1
        val autoCrop = settings.pdfAutoCrop && readingMode != PdfReadingMode.GUIDED_COLUMN
        val pageAnalysis = loadedAnalyses.getOrNull(pageIndex)
        var guidedRegionIndex by remember(pageIndex, readingMode) { mutableIntStateOf(0) }
        var bitmap by remember(holder, pageIndex, targetWidthPx, targetHeightPx, renderMultiplier, autoCrop) {
          mutableStateOf<ImageBitmap?>(null)
        }

        val guidedRegions =
          remember(pageAnalysis, targetWidthPx, targetHeightPx, readingMode) {
            if (readingMode == PdfReadingMode.GUIDED_COLUMN) {
              PdfRegionNavigator.buildGuidedRegions(
                analysis = pageAnalysis,
                viewportAspect = targetWidthPx.toFloat() / targetHeightPx.toFloat(),
              )
            } else {
              emptyList()
            }
          }

        DisposableEffect(pagerState.currentPage, pageIndex, readingMode, guidedRegions) {
          if (pagerState.currentPage == pageIndex && readingMode == PdfReadingMode.GUIDED_COLUMN && guidedRegions.isNotEmpty()) {
            activePageRegionPrev = {
              if (guidedRegionIndex > 0) {
                guidedRegionIndex -= 1
                true
              } else {
                false
              }
            }
            activePageRegionNext = {
              if (guidedRegionIndex < guidedRegions.lastIndex) {
                guidedRegionIndex += 1
                true
              } else {
                false
              }
            }
          } else if (pagerState.currentPage == pageIndex) {
            activePageRegionPrev = null
            activePageRegionNext = null
          }
          onDispose {
            if (pagerState.currentPage == pageIndex) {
              activePageRegionPrev = null
              activePageRegionNext = null
            }
          }
        }

        LaunchedEffect(pagerState.settledPage, pageIndex, readingMode) {
          if (pagerState.settledPage != pageIndex && readingMode != PdfReadingMode.GUIDED_COLUMN && (scale != 1f || offset != Offset.Zero)) {
            scale = 1f
            offset = Offset.Zero
          }
        }

        LaunchedEffect(scale, pagerState.currentPage, pageIndex, readingMode) {
          if (pagerState.currentPage == pageIndex) {
            isZoomed = readingMode != PdfReadingMode.GUIDED_COLUMN && scale > 1.01f
            resetActivePageZoom =
              if (isZoomed) {
                {
                  scale = 1f
                  offset = Offset.Zero
                }
              } else {
                null
              }
          }
        }

        LaunchedEffect(readingMode, guidedRegions, pendingBackwardLandingPage, pageIndex) {
          if (readingMode == PdfReadingMode.GUIDED_COLUMN && guidedRegions.isNotEmpty()) {
            if (pendingBackwardLandingPage == pageIndex) {
              guidedRegionIndex = guidedRegions.lastIndex
              pendingBackwardLandingPage = null
            } else if (guidedRegionIndex > guidedRegions.lastIndex) {
              guidedRegionIndex = 0
            }
          }
        }

        LaunchedEffect(readingMode, guidedRegions, guidedRegionIndex, bitmap, targetWidthPx, targetHeightPx) {
          val currentBmp = bitmap
          if (readingMode == PdfReadingMode.GUIDED_COLUMN && currentBmp != null && guidedRegions.isNotEmpty()) {
            val region = guidedRegions[guidedRegionIndex.coerceIn(0, guidedRegions.lastIndex)]
            val cam =
              PdfRegionNavigator.computeCameraTransform(
                region = region,
                bitmapWidth = currentBmp.width.toFloat(),
                bitmapHeight = currentBmp.height.toFloat(),
                containerWidth = targetWidthPx.toFloat(),
                containerHeight = targetHeightPx.toFloat(),
              )
            scale = cam.scale
            offset = Offset(cam.offsetX, cam.offsetY)
          }
        }

        LaunchedEffect(holder, pageIndex, targetWidthPx, targetHeightPx, renderMultiplier, autoCrop) {
          bitmap =
            holder.renderPage(
              index = pageIndex,
              maxWidth = targetWidthPx * renderMultiplier,
              maxHeight = targetHeightPx * renderMultiplier,
              autoCrop = autoCrop,
            )
        }

        Box(
          modifier =
            Modifier.fillMaxSize()
              .clipToBounds()
              .pointerInput(pageIndex, targetWidthPx, targetHeightPx, readingMode, pageAnalysis, bitmap, autoCrop) {
                detectTapGestures(
                  onDoubleTap = { tapOffset ->
                    if (readingMode == PdfReadingMode.GUIDED_COLUMN) return@detectTapGestures
                    if (scale > 1.01f) {
                      scale = 1f
                      offset = Offset.Zero
                    } else {
                      val currentBmp = bitmap
                      val tapRegion =
                        if (currentBmp != null && !autoCrop) {
                          val fitScale = minOf(targetWidthPx.toFloat() / currentBmp.width, targetHeightPx.toFloat() / currentBmp.height)
                          val dispW = currentBmp.width * fitScale
                          val dispH = currentBmp.height * fitScale
                          val imgLeft = (targetWidthPx - dispW) / 2f
                          val imgTop = (targetHeightPx - dispH) / 2f
                          val normX = ((tapOffset.x - imgLeft) / dispW).coerceIn(0f, 1f)
                          val normY = ((tapOffset.y - imgTop) / dispH).coerceIn(0f, 1f)
                          PdfRegionNavigator.findTapTargetRect(pageAnalysis, normX, normY)
                        } else {
                          null
                        }
                      if (tapRegion != null && currentBmp != null) {
                        val cam =
                          PdfRegionNavigator.computeCameraTransform(
                            region = tapRegion,
                            bitmapWidth = currentBmp.width.toFloat(),
                            bitmapHeight = currentBmp.height.toFloat(),
                            containerWidth = targetWidthPx.toFloat(),
                            containerHeight = targetHeightPx.toFloat(),
                          )
                        scale = cam.scale
                        offset = Offset(cam.offsetX, cam.offsetY)
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
                    }
                  },
                  onTap = { tapOffset ->
                    if (readingMode != PdfReadingMode.GUIDED_COLUMN && scale > 1.01f) {
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
              .pointerInput(pageIndex, targetWidthPx, targetHeightPx, readingMode) {
                val swipeThresholdPx = 42.dp.toPx()
                awaitEachGesture {
                  awaitFirstDown(requireUnconsumed = false)
                  var guidedDragX = 0f
                  var guidedDragY = 0f
                  var guidedSwiped = false
                  do {
                    val event = awaitPointerEvent()
                    val pointerCount = event.changes.count { it.pressed }
                    if (readingMode == PdfReadingMode.GUIDED_COLUMN && pointerCount == 1) {
                      val pan = event.calculatePan()
                      guidedDragX += pan.x
                      guidedDragY += pan.y
                      if (!guidedSwiped &&
                        abs(guidedDragX) >= swipeThresholdPx &&
                        abs(guidedDragX) > abs(guidedDragY) * 1.25f
                      ) {
                        guidedSwiped = true
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                        if (guidedDragX < 0f) {
                          controller.goForward()
                        } else {
                          controller.goBackward()
                        }
                      }
                    } else if (pointerCount > 1 || (readingMode != PdfReadingMode.GUIDED_COLUMN && scale > 1.01f)) {
                      val zoom = event.calculateZoom()
                      val rawPan = event.calculatePan()
                      val (lockedX, lockedY) = PdfRegionNavigator.applyHorizontalLock(rawPan.x, rawPan.y)
                      val pan = Offset(lockedX, lockedY)
                      val newScale = (scale * zoom).coerceIn(1f, 4.5f)
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
          if (readingMode == PdfReadingMode.GUIDED_COLUMN && guidedRegions.size > 1) {
            val bgColor = Color(settings.tone.background)
            val inkColor = Color(settings.tone.text)
            Box(
              modifier =
                Modifier.align(Alignment.TopEnd)
                  .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                  .padding(top = 10.dp, end = 12.dp)
                  .background(bgColor.copy(alpha = 0.92f))
                  .border(1.dp, inkColor)
                  .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
              Text(
                text = stringResource(R.string.pdf_guided_region_badge, guidedRegionIndex + 1, guidedRegions.size),
                style = MaterialTheme.typography.labelSmall,
                color = inkColor,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PdfPaginatedReflowScreen(
  screen: ReflowScreenPage,
  holder: PdfRendererHolder,
  maxWidthPx: Int,
  settings: EInkSettings,
  bodyTextStyle: TextStyle,
  headingTextStyle: TextStyle,
  horizontalPaddingDp: androidx.compose.ui.unit.Dp,
  topPaddingDp: androidx.compose.ui.unit.Dp,
  bottomPaddingDp: androidx.compose.ui.unit.Dp,
  paragraphSpacingDp: androidx.compose.ui.unit.Dp,
  colorFilter: ColorFilter,
  onPrevPage: () -> Unit,
  onNextPage: () -> Unit,
  onCenterTap: () -> Unit,
  onImageTap: (PaginatedReflowElement.InlineImage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val inkColor = Color(settings.tone.text)

  Column(
    modifier =
      modifier
        .background(Color(settings.tone.background))
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
          ),
        )
        .pointerInput(onPrevPage, onNextPage, onCenterTap) {
          detectTapGestures(
            onTap = { tapOffset ->
              val width = size.width.toFloat().coerceAtLeast(1f)
              when {
                tapOffset.x < width * 0.25f -> onPrevPage()
                tapOffset.x > width * 0.75f -> onNextPage()
                else -> onCenterTap()
              }
            },
          )
        }
        .padding(
          start = horizontalPaddingDp,
          end = horizontalPaddingDp,
          top = topPaddingDp,
          bottom = bottomPaddingDp,
        ),
    verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp),
  ) {
    screen.elements.forEach { element ->
      when (element) {
        is PaginatedReflowElement.ParagraphSlice -> {
          Text(
            text = element.text,
            color = inkColor,
            style = if (element.isHeading) headingTextStyle else bodyTextStyle,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        is PaginatedReflowElement.InlineImage -> {
          val renderWidthPx =
            if (element.displayWidthPx > 0) element.displayWidthPx else maxWidthPx
          var regionBitmap by remember(holder, element.sourcePage, element.bounds, renderWidthPx) {
            mutableStateOf<ImageBitmap?>(null)
          }
          LaunchedEffect(holder, element.sourcePage, element.bounds, renderWidthPx) {
            regionBitmap = holder.renderRegion(element.sourcePage, element.bounds, renderWidthPx)
          }
          val imgHeightDp = with(density) { element.displayHeightPx.toDp() }
          val isSymbol = element.kind == ReflowImageKind.SYMBOL_STACK
          val imgWidthDp = with(density) { renderWidthPx.toDp() }
          val boxModifier =
            if (isSymbol) {
              Modifier.fillMaxWidth().height(imgHeightDp)
            } else {
              Modifier.fillMaxWidth()
                .height(imgHeightDp)
                .clickable { onImageTap(element) }
            }
          Box(
            modifier = boxModifier,
            contentAlignment = Alignment.Center,
          ) {
            regionBitmap?.let { bmp ->
              Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier =
                  if (isSymbol) {
                    Modifier.width(imgWidthDp).fillMaxHeight()
                  } else {
                    Modifier.fillMaxSize()
                  },
              )
            }
          }
        }
        is PaginatedReflowElement.FallbackPage -> Unit
      }
    }
  }
}

@Composable
private fun PdfReflowImageZoomOverlay(
  element: PaginatedReflowElement.InlineImage,
  holder: PdfRendererHolder,
  maxRenderWidthPx: Int,
  settings: EInkSettings,
  colorFilter: ColorFilter,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onDismiss)
  var bitmap by remember(holder, element.sourcePage, element.bounds, maxRenderWidthPx) {
    mutableStateOf<ImageBitmap?>(null)
  }
  LaunchedEffect(holder, element.sourcePage, element.bounds, maxRenderWidthPx) {
    bitmap = holder.renderRegion(element.sourcePage, element.bounds, maxRenderWidthPx)
  }

  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var containerSize by remember { mutableStateOf(IntSize.Zero) }

  val bgColor = Color(settings.tone.background)
  val inkColor = Color(settings.tone.text)

  Box(
    modifier =
      modifier
        .background(bgColor)
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
          ),
        )
        .onSizeChanged { containerSize = it }
        .clipToBounds()
        .pointerInput(Unit) {
          detectTapGestures(
            onDoubleTap = {
              if (scale > 1.05f) {
                scale = 1f
                offset = Offset.Zero
              } else {
                scale = 2.2f
              }
            },
          )
        }
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            val newScale = (scale * zoom).coerceIn(1f, 5f)
            val newOffset =
              if (newScale > 1f) {
                clampOffset(offset + pan, newScale, containerSize.width, containerSize.height)
              } else {
                Offset.Zero
              }
            scale = newScale
            offset = newOffset
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    val currentBitmap = bitmap
    if (currentBitmap == null) {
      CircularProgressIndicator(color = inkColor)
    } else {
      Image(
        bitmap = currentBitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter,
        modifier =
          Modifier.fillMaxSize()
            .padding(16.dp)
            .graphicsLayer {
              scaleX = scale
              scaleY = scale
              translationX = offset.x
              translationY = offset.y
            },
      )
    }

    Box(
      modifier =
        Modifier.align(Alignment.TopEnd)
          .padding(16.dp)
          .background(bgColor)
          .border(1.5.dp, inkColor)
          .clickable(onClick = onDismiss)
          .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
      Text(
        text = stringResource(R.string.action_close),
        color = inkColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
      )
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

private suspend fun buildReflowMetas(analyses: List<PdfPageAnalysis>): List<ReflowPageMeta> {
  val context = currentCoroutineContext()
  return analyses.mapIndexed { idx, analysis ->
    context.ensureActive()
    val neighbors = listOfNotNull(analyses.getOrNull(idx - 1), analyses.getOrNull(idx + 1))
    val result = PdfReflowEngine.buildReflowPage(analysis, neighbors)
    ReflowPageMeta(
      pageIndex = analysis.pageIndex,
      hasSpecialObjects = PdfReflowPaginator.hasSpecialObjects(analysis, result),
      result = result,
    )
  }
}
