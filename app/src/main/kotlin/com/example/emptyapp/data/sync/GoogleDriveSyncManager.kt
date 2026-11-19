package com.example.emptyapp.data.sync

import android.accounts.Account
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
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
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

class DriveRemoteReadException(
  message: String,
  val statusCode: Int? = null,
) : Exception(message)

class DriveHttpException(val statusCode: Int, message: String) : Exception(message)

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
      tombstones.markBooks(listOf(contentHash), deleted = false)
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
      try {
        authorizeScopes()
      } catch (e: CancellationException) {
        throw e
      } catch (err: Throwable) {
        val msg = formatSyncError(err)
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
        val msg = formatSyncError(err)
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
    var needsReconnectUpdate = false
    val result =
      try {
        val authResult = authorizeScopes()
        val token = authResult.accessToken
        if (authResult.hasResolution()) {
          needsReconnectUpdate = true
          state.update {
            it.copy(
              needsReconnect = true,
              statusMessage = str(R.string.sync_needs_reconnect),
            )
          }
          null
        } else if (!token.isNullOrBlank()) {
          completeSyncWithToken(
            token = token,
            allowBookFiles = isUnmeteredNetwork(),
            isBackground = true,
          )
        } else {
          null
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        null
      }
    if (result is SyncActionResult.Synced) {
      consecutiveBackgroundFailures = 0
    } else if (needsReconnectUpdate || result is SyncActionResult.Error || result is SyncActionResult.NeedsConsent) {
      consecutiveBackgroundFailures = (consecutiveBackgroundFailures + 1).coerceAtMost(5)
    }
  }

  private fun isUnmeteredNetwork(): Boolean {
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    return !cm.isActiveNetworkMetered
  }

  fun signOut() {
    val wasSignedIn = state.value.isSignedIn
    val accountEmail = state.value.email
    clearAccountState(statusMessage = null)
    if (!wasSignedIn) return
    backgroundScope.launch {
      val authResult =
        try {
          authorizeScopes()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Throwable) {
          null
        }
      val token = authResult?.accessToken
      if (authResult != null && !authResult.hasResolution() && !token.isNullOrBlank()) {
        revokeAndClearSession(token, authResult, accountEmail)
      } else {
        if (!token.isNullOrBlank()) clearCachedAccessToken(token)
        revokeClientAccess(authResult, accountEmail)
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
        val initial =
          when (val auth = authorizeForSignedInOperation()) {
            is SignedInAuth.Ready -> auth
            is SignedInAuth.EarlyReturn -> return@withContext auth.result
          }
        val outcome =
          DriveAuthRetry.executeWithSingle401Retry(
            initialToken = initial.token,
            clearToken = ::clearCachedAccessToken,
            authorize = { authorizeScopes().toTokenResponse() },
          ) { activeToken ->
            deleteBookWithToken(activeToken, contentHash)
          }
        finishRetryOutcome(outcome) { updatedBooks, _ ->
          val label = title?.takeIf { it.isNotBlank() } ?: contentHash.take(8)
          val doneMsg = str(R.string.sync_book_deleted_cloud, label)
          state.update {
            it.copy(
              syncing = false,
              needsReconnect = false,
              cloudBooks = updatedBooks ?: it.cloudBooks,
              statusMessage = doneMsg,
            )
          }
        }
      }
    }

  private suspend fun deleteBookWithToken(
    token: String,
    contentHash: String,
  ): List<SyncedBookProgress>? {
    resolveAndBindAccount(token)
    tombstones.markBooks(listOf(contentHash), deleted = true)
    val appDataFiles = listAppDataFiles(token)
    for ((name, fileId) in appDataFiles) {
      if (name.startsWith("book_${contentHash}.")) {
        httpDeleteFile(token, fileId)
      }
    }
    val syncFileId = appDataFiles[SYNC_FILE_NAME] ?: return null
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
    return updatedPayload.books
  }

  suspend fun deleteAllCloudData(): SyncActionResult =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        state.update { it.copy(syncing = true, statusMessage = str(R.string.sync_deleting_cloud)) }
        val initial =
          when (val auth = authorizeForSignedInOperation()) {
            is SignedInAuth.Ready -> auth
            is SignedInAuth.EarlyReturn -> return@withContext auth.result
          }
        val accountEmail = state.value.email
        var latestAuthResult = initial.authResult
        val outcome =
          DriveAuthRetry.executeWithSingle401Retry(
            initialToken = initial.token,
            clearToken = ::clearCachedAccessToken,
            authorize = {
              val auth = authorizeScopes()
              latestAuthResult = auth
              auth.toTokenResponse()
            },
          ) { activeToken ->
            repeat(5) {
              val files = listAppDataFiles(activeToken)
              if (files.isEmpty()) return@repeat
              for ((_, fileId) in files) {
                httpDeleteFile(activeToken, fileId)
              }
            }
          }
        finishRetryOutcome(outcome) { _, usedToken ->
          tombstones.clear()
          revokeAndClearSession(usedToken, latestAuthResult, accountEmail)
          clearAccountState(statusMessage = str(R.string.sync_all_cloud_deleted))
        }
      }
    }

  private data class SyncRunSummary(
    val books: List<SyncedBookProgress>,
    val failedBookUploads: Int,
  )

  private suspend fun completeSyncWithToken(
    token: String,
    allowBookFiles: Boolean = true,
    isBackground: Boolean = false,
  ): SyncActionResult =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        state.update { it.copy(syncing = true, statusMessage = str(R.string.sync_syncing_drive)) }
        val outcome =
          DriveAuthRetry.executeWithSingle401Retry(
            initialToken = token,
            clearToken = ::clearCachedAccessToken,
            authorize = { authorizeScopes().toTokenResponse() },
          ) { activeToken ->
            syncOnceWithToken(activeToken, allowBookFiles)
          }
        finishRetryOutcome(outcome, isBackground = isBackground) { summary, _ ->
          val now = System.currentTimeMillis()
          prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
          val doneStatus =
            if (summary.failedBookUploads > 0) {
              str(R.string.sync_partial_book_upload_failed, summary.failedBookUploads)
            } else {
              str(R.string.sync_success)
            }
          state.update {
            it.copy(
              syncing = false,
              needsReconnect = false,
              lastSyncedAt = now,
              cloudBooks = summary.books,
              statusMessage = doneStatus,
            )
          }
        }
      }
    }

  private suspend fun syncOnceWithToken(
    token: String,
    allowBookFiles: Boolean,
  ): SyncRunSummary {
    resolveAndBindAccount(token)
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
    return SyncRunSummary(books = mergedPayload.books, failedBookUploads = failedBookUploads)
  }

  private sealed interface SignedInAuth {
    data class Ready(val authResult: AuthorizationResult, val token: String) : SignedInAuth
    data class EarlyReturn(val result: SyncActionResult) : SignedInAuth
  }

  private suspend fun authorizeForSignedInOperation(): SignedInAuth {
    val authResult =
      try {
        authorizeScopes()
      } catch (e: CancellationException) {
        throw e
      } catch (err: Throwable) {
        val msg = formatSyncError(err)
        state.update {
          it.copy(
            syncing = false,
            needsReconnect = it.needsReconnect || isAuthExpiredError(err),
            statusMessage = msg,
          )
        }
        return SignedInAuth.EarlyReturn(SyncActionResult.Error(msg))
      }

    if (authResult.hasResolution()) {
      val msg = str(R.string.sync_needs_reconnect)
      state.update { it.copy(syncing = false, needsReconnect = true, statusMessage = msg) }
      val early =
        authResult.pendingIntent?.let(SyncActionResult::NeedsConsent)
          ?: SyncActionResult.Error(msg)
      return SignedInAuth.EarlyReturn(early)
    }

    val token = authResult.accessToken
    if (token.isNullOrBlank()) {
      val msg = str(R.string.sync_no_token)
      state.update { it.copy(syncing = false, statusMessage = msg) }
      return SignedInAuth.EarlyReturn(SyncActionResult.Error(msg))
    }

    return SignedInAuth.Ready(authResult, token)
  }

  private suspend fun <T> finishRetryOutcome(
    outcome: TokenRetryOutcome<T, PendingIntent>,
    isBackground: Boolean = false,
    onSuccess: suspend (T, String) -> Unit,
  ): SyncActionResult =
    when (outcome) {
      is TokenRetryOutcome.Succeeded -> {
        onSuccess(outcome.value, outcome.tokenUsed)
        SyncActionResult.Synced
      }
      is TokenRetryOutcome.NeedsResolution -> {
        val msg = str(R.string.sync_needs_reconnect)
        state.update {
          it.copy(
            syncing = false,
            needsReconnect = true,
            statusMessage = msg,
          )
        }
        outcome.resolution?.let(SyncActionResult::NeedsConsent) ?: SyncActionResult.Error(msg)
      }
      is TokenRetryOutcome.Failed -> {
        val msg =
          if (isBackground && outcome.authExpired) str(R.string.sync_needs_reconnect)
          else formatSyncError(outcome.error)
        state.update {
          it.copy(
            syncing = false,
            needsReconnect = it.needsReconnect || outcome.authExpired,
            statusMessage = msg,
          )
        }
        SyncActionResult.Error(msg)
      }
    }

  private fun requestedScopes(): List<Scope> =
    listOf(
      Scope(SCOPE_DRIVE_APPDATA),
      Scope(SCOPE_EMAIL),
    )

  private suspend fun authorizeScopes(): AuthorizationResult {
    val request =
      AuthorizationRequest.builder()
        .setRequestedScopes(requestedScopes())
        .build()
    return Identity.getAuthorizationClient(appContext).authorize(request).await()
  }

  private fun AuthorizationResult.toTokenResponse(): AuthTokenResponse<PendingIntent> =
    AuthTokenResponse(
      accessToken = accessToken,
      hasResolution = hasResolution(),
      resolution = pendingIntent,
    )

  private suspend fun clearCachedAccessToken(token: String) {
    if (token.isBlank()) return
    withContext(Dispatchers.IO) {
      try {
        Identity.getAuthorizationClient(appContext)
          .clearToken(ClearTokenRequest.builder().setToken(token).build())
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
      }
      try {
        GoogleAuthUtil.clearToken(appContext, token)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
      }
    }
  }

  private suspend fun revokeClientAccess(
    authResult: AuthorizationResult?,
    accountEmail: String?,
  ) {
    val account =
      authResult?.toGoogleSignInAccount()?.account
        ?: accountEmail?.takeIf { it.isNotBlank() }?.let { Account(it, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE) }
        ?: return
    withContext(Dispatchers.IO) {
      try {
        val request =
          RevokeAccessRequest.builder()
            .setScopes(requestedScopes())
            .setAccount(account)
            .build()
        Identity.getAuthorizationClient(appContext).revokeAccess(request).await()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
      }
    }
  }

  private suspend fun revokeAndClearSession(
    token: String,
    authResult: AuthorizationResult?,
    accountEmail: String?,
  ) {
    if (token.isNotBlank()) {
      try {
        revokeToken(token)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
      }
      clearCachedAccessToken(token)
    }
    revokeClientAccess(authResult, accountEmail)
  }

  private suspend fun resolveAndBindAccount(token: String): String {
    val cachedAccountId =
      state.value.email?.takeIf { it.isNotBlank() }
        ?: prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }
    return SyncAccountResolver.resolveAndBindAccount(
      cachedAccountId = cachedAccountId,
      fetchUserInfoJson = { httpGet("https://www.googleapis.com/oauth2/v3/userinfo", token) },
      onProfileResolved = { profile ->
        prefs.edit()
          .putString(KEY_EMAIL, profile.accountId)
          .putString(KEY_NAME, profile.displayName)
          .apply()
        state.update { it.copy(email = profile.accountId, displayName = profile.displayName) }
      },
      bindToAccount = tombstones::bindToAccount,
      onUnresolvedAccount = { remoteReadError("userinfo") },
    )
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
      try {
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
      } finally {
        tempFile.delete()
      }
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
      val uploadErr = runCatching { uploadBinaryFileToAppData(token, driveFileName, localFile) }.exceptionOrNull()
      if (uploadErr != null) {
        if (isAuthExpiredError(uploadErr)) throw uploadErr
        failedCount++
      }
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
      val code = conn.responseCode
      if (code == 401) throw DriveHttpException(401, "HTTP 401")
      if (code !in 200..299) return false
      conn.inputStream.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
      }
      true
    } catch (e: DriveHttpException) {
      throw e
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
      if (code !in 200..299) throw DriveHttpException(code, "Drive book upload failed ($code)")
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

  private fun remoteReadError(reason: String, statusCode: Int? = null): DriveRemoteReadException =
    DriveRemoteReadException(str(R.string.sync_remote_read_failed, reason), statusCode)

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
        if (code !in 200..299) throw DriveHttpException(code, "Drive upload failed ($code)")
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
        if (code !in 200..299) throw DriveHttpException(code, "Drive update failed ($code)")
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
      val code = conn.responseCode
      if (code == 401) throw DriveHttpException(401, "HTTP 401")
      code in 200..299 || code == 404
    } catch (e: DriveHttpException) {
      throw e
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
      if (code == 401) throw DriveHttpException(401, "HTTP 401")
      if (code !in 200..299) throw remoteReadError("HTTP $code", statusCode = code)
      conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  private fun isAuthExpiredError(err: Throwable): Boolean = DriveAuthRetry.isAuthExpiredError(err)

  private fun formatSyncError(err: Throwable): String =
    when (err) {
      is DriveRemoteReadException -> err.message ?: str(R.string.sync_failed)
      is UnknownHostException, is ConnectException -> str(R.string.sync_error_offline)
      is SocketTimeoutException -> str(R.string.sync_error_timeout)
      is DriveHttpException ->
        when (err.statusCode) {
          401 -> str(R.string.sync_error_auth_expired)
          403, 507 -> str(R.string.sync_error_quota_or_permission)
          429, in 500..599 -> str(R.string.sync_error_server, err.statusCode)
          else -> str(R.string.sync_error_http, err.statusCode)
        }
      is com.google.android.gms.common.api.ApiException -> str(R.string.sync_play_services_error)
      else -> str(R.string.sync_failed)
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
