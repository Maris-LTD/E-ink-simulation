package com.example.emptyapp.reader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EInkSettingsStore private constructor(
  private val prefs: SharedPreferences?,
  initialSettings: EInkSettings?,
) {
  constructor(context: Context) : this(
    prefs = context.getSharedPreferences("eink_settings", Context.MODE_PRIVATE),
    initialSettings = null,
  )

  internal constructor(initial: EInkSettings = EInkSettings()) : this(
    prefs = null,
    initialSettings = initial,
  )

  private val state = MutableStateFlow(initialSettings ?: load())

  val settings: StateFlow<EInkSettings> = state.asStateFlow()

  fun update(transform: (EInkSettings) -> EInkSettings) {
    val updated = transform(state.value)
    state.value = updated
    save(updated)
  }

  fun preview(transform: (EInkSettings) -> EInkSettings) {
    state.value = transform(state.value)
  }

  fun persist() {
    save(state.value)
  }

  private fun load(): EInkSettings {
    val defaults = EInkSettings()
    val sharedPrefs = prefs ?: return defaults
    if (sharedPrefs.getInt(KEY_VERSION, 0) < SETTINGS_VERSION) {
      save(defaults)
      return defaults
    }
    return EInkSettings(
      tone = enumOf(sharedPrefs.getString(KEY_TONE, null), defaults.tone),
      font = enumOf(sharedPrefs.getString(KEY_FONT, null), defaults.font),
      fontScale = sharedPrefs.getFloat(KEY_FONT_SCALE, defaults.fontScale.toFloat()).toDouble(),
      fontWeight = sharedPrefs.getFloat(KEY_FONT_WEIGHT, defaults.fontWeight.toFloat()).toDouble(),
      lineHeight = sharedPrefs.getFloat(KEY_LINE_HEIGHT, defaults.lineHeight.toFloat()).toDouble(),
      paragraphSpacing = sharedPrefs.getFloat(KEY_PARAGRAPH_SPACING, defaults.paragraphSpacing.toFloat()).toDouble(),
      justifyText = sharedPrefs.getBoolean(KEY_JUSTIFY_TEXT, defaults.justifyText),
      pageMargins = sharedPrefs.getFloat(KEY_MARGINS, defaults.pageMargins.toFloat()).toDouble(),
      grayscale = sharedPrefs.getBoolean(KEY_GRAYSCALE, defaults.grayscale),
      contrast = sharedPrefs.getFloat(KEY_CONTRAST, defaults.contrast),
      brightness = sharedPrefs.getFloat(KEY_BRIGHTNESS, defaults.brightness),
      orientationLock = enumOf(sharedPrefs.getString(KEY_ORIENTATION_LOCK, null), defaults.orientationLock),
      flashEnabled = sharedPrefs.getBoolean(KEY_FLASH, defaults.flashEnabled),
      flashEveryPages = sharedPrefs.getInt(KEY_FLASH_EVERY, defaults.flashEveryPages),
      animations = sharedPrefs.getBoolean(KEY_ANIMATIONS, defaults.animations),
      paperGrain = sharedPrefs.getBoolean(KEY_PAPER_GRAIN, defaults.paperGrain),
      ghosting = sharedPrefs.getBoolean(KEY_GHOSTING, defaults.ghosting),
      readerFooter = sharedPrefs.getBoolean(KEY_READER_FOOTER, defaults.readerFooter),
      lockScreenReader = sharedPrefs.getBoolean(KEY_LOCK_SCREEN_READER, defaults.lockScreenReader),
      focusMode = sharedPrefs.getBoolean(KEY_FOCUS_MODE, defaults.focusMode),
      pdfAutoCrop = sharedPrefs.getBoolean(KEY_PDF_AUTO_CROP, defaults.pdfAutoCrop),
      volumeButtonsTurnPages = sharedPrefs.getBoolean(KEY_VOLUME_TURN_PAGES, defaults.volumeButtonsTurnPages),
      appLanguage = enumOf(sharedPrefs.getString(KEY_APP_LANGUAGE, null), defaults.appLanguage),
    )
  }

  private fun save(value: EInkSettings) {
    val sharedPrefs = prefs ?: return
    sharedPrefs
      .edit()
      .putInt(KEY_VERSION, SETTINGS_VERSION)
      .putString(KEY_TONE, value.tone.name)
      .putString(KEY_FONT, value.font.name)
      .putFloat(KEY_FONT_SCALE, value.fontScale.toFloat())
      .putFloat(KEY_FONT_WEIGHT, value.fontWeight.toFloat())
      .putFloat(KEY_LINE_HEIGHT, value.lineHeight.toFloat())
      .putFloat(KEY_PARAGRAPH_SPACING, value.paragraphSpacing.toFloat())
      .putBoolean(KEY_JUSTIFY_TEXT, value.justifyText)
      .putFloat(KEY_MARGINS, value.pageMargins.toFloat())
      .putBoolean(KEY_GRAYSCALE, value.grayscale)
      .putFloat(KEY_CONTRAST, value.contrast)
      .putFloat(KEY_BRIGHTNESS, value.brightness)
      .putString(KEY_ORIENTATION_LOCK, value.orientationLock.name)
      .putBoolean(KEY_FLASH, value.flashEnabled)
      .putInt(KEY_FLASH_EVERY, value.flashEveryPages)
      .putBoolean(KEY_ANIMATIONS, value.animations)
      .putBoolean(KEY_PAPER_GRAIN, value.paperGrain)
      .putBoolean(KEY_GHOSTING, value.ghosting)
      .putBoolean(KEY_READER_FOOTER, value.readerFooter)
      .putBoolean(KEY_LOCK_SCREEN_READER, value.lockScreenReader)
      .putBoolean(KEY_FOCUS_MODE, value.focusMode)
      .putBoolean(KEY_PDF_AUTO_CROP, value.pdfAutoCrop)
      .putBoolean(KEY_VOLUME_TURN_PAGES, value.volumeButtonsTurnPages)
      .putString(KEY_APP_LANGUAGE, value.appLanguage.name)
      .apply()
  }

  private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

  private companion object {
    const val SETTINGS_VERSION = 6
    const val KEY_VERSION = "settings_version"
    const val KEY_TONE = "tone"
    const val KEY_FONT = "font"
    const val KEY_FONT_SCALE = "font_scale"
    const val KEY_FONT_WEIGHT = "font_weight"
    const val KEY_LINE_HEIGHT = "line_height"
    const val KEY_PARAGRAPH_SPACING = "paragraph_spacing"
    const val KEY_JUSTIFY_TEXT = "justify_text"
    const val KEY_MARGINS = "page_margins"
    const val KEY_GRAYSCALE = "grayscale"
    const val KEY_CONTRAST = "contrast"
    const val KEY_BRIGHTNESS = "brightness"
    const val KEY_ORIENTATION_LOCK = "orientation_lock"
    const val KEY_FLASH = "flash"
    const val KEY_FLASH_EVERY = "flash_every"
    const val KEY_ANIMATIONS = "animations"
    const val KEY_PAPER_GRAIN = "paper_grain"
    const val KEY_GHOSTING = "ghosting"
    const val KEY_READER_FOOTER = "reader_footer"
    const val KEY_LOCK_SCREEN_READER = "lock_screen_reader"
    const val KEY_FOCUS_MODE = "focus_mode"
    const val KEY_PDF_AUTO_CROP = "pdf_auto_crop"
    const val KEY_VOLUME_TURN_PAGES = "volume_turn_pages"
    const val KEY_APP_LANGUAGE = "app_language"
  }
}
