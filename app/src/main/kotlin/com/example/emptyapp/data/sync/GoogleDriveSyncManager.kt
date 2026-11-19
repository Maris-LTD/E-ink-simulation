package com.example.emptyapp.data.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.activity.result.IntentSenderRequest
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
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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
  cause: Throwable? = null,
) : Exception(message, cause)

class DriveHttpException(val statusCode: Int, message: String) : Exception(message)

class GoogleDriveSyncManager(
  private val context: Context,
  private val library: LibraryRepository,
  private val annotations: AnnotationRepository,
  private val stats: ReadingStatsRepository,
  private val tombstones: SyncTombstoneStore,
  private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  private val appContext = context.applicationContext
  private fun str(resId: Int): String = LocaleHelper.wrap(appContext).getString(resId)
  private fun str(resId: Int, vararg args: Any): String = LocaleHelper.wrap(appContext).getString(resId, *args)
  private val prefs = appContext.getSharedPreferences("eink_google_sync", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val mutex = Mutex()
  private val sessionGuard = SyncSessionGuard()
  private val backgroundSyncLock = Any()
  private var backgroundSyncJob: Job? = null
  private var backgroundSyncRequested = false
  @Volatile private var consecutiveBackgroundFailures = 0
  @Volatile private var pendingConsentAllowBookFiles: Boolean? = null

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

  private val legacyMigrationJob: Job? =
    prefs.getStringSet(KEY_DELETED_HASHES, null)?.toSet()?.let { legacyDeletedHashes ->
      backgroundScope.launch(Dispatchers.IO) {
        tombstones.awaitLoaded()
        tombstones.markBooks(legacyDeletedHashes, deleted = true)
        prefs.edit().remove(KEY_DELETED_HASHES).apply()
      }
    }

  fun unmarkDeletedHash(contentHash: String?) {
    if (contentHash.isNullOrBlank()) return
    backgroundScope.launch(Dispatchers.IO) {
      legacyMigrationJob?.join()
      tombstones.awaitLoaded()
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

  fun shouldConfirmMeteredBookSync(): Boolean =
    state.value.syncBookFiles && isMeteredNetwork()

  fun launchSignInAndSync(
    allowBookFiles: Boolean = state.value.syncBookFiles,
    onNeedConsent: (IntentSenderRequest) -> Unit = {},
  ) {
    backgroundScope.launch {
      val result = signInAndSync(allowBookFiles = allowBookFiles)
      if (result is SyncActionResult.NeedsConsent) {
        val request = IntentSenderRequest.Builder(result.pendingIntent).build()
        withContext(Dispatchers.Main.immediate) {
          runCatching { onNeedConsent(request) }
        }
      }
    }
  }

  fun launchHandleConsentResult(
    data: Intent?,
    allowBookFiles: Boolean = pendingConsentAllowBookFiles ?: state.value.syncBookFiles,
  ) {
    backgroundScope.launch {
      handleConsentResult(data, allowBookFiles = allowBookFiles)
    }
  }

  fun launchDeleteAllCloudData() {
    backgroundScope.launch {
      deleteAllCloudData()
    }
  }

  suspend fun signInAndSync(
    allowBookFiles: Boolean = state.value.syncBookFiles,
  ): SyncActionResult {
    pendingConsentAllowBookFiles = allowBookFiles
    val signInGeneration =
      if (!state.value.isSignedIn) sessionGuard.advanceGeneration() else sessionGuard.currentGeneration()
    updateStateIfActive(signInGeneration) {
      it.copy(syncing = true, statusMessage = str(R.string.sync_connecting))
    }
    sessionGuard.awaitPendingSignOut()
    if (!sessionGuard.isCurrent(signInGeneration)) {
      return SyncActionResult.Error(str(R.string.sync_cancelled))
    }
    val authResult =
      try {
        authorizeScopes()
      } catch (e: CancellationException) {
        throw e
      } catch (err: Throwable) {
        val msg = formatSyncError(err)
        updateStateIfActive(signInGeneration) { it.copy(syncing = false, statusMessage = msg) }
        return SyncActionResult.Error(msg)
      }

    if (!sessionGuard.isCurrent(signInGeneration)) {
      return SyncActionResult.Error(str(R.string.sync_cancelled))
    }

    if (authResult.hasResolution()) {
      val pending = authResult.pendingIntent
      updateStateIfActive(signInGeneration) { it.copy(syncing = false, statusMessage = null) }
      return if (pending != null) {
        SyncActionResult.NeedsConsent(pending)
      } else {
        SyncActionResult.Error(str(R.string.sync_consent_open_error))
      }
    }

    val token = authResult.accessToken
    if (token.isNullOrBlank()) {
      val msg = str(R.string.sync_no_token)
      updateStateIfActive(signInGeneration) { it.copy(syncing = false, statusMessage = msg) }
      return SyncActionResult.Error(msg)
    }

    return completeSyncWithToken(
      token = token,
      allowBookFiles = allowBookFiles,
      expectedGeneration = signInGeneration,
    )
  }

  suspend fun handleConsentResult(
    data: Intent?,
    allowBookFiles: Boolean = pendingConsentAllowBookFiles ?: state.value.syncBookFiles,
  ): SyncActionResult {
    pendingConsentAllowBookFiles = null
    val signInGeneration =
      if (!state.value.isSignedIn) sessionGuard.advanceGeneration() else sessionGuard.currentGeneration()
    if (data == null) {
      val cancelled = str(R.string.sync_cancelled)
      updateStateIfActive(signInGeneration) { it.copy(syncing = false, statusMessage = cancelled) }
      return SyncActionResult.Error(cancelled)
    }
    updateStateIfActive(signInGeneration) {
      it.copy(syncing = true, statusMessage = str(R.string.sync_verifying))
    }
    sessionGuard.awaitPendingSignOut()
    if (!sessionGuard.isCurrent(signInGeneration)) {
      return SyncActionResult.Error(str(R.string.sync_cancelled))
    }
    val authResult =
      runCatching {
        Identity.getAuthorizationClient(appContext).getAuthorizationResultFromIntent(data)
      }.getOrElse { err ->
        if (err is CancellationException) throw err
        val msg = formatSyncError(err)
        updateStateIfActive(signInGeneration) { it.copy(syncing = false, statusMessage = msg) }
        return SyncActionResult.Error(msg)
      }
    if (!sessionGuard.isCurrent(signInGeneration)) {
      return SyncActionResult.Error(str(R.string.sync_cancelled))
    }
    val token = authResult.accessToken
    if (token.isNullOrBlank()) {
      val msg = str(R.string.sync_no_drive_permission)
      updateStateIfActive(signInGeneration) { it.copy(syncing = false, statusMessage = msg) }
      return SyncActionResult.Error(msg)
    }
    return completeSyncWithToken(
      token = token,
      allowBookFiles = allowBookFiles,
      expectedGeneration = signInGeneration,
    )
  }

  fun syncInBackgroundIfSignedIn() {
    if (!state.value.isSignedIn) return
    synchronized(backgroundSyncLock) {
      val scheduledGeneration = sessionGuard.currentGeneration()
      backgroundSyncRequested = true
      if (backgroundSyncJob?.isActive == true) return
      val job =
        backgroundScope.launch(start = CoroutineStart.LAZY) {
          val self = coroutineContext[Job]
          try {
            while (true) {
              val backoffMs =
                if (consecutiveBackgroundFailures <= 0) BACKGROUND_SYNC_DEBOUNCE_MS
                else (BACKGROUND_SYNC_DEBOUNCE_MS shl consecutiveBackgroundFailures.coerceAtMost(5))
                  .coerceAtMost(MAX_BACKGROUND_BACKOFF_MS)
              delay(backoffMs)
              synchronized(backgroundSyncLock) {
                if (!isSessionActive(scheduledGeneration, requireSignedIn = true)) {
                  backgroundSyncRequested = false
                  return@launch
                }
                backgroundSyncRequested = false
              }
              runBackgroundSyncOnce(scheduledGeneration)
              synchronized(backgroundSyncLock) {
                if (!backgroundSyncRequested || !isSessionActive(scheduledGeneration, requireSignedIn = true)) {
                  return@launch
                }
              }
            }
          } finally {
            synchronized(backgroundSyncLock) {
              if (backgroundSyncJob === self) {
                backgroundSyncJob = null
              }
            }
          }
        }
      backgroundSyncJob = job
      job.start()
    }
  }

  private fun cancelBackgroundSync() {
    synchronized(backgroundSyncLock) {
      backgroundSyncRequested = false
      backgroundSyncJob?.cancel()
      backgroundSyncJob = null
    }
    consecutiveBackgroundFailures = 0
  }

  private suspend fun runBackgroundSyncOnce(scheduledGeneration: Long) {
    if (!isSessionActive(scheduledGeneration, requireSignedIn = true)) return
    var needsReconnectUpdate = false
    val result =
      try {
        val authResult = authorizeScopes()
        if (!isSessionActive(scheduledGeneration, requireSignedIn = true)) return
        val token = authResult.accessToken
        if (authResult.hasResolution()) {
          needsReconnectUpdate = true
          updateStateIfActive(scheduledGeneration, requireSignedIn = true) {
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
            expectedGeneration = scheduledGeneration,
          )
        } else {
          null
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        null
      }
    if (!isSessionActive(scheduledGeneration, requireSignedIn = true)) return
    if (result is SyncActionResult.Synced) {
      consecutiveBackgroundFailures = 0
    } else if (needsReconnectUpdate || result is SyncActionResult.Error || result is SyncActionResult.NeedsConsent) {
      consecutiveBackgroundFailures = (consecutiveBackgroundFailures + 1).coerceAtMost(5)
    }
  }

  private fun isMeteredNetwork(): Boolean {
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    return cm.isActiveNetworkMetered
  }

  private fun isUnmeteredNetwork(): Boolean = !isMeteredNetwork()

  fun signOut() {
    val signOutGeneration = sessionGuard.advanceGeneration()
    cancelBackgroundSync()
    val wasSignedIn = state.value.isSignedIn
    val accountEmail = state.value.email
    clearAccountPrefs()
    resetAccountUiState(statusMessage = null)
    if (!wasSignedIn) return
    val job =
      backgroundScope.launch(start = CoroutineStart.LAZY) {
        try {
          sessionGuard.executeSignOutLocked(
            mutex = mutex,
            signOutGeneration = signOutGeneration,
            wasSignedIn = true,
            revokeSession = {
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
            },
            identitySignOut = { signOutIdentityClient() },
            clearAccountPrefs = { clearAccountPrefs() },
            resetUiStateIfStillCurrent = { resetAccountUiState(statusMessage = null) },
          )
        } finally {
          coroutineContext[Job]?.let(sessionGuard::clearSignOutJob)
        }
      }
    sessionGuard.registerSignOutJob(job)
    job.start()
  }

  private fun clearAccountPrefs() {
    prefs.edit()
      .remove(KEY_EMAIL)
      .remove(KEY_NAME)
      .remove(KEY_LAST_SYNC)
      .remove(KEY_CLOUD_BOOKS_JSON)
      .remove(KEY_DELETED_HASHES)
      .apply()
  }

  private fun resetAccountUiState(statusMessage: String?) {
    state.update {
      it.copy(
        email = null,
        displayName = null,
        lastSyncedAt = 0L,
        syncing = false,
        needsReconnect = false,
        statusMessage = statusMessage,
        cloudBooks = emptyList(),
      )
    }
  }

  fun deleteBookFromCloudAsync(contentHash: String?, title: String? = null) {
    if (contentHash.isNullOrBlank()) return
    val expectedGeneration = sessionGuard.currentGeneration()
    val signedIn = state.value.isSignedIn
    if (signedIn) {
      val remaining = state.value.cloudBooks.filterNot { it.contentHash == contentHash }
      saveCloudBooks(remaining)
      updateStateIfActive(expectedGeneration, requireSignedIn = true) {
        it.copy(cloudBooks = remaining)
      }
    }
    backgroundScope.launch(Dispatchers.IO) {
      if (!sessionGuard.isCurrent(expectedGeneration)) return@launch
      legacyMigrationJob?.join()
      tombstones.awaitLoaded()
      tombstones.markBooks(listOf(contentHash), deleted = true)
      if (signedIn && isSessionActive(expectedGeneration, requireSignedIn = true)) {
        deleteBookFromCloud(contentHash, title, expectedGeneration)
      }
    }
  }

  suspend fun deleteBookFromCloud(contentHash: String, title: String? = null): SyncActionResult =
    deleteBookFromCloud(contentHash, title, sessionGuard.currentGeneration())

  private suspend fun deleteBookFromCloud(
    contentHash: String,
    title: String?,
    expectedGeneration: Long,
  ): SyncActionResult =
    withContext(Dispatchers.IO) {
      val lockedResult =
        sessionGuard.runWithSessionLock(
          mutex = mutex,
          expectedGeneration = expectedGeneration,
          isSessionStillAllowed = { state.value.isSignedIn },
        ) {
          tombstones.markBooks(listOf(contentHash), deleted = true)
          val remainingLocalCloud = state.value.cloudBooks.filterNot { it.contentHash == contentHash }
          saveCloudBooks(remainingLocalCloud)
          updateStateIfActive(expectedGeneration, requireSignedIn = true) {
            it.copy(
              syncing = true,
              cloudBooks = remainingLocalCloud,
              statusMessage = str(R.string.sync_deleting_cloud),
            )
          }
          val initial =
            when (val auth = authorizeForSignedInOperation(expectedGeneration)) {
              is SignedInAuth.Ready -> auth
              is SignedInAuth.EarlyReturn -> return@runWithSessionLock auth.result
            }
          val outcome =
            DriveAuthRetry.executeWithSingle401Retry(
              initialToken = initial.token,
              clearToken = ::clearCachedAccessToken,
              authorize = {
                ensureSessionActive(expectedGeneration, requireSignedIn = true)
                authorizeScopes().toTokenResponse()
              },
            ) { activeToken ->
              deleteBookWithToken(activeToken, contentHash, expectedGeneration)
            }
          finishRetryOutcome(
            outcome = outcome,
            expectedGeneration = expectedGeneration,
            requireSignedIn = true,
          ) { updatedBooks, _ ->
            val label = title?.takeIf { it.isNotBlank() } ?: contentHash.take(8)
            val doneMsg = str(R.string.sync_book_deleted_cloud, label)
            updateStateIfActive(expectedGeneration, requireSignedIn = true) {
              it.copy(
                syncing = false,
                needsReconnect = false,
                cloudBooks = updatedBooks ?: it.cloudBooks,
                statusMessage = doneMsg,
              )
            }
          }
        }
      when (lockedResult) {
        is SessionLockedResult.Executed -> lockedResult.value
        SessionLockedResult.StaleSession -> SyncActionResult.Error(str(R.string.sync_cancelled))
      }
    }

  private suspend fun deleteBookWithToken(
    token: String,
    contentHash: String,
    expectedGeneration: Long,
  ): List<SyncedBookProgress>? {
    legacyMigrationJob?.join()
    tombstones.awaitLoaded()
    resolveAndBindAccount(token, expectedGeneration, requireSignedIn = true)
    ensureSessionActive(expectedGeneration, requireSignedIn = true)
    tombstones.markBooks(listOf(contentHash), deleted = true)
    val entries = listAppDataFileEntries(token)
    for (entry in entries) {
      if (entry.name.startsWith("book_${contentHash}.")) {
        ensureSessionActive(expectedGeneration, requireSignedIn = true)
        httpDeleteFile(token, entry.id)
      }
    }
    val syncResolution = SyncRemotePayloadMerger.resolveSyncFiles(entries, SYNC_FILE_NAME)
    if (syncResolution.primaryEntry == null) return null
    val remoteSnapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(
        resolution = syncResolution,
        now = System.currentTimeMillis(),
        downloadPayload = { fileId -> downloadSyncPayload(token, fileId) },
        ensureSessionActive = { ensureSessionActive(expectedGeneration, requireSignedIn = true) },
      ) ?: return null

    fun buildDeletePayload(remoteMerged: SyncPayload): SyncPayload {
      ensureSessionActive(expectedGeneration, requireSignedIn = true)
      tombstones.mergeRemote(remoteMerged.bookTombstones, remoteMerged.annotationTombstones)
      val tombstoneSnapshot = tombstones.snapshot()
      return SyncTombstoneMerger.withoutDeleted(
        payload = remoteMerged,
        deletedBookHashes = tombstones.deletedBookHashes(),
        deletedAnnotationIds = tombstones.deletedAnnotationIds(),
      ).copy(
        updatedAt = System.currentTimeMillis(),
        bookTombstones = tombstoneSnapshot.books,
        annotationTombstones = tombstoneSnapshot.annotations,
      ).canonicalized()
    }

    val initialUpdatedPayload = buildDeletePayload(remoteSnapshot.mergedPayload)
    val transport =
      createUploadTransport(
        token = token,
        expectedGeneration = expectedGeneration,
        requireSignedIn = true,
        rebuildMergedPayload = ::buildDeletePayload,
      )
    val uploadResult =
      DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
        initialResolution = syncResolution,
        initialMergedPayload = initialUpdatedPayload,
        initialRemoteSnapshot = remoteSnapshot,
        transport = transport,
        allowCreateIfMissing = false,
      )
    ensureSessionActive(expectedGeneration, requireSignedIn = true)
    if (!uploadResult.syncedFileExists) return null
    saveCloudBooks(uploadResult.payload.books)
    return uploadResult.payload.books
  }

  suspend fun deleteAllCloudData(): SyncActionResult {
    val expectedGeneration = sessionGuard.currentGeneration()
    return withContext(Dispatchers.IO) {
      val lockedResult =
        sessionGuard.runWithSessionLock(
          mutex = mutex,
          expectedGeneration = expectedGeneration,
          isSessionStillAllowed = { state.value.isSignedIn },
        ) {
          updateStateIfActive(expectedGeneration, requireSignedIn = true) {
            it.copy(syncing = true, statusMessage = str(R.string.sync_deleting_cloud))
          }
          val initial =
            when (val auth = authorizeForSignedInOperation(expectedGeneration)) {
              is SignedInAuth.Ready -> auth
              is SignedInAuth.EarlyReturn -> return@runWithSessionLock auth.result
            }
          val accountEmail = state.value.email
          var latestAuthResult = initial.authResult
          val outcome =
            DriveAuthRetry.executeWithSingle401Retry(
              initialToken = initial.token,
              clearToken = ::clearCachedAccessToken,
              authorize = {
                ensureSessionActive(expectedGeneration, requireSignedIn = true)
                val auth = authorizeScopes()
                latestAuthResult = auth
                auth.toTokenResponse()
              },
            ) { activeToken ->
              DriveWipeCoordinator.wipeAll(
                maxPasses = MAX_WIPE_PASSES,
                listFileIds = { listAppDataFileEntries(activeToken).map { it.id } },
                deleteFile = { fileId -> httpDeleteFile(activeToken, fileId) },
                ensureSessionActive = { ensureSessionActive(expectedGeneration, requireSignedIn = true) },
              )
            }
          finishRetryOutcome(
            outcome = outcome,
            expectedGeneration = expectedGeneration,
            requireSignedIn = true,
          ) { _, usedToken ->
            val wipeGeneration = sessionGuard.advanceGeneration()
            cancelBackgroundSync()
            legacyMigrationJob?.join()
            tombstones.clear()
            try {
              revokeAndClearSession(usedToken, latestAuthResult, accountEmail)
            } catch (e: CancellationException) {
              throw e
            } catch (_: Throwable) {
            }
            signOutIdentityClient()
            clearAccountPrefs()
            if (sessionGuard.isCurrent(wipeGeneration)) {
              resetAccountUiState(statusMessage = str(R.string.sync_all_cloud_deleted))
            }
          }
        }
      when (lockedResult) {
        is SessionLockedResult.Executed -> lockedResult.value
        SessionLockedResult.StaleSession -> SyncActionResult.Error(str(R.string.sync_cancelled))
      }
    }
  }

  private data class SyncRunSummary(
    val books: List<SyncedBookProgress>,
    val failedBookDownloads: Int,
    val failedBookUploads: Int,
  )

  private suspend fun completeSyncWithToken(
    token: String,
    allowBookFiles: Boolean = true,
    isBackground: Boolean = false,
    expectedGeneration: Long = sessionGuard.currentGeneration(),
  ): SyncActionResult =
    withContext(Dispatchers.IO) {
      val lockedResult =
        sessionGuard.runWithSessionLock(
          mutex = mutex,
          expectedGeneration = expectedGeneration,
          isSessionStillAllowed = { !isBackground || state.value.isSignedIn },
        ) {
          updateStateIfActive(expectedGeneration, requireSignedIn = isBackground) {
            it.copy(syncing = true, statusMessage = str(R.string.sync_syncing_drive))
          }
          val outcome =
            DriveAuthRetry.executeWithSingle401Retry(
              initialToken = token,
              clearToken = ::clearCachedAccessToken,
              authorize = {
                ensureSessionActive(expectedGeneration, requireSignedIn = isBackground)
                authorizeScopes().toTokenResponse()
              },
            ) { activeToken ->
              syncOnceWithToken(activeToken, allowBookFiles, expectedGeneration, isBackground)
            }
          finishRetryOutcome(
            outcome = outcome,
            expectedGeneration = expectedGeneration,
            requireSignedIn = isBackground,
            isBackground = isBackground,
          ) { summary, _ ->
            if (!isSessionActive(expectedGeneration, requireSignedIn = isBackground)) return@finishRetryOutcome
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
            val totalFailedBookFiles = summary.failedBookDownloads + summary.failedBookUploads
            val doneStatus =
              if (totalFailedBookFiles > 0) {
                str(R.string.sync_partial_book_upload_failed, totalFailedBookFiles)
              } else {
                str(R.string.sync_success)
              }
            updateStateIfActive(expectedGeneration, requireSignedIn = isBackground) {
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
      when (lockedResult) {
        is SessionLockedResult.Executed -> lockedResult.value
        SessionLockedResult.StaleSession -> SyncActionResult.Error(str(R.string.sync_cancelled))
      }
    }

  private suspend fun syncOnceWithToken(
    token: String,
    allowBookFiles: Boolean,
    expectedGeneration: Long,
    isBackground: Boolean,
  ): SyncRunSummary {
    legacyMigrationJob?.join()
    tombstones.awaitLoaded()
    library.awaitLoaded()
    annotations.awaitLoaded()
    stats.awaitLoaded()
    resolveAndBindAccount(token, expectedGeneration, requireSignedIn = isBackground)
    ensureSessionActive(expectedGeneration, requireSignedIn = isBackground)
    library.ensureContentHashes()

    val allEntries = listAppDataFileEntries(token)
    ensureSessionActive(expectedGeneration, requireSignedIn = isBackground)
    val syncResolution = SyncRemotePayloadMerger.resolveSyncFiles(allEntries, SYNC_FILE_NAME)
    val remoteSnapshot =
      DriveSyncUploadCoordinator.downloadAndMergeSyncFiles(
        resolution = syncResolution,
        now = System.currentTimeMillis(),
        downloadPayload = { fileId -> downloadSyncPayload(token, fileId) },
        ensureSessionActive = { ensureSessionActive(expectedGeneration, requireSignedIn = isBackground) },
      )
    val rawRemote = remoteSnapshot?.mergedPayload
    ensureSessionActive(expectedGeneration, requireSignedIn = isBackground)
    if (rawRemote != null) {
      tombstones.mergeRemote(rawRemote.bookTombstones, rawRemote.annotationTombstones)
    }
    val deletedHashes = tombstones.deletedBookHashes()
    val deletedAnnotationIds = tombstones.deletedAnnotationIds()
    annotations.dropDeleted(deletedAnnotationIds)

    for (entry in allEntries) {
      if (deletedHashes.any { hash -> entry.name.startsWith("book_${hash}.") }) {
        ensureSessionActive(expectedGeneration, requireSignedIn = isBackground)
        try {
          httpDeleteFile(token, entry.id)
        } catch (e: CancellationException) {
          throw e
        } catch (err: Throwable) {
          if (isAuthExpiredError(err)) throw err
        }
      }
    }
    val appDataFiles = LinkedHashMap<String, String>()
    for (entry in allEntries) {
      val name = entry.name
      if (name.isBlank() || name == SYNC_FILE_NAME) continue
      if (deletedHashes.any { hash -> name.startsWith("book_${hash}.") }) continue
      appDataFiles.putIfAbsent(name, entry.id)
    }
    val remote =
      rawRemote?.let {
        SyncTombstoneMerger.withoutDeleted(it, deletedHashes, deletedAnnotationIds).canonicalized()
      }

    var failedBookDownloads = 0
    var failedBookUploads = 0
    if (state.value.syncBookFiles && allowBookFiles) {
      if (remote != null) {
        failedBookDownloads =
          downloadMissingBooks(token, remote.books, appDataFiles, deletedHashes, expectedGeneration, isBackground)
      }
      failedBookUploads = uploadMissingBooks(token, appDataFiles, deletedHashes, expectedGeneration, isBackground)
    }

    ensureSessionActive(expectedGeneration, requireSignedIn = isBackground)
    if (remote != null) {
      mergeRemoteIntoLocal(remote)
    }

    val mergedPayload = buildLocalPayload(remote, deletedHashes, deletedAnnotationIds).canonicalized()
    val transport =
      createUploadTransport(
        token = token,
        expectedGeneration = expectedGeneration,
        requireSignedIn = isBackground,
        rebuildMergedPayload = { latestRemoteMerged ->
          ensureSessionActive(expectedGeneration, requireSignedIn = isBackground)
          tombstones.mergeRemote(latestRemoteMerged.bookTombstones, latestRemoteMerged.annotationTombstones)
          val latestDeletedHashes = tombstones.deletedBookHashes()
          val latestDeletedAnnotationIds = tombstones.deletedAnnotationIds()
          annotations.dropDeleted(latestDeletedAnnotationIds)
          val filteredLatestRemote =
            SyncTombstoneMerger.withoutDeleted(
              latestRemoteMerged,
              latestDeletedHashes,
              latestDeletedAnnotationIds,
            ).canonicalized()
          mergeRemoteIntoLocal(filteredLatestRemote)
          buildLocalPayload(filteredLatestRemote, latestDeletedHashes, latestDeletedAnnotationIds)
        },
      )
    val uploadResult =
      DriveSyncUploadCoordinator.uploadWithVersionCheckAndCleanup(
        initialResolution = syncResolution,
        initialMergedPayload = mergedPayload,
        initialRemoteSnapshot = remoteSnapshot,
        transport = transport,
        allowCreateIfMissing = true,
      )
    ensureSessionActive(expectedGeneration, requireSignedIn = isBackground)
    saveCloudBooks(uploadResult.payload.books)
    consecutiveBackgroundFailures = 0
    return SyncRunSummary(
      books = uploadResult.payload.books,
      failedBookDownloads = failedBookDownloads,
      failedBookUploads = failedBookUploads,
    )
  }

  private fun isSessionActive(expectedGeneration: Long, requireSignedIn: Boolean): Boolean =
    sessionGuard.isCurrent(expectedGeneration) && (!requireSignedIn || state.value.isSignedIn)

  private fun ensureSessionActive(expectedGeneration: Long, requireSignedIn: Boolean) {
    if (!isSessionActive(expectedGeneration, requireSignedIn)) {
      throw SyncSessionInvalidatedException()
    }
  }

  private inline fun updateStateIfActive(
    expectedGeneration: Long,
    requireSignedIn: Boolean = false,
    crossinline transform: (SyncUiState) -> SyncUiState,
  ) {
    state.update { current ->
      if (!sessionGuard.isCurrent(expectedGeneration)) return@update current
      if (requireSignedIn && !current.isSignedIn) return@update current
      transform(current)
    }
  }

  private sealed interface SignedInAuth {
    data class Ready(val authResult: AuthorizationResult, val token: String) : SignedInAuth
    data class EarlyReturn(val result: SyncActionResult) : SignedInAuth
  }

  private suspend fun authorizeForSignedInOperation(expectedGeneration: Long): SignedInAuth {
    val authResult =
      try {
        authorizeScopes()
      } catch (e: CancellationException) {
        throw e
      } catch (err: Throwable) {
        val msg = formatSyncError(err)
        updateStateIfActive(expectedGeneration, requireSignedIn = true) {
          it.copy(
            syncing = false,
            needsReconnect = it.needsReconnect || isAuthExpiredError(err),
            statusMessage = msg,
          )
        }
        return SignedInAuth.EarlyReturn(SyncActionResult.Error(msg))
      }

    if (!isSessionActive(expectedGeneration, requireSignedIn = true)) {
      return SignedInAuth.EarlyReturn(SyncActionResult.Error(str(R.string.sync_cancelled)))
    }

    if (authResult.hasResolution()) {
      val msg = str(R.string.sync_needs_reconnect)
      updateStateIfActive(expectedGeneration, requireSignedIn = true) {
        it.copy(syncing = false, needsReconnect = true, statusMessage = msg)
      }
      val early =
        authResult.pendingIntent?.let(SyncActionResult::NeedsConsent)
          ?: SyncActionResult.Error(msg)
      return SignedInAuth.EarlyReturn(early)
    }

    val token = authResult.accessToken
    if (token.isNullOrBlank()) {
      val msg = str(R.string.sync_no_token)
      updateStateIfActive(expectedGeneration, requireSignedIn = true) {
        it.copy(syncing = false, statusMessage = msg)
      }
      return SignedInAuth.EarlyReturn(SyncActionResult.Error(msg))
    }

    return SignedInAuth.Ready(authResult, token)
  }

  private suspend fun <T> finishRetryOutcome(
    outcome: TokenRetryOutcome<T, PendingIntent>,
    expectedGeneration: Long,
    requireSignedIn: Boolean = false,
    isBackground: Boolean = false,
    onSuccess: suspend (T, String) -> Unit,
  ): SyncActionResult {
    if (!isSessionActive(expectedGeneration, requireSignedIn)) {
      return SyncActionResult.Error(str(R.string.sync_cancelled))
    }
    return when (outcome) {
      is TokenRetryOutcome.Succeeded -> {
        onSuccess(outcome.value, outcome.tokenUsed)
        SyncActionResult.Synced
      }
      is TokenRetryOutcome.NeedsResolution -> {
        val msg = str(R.string.sync_needs_reconnect)
        updateStateIfActive(expectedGeneration, requireSignedIn) {
          it.copy(
            syncing = false,
            needsReconnect = true,
            statusMessage = msg,
          )
        }
        outcome.resolution?.let(SyncActionResult::NeedsConsent) ?: SyncActionResult.Error(msg)
      }
      is TokenRetryOutcome.Failed -> {
        if (outcome.error is SyncSessionInvalidatedException) {
          return SyncActionResult.Error(str(R.string.sync_cancelled))
        }
        val msg =
          if (isBackground && outcome.authExpired) str(R.string.sync_needs_reconnect)
          else formatSyncError(outcome.error)
        updateStateIfActive(expectedGeneration, requireSignedIn) {
          it.copy(
            syncing = false,
            needsReconnect = it.needsReconnect || outcome.authExpired,
            statusMessage = msg,
          )
        }
        SyncActionResult.Error(msg)
      }
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
  ): Boolean {
    var tokenRevoked = true
    if (token.isNotBlank()) {
      tokenRevoked =
        withContext(Dispatchers.IO) {
          try {
            revokeToken(token)
          } catch (e: CancellationException) {
            throw e
          } catch (_: Throwable) {
            false
          }
        }
      clearCachedAccessToken(token)
    }
    revokeClientAccess(authResult, accountEmail)
    return tokenRevoked
  }

  private suspend fun signOutIdentityClient() {
    withContext(Dispatchers.IO) {
      try {
        withTimeoutOrNull(IDENTITY_SIGNOUT_TIMEOUT_MS) {
          Identity.getSignInClient(appContext).signOut().await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
      }
    }
  }

  private suspend fun resolveAndBindAccount(
    token: String,
    expectedGeneration: Long,
    requireSignedIn: Boolean,
  ): String {
    ensureSessionActive(expectedGeneration, requireSignedIn)
    val cachedAccountId =
      state.value.email?.takeIf { it.isNotBlank() }
        ?: prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }
    return SyncAccountResolver.resolveAndBindAccount(
      cachedAccountId = cachedAccountId,
      fetchUserInfoJson = { httpGet("https://www.googleapis.com/oauth2/v3/userinfo", token) },
      onProfileResolved = { profile ->
        ensureSessionActive(expectedGeneration, requireSignedIn)
        prefs.edit()
          .putString(KEY_EMAIL, profile.accountId)
          .putString(KEY_NAME, profile.displayName)
          .apply()
        updateStateIfActive(expectedGeneration, requireSignedIn) {
          it.copy(email = profile.accountId, displayName = profile.displayName)
        }
      },
      bindToAccount = { accountId ->
        ensureSessionActive(expectedGeneration, requireSignedIn)
        tombstones.bindToAccount(accountId)
      },
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

  private fun createUploadTransport(
    token: String,
    expectedGeneration: Long,
    requireSignedIn: Boolean,
    rebuildMergedPayload: suspend (latestRemoteMerged: SyncPayload) -> SyncPayload,
  ): DriveUploadTransport =
    DriveUploadTransport(
      nowProvider = System::currentTimeMillis,
      listSyncFiles = { SyncRemotePayloadMerger.resolveSyncFiles(listAppDataFileEntries(token), SYNC_FILE_NAME) },
      fetchMetadata = { fileId -> fetchFileMetadata(token, fileId) },
      downloadPayload = { fileId -> downloadSyncPayload(token, fileId) },
      rebuildMergedPayload = rebuildMergedPayload,
      patchFile = { fileId, encoded -> uploadSyncJson(token, fileId, encoded) },
      createFile = { encoded -> uploadSyncJson(token, null, encoded) },
      deleteFile = { fileId -> httpDeleteFile(token, fileId) },
      encodePayload = { payload -> json.encodeToString(SyncPayload.serializer(), payload.canonicalized()) },
      ensureSessionActive = { ensureSessionActive(expectedGeneration, requireSignedIn = requireSignedIn) },
    )

  private fun listAppDataFileEntries(token: String): List<DriveFileEntry> {
    val entries = mutableListOf<DriveFileEntry>()
    var pageToken: String? = null
    do {
      val url =
        buildString {
          append("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder")
          append("&pageSize=$LIST_PAGE_SIZE&fields=nextPageToken,files(id,name,createdTime,version,md5Checksum)")
          pageToken?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
        }
      val body = httpGet(url, token)
      val page = DriveSyncMetadataParser.parseFileListPage(body) ?: throw remoteReadError("list")
      entries += page.entries
      pageToken = page.nextPageToken
    } while (pageToken != null)
    return entries
  }

  private fun fetchFileMetadata(token: String, fileId: String): DriveFileEntry? {
    val body =
      try {
        httpGet(
          "https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,createdTime,version,md5Checksum",
          token,
        )
      } catch (err: DriveRemoteReadException) {
        if (err.statusCode == 404) return null
        throw err
      }
    return DriveSyncMetadataParser.parseFileEntry(body) ?: throw remoteReadError("metadata")
  }

  private suspend fun downloadMissingBooks(
    token: String,
    remoteBooks: List<SyncedBookProgress>,
    appDataFiles: Map<String, String>,
    deletedHashes: Set<String>,
    expectedGeneration: Long,
    isBackground: Boolean,
  ): Int {
    val summary =
      DriveBookDownloadCoordinator.downloadMissingBooks(
        remoteBooks = remoteBooks,
        appDataFiles = appDataFiles,
        initialLocalHashes = library.books.value.mapNotNull { it.contentHash }.toSet(),
        deletedHashes = deletedHashes,
        tempDir = File(appContext.filesDir, "sync_tmp"),
        bookDriveFileName = ::bookDriveFileName,
        ensureSessionActive = { ensureSessionActive(expectedGeneration, isBackground) },
        onBookStarting = { remoteBook ->
          updateStateIfActive(expectedGeneration, isBackground) {
            it.copy(statusMessage = str(R.string.sync_downloading_book, remoteBook.title))
          }
        },
        downloadFileTo = { driveFileId, tempFile -> downloadFileTo(token, driveFileId, tempFile) },
        importSyncedFile = { tempFile, remoteBook ->
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
        },
      )
    return summary.failedCount
  }

  private fun uploadMissingBooks(
    token: String,
    appDataFiles: Map<String, String>,
    deletedHashes: Set<String>,
    expectedGeneration: Long,
    isBackground: Boolean,
  ): Int {
    var failedCount = 0
    for (book in library.books.value) {
      ensureSessionActive(expectedGeneration, isBackground)
      val hash = book.contentHash ?: continue
      if (hash in deletedHashes) continue
      val driveFileName = bookDriveFileName(hash, book.format.extension)
      if (driveFileName in appDataFiles) continue
      val localFile = library.fileOf(book)
      if (!localFile.exists() || localFile.length() <= 0L || localFile.length() > MAX_SYNC_BOOK_BYTES) {
        continue
      }
      updateStateIfActive(expectedGeneration, isBackground) {
        it.copy(statusMessage = str(R.string.sync_uploading_book, book.title))
      }
      val uploadErr = runCatching { uploadBinaryFileToAppData(token, driveFileName, localFile) }.exceptionOrNull()
      if (uploadErr != null) {
        if (uploadErr is CancellationException) throw uploadErr
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
      .getOrElse { err ->
        if (err is CancellationException) throw err
        throw remoteReadError("parse", cause = err)
      }
  }

  private fun remoteReadError(
    reason: String,
    statusCode: Int? = null,
    cause: Throwable? = null,
  ): DriveRemoteReadException =
    DriveRemoteReadException(
      message = str(R.string.sync_remote_read_failed, reason),
      statusCode = statusCode,
      cause = cause ?: statusCode?.let { DriveHttpException(it, "HTTP $it") },
    )

  private fun uploadSyncJson(token: String, existingFileId: String?, jsonBody: String): DriveFileEntry? {
    val requestUrl: String
    val method: String
    val contentType: String
    val bodyBytes: ByteArray
    val errorPrefix: String
    if (existingFileId == null) {
      val boundary = "eink_sync_boundary_${System.currentTimeMillis()}"
      requestUrl =
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime,version,md5Checksum"
      method = "POST"
      contentType = "multipart/related; boundary=$boundary"
      val metadata = """{"name":"$SYNC_FILE_NAME","parents":["appDataFolder"]}"""
      bodyBytes =
        ByteArrayOutputStream().apply {
          write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray(StandardCharsets.UTF_8))
          write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$jsonBody\r\n".toByteArray(StandardCharsets.UTF_8))
          write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()
      errorPrefix = "Drive upload failed"
    } else {
      requestUrl =
        "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media&fields=id,name,createdTime,version,md5Checksum"
      method = "PATCH"
      contentType = "application/json; charset=UTF-8"
      bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
      errorPrefix = "Drive update failed"
    }
    val conn = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
      requestMethod = method
      doOutput = true
      connectTimeout = 15_000
      readTimeout = 30_000
      setRequestProperty("Authorization", "Bearer $token")
      setRequestProperty("Content-Type", contentType)
    }
    return try {
      conn.outputStream.use { it.write(bodyBytes) }
      val code = conn.responseCode
      if (code !in 200..299) throw DriveHttpException(code, "$errorPrefix ($code)")
      val responseBody = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
      DriveSyncMetadataParser.parseFileEntry(responseBody)
    } finally {
      conn.disconnect()
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
      if (!DriveWipeCoordinator.isDeleteResponseSuccess(code)) {
        throw DriveHttpException(code, "Drive delete failed ($code)")
      }
      true
    } finally {
      conn.disconnect()
    }
  }

  private fun revokeToken(token: String): Boolean {
    val body = "token=" + URLEncoder.encode(token, "UTF-8")
    val conn =
      (URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      }
    return try {
      conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
      DriveWipeCoordinator.isRevokeResponseAccepted(conn.responseCode)
    } catch (_: Exception) {
      false
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

  internal fun formatSyncError(err: Throwable): String =
    formatSyncError(
      err = err,
      strings =
        SyncErrorStrings(
          offline = str(R.string.sync_error_offline),
          timeout = str(R.string.sync_error_timeout),
          authExpired = str(R.string.sync_error_auth_expired),
          quotaOrPermission = str(R.string.sync_error_quota_or_permission),
          serverError = { code -> str(R.string.sync_error_server, code) },
          httpError = { code -> str(R.string.sync_error_http, code) },
          playServicesError = str(R.string.sync_play_services_error),
          genericFailed = str(R.string.sync_failed),
        ),
    )

  private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
      addOnSuccessListener { result -> cont.resume(result) }
      addOnFailureListener { exc -> cont.resumeWithException(exc) }
    }

  internal data class SyncErrorStrings(
    val offline: String,
    val timeout: String,
    val authExpired: String,
    val quotaOrPermission: String,
    val serverError: (Int) -> String,
    val httpError: (Int) -> String,
    val playServicesError: String,
    val genericFailed: String,
  )

  internal companion object {
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
    const val MAX_WIPE_PASSES = DriveWipeCoordinator.DEFAULT_MAX_PASSES
    const val IDENTITY_SIGNOUT_TIMEOUT_MS = 5_000L
    const val BACKGROUND_SYNC_DEBOUNCE_MS = 20_000L
    const val MAX_BACKGROUND_BACKOFF_MS = 600_000L

    fun formatSyncError(err: Throwable, strings: SyncErrorStrings): String {
      if (err is CancellationException) throw err
      return when (err) {
        is DriveRemoteReadException -> {
          val code = err.statusCode ?: (err.cause as? DriveHttpException)?.statusCode
          if (code != null) {
            formatDriveHttpCode(code, strings)
          } else {
            when (val cause = err.cause) {
              is UnknownHostException, is ConnectException -> strings.offline
              is SocketTimeoutException -> strings.timeout
              else -> err.message?.takeIf { it.isNotBlank() } ?: strings.genericFailed
            }
          }
        }
        is UnknownHostException, is ConnectException -> strings.offline
        is SocketTimeoutException -> strings.timeout
        is DriveHttpException -> formatDriveHttpCode(err.statusCode, strings)
        is com.google.android.gms.common.api.ApiException -> strings.playServicesError
        else -> strings.genericFailed
      }
    }

    private fun formatDriveHttpCode(statusCode: Int, strings: SyncErrorStrings): String =
      when (statusCode) {
        401 -> strings.authExpired
        403, 507 -> strings.quotaOrPermission
        429, in 500..599 -> strings.serverError(statusCode)
        else -> strings.httpError(statusCode)
      }
  }
}
