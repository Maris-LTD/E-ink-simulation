package com.example.emptyapp.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.reader.EInkColorFilter
import com.example.emptyapp.reader.EInkPreferencesMapper
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.attachReaderInput
import com.example.emptyapp.reader.awaitView
import com.example.emptyapp.ui.eink.PaperGrainOverlay
import com.example.emptyapp.ui.eink.RefreshFlashOverlay
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

private const val HIGHLIGHT_GRAY = 0xFFD4D4D4.toInt()

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
  viewModel: ReaderViewModel,
  title: String,
  toc: List<Link>,
  positions: List<Locator>,
  pendingNoteLocator: Locator?,
  onSaveNote: (Locator, String) -> Unit,
  onDismissNote: () -> Unit,
  onClose: () -> Unit,
  onNavigatorReady: (EpubNavigatorFragment) -> Unit,
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()
  val flashTrigger by viewModel.flashTrigger.collectAsStateWithLifecycle()
  val pageTurnTrigger by viewModel.pageTurnTrigger.collectAsStateWithLifecycle()
  val pagesSinceFlash by viewModel.pagesSinceFlash.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()

  var chromeVisible by remember { mutableStateOf(false) }
  var settingsOpen by remember { mutableStateOf(false) }
  var contentsOpen by remember { mutableStateOf(false) }
  var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
  var latestSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
  var activeGhost by remember { mutableStateOf<ImageBitmap?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  BackHandler(enabled = contentsOpen || settingsOpen || chromeVisible) {
    when {
      contentsOpen -> contentsOpen = false
      settingsOpen -> settingsOpen = false
      else -> chromeVisible = false
    }
  }

  navigator?.let { nav ->
    LaunchedEffect(nav) {
      nav.awaitView()
      nav.attachReaderInput(onCenterTap = {
        chromeVisible = !chromeVisible
        settingsOpen = false
      })
      onNavigatorReady(nav)
    }
    LaunchedEffect(nav, settings) {
      val view = nav.awaitView()
      nav.submitPreferences(EInkPreferencesMapper.toEpub(settings))
      view.setLayerType(View.LAYER_TYPE_HARDWARE, EInkColorFilter.paintOf(settings))
      delay(120L)
      applyReaderStrokeStyle(nav, settings)
    }
    LaunchedEffect(nav) {
      nav.currentLocator.collect { locator ->
        viewModel.onLocatorChanged(locator)
        applyReaderStrokeStyle(nav, viewModel.settings.value)
      }
    }
    LaunchedEffect(nav, highlights) {
      nav.awaitView()
      val decorations =
        highlights.mapNotNull { entry ->
          runCatching { Locator.fromJSON(JSONObject(entry.locatorJson)) }
            .getOrNull()
            ?.let { Decoration(entry.id, it, Decoration.Style.Highlight(HIGHLIGHT_GRAY, isActive = true)) }
        }
      nav.applyDecorations(decorations, "highlights")
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
    AndroidFragment<EpubNavigatorFragment>(
      modifier = Modifier.fillMaxSize(),
      onUpdate = { fragment -> if (navigator == null) navigator = fragment },
    )
    RefreshFlashOverlay(ghostBitmap = activeGhost, ghostAlpha = ghostAlpha)
    if (settings.paperGrain) PaperGrainOverlay()
    if (!chromeVisible && settings.readerFooter) {
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
        onClose = onClose,
        onOpenContents = { contentsOpen = true; settingsOpen = false },
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
        onSeekToProgress = { fraction ->
          if (positions.isNotEmpty()) {
            val index = (fraction * (positions.size - 1)).toInt().coerceIn(0, positions.lastIndex)
            navigator?.go(positions[index], animated = false)
          }
        },
        onToggleSettings = { settingsOpen = !settingsOpen },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
    if (settingsOpen) {
      ReaderSettingsPanel(
        settings = settings,
        onChange = viewModel::updateSettings,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 112.dp)
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
          navigator?.go(link, animated = false)
          contentsOpen = false
          chromeVisible = false
        },
        onSelectLocatorJson = { jsonText ->
          runCatching { Locator.fromJSON(JSONObject(jsonText)) }.getOrNull()?.let {
            navigator?.go(it, animated = false)
          }
          contentsOpen = false
          chromeVisible = false
        },
        onRemoveBookmark = viewModel::removeBookmark,
        onRemoveHighlight = viewModel::removeHighlight,
        onDismiss = { contentsOpen = false },
      )
    }
    pendingNoteLocator?.let { locator ->
      HighlightNoteDialog(
        quote = locator.text.highlight.orEmpty(),
        onSave = { note -> onSaveNote(locator, note) },
        onDismiss = onDismissNote,
      )
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
