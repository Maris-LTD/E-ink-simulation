package com.example.emptyapp.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.reader.EInkColorFilter
import com.example.emptyapp.reader.EInkPreferencesMapper
import com.example.emptyapp.reader.attachReaderInput
import com.example.emptyapp.reader.awaitView
import com.example.emptyapp.ui.eink.RefreshFlashOverlay
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
  viewModel: ReaderViewModel,
  title: String,
  onClose: () -> Unit,
  onNavigatorReady: (EpubNavigatorFragment) -> Unit,
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()
  val flashTrigger by viewModel.flashTrigger.collectAsStateWithLifecycle()
  var chromeVisible by remember { mutableStateOf(false) }
  var settingsOpen by remember { mutableStateOf(false) }
  var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

  BackHandler(enabled = settingsOpen || chromeVisible) {
    if (settingsOpen) settingsOpen = false else chromeVisible = false
  }

  navigator?.let { nav ->
    LaunchedEffect(nav) {
      nav.awaitView()
      nav.attachReaderInput(onCenterTap = { chromeVisible = !chromeVisible; settingsOpen = false })
      onNavigatorReady(nav)
    }
    LaunchedEffect(nav, settings) {
      val view = nav.awaitView()
      nav.submitPreferences(EInkPreferencesMapper.toEpub(settings))
      view.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, EInkColorFilter.paintOf(settings))
    }
    LaunchedEffect(nav) { nav.currentLocator.collect(viewModel::onLocatorChanged) }
  }

  Box(modifier = Modifier.fillMaxSize().background(Color(settings.tone.background))) {
    AndroidFragment<EpubNavigatorFragment>(
      modifier = Modifier.fillMaxSize(),
      onUpdate = { fragment -> if (navigator == null) navigator = fragment },
    )
    RefreshFlashOverlay(trigger = flashTrigger)
    if (chromeVisible) {
      ReaderTopBar(title = title, onClose = onClose, modifier = Modifier.align(Alignment.TopCenter))
      ReaderBottomBar(
        progress = progress,
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
            .padding(bottom = 64.dp)
            .fillMaxHeight(0.6f)
            .background(MaterialTheme.colorScheme.background),
      )
    }
  }
}

@Composable
private fun ReaderTopBar(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .border(1.dp, MaterialTheme.colorScheme.outline)
        .statusBarsPadding()
        .padding(horizontal = 8.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(onClick = onClose) { Text("‹ Thư viện") }
    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
  }
}

@Composable
private fun ReaderBottomBar(
  progress: Float,
  onToggleSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .border(1.dp, MaterialTheme.colorScheme.outline)
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
    TextButton(onClick = onToggleSettings) { Text("Cài đặt") }
  }
}
