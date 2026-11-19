package com.example.emptyapp.data.dictionary

import android.content.Context
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.R
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class DictionaryEntry(
  val word: String,
  val translation: String? = null,
  val phonetic: String? = null,
  val meanings: List<DictionaryMeaning> = emptyList(),
)

data class DictionaryMeaning(
  val partOfSpeech: String,
  val definitions: List<DictionaryDefinition>,
)

data class DictionaryDefinition(
  val definition: String,
  val example: String?,
)

@Serializable
private data class ApiEntry(
  val word: String = "",
  val phonetic: String? = null,
  val phonetics: List<ApiPhonetic> = emptyList(),
  val meanings: List<ApiMeaning> = emptyList(),
)

@Serializable
private data class ApiPhonetic(val text: String? = null)

@Serializable
private data class ApiMeaning(
  val partOfSpeech: String = "",
  val definitions: List<ApiDefinition> = emptyList(),
)

@Serializable
private data class ApiDefinition(
  val definition: String = "",
  val example: String? = null,
)

@Serializable
private data class MyMemoryResponse(
  val responseData: MyMemoryData? = null,
)

@Serializable
private data class MyMemoryData(
  val translatedText: String? = null,
)

class DictionaryService(
  private val context: Context? = null,
  private val httpGetter: ((String) -> String?)? = null,
) {
  private val json = Json { ignoreUnknownKeys = true }
  private val listSerializer = ListSerializer(ApiEntry.serializer())

  private fun str(resId: Int, fallback: String): String =
    context?.let { LocaleHelper.wrap(it).getString(resId) } ?: fallback

  suspend fun lookup(
    rawText: String,
    targetLang: String = context?.let(LocaleHelper::effectiveLanguageCode) ?: "vi",
  ): Result<DictionaryEntry> =
    withContext(Dispatchers.IO) {
      val query = normalizeQuery(rawText)
      if (query.isEmpty()) {
        return@withContext Result.failure(
          IllegalArgumentException(str(R.string.dict_invalid_query, "Không có nội dung hợp lệ để tra"))
        )
      }
      val isSingleWord = !query.contains(' ')
      val translationDeferred = async { translate(query, targetLang) }
      val dictionaryDeferred = async {
        if (isSingleWord) lookupEnglishDictionary(query).getOrNull() else null
      }
      val rawTranslation = translationDeferred.await()
      val dict = dictionaryDeferred.await()
      val translation =
        if (targetLang == "en" && dict != null && rawTranslation.equals(query, ignoreCase = true)) {
          null
        } else {
          rawTranslation
        }
      if (translation == null && dict == null) {
        return@withContext Result.failure(
          IllegalStateException(
            str(R.string.dict_lookup_failed, "Không dịch hoặc tra được nội dung này (kiểm tra kết nối mạng)")
          )
        )
      }
      Result.success(
        DictionaryEntry(
          word = dict?.word?.ifBlank { query } ?: query,
          translation = translation,
          phonetic = dict?.phonetic,
          meanings = dict?.meanings.orEmpty(),
        )
      )
    }

  private fun lookupEnglishDictionary(word: String): Result<DictionaryEntry> {
    val encoded = URLEncoder.encode(word.lowercase(), "UTF-8").replace("+", "%20")
    val body = performGet("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
      ?: return Result.failure(NoSuchElementException(str(R.string.dict_not_found, "Không tìm thấy định nghĩa")))
    return parseResponse(body)
  }

  private fun translate(text: String, targetLang: String): String? {
    val boundedText = text.take(MAX_QUERY_CHARS)
    val encoded = URLEncoder.encode(boundedText, "UTF-8")
    val googleBody =
      performGet("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded")
    if (googleBody != null) {
      parseGoogleTranslate(googleBody)?.let { return it }
    }
    val myMemoryUrl = buildMyMemoryUrl(boundedText, targetLang)
    val myMemoryBody = performGet(myMemoryUrl)
    if (myMemoryBody != null) {
      parseMyMemory(myMemoryBody)?.let { return it }
    }
    return null
  }

  private fun performGet(urlString: String): String? =
    httpGetter?.invoke(urlString) ?: httpGet(urlString)

  private fun httpGet(urlString: String): String? {
    val connection =
      (URL(urlString).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "EmptyApp/1.0")
      }
    return try {
      if (connection.responseCode !in 200..299) null
      else connection.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) {
      null
    } finally {
      connection.disconnect()
    }
  }

  internal fun parseResponse(jsonText: String): Result<DictionaryEntry> =
    runCatching {
      val entries = json.decodeFromString(listSerializer, jsonText)
      val first = entries.firstOrNull() ?: error(str(com.example.emptyapp.R.string.dict_no_data, "No dictionary data"))
      val phonetic =
        first.phonetic?.takeIf { it.isNotBlank() }
          ?: first.phonetics.firstNotNullOfOrNull { it.text?.takeIf(String::isNotBlank) }
      val meanings =
        first.meanings.mapNotNull { meaning ->
          val defs =
            meaning.definitions
              .filter { it.definition.isNotBlank() }
              .take(3)
              .map { DictionaryDefinition(it.definition, it.example?.takeIf(String::isNotBlank)) }
          if (defs.isEmpty()) null
          else DictionaryMeaning(meaning.partOfSpeech, defs)
        }
      if (meanings.isEmpty()) error(str(com.example.emptyapp.R.string.dict_no_definition, "No definition found"))
      DictionaryEntry(
        word = first.word.ifBlank { "" },
        phonetic = phonetic,
        meanings = meanings,
      )
    }

  internal fun parseGoogleTranslate(jsonText: String): String? =
    runCatching {
      val root = json.parseToJsonElement(jsonText).jsonArray
      val segments = root.firstOrNull() as? JsonArray ?: return null
      val translated =
        segments.mapNotNull { segment ->
          (segment as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content
        }.joinToString("")
      translated.trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

  internal fun parseMyMemory(jsonText: String): String? =
    runCatching {
      json.decodeFromString(MyMemoryResponse.serializer(), jsonText)
        .responseData
        ?.translatedText
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

  companion object {
    const val MAX_QUERY_CHARS = 500

    private val EDGE_PUNCTUATION = charArrayOf(
      '.', ',', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}',
      '“', '”', '‘', '’', '—', '–', '-', '…',
    )

    fun normalizeQuery(raw: String): String {
      val trimmedInput = raw.trim().take(MAX_QUERY_CHARS)
      val collapsed = trimmedInput.replace(Regex("\\s+"), " ").trim().take(MAX_QUERY_CHARS)
      if (collapsed.isEmpty()) return ""
      return if (collapsed.contains(' ')) collapsed else collapsed.trim(*EDGE_PUNCTUATION)
    }

    fun normalizeWord(raw: String): String =
      raw.trim().take(MAX_QUERY_CHARS).trim(*EDGE_PUNCTUATION).lowercase()

    fun buildMyMemoryLangPair(targetLang: String): String {
      val normalizedTarget = targetLang.trim().ifEmpty { "vi" }
      return "auto|$normalizedTarget"
    }

    fun buildMyMemoryUrl(text: String, targetLang: String): String {
      val encoded = URLEncoder.encode(text.take(MAX_QUERY_CHARS), "UTF-8")
      val langPair = buildMyMemoryLangPair(targetLang)
      return "https://api.mymemory.translated.net/get?q=$encoded&langpair=$langPair"
    }
  }
}
