package com.example.emptyapp.reader

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
object EInkPreferencesMapper {
  fun toEpub(settings: EInkSettings): EpubPreferences =
    EpubPreferences(
      backgroundColor = Color(settings.tone.background),
      textColor = Color(settings.tone.text),
      theme = Theme.LIGHT,
      fontFamily = if (settings.font == EInkFont.SERIF) FontFamily.SERIF else FontFamily.SANS_SERIF,
      fontSize = settings.fontScale,
      lineHeight = settings.lineHeight,
      pageMargins = settings.pageMargins,
      publisherStyles = false,
      scroll = false,
      columnCount = ColumnCount.ONE,
    )
}
