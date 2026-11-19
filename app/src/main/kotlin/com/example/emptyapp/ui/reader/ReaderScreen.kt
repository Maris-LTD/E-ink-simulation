package com.example.emptyapp.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.reader.EInkColorFilter
import com.example.emptyapp.reader.EInkPreferencesMapper
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.pdf.PdfReadingMode
import com.example.emptyapp.reader.attachReaderInput
import com.example.emptyapp.reader.awaitView
import com.example.emptyapp.ui.eink.PaperGrainOverlay
import com.example.emptyapp.ui.eink.RefreshFlashOverlay
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
  isSearchable: Boolean = true,
  pendingNoteLocator: Locator?,
  pendingNoteDraft: String = "",
  onNoteDraftChange: (String) -> Unit = {},
  onSaveNote: (Locator, String) -> Unit,
  onDismissNote: () -> Unit,
  onClose: () -> Unit,
  onNavigatorReady: (OverflowableNavigator) -> Unit,
  hasDndPermission: () -> Boolean = { true },
  onOpenDndPermissionSettings: () -> Unit = {},
  onOpenNotificationSettings: () -> Unit = {},
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val pdfConfig by viewModel.pdfConfig.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  val isBookmarked by viewModel.isCurrentPageBookmarked.collectAsStateWithLifecycle()
  val dictionaryState by viewModel.dictionaryState.collectAsStateWithLifecycle()
  val searchState by viewModel.searchState.collectAsStateWithLifecycle()
  val writeError by viewModel.writeError.collectAsStateWithLifecycle()
  val notifBlockedCount by viewModel.notificationPermissionBlockedCount.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val density = LocalDensity.current

  var chromeVisible by remember { mutableStateOf(false) }
  var settingsOpen by remember { mutableStateOf(false) }
  var contentsOpen by remember { mutableStateOf(false) }
  var searchOpen by remember { mutableStateOf(false) }
  var bottomBarHeightPx by remember { mutableStateOf(0) }
  var navigator by remember { mutableStateOf<NavigatorFragment?>(null) }
  var brightnessHudVisible by remember { mutableStateOf(false) }
  var edgeStripHeightPx by remember { mutableFloatStateOf(1f) }
  val latestBrightness by rememberUpdatedState(settings.brightness)
  val latestOnLeftEdgeTap by rememberUpdatedState {
    if (bookFormat == BookFormat.PDF) {
      pdfController.goBackward()
    } else {
      (navigator as? OverflowableNavigator)?.goBackward(animated = settings.animations)
    }
  }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(chromeVisible, settingsOpen, contentsOpen, searchOpen) {
    viewModel.setOverlayOpen(chromeVisible || settingsOpen || contentsOpen || searchOpen)
  }
  DisposableEffect(Unit) {
    onDispose { viewModel.setOverlayOpen(false) }
  }

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
    LaunchedEffect(nav, settings.grayscale, settings.contrast, settings.tone) {
      val view = nav.awaitView()
      view.setLayerType(
        View.LAYER_TYPE_HARDWARE,
        EInkColorFilter.paintOf(settings, contentAlreadyDark = nav is EpubNavigatorFragment),
      )
    }
    val epubPreferences = remember(settings) { EInkPreferencesMapper.toEpub(settings) }
    LaunchedEffect(nav, epubPreferences) {
      if (nav is EpubNavigatorFragment) {
        nav.awaitView()
        nav.submitPreferences(epubPreferences)
        delay(120L)
        applyReaderStrokeStyle(nav, viewModel.settings.value)
      }
    }
    LaunchedEffect(nav) {
      var lastStyledHref: String? = null
      nav.currentLocator.collect { locator ->
        viewModel.onLocatorChanged(locator)
        if (nav is EpubNavigatorFragment) {
          val hrefStr = locator.href.toString()
          if (hrefStr != lastStyledHref) {
            lastStyledHref = hrefStr
            applyReaderStrokeStyle(nav, viewModel.settings.value)
          }
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
  }

  val focusEnabledMsg = stringResource(com.example.emptyapp.R.string.reader_focus_enabled)
  val focusNeedsDndMsg = stringResource(com.example.emptyapp.R.string.reader_focus_needs_dnd)
  val grantPermissionLabel = stringResource(com.example.emptyapp.R.string.action_grant_permission)
  val focusDisabledMsg = stringResource(com.example.emptyapp.R.string.reader_focus_disabled)
  val reflowLockedScanMsg = stringResource(com.example.emptyapp.R.string.pdf_reflow_locked_scan)
  val bookmarkSavedMsg = stringResource(com.example.emptyapp.R.string.reader_bookmark_saved)
  val bookmarkRemovedMsg = stringResource(com.example.emptyapp.R.string.reader_bookmark_removed)
  val libBookmarkDeletedMsg = stringResource(com.example.emptyapp.R.string.lib_bookmark_deleted)
  val libHighlightDeletedMsg = stringResource(com.example.emptyapp.R.string.lib_highlight_deleted)
  val undoActionLabel = stringResource(com.example.emptyapp.R.string.action_undo)
  val openBrowserErrorMsg = stringResource(com.example.emptyapp.R.string.error_open_browser)
  val saveErrorMsg = stringResource(com.example.emptyapp.R.string.error_save_data)
  val notifPermissionBlockedMsg = stringResource(com.example.emptyapp.R.string.notif_permission_blocked_msg)
  val notifOpenSettingsLabel = stringResource(com.example.emptyapp.R.string.notif_open_settings_action)
  LaunchedEffect(writeError) {
    if (writeError) {
      snackbarHostState.showSnackbar(saveErrorMsg, duration = SnackbarDuration.Short)
    }
  }
  LaunchedEffect(notifBlockedCount) {
    if (notifBlockedCount > 0) {
      viewModel.consumeNotificationPermissionBlocked()
      val res =
        snackbarHostState.showSnackbar(
          message = notifPermissionBlockedMsg,
          actionLabel = notifOpenSettingsLabel,
          duration = SnackbarDuration.Long,
        )
      if (res == SnackbarResult.ActionPerformed) {
        onOpenNotificationSettings()
      }
    }
  }

  fun handleToggleFocusMode(next: Boolean) {
    viewModel.updateSettings { it.copy(focusMode = next) }
    if (next) {
      settingsOpen = false
      chromeVisible = false
      scope.launch {
        if (hasDndPermission()) {
          snackbarHostState.showSnackbar(
            focusEnabledMsg,
            duration = SnackbarDuration.Short,
          )
        } else {
          val res =
            snackbarHostState.showSnackbar(
              message = focusNeedsDndMsg,
              actionLabel = grantPermissionLabel,
              duration = SnackbarDuration.Long,
            )
          if (res == SnackbarResult.ActionPerformed) {
            onOpenDndPermissionSettings()
          }
        }
      }
    } else {
      scope.launch {
        snackbarHostState.showSnackbar(
          focusDisabledMsg,
          duration = SnackbarDuration.Short,
        )
      }
    }
  }

  val effectivePdfMode = pdfConfig.mode ?: PdfReadingMode.SMART_ORIGINAL

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
          readingMode = effectivePdfMode,
          controller = pdfController,
          onPageChanged = { pageIndex, pageCount ->
            viewModel.onLocatorChanged(locatorForPdfPage(pageIndex, pageCount, positions))
          },
          onScreenTurn = viewModel::onSubPageScreenTurned,
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
    ReaderGhostAndFlashLayer(
      navigator = navigator,
      bookFormat = bookFormat,
      settings = settings,
      flashTriggerFlow = viewModel.flashTrigger,
      pageTurnTriggerFlow = viewModel.pageTurnTrigger,
      pagesSinceFlashFlow = viewModel.pagesSinceFlash,
    )
    if (settings.paperGrain) PaperGrainOverlay()
    if (!chromeVisible && settings.readerFooter && !settings.focusMode) {
      ReaderFooterBarContainer(
        title = title,
        progressFlow = viewModel.progress,
        inkColor = Color(settings.tone.text),
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
    if (settingsOpen) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .clickable(
              indication = null,
              interactionSource = remember { MutableInteractionSource() },
            ) {
              settingsOpen = false
            },
      )
    }
    if (chromeVisible) {
      val pdfModeLabel =
        if (bookFormat == BookFormat.PDF) {
          when (effectivePdfMode) {
            PdfReadingMode.SMART_ORIGINAL -> stringResource(com.example.emptyapp.R.string.pdf_mode_original)
            PdfReadingMode.GUIDED_COLUMN -> stringResource(com.example.emptyapp.R.string.pdf_mode_column)
            PdfReadingMode.HYBRID_REFLOW -> stringResource(com.example.emptyapp.R.string.pdf_mode_reflow)
          }
        } else {
          null
        }
      ReaderTopBar(
        title = title,
        showFontControls =
          bookFormat == BookFormat.EPUB ||
            (bookFormat == BookFormat.PDF && effectivePdfMode == PdfReadingMode.HYBRID_REFLOW),
        showSearch = bookFormat != BookFormat.CBZ && isSearchable,
        isBookmarked = isBookmarked,
        pdfModeLabel = pdfModeLabel,
        onTogglePdfMode =
          if (bookFormat == BookFormat.PDF) {
            {
              if (!pdfConfig.reflowAllowed) {
                scope.launch {
                  snackbarHostState.showSnackbar(
                    reflowLockedScanMsg,
                    duration = SnackbarDuration.Short,
                  )
                }
              } else {
                val nextMode =
                  when (effectivePdfMode) {
                    PdfReadingMode.SMART_ORIGINAL -> PdfReadingMode.GUIDED_COLUMN
                    PdfReadingMode.GUIDED_COLUMN -> PdfReadingMode.HYBRID_REFLOW
                    PdfReadingMode.HYBRID_REFLOW -> PdfReadingMode.SMART_ORIGINAL
                  }
                viewModel.updatePdfConfig { it.copy(mode = nextMode) }
              }
            }
          } else {
            null
          },
        onClose = onClose,
        onOpenSearch = { searchOpen = true; contentsOpen = false; settingsOpen = false },
        onOpenContents = { contentsOpen = true; searchOpen = false; settingsOpen = false },
        onBookmark = {
          val saved = viewModel.toggleBookmark()
          if (saved != null) {
            val msg = if (saved) bookmarkSavedMsg else bookmarkRemovedMsg
            scope.launch {
              snackbarHostState.currentSnackbarData?.dismiss()
              snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            }
          }
        },
        onDecreaseFont = {
          viewModel.updateSettings { it.copy(fontScale = (it.fontScale - 0.1).coerceAtLeast(0.7)) }
        },
        onIncreaseFont = {
          viewModel.updateSettings { it.copy(fontScale = (it.fontScale + 0.1).coerceAtMost(4.0)) }
        },
        modifier = Modifier.align(Alignment.TopCenter),
      )
      val bottomBarProgress by viewModel.progress.collectAsStateWithLifecycle()
      ReaderBottomBar(
        progress = bottomBarProgress,
        seekEnabled = positions.isNotEmpty(),
        tone = settings.tone,
        brightness = settings.brightness,
        onToneChange = { tone -> viewModel.updateSettings { it.copy(tone = tone) } },
        onBrightnessChange = viewModel::previewBrightness,
        onBrightnessChangeFinished = viewModel::commitBrightness,
        onSeekToProgress = { fraction ->
          if (positions.isNotEmpty()) {
            val index = (fraction * (positions.size - 1)).toInt().coerceIn(0, positions.lastIndex)
            viewModel.expectJump()
            if (bookFormat == BookFormat.PDF) {
              pdfController.jumpToPage(index)
            } else {
              navigator?.go(positions[index], animated = false)
            }
          }
        },
        focusMode = settings.focusMode,
        onToggleFocusMode = { handleToggleFocusMode(!settings.focusMode) },
        onToggleSettings = { settingsOpen = !settingsOpen },
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .onSizeChanged { bottomBarHeightPx = it.height },
      )
    }
    if (settingsOpen) {
      val bottomOffsetDp =
        if (bottomBarHeightPx > 0) with(density) { bottomBarHeightPx.toDp() } else 148.dp
      ReaderSettingsPanel(
        settings = settings,
        isReflowable =
          bookFormat == BookFormat.EPUB ||
            (bookFormat == BookFormat.PDF && effectivePdfMode == PdfReadingMode.HYBRID_REFLOW),
        isPdf = bookFormat == BookFormat.PDF,
        pdfMode = effectivePdfMode,
        pdfReflowAllowed = pdfConfig.reflowAllowed,
        onPdfModeChange = { next -> viewModel.updatePdfConfig { it.copy(mode = next) } },
        onToggleFocusMode = ::handleToggleFocusMode,
        onChange = viewModel::updateSettings,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .padding(bottom = bottomOffsetDp)
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
            resolvePdfTocPage(link, positions)?.let { page ->
              viewModel.expectJump()
              pdfController.jumpToPage(page)
            }
          } else {
            viewModel.expectJump()
            navigator?.go(link, animated = false)
          }
          contentsOpen = false
          chromeVisible = false
        },
        onSelectLocatorJson = { jsonText ->
          runCatching { Locator.fromJSON(JSONObject(jsonText)) }.getOrNull()?.let { locator ->
            viewModel.expectJump()
            if (bookFormat == BookFormat.PDF) {
              pdfController.jumpToPage(resolvePdfPageIndex(locator, positions))
            } else {
              navigator?.go(locator, animated = false)
            }
          }
          contentsOpen = false
          chromeVisible = false
        },
        onRemoveBookmark = { id ->
          val removed = bookmarks.firstOrNull { it.id == id }
          viewModel.removeBookmark(id)
          if (removed != null) {
            scope.launch {
              val res =
                snackbarHostState.showSnackbar(
                  message = libBookmarkDeletedMsg,
                  actionLabel = undoActionLabel,
                  duration = SnackbarDuration.Short,
                )
              if (res == SnackbarResult.ActionPerformed) {
                viewModel.restoreBookmark(removed)
              }
            }
          }
        },
        onRemoveHighlight = { id ->
          val removed = highlights.firstOrNull { it.id == id }
          viewModel.removeHighlight(id)
          if (removed != null) {
            scope.launch {
              val res =
                snackbarHostState.showSnackbar(
                  message = libHighlightDeletedMsg,
                  actionLabel = undoActionLabel,
                  duration = SnackbarDuration.Short,
                )
              if (res == SnackbarResult.ActionPerformed) {
                viewModel.restoreHighlight(removed)
              }
            }
          }
        },
        onDismiss = { contentsOpen = false },
      )
    }
    if (searchOpen) {
      ReaderSearchSheet(
        state = searchState,
        onSearch = viewModel::searchInBook,
        onLoadMore = viewModel::loadMoreSearchResults,
        onSelectLocator = { locator ->
          viewModel.expectJump()
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
        note = pendingNoteDraft,
        onNoteChange = onNoteDraftChange,
        onSave = { note -> onSaveNote(locator, note) },
        onDismiss = onDismissNote,
      )
    }
    dictionaryState?.let { dictState ->
      DictionaryDialog(
        state = dictState,
        onTranslateExternal = { query ->
          val encoded = java.net.URLEncoder.encode(query, "UTF-8")
          val tl = com.example.emptyapp.LocaleHelper.effectiveLanguageCode(context)
          val intent =
            Intent(
              Intent.ACTION_VIEW,
              Uri.parse("https://translate.google.com/?sl=auto&tl=$tl&text=$encoded"),
            )
          if (runCatching { context.startActivity(intent) }.isFailure) {
            scope.launch {
              snackbarHostState.showSnackbar(
                openBrowserErrorMsg,
                duration = SnackbarDuration.Short,
              )
            }
          }
        },
        onDismiss = viewModel::dismissDictionary,
      )
    }
    if (!chromeVisible && !searchOpen && !contentsOpen && !settingsOpen && pendingNoteLocator == null && dictionaryState == null) {
      Box(
        modifier =
          Modifier.align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(14.dp)
            .onSizeChanged { edgeStripHeightPx = it.height.coerceAtLeast(1).toFloat() }
            .pointerInput(Unit) {
              detectTapGestures(onTap = { latestOnLeftEdgeTap() })
            }
            .pointerInput(Unit) {
              var dragBrightness = 0.5f
              detectVerticalDragGestures(
                onDragStart = {
                  dragBrightness = if (latestBrightness < 0f) 0.5f else latestBrightness
                  brightnessHudVisible = true
                },
                onDragEnd = { viewModel.commitBrightness() },
                onDragCancel = { viewModel.commitBrightness() },
                onVerticalDrag = { change, dragAmount ->
                  change.consume()
                  val delta = -dragAmount / (edgeStripHeightPx * 0.65f)
                  dragBrightness = (dragBrightness + delta).coerceIn(0.02f, 1f)
                  viewModel.previewBrightness(dragBrightness)
                  brightnessHudVisible = true
                },
              )
            }
      )
    }
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
          text = androidx.compose.ui.res.stringResource(com.example.emptyapp.R.string.reader_brightness_hud, pct),
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

@Composable
private fun ReaderFooterBarContainer(
  title: String,
  progressFlow: StateFlow<Float>,
  inkColor: Color,
  modifier: Modifier = Modifier,
) {
  val progress by progressFlow.collectAsStateWithLifecycle()
  ReaderFooterBar(
    title = title,
    progress = progress,
    inkColor = inkColor,
    modifier = modifier,
  )
}

@Composable
private fun ReaderGhostAndFlashLayer(
  navigator: NavigatorFragment?,
  bookFormat: BookFormat,
  settings: EInkSettings,
  flashTriggerFlow: StateFlow<Int>,
  pageTurnTriggerFlow: StateFlow<Int>,
  pagesSinceFlashFlow: StateFlow<Int>,
) {
  val flashTrigger by flashTriggerFlow.collectAsStateWithLifecycle()
  val pagesSinceFlash by pagesSinceFlashFlow.collectAsStateWithLifecycle()
  var latestSnapshot by remember { mutableStateOf<GhostSnapshotCapture?>(null) }
  var activeGhost by remember { mutableStateOf<GhostSnapshotCapture?>(null) }
  val bufferA = remember { arrayOfNulls<Bitmap>(1) }
  val bufferB = remember { arrayOfNulls<Bitmap>(1) }
  var nextBufferSlot by remember { mutableStateOf(0) }

  LaunchedEffect(navigator, settings.ghosting) {
    val nav = navigator ?: return@LaunchedEffect
    if (!settings.ghosting) {
      activeGhost = null
      latestSnapshot = null
      return@LaunchedEffect
    }
    val view = nav.awaitView()
    merge(pageTurnTriggerFlow, flashTriggerFlow).collect {
      activeGhost = if (pagesSinceFlashFlow.value > 0) latestSnapshot else null
      delay(220L)
      val slotIndex = nextBufferSlot
      val slot = if (slotIndex == 0) bufferA else bufferB
      val captured = captureGhostSnapshot(view, slot)
      if (captured != null) {
        nextBufferSlot = 1 - slotIndex
        latestSnapshot = captured
      }
    }
  }

  val ghostAlpha =
    if (settings.ghosting && pagesSinceFlash > 0) (pagesSinceFlash * 0.014f).coerceAtMost(0.07f) else 0f
  val activeGhostCapture = activeGhost
  val applyGhostFilter =
    activeGhostCapture != null &&
      shouldApplyGhostOverlayFilter(
        sdkInt = Build.VERSION.SDK_INT,
        capturedViaPixelCopy = activeGhostCapture.capturedViaPixelCopy,
      )
  val ghostColorFilter =
    remember(settings.grayscale, settings.contrast, settings.tone, bookFormat, applyGhostFilter) {
      if (!applyGhostFilter) {
        null
      } else {
        ColorFilter.colorMatrix(
          ColorMatrix(
            EInkColorFilter.matrixOf(settings, contentAlreadyDark = bookFormat == BookFormat.EPUB).array,
          ),
        )
      }
    }

  RefreshFlashOverlay(
    ghostBitmap = activeGhostCapture?.bitmap,
    ghostAlpha = ghostAlpha,
    flashTrigger = flashTrigger,
    ghostColorFilter = ghostColorFilter,
  )
}

private data class GhostSnapshotCapture(
  val bitmap: ImageBitmap,
  val capturedViaPixelCopy: Boolean,
)

internal fun shouldApplyGhostOverlayFilter(
  sdkInt: Int,
  capturedViaPixelCopy: Boolean,
): Boolean = sdkInt < Build.VERSION_CODES.O || !capturedViaPixelCopy

private suspend fun captureGhostSnapshot(
  view: View,
  reusableSlot: Array<Bitmap?>,
): GhostSnapshotCapture? {
  if (view.width <= 0 || view.height <= 0) return null
  val scaledWidth = (view.width / 4).coerceAtLeast(1)
  val scaledHeight = (view.height / 4).coerceAtLeast(1)
  val existing = reusableSlot[0]
  val bitmap =
    if (existing != null && !existing.isRecycled && existing.width == scaledWidth && existing.height == scaledHeight) {
      existing
    } else {
      Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888).also { reusableSlot[0] = it }
    }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    val window = view.context.findActivity()?.window
    if (window != null && view.isAttachedToWindow) {
      val location = IntArray(2)
      view.getLocationInWindow(location)
      val srcRect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
      if (!srcRect.isEmpty) {
        val copied =
          suspendCancellableCoroutine { cont ->
            runCatching {
              PixelCopy.request(
                window,
                srcRect,
                bitmap,
                { result -> cont.resume(result == PixelCopy.SUCCESS) },
                Handler(Looper.getMainLooper()),
              )
            }.onFailure { cont.resume(false) }
          }
        if (copied) {
          return GhostSnapshotCapture(
            bitmap = bitmap.asImageBitmap(),
            capturedViaPixelCopy = true,
          )
        }
      }
    }
    return null
  }

  return runCatching {
    val canvas = Canvas(bitmap)
    canvas.scale(0.25f, 0.25f)
    view.draw(canvas)
    GhostSnapshotCapture(
      bitmap = bitmap.asImageBitmap(),
      capturedViaPixelCopy = false,
    )
  }.getOrNull()
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
