package com.example.emptyapp.data.book

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SampleBookSpec(
  val assetPath: String,
  val format: BookFormat,
  val displayName: String,
  val title: String,
  val author: String,
  val updatedAtOffsetMs: Long,
)

class SampleBooksSeeder internal constructor(
  private val isAlreadySeeded: () -> Boolean,
  private val markSeeded: () -> Unit,
  private val awaitLibraryLoaded: suspend () -> Unit,
  private val hasAnyBooks: () -> Boolean,
  private val importSampleAsset: suspend (SampleBookSpec, Long) -> Result<BookEntry>,
  private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
  private val seedMutex = Mutex()

  suspend fun seedIfNeeded(): Int =
    seedMutex.withLock {
      if (isAlreadySeeded()) return 0
      awaitLibraryLoaded()
      if (isAlreadySeeded()) return 0
      if (hasAnyBooks()) {
        markSeeded()
        return 0
      }
      val now = nowProvider()
      var importedCount = 0
      for (spec in DEFAULT_SAMPLES) {
        val result = importSampleAsset(spec, now - spec.updatedAtOffsetMs)
        val err = result.exceptionOrNull()
        if (err is CancellationException) throw err
        if (result.isSuccess) {
          importedCount++
        }
      }
      if (importedCount > 0) {
        markSeeded()
      }
      importedCount
    }

  companion object {
    private const val PREFS_NAME = "eink_sample_books_prefs"
    private const val KEY_SEEDED = "samples_seeded_v1"

    val DEFAULT_SAMPLES =
      listOf(
        SampleBookSpec(
          assetPath = "samples/welcome_inkify.epub",
          format = BookFormat.EPUB,
          displayName = "welcome_inkify.epub",
          title = "Welcome to Inkify",
          author = "Inkify",
          updatedAtOffsetMs = 0L,
        ),
        SampleBookSpec(
          assetPath = "samples/adaptive_pdf_sample.pdf",
          format = BookFormat.PDF,
          displayName = "adaptive_pdf_sample.pdf",
          title = "Adaptive PDF Reflow Demo",
          author = "Inkify",
          updatedAtOffsetMs = 1_000L,
        ),
      )

    fun forContext(context: Context, library: LibraryRepository): SampleBooksSeeder {
      val appCtx = context.applicationContext
      val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      return SampleBooksSeeder(
        isAlreadySeeded = { prefs.getBoolean(KEY_SEEDED, false) },
        markSeeded = { prefs.edit().putBoolean(KEY_SEEDED, true).apply() },
        awaitLibraryLoaded = { library.awaitLoaded() },
        hasAnyBooks = { library.books.value.isNotEmpty() },
        importSampleAsset = { spec, updatedAt ->
          val tempFile = File(appCtx.cacheDir, "sample_${UUID.randomUUID()}.${spec.format.extension}")
          try {
            withContext(Dispatchers.IO) {
              appCtx.assets.open(spec.assetPath).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
              }
            }
            library.importAssetFile(
              tempFile = tempFile,
              format = spec.format,
              displayName = spec.displayName,
              overrideTitle = spec.title,
              overrideAuthor = spec.author,
              updatedAt = updatedAt,
            )
          } catch (e: CancellationException) {
            throw e
          } catch (t: Throwable) {
            Result.failure(t)
          } finally {
            tempFile.delete()
          }
        },
      )
    }
  }
}
