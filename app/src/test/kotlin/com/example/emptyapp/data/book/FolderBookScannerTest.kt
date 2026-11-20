package com.example.emptyapp.data.book

import android.provider.DocumentsContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderBookScannerTest {
  @Test
  fun acceptsSupportedExtensionsCaseInsensitively() {
    assertTrue(FolderBookScanner.isSupportedBookDocument("novel.epub", null))
    assertTrue(FolderBookScanner.isSupportedBookDocument("Paper.PDF", "application/octet-stream"))
    assertTrue(FolderBookScanner.isSupportedBookDocument("Manga.Cbz", null))
  }

  @Test
  fun acceptsSupportedMimeTypesWithoutFileExtension() {
    assertTrue(FolderBookScanner.isSupportedBookDocument("book", "application/epub+zip"))
    assertTrue(FolderBookScanner.isSupportedBookDocument("book", "application/x-epub+zip"))
    assertTrue(FolderBookScanner.isSupportedBookDocument("doc", "application/pdf"))
    assertTrue(FolderBookScanner.isSupportedBookDocument("comic", "application/vnd.comicbook+zip"))
    assertTrue(FolderBookScanner.isSupportedBookDocument("comic", "application/x-cbz"))
  }

  @Test
  fun rejectsDirectoriesUnsupportedExtensionsAndEmptyInputs() {
    assertFalse(FolderBookScanner.isSupportedBookDocument("books.pdf", DocumentsContract.Document.MIME_TYPE_DIR))
    assertFalse(FolderBookScanner.isSupportedBookDocument("notes.txt", "text/plain"))
    assertFalse(FolderBookScanner.isSupportedBookDocument("cover.jpg", "image/jpeg"))
    assertFalse(FolderBookScanner.isSupportedBookDocument(null, null))
  }
}
