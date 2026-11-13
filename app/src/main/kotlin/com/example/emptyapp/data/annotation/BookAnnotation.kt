package com.example.emptyapp.data.annotation

import kotlinx.serialization.Serializable

@Serializable
data class BookmarkEntry(
  val id: String,
  val bookId: String,
  val title: String,
  val progress: Float,
  val locatorJson: String,
  val createdAt: Long,
)

@Serializable
data class HighlightEntry(
  val id: String,
  val bookId: String,
  val bookTitle: String,
  val quote: String,
  val note: String = "",
  val progress: Float,
  val locatorJson: String,
  val createdAt: Long,
)
