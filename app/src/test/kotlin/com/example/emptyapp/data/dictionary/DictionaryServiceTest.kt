package com.example.emptyapp.data.dictionary

import org.junit.Assert.assertEquals
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
