package com.example.emptyapp.reader.pdf

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CachedPdfDocument(
  val schemaVersion: Int = 1,
  val fileSize: Long,
  val lastModified: Long,
  val pageCount: Int,
  val pages: List<PdfPageAnalysis>,
)

class PdfDocumentCache(private val cacheDir: File) {
  companion object {
    private const val CURRENT_SCHEMA_VERSION = 2
  }

  private val json = Json { ignoreUnknownKeys = true }

  fun load(file: File): List<PdfPageAnalysis>? {
    if (!file.exists()) return null
    val cacheFile = cacheFileFor(file)
    if (!cacheFile.exists()) return null
    return runCatching {
      val cached = json.decodeFromString(CachedPdfDocument.serializer(), cacheFile.readText())
      if (
        cached.schemaVersion == CURRENT_SCHEMA_VERSION &&
          cached.fileSize == file.length() &&
          cached.lastModified == file.lastModified()
      ) {
        cached.pages
      } else {
        null
      }
    }.getOrNull()
  }

  fun save(file: File, pages: List<PdfPageAnalysis>) {
    if (!file.exists() || pages.isEmpty()) return
    runCatching {
      cacheDir.mkdirs()
      val payload =
        CachedPdfDocument(
          schemaVersion = CURRENT_SCHEMA_VERSION,
          fileSize = file.length(),
          lastModified = file.lastModified(),
          pageCount = pages.size,
          pages = pages,
        )
      cacheFileFor(file).writeText(json.encodeToString(CachedPdfDocument.serializer(), payload))
    }
  }

  private fun cacheFileFor(file: File): File {
    val safeName = file.name.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    return File(cacheDir, "$safeName.${file.length()}.json")
  }
}
