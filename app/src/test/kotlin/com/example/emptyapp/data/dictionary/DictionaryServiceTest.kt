package com.example.emptyapp.data.dictionary

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryServiceTest {
  @Test
  fun normalizeWordStripsEdgePunctuationAndLowercases() {
    assertEquals("ephemeral", DictionaryService.normalizeWord("  “Ephemeral,” "))
    assertEquals("read-only", DictionaryService.normalizeWord("(Read-Only)!"))
    assertEquals("Hello world, how are you?", DictionaryService.normalizeQuery("  Hello   world,\n how are you? "))
  }

  @Test
  fun normalizeQueryTruncatesToMaxQueryChars() {
    val longInput = "a".repeat(800)
    val normalized = DictionaryService.normalizeQuery(longInput)
    assertEquals(DictionaryService.MAX_QUERY_CHARS, normalized.length)

    val longPhrase = ("word ".repeat(200))
    val normalizedPhrase = DictionaryService.normalizeQuery(longPhrase)
    assertTrue(normalizedPhrase.length <= DictionaryService.MAX_QUERY_CHARS)
  }

  @Test
  fun myMemoryFallbackUsesAutoSourceLanguageAndTargetLang() = runTest {
    val requestedUrls = mutableListOf<String>()
    val service =
      DictionaryService(
        httpGetter = { url ->
          requestedUrls += url
          when {
            url.contains("translate.googleapis.com") -> null
            url.contains("api.mymemory.translated.net") ->
              """{"responseData":{"translatedText":"Bonjour"}}"""
            else -> null
          }
        },
      )

    val longText = "Hello world ".repeat(100)
    val result = service.lookup(longText, targetLang = "fr")

    assertTrue(result.isSuccess)
    assertEquals("Bonjour", result.getOrThrow().translation)
    val myMemoryCall = requestedUrls.first { it.contains("api.mymemory.translated.net") }
    assertTrue(myMemoryCall.contains("langpair=auto|fr"))
    assertFalse(myMemoryCall.contains("langpair=en|vi"))
    assertEquals("auto|vi", DictionaryService.buildMyMemoryLangPair("vi"))
    assertEquals("auto|en", DictionaryService.buildMyMemoryLangPair("en"))
  }

  @Test
  fun parseGoogleTranslateAndMyMemoryExtractTranslation() {
    val service = DictionaryService()
    val googleJson = """[[["xin chào ", "hello ", null, null, 3],["thế giới", "world", null, null, 3]], null, "en"]"""
    val myMemoryJson = """{"responseData":{"translatedText":"Xin chào thế giới"}}"""

    assertEquals("xin chào thế giới", service.parseGoogleTranslate(googleJson))
    assertEquals("Xin chào thế giới", service.parseMyMemory(myMemoryJson))
  }

  @Test
  fun parseResponseExtractsPhoneticMeaningsAndExamples() {
    val json =
      """
      [
        {
          "word": "ephemeral",
          "phonetic": "/ɪˈfɛm.ər.əl/",
          "meanings": [
            {
              "partOfSpeech": "adjective",
              "definitions": [
                {
                  "definition": "Lasting for a very short time.",
                  "example": "fashions are ephemeral"
                }
              ]
            }
          ]
        }
      ]
      """.trimIndent()

    val result = DictionaryService().parseResponse(json)
    assertTrue(result.isSuccess)
    val entry = result.getOrThrow()
    assertEquals("ephemeral", entry.word)
    assertEquals("/ɪˈfɛm.ər.əl/", entry.phonetic)
    assertEquals(1, entry.meanings.size)
    assertEquals("adjective", entry.meanings[0].partOfSpeech)
    assertEquals("Lasting for a very short time.", entry.meanings[0].definitions[0].definition)
    assertEquals("fashions are ephemeral", entry.meanings[0].definitions[0].example)
  }
}
