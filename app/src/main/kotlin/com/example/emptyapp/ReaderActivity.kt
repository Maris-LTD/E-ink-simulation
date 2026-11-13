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
import com.example.emptyapp.reader.LockScreenReadingNotifier
import com.example.emptyapp.reader.OrientationLock
import com.example.emptyapp.system.EInkReadingWidgetProvider
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
  private var navigator: OverflowableNavigator? = null
  private val pdfController = PdfPagerController()
  private val pendingNoteLocator = MutableStateFlow<Locator?>(null)
  private val requestNotificationPermission =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) postLockScreenNotification()
    }

  private val viewModel: ReaderViewModel by lazy {
    val container = (application as EmptyApplication).container
    val bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
    val overrideLocator = intent.getStringExtra(EXTRA_LOCATOR_JSON)
    androidx.lifecycle.ViewModelProvider(
      this,
      viewModelFactory {
        initializer {
          ReaderViewModel(
            bookId = bookId,
            repository = container.library,
            settingsStore = container.settings,
            annotations = container.annotations,
            dictionary = container.dictionary,
            statsRepository = container.stats,
            overrideLocatorJson = overrideLocator,
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
        applyLockScreenMode(settings.lockScreenReader)
        if (settings.lockScreenReader) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !LockScreenReadingNotifier.hasPermission(this@ReaderActivity)) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            postLockScreenNotification()
          }
        } else {
          LockScreenReadingNotifier.cancel(this@ReaderActivity)
        }
      }
      LaunchedEffect(viewModel.state.collectAsStateWithLifecycle().value is ReaderUiState.Ready, settings.lockScreenReader) {
        if (settings.lockScreenReader) postLockScreenNotification()
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

  private fun postLockScreenNotification() {
    if (!viewModel.settings.value.lockScreenReader) return
    val ready = viewModel.state.value as? ReaderUiState.Ready ?: return
    LockScreenReadingNotifier.show(
      context = this,
      bookId = ready.book.id,
      title = ready.book.title,
      progress = viewModel.progress.value,
    )
  }

  override fun onResume() {
    super.onResume()
    viewModel.onReaderForegroundChanged(true)
  }

  override fun onPause() {
    viewModel.onReaderForegroundChanged(false)
    postLockScreenNotification()
    EInkReadingWidgetProvider.refreshAll(this)
    super.onPause()
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
        ReaderUiState.Loading -> CenteredMessage("Đang mở sách...", null)
        is ReaderUiState.Error -> CenteredMessage(current.message, ::finish)
        is ReaderUiState.Ready -> {
          if (current.book.format != BookFormat.PDF) {
            remember(current.publication) { installNavigatorFactory(current) }
          }
          ReaderScreen(
            viewModel = viewModel,
            bookFormat = current.book.format,
            bookFile = current.file,
            initialLocator = current.initialLocator,
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
          )
        }
      }
    }
  }

  private fun installNavigatorFactory(ready: ReaderUiState.Ready): Boolean {
    supportFragmentManager.fragmentFactory =
      when (ready.book.format) {
        BookFormat.EPUB, BookFormat.PDF -> epubFragmentFactory(ready)
        BookFormat.CBZ ->
          ImageNavigatorFragment.createFactory(
            publication = ready.publication,
            initialLocator = ready.initialLocator,
          )
      }
    return true
  }

  private fun epubFragmentFactory(ready: ReaderUiState.Ready) =
    EpubNavigatorFactory(ready.publication).createFragmentFactory(
      initialLocator = ready.initialLocator,
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
        },
    )

  @Composable
  private fun CenteredMessage(message: String, onBack: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(message, style = MaterialTheme.typography.bodyLarge)
      if (onBack != null) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.BottomCenter)) {
          Text("Quay lại")
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
        menu.add(Menu.NONE, MENU_HIGHLIGHT, 0, "Highlight")
        menu.add(Menu.NONE, MENU_NOTE, 1, "Ghi chú")
        menu.add(Menu.NONE, MENU_LOOKUP, 2, "Tra từ / Dịch")
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
    private const val MENU_HIGHLIGHT = 1001
    private const val MENU_NOTE = 1002
    private const val MENU_LOOKUP = 1003

    fun intentFor(context: Context, bookId: String, locatorJson: String? = null): Intent =
      Intent(context, ReaderActivity::class.java)
        .putExtra(EXTRA_BOOK_ID, bookId)
        .apply { if (locatorJson != null) putExtra(EXTRA_LOCATOR_JSON, locatorJson) }
  }
}
