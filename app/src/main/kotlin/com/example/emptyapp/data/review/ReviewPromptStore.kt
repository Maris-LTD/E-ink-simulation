package com.example.emptyapp.data.review

import android.content.Context
import android.content.SharedPreferences

class ReviewPromptStore private constructor(
  private val prefs: SharedPreferences?,
  initialSessionCount: Int = 0,
  initialPrompted: Boolean = false,
) {
  constructor(context: Context) : this(
    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
  )

  internal constructor(
    initialSessionCount: Int = 0,
    initialPrompted: Boolean = false,
  ) : this(
    prefs = null,
    initialSessionCount = initialSessionCount,
    initialPrompted = initialPrompted,
  )

  private var memorySessionCount: Int = initialSessionCount
  private var memoryPrompted: Boolean = initialPrompted

  @Synchronized
  fun readingSessionCount(): Int =
    prefs?.getInt(KEY_READING_SESSION_COUNT, 0) ?: memorySessionCount

  @Synchronized
  fun hasPrompted(): Boolean =
    prefs?.getBoolean(KEY_REVIEW_PROMPTED, false) ?: memoryPrompted

  @Synchronized
  fun recordReadingSessionAndShouldPrompt(threshold: Int = DEFAULT_SESSION_THRESHOLD): Boolean {
    val nextCount = readingSessionCount() + 1
    if (prefs != null) {
      prefs.edit().putInt(KEY_READING_SESSION_COUNT, nextCount).apply()
    } else {
      memorySessionCount = nextCount
    }
    return !hasPrompted() && nextCount >= threshold
  }

  @Synchronized
  fun markPrompted() {
    if (prefs != null) {
      prefs.edit().putBoolean(KEY_REVIEW_PROMPTED, true).apply()
    } else {
      memoryPrompted = true
    }
  }

  companion object {
    const val DEFAULT_SESSION_THRESHOLD = 5
    private const val PREFS_NAME = "in_app_review"
    private const val KEY_READING_SESSION_COUNT = "reading_session_count"
    private const val KEY_REVIEW_PROMPTED = "review_prompted"
  }
}
