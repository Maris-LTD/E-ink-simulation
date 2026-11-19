package com.example.emptyapp.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.emptyapp.reader.pdf.CameraTransform
import com.example.emptyapp.reader.pdf.PaginatedReflowElement
import com.example.emptyapp.reader.pdf.PdfDocumentCache
import com.example.emptyapp.reader.pdf.PdfGuidedNavigation
import com.example.emptyapp.reader.pdf.PdfPageAnalysis
import com.example.emptyapp.reader.pdf.PdfPageAnalyzer
import com.example.emptyapp.reader.pdf.PdfReadingMode
import com.example.emptyapp.reader.pdf.PdfRect
import com.example.emptyapp.reader.pdf.PdfReflowEngine
import com.example.emptyapp.reader.pdf.PdfReflowPaginator
import com.example.emptyapp.reader.pdf.PdfRegionNavigator
import com.example.emptyapp.reader.pdf.ReflowImageKind
import com.example.emptyapp.reader.pdf.ReflowPageMeta
import com.example.emptyapp.reader.pdf.ReflowReadingAnchor
import com.example.emptyapp.reader.pdf.ReflowScreenPage
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface PdfHolderLoadState {
  data object Loading : PdfHolderLoadState
  data class Ready(val holder: PdfRendererHolder) : PdfHolderLoadState
  data object Failed : PdfHolderLoadState
}

private data class ViewportTileSnapshot(
  val scale: Float,
  val offset: Offset,
  val bitmapWidth: Int,
  val bitmapHeight: Int,
)

private data class ViewportTileState(
  val bitmap: ImageBitmap,
  val bitmapRect: PdfRect,
)

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

