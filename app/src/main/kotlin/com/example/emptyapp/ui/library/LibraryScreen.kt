package com.example.emptyapp.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.data.billing.PlayBillingManager
import com.example.emptyapp.data.billing.ProEntitlement
import com.example.emptyapp.data.billing.ProFeature
import com.example.emptyapp.data.billing.ProFeatureGate
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.SampleBooksSeeder
import com.example.emptyapp.data.stats.ReadingGamification
import com.example.emptyapp.ui.eink.BookOpenIcon
import com.example.emptyapp.ui.eink.CloseIcon
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText
import com.example.emptyapp.ui.eink.OverflowDotsIcon
import com.example.emptyapp.ui.eink.PlusIcon
import com.example.emptyapp.ui.eink.SearchIcon
import com.example.emptyapp.ui.eink.SettingsGearIcon
import com.example.emptyapp.ui.eink.SortIcon
import com.example.emptyapp.ui.monet.LibraryProMilestoneNudge
import com.example.emptyapp.ui.monet.ProPaywallSheet
import java.io.File
import kotlinx.coroutines.launch

private val SUPPORTED_MIMES =
  arrayOf(
    "application/epub+zip",
    "application/pdf",
    "application/vnd.comicbook+zip",
    "application/x-cbz",
    "application/zip",
    "application/octet-stream",
  )

internal enum class LibraryTab(val labelRes: Int) {
  ALL(com.example.emptyapp.R.string.lib_tab_all),
  READING(com.example.emptyapp.R.string.lib_tab_reading),
  UNREAD(com.example.emptyapp.R.string.lib_tab_unread),
  COMPLETED(com.example.emptyapp.R.string.lib_tab_completed),
  BOOKMARKS(com.example.emptyapp.R.string.lib_tab_bookmarks),
  HIGHLIGHTS(com.example.emptyapp.R.string.lib_tab_highlights),
}

internal enum class LibrarySort(val labelRes: Int) {
  RECENT(com.example.emptyapp.R.string.lib_sort_recent),
  TITLE(com.example.emptyapp.R.string.lib_sort_title),
  PROGRESS(com.example.emptyapp.R.string.lib_sort_progress);

  fun next(): LibrarySort = entries[(ordinal + 1) % entries.size]
}

private enum class LibraryViewMode(val labelRes: Int, val columns: Int) {
  LIST(com.example.emptyapp.R.string.lib_view_list, 1),
  GRID_2(com.example.emptyapp.R.string.lib_view_grid_2, 2),
  GRID_3(com.example.emptyapp.R.string.lib_view_grid_3, 3),
  GRID_4(com.example.emptyapp.R.string.lib_view_grid_4, 4);

  fun next(): LibraryViewMode = entries[(ordinal + 1) % entries.size]
}

