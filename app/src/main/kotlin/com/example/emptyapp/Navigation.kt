package com.example.emptyapp

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.emptyapp.ui.library.LibraryScreen
import com.example.emptyapp.ui.library.LibraryViewModel
import com.example.emptyapp.ui.library.ReadingStatsScreen

@Composable
fun MainNavigation(
  externalBookUri: Uri? = null,
  onConsumeExternalBookUri: () -> Unit = {},
) {
  val context = LocalContext.current
  val container = (context.applicationContext as EmptyApplication).container
  val backStack = rememberNavBackStack(Library)
  var openTutorialRequested by rememberSaveable { mutableStateOf(false) }
  val viewModel = viewModel {
    LibraryViewModel(
      repository = container.library,
      annotations = container.annotations,
      statsRepository = container.stats,
      context = context.applicationContext,
      tombstones = container.syncTombstones,
    )
  }

  LaunchedEffect(externalBookUri) {
    val uri = externalBookUri ?: return@LaunchedEffect
    onConsumeExternalBookUri()
    while (backStack.size > 1) {
      backStack.removeLastOrNull()
    }
    viewModel.importAndOpenExternal(uri) { bookId ->
      context.startActivity(ReaderActivity.intentFor(context, bookId))
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = {
      if (backStack.size > 1) {
        backStack.removeLastOrNull()
      }
    },
    entryProvider =
      entryProvider {
        entry<Library> {
          val settings by container.settings.settings.collectAsStateWithLifecycle()
          LibraryScreen(
            viewModel = viewModel,
            settings = settings,
            onUpdateSettings = container.settings::update,
            grayscaleCovers = settings.grayscale,
            billing = container.billing,
            proGate = container.proGate,
            sampleBooksSeeder = container.sampleBooksSeeder,
            openTutorialRequested = openTutorialRequested,
            onConsumeTutorialRequest = { openTutorialRequested = false },
            onOpenBook = { id, locatorJson ->
              context.startActivity(ReaderActivity.intentFor(context, id, locatorJson))
            },
            onOpenStats = {
              pushNavKeyIfNotTop(backStack, ReadingStats)
            },
            onOpenSettings = {
              pushNavKeyIfNotTop(backStack, AppSettings)
            },
          )
        }
        entry<ReadingStats> {
          StatsOrSettingsRoute(
            container = container,
            viewModel = viewModel,
            initialTab = 0,
            expectedKey = ReadingStats,
            backStack = backStack,
            onOpenTutorial = {
              popBackStackSafely(backStack, ReadingStats)
              openTutorialRequested = true
            },
          )
        }
        entry<AppSettings> {
          StatsOrSettingsRoute(
            container = container,
            viewModel = viewModel,
            initialTab = 1,
            expectedKey = AppSettings,
            backStack = backStack,
            onOpenTutorial = {
              popBackStackSafely(backStack, AppSettings)
              openTutorialRequested = true
            },
          )
        }
      },
  )
}

@Composable
private fun StatsOrSettingsRoute(
  container: AppContainer,
  viewModel: LibraryViewModel,
  initialTab: Int,
  expectedKey: NavKey,
  backStack: MutableList<NavKey>,
  onOpenTutorial: () -> Unit,
) {
  val context = LocalContext.current
  val books by viewModel.books.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  val stats by viewModel.stats.collectAsStateWithLifecycle()
  val settings by container.settings.settings.collectAsStateWithLifecycle()
  val syncState by container.sync.uiState.collectAsStateWithLifecycle()
  val writeError by viewModel.writeError.collectAsStateWithLifecycle()
  ReadingStatsScreen(
    stats = stats,
    completedBooksCount = books.count { it.progress >= 0.99f },
    highlightsCount = highlights.size,
    syncState = syncState,
    shouldConfirmMeteredBookSync = container.sync::shouldConfirmMeteredBookSync,
    onSignInAndSync = { allowBookFiles -> container.sync.signInAndSync(allowBookFiles) },
    onHandleSyncConsent = { data, allowBookFiles -> container.sync.handleConsentResult(data, allowBookFiles) },
    onLaunchSignInAndSync = { allowBookFiles, onNeedConsent ->
      container.sync.launchSignInAndSync(allowBookFiles = allowBookFiles, onNeedConsent = onNeedConsent)
    },
    onLaunchHandleSyncConsent = { data, allowBookFiles ->
      container.sync.launchHandleConsentResult(data, allowBookFiles = allowBookFiles)
    },
    onToggleSyncBookFiles = { enabled -> container.sync.setSyncBookFiles(enabled) },
    onDeleteCloudBook = { book -> container.sync.deleteBookFromCloud(book.contentHash, book.title) },
    onLaunchDeleteCloudBook = { book -> container.sync.deleteBookFromCloudAsync(book.contentHash, book.title) },
    onDeleteAllCloudData = { container.sync.deleteAllCloudData() },
    onLaunchDeleteAllCloudData = { container.sync.launchDeleteAllCloudData() },
    onSignOutSync = { container.sync.signOut() },
    onChangeGoalMinutes = viewModel::setDailyGoalMinutes,
    settings = settings,
    onUpdateSettings = container.settings::update,
    currentLanguage = settings.appLanguage,
    onChangeLanguage = { lang ->
      container.settings.update { it.copy(appLanguage = lang) }
      com.example.emptyapp.system.EInkReadingWidgetProvider.refreshAll(context)
      (context as? android.app.Activity)?.recreate()
    },
    writeError = writeError,
    billing = container.billing,
    proGate = container.proGate,
    initialTab = initialTab,
    onOpenTutorial = onOpenTutorial,
    onBack = { popBackStackSafely(backStack, expectedKey) },
  )
}
