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
    return EInkSettings(
      tone = enumOf(prefs.getString(KEY_TONE, null), defaults.tone),
      font = enumOf(prefs.getString(KEY_FONT, null), defaults.font),
      fontScale = prefs.getFloat(KEY_FONT_SCALE, defaults.fontScale.toFloat()).toDouble(),
      lineHeight = prefs.getFloat(KEY_LINE_HEIGHT, defaults.lineHeight.toFloat()).toDouble(),
      pageMargins = prefs.getFloat(KEY_MARGINS, defaults.pageMargins.toFloat()).toDouble(),
      grayscale = prefs.getBoolean(KEY_GRAYSCALE, defaults.grayscale),
      contrast = prefs.getFloat(KEY_CONTRAST, defaults.contrast),
      flashEnabled = prefs.getBoolean(KEY_FLASH, defaults.flashEnabled),
      flashEveryPages = prefs.getInt(KEY_FLASH_EVERY, defaults.flashEveryPages),
      animations = prefs.getBoolean(KEY_ANIMATIONS, defaults.animations),
    )
  }

  private fun save(value: EInkSettings) {
    prefs
      .edit()
      .putString(KEY_TONE, value.tone.name)
      .putString(KEY_FONT, value.font.name)
      .putFloat(KEY_FONT_SCALE, value.fontScale.toFloat())
      .putFloat(KEY_LINE_HEIGHT, value.lineHeight.toFloat())
      .putFloat(KEY_MARGINS, value.pageMargins.toFloat())
      .putBoolean(KEY_GRAYSCALE, value.grayscale)
      .putFloat(KEY_CONTRAST, value.contrast)
      .putBoolean(KEY_FLASH, value.flashEnabled)
      .putInt(KEY_FLASH_EVERY, value.flashEveryPages)
      .putBoolean(KEY_ANIMATIONS, value.animations)
      .apply()
  }

  private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

  private companion object {
    const val KEY_TONE = "tone"
    const val KEY_FONT = "font"
    const val KEY_FONT_SCALE = "font_scale"
    const val KEY_LINE_HEIGHT = "line_height"
    const val KEY_MARGINS = "page_margins"
    const val KEY_GRAYSCALE = "grayscale"
    const val KEY_CONTRAST = "contrast"
    const val KEY_FLASH = "flash"
    const val KEY_FLASH_EVERY = "flash_every"
    const val KEY_ANIMATIONS = "animations"
  }
}