@Composable
fun LibraryScreen(
  viewModel: LibraryViewModel,
  settings: com.example.emptyapp.reader.EInkSettings = com.example.emptyapp.reader.EInkSettings(),
  onUpdateSettings: ((com.example.emptyapp.reader.EInkSettings) -> com.example.emptyapp.reader.EInkSettings) -> Unit = {},
  grayscaleCovers: Boolean = true,
  billing: PlayBillingManager? = null,
  proGate: ProFeatureGate? = null,
  sampleBooksSeeder: SampleBooksSeeder? = null,
  openTutorialRequested: Boolean = false,
  onConsumeTutorialRequest: () -> Unit = {},
  onOpenBook: (String, String?) -> Unit,
  onOpenStats: () -> Unit,
  onOpenSettings: () -> Unit = onOpenStats,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val tutorialPrefs = remember { context.getSharedPreferences("eink_tutorial_prefs", android.content.Context.MODE_PRIVATE) }
  var tutorialOpen by remember { mutableStateOf(false) }
  var tutorialInitialPage by remember { mutableStateOf(0) }
  var paywallOpen by remember { mutableStateOf(false) }
  var paywallFeature by remember { mutableStateOf<ProFeature?>(null) }
  val entitlement = proGate?.entitlement?.collectAsStateWithLifecycle()?.value ?: ProEntitlement()
  var setupBannerDismissed by remember { mutableStateOf(tutorialPrefs.getBoolean("setup_banner_dismissed", false)) }
  var milestoneNudgeDismissed by remember {
    mutableStateOf(tutorialPrefs.getBoolean("pro_milestone_nudge_dismissed", false))
  }
  fun markTutorialSeen() {
    tutorialPrefs.edit().putBoolean("tutorial_seen", true).apply()
  }
  fun dismissTutorial() {
    tutorialOpen = false
    markTutorialSeen()
  }

  val books by viewModel.books.collectAsStateWithLifecycle()
  val booksLoaded by viewModel.booksLoaded.collectAsStateWithLifecycle()
  val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
  val highlights by viewModel.highlights.collectAsStateWithLifecycle()
  val stats by viewModel.stats.collectAsStateWithLifecycle()
  val importing by viewModel.importing.collectAsStateWithLifecycle()
  val batchImportProgress by viewModel.batchImportProgress.collectAsStateWithLifecycle()
  val writeError by viewModel.writeError.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.ALL) }
  var selectedAuthor by rememberSaveable { mutableStateOf<String?>(null) }
  var sortMode by rememberSaveable { mutableStateOf(LibrarySort.RECENT) }
  var query by rememberSaveable { mutableStateOf("") }
  var pendingDeleteBook by remember { mutableStateOf<BookEntry?>(null) }
  var pendingDetailsBook by remember { mutableStateOf<BookEntry?>(null) }
  var viewMode by rememberSaveable { mutableStateOf(LibraryViewMode.LIST) }
  var pickerLaunching by remember { mutableStateOf(false) }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      pickerLaunching = false
      if (uris.isNotEmpty()) {
        viewModel.importMultiple(uris)
      }
    }
  val folderPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
      pickerLaunching = false
      treeUri?.let(viewModel::importFolder)
    }
  fun launchImportPicker(mimes: Array<String> = SUPPORTED_MIMES) {
    if (importing || pickerLaunching) return
    pickerLaunching = true
    runCatching { picker.launch(mimes) }.onFailure { pickerLaunching = false }
  }
  fun launchFolderPicker() {
    if (importing || pickerLaunching) return
    pickerLaunching = true
    runCatching { folderPicker.launch(null) }.onFailure { pickerLaunching = false }
  }
  val importEnabled = !importing && !pickerLaunching
  val saveErrorMsg = stringResource(com.example.emptyapp.R.string.error_save_data)
  LaunchedEffect(booksLoaded, sampleBooksSeeder) {
    if (booksLoaded) {
      sampleBooksSeeder?.seedIfNeeded()
    }
  }
  LaunchedEffect(openTutorialRequested) {
    if (openTutorialRequested) {
      tutorialInitialPage = 0
      tutorialOpen = true
      onConsumeTutorialRequest()
    }
  }
  LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }
  LaunchedEffect(writeError) {
    if (writeError) {
      snackbarHostState.showSnackbar(saveErrorMsg, duration = SnackbarDuration.Short)
    }
  }

  val appLocale = remember(context) { com.example.emptyapp.LocaleHelper.effectiveLocale(context) }
  val nameComparator = remember(appLocale) { com.example.emptyapp.LocaleHelper.nameComparator(appLocale) }

  val filteredState by
    produceState(
      initialValue = LibraryFilteredState(),
      books,
      selectedTab,
      selectedAuthor,
      query,
      sortMode,
      nameComparator,
    ) {
      value =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
          filterAndSortLibraryBooks(
            books = books,
            selectedTab = selectedTab,
            selectedAuthor = selectedAuthor,
            query = query,
            sortMode = sortMode,
            nameComparator = nameComparator,
          )
        }
    }
  val tabBooks = filteredState.tabBooks
  val filteredBooks = filteredState.filteredBooks
  val authors = filteredState.authors
  LaunchedEffect(booksLoaded, authors) {
    if (booksLoaded && selectedAuthor != null && selectedAuthor !in authors) {
      selectedAuthor = null
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0),
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    Column(
      modifier =
        Modifier.padding(padding)
          .fillMaxSize()
          .then(if (tutorialOpen) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
      LibraryHeader(
        isPro = entitlement.isPro,
        showProButton =
          shouldShowLibraryProBadge(
            isPro = entitlement.isPro,
            hasBilling = billing != null && proGate != null,
            totalPagesTurned = stats.totalPagesTurned,
            totalReadingSeconds = stats.totalSeconds,
          ),
        onOpenPaywall = {
          paywallFeature = null
          paywallOpen = true
        },
      )
      var wallpaperActive by remember { mutableStateOf(false) }
      val wallpaperScope = rememberCoroutineScope()
      LaunchedEffect(context) {
        wallpaperActive =
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.emptyapp.system.EInkSystemSetupHelper.isLiveWallpaperActive(context)
          }
      }
      val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
      androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        val observer =
          androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
              wallpaperScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                wallpaperActive =
                  com.example.emptyapp.system.EInkSystemSetupHelper.isLiveWallpaperActive(context)
              }
            }
          }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }
      val notifActive =
        com.example.emptyapp.reader.LockScreenReadingNotifier.hasPermission(context) &&
          settings.lockScreenReader
      val lockFeaturesCount = (if (wallpaperActive) 1 else 0) + (if (notifActive) 1 else 0)
      val hasStartedReading = remember(books, stats.totalPagesTurned) {
        stats.totalPagesTurned >= 3 || books.any { it.progress > 0f }
      }
      val showSetupBanner = shouldShowSetupBanner(setupBannerDismissed, lockFeaturesCount, hasStartedReading)
      if (showSetupBanner) {
        Row(
          modifier =
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              .fillMaxWidth()
              .clip(EInkShapes.Card)
              .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Card)
              .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
              .clickable {
                tutorialInitialPage = 1
                tutorialOpen = true
              }
              .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          EInkText(
            text = stringResource(com.example.emptyapp.R.string.onboard_banner_setup, lockFeaturesCount),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
          )
          val dismissBannerCd = stringResource(com.example.emptyapp.R.string.cd_dismiss_banner)
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .clickable {
                  setupBannerDismissed = true
                  tutorialPrefs.edit().putBoolean("setup_banner_dismissed", true).apply()
                }
                .semantics { contentDescription = dismissBannerCd },
            contentAlignment = Alignment.Center,
          ) {
            CloseIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
          }
        }
      }
      val completedBooksCount = remember(books) { books.count { it.progress >= 0.99f } }
      if (
        !showSetupBanner &&
          billing != null &&
          proGate != null &&
          ReadingGamification.shouldShowMilestoneNudge(
            isPro = entitlement.isPro,
            dismissed = milestoneNudgeDismissed,
            stats = stats,
            completedBooksCount = completedBooksCount,
          )
      ) {
        LibraryProMilestoneNudge(
          onExplorePro = {
            paywallFeature = null
            paywallOpen = true
          },
          onDismiss = {
            milestoneNudgeDismissed = true
            tutorialPrefs.edit().putBoolean("pro_milestone_nudge_dismissed", true).apply()
          },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      val clearFilters = {
        selectedTab = LibraryTab.ALL
        sortMode = LibrarySort.RECENT
        selectedAuthor = null
      }
      var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
      val isFilterActive =
        selectedTab != LibraryTab.ALL || sortMode != LibrarySort.RECENT || selectedAuthor != null
      val filterLabel = stringResource(selectedTab.labelRes)
      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (selectedTab == LibraryTab.BOOKMARKS) {
          val bookmarkDeletedMsg = stringResource(com.example.emptyapp.R.string.lib_bookmark_deleted)
          val undoActionLabel = stringResource(com.example.emptyapp.R.string.action_undo)
          val bookTitlesById = remember(books) { books.associate { it.id to it.title } }
          val filteredBookmarks =
            remember(bookmarks, bookTitlesById, query) {
              if (query.isBlank()) bookmarks
              else {
                bookmarks.filter {
                  it.title.contains(query, ignoreCase = true) ||
                    bookTitlesById[it.bookId].orEmpty().contains(query, ignoreCase = true)
                }
              }
            }
          LibraryBookmarkList(
            bookmarks = filteredBookmarks,
            bookTitlesById = bookTitlesById,
            onOpenBookmark = { bookId, locatorJson -> onOpenBook(bookId, locatorJson) },
            onRemove = { id ->
              val removed = bookmarks.firstOrNull { it.id == id }
              viewModel.removeBookmark(id)
              if (removed != null) {
                scope.launch {
                  val res =
                    snackbarHostState.showSnackbar(
                      message = bookmarkDeletedMsg,
                      actionLabel = undoActionLabel,
                      duration = SnackbarDuration.Short,
                    )
                  if (res == SnackbarResult.ActionPerformed) {
                    viewModel.restoreBookmark(removed)
                  }
                }
              }
            },
          )
        } else if (selectedTab == LibraryTab.HIGHLIGHTS) {
          val highlightDeletedMsg = stringResource(com.example.emptyapp.R.string.lib_highlight_deleted)
          val undoActionLabel = stringResource(com.example.emptyapp.R.string.action_undo)
          val filteredHighlights =
            remember(highlights, query) {
              if (query.isBlank()) highlights
              else {
                highlights.filter {
                  it.quote.contains(query, ignoreCase = true) ||
                    it.note.contains(query, ignoreCase = true) ||
                    it.bookTitle.contains(query, ignoreCase = true)
                }
              }
            }
          LibraryHighlightList(
            highlights = filteredHighlights,
            onOpenHighlight = { bookId, locatorJson -> onOpenBook(bookId, locatorJson) },
            onRemove = { id ->
              val removed = highlights.firstOrNull { it.id == id }
              viewModel.removeHighlight(id)
              if (removed != null) {
                scope.launch {
                  val res =
                    snackbarHostState.showSnackbar(
                      message = highlightDeletedMsg,
                      actionLabel = undoActionLabel,
                      duration = SnackbarDuration.Short,
                    )
                  if (res == SnackbarResult.ActionPerformed) {
                    viewModel.restoreHighlight(removed)
                  }
                }
              }
            },
          )
        } else if (!booksLoaded) {
          Box(modifier = Modifier.fillMaxSize())
        } else if (filteredBooks.isEmpty()) {
          EmptyLibrary(
            hasAnyBooks = books.isNotEmpty(),
            importEnabled = importEnabled,
            onImportBook = { launchImportPicker() },
            onImportFolder = { launchFolderPicker() },
          )
        } else {
          val heroBook =
            remember(books, selectedTab, query, selectedAuthor) {
              resolveHeroBook(
                books = books,
                isAllOrReadingTab = selectedTab == LibraryTab.ALL || selectedTab == LibraryTab.READING,
                query = query,
                selectedAuthor = selectedAuthor,
              )
            }
          val listBooks =
            remember(filteredBooks, heroBook) {
              if (heroBook != null) filteredBooks.filterNot { it.id == heroBook.id } else filteredBooks
            }
          if (viewMode == LibraryViewMode.LIST) {
            BookList(
              heroBook = heroBook,
              books = listBooks,
              grayscaleCovers = grayscaleCovers,
              coverOf = viewModel::coverFileOf,
              onOpenBook = { id -> onOpenBook(id, null) },
              onToggleCompleted = { book, completed -> viewModel.setBookCompleted(book.id, completed) },
              onShowDetails = { book -> pendingDetailsBook = book },
              onRemove = { book -> pendingDeleteBook = book },
            )
          } else {
            BookGrid(
              heroBook = heroBook,
              books = listBooks,
              columns = viewMode.columns,
              grayscaleCovers = grayscaleCovers,
              coverOf = viewModel::coverFileOf,
              onOpenBook = { id -> onOpenBook(id, null) },
              onToggleCompleted = { book, completed -> viewModel.setBookCompleted(book.id, completed) },
              onShowDetails = { book -> pendingDetailsBook = book },
              onRemove = { book -> pendingDeleteBook = book },
            )
          }
        }
        LibraryFilterFloatingButton(
          filterLabel = filterLabel,
          isFilterActive = isFilterActive,
          onClick = { filterSheetOpen = true },
          onClear = clearFilters,
          modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 14.dp),
        )
      }
      val searchPlaceholder =
        when (selectedTab) {
          LibraryTab.BOOKMARKS -> stringResource(com.example.emptyapp.R.string.lib_search_bookmarks_placeholder)
          LibraryTab.HIGHLIGHTS -> stringResource(com.example.emptyapp.R.string.lib_search_highlights_placeholder)
          else -> stringResource(com.example.emptyapp.R.string.lib_search_placeholder)
        }
      LibraryBottomBar(
        viewMode = viewMode,
        showViewToggle = selectedTab != LibraryTab.HIGHLIGHTS && selectedTab != LibraryTab.BOOKMARKS,
        query = query,
        searchPlaceholder = searchPlaceholder,
        importing = importing,
        batchImportProgress = batchImportProgress,
        importEnabled = importEnabled,
        onCycleViewMode = { viewMode = viewMode.next() },
        onQueryChange = { query = it },
        onAddBook = { launchImportPicker() },
        onAddFolder = { launchFolderPicker() },
      )
      if (filterSheetOpen) {
        LibraryFilterSheet(
          selectedTab = selectedTab,
          sortMode = sortMode,
          totalCount = tabBooks.size,
          authors = authors,
          selectedAuthor = selectedAuthor,
          onSelectTab = { selectedTab = it },
          onSelectSort = { sortMode = it },
          onSelectAuthor = { selectedAuthor = it },
          onReset = clearFilters,
          onDismiss = { filterSheetOpen = false },
        )
      }
    }
    pendingDetailsBook?.let { bookToInspect ->
      val fileSizeBytes by
        produceState(initialValue = 0L, key1 = bookToInspect.id) {
          value =
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
              runCatching { viewModel.bookFileOf(bookToInspect).length() }.getOrDefault(0L)
            }
        }
      BookDetailsDialog(
        book = bookToInspect,
        fileSizeBytes = fileSizeBytes,
        onDismiss = { pendingDetailsBook = null },
      )
    }
    pendingDeleteBook?.let { bookToDelete ->
      var deleteFromCloud by remember(bookToDelete.id) { mutableStateOf(false) }
      AlertDialog(
        onDismissRequest = { pendingDeleteBook = null },
        containerColor = MaterialTheme.colorScheme.background,
        title = { EInkText(stringResource(com.example.emptyapp.R.string.lib_delete_book_title), style = MaterialTheme.typography.titleMedium) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EInkText(
              stringResource(com.example.emptyapp.R.string.lib_delete_book_message, bookToDelete.title),
              style = MaterialTheme.typography.bodyMedium,
            )
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { deleteFromCloud = !deleteFromCloud }
                  .padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = deleteFromCloud,
                onCheckedChange = { deleteFromCloud = it },
              )
              EInkText(
                text = stringResource(com.example.emptyapp.R.string.lib_delete_also_cloud),
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        },
        confirmButton = {
          TextButton(
            onClick = {
              viewModel.remove(bookToDelete.id, alsoDeleteFromCloud = deleteFromCloud)
              pendingDeleteBook = null
            },
          ) {
            EInkText(stringResource(com.example.emptyapp.R.string.action_delete), style = MaterialTheme.typography.labelLarge)
          }
        },
        dismissButton = {
          TextButton(onClick = { pendingDeleteBook = null }) {
            EInkText(
              stringResource(com.example.emptyapp.R.string.action_cancel),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        },
      )
    }
    if (tutorialOpen) {
      TutorialOverlay(
        settings = settings,
        onUpdateSettings = onUpdateSettings,
        onImportBook = {
          launchImportPicker(arrayOf("application/epub+zip", "application/pdf", "application/vnd.comicbook+zip", "application/x-cbz"))
        },
        onMarkSeen = ::markTutorialSeen,
        onDismiss = ::dismissTutorial,
        initialPage = tutorialInitialPage,
        proGate = proGate,
        onRequirePro = { feature ->
          paywallFeature = feature
          paywallOpen = true
        },
      )
    }
    if (paywallOpen && billing != null && proGate != null) {
      ProPaywallSheet(
        billing = billing,
        proGate = proGate,
        feature = paywallFeature,
        onDismiss = { paywallOpen = false },
      )
    }
  }
}

