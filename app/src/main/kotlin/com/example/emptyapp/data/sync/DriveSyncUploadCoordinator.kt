package com.example.emptyapp.data.sync

import java.io.IOException
import kotlinx.coroutines.CancellationException

internal class DriveSyncConflictException(
  message: String = "Remote sync file changed concurrently",
) : IOException(message)

internal data class RemoteSyncSnapshot(
  val primaryEntry: DriveFileEntry,
  val duplicateEntries: List<DriveFileEntry>,
  val mergedPayload: SyncPayload,
  val duplicatePayloads: List<SyncPayload>,
)

internal data class SyncUploadResult(
  val payload: SyncPayload,
  val syncedFileExists: Boolean,
)

internal class DriveUploadTransport(
  val nowProvider: () -> Long,
  val listSyncFiles: suspend () -> SyncFileResolution,
  val fetchMetadata: suspend (fileId: String) -> DriveFileEntry?,
  val downloadPayload: suspend (fileId: String) -> SyncPayload,
  val rebuildMergedPayload: suspend (latestRemoteMerged: SyncPayload) -> SyncPayload,
  val patchFile: suspend (fileId: String, encodedJson: String) -> Unit,
  val createFile: suspend (encodedJson: String) -> DriveFileEntry?,
  val deleteFile: suspend (fileId: String) -> Unit,
  val encodePayload: (SyncPayload) -> String,
  val ensureSessionActive: () -> Unit = {},
)

internal object DriveSyncUploadCoordinator {
  const val DEFAULT_MAX_RETRIES = 2

  suspend fun downloadAndMergeSyncFiles(
    resolution: SyncFileResolution,
    now: Long,
    downloadPayload: suspend (fileId: String) -> SyncPayload,
    ensureSessionActive: () -> Unit = {},
  ): RemoteSyncSnapshot? {
    val primary = resolution.primaryEntry ?: return null
    ensureSessionActive()
    var primaryPayload = downloadPayload(primary.id)
    val extraPayloads = mutableListOf<SyncPayload>()
    var refreshPrimaryAfterDup404 = false
    for (dup in resolution.duplicateEntries) {
      ensureSessionActive()
      try {
        extraPayloads += downloadPayload(dup.id)
      } catch (e: CancellationException) {
        throw e
      } catch (err: Throwable) {
        if (isNotFoundError(err)) {
          refreshPrimaryAfterDup404 = true
        } else {
          throw err
        }
      }
    }
    if (refreshPrimaryAfterDup404) {
      ensureSessionActive()
      primaryPayload = downloadPayload(primary.id)
    }
    val merged = SyncRemotePayloadMerger.mergePayloads(listOf(primaryPayload) + extraPayloads, now)
    return RemoteSyncSnapshot(
      primaryEntry = primary,
      duplicateEntries = resolution.duplicateEntries,
      mergedPayload = merged,
      duplicatePayloads = extraPayloads,
    )
  }

  suspend fun uploadWithVersionCheckAndCleanup(
    initialResolution: SyncFileResolution,
    initialMergedPayload: SyncPayload,
    initialRemoteSnapshot: RemoteSyncSnapshot?,
    transport: DriveUploadTransport,
    allowCreateIfMissing: Boolean = true,
    maxRetries: Int = DEFAULT_MAX_RETRIES,
  ): SyncUploadResult {
    val canonicalInitial = initialMergedPayload.canonicalized()
    if (initialResolution.primaryEntry == null || initialRemoteSnapshot == null) {
      if (!allowCreateIfMissing) {
        return SyncUploadResult(payload = canonicalInitial, syncedFileExists = false)
      }
      return createOrMergeIfAppeared(canonicalInitial, transport, maxRetries)
    }

    val hasDuplicates = initialResolution.duplicateEntries.isNotEmpty()
    val changed = SyncPayloadCanonicalizer.hasPayloadChanged(canonicalInitial, initialRemoteSnapshot.mergedPayload)
    if (!hasDuplicates && !changed) {
      return SyncUploadResult(payload = canonicalInitial, syncedFileExists = true)
    }

    return patchWithVersionCheck(
      initialSnapshot = initialRemoteSnapshot,
      initialPayload = canonicalInitial,
      transport = transport,
      allowCreateIfMissing = allowCreateIfMissing,
      maxRetries = maxRetries,
    )
  }

  private suspend fun createOrMergeIfAppeared(
    initialPayload: SyncPayload,
    transport: DriveUploadTransport,
    maxRetries: Int,
  ): SyncUploadResult {
    transport.ensureSessionActive()
    val preCreateResolution = transport.listSyncFiles()
    if (preCreateResolution.primaryEntry != null) {
      return mergeAndPatchResolution(preCreateResolution, transport, maxRetries)
    }

    transport.ensureSessionActive()
    transport.createFile(transport.encodePayload(initialPayload))
    transport.ensureSessionActive()
    val postCreateResolution = transport.listSyncFiles()
    if (postCreateResolution.primaryEntry != null && postCreateResolution.duplicateEntries.isNotEmpty()) {
      return mergeAndPatchResolution(postCreateResolution, transport, maxRetries)
    }
    return SyncUploadResult(payload = initialPayload, syncedFileExists = true)
  }

