package com.example.emptyapp.reader.pdf

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class PdfBookConfigStore(private val storageFile: File) {
  constructor(context: Context) : this(File(context.filesDir, "pdf_book_configs.json"))

  private val json = Json { ignoreUnknownKeys = true }
  private val serializer = MapSerializer(String.serializer(), PdfBookConfig.serializer())
  private val lock = Any()
  private val state = MutableStateFlow(readFromDisk())

  val configs: StateFlow<Map<String, PdfBookConfig>> = state.asStateFlow()

  fun get(bookId: String): PdfBookConfig? = state.value[bookId]

  fun update(bookId: String, transform: (PdfBookConfig) -> PdfBookConfig): PdfBookConfig {
    synchronized(lock) {
      val current = state.value[bookId] ?: PdfBookConfig()
      val updated = transform(current)
      val nextMap = state.value.toMutableMap().apply { put(bookId, updated) }
      state.value = nextMap
      writeToDisk(nextMap)
      return updated
    }
  }

  private fun readFromDisk(): Map<String, PdfBookConfig> {
    if (!storageFile.exists()) return emptyMap()
    return runCatching { json.decodeFromString(serializer, storageFile.readText()) }
      .getOrDefault(emptyMap())
  }

  private fun writeToDisk(map: Map<String, PdfBookConfig>) {
    runCatching {
      storageFile.parentFile?.mkdirs()
      storageFile.writeText(json.encodeToString(serializer, map))
    }
  }
}
