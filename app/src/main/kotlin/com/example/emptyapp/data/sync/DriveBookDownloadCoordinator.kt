package com.example.emptyapp.data.sync

import java.io.File
import kotlinx.coroutines.CancellationException

data class CloudBookDownloadSummary(
  val importedCount: Int,
  val failedCount: Int,
)

object DriveBookDownloadCoordinator {
  suspend fun downloadMissingBooks(
    remoteBooks: List<SyncedBookProgress>,
    appDataFiles: Map<String, String>,
    initialLocalHashes: Set<String>,
    deletedHashes: Set<String>,
    tempDir: File,
    bookDriveFileName: (hash: String, extension: String) -> String = { hash, ext -> "book_${hash}.$ext" },
    ensureSessionActive: suspend () -> Unit = {},
    onBookStarting: (SyncedBookProgress) -> Unit = {},
    downloadFileTo: suspend (driveFileId: String, tempFile: File) -> Boolean,
    importSyncedFile: suspend (tempFile: File, remoteBook: SyncedBookProgress) -> Boolean,
  ): CloudBookDownloadSummary {
    val localHashes = initialLocalHashes.map { it.lowercase() }.toHashSet()
    val deletedNormalized = deletedHashes.map { it.lowercase() }.toHashSet()
    var importedCount = 0
    var failedCount = 0

    for (remoteBook in remoteBooks) {
      ensureSessionActive()
      val normalizedHash = remoteBook.contentHash.trim().lowercase()
      if (normalizedHash.isEmpty()) continue
      if (normalizedHash in localHashes || normalizedHash in deletedNormalized) continue
      val driveFileName = bookDriveFileName(remoteBook.contentHash, remoteBook.format.extension)
      val driveFileId = appDataFiles[driveFileName] ?: continue

      onBookStarting(remoteBook)
      tempDir.mkdirs()
      val tempFile = File(tempDir, "sync_${normalizedHash}.tmp")
      try {
        val downloaded = downloadFileTo(driveFileId, tempFile)
        if (!downloaded) {
          failedCount++
          continue
        }
        ensureSessionActive()
        val imported = importSyncedFile(tempFile, remoteBook)
        if (imported) {
          importedCount++
        }
        localHashes += normalizedHash
      } catch (e: CancellationException) {
        throw e
      } catch (e: SyncSessionInvalidatedException) {
        throw e
      } catch (err: Throwable) {
        if (DriveAuthRetry.isAuthExpiredError(err)) {
          throw err
        }
        failedCount++
      } finally {
        tempFile.delete()
      }
    }

    return CloudBookDownloadSummary(
      importedCount = importedCount,
      failedCount = failedCount,
    )
  }
}
