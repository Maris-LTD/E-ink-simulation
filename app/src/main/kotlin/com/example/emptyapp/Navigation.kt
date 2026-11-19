package com.example.emptyapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.emptyapp.ui.library.LibraryScreen
import com.example.emptyapp.ui.library.LibraryViewModel
import com.example.emptyapp.ui.library.ReadingStatsScreen

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val container = (context.applicationContext as EmptyApplication).container
  val backStack = rememberNavBackStack(Library)
  val viewModel = viewModel {
    LibraryViewModel(container.library, container.annotations, container.stats, context.applicationContext)
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Library> {
          val settings by container.settings.settings.collectAsStateWithLifecycle()
          LibraryScreen(
            viewModel = viewModel,
            settings = settings,
            onUpdateSettings = container.settings::update,
            grayscaleCovers = settings.grayscale,
            onOpenBook = { id, locatorJson ->
              context.startActivity(ReaderActivity.intentFor(context, id, locatorJson))
            },
            onOpenStats = {
              if (backStack.lastOrNull() != ReadingStats) {
                backStack.add(ReadingStats)
              }
            },
          )
        }
        entry<ReadingStats> {
          val books by viewModel.books.collectAsStateWithLifecycle()
          val highlights by viewModel.highlights.collectAsStateWithLifecycle()
          val stats by viewModel.stats.collectAsStateWithLifecycle()
          val settings by container.settings.settings.collectAsStateWithLifecycle()
          val syncState by container.sync.uiState.collectAsStateWithLifecycle()
          ReadingStatsScreen(
            stats = stats,
            completedBooksCount = books.count { it.progress >= 0.99f },
            highlightsCount = highlights.size,
            syncState = syncState,
            onSignInAndSync = { container.sync.signInAndSync() },
            onHandleSyncConsent = { data -> container.sync.handleConsentResult(data) },
            onLaunchSignInAndSync = { onNeedConsent -> container.sync.launchSignInAndSync(onNeedConsent) },
            onLaunchHandleSyncConsent = { data -> container.sync.launchHandleConsentResult(data) },
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
              (context as? android.app.Activity)?.recreate()
            },
            onBack = { backStack.removeLastOrNull() },
          )
        }
      },
  )
}
