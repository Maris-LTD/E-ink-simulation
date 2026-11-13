package com.example.emptyapp.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface SyncActionResult {
  data object Synced : SyncActionResult
  data class NeedsConsent(val pendingIntent: PendingIntent) : SyncActionResult
  data class Error(val message: String) : SyncActionResult
}

class GoogleDriveSyncManager(
  private val context: Context,
  private val library: LibraryRepository,
  private val annotations: AnnotationRepository,
  private val stats: ReadingStatsRepository,
) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences("eink_google_sync", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val mutex = Mutex()
  private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val state =
    MutableStateFlow(
      SyncUiState(
        email = prefs.getString(KEY_EMAIL, null),
        displayName = prefs.getString(KEY_NAME, null),
        lastSyncedAt = prefs.getLong(KEY_LAST_SYNC, 0L),
        syncBookFiles = prefs.getBoolean(KEY_SYNC_BOOKS, true),
      ),
    )

  val uiState: StateFlow<SyncUiState> = state.asStateFlow()

  fun setSyncBookFiles(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SYNC_BOOKS, enabled).apply()
    state.update { it.copy(syncBookFiles = enabled) }
  }

  suspend fun signInAndSync(): SyncActionResult {
    state.update { it.copy(syncing = true, statusMessage = "Đang kết nối Google...") }
    val authResult =
      runCatching { authorizeScopes() }.getOrElse { err ->
        val msg = err.localizedMessage ?: "Không thể kết nối Google Play Services"
        state.update { it.copy(syncing = false, statusMessage = msg) }
        return SyncActionResult.Error(msg)
      }

    if (authResult.hasResolution()) {
      val pending = authResult.pendingIntent
      state.update { it.copy(syncing = false, statusMessage = null) }
      return if (pending != null) {
        SyncActionResult.NeedsConsent(pending)
      } else {
        SyncActionResult.Error("Không mở được màn hình đăng nhập Google")
      }
    }

    val token = authResult.accessToken
    if (token.isNullOrBlank()) {
      val msg = "Không lấy được access token từ Google"
      state.update { it.copy(syncing = false, statusMessage = msg) }
      return SyncActionResult.Error(msg)
    }

    return completeSyncWithToken(token)
  }

  suspend fun handleConsentResult(data: Intent?): SyncActionResult {
    if (data == null) {
      state.update { it.copy(syncing = false, statusMessage = "Đã hủy đăng nhập Google") }
      return SyncActionResult.Error("Đã hủy đăng nhập Google")
    }
    state.update { it.copy(syncing = true, statusMessage = "Đang xác thực...") }
    val authResult =
      runCatching {
        Identity.getAuthorizationClient(appContext).getAuthorizationResultFromIntent(data)
      }.getOrElse { err ->
        val msg = err.localizedMessage ?: "Xác thực Google thất bại"
        state.update { it.copy(syncing = false, statusMessage = msg) }
        return SyncActionResult.Error(msg)
      }
    val token = authResult.accessToken
    if (token.isNullOrBlank()) {
      val msg = "Không nhận được quyền Google Drive"
      state.update { it.copy(syncing = false, statusMessage = msg) }
      return SyncActionResult.Error(msg)
    }
    return completeSyncWithToken(token)
  }

  fun syncInBackgroundIfSignedIn() {
    if (!state.value.isSignedIn) return
    backgroundScope.launch {
      runCatching {
        val authResult = authorizeScopes()
        val token = authResult.accessToken
        if (!authResult.hasResolution() && !token.isNullOrBlank()) {
          completeSyncWithToken(token)
        }
      }
    }
  }

  fun signOut() {
    val keepSyncBooks = state.value.syncBookFiles
    prefs.edit().clear().putBoolean(KEY_SYNC_BOOKS, keepSyncBooks).apply()
    state.value = SyncUiState(syncBookFiles = keepSyncBooks)
    runCatching { Identity.getSignInClient(appContext).signOut() }
  }

  private suspend fun completeSyncWithToken(token: String): SyncActionResult =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        runCatching {
          state.update { it.copy(syncing = true, statusMessage = "Đang đồng bộ Drive...") }
          fetchUserProfile(token)
          library.ensureContentHashes()

          val appDataFiles = listAppDataFiles(token)
          val syncFileId = appDataFiles[SYNC_FILE_NAME]
          val remote = syncFileId?.let { downloadSyncPayload(token, it) }

          if (state.value.syncBookFiles) {
            if (remote != null) {
              downloadMissingBooks(token, remote.books, appDataFiles)
            }
            uploadMissingBooks(token, appDataFiles)
          }

          if (remote != null) {
            mergeRemoteIntoLocal(remote)
          }

          val mergedPayload = buildLocalPayload()
          val encoded = json.encodeToString(SyncPayload.serializer(), mergedPayload)
          uploadSyncJson(token, syncFileId, encoded)

          val now = System.currentTimeMillis()
          prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
          state.update {
            it.copy(
              syncing = false,
              lastSyncedAt = now,
              statusMessage = "Đồng bộ thành công",
            )
          }
          SyncActionResult.Synced
        }.getOrElse { err ->
          val msg = err.localizedMessage ?: "Đồng bộ thất bại"
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
    if (result != null) {
      val obj = runCatching { JSONObject(result) }.getOrNull() ?: return
      val email = obj.optString("email").takeIf { it.isNotBlank() }
      val name = obj.optString("name").takeIf { it.isNotBlank() }
      if (email != null) {
        prefs.edit().putString(KEY_EMAIL, email).putString(KEY_NAME, name).apply()
        state.update { it.copy(email = email, displayName = name) }
      }
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

  private fun buildLocalPayload(): SyncPayload {
    val books = library.books.value
    val hashById = books.mapNotNull { book -> book.contentHash?.let { book.id to it } }.toMap()
    val syncedBooks =
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
    return SyncPayload(
      version = 1,
      updatedAt = System.currentTimeMillis(),
      books = syncedBooks,
      bookmarks = syncedBookmarks,
      highlights = syncedHighlights,
      stats = stats.stats.value,
    )
  }

  private fun listAppDataFiles(token: String): Map<String, String> {
    val url =
      "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder" +
        "&pageSize=200&fields=files(id,name)"
    val body = httpGet(url, token) ?: return emptyMap()
    val files = JSONObject(body).optJSONArray("files") ?: return emptyMap()
    val map = LinkedHashMap<String, String>(files.length())
    for (i in 0 until files.length()) {
      val obj = files.getJSONObject(i)
      val id = obj.optString("id")
      val name = obj.optString("name")
      if (id.isNotBlank() && name.isNotBlank()) {
        map[name] = id
      }
    }
    return map
  }

  private suspend fun downloadMissingBooks(
    token: String,
    remoteBooks: List<SyncedBookProgress>,
    appDataFiles: Map<String, String>,
  ) {
    val localHashes = library.books.value.mapNotNull { it.contentHash }.toHashSet()
    for (remoteBook in remoteBooks) {
      if (remoteBook.contentHash in localHashes) continue
      val driveFileName = bookDriveFileName(remoteBook.contentHash, remoteBook.format.extension)
      val driveFileId = appDataFiles[driveFileName] ?: continue
      state.update { it.copy(statusMessage = "Đang tải sách: ${remoteBook.title}") }
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
  ) {
    for (book in library.books.value) {
      val hash = book.contentHash ?: continue
      val driveFileName = bookDriveFileName(hash, book.format.extension)
      if (driveFileName in appDataFiles) continue
      val localFile = library.fileOf(book)
      if (!localFile.exists() || localFile.length() <= 0L || localFile.length() > MAX_SYNC_BOOK_BYTES) {
        continue
      }
      state.update { it.copy(statusMessage = "Đang tải lên: ${book.title}") }
      uploadBinaryFileToAppData(token, driveFileName, localFile)
    }
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

  private fun downloadSyncPayload(token: String, fileId: String): SyncPayload? {
    val raw = httpGet("https://www.googleapis.com/drive/v3/files/$fileId?alt=media", token) ?: return null
    return runCatching { json.decodeFromString(SyncPayload.serializer(), raw) }.getOrNull()
  }

  private fun uploadSyncJson(token: String, existingFileId: String?, jsonBody: String) {
    if (existingFileId == null) {
      val boundary = "eink_sync_boundary_${System.currentTimeMillis()}"
      val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
      val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
      }
      val metadata = """{"name":"$SYNC_FILE_NAME","parents":["appDataFolder"]}"""
      val payload = ByteArrayOutputStream().apply {
        write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray(StandardCharsets.UTF_8))
        write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$jsonBody\r\n".toByteArray(StandardCharsets.UTF_8))
        write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
      }.toByteArray()
      conn.outputStream.use { it.write(payload) }
      val code = conn.responseCode
      conn.disconnect()
      if (code !in 200..299) error("Drive upload failed ($code)")
    } else {
      val url = URL("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
      val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "PATCH"
        doOutput = true
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
      }
      conn.outputStream.use { it.write(jsonBody.toByteArray(StandardCharsets.UTF_8)) }
      val code = conn.responseCode
      conn.disconnect()
      if (code !in 200..299) error("Drive update failed ($code)")
    }
  }

  private fun httpGet(urlString: String, token: String): String? {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 10_000
      readTimeout = 15_000
      setRequestProperty("Authorization", "Bearer $token")
    }
    return try {
      val code = conn.responseCode
      if (code in 200..299) {
        conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
      } else {
        null
      }
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
    const val MAX_SYNC_BOOK_BYTES = 100L * 1024L * 1024L
  }
}
