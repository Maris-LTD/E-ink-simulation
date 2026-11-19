package com.example.emptyapp.data.sync

import java.io.IOException
import kotlinx.coroutines.CancellationException

internal object DriveWipeCoordinator {
  const val DEFAULT_MAX_PASSES = 5

  fun isRevokeResponseAccepted(statusCode: Int): Boolean =
    statusCode in 200..299 || statusCode == 400

  fun isDeleteResponseSuccess(statusCode: Int): Boolean =
    statusCode in 200..299 || statusCode == 404

  suspend fun wipeAll(
    maxPasses: Int = DEFAULT_MAX_PASSES,
    listFileIds: suspend () -> List<String>,
    deleteFile: suspend (String) -> Boolean,
    ensureSessionActive: () -> Unit = {},
  ) {
    require(maxPasses > 0) { "maxPasses must be positive" }
    var lastDeleteError: Throwable? = null
    for (pass in 1..maxPasses) {
      ensureSessionActive()
      val fileIds = listFileIds()
      if (fileIds.isEmpty()) return
      lastDeleteError = null
      for (fileId in fileIds) {
        ensureSessionActive()
        try {
          deleteFile(fileId)
        } catch (e: CancellationException) {
          throw e
        } catch (err: Throwable) {
          if (err is SyncSessionInvalidatedException || DriveAuthRetry.isAuthExpiredError(err)) {
            throw err
          }
          lastDeleteError = err
        }
      }
    }
    ensureSessionActive()
    val remaining = listFileIds()
    if (remaining.isEmpty()) return
    throw lastDeleteError
      ?: IOException("Google Drive wipe incomplete: ${remaining.size} file(s) remaining")
  }
}
