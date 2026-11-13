package com.example.emptyapp.data.book

import kotlinx.serialization.Serializable

@Serializable
enum class BookFormat(val extension: String, val label: String) {
  EPUB("epub", "EPUB"),
  PDF("pdf", "PDF"),
  CBZ("cbz", "CBZ"),
}

@Serializable
data class BookEntry(
  val id: String,
  val title: String,
  val author: String,
  val fileName: String,
  val format: BookFormat = BookFormat.EPUB,
  val lastLocatorJson: String? = null,
  val progress: Float = 0f,
)
