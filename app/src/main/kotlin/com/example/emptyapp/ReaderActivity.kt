package com.example.emptyapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
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
import com.example.emptyapp.reader.pdf.PdfPageAnalysis
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
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.isSearchable

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity() {
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleHelper.wrap(newBase))
  }

  private var navigator: OverflowableNavigator? = null
  private val pdfController = PdfPagerController()
  private val focusModeController by lazy { FocusModeController(this) }
  private var lastLockScreenPositionKey: String? = null
  private var overrideLocatorConsumedInState: Boolean = false
  @Volatile private var cachedChapterSnippets: Pair<String, List<String>>? = null
  private val cachedPdfPageSnippets = PdfSnippetMemoryCache()
  private val requestNotificationPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted && NotificationManagerCompat.from(this).areNotificationsEnabled()) {
        postLockScreenNotification(force = true)
      } else {
        viewModel.updateSettings { it.copy(lockScreenReader = false) }
        val shouldShowRationale =
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)
        if (!shouldShowRationale) {
          viewModel.notifyNotificationPermissionBlocked()
        }
      }
    }

  private val viewModel: ReaderViewModel by lazy {
    val container = (application as EmptyApplication).container
    val bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
    val overrideLocator =
      if (overrideLocatorConsumedInState) null else intent.getStringExtra(EXTRA_LOCATOR_JSON)
    val overrideLocatorCreatedAt =
      if (overrideLocatorConsumedInState) 0L else intent.getLongExtra(EXTRA_LOCATOR_CREATED_AT, 0L)
    if (overrideLocator != null) {
      overrideLocatorConsumedInState = true
    }
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
    overrideLocatorConsumedInState =
      savedInstanceState?.getBoolean(STATE_OVERRIDE_LOCATOR_CONSUMED, false) == true
    val restoredNoteLocatorJson = savedInstanceState?.getString(STATE_PENDING_NOTE_LOCATOR_JSON)
    val restoredNoteDraft = savedInstanceState?.getString(STATE_PENDING_NOTE_DRAFT)
    super.onCreate(null)
    (application as EmptyApplication).container.warmUpForUiAsync()
    if (restoredNoteLocatorJson != null) {
      viewModel.restorePendingNoteIfAbsent(restoredNoteLocatorJson, restoredNoteDraft)
    }
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
      val showNotifRationaleState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
      LaunchedEffect(settings.lockScreenReader) {
        applyLockScreenMode(false)
        if (settings.lockScreenReader) {
          val prefs = getSharedPreferences("eink_permissions", Context.MODE_PRIVATE)
          val askedOnce = prefs.getBoolean("notif_asked_once", false)
          val hasRuntimePermission = LockScreenReadingNotifier.hasPermission(this@ReaderActivity)
          val areNotificationsEnabled =
            NotificationManagerCompat.from(this@ReaderActivity).areNotificationsEnabled()
          val shouldShowRationale =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
              ActivityCompat.shouldShowRequestPermissionRationale(
                this@ReaderActivity,
                Manifest.permission.POST_NOTIFICATIONS,
              )
          when (
            resolveNotificationPermissionAction(
              sdkInt = Build.VERSION.SDK_INT,
              hasRuntimePermission = hasRuntimePermission,
              areNotificationsEnabled = areNotificationsEnabled,
              askedOnce = askedOnce,
              shouldShowRationale = shouldShowRationale,
            )
          ) {
            NotificationPermissionAction.PROCEED -> postLockScreenNotification(force = true)
            NotificationPermissionAction.SHOW_RATIONALE -> showNotifRationaleState.value = true
            NotificationPermissionAction.BLOCKED_OPEN_SETTINGS -> {
              viewModel.updateSettings { it.copy(lockScreenReader = false) }
              viewModel.notifyNotificationPermissionBlocked()
            }
          }
        } else {
          LockScreenReadingNotifier.cancel(this@ReaderActivity)
        }
      }
      if (showNotifRationaleState.value) {
        androidx.compose.material3.AlertDialog(
          onDismissRequest = {
            showNotifRationaleState.value = false
            viewModel.updateSettings { it.copy(lockScreenReader = false) }
          },
          properties = DialogProperties(dismissOnClickOutside = false),
          title = { androidx.compose.material3.Text(getString(R.string.notif_rationale_title)) },
          text = { androidx.compose.material3.Text(getString(R.string.notif_rationale_message)) },
          confirmButton = {
            androidx.compose.material3.TextButton(
              onClick = {
                showNotifRationaleState.value = false
                getSharedPreferences("eink_permissions", Context.MODE_PRIVATE)
                  .edit().putBoolean("notif_asked_once", true).apply()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
              },
            ) {
              androidx.compose.material3.Text(getString(R.string.notif_rationale_allow))
            }
          },
          dismissButton = {
            androidx.compose.material3.TextButton(
              onClick = {
                showNotifRationaleState.value = false
                viewModel.updateSettings { it.copy(lockScreenReader = false) }
              },
            ) {
              androidx.compose.material3.Text(getString(R.string.notif_rationale_later))
            }
          },
        )
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
    val capturedAt = System.currentTimeMillis()
    val locatorJson = locator?.toJSON()?.toString()

    container.appScope.launch(Dispatchers.IO) {
      val (extractedSnippets, extractedStartIdx) = extractLockScreenSnippets(ready, locator, progress)
      val fallbackSnippet =
        locator?.title?.takeIf { it.isNotBlank() }
          ?: ready.toc.firstOrNull { it.url() == locator?.href }?.title?.takeIf { it.isNotBlank() }
          ?: ready.book.title.takeIf { it.isNotBlank() }
      val (snippets, startIdx) =
        if (extractedSnippets.isNotEmpty()) {
          extractedSnippets to extractedStartIdx
        } else if (fallbackSnippet != null) {
          listOf(fallbackSnippet) to 0
        } else {
          container.lockScreenReading.clear()
          LockScreenReadingNotifier.cancel(appContext)
          return@launch
        }
      val lockState =
        LockScreenReadingState(
          bookId = ready.book.id,
          title = ready.book.title,
          author = ready.book.author,
          snippets = snippets,
          currentSnippetIndex = startIdx,
          progress = progress,
          locatorJson = locatorJson,
          capturedAt = capturedAt,
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
        locatorCreatedAt = lockState.capturedAt,
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
      val appCtx = applicationContext
      val cache = PdfDocumentCache(File(appCtx.filesDir, "pdf_cache"))
      return extractPdfLockScreenSnippets(
        bookId = ready.book.id,
        file = ready.file,
        pageIdx = pageIdx,
        totalPages = totalPages,
        snippetCache = cachedPdfPageSnippets,
        documentCache = cache,
        analyzeFallback = { pdfFile, indices ->
          PdfPageAnalyzer.ensureInitialized(appCtx)
          PdfPageAnalyzer.analyzePages(pdfFile, indices)
        },
      )
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
    val container = (application as EmptyApplication).container
    container.appScope.launch(Dispatchers.IO) {
      container.sync.syncInBackgroundIfSignedIn()
    }
    super.onPause()
  }

  override fun onStop() {
    focusModeController.onLeaveForeground()
    super.onStop()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(
      STATE_OVERRIDE_LOCATOR_CONSUMED,
      overrideLocatorConsumedInState || viewModel.isOverrideLocatorConsumed(),
    )
    val noteLocatorJson = viewModel.pendingNoteLocatorJsonSnapshot()
    if (noteLocatorJson != null) {
      outState.putString(STATE_PENDING_NOTE_LOCATOR_JSON, noteLocatorJson)
      outState.putString(STATE_PENDING_NOTE_DRAFT, viewModel.pendingNoteDraft.value)
    }
  }

  private fun openAppNotificationSettings() {
    val notifIntent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
          putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      } else {
        null
      }
    val openedNotif =
      notifIntent != null && runCatching { startActivity(notifIntent) }.isSuccess
    if (!openedNotif) {
      val fallbackIntent =
        Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.fromParts("package", packageName, null),
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      if (runCatching { startActivity(fallbackIntent) }.isFailure) {
        val msg = LocaleHelper.wrap(this).getString(R.string.error_open_system_settings)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun onUserInteraction() {
    super.onUserInteraction()
    viewModel.notifyUserInteraction()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    viewModel.expectRelayout()
  }

  @Composable
  private fun ReaderRoot() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val noteLocator by viewModel.pendingNoteLocator.collectAsStateWithLifecycle()
    val noteDraft by viewModel.pendingNoteDraft.collectAsStateWithLifecycle()
    LaunchedEffect(settings.brightness) {
      val override =
        if (settings.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        else settings.brightness.coerceIn(0.02f, 1f)
      window.attributes = window.attributes.apply { screenBrightness = override }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      when (val current = state) {
        ReaderUiState.Loading -> CenteredMessage(getString(R.string.reader_opening), null)
        is ReaderUiState.Error -> CenteredMessage(current.message, ::finish, viewModel::retryLoad)
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
            isSearchable = current.publication.isSearchable,
            pendingNoteLocator = noteLocator,
            pendingNoteDraft = noteDraft,
            onNoteDraftChange = viewModel::updateNoteDraft,
            onSaveNote = { locator, note ->
              if (viewModel.addHighlight(locator, note)) {
                Toast.makeText(
                  this@ReaderActivity,
                  getString(R.string.reader_highlight_saved),
                  Toast.LENGTH_SHORT,
                ).show()
              }
              viewModel.dismissNoteDialog()
            },
            onDismissNote = viewModel::dismissNoteDialog,
            onClose = ::finish,
            onNavigatorReady = { navigator = it },
            hasDndPermission = focusModeController::hasDndPermission,
            onOpenDndPermissionSettings = focusModeController::openDndPermissionSettings,
            onOpenNotificationSettings = ::openAppNotificationSettings,
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
      paginationListener =
        object : EpubNavigatorFragment.PaginationListener {
          override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
            viewModel.onEpubPageChanged(totalPages, locator)
          }

          override fun onPageLoaded() = Unit
        },
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
  private fun CenteredMessage(
    message: String,
    onBack: (() -> Unit)?,
    onRetry: (() -> Unit)? = null,
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
      ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
          androidx.compose.material3.OutlinedButton(onClick = onRetry) {
            Text(getString(R.string.action_retry))
          }
        }
      }
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

  private fun canTurnPageWithVolumeKey(keyCode: Int): Boolean {
    val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
    return isVolumeKey &&
      shouldHandleVolumePageTurn(
        volumeButtonsTurnPages = viewModel.settings.value.volumeButtonsTurnPages,
        pendingNoteOpen = viewModel.pendingNoteLocator.value != null,
        dictionaryOpen = viewModel.dictionaryState.value != null,
        overlayOpen = viewModel.overlayOpen.value,
      )
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (!canTurnPageWithVolumeKey(keyCode)) {
      return super.onKeyDown(keyCode, event)
    }
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

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    return if (canTurnPageWithVolumeKey(keyCode)) {
      true
    } else {
      super.onKeyUp(keyCode, event)
    }
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
              val sel = nav.currentSelection()
              if (sel != null && viewModel.addHighlight(sel.locator)) {
                Toast.makeText(
                  this@ReaderActivity,
                  getString(R.string.reader_highlight_saved),
                  Toast.LENGTH_SHORT,
                ).show()
              }
              nav.clearSelection()
            }
            mode.finish()
            true
          }
          MENU_NOTE -> {
            lifecycleScope.launch {
              nav.currentSelection()?.let { viewModel.openNoteDialog(it.locator) }
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
    internal const val STATE_OVERRIDE_LOCATOR_CONSUMED = "override_locator_consumed"
    internal const val STATE_PENDING_NOTE_LOCATOR_JSON = "pending_note_locator_json"
    internal const val STATE_PENDING_NOTE_DRAFT = "pending_note_draft"
    private const val MENU_HIGHLIGHT = 1001
    private const val MENU_NOTE = 1002
    private const val MENU_LOOKUP = 1003

    fun intentFor(
      context: Context,
      bookId: String,
      locatorJson: String? = null,
      locatorCreatedAt: Long = System.currentTimeMillis(),
    ): Intent =
      Intent(context, ReaderActivity::class.java)
        .putExtra(EXTRA_BOOK_ID, bookId)
        .apply {
          if (locatorJson != null) {
            putExtra(EXTRA_LOCATOR_JSON, locatorJson)
            putExtra(
              EXTRA_LOCATOR_CREATED_AT,
              if (locatorCreatedAt > 0L) locatorCreatedAt else System.currentTimeMillis(),
            )
          }
        }
  }
}

internal enum class NotificationPermissionAction {
  PROCEED,
  SHOW_RATIONALE,
  BLOCKED_OPEN_SETTINGS,
}

internal fun resolveNotificationPermissionAction(
  sdkInt: Int,
  hasRuntimePermission: Boolean,
  areNotificationsEnabled: Boolean,
  askedOnce: Boolean,
  shouldShowRationale: Boolean,
): NotificationPermissionAction {
  if (sdkInt >= Build.VERSION_CODES.TIRAMISU && !hasRuntimePermission) {
    return if (!askedOnce || shouldShowRationale) {
      NotificationPermissionAction.SHOW_RATIONALE
    } else {
      NotificationPermissionAction.BLOCKED_OPEN_SETTINGS
    }
  }
  if (!areNotificationsEnabled) {
    return NotificationPermissionAction.BLOCKED_OPEN_SETTINGS
  }
  return NotificationPermissionAction.PROCEED
}

internal fun shouldHandleVolumePageTurn(
  volumeButtonsTurnPages: Boolean,
  pendingNoteOpen: Boolean,
  dictionaryOpen: Boolean,
  overlayOpen: Boolean,
): Boolean = volumeButtonsTurnPages && !pendingNoteOpen && !dictionaryOpen && !overlayOpen

internal class PdfSnippetMemoryCache(private val maxEntries: Int = 16) {
  private val lock = Any()
  private val entries =
    object : LinkedHashMap<Pair<String, Int>, List<String>>(maxEntries, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Int>, List<String>>?): Boolean =
        size > maxEntries
    }

  fun get(bookId: String, pageIndex: Int): List<String>? =
    synchronized(lock) { entries[bookId to pageIndex] }

  fun put(bookId: String, pageIndex: Int, snippets: List<String>) {
    synchronized(lock) { entries[bookId to pageIndex] = snippets }
  }
}

internal fun extractPdfLockScreenSnippets(
  bookId: String,
  file: File,
  pageIdx: Int,
  totalPages: Int,
  snippetCache: PdfSnippetMemoryCache,
  documentCache: PdfDocumentCache,
  analyzeFallback: (File, List<Int>) -> List<PdfPageAnalysis>,
): Pair<List<String>, Int> {
  snippetCache.get(bookId, pageIdx)?.let { return it to 0 }
  val requestedIndices =
    if (pageIdx + 1 < totalPages) listOf(pageIdx, pageIdx + 1) else listOf(pageIdx)
  val cachedByPage = documentCache.loadPages(file, requestedIndices)
  val current = cachedByPage[pageIdx]
  val analyses =
    if (current != null) {
      requestedIndices.mapNotNull { cachedByPage[it] }
    } else {
      analyzeFallback(file, requestedIndices)
    }
  val resolvedCurrent = analyses.firstOrNull { it.pageIndex == pageIdx } ?: return emptyList<String>() to 0
  val neighbors = analyses.filter { it.pageIndex != pageIdx }
  val reflowed = PdfReflowEngine.buildReflowPage(resolvedCurrent, neighbors) as? ReflowPageResult.Reflowed
  val snippets =
    if (reflowed != null) {
      val bodyText =
        reflowed.items
          .filterIsInstance<ReflowItem.Paragraph>()
          .joinToString("\n\n") { it.text }
      LockScreenTextChunker.chunkText(bodyText, maxCharsPerSnippet = 260)
    } else {
      emptyList()
    }
  snippetCache.put(bookId, pageIdx, snippets)
  return snippets to 0
}
