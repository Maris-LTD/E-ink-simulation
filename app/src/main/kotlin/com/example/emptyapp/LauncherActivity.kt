package com.example.emptyapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.mostRecentlyReadBook
import com.example.emptyapp.data.stats.ReadingGamification
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.reader.EInkTone
import com.example.emptyapp.ui.eink.CoverBitmapCache
import com.example.emptyapp.ui.eink.EInkText
import com.example.emptyapp.ui.eink.EInkTheme
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LaunchableApp(
  val label: String,
  val packageName: String,
)

private enum class DrawerViewMode(val labelRes: Int) {
  LIST(R.string.launcher_view_list),
  GRID_3(R.string.launcher_view_grid_3),
  GRID_4(R.string.launcher_view_grid_4),
  TEXT_ONLY(R.string.launcher_view_text);

  fun next(): DrawerViewMode = entries[(ordinal + 1) % entries.size]
}

class LauncherActivity : ComponentActivity() {
  private var appliedLanguage: com.example.emptyapp.reader.AppLanguage? = null

  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleHelper.wrap(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    appliedLanguage = LocaleHelper.getSavedLanguage(this)
    super.onCreate(savedInstanceState)
    (application as EmptyApplication).container.warmUpForUiAsync()
    enableEdgeToEdge()
    setContent {
      val container = (application as EmptyApplication).container
      val settings by container.settings.settings.collectAsStateWithLifecycle()
      val books by container.library.books.collectAsStateWithLifecycle()
      val highlights by container.annotations.highlights.collectAsStateWithLifecycle()
      val stats by container.stats.stats.collectAsStateWithLifecycle()
      val isDark = settings.tone == EInkTone.DARK

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
          EInkHomePager(
            books = books,
            highlightsCount = highlights.size,
            stats = stats,
            tone = settings.tone,
            grayscale = settings.grayscale,
            coverOf = container.library::coverFileOf,
            onToneChange = { tone -> container.settings.update { it.copy(tone = tone) } },
            onOpenLibrary = { startActivity(Intent(this, MainActivity::class.java)) },
            onOpenBook = { id -> startActivity(ReaderActivity.intentFor(this, id)) },
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    val currentLanguage = LocaleHelper.getSavedLanguage(this)
    if (appliedLanguage != null && appliedLanguage != currentLanguage) {
      recreate()
    }
  }
}

@Composable
private fun EInkHomePager(
  books: List<BookEntry>,
  highlightsCount: Int,
  stats: ReadingStatsData,
  tone: EInkTone,
  grayscale: Boolean,
  coverOf: (BookEntry) -> File?,
  onToneChange: (EInkTone) -> Unit,
  onOpenLibrary: () -> Unit,
  onOpenBook: (String) -> Unit,
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences("eink_launcher_prefs", Context.MODE_PRIVATE) }
  val pagerState = rememberPagerState(pageCount = { 2 })
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  var apps by remember { mutableStateOf(LauncherAppCache.cachedAppsOrEmpty()) }
  LaunchedEffect(context) {
    apps = withContext(Dispatchers.IO) { LauncherAppCache.loadApps(context) }
  }
  DisposableEffect(context) {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
          val pkg = intent.data?.schemeSpecificPart
          if (pkg != null) LauncherAppCache.evictIcon(pkg)
          scope.launch {
            apps = withContext(Dispatchers.IO) { LauncherAppCache.loadApps(context) }
          }
        }
      }
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addDataScheme("package")
      }
    androidx.core.content.ContextCompat.registerReceiver(
      context,
      receiver,
      filter,
      androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    onDispose { runCatching { context.unregisterReceiver(receiver) } }
  }

  var pinnedPackages by remember {
    mutableStateOf(prefs.getStringSet("pinned_packages", null))
  }
  LaunchedEffect(apps, pinnedPackages) {
    if (pinnedPackages == null && apps.isNotEmpty()) {
      pinnedPackages = apps.take(4).map { it.packageName }.toSet()
    }
  }
  val resolvedPinned = pinnedPackages.orEmpty()
  var drawerViewMode by remember {
    val saved = prefs.getString("drawer_view_mode", DrawerViewMode.GRID_3.name)
    mutableStateOf(DrawerViewMode.entries.firstOrNull { it.name == saved } ?: DrawerViewMode.GRID_3)
  }

  val pinnedToastFmt = stringResource(R.string.launcher_pinned_toast)
  val unpinnedToastFmt = stringResource(R.string.launcher_unpinned_toast)

  fun togglePin(app: LaunchableApp) {
    val current = pinnedPackages.orEmpty()
    val next =
      if (app.packageName in current) current - app.packageName
      else current + app.packageName
    pinnedPackages = next
    prefs.edit().putStringSet("pinned_packages", next).apply()
    val msg =
      String.format(
        if (app.packageName in next) pinnedToastFmt else unpinnedToastFmt,
        app.label,
      )
    scope.launch { snackbarHostState.showSnackbar(msg) }
  }

  fun cycleViewMode() {
    val next = drawerViewMode.next()
    drawerViewMode = next
    prefs.edit().putString("drawer_view_mode", next.name).apply()
  }

  BackHandler(enabled = true) {
    if (pagerState.currentPage != 0) {
      scope.launch { pagerState.animateScrollToPage(0) }
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    HorizontalPager(
      state = pagerState,
      beyondViewportPageCount = 1,
      verticalAlignment = Alignment.Top,
      modifier = Modifier.padding(padding).fillMaxSize(),
    ) { page ->
      if (page == 0) {
        MinimalistHomePage(
          books = books,
          highlightsCount = highlightsCount,
          stats = stats,
          tone = tone,
          grayscale = grayscale,
          pinnedApps = remember(apps, resolvedPinned) { apps.filter { it.packageName in resolvedPinned } },
          coverOf = coverOf,
          onToneChange = onToneChange,
          onOpenLibrary = onOpenLibrary,
          onOpenBook = onOpenBook,
          onOpenDrawer = { scope.launch { pagerState.animateScrollToPage(1) } },
          onLaunchApp = { pkg -> launchPackage(context, pkg) },
          onUnpinApp = { app -> togglePin(app) },
        )
      } else {
        MinimalistAppDrawerPage(
          apps = apps,
          pinnedPackages = resolvedPinned,
          viewMode = drawerViewMode,
          onCycleViewMode = ::cycleViewMode,
          onLaunchApp = { pkg -> launchPackage(context, pkg) },
          onTogglePin = ::togglePin,
          onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
        )
      }
    }
  }
}

