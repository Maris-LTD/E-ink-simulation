package com.example.emptyapp.data.sync

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSyncUploadCoordinatorTest {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private fun book(hash: String, progress: Float = 0.5f, updatedAt: Long = 100L) =
    SyncedBookProgress(
      contentHash = hash,
      title = "Title $hash",
      progress = progress,
      lastLocatorJson = """{"p":$progress}""",
      updatedAt = updatedAt,
    )

  private fun bookmark(id: String, hash: String = "h1", createdAt: Long = 100L) =
    SyncedBookmark(
      id = id,
      bookContentHash = hash,
      title = id,
      progress = 0.2f,
      locatorJson = "{}",
      createdAt = createdAt,
    )

  @Test
  fun skipsMetadataCheckAndPatchWhenSingleFileCanonicalContentUnchanged() = runTest {
    val primary = DriveFileEntry("id-1", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "5")
    val resolution = SyncFileResolution(primary, emptyList())
    val remotePayload =
      SyncPayload(
        updatedAt = 100L,
        books = listOf(book("b2"), book("b1")),
        bookmarks = listOf(bookmark("bm2"), bookmark("bm1")),
      )
    val localPayload =
      SyncPayload(
        updatedAt = 999L,
        books = listOf(book("b1"), book("b2")),
        bookmarks = listOf(bookmark("bm1"), bookmark("bm2")),
      )
    val snapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(resolution, now = 1_000L, downloadPayload = { remotePayload })
    var metadataCalls = 0
    var patchCalls = 0

    val result =
      DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
        initialResolution = resolution,
        initialMergedPayload = localPayload,
        initialRemoteSnapshot = snapshot,
        transport =
          DriveUploadTransport(
            nowProvider = { 1_000L },
            listSyncFiles = { resolution },
            fetchMetadata = {
              metadataCalls++
              primary
            },
            downloadPayload = { remotePayload },
            rebuildMergedPayload = { it },
            patchFile = { _, _ -> patchCalls++ },
            createFile = { null },
            deleteFile = {},
            encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
          ),
      )

    assertTrue(result.syncedFileExists)
    assertEquals(0, metadataCalls)
    assertEquals(0, patchCalls)
    assertEquals(listOf("b1", "b2"), result.payload.books.map { it.contentHash })
  }

  @Test
  fun mergesDuplicateSyncFilesPatchesPrimaryFirstAndDeletesDuplicates() = runTest {
    val entries =
      listOf(
        DriveFileEntry("dup-new", "eink_sync_v1.json", createdTime = "2026-11-19T02:00:00Z", version = "1"),
        DriveFileEntry("primary-old", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "3"),
      )
    val resolution = SyncRemotePayloadMerger.resolveSyncFiles(entries, "eink_sync_v1.json")
    val payloads =
      mapOf(
        "primary-old" to SyncPayload(books = listOf(book("b-primary")), bookmarks = listOf(bookmark("bm-1"))),
        "dup-new" to SyncPayload(books = listOf(book("b-dup")), bookmarks = listOf(bookmark("bm-2"))),
      )
    val snapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(resolution, now = 1_000L, downloadPayload = { payloads.getValue(it) })!!
    val events = mutableListOf<String>()

    val result =
      DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
        initialResolution = resolution,
        initialMergedPayload = snapshot.mergedPayload,
        initialRemoteSnapshot = snapshot,
        transport =
          DriveUploadTransport(
            nowProvider = { 1_000L },
            listSyncFiles = { resolution },
            fetchMetadata = { id -> entries.first { it.id == id } },
            downloadPayload = { payloads.getValue(it) },
            rebuildMergedPayload = { it },
            patchFile = { id, encoded ->
              events += "patch:$id"
              val decoded = json.decodeFromString(SyncPayload.serializer(), encoded)
              assertEquals(listOf("b-dup", "b-primary"), decoded.books.map { it.contentHash })
              assertEquals(listOf("bm-1", "bm-2"), decoded.bookmarks.map { it.id })
            },
            createFile = { null },
            deleteFile = { id -> events += "delete:$id" },
            encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
          ),
      )

    assertTrue(result.syncedFileExists)
    assertEquals(listOf("patch:primary-old", "delete:dup-new"), events)
  }

  @Test
  fun doesNotDeleteDuplicatesIfPatchFails() = runTest {
    val entries =
      listOf(
        DriveFileEntry("primary", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "1"),
        DriveFileEntry("dup", "eink_sync_v1.json", createdTime = "2026-11-19T02:00:00Z", version = "1"),
      )
    val resolution = SyncRemotePayloadMerger.resolveSyncFiles(entries, "eink_sync_v1.json")
    val snapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(resolution, now = 1_000L, downloadPayload = { SyncPayload() })!!
    val deletedIds = mutableListOf<String>()

    val error =
      runCatching {
        DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
          initialResolution = resolution,
          initialMergedPayload = snapshot.mergedPayload,
          initialRemoteSnapshot = snapshot,
          transport =
            DriveUploadTransport(
              nowProvider = { 1_000L },
              listSyncFiles = { resolution },
              fetchMetadata = { entries.first { e -> e.id == it } },
              downloadPayload = { SyncPayload() },
              rebuildMergedPayload = { it },
              patchFile = { _, _ -> throw DriveHttpException(503, "unavailable") },
              createFile = { null },
              deleteFile = { deletedIds += it },
              encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
            ),
        )
      }.exceptionOrNull()

    assertTrue(error is DriveHttpException)
    assertTrue(deletedIds.isEmpty())
  }

  @Test
  fun detectsVersionChangeBeforePatchRedownloadsMergesAndRetries() = runTest {
    val initialEntry = DriveFileEntry("f1", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "1")
    val resolution = SyncFileResolution(initialEntry, emptyList())
    val initialRemote = SyncPayload(books = listOf(book("shared", progress = 0.2f, updatedAt = 100L)))
    val snapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(resolution, now = 1_000L, downloadPayload = { initialRemote })!!

    val localBookA = book("local-a", progress = 0.4f, updatedAt = 200L)
    val concurrentBookB = book("remote-b", progress = 0.9f, updatedAt = 300L)
    val initialMerged = SyncPayload(books = listOf(initialRemote.books.single(), localBookA))
    val driveVersion2 = SyncPayload(books = listOf(initialRemote.books.single(), concurrentBookB))

    var metadataReads = 0
    var rebuildCalls = 0
    var patchedPayload: SyncPayload? = null

    val result =
      DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
        initialResolution = resolution,
        initialMergedPayload = initialMerged,
        initialRemoteSnapshot = snapshot,
        transport =
          DriveUploadTransport(
            nowProvider = { 1_000L },
            listSyncFiles = { resolution },
            fetchMetadata = {
              metadataReads++
              initialEntry.copy(version = "2")
            },
            downloadPayload = { driveVersion2 },
            rebuildMergedPayload = { latestRemote ->
              rebuildCalls++
              SyncRemotePayloadMerger.mergePayloads(listOf(latestRemote, initialMerged), now = 1_000L)
            },
            patchFile = { _, encoded ->
              patchedPayload = json.decodeFromString(SyncPayload.serializer(), encoded)
            },
            createFile = { null },
            deleteFile = {},
            encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
          ),
      )

    assertEquals(2, metadataReads)
    assertEquals(1, rebuildCalls)
    assertEquals(listOf("local-a", "remote-b", "shared"), patchedPayload?.books?.map { it.contentHash })
    assertEquals(patchedPayload, result.payload)
  }

  @Test
  fun throwsConflictAfterMaxRetriesWhenVersionKeepsChanging() = runTest {
    val entry = DriveFileEntry("f1", "eink_sync_v1.json", createdTime = "2026-11-19T01:00:00Z", version = "1")
    val resolution = SyncFileResolution(entry, emptyList())
    val remote = SyncPayload(books = listOf(book("b1")))
    val snapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(resolution, now = 1_000L, downloadPayload = { remote })!!
    var currentVersion = 1
    var patchCalls = 0

    val error =
      runCatching {
        DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
          initialResolution = resolution,
          initialMergedPayload = remote.copy(books = listOf(book("b1"), book("b2"))),
          initialRemoteSnapshot = snapshot,
          maxRetries = 2,
          transport =
            DriveUploadTransport(
              nowProvider = { 1_000L },
              listSyncFiles = { resolution },
              fetchMetadata = { entry.copy(version = (++currentVersion).toString()) },
              downloadPayload = { remote },
              rebuildMergedPayload = { it.copy(books = it.books + book("b2")) },
              patchFile = { _, _ -> patchCalls++ },
              createFile = { null },
              deleteFile = {},
              encodePayload = { json.encodeToString(SyncPayload.serializer(), it) },
            ),
        )
      }.exceptionOrNull()

    assertTrue(error is DriveSyncConflictException)
    assertEquals(0, patchCalls)
  }
}
