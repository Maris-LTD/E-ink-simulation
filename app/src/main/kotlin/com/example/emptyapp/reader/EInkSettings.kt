package com.example.emptyapp.reader

enum class EInkTone(val background: Int, val text: Int) {
  PAPER(0xFFE4E0D4.toInt(), 0xFF1A1A1A.toInt()),
  WARM(0xFFEBD9B9.toInt(), 0xFF2B2118.toInt()),
  HIGH_CONTRAST(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
  SOFT_GRAY(0xFFCFCFCF.toInt(), 0xFF101010.toInt()),
  DARK(0xFF121212.toInt(), 0xFFEAEAEA.toInt()),
}

enum class EInkFont { SERIF, SANS_SERIF }

enum class OrientationLock { AUTO, PORTRAIT, LANDSCAPE }

enum class AppLanguage { SYSTEM, VI, EN }

data class EInkSettings(
  val tone: EInkTone = EInkTone.HIGH_CONTRAST,
  val font: EInkFont = EInkFont.SERIF,
  val fontScale: Double = 1.2,
  val fontWeight: Double = 1.25,
  val lineHeight: Double = 1.5,
  val paragraphSpacing: Double = 0.0,
  val justifyText: Boolean = true,
  val pageMargins: Double = 1.5,
  val grayscale: Boolean = true,
  val contrast: Float = 1.25f,
  val brightness: Float = -1f,
  val orientationLock: OrientationLock = OrientationLock.AUTO,
  val flashEnabled: Boolean = false,
  val flashEveryPages: Int = 6,
  val animations: Boolean = false,
  val paperGrain: Boolean = true,
  val ghosting: Boolean = false,
  val readerFooter: Boolean = true,
  val lockScreenReader: Boolean = true,
  val focusMode: Boolean = false,
  val pdfAutoCrop: Boolean = true,
  val appLanguage: AppLanguage = AppLanguage.SYSTEM,
)
