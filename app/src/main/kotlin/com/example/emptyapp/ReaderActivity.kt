package com.example.emptyapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.emptyapp.ui.eink.EInkTheme
import com.example.emptyapp.ui.reader.ReaderScreen
import com.example.emptyapp.ui.reader.ReaderUiState
import com.example.emptyapp.ui.reader.ReaderViewModel
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity() {
  private var navigator: EpubNavigatorFragment? = null

  private val viewModel: ReaderViewModel by lazy {
    val container = (application as EmptyApplication).container
    val bookId = intent.getStringExtra(EXTRA_BOOK_ID).orEmpty()
    androidx.lifecycle.ViewModelProvider(
      this,
      viewModelFactory {
        initializer { ReaderViewModel(bookId, container.library, container.settings) }
      },
    )[ReaderViewModel::class.java]
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(null)
    enableEdgeToEdge()
    hideSystemBars()
    setContent { EInkTheme { ReaderRoot() } }
  }

  @Composable
  private fun ReaderRoot() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      when (val current = state) {
        ReaderUiState.Loading -> CenteredMessage("Đang mở sách...", null)
        is ReaderUiState.Error -> CenteredMessage(current.message, ::finish)
        is ReaderUiState.Ready -> {
          remember(current.publication) { installNavigatorFactory(current) }
          ReaderScreen(
            viewModel = viewModel,
            title = current.book.title,
            onClose = ::finish,
            onNavigatorReady = { navigator = it },
          )
        }
      }
    }
  }

  private fun installNavigatorFactory(ready: ReaderUiState.Ready): Boolean {
    val factory = EpubNavigatorFactory(ready.publication)
    val config =
      EpubNavigatorFragment.Configuration(servedAssets = listOf("fonts/.*")).apply {
        addFontFamilyDeclaration(
          com.example.emptyapp.reader.EInkPreferencesMapper.READER_SERIF,
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
      }
    supportFragmentManager.fragmentFactory =
      factory.createFragmentFactory(
        initialLocator = ready.initialLocator,
        initialPreferences =
          com.example.emptyapp.reader.EInkPreferencesMapper.toEpub(viewModel.settings.value),
        configuration = config,
      )
    return true
  }

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

  companion object {
    private const val EXTRA_BOOK_ID = "book_id"

    fun intentFor(context: Context, bookId: String): Intent =
      Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
  }
}
