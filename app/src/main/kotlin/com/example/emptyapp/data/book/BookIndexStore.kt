package com.example.emptyapp.data.book

import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class BookIndexStore(private val indexFile: File) {
  private val json = Json { ignoreUnknownKeys = true }
  private val serializer = ListSerializer(BookEntry.serializer())

  fun read(): List<BookEntry> {
    if (!indexFile.exists()) return emptyList()
    return runCatching { json.decodeFromString(serializer, indexFile.readText()) }
      .getOrDefault(emptyList())
  }

  fun write(books: List<BookEntry>) {
    indexFile.parentFile?.mkdirs()
    indexFile.writeText(json.encodeToString(serializer, books))
  }
}
