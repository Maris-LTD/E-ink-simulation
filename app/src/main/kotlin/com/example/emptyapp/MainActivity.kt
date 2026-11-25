package com.example.emptyapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.reader.EInkTone
import com.example.emptyapp.ui.eink.EInkTheme
import com.example.emptyapp.ui.eink.PaperGrainOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val pendingExternalUri = MutableStateFlow<Uri?>(null)

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleHelper.wrap(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      pendingExternalUri.value = extractIncomingBookUri(intent)
    }
    (application as EmptyApplication).container.warmUpForUiAsync()
    enableEdgeToEdge()
    setContent {
      val settings by (application as EmptyApplication).container.settings.settings.collectAsStateWithLifecycle()
      val externalBookUri by pendingExternalUri.collectAsStateWithLifecycle()
      val isDark = settings.tone.isDarkTone
      LaunchedEffect(settings.tone) {
        window.decorView.setBackgroundColor(settings.tone.background)
        WindowInsetsControllerCompat(window, window.decorView).apply {
          isAppearanceLightStatusBars = !isDark
          isAppearanceLightNavigationBars = !isDark
        }
      }
      EInkTheme(tone = settings.tone) {
        Surface(
          modifier = Modifier.fillMaxSize().safeDrawingPadding(),
          color = MaterialTheme.colorScheme.background,
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            MainNavigation(
              externalBookUri = externalBookUri,
              onConsumeExternalBookUri = { pendingExternalUri.value = null },
            )
            if (settings.paperGrain) PaperGrainOverlay()
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractIncomingBookUri(intent)?.let { pendingExternalUri.value = it }
  }

  override fun onResume() {
    super.onResume()
    val container = (application as EmptyApplication).container
    container.appScope.launch {
      container.billing.refreshPurchases()
    }
  }
}
