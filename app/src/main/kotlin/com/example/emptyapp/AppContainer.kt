package com.example.emptyapp

import android.content.Context
import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.dictionary.DictionaryService
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.reader.EInkSettingsStore
import com.example.emptyapp.reader.ReadiumEngine

class AppContainer(context: Context) {
  val engine = ReadiumEngine(context)
  val library = LibraryRepository(context, engine)
  val settings = EInkSettingsStore(context)
  val annotations = AnnotationRepository(context)
  val dictionary = DictionaryService()
  val stats = ReadingStatsRepository(context)
}
