package com.example.emptyapp.reader

import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
object EInkPreferencesMapper {
  val READER_SERIF = FontFamily("Literata")

  fun toEpub(settings: EInkSettings): EpubPreferences =
    EpubPreferences(
      backgroundColor = Color(settings.tone.background),
      textColor = Color(settings.tone.text),
      theme = if (settings.tone == EInkTone.DARK) Theme.DARK else Theme.LIGHT,
      fontFamily = if (settings.font == EInkFont.SERIF) READER_SERIF else FontFamily.SANS_SERIF,
      fontSize = settings.fontScale,
      fontWeight = settings.fontWeight,
      lineHeight = settings.lineHeight,
      pageMargins = settings.pageMargins,
      publisherStyles = false,
      scroll = false,
      columnCount = ColumnCount.ONE,
    )

  fun toPdf(): PdfiumPreferences =
    PdfiumPreferences(
      fit = Fit.CONTAIN,
      pageSpacing = 0.0,
      scrollAxis = Axis.HORIZONTAL,
    )
}
