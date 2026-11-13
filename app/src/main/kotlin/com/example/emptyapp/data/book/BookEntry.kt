package com.example.emptyapp.data.book

import kotlinx.serialization.Serializable

@Serializable
data class BookEntry(
  val id: String,
  val title: String,
  val author: String,
  val fileName: String,
  val lastLocatorJson: String? = null,
  val progress: Float = 0f,
)
