package com.example.emptyapp.reader.pdf

import com.example.emptyapp.data.storage.AtomicJsonFile
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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

@Serializable
data class PdfCacheManifest(
  val schemaVersion: Int = PdfDocumentCache.CURRENT_SCHEMA_VERSION,
  val fileSize: Long,
  val lastModified: Long,
  val pageCount: Int,
  val chunkSize: Int,
  val chunkCount: Int,
)

@Serializable
data class PdfCacheChunk(
  val schemaVersion: Int = PdfDocumentCache.CURRENT_SCHEMA_VERSION,
  val chunkIndex: Int,
  val pages: List<PdfPageAnalysis>,
)

class PdfDocumentCache internal constructor(
  private val cacheDir: File,
  private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
  private val textReader: (File) -> String = { it.readText() },
) {
  constructor(cacheDir: File) : this(cacheDir, DEFAULT_CHUNK_SIZE)

  companion object {
    internal const val CURRENT_SCHEMA_VERSION = 4
    internal const val DEFAULT_CHUNK_SIZE = 50
    private const val MAX_IN_MEMORY_MANIFESTS = 16
    private const val MAX_IN_MEMORY_CHUNKS = 8

    private val INVALID_MANIFEST =
      PdfCacheManifest(
        schemaVersion = -1,
        fileSize = -1L,
        lastModified = -1L,
        pageCount = -1,
        chunkSize = -1,
        chunkCount = -1,
      )
    private val INVALID_CHUNK =
      PdfCacheChunk(
        schemaVersion = -1,
        chunkIndex = -1,
        pages = emptyList(),
      )

    private val memoryLock = Any()
    private val manifestMemoryCache = InMemoryLru<DocumentCacheKey, PdfCacheManifest>(MAX_IN_MEMORY_MANIFESTS)
    private val chunkMemoryCache = InMemoryLru<ChunkCacheKey, CachedChunkEntry>(MAX_IN_MEMORY_CHUNKS)
    private val docLocks = ConcurrentHashMap<String, Any>()
    private val CHUNK_FILE_REGEX = Regex("""^chunk_(\d+)\.json(?:\.tmp)?$""")

    internal fun clearInMemoryCache() {
      synchronized(memoryLock) {
        manifestMemoryCache.clear()
        chunkMemoryCache.clear()
      }
    }
  }

  private data class DocumentCacheKey(
    val cacheDirPath: String,
    val fileCanonicalPath: String,
    val fileSize: Long,
    val lastModified: Long,
  )

  private data class ChunkCacheKey(
    val docKey: DocumentCacheKey,
    val chunkIndex: Int,
  )

  private data class CachedChunkEntry(
    val pages: List<PdfPageAnalysis>,
    val byIndex: Map<Int, PdfPageAnalysis>,
  )

  private class InMemoryLru<K, V>(private val maxEntries: Int) {
    private val map =
      object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
          size > maxEntries
      }

    fun get(key: K): V? = map[key]

    fun put(key: K, value: V) {
      map[key] = value
    }

    fun removeIf(predicate: (K) -> Boolean) {
      val iter = map.keys.iterator()
      while (iter.hasNext()) {
        if (predicate(iter.next())) {
          iter.remove()
        }
      }
    }

    fun clear() {
      map.clear()
    }
  }

  private val json = Json { ignoreUnknownKeys = true }

  fun loadManifest(file: File): PdfCacheManifest? {
    if (!file.exists()) return null
    return synchronized(lockFor(file)) {
      if (!file.exists()) return@synchronized null
      readValidManifestLocked(file, documentKeyOf(file))
    }
  }

  fun load(file: File): List<PdfPageAnalysis>? {
    if (!file.exists()) return null
    return synchronized(lockFor(file)) {
      if (!file.exists()) return@synchronized null
      val docKey = documentKeyOf(file)
      val manifest = readValidManifestLocked(file, docKey) ?: return@synchronized null
      val entryDir = cacheEntryDir(file)
      val result = ArrayList<PdfPageAnalysis>(manifest.pageCount)
      for (chunkIndex in 0 until manifest.chunkCount) {
        val chunkEntry = readChunkLocked(entryDir, docKey, chunkIndex) ?: return@synchronized null
        result.addAll(chunkEntry.pages)
      }
      if (result.size != manifest.pageCount) return@synchronized null
      result
    }
  }

  fun loadPages(file: File, pageIndices: Collection<Int>): Map<Int, PdfPageAnalysis> {
    if (!file.exists() || pageIndices.none { it >= 0 }) return emptyMap()
    return synchronized(lockFor(file)) {
      if (!file.exists()) return@synchronized emptyMap()
      val docKey = documentKeyOf(file)
      val manifest = readValidManifestLocked(file, docKey) ?: return@synchronized emptyMap()
      val targetIndices =
        pageIndices
          .asSequence()
          .filter { it in 0 until manifest.pageCount }
          .distinct()
          .toList()
      if (targetIndices.isEmpty()) return@synchronized emptyMap()

      val entryDir = cacheEntryDir(file)
      val indicesByChunk = targetIndices.groupBy { it / manifest.chunkSize }
      val result = LinkedHashMap<Int, PdfPageAnalysis>(targetIndices.size)
      for ((chunkIndex, indicesInChunk) in indicesByChunk) {
        if (chunkIndex !in 0 until manifest.chunkCount) continue
        val chunkEntry = readChunkLocked(entryDir, docKey, chunkIndex) ?: continue
        for (pageIndex in indicesInChunk) {
          chunkEntry.byIndex[pageIndex]?.let { result[pageIndex] = it }
        }
      }
      result
    }
  }

  fun loadPageRange(file: File, startPage: Int, endPageInclusive: Int): List<PdfPageAnalysis> {
    if (endPageInclusive < startPage) return emptyList()
    val start = startPage.coerceAtLeast(0)
    val indices = (start..endPageInclusive).toList()
    if (indices.isEmpty()) return emptyList()
    val loaded = loadPages(file, indices)
    return indices.mapNotNull { loaded[it] }
  }

  fun save(file: File, pages: List<PdfPageAnalysis>) {
    if (!file.exists() || pages.isEmpty()) return
    runCatching {
      synchronized(lockFor(file)) {
        if (!file.exists()) return
        val normalizedChunkSize = chunkSize.coerceAtLeast(1)
        val sortedPages = pages.sortedBy { it.pageIndex }
        val maxPageIndex = sortedPages.maxOf { it.pageIndex.coerceAtLeast(0) }
        val chunkCount =
          maxOf(
            (sortedPages.size + normalizedChunkSize - 1) / normalizedChunkSize,
            (maxPageIndex / normalizedChunkSize) + 1,
          )
        val entryDir = cacheEntryDir(file)
        entryDir.mkdirs()

        // Delete manifest first so an interrupted save cannot pair an old manifest with new chunks.
        val manifestFile = manifestFileFor(entryDir)
        manifestFile.delete()
        File(entryDir, "${manifestFile.name}.tmp").delete()

        val docKey = documentKeyOf(file)
        evictInMemoryForFile(docKey.cacheDirPath, docKey.fileCanonicalPath)

        val pagesByChunk = sortedPages.groupBy { it.pageIndex.coerceAtLeast(0) / normalizedChunkSize }
        for (chunkIndex in 0 until chunkCount) {
          val chunkPages = pagesByChunk[chunkIndex].orEmpty()
          val chunkPayload =
            PdfCacheChunk(
              schemaVersion = CURRENT_SCHEMA_VERSION,
              chunkIndex = chunkIndex,
              pages = chunkPages,
            )
          AtomicJsonFile(chunkFileFor(entryDir, chunkIndex), PdfCacheChunk.serializer(), json)
            .write(chunkPayload)
          putInMemoryChunk(ChunkCacheKey(docKey, chunkIndex), toChunkEntry(chunkPages))
        }

        cleanupExcessChunkFiles(entryDir, chunkCount)

        val manifest =
          PdfCacheManifest(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            fileSize = docKey.fileSize,
            lastModified = docKey.lastModified,
            pageCount = sortedPages.size,
            chunkSize = normalizedChunkSize,
            chunkCount = chunkCount,
          )
        AtomicJsonFile(manifestFile, PdfCacheManifest.serializer(), json).write(manifest)
        putInMemoryManifest(docKey, manifest)
        cleanupLegacyFiles(file)
      }
    }
  }

  fun delete(file: File) {
    runCatching {
      synchronized(lockFor(file)) {
        evictInMemoryForFile(canonicalPathOf(cacheDir), canonicalPathOf(file))
        deleteCacheFilesLocked(file)
      }
    }
  }

  private fun readValidManifestLocked(file: File, docKey: DocumentCacheKey): PdfCacheManifest? {
    val cached = synchronized(memoryLock) { manifestMemoryCache.get(docKey) }
    if (cached != null && isManifestValid(cached, docKey)) {
      return cached
    }

    val entryDir = cacheEntryDir(file)
    val manifestFile = manifestFileFor(entryDir)
    val manifestTempFile = File(entryDir, "${manifestFile.name}.tmp")
    if (!manifestFile.exists() && !manifestTempFile.exists()) {
      if (hasAnyLegacyCacheFile(file)) {
        evictInMemoryForFile(docKey.cacheDirPath, docKey.fileCanonicalPath)
        cleanupLegacyFiles(file)
      }
      return null
    }

    val manifest =
      runCatching {
        AtomicJsonFile(manifestFile, PdfCacheManifest.serializer(), json, textReader = textReader)
          .read(INVALID_MANIFEST)
      }.getOrDefault(INVALID_MANIFEST)

    if (!isManifestValid(manifest, docKey)) {
      evictInMemoryForFile(docKey.cacheDirPath, docKey.fileCanonicalPath)
      if (manifest != INVALID_MANIFEST) {
        deleteCacheFilesLocked(file)
      } else {
        cleanupLegacyFiles(file)
      }
      return null
    }

    putInMemoryManifest(docKey, manifest)
    return manifest
  }

  private fun readChunkLocked(
    entryDir: File,
    docKey: DocumentCacheKey,
    chunkIndex: Int,
  ): CachedChunkEntry? {
    val chunkKey = ChunkCacheKey(docKey, chunkIndex)
    synchronized(memoryLock) { chunkMemoryCache.get(chunkKey) }?.let { return it }

    val chunkFile = chunkFileFor(entryDir, chunkIndex)
    val chunkTempFile = File(entryDir, "${chunkFile.name}.tmp")
    if (!chunkFile.exists() && !chunkTempFile.exists()) return null

    val chunk =
      runCatching {
        AtomicJsonFile(chunkFile, PdfCacheChunk.serializer(), json, textReader = textReader)
          .read(INVALID_CHUNK)
      }.getOrDefault(INVALID_CHUNK)

    if (chunk.schemaVersion != CURRENT_SCHEMA_VERSION || chunk.chunkIndex != chunkIndex) {
      return null
    }

    val entry = toChunkEntry(chunk.pages)
    putInMemoryChunk(chunkKey, entry)
    return entry
  }

  private fun isManifestValid(manifest: PdfCacheManifest, docKey: DocumentCacheKey): Boolean =
    manifest.schemaVersion == CURRENT_SCHEMA_VERSION &&
      manifest.fileSize == docKey.fileSize &&
      manifest.lastModified == docKey.lastModified &&
      manifest.pageCount > 0 &&
      manifest.chunkSize > 0 &&
      manifest.chunkCount > 0

  private fun toChunkEntry(pages: List<PdfPageAnalysis>): CachedChunkEntry {
    val byIndex = LinkedHashMap<Int, PdfPageAnalysis>(pages.size)
    for (page in pages) {
      byIndex[page.pageIndex] = page
    }
    return CachedChunkEntry(pages = pages, byIndex = byIndex)
  }

  private fun putInMemoryManifest(docKey: DocumentCacheKey, manifest: PdfCacheManifest) {
    synchronized(memoryLock) { manifestMemoryCache.put(docKey, manifest) }
  }

  private fun putInMemoryChunk(chunkKey: ChunkCacheKey, entry: CachedChunkEntry) {
    synchronized(memoryLock) { chunkMemoryCache.put(chunkKey, entry) }
  }

  private fun evictInMemoryForFile(cacheDirPath: String, fileCanonicalPath: String) {
    synchronized(memoryLock) {
      manifestMemoryCache.removeIf { key ->
        key.cacheDirPath == cacheDirPath && key.fileCanonicalPath == fileCanonicalPath
      }
      chunkMemoryCache.removeIf { key ->
        key.docKey.cacheDirPath == cacheDirPath && key.docKey.fileCanonicalPath == fileCanonicalPath
      }
    }
  }

  private fun deleteCacheFilesLocked(file: File) {
    val entryDir = cacheEntryDir(file)
    if (entryDir.exists()) {
      entryDir.deleteRecursively()
    }
    cleanupLegacyFiles(file)
  }

  private fun cleanupLegacyFiles(file: File) {
    val legacyFile = legacyCacheFileFor(file)
    legacyFile.delete()
    File(cacheDir, "${legacyFile.name}.tmp").delete()
    if (cacheDir.isDirectory) {
      val pattern = legacyFileRegex(file)
      cacheDir.listFiles()?.forEach { candidate ->
        if (candidate.isFile && pattern.matches(candidate.name)) {
          candidate.delete()
        }
      }
    }
  }

  private fun hasAnyLegacyCacheFile(file: File): Boolean {
    if (legacyCacheFileFor(file).exists()) return true
    if (!cacheDir.isDirectory) return false
    val pattern = legacyFileRegex(file)
    return cacheDir.listFiles()?.any { it.isFile && pattern.matches(it.name) } == true
  }

  private fun legacyFileRegex(file: File): Regex {
    val safeName = safeFileName(file)
    return Regex("""^${Regex.escape(safeName)}\.\d+\.json(\.tmp|\.corrupt-\d+)?$""")
  }

  private fun cleanupExcessChunkFiles(entryDir: File, chunkCount: Int) {
    entryDir.listFiles()?.forEach { candidate ->
      val idx = parseChunkIndex(candidate.name)
      if (idx != null && idx >= chunkCount) {
        candidate.delete()
      }
    }
  }

  private fun parseChunkIndex(fileName: String): Int? {
    val match = CHUNK_FILE_REGEX.matchEntire(fileName) ?: return null
    return match.groupValues[1].toIntOrNull()
  }

  private fun documentKeyOf(file: File): DocumentCacheKey =
    DocumentCacheKey(
      cacheDirPath = canonicalPathOf(cacheDir),
      fileCanonicalPath = canonicalPathOf(file),
      fileSize = file.length(),
      lastModified = file.lastModified(),
    )

  private fun lockFor(file: File): Any {
    val key = "${canonicalPathOf(cacheDir)}|${canonicalPathOf(file)}"
    return docLocks.computeIfAbsent(key) { Any() }
  }

  private fun safeFileName(file: File): String =
    file.name.replace(Regex("""[^A-Za-z0-9._-]"""), "_")

  private fun canonicalPathOf(file: File): String =
    runCatching { file.canonicalFile.path }.getOrElse { file.absolutePath }

  private fun pathDigest(canonicalPath: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(canonicalPath.toByteArray(Charsets.UTF_8))
    return buildString(12) {
      for (i in 0 until 6) {
        append("%02x".format(Locale.US, digest[i].toInt() and 0xff))
      }
    }
  }

  private fun cacheEntryDir(file: File): File {
    val safeName = safeFileName(file)
    val digest = pathDigest(canonicalPathOf(file))
    return File(cacheDir, "${safeName}_$digest")
  }

  private fun manifestFileFor(entryDir: File): File = File(entryDir, "manifest.json")

  private fun chunkFileFor(entryDir: File, chunkIndex: Int): File =
    File(entryDir, "chunk_%04d.json".format(Locale.US, chunkIndex))

  private fun legacyCacheFileFor(file: File): File {
    val safeName = safeFileName(file)
    return File(cacheDir, "$safeName.${file.length()}.json")
  }
}