@Composable
private fun LibraryHeader(
  isPro: Boolean,
  showProButton: Boolean,
  onOpenPaywall: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    EInkText(stringResource(com.example.emptyapp.R.string.app_name), style = MaterialTheme.typography.headlineLarge)
    if (showProButton) {
      val proCd = stringResource(com.example.emptyapp.R.string.pro_card_title)
      Box(
        modifier =
          Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onOpenPaywall)
            .semantics { contentDescription = proCd },
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier =
            Modifier.clip(EInkShapes.Badge)
              .background(
                if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                EInkShapes.Badge,
              )
              .border(EInkBorder.Card, MaterialTheme.colorScheme.outline, EInkShapes.Badge)
              .padding(horizontal = 8.dp, vertical = 5.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = stringResource(com.example.emptyapp.R.string.paywall_badge_pro),
            style = MaterialTheme.typography.labelSmall,
            color = if (isPro) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
          )
        }
      }
    }
  }
}

@Composable
private fun LibraryFilterFloatingButton(
  filterLabel: String,
  isFilterActive: Boolean,
  onClick: () -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val filterCd = stringResource(com.example.emptyapp.R.string.cd_library_filter)
  val fg = if (isFilterActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
  val bg = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
  val borderWidth = if (isFilterActive) EInkBorder.Active else EInkBorder.Card
  val borderColor = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (isFilterActive) {
      val clearFilterCd = stringResource(com.example.emptyapp.R.string.cd_clear_filter)
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .border(EInkBorder.Card, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClear)
            .semantics { contentDescription = clearFilterCd },
        contentAlignment = Alignment.Center,
      ) {
        CloseIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onBackground)
      }
    }
    Row(
      modifier =
        Modifier.defaultMinSize(minHeight = 48.dp)
          .clip(EInkShapes.Pill)
          .background(bg, EInkShapes.Pill)
          .border(width = borderWidth, color = borderColor, shape = EInkShapes.Pill)
          .clickable(onClick = onClick)
          .semantics { contentDescription = filterCd }
          .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SortIcon(
        modifier = Modifier.size(14.dp),
        tint = fg,
      )
      EInkText(
        text = filterLabel,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun LibraryBottomBar(
  viewMode: LibraryViewMode,
  showViewToggle: Boolean,
  query: String,
  searchPlaceholder: String,
  importing: Boolean,
  batchImportProgress: BatchImportProgress?,
  importEnabled: Boolean,
  onCycleViewMode: () -> Unit,
  onQueryChange: (String) -> Unit,
  onAddBook: () -> Unit,
  onAddFolder: () -> Unit,
) {
  var importMenuOpen by remember { mutableStateOf(false) }
  Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
    if (importing) {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.onBackground,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
      )
      val importText =
        if (batchImportProgress != null) {
          stringResource(
            com.example.emptyapp.R.string.lib_importing_batch_progress,
            batchImportProgress.current,
            batchImportProgress.total,
          )
        } else {
          stringResource(com.example.emptyapp.R.string.lib_importing_banner)
        }
      EInkText(
        text = importText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
    }
    HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showViewToggle) {
        val viewModeCd = stringResource(viewMode.labelRes)
        IconButton(
          onClick = onCycleViewMode,
          modifier =
            Modifier.size(48.dp).semantics { contentDescription = viewModeCd },
        ) {
          ViewModeIcon(viewMode = viewMode)
        }
      }
      GoogleStyleSearchBar(
        query = query,
        placeholder = searchPlaceholder,
        onQueryChange = onQueryChange,
        modifier = Modifier.weight(1f),
      )
      val addBookCd = stringResource(com.example.emptyapp.R.string.cd_add_book)
      Box {
        Box(
          modifier =
            Modifier.size(48.dp)
              .clickable(enabled = importEnabled) { importMenuOpen = true }
              .semantics { contentDescription = addBookCd },
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier =
              Modifier.size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            if (importing) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
              )
            } else {
              PlusIcon(modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
          }
        }
        ImportSourceMenu(
          expanded = importMenuOpen,
          onDismiss = { importMenuOpen = false },
          onPickFiles = onAddBook,
          onPickFolder = onAddFolder,
        )
      }
    }
  }
}

