package com.example.emptyapp.data.sync

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SyncSessionInvalidatedException : Exception("Sync session invalidated")

internal sealed interface SessionLockedResult<out T> {
  data class Executed<T>(val value: T) : SessionLockedResult<T>
  data object StaleSession : SessionLockedResult<Nothing>
}

internal class SyncSessionGuard {
  private val generation = AtomicLong(0L)
  private val signOutJobLock = Any()
  private var activeSignOutJob: Job? = null

  fun currentGeneration(): Long = generation.get()

  fun advanceGeneration(): Long = generation.incrementAndGet()

  fun isCurrent(capturedGeneration: Long): Boolean = generation.get() == capturedGeneration

  fun ensureCurrent(capturedGeneration: Long) {
    if (!isCurrent(capturedGeneration)) {
      throw SyncSessionInvalidatedException()
    }
  }

  fun registerSignOutJob(job: Job) {
    synchronized(signOutJobLock) {
      activeSignOutJob = job
    }
  }

  fun clearSignOutJob(job: Job) {
    synchronized(signOutJobLock) {
      if (activeSignOutJob === job) {
        activeSignOutJob = null
      }
    }
  }

  suspend fun awaitPendingSignOut() {
    while (true) {
      val job = synchronized(signOutJobLock) { activeSignOutJob } ?: return
      job.join()
      synchronized(signOutJobLock) {
        if (activeSignOutJob === job || activeSignOutJob == null) {
          if (activeSignOutJob === job) {
            activeSignOutJob = null
          }
          return
        }
      }
    }
  }

  suspend fun <T> runWithSessionLock(
    mutex: Mutex,
    expectedGeneration: Long,
    isSessionStillAllowed: () -> Boolean = { true },
    block: suspend () -> T,
  ): SessionLockedResult<T> {
    if (!isCurrent(expectedGeneration) || !isSessionStillAllowed()) {
      return SessionLockedResult.StaleSession
    }
    return mutex.withLock {
      if (!isCurrent(expectedGeneration) || !isSessionStillAllowed()) {
        SessionLockedResult.StaleSession
      } else {
        SessionLockedResult.Executed(block())
      }
    }
  }

  suspend fun executeSignOutLocked(
    mutex: Mutex,
    signOutGeneration: Long,
    wasSignedIn: Boolean,
    revokeSession: suspend () -> Unit,
    identitySignOut: suspend () -> Unit,
    clearAccountPrefs: () -> Unit,
    resetUiStateIfStillCurrent: () -> Unit,
  ) {
    mutex.withLock {
      if (wasSignedIn) {
        try {
          revokeSession()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Throwable) {
        }
        try {
          identitySignOut()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Throwable) {
        }
      }
      clearAccountPrefs()
      if (isCurrent(signOutGeneration)) {
        resetUiStateIfStillCurrent()
      }
    }
  }
}
