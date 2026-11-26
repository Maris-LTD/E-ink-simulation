package com.example.emptyapp.reader

import android.content.Context
import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class LockScreenReadingState(
  val bookId: String,
  val title: String,
  val author: String = "",
  val snippets: List<String>,
  val currentSnippetIndex: Int = 0,
  val progress: Float = 0f,
  val locatorJson: String? = null,
  val capturedAt: Long = System.currentTimeMillis(),
  val progressStart: Float = progress,
  val progressEnd: Float = progress,
  val isPdf: Boolean = false,
  val snippetPages: List<Int>? = null,
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

class LockScreenReadingStore internal constructor(
  private val file: File,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
  private val nowProvider: () -> Long = System::currentTimeMillis,
) {
  constructor(context: Context) : this(
    file = File(context.applicationContext.filesDir, "lockscreen_reading.json"),
  )

  private val json = Json { ignoreUnknownKeys = true }
  private val store = AtomicJsonFile(file, LockScreenReadingState.serializer(), json)
  private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)
  private val stateLock = Any()
  private val _state = MutableStateFlow(loadFromDisk())
  val state: StateFlow<LockScreenReadingState?> = _state.asStateFlow()

  fun updateSession(newState: LockScreenReadingState) {
    val clamped =
      newState.copy(
        currentSnippetIndex =
          if (newState.snippets.isEmpty()) 0
          else newState.currentSnippetIndex.coerceIn(0, newState.snippets.lastIndex),
        capturedAt = if (newState.capturedAt > 0L) newState.capturedAt else nowProvider(),
      )
    synchronized(stateLock) {
      _state.update { clamped }
      schedulePersistSnapshot()
    }
  }

  fun stepSnippet(delta: Int): LockScreenReadingState? {
    var stepped: LockScreenReadingState? = null
    synchronized(stateLock) {
      _state.update { current ->
        if (current == null || current.snippets.isEmpty()) {
          stepped = null
          return@update current
        }
        val nextIdx = (current.currentSnippetIndex + delta).coerceIn(0, current.snippets.lastIndex)
        if (nextIdx == current.currentSnippetIndex) {
          stepped = null
          return@update current
        }
        val fraction =
          if (current.snippets.size > 1) nextIdx.toFloat() / (current.snippets.size - 1).toFloat()
          else 0f
        val updatedProgress =
          if (current.progressEnd > current.progressStart) {
            (current.progressStart + (current.progressEnd - current.progressStart) * fraction).coerceIn(0f, 1f)
          } else {
            current.progress
          }
        val targetPdfPage = current.snippetPages?.getOrNull(nextIdx)
        val updatedLocatorJson =
          current.locatorJson?.let { raw ->
            if (current.isPdf && targetPdfPage != null) {
              updatePdfLocatorPosition(raw, targetPdfPage, updatedProgress)
            } else {
              updateLocatorProgression(raw, nextIdx, current.snippets.size)
            }
          }
        val updated =
          current.copy(
            currentSnippetIndex = nextIdx,
            progress = updatedProgress,
            locatorJson = updatedLocatorJson,
            capturedAt = nowProvider(),
          )
        stepped = updated
        updated
      }
      if (stepped != null) {
        schedulePersistSnapshot()
      }
    }
    return stepped
  }

  fun clear() {
    synchronized(stateLock) {
      _state.update { null }
      schedulePersistSnapshot()
    }
  }

  private fun updateLocatorProgression(raw: String, nextIdx: Int, snippetCount: Int): String =
    runCatching {
      val root = json.parseToJsonElement(raw).jsonObject
      val existingLocations = (root["locations"] as? JsonObject)?.toMap().orEmpty()
      val updatedLocations =
        buildJsonObject {
          for ((k, v) in existingLocations) {
            if (k != "progression" || snippetCount <= 1) put(k, v)
          }
          if (snippetCount > 1) {
            val newChapterProg = nextIdx.toDouble() / (snippetCount - 1).toDouble()
            put("progression", JsonPrimitive(newChapterProg))
          }
        }
      val updatedRoot =
        buildJsonObject {
          for ((k, v) in root) {
            if (k != "locations") put(k, v)
          }
          put("locations", updatedLocations)
        }
      json.encodeToString(JsonObject.serializer(), updatedRoot)
    }.getOrDefault(raw)

  private fun updatePdfLocatorPosition(raw: String, pageIndexZeroBased: Int, totalProgress: Float): String =
    runCatching {
      val root = json.parseToJsonElement(raw).jsonObject
      val existingLocations = (root["locations"] as? JsonObject)?.toMap().orEmpty()
      val updatedLocations =
        buildJsonObject {
          for ((k, v) in existingLocations) {
            if (k != "position" && k != "totalProgression") put(k, v)
          }
          put("position", JsonPrimitive(pageIndexZeroBased + 1))
          put("totalProgression", JsonPrimitive(totalProgress.toDouble()))
        }
      val updatedRoot =
        buildJsonObject {
          for ((k, v) in root) {
            if (k != "locations") put(k, v)
          }
          put("locations", updatedLocations)
        }
      json.encodeToString(JsonObject.serializer(), updatedRoot)
    }.getOrDefault(raw)

  private fun loadFromDisk(): LockScreenReadingState? =
    runCatching {
      if (!file.exists()) return null
      json.decodeFromString(LockScreenReadingState.serializer(), file.readText())
    }.getOrNull()

  private fun schedulePersistSnapshot() {
    ioScope.launch {
      val snapshot = synchronized(stateLock) { _state.value }
      runCatching {
        if (snapshot == null) {
          if (file.exists()) file.delete()
        } else {
          store.write(snapshot)
        }
      }
    }
  }
}