@Composable
private fun GoogleStyleSearchBar(
  query: String,
  placeholder: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val ink = MaterialTheme.colorScheme.onBackground
  Row(
    modifier =
      modifier
        .height(44.dp)
        .clip(EInkShapes.Pill)
        .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Pill)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Pill)
        .padding(start = 14.dp, end = if (query.isNotEmpty()) 4.dp else 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SearchIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (query.isEmpty()) {
        EInkText(
          text = placeholder,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.secondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
        cursorBrush = SolidColor(ink),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (query.isNotEmpty()) {
      val clearCd = stringResource(com.example.emptyapp.R.string.cd_clear_search)
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .clickable { onQueryChange("") }
            .semantics { contentDescription = clearCd },
        contentAlignment = Alignment.Center,
      ) {
        CloseIcon(modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
      }
    }
  }
}

@Composable
private fun ViewModeIcon(viewMode: LibraryViewMode) {
  val ink = MaterialTheme.colorScheme.onBackground
  Canvas(modifier = Modifier.size(18.dp)) {
    if (viewMode == LibraryViewMode.LIST) {
      listOf(0.22f, 0.5f, 0.78f).forEach { frac ->
        val y = size.height * frac
        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
      }
    } else {
      val cols = viewMode.columns.coerceIn(2, 4)
      val stroke = Stroke(width = 1.5.dp.toPx())
      val gap = size.width * 0.12f
      val cell = (size.width - gap * (cols - 1)) / cols
      for (r in 0 until cols) {
        for (c in 0 until cols) {
          drawRect(
            color = ink,
            topLeft = Offset(c * (cell + gap), r * (cell + gap)),
            size = Size(cell, cell),
            style = stroke,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilterSheet(
  selectedTab: LibraryTab,
  sortMode: LibrarySort,
  totalCount: Int,
  authors: List<String>,
  selectedAuthor: String?,
  onSelectTab: (LibraryTab) -> Unit,
  onSelectSort: (LibrarySort) -> Unit,
  onSelectAuthor: (String?) -> Unit,
  onReset: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val hasActiveFilter =
    selectedTab != LibraryTab.ALL || sortMode != LibrarySort.RECENT || selectedAuthor != null
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    shape = EInkShapes.SheetTop,
    containerColor = MaterialTheme.colorScheme.background,
    dragHandle = null,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        EInkText(
          text = stringResource(com.example.emptyapp.R.string.lib_filter_title),
          style = MaterialTheme.typography.titleLarge,
        )
        val closeCd = stringResource(com.example.emptyapp.R.string.action_close)
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(48.dp).semantics { contentDescription = closeCd },
        ) {
          CloseIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        }
      }
      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
      Column(
        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          EInkText(
            text = stringResource(com.example.emptyapp.R.string.lib_filter_view),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            LibraryTab.entries.forEach { tab ->
              FilterPill(
                label = stringResource(tab.labelRes),
                active = tab == selectedTab,
                onClick = { onSelectTab(tab) },
              )
            }
          }
        }
        if (selectedTab != LibraryTab.HIGHLIGHTS && selectedTab != LibraryTab.BOOKMARKS) {
          HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EInkText(
              text = stringResource(com.example.emptyapp.R.string.lib_sort_label),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              LibrarySort.entries.forEach { option ->
                FilterPill(
                  label = stringResource(option.labelRes),
                  active = option == sortMode,
                  onClick = { onSelectSort(option) },
                )
              }
            }
          }
          if (authors.size > 1 || selectedAuthor != null) {
            HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              EInkText(
                text = stringResource(com.example.emptyapp.R.string.lib_filter_author),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilterPill(
                  label = stringResource(com.example.emptyapp.R.string.lib_author_all, totalCount),
                  active = selectedAuthor == null,
                  onClick = { onSelectAuthor(null) },
                )
                authors.forEach { author ->
                  FilterPill(
                    label = author,
                    active = selectedAuthor == author,
                    onClick = { onSelectAuthor(if (selectedAuthor == author) null else author) },
                  )
                }
              }
            }
          }
        }
      }
      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onReset,
          enabled = hasActiveFilter,
          shape = EInkShapes.Control,
          modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
        ) {
          EInkText(
            text = stringResource(com.example.emptyapp.R.string.lib_filter_reset),
            style = MaterialTheme.typography.labelLarge,
            color = if (hasActiveFilter) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
          )
        }
        Button(
          onClick = onDismiss,
          shape = EInkShapes.Control,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
        ) {
          EInkText(
            text = stringResource(com.example.emptyapp.R.string.lib_filter_done),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
    }
  }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier.clip(EInkShapes.Pill)
        .background(
          if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
          EInkShapes.Pill,
        )
        .border(
          width = if (active) EInkBorder.Card else EInkBorder.Hairline,
          color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          shape = EInkShapes.Pill,
        )
        .selectable(selected = active, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 6.dp)
  ) {
    EInkText(
      text = label,
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
      color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
    )
  }
}

