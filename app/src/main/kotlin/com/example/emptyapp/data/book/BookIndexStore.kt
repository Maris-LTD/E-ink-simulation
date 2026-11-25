package com.example.emptyapp.data.book

import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import kotlinx.serialization.builtins.ListSerializer

class BookIndexStore(indexFile: File) {
  private val store = AtomicJsonFile(indexFile, ListSerializer(BookEntry.serializer()))

  fun read(): List<BookEntry> = store.read(emptyList())

  fun write(books: List<BookEntry>, syncToDisk: Boolean = true) =
    store.write(books, syncToDisk = syncToDisk)
}