  private suspend fun mergeAndPatchResolution(
    resolution: SyncFileResolution,
    transport: DriveUploadTransport,
    maxRetries: Int,
  ): SyncUploadResult {
    val snapshot = downloadSnapshot(resolution, transport)
    val rebuilt = transport.rebuildMergedPayload(snapshot.mergedPayload).canonicalized()
    return patchWithVersionCheck(snapshot, rebuilt, transport, allowCreateIfMissing = true, maxRetries = maxRetries)
  }

  private suspend fun downloadSnapshot(
    resolution: SyncFileResolution,
    transport: DriveUploadTransport,
  ): RemoteSyncSnapshot =
    downloadAndMergeSyncFiles(
      resolution = resolution,
      now = transport.nowProvider(),
      downloadPayload = transport.downloadPayload,
      ensureSessionActive = transport.ensureSessionActive,
    )!!

  private suspend fun patchWithVersionCheck(
    initialSnapshot: RemoteSyncSnapshot,
    initialPayload: SyncPayload,
    transport: DriveUploadTransport,
    allowCreateIfMissing: Boolean,
    maxRetries: Int,
  ): SyncUploadResult {
    var primaryEntry = initialSnapshot.primaryEntry
    var duplicateEntries = initialSnapshot.duplicateEntries
    var duplicatePayloads = initialSnapshot.duplicatePayloads
    var currentRemote = initialSnapshot.mergedPayload
    var currentMerged = initialPayload.canonicalized()
    var expectedRevision = primaryEntry.revision
    var retriesUsed = 0

    while (true) {
      transport.ensureSessionActive()
      val latestMeta = transport.fetchMetadata(primaryEntry.id)
      if (latestMeta == null) {
        val relisted = transport.listSyncFiles()
        if (relisted.primaryEntry == null) {
          if (!allowCreateIfMissing) {
            return SyncUploadResult(payload = currentMerged, syncedFileExists = false)
          }
          if (retriesUsed >= maxRetries) throw DriveSyncConflictException()
          return createOrMergeIfAppeared(currentMerged, transport, maxRetries - retriesUsed)
        }
        if (retriesUsed >= maxRetries) throw DriveSyncConflictException()
        retriesUsed++
        val refreshed = downloadSnapshot(relisted, transport)
        primaryEntry = refreshed.primaryEntry
        duplicateEntries = refreshed.duplicateEntries
        duplicatePayloads = refreshed.duplicatePayloads
        currentRemote = refreshed.mergedPayload
        expectedRevision = primaryEntry.revision
        currentMerged = transport.rebuildMergedPayload(currentRemote).canonicalized()
        continue
      }

      if (expectedRevision.matches(latestMeta.revision)) {
        val needPatch =
          duplicateEntries.isNotEmpty() ||
            SyncPayloadCanonicalizer.hasPayloadChanged(currentMerged, currentRemote)
        if (needPatch) {
          transport.ensureSessionActive()
          transport.patchFile(primaryEntry.id, transport.encodePayload(currentMerged))
        }
        deleteDuplicatesBestEffort(duplicateEntries, transport)
        return SyncUploadResult(payload = currentMerged, syncedFileExists = true)
      }

      if (retriesUsed >= maxRetries) throw DriveSyncConflictException()
      retriesUsed++
      transport.ensureSessionActive()
      val latestPrimaryPayload =
        try {
          transport.downloadPayload(primaryEntry.id)
        } catch (e: CancellationException) {
          throw e
        } catch (err: Throwable) {
          if (isNotFoundError(err)) {
            expectedRevision = DriveFileRevision()
            continue
          }
          throw err
        }
      val mergedLatestRemote =
        SyncRemotePayloadMerger.mergePayloads(
          listOf(latestPrimaryPayload) + duplicatePayloads,
          transport.nowProvider(),
        )
      currentRemote = mergedLatestRemote
      expectedRevision = latestMeta.revision
      currentMerged = transport.rebuildMergedPayload(mergedLatestRemote).canonicalized()
    }
  }

  private suspend fun deleteDuplicatesBestEffort(
    duplicateEntries: List<DriveFileEntry>,
    transport: DriveUploadTransport,
  ) {
    for (dup in duplicateEntries) {
      transport.ensureSessionActive()
      try {
        transport.deleteFile(dup.id)
      } catch (e: CancellationException) {
        throw e
      } catch (err: Throwable) {
        if (err is SyncSessionInvalidatedException || DriveAuthRetry.isAuthExpiredError(err)) {
          throw err
        }
      }
    }
  }

  private fun isNotFoundError(err: Throwable): Boolean =
    (err as? DriveRemoteReadException)?.statusCode == 404 ||
      (err as? DriveHttpException)?.statusCode == 404
}
