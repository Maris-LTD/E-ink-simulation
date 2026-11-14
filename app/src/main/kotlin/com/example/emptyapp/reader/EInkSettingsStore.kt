package com.example.emptyapp.reader

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EInkSettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("eink_settings", Context.MODE_PRIVATE)
  private val state = MutableStateFlow(load())

  val settings: StateFlow<EInkSettings> = state.asStateFlow()

  fun update(transform: (EInkSettings) -> EInkSettings) {
    val updated = transform(state.value)
    state.value = updated
    save(updated)
  }

  private fun load(): EInkSettings {
    val defaults = EInkSettings()
    if (prefs.getInt(KEY_VERSION, 0) < SETTINGS_VERSION) {
      save(defaults)
      return defaults
    }
    return EInkSettings(
      tone = enumOf(prefs.getString(KEY_TONE, null), defaults.tone),
      font = enumOf(prefs.getString(KEY_FONT, null), defaults.font),
      fontScale = prefs.getFloat(KEY_FONT_SCALE, defaults.fontScale.toFloat()).toDouble(),
      fontWeight = prefs.getFloat(KEY_FONT_WEIGHT, defaults.fontWeight.toFloat()).toDouble(),
      lineHeight = prefs.getFloat(KEY_LINE_HEIGHT, defaults.lineHeight.toFloat()).toDouble(),
      paragraphSpacing = prefs.getFloat(KEY_PARAGRAPH_SPACING, defaults.paragraphSpacing.toFloat()).toDouble(),
      justifyText = prefs.getBoolean(KEY_JUSTIFY_TEXT, defaults.justifyText),
      pageMargins = prefs.getFloat(KEY_MARGINS, defaults.pageMargins.toFloat()).toDouble(),
      grayscale = prefs.getBoolean(KEY_GRAYSCALE, defaults.grayscale),
      contrast = prefs.getFloat(KEY_CONTRAST, defaults.contrast),
      brightness = prefs.getFloat(KEY_BRIGHTNESS, defaults.brightness),
      orientationLock = enumOf(prefs.getString(KEY_ORIENTATION_LOCK, null), defaults.orientationLock),
      flashEnabled = prefs.getBoolean(KEY_FLASH, defaults.flashEnabled),
      flashEveryPages = prefs.getInt(KEY_FLASH_EVERY, defaults.flashEveryPages),
      animations = prefs.getBoolean(KEY_ANIMATIONS, defaults.animations),
      paperGrain = prefs.getBoolean(KEY_PAPER_GRAIN, defaults.paperGrain),
      ghosting = prefs.getBoolean(KEY_GHOSTING, defaults.ghosting),
      readerFooter = prefs.getBoolean(KEY_READER_FOOTER, defaults.readerFooter),
      lockScreenReader = prefs.getBoolean(KEY_LOCK_SCREEN_READER, defaults.lockScreenReader),
      focusMode = prefs.getBoolean(KEY_FOCUS_MODE, defaults.focusMode),
      appLanguage = enumOf(prefs.getString(KEY_APP_LANGUAGE, null), defaults.appLanguage),
    )
  }

  private fun save(value: EInkSettings) {
    prefs
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
    const val KEY_APP_LANGUAGE = "app_language"
  }
}