internal const val HOME_PINNED_MAX_VISIBLE = 8

internal data class PinnedAppsHomeSlice<T>(
  val visibleApps: List<T>,
  val overflowCount: Int,
)

internal fun <T> slicePinnedAppsForHome(
  pinnedApps: List<T>,
  maxVisible: Int = HOME_PINNED_MAX_VISIBLE,
): PinnedAppsHomeSlice<T> {
  val cap = maxVisible.coerceAtLeast(0)
  if (pinnedApps.size <= cap) {
    return PinnedAppsHomeSlice(visibleApps = pinnedApps, overflowCount = 0)
  }
  return PinnedAppsHomeSlice(
    visibleApps = pinnedApps.take(cap),
    overflowCount = pinnedApps.size - cap,
  )
}

internal fun layoutHomeSectionPositions(
  totalSize: Int,
  sizes: IntArray,
  spacingPx: Int,
  outPositions: IntArray,
) {
  if (sizes.isEmpty()) return
  if (sizes.size == 1) {
    outPositions[0] = 0
    return
  }
  val contentSum = sizes.sum()
  val minTotal = contentSum + spacingPx * (sizes.size - 1)
  if (totalSize <= minTotal) {
    var y = 0
    for (i in sizes.indices) {
      outPositions[i] = y
      y += sizes[i] + spacingPx
    }
  } else {
    val gap = (totalSize - contentSum).toFloat() / (sizes.size - 1)
    var y = 0f
    for (i in sizes.indices) {
      outPositions[i] = y.toInt()
      y += sizes[i] + gap
    }
  }
}

