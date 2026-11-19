package com.example.emptyapp.data.book

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException

sealed interface SyncedBookImportResult {
  data class Imported(val entry: BookEntry) : SyncedBookImportResult
  data object Duplicate : SyncedBookImportResult
  data class Failed(val error: Throwable) : SyncedBookImportResult
}

object SyncedBookFileImporter {
  fun moveFileToTarget(sourceFile: File, targetFile: File) {
    targetFile.parentFile?.mkdirs()
    if (targetFile.exists() && !targetFile.delete()) {
      throw IOException("Cannot delete existing target file: ${targetFile.name}")
    }
    if (sourceFile.renameTo(targetFile) && targetFile.exists()) {
      return
    }
    try {
      sourceFile.inputStream().use { input ->
        targetFile.outputStream().use { output ->
          input.copyTo(output)
          output.fd.sync()
        }
      }
    } catch (t: Throwable) {
      targetFile.delete()
      throw t
    } finally {
      sourceFile.delete()
    }
  }

  suspend fun importSyncedBook(
    sourceFile: File,
    booksDir: File,
    coversDir: File,
    title: String,
    author: String,
    format: BookFormat,
    expectedContentHash: String,
    progress: Float,
    lastLocatorJson: String?,
    updatedAt: Long,
    idProvider: () -> String = { UUID.randomUUID().toString() },
    isDuplicateHash: (String) -> Boolean,
    verifyAndExtractCover: suspend (bookFile: File, coverFile: File) -> Boolean,
    commitEntry: suspend (BookEntry) -> Boolean,
  ): SyncedBookImportResult {
    val normalizedHash = expectedContentHash.trim().lowercase()
    var targetFile: File? = null
    var coverFile: File? = null
    var committed = false
    try {
      if (normalizedHash.isEmpty()) {
        throw IOException("Missing expected SHA-256 content hash")
      }
      if (!sourceFile.exists() || sourceFile.length() <= 0L) {
        throw IOException("Downloaded book file is missing or empty")
      }
      if (isDuplicateHash(normalizedHash)) {
        return SyncedBookImportResult.Duplicate
      }
      val actualHash =
        BookImporter.sha256Hex(sourceFile)?.lowercase()
          ?: throw IOException("Failed to compute SHA-256 of downloaded book")
      if (!actualHash.equals(normalizedHash, ignoreCase = true)) {
        throw IOException("SHA-256 mismatch: expected=$normalizedHash actual=$actualHash")
      }
      if (isDuplicateHash(normalizedHash)) {
        return SyncedBookImportResult.Duplicate
      }

      val id = idProvider()
      val fileName = "$id.${format.extension}"
      val destFile = File(booksDir, fileName)
      val coverName = "$id.jpg"
      val destCoverFile = File(coversDir, coverName)
      targetFile = destFile
      coverFile = destCoverFile

      moveFileToTarget(sourceFile, destFile)

      val hasCover = verifyAndExtractCover(destFile, destCoverFile)
      if (!hasCover) {
        destCoverFile.delete()
      }

      val entry =
        BookEntry(
          id = id,
          title = title,
          author = author,
          fileName = fileName,
          format = format,
          coverFileName = if (hasCover && destCoverFile.exists()) coverName else null,
          lastLocatorJson = lastLocatorJson,
          progress = progress,
          contentHash = normalizedHash,
          updatedAt = updatedAt,
        )
      committed = commitEntry(entry)
      return if (committed) {
        SyncedBookImportResult.Imported(entry)
      } else {
        SyncedBookImportResult.Duplicate
      }
    } catch (e: CancellationException) {
      throw e
    } catch (err: Throwable) {
      return SyncedBookImportResult.Failed(err)
    } finally {
      sourceFile.delete()
      if (!committed) {
        targetFile?.delete()
        coverFile?.delete()
      }
    }
  }
}
