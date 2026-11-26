package com.example.emptyapp.system

import com.example.emptyapp.reader.AppLanguage

internal data class WallpaperLocaleKey(
  val appLanguage: AppLanguage,
  val systemLocaleTag: String,
)

internal data class WallpaperFormatterKey(
  val localeTag: String,
  val is24Hour: Boolean,
)

internal data class WallpaperRankKey(
  val totalSeconds: Long,
  val totalPagesTurned: Int,
  val completedBooksCount: Int,
  val highlightsCount: Int,
  val languageCode: String,
)

internal data class WallpaperExcerptLayoutKey(
  val snippet: String,
  val bodyWidth: Int,
  val widthPx: Float,
  val fgColor: Int,
  val maxLines: Int = Int.MAX_VALUE,
)

internal data class WallpaperCoverKey(
  val bookId: String,
  val filePath: String,
)
