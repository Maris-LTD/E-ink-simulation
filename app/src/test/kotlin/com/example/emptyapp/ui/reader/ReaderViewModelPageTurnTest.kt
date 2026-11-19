package com.example.emptyapp.ui.reader

import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.dictionary.DictionaryService
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.EInkSettingsStore
import com.example.emptyapp.reader.pdf.PdfBookConfigStore
import com.example.emptyapp.reader.pdf.PdfGuidedNavigation
import com.example.emptyapp.reader.pdf.PdfReadingMode
import com.example.emptyapp.reader.pdf.PdfReflowPaginator
import com.example.emptyapp.reader.pdf.ReflowItem
import com.example.emptyapp.reader.pdf.ReflowPageMeta
import com.example.emptyapp.reader.pdf.ReflowPageResult
import com.example.emptyapp.shouldHandleVolumePageTurn
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelPageTurnTest {

  @Before
  fun setUp() {
    Dispatchers.setMain(Dispatchers.Unconfined)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun ReaderViewModel.simulatePdfPageChanged(pageIndex: Int, totalPages: Int = 10) {
    val total = if (totalPages > 1) pageIndex.toFloat() / (totalPages - 1) else 0f
    onPositionKeyChanged(key = "book.pdf#null#${pageIndex + 1}", totalProgression = total)
  }

  private fun createViewModel(
    dir: File,
    initialSettings: EInkSettings,
    nowProvider: () -> Long,
  ): Triple<ReaderViewModel, ReadingStatsRepository, EInkSettingsStore> {
    val repo =
      LibraryRepository(
        appFilesDir = dir,
        engineProvider = { error("Not used") },
        importFromUri = { _, _ -> Result.failure(UnsupportedOperationException()) },
        verifyAndExtractSyncedCover = { _, _, _ -> false },
        extractBackfillCover = { _, _, _ -> false },
        readAttemptedCoverIds = { emptySet() },
        persistAttemptedCoverIds = {},
        autoBackfill = false,
      )
    val settingsStore = EInkSettingsStore(initialSettings)
    val pdfConfigs = PdfBookConfigStore(File(dir, "pdf_configs.json"))
    val annotations = AnnotationRepository(File(dir, "bookmarks.json"), File(dir, "highlights.json"))
    val stats = ReadingStatsRepository(File(dir, "reading_stats.json"))
    val vm =
      ReaderViewModel(
        bookId = "book-1",
        repository = repo,
        settingsStore = settingsStore,
        pdfBookConfigs = pdfConfigs,
        annotations = annotations,
        dictionary = DictionaryService(),
        statsRepository = stats,
        elapsedRealtimeMs = nowProvider,
      )
    return Triple(vm, stats, settingsStore)
  }

  @Test
  fun subPageScreenTurnsIncrementPendingPagesAndTriggerFlashOnInterval() = runBlocking {
    val dir = createTempDirectory("reader_vm_subpage_test").toFile()
    var nowMs = 5_000L
    var vmRef: ReaderViewModel? = null
    try {
      val (vm, stats, _) =
        createViewModel(
          dir = dir,
          initialSettings = EInkSettings(flashEnabled = true, flashEveryPages = 3),
          nowProvider = { nowMs },
        )
      vmRef = vm

      vm.onReaderForegroundChanged(true)
      vm.simulatePdfPageChanged(pageIndex = 0)
      assertEquals(0, vm.pagesSinceFlash.value)
      assertEquals(0, vm.flashTrigger.value)
      assertEquals(0, vm.pageTurnTrigger.value)

      // 2 sub-page turns within PDF page 0
      vm.onSubPageScreenTurned()
      assertEquals(1, vm.pagesSinceFlash.value)
      assertEquals(1, vm.pageTurnTrigger.value)
      assertEquals(0, vm.flashTrigger.value)

      vm.onSubPageScreenTurned()
      assertEquals(2, vm.pagesSinceFlash.value)
      assertEquals(2, vm.pageTurnTrigger.value)
      assertEquals(0, vm.flashTrigger.value)

      // 3rd turn crosses to PDF page 1 -> hits flash interval 3, resets counter
      vm.simulatePdfPageChanged(pageIndex = 1)
      assertEquals(0, vm.pagesSinceFlash.value)
      assertEquals(2, vm.pageTurnTrigger.value)
      assertEquals(1, vm.flashTrigger.value)

      vm.onReaderForegroundChanged(false)
      stats.recordReading(0, 0)
      assertEquals(3, stats.stats.value.totalPagesTurned)
    } finally {
      vmRef?.onReaderForegroundChanged(false)
      dir.deleteRecursively()
    }
  }

  @Test
  fun reflowAndGuidedTransitionsCountOncePerStepWithoutDoubleCountingAcrossPageBoundary() = runBlocking {
    val dir = createTempDirectory("reader_vm_boundary_test").toFile()
    var nowMs = 5_000L
    var vmRef: ReaderViewModel? = null
    try {
      val (vm, stats, _) =
        createViewModel(
          dir = dir,
          initialSettings = EInkSettings(flashEnabled = true, flashEveryPages = 4),
          nowProvider = { nowMs },
        )
      vmRef = vm

      val pages =
        listOf(
          ReflowPageMeta(
            pageIndex = 0,
            hasSpecialObjects = false,
            result = ReflowPageResult.Reflowed(listOf(ReflowItem.Paragraph("aa bb cc dd", false, 0.1f))),
          ),
          ReflowPageMeta(
            pageIndex = 1,
            hasSpecialObjects = false,
            result = ReflowPageResult.Reflowed(listOf(ReflowItem.Paragraph("ee ff", false, 0.1f))),
          ),
        )
      val screens =
        PdfReflowPaginator.paginate(
          pages = pages,
          maxContentWidthPx = 200,
          maxContentHeightPx = 25,
          paragraphSpacingPx = 4,
        ) { text, _, _ ->
          val lines = ((text.length + 5) / 6).coerceAtLeast(1)
          lines * 20
        }
      assertEquals(3, screens.size)

      vm.onReaderForegroundChanged(true)
      var currentPdfPage = PdfReflowPaginator.resolveSnapBackPage(screens[0])
      vm.simulatePdfPageChanged(currentPdfPage)

      // Step through remaining screens: screen 0 -> 1 (same PDF page 0), screen 1 -> 2 (PDF page 0 -> 1)
      var prevScreenIdx = 0
      for (nextScreenIdx in 1..screens.lastIndex) {
        val settle =
          PdfReflowPaginator.evaluateScreenSettle(
            screen = screens[nextScreenIdx],
            previousScreenIndex = prevScreenIdx,
            previousPdfPage = currentPdfPage,
            isExplicitJump = false,
          )
        prevScreenIdx = nextScreenIdx
        currentPdfPage = settle.snapPdfPage
        vm.simulatePdfPageChanged(settle.snapPdfPage)
        if (settle.isSubPageTurn) {
          vm.onSubPageScreenTurned()
        }
      }

      // Also step through 2 guided regions on page 1 (region 0 -> 1 -> boundary to page 2)
      val stepInPage =
        PdfGuidedNavigation.stepNext(
          readingMode = PdfReadingMode.GUIDED_COLUMN,
          hasAnalysis = true,
          currentRegionIndex = 0,
          regionCount = 2,
        )
      if (stepInPage.changedRegionOnSamePage) {
        vm.onSubPageScreenTurned()
      }
      val stepOutOfPage =
        PdfGuidedNavigation.stepNext(
          readingMode = PdfReadingMode.GUIDED_COLUMN,
          hasAnalysis = true,
          currentRegionIndex = stepInPage.nextRegionIndex,
          regionCount = 2,
        )
      if (stepOutOfPage.changedRegionOnSamePage) {
        vm.onSubPageScreenTurned()
      } else if (!stepOutOfPage.consumed) {
        vm.simulatePdfPageChanged(2)
      }

      vm.onReaderForegroundChanged(false)
      stats.recordReading(0, 0)
      // Total steps: 2 reflow screens + 1 guided region step + 1 guided page turn = 4 -> triggers 1 flash
      assertEquals(4, stats.stats.value.totalPagesTurned)
      assertEquals(1, vm.flashTrigger.value)
      assertEquals(0, vm.pagesSinceFlash.value)
    } finally {
      vmRef?.onReaderForegroundChanged(false)
      dir.deleteRecursively()
    }
  }

  @Test
  fun relayoutSuppressionConsumesExactlyOneTransitionEvenOverOneSecondAndCountsImmediateNextTurn() = runBlocking {
    val dir = createTempDirectory("reader_vm_suppress_test").toFile()
    var nowMs = 5_000L
    var vmRef: ReaderViewModel? = null
    try {
      val (vm, stats, _) =
        createViewModel(
          dir = dir,
          initialSettings = EInkSettings(flashEnabled = true, flashEveryPages = 3),
          nowProvider = { nowMs },
        )
      vmRef = vm

      vm.onReaderForegroundChanged(true)
      vm.simulatePdfPageChanged(0)

      // Trigger relayout suppression via fontScale update (even when relayout takes 2_000 ms > 1 s)
      vm.updateSettings { it.copy(fontScale = 1.6) }
      nowMs += 2_000L
      vm.simulatePdfPageChanged(1)
      assertEquals(0, vm.pagesSinceFlash.value)
      assertEquals(0, vm.pageTurnTrigger.value)

      // Real user page turn right after (100 ms later) is counted immediately because the one-shot flag was consumed
      nowMs += 100L
      vm.simulatePdfPageChanged(2)
      assertEquals(1, vm.pagesSinceFlash.value)
      assertEquals(1, vm.pageTurnTrigger.value)

      // Same one-shot behavior for expectRelayout() (e.g. onConfigurationChanged) + sub-page turn
      vm.expectRelayout()
      nowMs += 1_800L
      vm.onSubPageScreenTurned()
      assertEquals(1, vm.pagesSinceFlash.value)
      assertEquals(1, vm.pageTurnTrigger.value)

      nowMs += 150L
      vm.onSubPageScreenTurned()
      assertEquals(2, vm.pagesSinceFlash.value)
      assertEquals(2, vm.pageTurnTrigger.value)

      // Same one-shot behavior for expectJump() (TOC / search / slider / bookmark jump)
      vm.expectJump()
      nowMs += 400L
      vm.simulatePdfPageChanged(7)
      assertEquals(2, vm.pagesSinceFlash.value)
      assertEquals(2, vm.pageTurnTrigger.value)

      // Next real turn after jump hits flashEveryPages = 3
      nowMs += 200L
      vm.simulatePdfPageChanged(8)
      assertEquals(0, vm.pagesSinceFlash.value)
      assertEquals(1, vm.flashTrigger.value)

      vm.onReaderForegroundChanged(false)
      stats.recordReading(0, 0)
      assertEquals(3, stats.stats.value.totalPagesTurned)
    } finally {
      vmRef?.onReaderForegroundChanged(false)
      dir.deleteRecursively()
    }
  }

  @Test
  fun expectRelayoutOrJumpExpiresAfterFallbackTimeoutWhenNoTransitionArrives() = runBlocking {
    val dir = createTempDirectory("reader_vm_fallback_timeout_test").toFile()
    var nowMs = 10_000L
    var vmRef: ReaderViewModel? = null
    try {
      val (vm, stats, _) =
        createViewModel(
          dir = dir,
          initialSettings = EInkSettings(flashEnabled = true, flashEveryPages = 3),
          nowProvider = { nowMs },
        )
      vmRef = vm

      vm.onReaderForegroundChanged(true)
      vm.simulatePdfPageChanged(3)

      // Jump to the same page -> no locator change arrives
      vm.expectJump()
      vm.simulatePdfPageChanged(3)
      assertEquals(0, vm.pagesSinceFlash.value)

      // After fallback timeout (> 3_000 ms), the next real page turn is counted normally
      nowMs += ReaderViewModel.RELAYOUT_JUMP_FALLBACK_TIMEOUT_MS + 100L
      vm.simulatePdfPageChanged(4)
      assertEquals(1, vm.pagesSinceFlash.value)
      assertEquals(1, vm.pageTurnTrigger.value)

      // Same expiry check for expectRelayout() with no position change
      vm.expectRelayout()
      nowMs += ReaderViewModel.RELAYOUT_JUMP_FALLBACK_TIMEOUT_MS + 50L
      vm.onSubPageScreenTurned()
      assertEquals(2, vm.pagesSinceFlash.value)
      assertEquals(2, vm.pageTurnTrigger.value)

      vm.onReaderForegroundChanged(false)
      stats.recordReading(0, 0)
      assertEquals(2, stats.stats.value.totalPagesTurned)
    } finally {
      vmRef?.onReaderForegroundChanged(false)
      dir.deleteRecursively()
    }
  }

  @Test
  fun setOverlayOpenUpdatesOverlayStateFlowAndSuppressesVolumePageTurn() = runBlocking {
    val dir = createTempDirectory("reader_vm_overlay_test").toFile()
    var vmRef: ReaderViewModel? = null
    try {
      val (vm, _, _) =
        createViewModel(
          dir = dir,
          initialSettings = EInkSettings(volumeButtonsTurnPages = true),
          nowProvider = { 1_000L },
        )
      vmRef = vm

      assertFalse(vm.overlayOpen.value)
      assertTrue(
        shouldHandleVolumePageTurn(
          volumeButtonsTurnPages = vm.settings.value.volumeButtonsTurnPages,
          pendingNoteOpen = false,
          dictionaryOpen = false,
          overlayOpen = vm.overlayOpen.value,
        ),
      )

      vm.setOverlayOpen(true)
      assertTrue(vm.overlayOpen.value)
      assertFalse(
        shouldHandleVolumePageTurn(
          volumeButtonsTurnPages = vm.settings.value.volumeButtonsTurnPages,
          pendingNoteOpen = false,
          dictionaryOpen = false,
          overlayOpen = vm.overlayOpen.value,
        ),
      )

      vm.setOverlayOpen(false)
      assertFalse(vm.overlayOpen.value)
      assertTrue(
        shouldHandleVolumePageTurn(
          volumeButtonsTurnPages = vm.settings.value.volumeButtonsTurnPages,
          pendingNoteOpen = false,
          dictionaryOpen = false,
          overlayOpen = vm.overlayOpen.value,
        ),
      )
    } finally {
      vmRef?.onReaderForegroundChanged(false)
      dir.deleteRecursively()
    }
  }

  @Test
  fun shouldHandleVolumePageTurnBlocksWhenSettingDisabledOrAnyModalOrOverlayOpen() {
    assertTrue(
      shouldHandleVolumePageTurn(
        volumeButtonsTurnPages = true,
        pendingNoteOpen = false,
        dictionaryOpen = false,
        overlayOpen = false,
      ),
    )
    assertFalse(
      shouldHandleVolumePageTurn(
        volumeButtonsTurnPages = false,
        pendingNoteOpen = false,
        dictionaryOpen = false,
        overlayOpen = false,
      ),
    )
    assertFalse(
      shouldHandleVolumePageTurn(
        volumeButtonsTurnPages = true,
        pendingNoteOpen = true,
        dictionaryOpen = false,
        overlayOpen = false,
      ),
    )
    assertFalse(
      shouldHandleVolumePageTurn(
        volumeButtonsTurnPages = true,
        pendingNoteOpen = false,
        dictionaryOpen = true,
        overlayOpen = false,
      ),
    )
    assertFalse(
      shouldHandleVolumePageTurn(
        volumeButtonsTurnPages = true,
        pendingNoteOpen = false,
        dictionaryOpen = false,
        overlayOpen = true,
      ),
    )
  }
}
