package com.example.emptyapp.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSessionGuardTest {
  @Test
  fun queuedSyncWaitingForMutexIsSkippedAfterSignOutAdvancesGeneration() = runTest {
    val mutex = Mutex()
    val guard = SyncSessionGuard()
    val capturedGen = guard.currentGeneration()
    val lockHeld = CompletableDeferred<Unit>()
    val releaseLock = CompletableDeferred<Unit>()
    var committedPrefs = false

    val holder =
      launch {
        mutex.withLock {
          lockHeld.complete(Unit)
          releaseLock.await()
        }
      }
    lockHeld.await()

    var lockResult: SessionLockedResult<String>? = null
    val queuedSync =
      launch {
        lockResult =
          guard.runWithSessionLock(
            mutex = mutex,
            expectedGeneration = capturedGen,
          ) {
            committedPrefs = true
            "synced"
          }
      }
    testScheduler.runCurrent()

    guard.advanceGeneration()
    releaseLock.complete(Unit)
    holder.join()
    queuedSync.join()

    assertEquals(SessionLockedResult.StaleSession, lockResult)
    assertFalse(committedPrefs)
  }

  @Test
  fun inFlightSyncThrowsSyncSessionInvalidatedExceptionBeforeCommit() = runTest {
    val guard = SyncSessionGuard()
    val mutex = Mutex()
    val capturedGen = guard.currentGeneration()
    var committedEmailOrLastSync = false

    val err =
      runCatching {
        guard.runWithSessionLock(
          mutex = mutex,
          expectedGeneration = capturedGen,
        ) {
          guard.advanceGeneration()
          guard.ensureCurrent(capturedGen)
          committedEmailOrLastSync = true
        }
      }.exceptionOrNull()

    assertTrue(err is SyncSessionInvalidatedException)
    assertFalse(committedEmailOrLastSync)
  }

  @Test
  fun awaitPendingSignOutReturnsImmediatelyWhenIdleAndWaitsActiveJob() = runTest {
    val guard = SyncSessionGuard()
    guard.awaitPendingSignOut()

    val jobGate = CompletableDeferred<Unit>()
    val signOutJob =
      launch {
        try {
          jobGate.await()
        } finally {
          coroutineContext[Job]?.let(guard::clearSignOutJob)
        }
      }
    guard.registerSignOutJob(signOutJob)

    var resumed = false
    val waiter =
      launch {
        guard.awaitPendingSignOut()
        resumed = true
      }
    testScheduler.runCurrent()
    assertFalse(resumed)

    jobGate.complete(Unit)
    signOutJob.join()
    waiter.join()
    assertTrue(resumed)
  }

  @Test
  fun backgroundSyncIsRejectedWhenSignedOutEvenIfGenerationUnchanged() = runTest {
    val guard = SyncSessionGuard()
    val mutex = Mutex()
    val gen = guard.currentGeneration()
    var ran = false

    val result =
      guard.runWithSessionLock(
        mutex = mutex,
        expectedGeneration = gen,
        isSessionStillAllowed = { false },
      ) {
        ran = true
        "ok"
      }

    assertEquals(SessionLockedResult.StaleSession, result)
    assertFalse(ran)
  }

  @Test
  fun executeSignOutLockedRunsRevokeBeforeIdentitySignOutAndClearsPrefsUnderMutex() = runTest {
    val guard = SyncSessionGuard()
    val mutex = Mutex()
    val events = mutableListOf<String>()
    val lockHeld = CompletableDeferred<Unit>()
    val releaseLock = CompletableDeferred<Unit>()

    val runningSync =
      launch {
        mutex.withLock {
          lockHeld.complete(Unit)
          releaseLock.await()
          events += "sync_released_mutex"
        }
      }
    lockHeld.await()

    val signOutGen = guard.advanceGeneration()
    val signOutJob =
      launch {
        guard.executeSignOutLocked(
          mutex = mutex,
          signOutGeneration = signOutGen,
          wasSignedIn = true,
          revokeSession = {
            events += "revoke"
            error("network hiccup during revoke")
          },
          identitySignOut = { events += "identity_sign_out" },
          clearAccountPrefs = { events += "clear_prefs" },
          resetUiStateIfStillCurrent = { events += "reset_ui" },
        )
      }
    testScheduler.runCurrent()
    assertTrue(events.isEmpty())

    releaseLock.complete(Unit)
    runningSync.join()
    signOutJob.join()

    assertEquals(
      listOf("sync_released_mutex", "revoke", "identity_sign_out", "clear_prefs", "reset_ui"),
      events,
    )
  }

  @Test
  fun newSignInAwaitsPendingSignOutAndIsNotClobberedByTrailingSignOutReset() = runTest {
    val guard = SyncSessionGuard()
    val mutex = Mutex()
    val events = mutableListOf<String>()
    val revokeStarted = CompletableDeferred<Unit>()
    val allowRevokeFinish = CompletableDeferred<Unit>()

    val signOutGen = guard.advanceGeneration()
    val signOutJob =
      launch(start = CoroutineStart.LAZY) {
        try {
          guard.executeSignOutLocked(
            mutex = mutex,
            signOutGeneration = signOutGen,
            wasSignedIn = true,
            revokeSession = {
              events += "revoke_start"
              revokeStarted.complete(Unit)
              allowRevokeFinish.await()
              events += "revoke_done"
            },
            identitySignOut = { events += "identity_sign_out" },
            clearAccountPrefs = { events += "clear_prefs" },
            resetUiStateIfStillCurrent = { events += "stale_reset_ui" },
          )
        } finally {
          coroutineContext[Job]?.let(guard::clearSignOutJob)
        }
      }
    guard.registerSignOutJob(signOutJob)
    signOutJob.start()
    revokeStarted.await()

    val signInGen = guard.advanceGeneration()
    val signInJob =
      launch {
        events += "signin_connecting"
        guard.awaitPendingSignOut()
        assertTrue(guard.isCurrent(signInGen))
        events += "signin_authorize"
      }
    testScheduler.runCurrent()

    assertEquals(listOf("revoke_start", "signin_connecting"), events)

    allowRevokeFinish.complete(Unit)
    signOutJob.join()
    signInJob.join()

    assertEquals(
      listOf(
        "revoke_start",
        "signin_connecting",
        "revoke_done",
        "identity_sign_out",
        "clear_prefs",
        "signin_authorize",
      ),
      events,
    )
  }

  @Test
  fun executeSignOutLockedWhenNotSignedInSkipsRevokeAndIdentitySignOut() = runTest {
    val guard = SyncSessionGuard()
    val mutex = Mutex()
    val events = mutableListOf<String>()
    val gen = guard.advanceGeneration()

    guard.executeSignOutLocked(
      mutex = mutex,
      signOutGeneration = gen,
      wasSignedIn = false,
      revokeSession = { events += "revoke" },
      identitySignOut = { events += "identity_sign_out" },
      clearAccountPrefs = { events += "clear_prefs" },
      resetUiStateIfStillCurrent = { events += "reset_ui" },
    )

    assertEquals(listOf("clear_prefs", "reset_ui"), events)
  }
}
