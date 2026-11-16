package com.example.emptyapp.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenTextChunkerTest {
  @Test
  fun stripsHtmlAndChunksIntoReadableSnippets() {
    val html = "<html><body><h1>Chương 1</h1><p>Đoạn văn đầu tiên khá ngắn.</p><p>Đoạn văn thứ hai cũng ngắn gọn.</p></body></html>"
    val plain = LockScreenTextChunker.stripHtmlToPlainText(html)
    assertTrue(plain.contains("Chương 1"))
    assertTrue(plain.contains("Đoạn văn đầu tiên khá ngắn."))

    val snippets = LockScreenTextChunker.chunkText(plain, maxCharsPerSnippet = 80)
    assertTrue(snippets.isNotEmpty())
    assertTrue(snippets.all { it.length <= 80 })
  }

  @Test
  fun splitsLongParagraphsOnWordBoundaries() {
    val longPara = (1..40).joinToString(" ") { "word$it" }
    val snippets = LockScreenTextChunker.chunkText(longPara, maxCharsPerSnippet = 90)
    assertTrue(snippets.size >= 3)
    assertTrue(snippets.all { it.length <= 90 })
    assertEquals(longPara, snippets.joinToString(" "))
  }

  @Test
  fun estimatesSnippetIndexFromProgression() {
    val snippets = listOf("A", "B", "C", "D", "E")
    assertEquals(0, LockScreenTextChunker.estimateInitialSnippetIndex(snippets, 0f))
    assertEquals(2, LockScreenTextChunker.estimateInitialSnippetIndex(snippets, 0.5f))
    assertEquals(4, LockScreenTextChunker.estimateInitialSnippetIndex(snippets, 1f))
  }
}
