package com.example.emptyapp.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.R
import com.example.emptyapp.data.annotation.AnnotationRepository
import com.example.emptyapp.data.annotation.BookmarkEntry
import com.example.emptyapp.data.annotation.HighlightEntry
import com.example.emptyapp.data.book.LibraryRepository
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface SyncActionResult {
  data object Synced : SyncActionResult
  data class NeedsConsent(val pendingIntent: PendingIntent) : SyncActionResult
  data class Error(val message: String) : SyncActionResult
}

class DriveRemoteReadException(message: String) : Exception(message)

class GoogleDriveSyncManager(
  private val context: Context,
  private val library: LibraryRepository,
  private val annotations: AnnotationRepository,
  private val stats: ReadingStatsRepository,
  private val tombstones: SyncTombstoneStore,
) {
  private val appContext = context.applicationContext
  private fun str(resId: Int): String = LocaleHelper.wrap(appContext).getString(resId)
  private fun str(resId: Int, vararg args: Any): String = LocaleHelper.wrap(appContext).getString(resId, *args)
  private val prefs = appContext.getSharedPreferences("eink_google_sync", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val mutex = Mutex()
  private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val backgroundSyncLock = Any()
  private var backgroundSyncJob: Job? = null
  private var backgroundSyncRequested = false
  @Volatile private var consecutiveBackgroundFailures = 0

  private fun loadSavedCloudBooks(): List<SyncedBookProgress> {
    val raw = prefs.getString(KEY_CLOUD_BOOKS_JSON, null) ?: return emptyList()
    return runCatching {
      json.decodeFromString(ListSerializer(SyncedBookProgress.serializer()), raw)
    }.getOrDefault(emptyList())
  }

  private fun saveCloudBooks(books: List<SyncedBookProgress>) {
    val encoded =
      runCatching {
        json.encodeToString(ListSerializer(SyncedBookProgress.serializer()), books)
      }.getOrNull() ?: return
    prefs.edit().putString(KEY_CLOUD_BOOKS_JSON, encoded).apply()
  }

  init {
    val legacyDeletedHashes = prefs.getStringSet(KEY_DELETED_HASHES, null)
    if (legacyDeletedHashes != null) {
      tombstones.markBooks(legacyDeletedHashes.toSet(), deleted = true)
      prefs.edit().remove(KEY_DELETED_HASHES).apply()
    }
  }

  fun unmarkDeletedHash(contentHash: String?) {
    if (contentHash.isNullOrBlank()) return
    backgroundScope.launch {
      if (contentHash in tombstones.deletedBookHashes()) {
        tombstones.markBooks(listOf(contentHash), deleted = false)
      }
    }
  }

  private val state =
    MutableStateFlow(
      SyncUiState(
        email = prefs.getString(KEY_EMAIL, null),
        displayName = prefs.getString(KEY_NAME, null),
        lastSyncedAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        syncBookFiles = prefs.getBoolean(KEY_SYNC_BOOKS, false),
        cloudBooks = loadSavedCloudBooks(),
      ),
    )

  val uiState: StateFlow<SyncUiState> = state.asStateFlow()

  fun setSyncBookFiles(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SYNC_BOOKS, enabled).apply()
    state.update { it.copy(syncBookFiles = enabled) }
  }

  suspend fun signInAndSync(): SyncActionResult {
    state.update { it.copy(syncing = true, statusMessage = str(R.string.sync_connecting)) }
    val authResult =
      runCatching { authorizeScopes() }.getOrElse { err ->
        val msg = err.localizedMessage ?: str(R.string.sync_play_services_error)
        state.update { it.copy(syncing = false, statusMessage = msg) }
        return SyncActionResult.Error(msg)
      }

    if (authResult.hasResolution()) {
      val pending = authResult.pendingIntent
      state.update { it.copy(syncing = false, statusMessage = null) }
      return if (pending != null) {
        SyncActionResult.NeedsConsent(pending)
      } else {
        SyncActionResult.Error(str(R.string.sync_consent_open_error))
      }
    }

    val token = authResult.accessToken
    if (token.isNullOrBlank()) {
      val msg = str(R.string.sync_no_token)
      state.update { it.copy(syncing = false, statusMessage = msg) }
      return SyncActionResult.Error(msg)
    }

    return completeSyncWithToken(token)
  }

  suspend fun handleConsentResult(data: Intent?): SyncActionResult {
    if (data == null) {
      val cancelled = str(R.string.sync_cancelled)
      state.update { it.copy(syncing = false, statusMessage = cancelled) }
      return SyncActionResult.Error(cancelled)
    }
    state.update { it.copy(syncing = true, statusMessage = str(R.string.sync_verifying)) }
    val authResult =
      runCatching {
        Identity.getAuthorizationClient(appContext).getAuthorizationResultFromIntent(data)
      }.getOrElse { err ->
        val msg = err.localizedMessage ?: str(R.string.sync_auth_failed)
        state.update { it.copy(syncing = false, statusMessage = msg) }
        return SyncActionResult.Error(msg)
      }
    val token = authResult.accessToken
    if (token.isNullOrBlank()) {
      val msg = str(R.string.sync_no_drive_permission)
      state.update { it.copy(syncing = false, statusMessage = msg) }
      return SyncActionResult.Error(msg)
    }
    return completeSyncWithToken(token)
  }

  fun syncInBackgroundIfSignedIn() {
    if (!state.value.isSignedIn) return
    synchronized(backgroundSyncLock) {
      backgroundSyncRequested = true
      if (backgroundSyncJob != null) return
      backgroundSyncJob =
        backgroundScope.launch {
          while (true) {
            val backoffMs =
              if (consecutiveBackgroundFailures <= 0) BACKGROUND_SYNC_DEBOUNCE_MS
              else (BACKGROUND_SYNC_DEBOUNCE_MS shl consecutiveBackgroundFailures.coerceAtMost(5))
                .coerceAtMost(MAX_BACKGROUND_BACKOFF_MS)
            delay(backoffMs)
            synchronized(backgroundSyncLock) { backgroundSyncRequested = false }
            runBackgroundSyncOnce()
            synchronized(backgroundSyncLock) {
              if (!backgroundSyncRequested) {
                backgroundSyncJob = null
                return@launch
              }
            }
          }
        }
    }
  }

  private suspend fun runBackgroundSyncOnce() {
    if (!state.value.isSignedIn) return
    val result =
      runCatching {
        val authResult = authorizeScopes()
        val token = authResult.accessToken
        if (!authResult.hasResolution() && !token.isNullOrBlank()) {
          completeSyncWithToken(token, allowBookFiles = isUnmeteredNetwork())
        } else {
          null
        }
      }.getOrNull()
    if (result is SyncActionResult.Synced) {
      consecutiveBackgroundFailures = 0
    } else if (result is SyncActionResult.Error) {
      consecutiveBackgroundFailures = (consecutiveBackgroundFailures + 1).coerceAtMost(5)
    }
  }

  private fun isUnmeteredNetwork(): Boolean {
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    return !cm.isActiveNetworkMetered
  }

  fun signOut() {
    val wasSignedIn = state.value.isSignedIn
    clearAccountState(statusMessage = null)
    if (!wasSignedIn) return
    backgroundScope.launch {
      runCatching {
        val authResult = authorizeScopes()
        val token = authResult.accessToken
        if (!authResult.hasResolution() && !token.isNullOrBlank()) revokeToken(token)
      }
    }
  }

  private fun clearAccountState(statusMessage: String?) {
    val keepSyncBooks = state.value.syncBookFiles
    prefs.edit().clear().putBoolean(KEY_SYNC_BOOKS, keepSyncBooks).apply()
    state.value = SyncUiState(syncBookFiles = keepSyncBooks, statusMessage = statusMessage)
    runCatching { Identity.getSignInClient(appContext).signOut() }
  }

  fun deleteBookFromCloudAsync(contentHash: String?, title: String? = null) {
    if (contentHash.isNullOrBlank()) return
    val remaining = state.value.cloudBooks.filterNot { it.contentHash == contentHash }
    saveCloudBooks(remaining)
    state.update { it.copy(cloudBooks = remaining) }
    val signedIn = state.value.isSignedIn
    backgroundScope.launch {
      tombstones.markBooks(listOf(contentHash), deleted = true)
      if (signedIn) deleteBookFromCloud(contentHash, title)
    }
  }

  suspend fun deleteBookFromCloud(contentHash: String, title: String? = null): SyncActionResult =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        tombstones.markBooks(listOf(contentHash), deleted = true)
        val remainingLocalCloud = state.value.cloudBooks.filterNot { it.contentHash == contentHash }
        saveCloudBooks(remainingLocalCloud)
        state.update {
          it.copy(
            syncing = true,
            cloudBooks = remainingLocalCloud,
            statusMessage = str(R.string.sync_deleting_cloud),
          )
        }
        runCatching {
          val authResult = authorizeScopes()
          val token = authResult.accessToken
          if (authResult.hasResolution() || token.isNullOrBlank()) {
            error(str(R.string.sync_no_token))
          }
          val appDataFiles = listAppDataFiles(token)
          for ((name, fileId) in appDataFiles) {
            if (name.startsWith("book_${contentHash}.")) {
              httpDeleteFile(token, fileId)
            }
          }
          val syncFileId = appDataFiles[SYNC_FILE_NAME]
          if (syncFileId != null) {
            val remote = downloadSyncPayload(token, syncFileId)
            tombstones.mergeRemote(remote.bookTombstones, remote.annotationTombstones)
            val tombstoneSnapshot = tombstones.snapshot()
            val updatedPayload =
              SyncTombstoneMerger.withoutDeleted(
                payload = remote,
                deletedBookHashes = tombstones.deletedBookHashes(),
                deletedAnnotationIds = tombstones.deletedAnnotationIds(),
              ).copy(
                updatedAt = System.currentTimeMillis(),
                bookTombstones = tombstoneSnapshot.books,
                annotationTombstones = tombstoneSnapshot.annotations,
              )
            val encoded = json.encodeToString(SyncPayload.serializer(), updatedPayload)
            uploadSyncJson(token, syncFileId, encoded)
            saveCloudBooks(updatedPayload.books)
            state.update { it.copy(cloudBooks = updatedPayload.books) }
          }
          val label = title?.takeIf { it.isNotBlank() } ?: contentHash.take(8)
          val doneMsg = str(R.string.sync_book_deleted_cloud, label)
          state.update { it.copy(syncing = false, statusMessage = doneMsg) }
          SyncActionResult.Synced
        }.getOrElse { err ->
          val msg = err.localizedMessage ?: str(R.string.sync_failed)
          state.update { it.copy(syncing = false, statusMessage = msg) }
          SyncActionResult.Error(msg)
        }
      }
    }

  suspend fun deleteAllCloudData(): SyncActionResult =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        state.update { it.copy(syncing = true, statusMessage = str(R.string.sync_deleting_cloud)) }
        runCatching {
          val authResult = authorizeScopes()
          val token = authResult.accessToken
          if (authResult.hasResolution() || token.isNullOrBlank()) {
            error(str(R.string.sync_no_token))
          }
          repeat(5) {
            val files = listAppDataFiles(token)
            if (files.isEmpty()) return@repeat
            for ((_, fileId) in files) {
              httpDeleteFile(token, fileId)
            }
          }
          tombstones.clear()
          runCatching { revokeToken(token) }
          clearAccountState(statusMessage = str(R.string.sync_all_cloud_deleted))
          SyncActionResult.Synced
        }.getOrElse { err ->
          val msg = err.localizedMessage ?: str(R.string.sync_failed)
          state.update { it.copy(syncing = false, statusMessage = msg) }
          SyncActionResult.Error(msg)
        }
      }
    }

  private suspend fun completeSyncWithToken(
    token: String,
    allowBookFiles: Boolean = true,
  ): SyncActionResult =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        runCatching {
          state.update { it.copy(syncing = true, statusMessage = str(R.string.sync_syncing_drive)) }
          if (state.value.email.isNullOrBlank()) {
            runCatching { fetchUserProfile(token) }
          }
          state.value.email?.let(tombstones::bindToAccount)
          library.ensureContentHashes()

          val rawAppDataFiles = listAppDataFiles(token)
          val syncFileId = rawAppDataFiles[SYNC_FILE_NAME]
          val rawRemote = syncFileId?.let { downloadSyncPayload(token, it) }
          if (rawRemote != null) {
            tombstones.mergeRemote(rawRemote.bookTombstones, rawRemote.annotationTombstones)
          }
          val deletedHashes = tombstones.deletedBookHashes()
          val deletedAnnotationIds = tombstones.deletedAnnotationIds()
          annotations.dropDeleted(deletedAnnotationIds)

          for ((name, fileId) in rawAppDataFiles) {
            if (deletedHashes.any { hash -> name.startsWith("book_${hash}.") }) {
              httpDeleteFile(token, fileId)
            }
          }
          val appDataFiles =
            rawAppDataFiles.filterKeys { name ->
              deletedHashes.none { hash -> name.startsWith("book_${hash}.") }
            }
          val remote =
            rawRemote?.let { SyncTombstoneMerger.withoutDeleted(it, deletedHashes, deletedAnnotationIds) }

          var failedBookUploads = 0
          if (state.value.syncBookFiles && allowBookFiles) {
            if (remote != null) {
              downloadMissingBooks(token, remote.books, appDataFiles, deletedHashes)
            }
            failedBookUploads = uploadMissingBooks(token, appDataFiles, deletedHashes)
          }

          if (remote != null) {
            mergeRemoteIntoLocal(remote)
          }

          val mergedPayload = buildLocalPayload(remote, deletedHashes, deletedAnnotationIds)
          val needsUpload =
            syncFileId == null ||
              remote == null ||
              mergedPayload.copy(updatedAt = 0L) != remote.copy(updatedAt = 0L)
          if (needsUpload) {
            val encoded = json.encodeToString(SyncPayload.serializer(), mergedPayload)
            uploadSyncJson(token, syncFileId, encoded)
          }
          saveCloudBooks(mergedPayload.books)
          consecutiveBackgroundFailures = 0

          val now = System.currentTimeMillis()
          prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
          val doneStatus =
            if (failedBookUploads > 0) str(R.string.sync_partial_book_upload_failed, failedBookUploads)
            else str(R.string.sync_success)
          state.update {
            it.copy(
              syncing = false,
              lastSyncedAt = now,
              cloudBooks = mergedPayload.books,
              statusMessage = doneStatus,
            )
          }
          SyncActionResult.Synced
        }.getOrElse { err ->
          val msg = err.localizedMessage ?: str(R.string.sync_failed)
          state.update { it.copy(syncing = false, statusMessage = msg) }
          SyncActionResult.Error(msg)
        }
      }
    }

  private suspend fun authorizeScopes(): AuthorizationResult {
    val request =
      AuthorizationRequest.builder()
        .setRequestedScopes(
          listOf(
            Scope(SCOPE_DRIVE_APPDATA),
            Scope(SCOPE_EMAIL),
          ),
        )
        .build()
    return Identity.getAuthorizationClient(appContext).authorize(request).await()
  }

  private fun fetchUserProfile(token: String) {
    val result = httpGet("https://www.googleapis.com/oauth2/v3/userinfo", token)
    val obj = runCatching { JSONObject(result) }.getOrNull() ?: return
    val email = obj.optString("email").takeIf { it.isNotBlank() }
    val name = obj.optString("name").takeIf { it.isNotBlank() }
    if (email != null) {
      prefs.edit().putString(KEY_EMAIL, email).putString(KEY_NAME, name).apply()
      state.update { it.copy(email = email, displayName = name) }
    }
  }

  private suspend fun mergeRemoteIntoLocal(remote: SyncPayload) {
    val progressByHash =
      remote.books.associate { item ->
        item.contentHash to (item.lastLocatorJson to (item.progress to item.updatedAt))
      }
    library.applySyncedProgress(progressByHash)

    val localByHash =
      library.books.value
        .mapNotNull { book -> book.contentHash?.let { hash -> hash to book.id } }
        .toMap()

    val mappedBookmarks =
      remote.bookmarks.mapNotNull { item ->
        val localBookId = localByHash[item.bookContentHash] ?: return@mapNotNull null
        BookmarkEntry(
          id = item.id,
          bookId = localBookId,
          title = item.title,
          progress = item.progress,
          locatorJson = item.locatorJson,
          createdAt = item.createdAt,
        )
      }
    val mappedHighlights =
      remote.highlights.mapNotNull { item ->
        val localBookId = localByHash[item.bookContentHash] ?: return@mapNotNull null
        HighlightEntry(
          id = item.id,
          bookId = localBookId,
          bookTitle = item.bookTitle,
          quote = item.quote,
          note = item.note,
          progress = item.progress,
          locatorJson = item.locatorJson,
          createdAt = item.createdAt,
        )
      }
    annotations.mergeFromRemote(mappedBookmarks, mappedHighlights)
    stats.mergeFromRemote(remote.stats)
  }

  private fun buildLocalPayload(
    remote: SyncPayload?,
    deletedHashes: Set<String>,
    deletedAnnotationIds: Set<String>,
  ): SyncPayload {
    val books = library.books.value.filter { it.contentHash != null && it.contentHash !in deletedHashes }
    val hashById = books.mapNotNull { book -> book.contentHash?.let { book.id to it } }.toMap()
    val localSyncedBooks =
      books.mapNotNull { book ->
        val hash = book.contentHash ?: return@mapNotNull null
        SyncedBookProgress(
          contentHash = hash,
          title = book.title,
          author = book.author,
          format = book.format,
          progress = book.progress,
          lastLocatorJson = book.lastLocatorJson,
          updatedAt = book.updatedAt,
        )
      }
    val booksByHash = LinkedHashMap<String, SyncedBookProgress>()
    localSyncedBooks.forEach { booksByHash[it.contentHash] = it }
    remote?.books
      ?.filterNot { it.contentHash in deletedHashes }
      ?.forEach { remoteBook ->
        booksByHash.putIfAbsent(remoteBook.contentHash, remoteBook)
      }
    val syncedBookmarks =
      annotations.bookmarks.value.mapNotNull { bm ->
        val hash = hashById[bm.bookId] ?: return@mapNotNull null
        SyncedBookmark(
          id = bm.id,
          bookContentHash = hash,
          title = bm.title,
          progress = bm.progress,
          locatorJson = bm.locatorJson,
          createdAt = bm.createdAt,
        )
      }
    val syncedHighlights =
      annotations.highlights.value.mapNotNull { hl ->
        val hash = hashById[hl.bookId] ?: return@mapNotNull null
        SyncedHighlight(
          id = hl.id,
          bookContentHash = hash,
          bookTitle = hl.bookTitle,
          quote = hl.quote,
          note = hl.note,
          progress = hl.progress,
          locatorJson = hl.locatorJson,
          createdAt = hl.createdAt,
        )
      }
    val localBookHashes = library.books.value.mapNotNull { it.contentHash }.toSet()
    val tombstoneSnapshot = tombstones.snapshot()
    return SyncPayload(
      version = 1,
      updatedAt = System.currentTimeMillis(),
      books = booksByHash.values.toList(),
      bookmarks =
        SyncAnnotationMerger.bookmarksToUpload(
          local = syncedBookmarks,
          remote = remote?.bookmarks.orEmpty(),
          localBookHashes = localBookHashes,
          deletedHashes = deletedHashes,
          deletedIds = deletedAnnotationIds,
        ),
      highlights =
        SyncAnnotationMerger.highlightsToUpload(
          local = syncedHighlights,
          remote = remote?.highlights.orEmpty(),
          localBookHashes = localBookHashes,
          deletedHashes = deletedHashes,
          deletedIds = deletedAnnotationIds,
        ),
      stats = stats.stats.value,
      bookTombstones = tombstoneSnapshot.books,
      annotationTombstones = tombstoneSnapshot.annotations,
    )
  }

  private fun listAppDataFiles(token: String): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    var pageToken: String? = null
    do {
      val url =
        buildString {
          append("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder")
          append("&pageSize=$LIST_PAGE_SIZE&fields=nextPageToken,files(id,name)")
          pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
        }
      val body = httpGet(url, token)
      val root = runCatching { JSONObject(body) }.getOrElse { throw remoteReadError("list") }
      val files = root.optJSONArray("files")
      if (files != null) {
        for (i in 0 until files.length()) {
          val obj = files.getJSONObject(i)
          val id = obj.optString("id")
          val name = obj.optString("name")
          if (id.isNotBlank() && name.isNotBlank()) {
            map[name] = id
          }
        }
      }
      pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
    } while (pageToken != null)
    return map
  }

  private suspend fun downloadMissingBooks(
    token: String,
    remoteBooks: List<SyncedBookProgress>,
    appDataFiles: Map<String, String>,
    deletedHashes: Set<String>,
  ) {
    val localHashes = library.books.value.mapNotNull { it.contentHash }.toHashSet()
    for (remoteBook in remoteBooks) {
      if (remoteBook.contentHash in localHashes || remoteBook.contentHash in deletedHashes) continue
      val driveFileName = bookDriveFileName(remoteBook.contentHash, remoteBook.format.extension)
      val driveFileId = appDataFiles[driveFileName] ?: continue
      state.update { it.copy(statusMessage = str(R.string.sync_downloading_book, remoteBook.title)) }
      val tempFile = File(appContext.cacheDir, "sync_${remoteBook.contentHash}.tmp")
      val downloaded = downloadFileTo(token, driveFileId, tempFile)
      if (downloaded) {
        library.importFromSyncedFile(
          tempFile = tempFile,
          title = remoteBook.title,
          author = remoteBook.author,
          format = remoteBook.format,
          contentHash = remoteBook.contentHash,
          progress = remoteBook.progress,
          lastLocatorJson = remoteBook.lastLocatorJson,
          updatedAt = remoteBook.updatedAt,
        )
        localHashes += remoteBook.contentHash
      }
      tempFile.delete()
    }
  }

  private fun uploadMissingBooks(
    token: String,
    appDataFiles: Map<String, String>,
    deletedHashes: Set<String>,
  ): Int {
    var failedCount = 0
    for (book in library.books.value) {
      val hash = book.contentHash ?: continue
      if (hash in deletedHashes) continue
      val driveFileName = bookDriveFileName(hash, book.format.extension)
      if (driveFileName in appDataFiles) continue
      val localFile = library.fileOf(book)
      if (!localFile.exists() || localFile.length() <= 0L || localFile.length() > MAX_SYNC_BOOK_BYTES) {
        continue
      }
      state.update { it.copy(statusMessage = str(R.string.sync_uploading_book, book.title)) }
      val uploaded = runCatching { uploadBinaryFileToAppData(token, driveFileName, localFile) }.isSuccess
      if (!uploaded) failedCount++
    }
    return failedCount
  }

  private fun downloadFileTo(token: String, fileId: String, target: File): Boolean {
    val conn =
      (URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("Authorization", "Bearer $token")
      }
    return try {
      if (conn.responseCode !in 200..299) return false
      conn.inputStream.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
      }
      true
    } catch (_: Exception) {
      false
    } finally {
      conn.disconnect()
    }
  }

  private fun uploadBinaryFileToAppData(token: String, driveFileName: String, file: File) {
    val boundary = "eink_book_${System.currentTimeMillis()}"
    val header =
      ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
        """{"name":"$driveFileName","parents":["appDataFolder"]}""" +
        "\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n")
        .toByteArray(StandardCharsets.UTF_8)
    val footer = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
    val totalLength = header.size.toLong() + file.length() + footer.size.toLong()

    val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
    val conn =
      (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 15_000
        readTimeout = 120_000
        setFixedLengthStreamingMode(totalLength)
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
      }
    try {
      conn.outputStream.use { out ->
        out.write(header)
        file.inputStream().use { input -> input.copyTo(out, bufferSize = 16 * 1024) }
        out.write(footer)
      }
      val code = conn.responseCode
      if (code !in 200..299) error("Drive book upload failed ($code)")
    } finally {
      conn.disconnect()
    }
  }

  private fun bookDriveFileName(hash: String, ext: String): String = "book_${hash}.$ext"

  private fun downloadSyncPayload(token: String, fileId: String): SyncPayload {
    val raw = httpGet("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", token)
    return runCatching { json.decodeFromString(SyncPayload.serializer(), raw) }
      .getOrElse { throw remoteReadError("parse") }
  }

  private fun remoteReadError(reason: String): DriveRemoteReadException =
    DriveRemoteReadException(str(R.string.sync_remote_read_failed, reason))

  private fun uploadSyncJson(token: String, existingFileId: String?, jsonBody: String) {
    if (existingFileId == null) {
      val boundary = "eink_sync_boundary_${System.currentTimeMillis()}"
      val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
      val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
      }
      val metadata = """{"name":"$SYNC_FILE_NAME","parents":["appDataFolder"]}"""
      val payload = ByteArrayOutputStream().apply {
        write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray(StandardCharsets.UTF_8))
        write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$jsonBody\r\n".toByteArray(StandardCharsets.UTF_8))
        write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
      }.toByteArray()
      try {
        conn.outputStream.use { it.write(payload) }
        val code = conn.responseCode
        if (code !in 200..299) error("Drive upload failed ($code)")
      } finally {
        conn.disconnect()
      }
    } else {
      val url = URL("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
      val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "PATCH"
        doOutput = true
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      }
      try {
        conn.outputStream.use { it.write(jsonBody.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        if (code !in 200..299) error("Drive update failed ($code)")
      } finally {
        conn.disconnect()
      }
    }
  }

  private fun httpDeleteFile(token: String, fileId: String): Boolean {
    val conn =
      (URL("https://www.googleapis.com/drive/v3/files/$fileId").openConnection() as HttpURLConnection).apply {
        requestMethod = "DELETE"
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Authorization", "Bearer $token")
      }
    return try {
      conn.responseCode in 200..299 || conn.responseCode == 404
    } catch (_: Exception) {
      false
    } finally {
      conn.disconnect()
    }
  }

  private fun revokeToken(token: String) {
    val body = "token=" + URLEncoder.encode(token, "UTF-8")
    val conn =
      (URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      }
    try {
      conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
      conn.responseCode
    } finally {
      conn.disconnect()
    }
  }

  private fun httpGet(urlString: String, token: String): String {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 10_000
      readTimeout = 15_000
      setRequestProperty("Authorization", "Bearer $token")
    }
    return try {
      val code = conn.responseCode
      if (code !in 200..299) throw remoteReadError("HTTP $code")
      conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
      addOnSuccessListener { result -> cont.resume(result) }
      addOnFailureListener { exc -> cont.resumeWithException(exc) }
    }

  private companion object {
    const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    const val SCOPE_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    const val SYNC_FILE_NAME = "eink_sync_v1.json"
    const val KEY_EMAIL = "email"
    const val KEY_NAME = "name"
    const val KEY_LAST_SYNC = "last_sync"
    const val KEY_SYNC_BOOKS = "sync_book_files"
    const val KEY_CLOUD_BOOKS_JSON = "cloud_books_json"
    const val KEY_DELETED_HASHES = "deleted_cloud_book_hashes"
    const val MAX_SYNC_BOOK_BYTES = 100L * 1024L * 1024L
    const val LIST_PAGE_SIZE = 1000
    const val BACKGROUND_SYNC_DEBOUNCE_MS = 20_000L
    const val MAX_BACKGROUND_BACKOFF_MS = 600_000L
  }
}