private val HomeSectionArrangement =
  object : Arrangement.Vertical {
    override val spacing: Dp = 12.dp

    override fun Density.arrange(
      totalSize: Int,
      sizes: IntArray,
      outPositions: IntArray,
    ) {
      layoutHomeSectionPositions(
        totalSize = totalSize,
        sizes = sizes,
        spacingPx = spacing.roundToPx(),
        outPositions = outPositions,
      )
    }
  }

@Composable
private fun MinimalistHomePage(
  books: List<BookEntry>,
  highlightsCount: Int,
  stats: ReadingStatsData,
  tone: EInkTone,
  grayscale: Boolean,
  pinnedApps: List<LaunchableApp>,
  coverOf: (BookEntry) -> File?,
  onToneChange: (EInkTone) -> Unit,
  onOpenLibrary: () -> Unit,
  onOpenBook: (String) -> Unit,
  onOpenDrawer: () -> Unit,
  onLaunchApp: (String) -> Unit,
  onUnpinApp: (LaunchableApp) -> Unit,
) {
  val context = LocalContext.current
  var now by remember { mutableStateOf(LocalDateTime.now()) }
  var batteryPct by remember { mutableStateOf(readBatteryPercent(context)) }
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
      while (true) {
        val current = LocalDateTime.now()
        now = current
        batteryPct = readBatteryPercent(context)
        val msUntilNextMinute =
          ((60 - current.second) * 1000L - current.nano / 1_000_000L).coerceIn(1_000L, 60_000L)
        delay(msUntilNextMinute)
      }
    }
  }

  val timeText = remember(now) { now.format(LAUNCHER_TIME_FORMATTER) }
  val langCode = remember(context) { LocaleHelper.effectiveLanguageCode(context) }
  val dateText =
    remember(now, langCode) {
      val dateLocale = if (langCode == "en") Locale.ENGLISH else Locale("vi", "VN")
      now.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", dateLocale))
    }
  val currentBook = remember(books) { books.mostRecentlyReadBook() }
  val completedCount = remember(books) { books.count { it.progress >= 0.99f } }
  val rank =
    remember(stats, completedCount, highlightsCount) {
      ReadingGamification.rankFor(stats, completedCount, highlightsCount, context)
    }
  val todayMin = stats.todaySeconds() / 60
  val goalMin = stats.dailyGoalMinutes.coerceAtLeast(5)
  val goalProgress = (stats.todaySeconds().toFloat() / (goalMin * 60f)).coerceIn(0f, 1f)
  val pinnedSlice = remember(pinnedApps) { slicePinnedAppsForHome(pinnedApps) }

  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BoxWithConstraints(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight),
        verticalArrangement = HomeSectionArrangement,
      ) {
        // 1. Top status bar: Battery + Streak + Quick Tone Swatches
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          EInkText(
            text = stringResource(R.string.launcher_status_format, batteryPct, stats.currentStreak),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EInkTone.entries.forEach { option ->
              val selected = option == tone
              Box(
                modifier =
                  Modifier.size(18.dp)
                    .clip(CircleShape)
                    .background(Color(option.background))
                    .border(
                      width = if (selected) 2.2.dp else 1.dp,
                      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                      shape = CircleShape,
                    )
                    .clickable { onToneChange(option) },
              )
            }
          }
        }

        // 2. Hero Clock + Rank + Daily Goal
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          EInkText(
            text = timeText,
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            EInkText(
              text = dateText,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.secondary,
            )
            EInkText(
              text = stringResource(R.string.stats_rank_format, rank.level, rank.title),
              style = MaterialTheme.typography.labelMedium,
              modifier =
                Modifier.border(1.dp, MaterialTheme.colorScheme.outline)
                  .padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              EInkText(
                text = stringResource(R.string.stats_daily_goal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
              )
              EInkText(
                text = stringResource(R.string.wallpaper_minutes_format, todayMin, goalMin),
                style = MaterialTheme.typography.labelSmall,
              )
            }
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(8.dp)
                  .border(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              if (goalProgress > 0f) {
                Box(
                  modifier =
                    Modifier.fillMaxWidth(goalProgress)
                      .fillMaxHeight()
                      .background(MaterialTheme.colorScheme.primary),
                )
              }
            }
          }
        }

        // 3. Currently Reading Card with Real Book Cover
        CurrentBookHeroCard(
          book = currentBook,
          coverFile = currentBook?.let(coverOf),
          grayscale = grayscale,
          onClick = {
            if (currentBook != null) onOpenBook(currentBook.id) else onOpenLibrary()
          },
        )

        // 4. Pinned Apps Strip / Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            EInkText(
              text = stringResource(R.string.launcher_pinned_apps),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
            EInkText(
              text = stringResource(R.string.launcher_long_press_unpin),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
          if (pinnedSlice.visibleApps.isEmpty()) {
            EInkText(
              text = stringResource(R.string.launcher_no_pinned),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.secondary,
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              pinnedSlice.visibleApps.chunked(4).forEach { rowApps ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  rowApps.forEach { app ->
                    PinnedAppTile(
                      app = app,
                      onClick = { onLaunchApp(app.packageName) },
                      onLongClick = { onUnpinApp(app) },
                      modifier = Modifier.weight(1f),
                    )
                  }
                  repeat(4 - rowApps.size) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
              if (pinnedSlice.overflowCount > 0) {
                OutlinedButton(
                  onClick = onOpenDrawer,
                  modifier = Modifier.fillMaxWidth(),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                  EInkText(
                    text = stringResource(R.string.launcher_pinned_more, pinnedSlice.overflowCount),
                    style = MaterialTheme.typography.labelMedium,
                  )
                }
              }
            }
          }
        }
      }
    }

    // 5. Bottom E-Ink Dock
    LauncherBottomDock(
      onOpenLibrary = onOpenLibrary,
      onOpenDrawer = onOpenDrawer,
    )
  }
}

