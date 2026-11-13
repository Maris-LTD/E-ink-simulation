package com.example.emptyapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.emptyapp.ui.library.LibraryScreen
import com.example.emptyapp.ui.library.LibraryViewModel
import com.example.emptyapp.ui.library.ReadingStatsScreen
import kotlinx.coroutines.launch

@Composable
fun MainNavigation() {
  val context = LocalContext.current
  val container = (context.applicationContext as EmptyApplication).container
  val backStack = rememberNavBackStack(Library)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Library> {
          val viewModel = viewModel { LibraryViewModel(container.library, container.annotations, container.stats) }
          val books by viewModel.books.collectAsStateWithLifecycle()
          val highlights by viewModel.highlights.collectAsStateWithLifecycle()
          val stats by viewModel.stats.collectAsStateWithLifecycle()
          val syncState by container.sync.uiState.collectAsStateWithLifecycle()
          val pagerState = rememberPagerState(pageCount = { 2 })
          val scope = rememberCoroutineScope()

          BackHandler(enabled = pagerState.currentPage != 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
          }

          HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize(),
          ) { page ->
            if (page == 0) {
              LibraryScreen(
                viewModel = viewModel,
                onOpenBook = { id, locatorJson ->
                  context.startActivity(ReaderActivity.intentFor(context, id, locatorJson))
                },
                onOpenStats = { scope.launch { pagerState.animateScrollToPage(1) } },
              )
            } else {
              ReadingStatsScreen(
                stats = stats,
                completedBooksCount = books.count { it.progress >= 0.99f },
                highlightsCount = highlights.size,
                syncState = syncState,
                onSignInAndSync = { container.sync.signInAndSync() },
                onHandleSyncConsent = { data -> container.sync.handleConsentResult(data) },
                onToggleSyncBookFiles = { enabled -> container.sync.setSyncBookFiles(enabled) },
                onSignOutSync = { container.sync.signOut() },
                onChangeGoalMinutes = viewModel::setDailyGoalMinutes,
                onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
              )
            }
          }
        }
      },
  )
}
