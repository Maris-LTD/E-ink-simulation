package com.example.emptyapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.example.emptyapp.reader.AppLanguage
import java.text.Collator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object LocaleHelper {
  private const val PREFS_NAME = "eink_settings"
  private const val KEY_APP_LANGUAGE = "app_language"

  fun getSavedLanguage(context: Context): AppLanguage {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_APP_LANGUAGE, null)
    return AppLanguage.entries.firstOrNull { it.name == raw } ?: AppLanguage.SYSTEM
  }

  fun effectiveLanguageCode(context: Context): String =
    when (getSavedLanguage(context)) {
      AppLanguage.VI -> "vi"
      AppLanguage.EN -> "en"
      AppLanguage.SYSTEM -> {
        val sysLocale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
        if (sysLocale.language.equals("vi", ignoreCase = true)) "vi" else "en"
      }
    }

  fun effectiveLocale(context: Context): Locale =
    if (effectiveLanguageCode(context) == "en") Locale.ENGLISH else Locale.forLanguageTag("vi-VN")

  fun formatClockTime(
    time: LocalDateTime,
    is24Hour: Boolean,
    locale: Locale,
  ): String {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, locale))
  }

  fun nameComparator(locale: Locale): Comparator<String> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return Comparator { a, b -> collator.compare(a, b) }
  }

  @SuppressLint("AppBundleLocaleChanges")
  fun wrap(base: Context): Context {
    val saved = getSavedLanguage(base)
    if (saved == AppLanguage.SYSTEM) return base
    val locale = Locale.forLanguageTag(if (saved == AppLanguage.VI) "vi" else "en")
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
  }
}