@OptIn(ExperimentalTextApi::class, FlowPreview::class)
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
  onScreenTurn: () -> Unit = {},
) {
  val context = LocalContext.current
  val holderContext = context.applicationContext ?: context
  val density = LocalDensity.current
  val latestOnPageChanged by rememberUpdatedState(onPageChanged)
  val latestOnScreenTurn by rememberUpdatedState(onScreenTurn)
  val latestOnCenterTap by rememberUpdatedState(onCenterTap)
  val latestController by rememberUpdatedState(controller)
  val holderState by
    produceState<PdfHolderLoadState>(initialValue = PdfHolderLoadState.Loading, file, holderContext) {
      value = PdfHolderLoadState.Loading
      var opened: PdfRendererHolder? = null
      try {
        withContext(Dispatchers.IO) {
          opened = openPdfHolder(file, holderContext)
        }
        val ready = opened
        if (ready != null && ready.pageCount > 0) {
          value = PdfHolderLoadState.Ready(ready)
          awaitCancellation()
        } else {
          ready?.close()
          opened = null
          value = PdfHolderLoadState.Failed
        }
      } finally {
        opened?.close()
      }
    }

  val readyHolder =
    when (val state = holderState) {
      PdfHolderLoadState.Loading -> {
        Box(
          modifier =
            modifier
              .fillMaxSize()
              .background(Color(settings.tone.background))
              .pointerInput(Unit) { detectTapGestures(onTap = { latestOnCenterTap() }) },
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(color = Color(settings.tone.text))
        }
        return
      }
      PdfHolderLoadState.Failed -> {
        Box(
          modifier =
            modifier
              .fillMaxSize()
              .background(Color(settings.tone.background))
              .pointerInput(Unit) { detectTapGestures(onTap = { latestOnCenterTap() }) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.pdf_load_error),
            color = Color(settings.tone.text),
            style = MaterialTheme.typography.bodyLarge,
          )
        }
        return
      }
      is PdfHolderLoadState.Ready -> state.holder
    }

  val holder = readyHolder
  DisposableEffect(holder) {
    onDispose { holder.close() }
  }

  val pageCount = holder.pageCount
  var currentPdfPage by remember(file) { mutableIntStateOf(initialPage.coerceIn(0, pageCount - 1)) }
  var currentReflowAnchor by remember(file) { mutableStateOf<ReflowReadingAnchor?>(null) }
  val isReflowMode = readingMode == PdfReadingMode.HYBRID_REFLOW
  val cache = remember(context) { PdfDocumentCache(File(context.filesDir, "pdf_cache")) }
  var allAnalyses by remember(file) { mutableStateOf<List<PdfPageAnalysis>?>(null) }
  var analyzeDone by remember(file) { mutableIntStateOf(0) }
  var analyzeTotal by remember(file) { mutableIntStateOf(pageCount) }

  LaunchedEffect(file, holder, isReflowMode) {
    if (!isReflowMode || allAnalyses != null) return@LaunchedEffect
    val cached = withContext(Dispatchers.IO) { cache.load(file) }
    if (cached != null && cached.size == pageCount) {
      holder.seedAnalyses(cached)
      allAnalyses = cached
    } else {
      val computed =
        withContext(Dispatchers.IO) {
          PdfPageAnalyzer.ensureInitialized(context)
          PdfPageAnalyzer.analyzeAllPages(
            file = file,
            shouldContinue = { isActive },
          ) { done, total ->
            analyzeDone = done
            analyzeTotal = total
          }.also { pages ->
            if (pages.isNotEmpty()) cache.save(file, pages)
          }
        }
      if (computed.isNotEmpty()) {
        holder.seedAnalyses(computed)
        allAnalyses = computed
      } else if (isActive) {
        allAnalyses = emptyList()
      }
    }
  }

  val loadedAnalyses = allAnalyses
  if (isReflowMode && loadedAnalyses == null) {
    Box(
      modifier =
        modifier
          .fillMaxSize()
          .background(Color(settings.tone.background))
          .pointerInput(onCenterTap) { detectTapGestures(onTap = { onCenterTap() }) },
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

  val reflowMetas by
    produceState<List<ReflowPageMeta>?>(null, loadedAnalyses, isReflowMode) {
      value =
        if (isReflowMode && loadedAnalyses != null) {
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
      Box(
        modifier =
          Modifier.fillMaxSize()
            .pointerInput(onCenterTap) { detectTapGestures(onTap = { onCenterTap() }) },
        contentAlignment = Alignment.Center,
      ) {
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

    if (readingMode == PdfReadingMode.HYBRID_REFLOW && reflowScreens.isNotEmpty()) {
      var zoomedImageElement by remember { mutableStateOf<PaginatedReflowElement.InlineImage?>(null) }
      var pendingJumpTargetScreen by remember { mutableStateOf<Int?>(null) }
      val initialAnchor = currentReflowAnchor ?: ReflowReadingAnchor(sourcePageIndex = currentPdfPage)
      val startScreen = remember(reflowScreens) {
        PdfReflowPaginator.findScreenIndexForAnchor(reflowScreens, initialAnchor)
          .coerceIn(0, reflowScreens.lastIndex)
      }
      val reflowPagerState =
        rememberPagerState(
          initialPage = startScreen,
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
          val targetPdf = pdfIdx.coerceIn(0, pageCount - 1)
          val jumpAnchor = ReflowReadingAnchor(sourcePageIndex = targetPdf)
          currentReflowAnchor = jumpAnchor
          val screenIdx =
            PdfReflowPaginator.findScreenIndexForAnchor(reflowScreens, jumpAnchor)
              .coerceIn(0, reflowScreens.lastIndex)
          if (screenIdx != reflowPagerState.currentPage || screenIdx != reflowPagerState.settledPage) {
            pendingJumpTargetScreen = screenIdx
            scope.launch { reflowPagerState.scrollToPage(screenIdx) }
          }
        }
        onDispose {
          controller.onPrev = null
          controller.onNext = null
          controller.onJumpTo = null
        }
      }

      LaunchedEffect(reflowPagerState, reflowScreens) {
        val targetAnchor = currentReflowAnchor ?: ReflowReadingAnchor(sourcePageIndex = currentPdfPage)
        if (currentReflowAnchor == null) {
          currentReflowAnchor = targetAnchor
        }
        val targetScreen =
          PdfReflowPaginator.findScreenIndexForAnchor(reflowScreens, targetAnchor)
            .coerceIn(0, reflowScreens.lastIndex)
        if (reflowPagerState.currentPage != targetScreen || reflowPagerState.settledPage != targetScreen) {
          pendingJumpTargetScreen = null
          reflowPagerState.scrollToPage(targetScreen)
        }
        var lastSettledScreen: Int? = null
        snapshotFlow { reflowPagerState.settledPage }
          .distinctUntilChanged()
          .collect { screenIdx ->
            val screen = reflowScreens.getOrNull(screenIdx) ?: return@collect
            val isExplicitJump = pendingJumpTargetScreen == screenIdx
            if (lastSettledScreen != null && lastSettledScreen != screenIdx) {
              pendingJumpTargetScreen = null
            }
            val settle =
              PdfReflowPaginator.evaluateScreenSettle(
                screen = screen,
                previousScreenIndex = lastSettledScreen,
                previousPdfPage = currentPdfPage,
                isExplicitJump = isExplicitJump,
              )
            lastSettledScreen = screenIdx
            settle.updatedAnchor?.let { currentReflowAnchor = it }
            currentPdfPage = settle.snapPdfPage
            latestOnPageChanged(settle.snapPdfPage, pageCount)
            if (settle.isSubPageTurn) {
              latestOnScreenTurn()
            }
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
            currentReflowAnchor = null
            latestOnPageChanged(page, pageCount)
          }
      }

      LaunchedEffect(readingMode) {
        if (readingMode != PdfReadingMode.GUIDED_COLUMN) {
          pendingBackwardLandingPage = null
        }
        if (readingMode != PdfReadingMode.HYBRID_REFLOW) {
          currentReflowAnchor = null
        }
      }

      LaunchedEffect(holder, pagerState.currentPage, readingMode, loadedAnalyses) {
        if (readingMode == PdfReadingMode.GUIDED_COLUMN && loadedAnalyses == null) {
          holder.getOrAnalyzePage(pagerState.currentPage)
          holder.prefetchAdjacentAnalyses(pagerState.currentPage)
        }
      }

      LaunchedEffect(holder, pagerState.settledPage, readingMode, loadedAnalyses) {
        if (readingMode == PdfReadingMode.SMART_ORIGINAL && loadedAnalyses == null) {
          holder.getOrAnalyzePage(pagerState.settledPage)
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
        val isCurrentPage by remember(pagerState, pageIndex) {
          derivedStateOf { pagerState.currentPage == pageIndex }
        }
        val isPageZoomed by remember(pageIndex, readingMode) {
          derivedStateOf { readingMode != PdfReadingMode.GUIDED_COLUMN && scale > 1.01f }
        }
        val isDeepZoomed by remember(pagerState, pageIndex, readingMode) {
          derivedStateOf {
            pagerState.currentPage == pageIndex &&
              readingMode != PdfReadingMode.GUIDED_COLUMN &&
              scale > 2.0f
          }
        }
        val renderMultiplier by remember(pagerState, pageIndex, readingMode) {
          derivedStateOf {
            if (pagerState.currentPage == pageIndex &&
              (readingMode == PdfReadingMode.GUIDED_COLUMN || scale > 1.35f)
            ) {
              2
            } else {
              1
            }
          }
        }
        val autoCrop = settings.pdfAutoCrop && readingMode != PdfReadingMode.GUIDED_COLUMN
        val analysisVersion by holder.analysisVersion.collectAsState()
        val pageAnalysis =
          remember(holder, pageIndex, loadedAnalyses, analysisVersion) {
            loadedAnalyses?.getOrNull(pageIndex) ?: holder.cachedAnalysis(pageIndex)
          }
        var guidedRegionIndex by remember(pageIndex, readingMode) { mutableIntStateOf(0) }
        var bitmap by remember(holder, pageIndex, autoCrop) {
          mutableStateOf<ImageBitmap?>(null)
        }
        var viewportTile by remember(holder, pageIndex, autoCrop) {
          mutableStateOf<ViewportTileState?>(null)
        }
        var renderFailed by remember(holder, pageIndex, autoCrop) { mutableStateOf(false) }
        var retryNonce by remember(pageIndex) { mutableIntStateOf(0) }

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

        val latestAnalysis by rememberUpdatedState(pageAnalysis)
        val latestRegions by rememberUpdatedState(guidedRegions)
        val latestPendingBackward by rememberUpdatedState(pendingBackwardLandingPage)
        val latestBitmap by rememberUpdatedState(bitmap)
        val latestAutoCrop by rememberUpdatedState(autoCrop)

        DisposableEffect(isCurrentPage, pageIndex, readingMode) {
          if (isCurrentPage && readingMode == PdfReadingMode.GUIDED_COLUMN) {
            activePageRegionPrev = {
              if (pagerState.currentPage != pageIndex) {
                true
              } else {
                val step =
                  PdfGuidedNavigation.stepPrev(
                    readingMode = readingMode,
                    hasAnalysis = latestAnalysis != null,
                    currentRegionIndex = guidedRegionIndex,
                    awaitingBackwardLanding = latestPendingBackward == pageIndex,
                  )
                guidedRegionIndex = step.nextRegionIndex
                if (step.changedRegionOnSamePage) {
                  latestOnScreenTurn()
                }
                step.consumed
              }
            }
            activePageRegionNext = {
              if (pagerState.currentPage != pageIndex) {
                true
              } else {
                val step =
                  PdfGuidedNavigation.stepNext(
                    readingMode = readingMode,
                    hasAnalysis = latestAnalysis != null,
                    currentRegionIndex = guidedRegionIndex,
                    regionCount = latestRegions.size,
                    awaitingBackwardLanding = latestPendingBackward == pageIndex,
                  )
                guidedRegionIndex = step.nextRegionIndex
                if (step.changedRegionOnSamePage) {
                  latestOnScreenTurn()
                }
                step.consumed
              }
            }
          } else if (isCurrentPage) {
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

        LaunchedEffect(isPageZoomed, isCurrentPage) {
          if (isCurrentPage) {
            isZoomed = isPageZoomed
            resetActivePageZoom =
              if (isPageZoomed) {
                {
                  scale = 1f
                  offset = Offset.Zero
                }
              } else {
                null
              }
          }
        }

        LaunchedEffect(readingMode, pageAnalysis, guidedRegions, pendingBackwardLandingPage, pageIndex) {
          guidedRegionIndex =
            PdfGuidedNavigation.resolveRegionIndex(
              readingMode = readingMode,
              pageAnalysis = pageAnalysis,
              guidedRegions = guidedRegions,
              currentRegionIndex = guidedRegionIndex,
              pendingBackwardLandingPage = pendingBackwardLandingPage,
              pageIndex = pageIndex,
              onConsumeBackwardLanding = { pendingBackwardLandingPage = null },
            )
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

        LaunchedEffect(holder, pageIndex, targetWidthPx, targetHeightPx, renderMultiplier, autoCrop, retryNonce) {
          renderFailed = false
          val rendered =
            holder.renderPage(
              index = pageIndex,
              maxWidth = targetWidthPx * renderMultiplier,
              maxHeight = targetHeightPx * renderMultiplier,
              autoCrop = autoCrop,
            )
          if (rendered != null) {
            bitmap = rendered
          } else if (bitmap == null) {
            renderFailed = true
          }
        }

        LaunchedEffect(holder, pageIndex, isDeepZoomed, targetWidthPx, targetHeightPx, autoCrop, retryNonce) {
          if (!isDeepZoomed) {
            viewportTile = null
            return@LaunchedEffect
          }
          snapshotFlow {
            val bmp = bitmap
            if (bmp == null) null else ViewportTileSnapshot(scale, offset, bmp.width, bmp.height)
          }
            .distinctUntilChanged()
            .debounce(180L)
            .collectLatest { snap ->
              if (snap == null || snap.scale <= 2.0f) {
                viewportTile = null
                return@collectLatest
              }
              val bitmapRect =
                PdfRegionNavigator.computeVisibleViewportBitmapRect(
                  scale = snap.scale,
                  offsetX = snap.offset.x,
                  offsetY = snap.offset.y,
                  containerWidth = targetWidthPx.toFloat(),
                  containerHeight = targetHeightPx.toFloat(),
                  bitmapWidth = snap.bitmapWidth.toFloat(),
                  bitmapHeight = snap.bitmapHeight.toFloat(),
                )
              if (bitmapRect == null) {
                viewportTile = null
                return@collectLatest
              }
              val cropRect =
                if (autoCrop) {
                  holder.cachedCropRect(pageIndex)?.let { PdfRect(it.left, it.top, it.right, it.bottom) }
                } else {
                  null
                }
              val pageRect =
                PdfRegionNavigator.computeVisibleViewportPageRect(
                  scale = snap.scale,
                  offsetX = snap.offset.x,
                  offsetY = snap.offset.y,
                  containerWidth = targetWidthPx.toFloat(),
                  containerHeight = targetHeightPx.toFloat(),
                  bitmapWidth = snap.bitmapWidth.toFloat(),
                  bitmapHeight = snap.bitmapHeight.toFloat(),
                  cropRect = cropRect,
                ) ?: return@collectLatest
              val renderedTile =
                holder.renderViewportTile(
                  index = pageIndex,
                  region = pageRect,
                  maxWidth = targetWidthPx,
                  maxHeight = targetHeightPx,
                )
              if (renderedTile != null) {
                viewportTile = ViewportTileState(bitmap = renderedTile, bitmapRect = bitmapRect)
              }
            }
        }

        Box(
          modifier =
            Modifier.fillMaxSize()
              .clipToBounds()
              .pointerInput(holder, pageIndex, targetWidthPx, targetHeightPx, readingMode) {
                detectTapGestures(
                  onDoubleTap = { tapOffset ->
                    if (readingMode == PdfReadingMode.GUIDED_COLUMN) return@detectTapGestures
                    if (scale > 1.01f) {
                      scale = 1f
                      offset = Offset.Zero
                    } else {
                      val currentBmp = latestBitmap
                      val existingAnalysis = latestAnalysis
                      if (existingAnalysis != null || currentBmp == null) {
                        val cam =
                          computeDoubleTapCamera(
                            tapOffset = tapOffset,
                            bitmap = currentBmp,
                            pageAnalysis = existingAnalysis,
                            cropRect = if (latestAutoCrop) holder.cachedCropRect(pageIndex) else null,
                            targetWidthPx = targetWidthPx,
                            targetHeightPx = targetHeightPx,
                          )
                        scale = cam.scale
                        offset = Offset(cam.offsetX, cam.offsetY)
                      } else {
                        scope.launch {
                          val analyzed = holder.getOrAnalyzePage(pageIndex)
                          if (pagerState.currentPage == pageIndex && scale <= 1.01f) {
                            val bmp = latestBitmap ?: currentBmp
                            val crop = if (latestAutoCrop) holder.cachedCropRect(pageIndex) else null
                            val cam =
                              computeDoubleTapCamera(
                                tapOffset = tapOffset,
                                bitmap = bmp,
                                pageAnalysis = analyzed,
                                cropRect = crop,
                                targetWidthPx = targetWidthPx,
                                targetHeightPx = targetHeightPx,
                              )
                            scale = cam.scale
                            offset = Offset(cam.offsetX, cam.offsetY)
                          }
                        }
                      }
                    }
                  },
                  onTap = { tapOffset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    when {
                      tapOffset.x < width * 0.25f -> latestController.goBackward()
                      tapOffset.x > width * 0.75f -> latestController.goForward()
                      else -> latestOnCenterTap()
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
                          latestController.goForward()
                        } else {
                          latestController.goBackward()
                        }
                      }
                    } else if (pointerCount > 1 || (readingMode != PdfReadingMode.GUIDED_COLUMN && scale > 1.01f)) {
                      val zoom = event.calculateZoom()
                      val centroid = event.calculateCentroid(useCurrent = false)
                      val rawPan = event.calculatePan()
                      val (lockedX, lockedY) = PdfRegionNavigator.applyHorizontalLock(rawPan.x, rawPan.y)
                      val pan = Offset(lockedX, lockedY)
                      val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                      val zoomFactor = if (scale > 0f) newScale / scale else 1f
                      val center = Offset(targetWidthPx / 2f, targetHeightPx / 2f)
                      val centroidDelta =
                        if (centroid.x.isFinite() && centroid.y.isFinite()) (centroid - center) * (1f - zoomFactor)
                        else Offset.Zero
                      val rawOffset = offset * zoomFactor + centroidDelta + pan
                      val currentBmp = latestBitmap
                      val newOffset =
                        if (newScale <= 1.01f) {
                          Offset.Zero
                        } else {
                          clampOffset(
                            offset = rawOffset,
                            scale = newScale,
                            widthPx = targetWidthPx,
                            heightPx = targetHeightPx,
                            bitmapWidthPx = currentBmp?.width ?: 0,
                            bitmapHeightPx = currentBmp?.height ?: 0,
                          )
                        }
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
              contentDescription = stringResource(R.string.cd_pdf_page, pageIndex + 1),
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
            val activeTile = if (isDeepZoomed) viewportTile else null
            if (activeTile != null) {
              val bw = current.width.toFloat().coerceAtLeast(1f)
              val bh = current.height.toFloat().coerceAtLeast(1f)
              val fitScale = minOf(targetWidthPx.toFloat() / bw, targetHeightPx.toFloat() / bh)
              val dispW = bw * fitScale
              val dispH = bh * fitScale
              val r = activeTile.bitmapRect
              val tileW = (r.width * dispW).coerceAtLeast(1f)
              val tileH = (r.height * dispH).coerceAtLeast(1f)
              val measuredTileW = tileW.roundToInt().coerceIn(1, targetWidthPx)
              val measuredTileH = tileH.roundToInt().coerceIn(1, targetHeightPx)
              val tileWidthDp = with(density) { measuredTileW.toDp() }
              val tileHeightDp = with(density) { measuredTileH.toDp() }
              val unzoomedDx = ((r.left + r.right) * 0.5f - 0.5f) * dispW
              val unzoomedDy = ((r.top + r.bottom) * 0.5f - 0.5f) * dispH
              val ratioX = tileW / measuredTileW.toFloat()
              val ratioY = tileH / measuredTileH.toFloat()
              Image(
                bitmap = activeTile.bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                colorFilter = colorFilter,
                modifier =
                  Modifier.size(width = tileWidthDp, height = tileHeightDp)
                    .graphicsLayer {
                      scaleX = scale * ratioX
                      scaleY = scale * ratioY
                      translationX = unzoomedDx * scale + offset.x
                      translationY = unzoomedDy * scale + offset.y
                    },
              )
            }
          } else if (renderFailed) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(
                text = stringResource(R.string.pdf_page_render_error),
                color = Color(settings.tone.text),
                style = MaterialTheme.typography.bodyLarge,
              )
              OutlinedButton(onClick = { retryNonce += 1 }) {
                Text(text = stringResource(R.string.action_retry))
              }
            }
          } else {
            CircularProgressIndicator(color = Color(settings.tone.text))
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
  val latestBitmap by rememberUpdatedState(bitmap)
  val paddingPx = with(LocalDensity.current) { 32.dp.roundToPx() }

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
            val currentBmp = latestBitmap
            val availW = (containerSize.width - paddingPx).coerceAtLeast(1)
            val availH = (containerSize.height - paddingPx).coerceAtLeast(1)
            val newOffset =
              if (newScale > 1f) {
                clampOffset(
                  offset = offset + pan,
                  scale = newScale,
                  widthPx = availW,
                  heightPx = availH,
                  bitmapWidthPx = currentBmp?.width ?: 0,
                  bitmapHeightPx = currentBmp?.height ?: 0,
                )
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

private fun computeDoubleTapCamera(
  tapOffset: Offset,
  bitmap: ImageBitmap?,
  pageAnalysis: PdfPageAnalysis?,
  cropRect: NormalizedCropRect?,
  targetWidthPx: Int,
  targetHeightPx: Int,
): CameraTransform {
  val tapRegion =
    if (bitmap != null && pageAnalysis != null) {
      val fitScale = minOf(targetWidthPx.toFloat() / bitmap.width, targetHeightPx.toFloat() / bitmap.height)
      val dispW = bitmap.width * fitScale
      val dispH = bitmap.height * fitScale
      val imgLeft = (targetWidthPx - dispW) / 2f
      val imgTop = (targetHeightPx - dispH) / 2f
      val rawNormX = ((tapOffset.x - imgLeft) / dispW).coerceIn(0f, 1f)
      val rawNormY = ((tapOffset.y - imgTop) / dispH).coerceIn(0f, 1f)
      val normX = if (cropRect != null) (cropRect.left + rawNormX * cropRect.width).coerceIn(0f, 1f) else rawNormX
      val normY = if (cropRect != null) (cropRect.top + rawNormY * cropRect.height).coerceIn(0f, 1f) else rawNormY
      val pageRect = PdfRegionNavigator.findTapTargetRect(pageAnalysis, normX, normY)
      if (pageRect != null && cropRect != null && cropRect.width > 0f && cropRect.height > 0f) {
        PdfRegionNavigator.mapPageRectToCropSpace(
          rect = pageRect,
          crop = PdfRect(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom),
        )
      } else {
        pageRect
      }
    } else {
      null
    }
  if (tapRegion != null && bitmap != null) {
    return PdfRegionNavigator.computeCameraTransform(
      region = tapRegion,
      bitmapWidth = bitmap.width.toFloat(),
      bitmapHeight = bitmap.height.toFloat(),
      containerWidth = targetWidthPx.toFloat(),
      containerHeight = targetHeightPx.toFloat(),
    )
  }
  val targetScale = 2.2f
  val centerX = targetWidthPx / 2f
  val centerY = targetHeightPx / 2f
  val rawOffset =
    Offset(
      x = (centerX - tapOffset.x) * (targetScale - 1f),
      y = (centerY - tapOffset.y) * (targetScale - 1f),
    )
  val clamped =
    clampOffset(
      offset = rawOffset,
      scale = targetScale,
      widthPx = targetWidthPx,
      heightPx = targetHeightPx,
      bitmapWidthPx = bitmap?.width ?: 0,
      bitmapHeightPx = bitmap?.height ?: 0,
    )
  return CameraTransform(targetScale, clamped.x, clamped.y)
}

internal fun clampOffset(
  offset: Offset,
  scale: Float,
  widthPx: Int,
  heightPx: Int,
  bitmapWidthPx: Int = 0,
  bitmapHeightPx: Int = 0,
): Offset {
  val (clampedX, clampedY) =
    PdfRegionNavigator.clampOffsetForFitContent(
      offsetX = offset.x,
      offsetY = offset.y,
      scale = scale,
      containerWidth = widthPx.toFloat(),
      containerHeight = heightPx.toFloat(),
      bitmapWidth = bitmapWidthPx.toFloat(),
      bitmapHeight = bitmapHeightPx.toFloat(),
    )
  return Offset(x = clampedX, y = clampedY)
}

private suspend fun buildReflowMetas(analyses: List<PdfPageAnalysis>): List<ReflowPageMeta> {
  val context = currentCoroutineContext()
  return analyses.mapIndexed { idx, analysis ->
    context.ensureActive()
    val neighbors = listOfNotNull(analyses.getOrNull(idx - 1), analyses.getOrNull(idx + 1))
    val result = PdfReflowEngine.buildReflowPage(analysis, neighbors)
    val pageAspect =
      if (analysis.pageWidthPt > 0f && analysis.pageHeightPt > 0f) {
        analysis.pageWidthPt / analysis.pageHeightPt
      } else {
        1f
      }
    ReflowPageMeta(
      pageIndex = analysis.pageIndex,
      hasSpecialObjects = PdfReflowPaginator.hasSpecialObjects(analysis, result),
      result = result,
      pageAspectRatio = pageAspect,
    )
  }
}