@Composable
private fun LauncherBottomDock(
  onOpenLibrary: () -> Unit,
  onOpenDrawer: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) {
      EInkText(stringResource(R.string.launcher_library_btn))
    }
    OutlinedButton(onClick = onOpenDrawer, modifier = Modifier.weight(1f)) {
      EInkText(stringResource(R.string.launcher_apps_btn))
    }
  }
}

@Composable
private fun CurrentBookHeroCard(
  book: BookEntry?,
  coverFile: File?,
  grayscale: Boolean,
  onClick: () -> Unit,
) {
  val coverPath = coverFile?.absolutePath
  val coverBitmap by
    produceState<ImageBitmap?>(initialValue = CoverBitmapCache.get(coverPath), key1 = coverPath) {
      if (value == null && coverFile != null) {
        value = withContext(Dispatchers.IO) { CoverBitmapCache.decodeAndCache(coverFile) }
      }
    }
  val pct = ((book?.progress ?: 0f) * 100).toInt()

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .clickable(onClick = onClick)
        .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.width(62.dp)
          .aspectRatio(2f / 3f)
          .border(1.dp, MaterialTheme.colorScheme.outline)
          .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      val currentCover = coverBitmap
      if (currentCover != null) {
        val coverFilter = remember(grayscale) { CoverBitmapCache.colorFilterFor(grayscale) }
        Image(
          bitmap = currentCover,
          contentDescription = book?.title,
          contentScale = ContentScale.Crop,
          colorFilter = coverFilter,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        EInkText(
          text = book?.format?.label ?: "BOOK",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      EInkText(
        text = stringResource(R.string.wallpaper_currently_reading),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
      )
      EInkText(
        text = book?.title ?: stringResource(R.string.launcher_no_book),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (book != null) {
        if (book.author.isNotBlank()) {
          EInkText(
            text = book.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          EInkText("$pct%", style = MaterialTheme.typography.labelMedium)
          EInkText(stringResource(R.string.launcher_resume), style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedAppTile(
  app: LaunchableApp,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 6.dp, vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    MonochromeAppIcon(packageName = app.packageName, size = 34.dp)
    EInkText(
      text = app.label,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalistAppDrawerPage(
  apps: List<LaunchableApp>,
  pinnedPackages: Set<String>,
  viewMode: DrawerViewMode,
  onCycleViewMode: () -> Unit,
  onLaunchApp: (String) -> Unit,
  onTogglePin: (LaunchableApp) -> Unit,
  onBack: () -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val filtered =
    remember(apps, query) {
      if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
  val ink = MaterialTheme.colorScheme.onBackground

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        EInkText(stringResource(R.string.launcher_back_home))
      }
      Box(
        modifier =
          Modifier.weight(1f)
            .border(1.5.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        if (query.isEmpty()) {
          EInkText(
            stringResource(R.string.launcher_search_apps),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
          cursorBrush = SolidColor(ink),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      val viewModeLabel = stringResource(viewMode.labelRes)
      IconButton(
        onClick = onCycleViewMode,
        modifier =
          Modifier.size(40.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.outline)
            .semantics { contentDescription = viewModeLabel },
      ) {
        DrawerViewModeIcon(viewMode = viewMode)
      }
    }
    EInkText(
      text = stringResource(R.string.launcher_drawer_hint, stringResource(viewMode.labelRes)),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.secondary,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)

    when (viewMode) {
      DrawerViewMode.LIST, DrawerViewMode.TEXT_ONLY -> {
        val showIcons = viewMode == DrawerViewMode.LIST
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          items(filtered, key = { it.packageName }, contentType = { "app_row" }) { app ->
            val pinned = app.packageName in pinnedPackages
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .combinedClickable(
                    onClick = { onLaunchApp(app.packageName) },
                    onLongClick = { onTogglePin(app) },
                  )
                  .padding(horizontal = 16.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (showIcons) {
                MonochromeAppIcon(packageName = app.packageName, size = 32.dp)
              }
              EInkText(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (pinned) {
                EInkText(
                  text = stringResource(R.string.launcher_pinned_badge),
                  style = MaterialTheme.typography.labelSmall,
                  modifier =
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline)
                      .padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
              EInkText("›", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
      }
      DrawerViewMode.GRID_3, DrawerViewMode.GRID_4 -> {
        val columns = if (viewMode == DrawerViewMode.GRID_3) 3 else 4
        val iconSize = if (columns == 3) 44.dp else 38.dp
        LazyVerticalGrid(
          columns = GridCells.Fixed(columns),
          contentPadding = PaddingValues(12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          gridItems(filtered, key = { it.packageName }, contentType = { "app_grid" }) { app ->
            val pinned = app.packageName in pinnedPackages
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .border(
                    width = if (pinned) 1.5.dp else 1.dp,
                    color = if (pinned) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                  )
                  .combinedClickable(
                    onClick = { onLaunchApp(app.packageName) },
                    onLongClick = { onTogglePin(app) },
                  )
                  .padding(horizontal = 6.dp, vertical = 10.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              MonochromeAppIcon(packageName = app.packageName, size = iconSize)
              EInkText(
                text = app.label,
                style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MonochromeAppIcon(
  packageName: String,
  size: Dp,
) {
  val context = LocalContext.current
  var iconBitmap by remember(packageName) { mutableStateOf(LauncherAppCache.getIcon(packageName)) }
  if (iconBitmap == null) {
    LaunchedEffect(packageName) {
      iconBitmap = withContext(Dispatchers.IO) { LauncherAppCache.loadIcon(context, packageName) }
    }
  }

  Box(
    modifier =
      Modifier.size(size)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        .padding(3.dp),
    contentAlignment = Alignment.Center,
  ) {
    iconBitmap?.let { bmp ->
      Image(
        bitmap = bmp,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

@Composable
private fun DrawerViewModeIcon(viewMode: DrawerViewMode) {
  val ink = MaterialTheme.colorScheme.onBackground
  Canvas(modifier = Modifier.size(18.dp)) {
    val w = size.width
    val h = size.height
    when (viewMode) {
      DrawerViewMode.LIST -> {
        for (i in 0 until 3) {
          val y = h * (0.2f + i * 0.3f)
          drawRect(ink, topLeft = Offset(0f, y - 2f), size = Size(4.dp.toPx(), 4.dp.toPx()))
          drawLine(ink, Offset(6.dp.toPx(), y), Offset(w, y), strokeWidth = 1.8.dp.toPx())
        }
      }
      DrawerViewMode.TEXT_ONLY -> {
        for (i in 0 until 3) {
          val y = h * (0.2f + i * 0.3f)
          drawLine(ink, Offset(0f, y), Offset(w, y), strokeWidth = 2.dp.toPx())
        }
      }
      DrawerViewMode.GRID_3 -> drawGridCells(ink, 3)
      DrawerViewMode.GRID_4 -> drawGridCells(ink, 4)
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGridCells(
  ink: Color,
  count: Int,
) {
  val gap = 2.dp.toPx()
  val cellW = (size.width - gap * (count - 1)) / count
  val rows = 2
  val cellH = (size.height - gap * (rows - 1)) / rows
  for (r in 0 until rows) {
    for (c in 0 until count) {
      drawRect(
        color = ink,
        topLeft = Offset(c * (cellW + gap), r * (cellH + gap)),
        size = Size(cellW, cellH),
      )
    }
  }
}

private val LAUNCHER_TIME_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private val LAUNCHER_DATE_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale("vi", "VN"))

private object LauncherAppCache {
  private val iconCache = LruCache<String, ImageBitmap>(256)
  @Volatile private var cachedApps: List<LaunchableApp>? = null
  private val grayscalePaint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      colorFilter = ColorMatrixColorFilter(AndroidColorMatrix().apply { setSaturation(0f) })
    }

  fun cachedAppsOrEmpty(): List<LaunchableApp> = cachedApps.orEmpty()

  fun getIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

  fun evictIcon(packageName: String) {
    iconCache.remove(packageName)
  }

  fun loadApps(context: Context): List<LaunchableApp> {
    val queried = queryLaunchableApps(context)
    cachedApps = queried
    return queried
  }

  fun loadIcon(context: Context, packageName: String): ImageBitmap? {
    iconCache.get(packageName)?.let { return it }
    val image =
      runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val sizePx = 88
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val saveCount = canvas.saveLayer(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), grayscalePaint)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        canvas.restoreToCount(saveCount)
        bitmap.asImageBitmap()
      }.getOrNull()
    if (image != null) {
      iconCache.put(packageName, image)
    }
    return image
  }
}

private fun readBatteryPercent(context: Context): Int {
  val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return 100
  val pct = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
  return if (pct in 0..100) pct else 100
}

private fun queryLaunchableApps(context: Context): List<LaunchableApp> {
  val pm = context.packageManager
  val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
  return pm
    .queryIntentActivities(intent, 0)
    .asSequence()
    .filter { it.activityInfo.packageName != context.packageName }
    .map {
      LaunchableApp(
        label = it.loadLabel(pm).toString(),
        packageName = it.activityInfo.packageName,
      )
    }
    .distinctBy { it.packageName }
    .sortedBy { it.label.lowercase(Locale.getDefault()) }
    .toList()
}

private fun launchPackage(
  context: Context,
  packageName: String,
) {
  val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
  launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(launchIntent) }
}
