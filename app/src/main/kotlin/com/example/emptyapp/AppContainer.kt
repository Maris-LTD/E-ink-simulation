package com.example.emptyapp

import android.content.Context
import com.example.emptyapp.data.annotation.AnnotationRepository
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
  val engine: ReadiumEngine by lazy { ReadiumEngine(appContext) }
  val library: LibraryRepository by lazy { LibraryRepository(appContext, engine) }
  val settings: EInkSettingsStore by lazy { EInkSettingsStore(appContext) }
  val pdfBookConfigs: PdfBookConfigStore by lazy { PdfBookConfigStore(appContext) }
  val syncTombstones: SyncTombstoneStore by lazy {
    SyncTombstoneStore(File(appContext.filesDir, "sync_tombstones.json"))
  }
  val annotations: AnnotationRepository by lazy { AnnotationRepository(appContext, syncTombstones) }
  val dictionary: DictionaryService by lazy { DictionaryService(appContext) }
  val stats: ReadingStatsRepository by lazy { ReadingStatsRepository(appContext) }
  val sync: GoogleDriveSyncManager by lazy {
    GoogleDriveSyncManager(appContext, library, annotations, stats, syncTombstones)
  }
  val lockScreenReading: LockScreenReadingStore by lazy { LockScreenReadingStore(appContext) }

  fun warmUpForUiAsync() {
    appScope.launch(Dispatchers.IO) { settings }
    appScope.launch(Dispatchers.IO) { library }
    appScope.launch(Dispatchers.IO) { annotations }
    appScope.launch(Dispatchers.IO) { stats }
  }
}
