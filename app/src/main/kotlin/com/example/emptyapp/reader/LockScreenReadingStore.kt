package com.example.emptyapp.reader

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LockScreenReadingState(
  val bookId: String,
  val title: String,
  val author: String = "",
  val snippets: List<String>,
  val currentSnippetIndex: Int = 0,
  val progress: Float = 0f,
  val locatorJson: String? = null,
) {
  val currentSnippet: String
    get() = snippets.getOrNull(currentSnippetIndex.coerceIn(0, (snippets.size - 1).coerceAtLeast(0))).orEmpty()
}

object LockScreenTextChunker {
  private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
  private val WHITESPACE_REGEX = Regex("""\s+""")

  fun stripHtmlToPlainText(html: String): String =
    html
      .replace(Regex("""<(script|style)[^>]*>[\s\S]*?</\1>""", RegexOption.IGNORE_CASE), " ")
      .replace(Regex("""</(p|div|h[1-6]|li|br\s*/?)>""", RegexOption.IGNORE_CASE), "\n")
      .replace(HTML_TAG_REGEX, " ")
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .lines()
      .map { it.replace(WHITESPACE_REGEX, " ").trim() }
      .filter { it.isNotEmpty() }
      .joinToString("\n\n")

  fun chunkText(text: String, maxCharsPerSnippet: Int = 280): List<String> {
    val cleaned = text.trim()
    if (cleaned.isEmpty()) return emptyList()
    val maxLen = maxCharsPerSnippet.coerceAtLeast(80)
    val paragraphs = cleaned.split(Regex("""\n{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }
    val result = ArrayList<String>()
    val current = StringBuilder()

    fun flush() {
      val s = current.toString().trim()
      if (s.isNotEmpty()) result += s
      current.clear()
    }

    for (para in paragraphs) {
      if (para.length <= maxLen) {
        if (current.isNotEmpty() && current.length + 2 + para.length > maxLen) {
          flush()
        }
        if (current.isNotEmpty()) current.append("\n\n")
        current.append(para)
      } else {
        flush()
        val words = para.split(WHITESPACE_REGEX)
        for (word in words) {
          if (current.isNotEmpty() && current.length + 1 + word.length > maxLen) {
            flush()
          }
          if (current.isNotEmpty()) current.append(' ')
          current.append(word)
        }
        flush()
      }
    }
    flush()
    return result
  }

  fun estimateInitialSnippetIndex(snippets: List<String>, progressionInChapter: Float): Int {
    if (snippets.isEmpty()) return 0
    return (progressionInChapter.coerceIn(0f, 1f) * (snippets.size - 1)).toInt().coerceIn(0, snippets.lastIndex)
  }
}

class LockScreenReadingStore(context: Context) {
  private val file = File(context.filesDir, "lockscreen_reading.json")
  private val json = Json { ignoreUnknownKeys = true }
  private val _state = MutableStateFlow(loadFromDisk())
  val state: StateFlow<LockScreenReadingState?> = _state.asStateFlow()

  fun updateSession(newState: LockScreenReadingState) {
    val clamped =
      newState.copy(
        currentSnippetIndex =
          if (newState.snippets.isEmpty()) 0
          else newState.currentSnippetIndex.coerceIn(0, newState.snippets.lastIndex),
      )
    _state.value = clamped
    saveToDisk(clamped)
  }

  fun stepSnippet(delta: Int): LockScreenReadingState? {
    val current = _state.value ?: return null
    if (current.snippets.isEmpty()) return current
    val nextIdx = (current.currentSnippetIndex + delta).coerceIn(0, current.snippets.lastIndex)
    if (nextIdx == current.currentSnippetIndex) return current
    val updatedLocatorJson =
      current.locatorJson?.let { raw ->
        runCatching {
          val obj = org.json.JSONObject(raw)
          val locations = obj.optJSONObject("locations") ?: org.json.JSONObject().also { obj.put("locations", it) }
          if (current.snippets.size > 1) {
            val newChapterProg = nextIdx.toDouble() / (current.snippets.size - 1).toDouble()
            locations.put("progression", newChapterProg)
          }
          obj.toString()
        }.getOrDefault(raw)
      }
    val updated =
      current.copy(
        currentSnippetIndex = nextIdx,
        locatorJson = updatedLocatorJson,
      )
    _state.value = updated
    saveToDisk(updated)
    return updated
  }

  fun clear() {
    _state.value = null
    runCatching { if (file.exists()) file.delete() }
  }

  private fun loadFromDisk(): LockScreenReadingState? =
    runCatching {
      if (!file.exists()) return null
      json.decodeFromString(LockScreenReadingState.serializer(), file.readText())
    }.getOrNull()

  private fun saveToDisk(value: LockScreenReadingState) {
    runCatching {
      file.parentFile?.mkdirs()
      file.writeText(json.encodeToString(LockScreenReadingState.serializer(), value))
    }
  }
}
