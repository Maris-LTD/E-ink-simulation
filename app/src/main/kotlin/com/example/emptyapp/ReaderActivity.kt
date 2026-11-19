package com.example.emptyapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.reader.EInkPreferencesMapper
import com.example.emptyapp.reader.EInkTone
import com.example.emptyapp.reader.FocusModeController
import com.example.emptyapp.reader.LockScreenReadingNotifier
import com.example.emptyapp.reader.LockScreenReadingState
import com.example.emptyapp.reader.LockScreenTextChunker
import com.example.emptyapp.reader.OrientationLock
import com.example.emptyapp.reader.pdf.PdfDocumentCache
import com.example.emptyapp.reader.pdf.PdfPageAnalyzer
import com.example.emptyapp.reader.pdf.PdfReflowEngine
import com.example.emptyapp.reader.pdf.ReflowItem
import com.example.emptyapp.reader.pdf.ReflowPageResult
import com.example.emptyapp.system.EInkReadingWidgetProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.emptyapp.ui.eink.EInkTheme
import com.example.emptyapp.ui.reader.PdfPagerController
import com.example.emptyapp.ui.reader.ReaderScreen
import com.example.emptyapp.ui.reader.ReaderUiState
import com.example.emptyapp.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleHelper.wrap(newBase))
  }

  private var navigator: OverflowableNavigator? = null
  private val pdfController = PdfPagerController()
  private val pendingNoteLocator = MutableStateFlow<Locator?>(null)
  private val focusModeController by lazy { FocusModeController(this) }
  private var lastLockScreenPositionKey: String? = null
  @Volatile private var cachedChapterSnippets: Pair<String, List<String>>? = null
  private val requestNotificationPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) postLockScreenNotification(force = true)
    }

  private val viewModel: ReaderViewModel by lazy {
    val container = (application as EmptyApplication).container
    val bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
    val overrideLocator = intent.getStringExtra(EXTRA_LOCATOR_JSON)
    val overrideLocatorCreatedAt = intent.getLongExtra(EXTRA_LOCATOR_CREATED_AT, 0L)
    intent.removeExtra(EXTRA_LOCATOR_JSON)
    intent.removeExtra(EXTRA_LOCATOR_CREATED_AT)
    androidx.lifecycle.ViewModelProvider(
      this,
      viewModelFactory {
        initializer {
          ReaderViewModel(
            bookId = bookId,
            repository = container.library,
            settingsStore = container.settings,
            pdfBookConfigs = container.pdfBookConfigs,
            annotations = container.annotations,
            dictionary = container.dictionary,
            statsRepository = container.stats,
            overrideLocatorJson = overrideLocator,
            overrideLocatorCreatedAt = overrideLocatorCreatedAt,
            context = applicationContext,
          )
        }
      },
    )[ReaderViewModel::class.java]
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(null)
    enableEdgeToEdge()
    hideSystemBars()
    setContent {
      val settings by viewModel.settings.collectAsStateWithLifecycle()
      val isDark = settings.tone == EInkTone.DARK
      LaunchedEffect(settings.tone) {
        window.decorView.setBackgroundColor(settings.tone.background)
        WindowInsetsControllerCompat(window, window.decorView).apply {
          isAppearanceLightStatusBars = !isDark
          isAppearanceLightNavigationBars = !isDark
        }
      }
      LaunchedEffect(settings.orientationLock) {
        requestedOrientation =
          when (settings.orientationLock) {
            OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
          }
      }
      LaunchedEffect(settings.lockScreenReader) {
        applyLockScreenMode(false)
        if (settings.lockScreenReader) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !LockScreenReadingNotifier.hasPermission(this@ReaderActivity)) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            postLockScreenNotification(force = true)
          }
        } else {
          LockScreenReadingNotifier.cancel(this@ReaderActivity)
        }
      }
      LaunchedEffect(viewModel.state.collectAsStateWithLifecycle().value is ReaderUiState.Ready, settings.lockScreenReader) {
        if (settings.lockScreenReader) postLockScreenNotification()
      }
      LaunchedEffect(settings.focusMode) {
        focusModeController.applyFocusMode(settings.focusMode)
      }
      EInkTheme(tone = settings.tone) { ReaderRoot() }
    }
  }

  private fun applyLockScreenMode(enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(enabled)
    } else {
      @Suppress("DEPRECATION")
      if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
      else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }
  }

  private fun postLockScreenNotification(force: Boolean = false) {
    if (!viewModel.settings.value.lockScreenReader) return
    val ready = viewModel.state.value as? ReaderUiState.Ready ?: return
    val container = (application as EmptyApplication).container
    val coverPath = container.library.coverFileOf(ready.book)?.absolutePath
    val progress = viewModel.progress.value
    val locator = viewModel.currentLocatorSnapshot() ?: ready.initialLocator
    val positionKey =
      "${ready.book.id}|${locator?.href}|${locator?.locations?.position}|${locator?.locations?.progression}"
    if (!force && positionKey == lastLockScreenPositionKey) return
    lastLockScreenPositionKey = positionKey
    val appContext = applicationContext

    container.appScope.launch(Dispatchers.IO) {
      val (snippets, startIdx) = extractLockScreenSnippets(ready, locator, progress)
      if (snippets.isEmpty()) return@launch
      val lockState =
        LockScreenReadingState(
          bookId = ready.book.id,
          title = ready.book.title,
          author = ready.book.author,
          snippets = snippets,
          currentSnippetIndex = startIdx,
          progress = progress,
          locatorJson = locator?.toJSON()?.toString(),
        )
      container.lockScreenReading.updateSession(lockState)
      LockScreenReadingNotifier.show(
        context = appContext,
        bookId = ready.book.id,
        title = ready.book.title,
        progress = progress,
        author = ready.book.author,
        coverPath = coverPath,
        snippet = lockState.currentSnippet,
        snippetIndex = lockState.currentSnippetIndex,
        snippetCount = lockState.snippets.size,
        locatorJson = lockState.locatorJson,
      )
    }
  }

  private suspend fun extractLockScreenSnippets(
    ready: ReaderUiState.Ready,
    locator: org.readium.r2.shared.publication.Locator?,
    progress: Float,
  ): Pair<List<String>, Int> {
    if (ready.book.format == BookFormat.PDF) {
      val totalPages = ready.positions.size.coerceAtLeast(1)
      val pageIdx =
        (locator?.locations?.position?.minus(1) ?: (progress * (totalPages - 1)).toInt())
          .coerceIn(0, totalPages - 1)
      val cached = PdfDocumentCache(File(filesDir, "pdf_cache")).load(ready.file)
      val analyses =
        if (cached != null && pageIdx in cached.indices) {
          listOfNotNull(cached.getOrNull(pageIdx), cached.getOrNull(pageIdx + 1))
        } else {
          PdfPageAnalyzer.ensureInitialized(applicationContext)
          PdfPageAnalyzer.analyzePages(ready.file, listOf(pageIdx, pageIdx + 1))
        }
      val current = analyses.firstOrNull { it.pageIndex == pageIdx } ?: return emptyList<String>() to 0
      val neighbors = analyses.filter { it.pageIndex != pageIdx }
      val reflowed = PdfReflowEngine.buildReflowPage(current, neighbors) as? ReflowPageResult.Reflowed
        ?: return emptyList<String>() to 0
      val bodyText =
        reflowed.items
          .filterIsInstance<ReflowItem.Paragraph>()
          .joinToString("\n\n") { it.text }
      val snippets = LockScreenTextChunker.chunkText(bodyText, maxCharsPerSnippet = 260)
      return snippets to 0
    } else {
      val targetHref = locator?.href ?: ready.publication.readingOrder.firstOrNull()?.url() ?: return emptyList<String>() to 0
      val link =
        ready.publication.readingOrder.firstOrNull { it.url() == targetHref }
          ?: ready.publication.readingOrder.firstOrNull()
          ?: return emptyList<String>() to 0
      val chapterKey = "${ready.book.id}|${link.url()}"
      val snippets =
        cachedChapterSnippets?.takeIf { it.first == chapterKey }?.second
          ?: run {
            val html =
              runCatching<String?> {
                val res = ready.publication.get(link) ?: return@runCatching null
                try {
                  res.read().getOrNull()?.decodeToString()
                } finally {
                  res.close()
                }
              }.getOrNull().orEmpty()
            if (html.isBlank()) return emptyList<String>() to 0
            val plain = LockScreenTextChunker.stripHtmlToPlainText(html)
            LockScreenTextChunker.chunkText(plain, maxCharsPerSnippet = 260)
              .also { cachedChapterSnippets = chapterKey to it }
          }
      val chapterProg = (locator?.locations?.progression ?: 0.0).toFloat()
      val idx = LockScreenTextChunker.estimateInitialSnippetIndex(snippets, chapterProg)
      return snippets to idx
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.onReaderForegroundChanged(true)
    if (viewModel.settings.value.focusMode) {
      focusModeController.applyFocusMode(true)
    }
  }

  override fun onPause() {
    viewModel.onReaderForegroundChanged(false)
    postLockScreenNotification()
    EInkReadingWidgetProvider.refreshAll(this)
    (application as EmptyApplication).container.sync.syncInBackgroundIfSignedIn()
    super.onPause()
  }

  override fun onStop() {
    focusModeController.onLeaveForeground()
    super.onStop()
  }

  override fun onUserInteraction() {
    super.onUserInteraction()
    viewModel.notifyUserInteraction()
  }

  @Composable
  private fun ReaderRoot() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val noteLocator by pendingNoteLocator.collectAsStateWithLifecycle()
    LaunchedEffect(settings.brightness) {
      val override =
        if (settings.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        else settings.brightness.coerceIn(0.02f, 1f)
      window.attributes = window.attributes.apply { screenBrightness = override }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      when (val current = state) {
        ReaderUiState.Loading -> CenteredMessage(getString(R.string.reader_opening), null)
        is ReaderUiState.Error -> CenteredMessage(current.message, ::finish)
        is ReaderUiState.Ready -> {
          if (current.book.format != BookFormat.PDF) {
            remember(current.publication) { installNavigatorFactory(current) }
          }
          ReaderScreen(
            viewModel = viewModel,
            bookFormat = current.book.format,
            bookFile = current.file,
            initialLocator = viewModel.currentLocatorSnapshot() ?: current.initialLocator,
            pdfController = pdfController,
            title = current.book.title,
            toc = current.toc,
            positions = current.positions,
            pendingNoteLocator = noteLocator,
            onSaveNote = { locator, note ->
              viewModel.addHighlight(locator, note)
              pendingNoteLocator.value = null
            },
            onDismissNote = { pendingNoteLocator.value = null },
            onClose = ::finish,
            onNavigatorReady = { navigator = it },
            hasDndPermission = focusModeController::hasDndPermission,
            onOpenDndPermissionSettings = focusModeController::openDndPermissionSettings,
          )
        }
      }
    }
  }

  private fun installNavigatorFactory(ready: ReaderUiState.Ready): Boolean {
    val activeLocator = viewModel.currentLocatorSnapshot() ?: ready.initialLocator
    supportFragmentManager.fragmentFactory =
      when (ready.book.format) {
        BookFormat.EPUB, BookFormat.PDF -> epubFragmentFactory(ready, activeLocator)
        BookFormat.CBZ ->
          ImageNavigatorFragment.createFactory(
            publication = ready.publication,
            initialLocator = activeLocator,
          )
      }
    return true
  }

  private fun epubFragmentFactory(ready: ReaderUiState.Ready, initialLocator: Locator?) =
    EpubNavigatorFactory(ready.publication).createFragmentFactory(
      initialLocator = initialLocator,
      initialPreferences = EInkPreferencesMapper.toEpub(viewModel.settings.value),
      configuration =
        EpubNavigatorFragment.Configuration(
          servedAssets = listOf("fonts/.*"),
          selectionActionModeCallback = selectionCallback(),
        ).apply {
          addFontFamilyDeclaration(
            EInkPreferencesMapper.READER_SERIF,
            alternates = listOf(FontFamily.SERIF),
          ) {
            addFontFace {
              addSource("fonts/literata.ttf")
              setFontStyle(FontStyle.NORMAL)
              setFontWeight(200..900)
            }
            addFontFace {
              addSource("fonts/literata_italic.ttf")
              setFontStyle(FontStyle.ITALIC)
              setFontWeight(200..900)
            }
          }
          addFontFamilyDeclaration(
            EInkPreferencesMapper.READER_SANS,
            alternates = listOf(FontFamily.SANS_SERIF),
          ) {
            addFontFace {
              addSource("fonts/rubik.ttf")
              setFontStyle(FontStyle.NORMAL)
              setFontWeight(200..900)
            }
          }
        },
    )

  @Composable
  private fun CenteredMessage(message: String, onBack: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(message, style = MaterialTheme.typography.bodyLarge)
      if (onBack != null) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.BottomCenter)) {
          Text(getString(R.string.action_back))
        }
      }
    }
  }

  private fun hideSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val ready = viewModel.state.value as? ReaderUiState.Ready
    if (ready?.book?.format == BookFormat.PDF) {
      return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> { pdfController.goForward(); true }
        KeyEvent.KEYCODE_VOLUME_UP -> { pdfController.goBackward(); true }
        else -> super.onKeyDown(keyCode, event)
      }
    }
    val nav = navigator ?: return super.onKeyDown(keyCode, event)
    return when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_DOWN -> nav.goForward(animated = false).let { true }
      KeyEvent.KEYCODE_VOLUME_UP -> nav.goBackward(animated = false).let { true }
      else -> super.onKeyDown(keyCode, event)
    }
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      true
    } else {
      super.onKeyUp(keyCode, event)
    }

  private fun selectionCallback(): ActionMode.Callback =
    object : ActionMode.Callback {
      override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_HIGHLIGHT, 0, getString(R.string.reader_menu_highlight))
        menu.add(Menu.NONE, MENU_NOTE, 1, getString(R.string.reader_menu_note))
        menu.add(Menu.NONE, MENU_LOOKUP, 2, getString(R.string.reader_menu_lookup))
        return true
      }

      override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

      override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val nav = navigator as? EpubNavigatorFragment ?: return false
        return when (item.itemId) {
          MENU_HIGHLIGHT -> {
            lifecycleScope.launch {
              nav.currentSelection()?.let { viewModel.addHighlight(it.locator) }
              nav.clearSelection()
            }
            mode.finish()
            true
          }
          MENU_NOTE -> {
            lifecycleScope.launch {
              nav.currentSelection()?.let { pendingNoteLocator.value = it.locator }
              nav.clearSelection()
            }
            mode.finish()
            true
          }
          MENU_LOOKUP -> {
            lifecycleScope.launch {
              nav.currentSelection()?.locator?.text?.highlight?.let(viewModel::lookupWord)
              nav.clearSelection()
            }
            mode.finish()
            true
          }
          else -> false
        }
      }

      override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

  companion object {
    private const val EXTRA_BOOK_ID = "book_id"
    private const val EXTRA_LOCATOR_JSON = "locator_json"
    private const val EXTRA_LOCATOR_CREATED_AT = "locator_created_at"
    private const val MENU_HIGHLIGHT = 1001
    private const val MENU_NOTE = 1002
    private const val MENU_LOOKUP = 1003

    fun intentFor(context: Context, bookId: String, locatorJson: String? = null): Intent =
      Intent(context, ReaderActivity::class.java)
        .putExtra(EXTRA_BOOK_ID, bookId)
        .apply {
          if (locatorJson != null) {
            putExtra(EXTRA_LOCATOR_JSON, locatorJson)
            putExtra(EXTRA_LOCATOR_CREATED_AT, System.currentTimeMillis())
          }
        }
  }
}
