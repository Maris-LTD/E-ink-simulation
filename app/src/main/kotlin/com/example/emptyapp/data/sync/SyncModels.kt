package com.example.emptyapp.data.sync

import com.example.emptyapp.data.book.BookFormat
import com.example.emptyapp.data.stats.ReadingStatsData
import kotlinx.serialization.Serializable

@Serializable
data class SyncedBookProgress(
  val contentHash: String,
  val title: String,
  val author: String = "",
  val format: BookFormat = BookFormat.EPUB,
  val progress: Float,
  val lastLocatorJson: String? = null,
  val updatedAt: Long = 0L,
)

@Serializable
data class SyncedBookmark(
  val id: String,
  val bookContentHash: String,
  val title: String,
  val progress: Float,
  val locatorJson: String,
  val createdAt: Long,
)

@Serializable
data class SyncedHighlight(
  val id: String,
  val bookContentHash: String,
  val bookTitle: String,
  val quote: String,
  val note: String = "",
  val progress: Float,
  val locatorJson: String,
  val createdAt: Long,
)

@Serializable
data class SyncPayload(
  val version: Int = 1,
  val updatedAt: Long = 0L,
  val books: List<SyncedBookProgress> = emptyList(),
  val bookmarks: List<SyncedBookmark> = emptyList(),
  val highlights: List<SyncedHighlight> = emptyList(),
  val stats: ReadingStatsData = ReadingStatsData(),
)

data class SyncUiState(
  val email: String? = null,
  val displayName: String? = null,
  val lastSyncedAt: Long = 0L,
  val syncBookFiles: Boolean = true,
  val syncing: Boolean = false,
  val statusMessage: String? = null,
  val cloudBooks: List<SyncedBookProgress> = emptyList(),
) {
  val isSignedIn: Boolean get() = !email.isNullOrBlank()
}
