package com.example.emptyapp.reader

enum class EInkTone(val background: Int, val text: Int) {
  PAPER(0xFFE4E0D4.toInt(), 0xFF1A1A1A.toInt()),
  HIGH_CONTRAST(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
  SOFT_GRAY(0xFFCFCFCF.toInt(), 0xFF101010.toInt()),
}

enum class EInkFont { SERIF, SANS_SERIF }

data class EInkSettings(
  val tone: EInkTone = EInkTone.HIGH_CONTRAST,
  val font: EInkFont = EInkFont.SERIF,
  val fontScale: Double = 1.2,
  val fontWeight: Double = 1.25,
  val lineHeight: Double = 1.5,
  val pageMargins: Double = 1.5,
  val grayscale: Boolean = true,
  val contrast: Float = 1.25f,
  val flashEnabled: Boolean = false,
  val flashEveryPages: Int = 6,
  val animations: Boolean = false,
  val paperGrain: Boolean = false,
  val ghosting: Boolean = false,
  val readerFooter: Boolean = true,
)
