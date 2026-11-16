package com.example.emptyapp

import android.content.Context
import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.dictionary.DictionaryService
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.data.sync.GoogleDriveSyncManager
import com.example.emptyapp.reader.EInkSettingsStore
import com.example.emptyapp.reader.LockScreenReadingStore
import com.example.emptyapp.reader.ReadiumEngine
import com.example.emptyapp.reader.pdf.PdfBookConfigStore

class AppContainer(context: Context) {
  val engine = ReadiumEngine(context)
  val library = LibraryRepository(context, engine)
  val settings = EInkSettingsStore(context)
  val pdfBookConfigs = PdfBookConfigStore(context)
  val annotations = AnnotationRepository(context)
  val dictionary = DictionaryService(context)
  val stats = ReadingStatsRepository(context)
  val sync = GoogleDriveSyncManager(context, library, annotations, stats)
  val lockScreenReading = LockScreenReadingStore(context)
}
