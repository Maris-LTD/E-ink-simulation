package com.example.emptyapp.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSyncUploadRaceTest {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private fun book(hash: String, updatedAt: Long = 100L) =
    SyncedBookProgress(
      contentHash = hash,
      title = "Title $hash",
      progress = 0.5f,
      lastLocatorJson = """{"p":0.5}""",
      updatedAt = updatedAt,
    )

  @Test
  fun preCreateCheckMergesIntoExistingFileInsteadOfCreatingDuplicate() = runTest {
    val emptyResolution = SyncFileResolution(null, emptyList())
    val existing = DriveFileEntry("existing", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "1")
    val existingPayload = SyncPayload(books = listOf(book("from-other")))
    val localPayload = SyncPayload(books = listOf(book("from-local")))

    var createCalls = 0
    var patchedId: String? = null
    var patchedPayload: SyncPayload? = null

    val result =
      DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
        initialResolution = emptyResolution,
        initialMergedPayload = localPayload,
        initialRemoteSnapshot = null,
        transport =
          DriveUploadTransport(
            nowProvider = { 1_000L },
            listSyncFiles = { SyncFileResolution(existing, emptyList()) },
            fetchMetadata = { existing },
            downloadPayload = { existingPayload },
            rebuildMergedPayload = { remote ->
              SyncRemotePayloadMerger.mergePayloads(listOf(remote, localPayload), now = 1_000L)
            },
            patchFile = { id, encoded ->
              patchedId = id
              patchedPayload = json.decodeFromString(SyncPayload.serializer(), encoded)
            },
            createFile = {
              createCalls++
              null
            },
            deleteFile = {},
            encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
          ),
      )

    assertEquals(0, createCalls)
    assertEquals("existing", patchedId)
    assertEquals(listOf("from-local", "from-other"), patchedPayload?.books?.map { it.contentHash })
    assertEquals(patchedPayload, result.payload)
  }

  @Test
  fun postCreateCheckMergesSimultaneousCreatesAndDeletesNewerDuplicate() = runTest {
    val emptyResolution = SyncFileResolution(null, emptyList())
    val fileA = DriveFileEntry("a-old", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "1")
    val fileB = DriveFileEntry("b-new", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:01Z", version = "1")
    val payloadA = SyncPayload(books = listOf(book("from-a")))
    val payloadB = SyncPayload(books = listOf(book("from-b")))

    var listCalls = 0
    var createCalls = 0
    var patchedId: String? = null
    val deletedIds = mutableListOf<String>()

    val result =
      DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
        initialResolution = emptyResolution,
        initialMergedPayload = payloadB,
        initialRemoteSnapshot = null,
        transport =
          DriveUploadTransport(
            nowProvider = { 1_000L },
            listSyncFiles = {
              listCalls++
              if (listCalls == 1) emptyResolution
              else SyncRemotePayloadMerger.resolveSyncFiles(listOf(fileB, fileA), "eink_sync_v1.json")
            },
            fetchMetadata = { fileA },
            downloadPayload = { id -> if (id == "a-old") payloadA else payloadB },
            rebuildMergedPayload = { remote ->
              SyncRemotePayloadMerger.mergePayloads(listOf(remote, payloadB), now = 1_000L)
            },
            patchFile = { id, _ -> patchedId = id },
            createFile = {
              createCalls++
              fileB
            },
            deleteFile = { deletedIds += it },
            encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
          ),
      )

    assertEquals(1, createCalls)
    assertEquals("a-old", patchedId)
    assertEquals(listOf("b-new"), deletedIds)
    assertEquals(listOf("from-a", "from-b"), result.payload.books.map { it.contentHash })
  }

  @Test
  fun refreshesPrimaryWhenDuplicateReturns404DuringMerge() = runTest {
    val primary = DriveFileEntry("primary", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "1")
    val dup = DriveFileEntry("dup", "eink_sync_v1.json", createdTime = "2026-11-19T02:00:00Z", version = "1")
    val resolution = SyncFileResolution(primary, listOf(dup))
    var primaryDownloads = 0

    val snapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(
        resolution = resolution,
        now = 1_000L,
        downloadPayload = { id ->
          if (id == "dup") throw DriveRemoteReadException("HTTP 404", statusCode = 404)
          primaryDownloads++
          if (primaryDownloads == 1) SyncPayload(books = listOf(book("b1")))
          else SyncPayload(books = listOf(book("b1"), book("b-from-dup")))
        },
      )!!

    assertEquals(2, primaryDownloads)
    assertEquals(listOf("b-from-dup", "b1"), snapshot.mergedPayload.books.map { it.contentHash })
  }

  @Test
  fun handlesMissingPrimaryWhenCreateDisallowedAndPropagatesCancellationAnd401() = runTest {
    val entry = DriveFileEntry("f1", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "1")
    val dup = DriveFileEntry("f2", "eink_sync_v1.json", createdTime = "2026-11-19T02:00:00Z", version = "1")
    val resolution = SyncFileResolution(entry, emptyList())
    val remote = SyncPayload(books = listOf(book("b1")))
    val snapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(resolution, now = 1_000L, downloadPayload = { remote })!!
    var createCalls = 0

    val missingResult =
      DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
        initialResolution = resolution,
        initialMergedPayload = SyncPayload(),
        initialRemoteSnapshot = snapshot,
        allowCreateIfMissing = false,
        transport =
          DriveUploadTransport(
            nowProvider = { 1_000L },
            listSyncFiles = { SyncFileResolution(null, emptyList()) },
            fetchMetadata = { null },
            downloadPayload = { remote },
            rebuildMergedPayload = { it },
            patchFile = { _, _ -> },
            createFile = {
              createCalls++
              null
            },
            deleteFile = {},
            encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
          ),
      )

    assertFalse(missingResult.syncedFileExists)
    assertEquals(0, createCalls)

    val dupResolution = SyncFileResolution(entry, listOf(dup))
    val dupSnapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(dupResolution, now = 1_000L, downloadPayload = { remote })!!
    val authErr =
      runCatching {
        DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
          initialResolution = dupResolution,
          initialMergedPayload = remote,
          initialRemoteSnapshot = dupSnapshot,
          transport =
            DriveUploadTransport(
              nowProvider = { 1_000L },
              listSyncFiles = { dupResolution },
              fetchMetadata = { entry },
              downloadPayload = { remote },
              rebuildMergedPayload = { it },
              patchFile = { _, _ -> },
              createFile = { null },
              deleteFile = { throw DriveHttpException(401, "HTTP 401") },
              encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
            ),
        )
      }.exceptionOrNull()
    assertTrue(authErr is DriveHttpException)

    var cancelled = false
    try {
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(
        resolution = resolution,
        now = 1_000L,
        downloadPayload = { throw CancellationException("cancelled") },
      )
    } catch (_: CancellationException) {
      cancelled = true
    }
    assertTrue(cancelled)
  }
}
