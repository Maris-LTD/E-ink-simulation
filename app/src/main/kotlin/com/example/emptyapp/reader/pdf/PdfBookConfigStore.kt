package com.example.emptyapp.reader.pdf

import android.content.Context
import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class PdfBookConfigStore(storageFile: File) {
  constructor(context: Context) : this(File(context.filesDir, "pdf_book_configs.json"))

  private val store =
    AtomicJsonFile(storageFile, MapSerializer(String.serializer(), PdfBookConfig.serializer()))
  private val lock = Any()
  private val state = MutableStateFlow(store.read(emptyMap()))

  val configs: StateFlow<Map<String, PdfBookConfig>> = state.asStateFlow()

  fun get(bookId: String): PdfBookConfig? = state.value[bookId]

  fun update(bookId: String, transform: (PdfBookConfig) -> PdfBookConfig): PdfBookConfig {
    synchronized(lock) {
      val current = state.value[bookId] ?: PdfBookConfig()
      val updated = transform(current)
      val nextMap = state.value.toMutableMap().apply { put(bookId, updated) }
      state.value = nextMap
      runCatching { store.write(nextMap) }
      return updated
    }
  }
}
