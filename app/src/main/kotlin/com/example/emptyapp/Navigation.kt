package com.example.emptyapp

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emptyapp.ui.eink.BookOpenIcon
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText
import com.example.emptyapp.ui.eink.SettingsGearIcon
import com.example.emptyapp.ui.library.LibraryScreen
import com.example.emptyapp.ui.library.LibraryViewModel
import com.example.emptyapp.ui.library.ReaderAvatarIcon
import com.example.emptyapp.ui.library.ReadingStatsScreen
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
  externalBookUri: Uri? = null,
  onConsumeExternalBookUri: () -> Unit = {},
) {
  val context = LocalContext.current
  val container = (context.applicationContext as EmptyApplication).container
  val pagerState = rememberPagerState(initialPage = 0) { 3 }
  val scope = rememberCoroutineScope()
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
    pagerState.scrollToPage(0)
    viewModel.importAndOpenExternal(uri) { bookId ->
      context.startActivity(ReaderActivity.intentFor(context, bookId))
    }
  }

  BackHandler(enabled = pagerState.currentPage != 0) {
    scope.launch { pagerState.animateScrollToPage(0) }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.weight(1f).fillMaxWidth(),
      beyondViewportPageCount = 1,
    ) { page ->
      when (page) {
        0 -> {
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
              scope.launch { pagerState.animateScrollToPage(1) }
            },
            onOpenSettings = {
              scope.launch { pagerState.animateScrollToPage(2) }
            },
          )
        }
        1 -> {
          StatsOrSettingsRoute(
            container = container,
            viewModel = viewModel,
            initialTab = 0,
            onOpenTutorial = {
              scope.launch { pagerState.animateScrollToPage(0) }
              openTutorialRequested = true
            },
            onBack = {
              scope.launch { pagerState.animateScrollToPage(0) }
            },
          )
        }
        else -> {
          StatsOrSettingsRoute(
            container = container,
            viewModel = viewModel,
            initialTab = 1,
            onOpenTutorial = {
              scope.launch { pagerState.animateScrollToPage(0) }
              openTutorialRequested = true
            },
            onBack = {
              scope.launch { pagerState.animateScrollToPage(0) }
            },
          )
        }
      }
    }
    EInkBottomNavBar(
      selectedIndex = pagerState.currentPage,
      onSelectTab = { index ->
        if (pagerState.currentPage != index) {
          scope.launch { pagerState.animateScrollToPage(index) }
        }
      },
    )
  }
}

@Composable
private fun EInkBottomNavBar(
  selectedIndex: Int,
  onSelectTab: (Int) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
    HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val tabs =
        listOf(
          R.string.nav_library,
          R.string.nav_stats,
          R.string.nav_settings,
        )
      tabs.forEachIndexed { index, labelRes ->
        val selected = selectedIndex == index
        val contentColor =
          if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        Column(
          modifier =
            Modifier.weight(1f)
              .defaultMinSize(minHeight = 56.dp)
              .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { onSelectTab(index) },
              )
              .padding(horizontal = 6.dp, vertical = 6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Box(
            modifier =
              Modifier.width(28.dp)
                .height(2.5.dp)
                .clip(EInkShapes.Pill)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background),
          )
          when (index) {
            0 -> BookOpenIcon(modifier = Modifier.size(20.dp), tint = contentColor)
            1 -> ReaderAvatarIcon(modifier = Modifier.size(20.dp), tint = contentColor)
            else -> SettingsGearIcon(modifier = Modifier.size(20.dp), tint = contentColor)
          }
          EInkText(
            text = stringResource(labelRes),
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun StatsOrSettingsRoute(
  container: AppContainer,
  viewModel: LibraryViewModel,
  initialTab: Int,
  onOpenTutorial: () -> Unit,
  onBack: () -> Unit,
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
    onBack = onBack,
  )
}
