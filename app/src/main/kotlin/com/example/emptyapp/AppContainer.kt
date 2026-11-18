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

class AppContainer(context: Context) {
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val engine = ReadiumEngine(context)
  val library = LibraryRepository(context, engine)
  val settings = EInkSettingsStore(context)
  val pdfBookConfigs = PdfBookConfigStore(context)
  val syncTombstones = SyncTombstoneStore(File(context.filesDir, "sync_tombstones.json"))
  val annotations = AnnotationRepository(context, syncTombstones)
  val dictionary = DictionaryService(context)
  val stats = ReadingStatsRepository(context)
  val sync = GoogleDriveSyncManager(context, library, annotations, stats, syncTombstones)
  val lockScreenReading = LockScreenReadingStore(context)
}
