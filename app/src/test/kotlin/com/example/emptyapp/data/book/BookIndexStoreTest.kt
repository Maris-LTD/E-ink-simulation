package com.example.emptyapp.data.book

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class BookIndexStoreTest {
  @Test
  fun legacyJsonWithoutFormatDefaultsToEpub() {
    val dir = createTempDirectory().toFile()
    val file = File(dir, "library.json")
    file.writeText("""[{"id":"1","title":"Old Book","author":"Author","fileName":"1.epub"}]""")

    val loaded = BookIndexStore(file).read()

    assertEquals(1, loaded.size)
    assertEquals(BookFormat.EPUB, loaded[0].format)
    dir.deleteRecursively()
  }

  @Test
  fun roundTripPreservesPdfAndCbzFormats() {
    val dir = createTempDirectory().toFile()
    val file = File(dir, "library.json")
    val store = BookIndexStore(file)
    val books =
      listOf(
        BookEntry(id = "1", title = "Doc", author = "", fileName = "1.pdf", format = BookFormat.PDF),
        BookEntry(id = "2", title = "Comic", author = "", fileName = "2.cbz", format = BookFormat.CBZ),
      )

    store.write(books)
    val loaded = store.read()

    assertEquals(books, loaded)
    dir.deleteRecursively()
  }
}