@Composable
private fun EmptyLibrary(
  hasAnyBooks: Boolean,
  importEnabled: Boolean,
  onImportBook: () -> Unit,
  onImportFolder: () -> Unit,
) {
  var importMenuOpen by remember { mutableStateOf(false) }
  Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier =
          Modifier.size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        BookOpenIcon(modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
      }
      EInkText(
        text =
          if (hasAnyBooks) stringResource(com.example.emptyapp.R.string.lib_empty_filtered)
          else stringResource(com.example.emptyapp.R.string.lib_empty_all),
        style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.secondary,
      )
      if (!hasAnyBooks) {
        Box {
          Button(
            onClick = { importMenuOpen = true },
            enabled = importEnabled,
            shape = EInkShapes.Control,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
          ) {
            EInkText(
              stringResource(com.example.emptyapp.R.string.lib_import_first_book),
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
          ImportSourceMenu(
            expanded = importMenuOpen,
            onDismiss = { importMenuOpen = false },
            onPickFiles = onImportBook,
            onPickFolder = onImportFolder,
          )
        }
      }
    }
  }
}

@Composable
private fun BookList(
  heroBook: BookEntry?,
  books: List<BookEntry>,
  grayscaleCovers: Boolean,
  coverOf: (BookEntry) -> File?,
  onOpenBook: (String) -> Unit,
  onToggleCompleted: (BookEntry, Boolean) -> Unit,
  onShowDetails: (BookEntry) -> Unit,
  onRemove: (BookEntry) -> Unit,
) {
  LazyColumn(contentPadding = PaddingValues(bottom = 76.dp)) {
    if (heroBook != null) {
      item(key = "continue_reading_hero") {
        ContinueReadingHeroCard(
          book = heroBook,
          grayscaleCover = grayscaleCovers,
          coverFile = coverOf(heroBook),
          onOpen = { onOpenBook(heroBook.id) },
          onToggleCompleted = { completed -> onToggleCompleted(heroBook, completed) },
          onShowDetails = { onShowDetails(heroBook) },
          onRemove = { onRemove(heroBook) },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
      }
    }
    items(books, key = { it.id }) { book ->
      BookRow(
        book = book,
        grayscaleCover = grayscaleCovers,
        coverFile = coverOf(book),
        onOpen = { onOpenBook(book.id) },
        onToggleCompleted = { completed -> onToggleCompleted(book, completed) },
        onShowDetails = { onShowDetails(book) },
        onRemove = { onRemove(book) },
      )
      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

@Composable
private fun BookGrid(
  heroBook: BookEntry?,
  books: List<BookEntry>,
  columns: Int,
  grayscaleCovers: Boolean,
  coverOf: (BookEntry) -> File?,
  onOpenBook: (String) -> Unit,
  onToggleCompleted: (BookEntry, Boolean) -> Unit,
  onShowDetails: (BookEntry) -> Unit,
  onRemove: (BookEntry) -> Unit,
) {
  val spacing = if (columns >= 4) 10.dp else 14.dp
  val basePadding = if (columns >= 4) 12.dp else 16.dp
  LazyVerticalGrid(
    columns = GridCells.Fixed(columns),
    contentPadding = PaddingValues(start = basePadding, top = basePadding, end = basePadding, bottom = basePadding + 64.dp),
    horizontalArrangement = Arrangement.spacedBy(spacing),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    if (heroBook != null) {
      item(key = "continue_reading_hero", span = { GridItemSpan(maxLineSpan) }) {
        ContinueReadingHeroCard(
          book = heroBook,
          grayscaleCover = grayscaleCovers,
          coverFile = coverOf(heroBook),
          onOpen = { onOpenBook(heroBook.id) },
          onToggleCompleted = { completed -> onToggleCompleted(heroBook, completed) },
          onShowDetails = { onShowDetails(heroBook) },
          onRemove = { onRemove(heroBook) },
        )
      }
    }
    gridItems(books, key = { it.id }) { book ->
      BookGridCard(
        book = book,
        columns = columns,
        grayscaleCover = grayscaleCovers,
        coverFile = coverOf(book),
        onOpen = { onOpenBook(book.id) },
        onToggleCompleted = { completed -> onToggleCompleted(book, completed) },
        onShowDetails = { onShowDetails(book) },
        onRemove = { onRemove(book) },
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridCard(
  book: BookEntry,
  columns: Int,
  grayscaleCover: Boolean,
  coverFile: File?,
  onOpen: () -> Unit,
  onToggleCompleted: (Boolean) -> Unit,
  onShowDetails: () -> Unit,
  onRemove: () -> Unit,
) {
  val compact = columns >= 3
  var menuExpanded by remember { mutableStateOf(false) }
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true }),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    BookCover(
      book = book,
      coverFile = coverFile,
      compact = compact,
      grayscale = grayscaleCover,
      modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
    )
    if (book.progress > 0f) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(3.dp)
            .clip(EInkShapes.Pill)
            .background(MaterialTheme.colorScheme.outlineVariant),
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth(book.progress.coerceIn(0f, 1f))
              .fillMaxHeight()
              .background(MaterialTheme.colorScheme.primary),
        )
      }
    }
    EInkText(
      text = book.title,
      style = if (columns >= 4) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
      minLines = 2,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EInkText(
        text =
          if (columns >= 4) "${(book.progress * 100).toInt()}%"
          else "${book.format.label} · ${(book.progress * 100).toInt()}%",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Box {
        val actionsCd = stringResource(com.example.emptyapp.R.string.cd_book_actions)
        Box(
          modifier =
            Modifier.size(48.dp)
              .clickable { menuExpanded = true }
              .semantics { contentDescription = actionsCd },
          contentAlignment = Alignment.Center,
        ) {
          OverflowDotsIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
        }
        BookActionMenu(
          book = book,
          expanded = menuExpanded,
          onDismiss = { menuExpanded = false },
          onOpen = onOpen,
          onToggleCompleted = onToggleCompleted,
          onShowDetails = onShowDetails,
          onDelete = onRemove,
        )
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
  book: BookEntry,
  grayscaleCover: Boolean,
  coverFile: File?,
  onOpen: () -> Unit,
  onToggleCompleted: (Boolean) -> Unit,
  onShowDetails: () -> Unit,
  onRemove: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true })
        .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    BookCover(
      book = book,
      coverFile = coverFile,
      compact = true,
      grayscale = grayscaleCover,
      modifier = Modifier.size(width = 48.dp, height = 68.dp),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      EInkText(
        text = book.title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      val meta =
        listOfNotNull(
          book.format.label,
          book.author.takeIf { it.isNotBlank() },
          "${(book.progress * 100).toInt()}%",
        ).joinToString(" · ")
      EInkText(
        text = meta,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
      )
      if (book.progress > 0f) {
        Box(
          modifier =
            Modifier.fillMaxWidth(0.55f)
              .height(3.dp)
              .clip(EInkShapes.Pill)
              .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth(book.progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
          )
        }
      }
    }
    Box {
      val actionsCd = stringResource(com.example.emptyapp.R.string.cd_book_actions)
      IconButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.size(48.dp).semantics { contentDescription = actionsCd },
      ) {
        OverflowDotsIcon(modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
      }
      BookActionMenu(
        book = book,
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        onOpen = onOpen,
        onToggleCompleted = onToggleCompleted,
        onShowDetails = onShowDetails,
        onDelete = onRemove,
      )
    }
  }
}
