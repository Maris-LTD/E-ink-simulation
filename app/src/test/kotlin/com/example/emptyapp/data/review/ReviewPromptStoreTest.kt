package com.example.emptyapp.data.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptStoreTest {
  @Test
  fun triggersOnlyStartingOnFifthReadingSession() {
    val store = ReviewPromptStore()

    repeat(4) {
      assertFalse(store.recordReadingSessionAndShouldPrompt())
    }
    assertEquals(4, store.readingSessionCount())
    assertTrue(store.recordReadingSessionAndShouldPrompt())
    assertEquals(5, store.readingSessionCount())
  }

  @Test
  fun stopsTriggeringOnceMarkedPrompted() {
    val store = ReviewPromptStore()

    repeat(4) { store.recordReadingSessionAndShouldPrompt() }
    assertTrue(store.recordReadingSessionAndShouldPrompt())

    store.markPrompted()

    assertTrue(store.hasPrompted())
    assertFalse(store.recordReadingSessionAndShouldPrompt())
    assertEquals(6, store.readingSessionCount())
  }

  @Test
  fun retriesOnNextSessionIfPreviousAttemptDidNotMarkPrompted() {
    val store = ReviewPromptStore(initialSessionCount = 4, initialPrompted = false)

    assertTrue(store.recordReadingSessionAndShouldPrompt())
    assertFalse(store.hasPrompted())
    assertTrue(store.recordReadingSessionAndShouldPrompt())
    assertEquals(6, store.readingSessionCount())
  }
}
