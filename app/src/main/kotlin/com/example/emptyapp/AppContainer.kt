package com.example.emptyapp

import android.content.Context
import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.billing.AndroidKeystoreEntitlementCipher
import com.example.emptyapp.data.billing.EntitlementStore
import com.example.emptyapp.data.billing.PlayBillingManager
import com.example.emptyapp.data.billing.ProFeatureGate
import com.example.emptyapp.security.AppIntegrityGuard
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.dictionary.DictionaryService
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.data.sync.GoogleDriveSyncManager
import com.example.emptyapp.data.sync.SyncTombstoneStore
import com.example.emptyapp.reader.EInkSettingsStore
import com.example.emptyapp.reader.LockScreenReadingStore
import com.example.emptyapp.reader.ReadiumEngine
import com.example.emptyapp.reader.pdf.PdfBookConfigStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
  private val appContext = context.applicationContext
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val engineLazy: Lazy<ReadiumEngine> = lazy { ReadiumEngine(appContext) }
  val engine: ReadiumEngine by engineLazy
  val library: LibraryRepository by lazy { LibraryRepository(appContext, engineLazy) }
  val settings: EInkSettingsStore by lazy { EInkSettingsStore(appContext) }
  val pdfBookConfigs: PdfBookConfigStore by lazy { PdfBookConfigStore(appContext) }
  val syncTombstones: SyncTombstoneStore by lazy {
    SyncTombstoneStore(File(appContext.filesDir, "sync_tombstones.json"), preloadInBackground = true)
  }
  val annotations: AnnotationRepository by lazy { AnnotationRepository(appContext, syncTombstones) }
  val dictionary: DictionaryService by lazy { DictionaryService(appContext) }
  val stats: ReadingStatsRepository by lazy { ReadingStatsRepository(appContext) }
  val sync: GoogleDriveSyncManager by lazy {
    GoogleDriveSyncManager(appContext, library, annotations, stats, syncTombstones, appScope)
  }
  val lockScreenReading: LockScreenReadingStore by lazy { LockScreenReadingStore(appContext) }
  val entitlements: EntitlementStore by lazy {
    EntitlementStore(
      appContext.filesDir,
      fileName = EntitlementStore.ENCRYPTED_FILE_NAME,
      cipher = AndroidKeystoreEntitlementCipher(),
    )
  }
  val proGate: ProFeatureGate by lazy {
    ProFeatureGate(entitlements) { AppIntegrityGuard.isIntegrityValid(appContext) }
  }
  val billing: PlayBillingManager by lazy { PlayBillingManager(appContext, proGate, appScope) }

  fun warmUpForUiAsync() {
    appScope.launch(Dispatchers.IO) { settings }
    appScope.launch(Dispatchers.IO) { library }
    appScope.launch(Dispatchers.IO) { pdfBookConfigs }
    appScope.launch(Dispatchers.IO) { syncTombstones }
    appScope.launch(Dispatchers.IO) { annotations }
    appScope.launch(Dispatchers.IO) { stats }
    appScope.launch(Dispatchers.IO) { lockScreenReading }
    appScope.launch(Dispatchers.IO) { proGate }
    appScope.launch(Dispatchers.IO) { billing.startConnection() }
    appScope.launch(Dispatchers.IO) { sync }
  }
}
