package com.example.emptyapp.ui.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.reader.EInkColorFilter
import com.example.emptyapp.reader.EInkPreferencesMapper
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.attachReaderInput
import com.example.emptyapp.reader.awaitView
import com.example.emptyapp.ui.eink.PaperGrainOverlay
import com.example.emptyapp.ui.eink.RefreshFlashOverlay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.NavigatorFragment
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

private const val HIGHLIGHT_GRAY = 0xFFD4D4D4.toInt()

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
  viewModel: ReaderViewModel,
  bookFormat: BookFormat,
  bookFile: File,
  initialLocator: Locator?,
  pdfController: PdfPagerController,
  title: String,
  toc: List<Link>,
  positions: List<Locator>,
  pendingNoteLocator: Locator?,
  onSaveNote: (Locator, String) -> Unit,
  onDismissNote: () -> Unit,
  onClose: () -> Unit,
  onNavigatorReady: (OverflowableNavigator) -> Unit,
  hasDndPermission: () -> Boolean = { true },
  onOpenDndPermissionSettings: () -> Unit = {},
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()
  val flashTrigger by viewModel.flashTrigger.collectAsStateWithLifecycle()
  val pageTurnTrigger by viewModel.pageTurnTrigger.collectAsStateWithLifecycle()
  val pagesSinceFlash by viewModel.pagesSinceFlash.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  val dictionaryState by viewModel.dictionaryState.collectAsStateWithLifecycle()
  val searchState by viewModel.searchState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  var chromeVisible by remember { mutableStateOf(false) }
  var settingsOpen by remember { mutableStateOf(false) }
  var contentsOpen by remember { mutableStateOf(false) }
  var searchOpen by remember { mutableStateOf(false) }
  var navigator by remember { mutableStateOf<NavigatorFragment?>(null) }
  var latestSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
  var activeGhost by remember { mutableStateOf<ImageBitmap?>(null) }
  var brightnessHudVisible by remember { mutableStateOf(false) }
  var edgeStripHeightPx by remember { mutableFloatStateOf(1f) }
  val latestBrightness by rememberUpdatedState(settings.brightness)
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(brightnessHudVisible, settings.brightness) {
    if (brightnessHudVisible) {
      delay(1200)
      brightnessHudVisible = false
    }
  }

  BackHandler(enabled = searchOpen || contentsOpen || settingsOpen || chromeVisible) {
    when {
      searchOpen -> searchOpen = false
      contentsOpen -> contentsOpen = false
      settingsOpen -> settingsOpen = false
      else -> chromeVisible = false
    }
  }

  navigator?.let { nav ->
    LaunchedEffect(nav) {
      nav.awaitView()
      val toggleChrome = {
        chromeVisible = !chromeVisible
        settingsOpen = false
      }
      when (nav) {
        is EpubNavigatorFragment -> nav.attachReaderInput(onCenterTap = toggleChrome)
        is ImageNavigatorFragment -> nav.attachReaderInput(onCenterTap = toggleChrome)
      }
      (nav as? OverflowableNavigator)?.let(onNavigatorReady)
    }
    LaunchedEffect(nav, settings) {
      val view = nav.awaitView()
      view.setLayerType(View.LAYER_TYPE_HARDWARE, EInkColorFilter.paintOf(settings))
      if (nav is EpubNavigatorFragment) {
        nav.submitPreferences(EInkPreferencesMapper.toEpub(settings))
        delay(120L)
        applyReaderStrokeStyle(nav, settings)
      }
    }
    LaunchedEffect(nav) {
      nav.currentLocator.collect { locator ->
        viewModel.onLocatorChanged(locator)
        if (nav is EpubNavigatorFragment) {
          applyReaderStrokeStyle(nav, viewModel.settings.value)
        }
      }
    }
    LaunchedEffect(nav, highlights) {
      if (nav is EpubNavigatorFragment) {
        nav.awaitView()
        val decorations =
          highlights.mapNotNull { entry ->
            runCatching { Locator.fromJSON(JSONObject(entry.locatorJson)) }
              .getOrNull()
              ?.let { Decoration(entry.id, it, Decoration.Style.Highlight(HIGHLIGHT_GRAY, isActive = true)) }
          }
        nav.applyDecorations(decorations, "highlights")
      }
    }
    LaunchedEffect(nav, pageTurnTrigger, flashTrigger, settings.ghosting) {
      activeGhost = if (settings.ghosting && pagesSinceFlash > 0) latestSnapshot else null
      val view = nav.awaitView()
      delay(220L)
      if (settings.ghosting) latestSnapshot = captureGhostSnapshot(view)
    }
  }

  val ghostAlpha =
    if (settings.ghosting && pagesSinceFlash > 0) (pagesSinceFlash * 0.014f).coerceAtMost(0.07f) else 0f

  Box(modifier = Modifier.fillMaxSize().background(Color(settings.tone.background))) {
    when (bookFormat) {
      BookFormat.EPUB ->
        AndroidFragment<EpubNavigatorFragment>(
          modifier = Modifier.fillMaxSize(),
          onUpdate = { fragment -> if (navigator == null) navigator = fragment },
        )
      BookFormat.PDF ->
        PdfPagerReader(
          file = bookFile,
          initialPage = remember(initialLocator, positions) { resolvePdfPageIndex(initialLocator, positions) },
          settings = settings,
          controller = pdfController,
          onPageChanged = { pageIndex, pageCount ->
            viewModel.onLocatorChanged(locatorForPdfPage(pageIndex, pageCount, positions))
          },
          onCenterTap = {
            chromeVisible = !chromeVisible
            settingsOpen = false
          },
          modifier = Modifier.fillMaxSize(),
        )
      BookFormat.CBZ ->
        AndroidFragment<ImageNavigatorFragment>(
          modifier = Modifier.fillMaxSize(),
          onUpdate = { fragment -> if (navigator == null) navigator = fragment },
        )
    }
    RefreshFlashOverlay(ghostBitmap = activeGhost, ghostAlpha = ghostAlpha)
    if (settings.paperGrain) PaperGrainOverlay()
    if (!chromeVisible && settings.readerFooter && !settings.focusMode) {
      ReaderFooterBar(
        title = title,
        progress = progress,
        inkColor = Color(settings.tone.text),
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
    if (chromeVisible) {
      ReaderTopBar(
        title = title,
        showFontControls = bookFormat == BookFormat.EPUB,
        onClose = onClose,
        onOpenSearch = { searchOpen = true; contentsOpen = false; settingsOpen = false },
        onOpenContents = { contentsOpen = true; searchOpen = false; settingsOpen = false },
        onBookmark = {
          viewModel.addBookmark()
          scope.launch { snackbarHostState.showSnackbar("Đã lưu dấu trang") }
        },
        onDecreaseFont = {
          viewModel.updateSettings { it.copy(fontScale = (it.fontScale - 0.1).coerceAtLeast(0.7)) }
        },
        onIncreaseFont = {
          viewModel.updateSettings { it.copy(fontScale = (it.fontScale + 0.1).coerceAtMost(4.0)) }
        },
        modifier = Modifier.align(Alignment.TopCenter),
      )
      ReaderBottomBar(
        progress = progress,
        seekEnabled = positions.isNotEmpty(),
        tone = settings.tone,
        brightness = settings.brightness,
        onToneChange = { tone -> viewModel.updateSettings { it.copy(tone = tone) } },
        onBrightnessChange = { value -> viewModel.updateSettings { it.copy(brightness = value) } },
        onSeekToProgress = { fraction ->
          if (positions.isNotEmpty()) {
            val index = (fraction * (positions.size - 1)).toInt().coerceIn(0, positions.lastIndex)
            if (bookFormat == BookFormat.PDF) {
              pdfController.jumpToPage(index)
            } else {
              navigator?.go(positions[index], animated = false)
            }
          }
        },
        focusMode = settings.focusMode,
        onToggleFocusMode = {
          val next = !settings.focusMode
          viewModel.updateSettings { it.copy(focusMode = next) }
          if (next) {
            settingsOpen = false
            chromeVisible = false
            scope.launch {
              if (hasDndPermission()) {
                snackbarHostState.showSnackbar("Đã bật Focus Mode", duration = SnackbarDuration.Short)
              } else {
                val res =
                  snackbarHostState.showSnackbar(
                    message = "Đã bật Focus Mode — Cấp quyền Không làm phiền để chặn thông báo",
                    actionLabel = "Cấp quyền",
                    duration = SnackbarDuration.Long,
                  )
                if (res == SnackbarResult.ActionPerformed) {
                  onOpenDndPermissionSettings()
                }
              }
            }
          } else {
            scope.launch {
              snackbarHostState.showSnackbar("Đã tắt Focus Mode", duration = SnackbarDuration.Short)
            }
          }
        },
        onToggleSettings = { settingsOpen = !settingsOpen },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
    if (settingsOpen) {
      ReaderSettingsPanel(
        settings = settings,
        isReflowable = bookFormat == BookFormat.EPUB,
        onChange = viewModel::updateSettings,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 156.dp)
            .fillMaxHeight(0.6f)
            .background(MaterialTheme.colorScheme.background),
      )
    }
    if (contentsOpen) {
      ReaderContentsSheet(
        toc = toc,
        bookmarks = bookmarks,
        highlights = highlights,
        onSelectToc = { link ->
          if (bookFormat == BookFormat.PDF) {
            resolvePdfTocPage(link, positions)?.let(pdfController::jumpToPage)
          } else {
            navigator?.go(link, animated = false)
          }
          contentsOpen = false
          chromeVisible = false
        },
        onSelectLocatorJson = { jsonText ->
          runCatching { Locator.fromJSON(JSONObject(jsonText)) }.getOrNull()?.let { locator ->
            if (bookFormat == BookFormat.PDF) {
              pdfController.jumpToPage(resolvePdfPageIndex(locator, positions))
            } else {
              navigator?.go(locator, animated = false)
            }
          }
          contentsOpen = false
          chromeVisible = false
        },
        onRemoveBookmark = viewModel::removeBookmark,
        onRemoveHighlight = viewModel::removeHighlight,
        onDismiss = { contentsOpen = false },
      )
    }
    if (searchOpen) {
      ReaderSearchSheet(
        state = searchState,
        onSearch = viewModel::searchInBook,
        onLoadMore = viewModel::loadMoreSearchResults,
        onSelectLocator = { locator ->
          if (bookFormat == BookFormat.PDF) {
            pdfController.jumpToPage(resolvePdfPageIndex(locator, positions))
          } else {
            navigator?.go(locator, animated = false)
          }
          searchOpen = false
          chromeVisible = false
        },
        onDismiss = { searchOpen = false },
      )
    }
    pendingNoteLocator?.let { locator ->
      HighlightNoteDialog(
        quote = locator.text.highlight.orEmpty(),
        onSave = { note -> onSaveNote(locator, note) },
        onDismiss = onDismissNote,
      )
    }
    dictionaryState?.let { dictState ->
      DictionaryDialog(
        state = dictState,
        onTranslateExternal = { query ->
          val encoded = java.net.URLEncoder.encode(query, "UTF-8")
          val intent =
            Intent(
              Intent.ACTION_VIEW,
              Uri.parse("https://translate.google.com/?sl=auto&tl=vi&text=$encoded"),
            )
          runCatching { context.startActivity(intent) }
        },
        onDismiss = viewModel::dismissDictionary,
      )
    }
    Box(
      modifier =
        Modifier.align(Alignment.CenterStart)
          .fillMaxHeight()
          .width(28.dp)
          .onSizeChanged { edgeStripHeightPx = it.height.coerceAtLeast(1).toFloat() }
          .pointerInput(Unit) {
            var dragBrightness = 0.5f
            detectVerticalDragGestures(
              onDragStart = {
                dragBrightness = if (latestBrightness < 0f) 0.5f else latestBrightness
                brightnessHudVisible = true
              },
              onVerticalDrag = { change, dragAmount ->
                change.consume()
                val delta = -dragAmount / (edgeStripHeightPx * 0.65f)
                dragBrightness = (dragBrightness + delta).coerceIn(0.02f, 1f)
                viewModel.updateSettings { it.copy(brightness = dragBrightness) }
                brightnessHudVisible = true
              },
            )
          }
    )
    if (brightnessHudVisible) {
      val pct = (settings.brightness.coerceIn(0.02f, 1f) * 100).roundToInt()
      Box(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 16.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 14.dp, vertical = 8.dp)
      ) {
        Text(
          text = "Độ sáng: $pct%",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp),
    )
  }
}

private suspend fun applyReaderStrokeStyle(nav: EpubNavigatorFragment, settings: EInkSettings) {
  val cssWeight = (400 + ((settings.fontWeight - 1.0) * 300)).toInt().coerceIn(400, 900)
  val strokePx = String.format(Locale.US, "%.2f", (settings.fontWeight - 1.0).coerceAtLeast(0.0) * 0.35)
  val inkHex = String.format(Locale.US, "#%06X", 0xFFFFFF and settings.tone.text)
  val css =
    "body, p, div, span, li, blockquote, a, em, i, h1, h2, h3, h4, h5, h6 { " +
      "font-weight: $cssWeight !important; " +
      "-webkit-text-stroke: ${strokePx}px $inkHex !important; " +
      "}"
  val js =
    "(function(){var s=document.getElementById('eink-stroke')||document.createElement('style');" +
      "s.id='eink-stroke';s.textContent='$css';" +
      "if(document.head)document.head.appendChild(s);})();"
  nav.evaluateJavascript(js)
}

private fun captureGhostSnapshot(view: View): ImageBitmap? {
  if (view.width <= 0 || view.height <= 0) return null
  val scaledWidth = (view.width / 4).coerceAtLeast(1)
  val scaledHeight = (view.height / 4).coerceAtLeast(1)
  return runCatching {
    val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(0.25f, 0.25f)
    view.draw(canvas)
    bitmap.asImageBitmap()
  }.getOrNull()
}

private fun resolvePdfPageIndex(locator: Locator?, positions: List<Locator>): Int {
  locator?.locations?.position?.let { pos -> if (pos >= 1) return pos - 1 }
  val total = locator?.locations?.totalProgression
  if (total != null && positions.size > 1) {
    return (total * (positions.size - 1)).toInt().coerceIn(0, positions.lastIndex)
  }
  return 0
}

private fun resolvePdfTocPage(link: Link, positions: List<Locator>): Int? {
  val href = link.href.toString()
  href.substringAfter("#page=", "").substringBefore('&').toIntOrNull()?.let { page ->
    if (page >= 1) return page - 1
  }
  val idx = positions.indexOfFirst { it.href == link.href }
  return if (idx >= 0) idx else null
}

private fun locatorForPdfPage(pageIndex: Int, pageCount: Int, positions: List<Locator>): Locator {
  positions.getOrNull(pageIndex)?.let { return it }
  val progression = if (pageCount > 1) pageIndex.toDouble() / (pageCount - 1) else 0.0
  return Locator(
    href = Url("page-${pageIndex + 1}")!!,
    mediaType = MediaType.PDF,
    locations = Locator.Locations(position = pageIndex + 1, totalProgression = progression),
  )
}
