package com.example.emptyapp.data.sync

import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveWipeCoordinatorTest {
  @Test
  fun exitsImmediatelyWhenDriveIsAlreadyEmpty() = runTest {
    var listCalls = 0
    val deleted = mutableListOf<String>()

    DriveWipeCoordinator.wipeAll(
      listFileIds = {
        listCalls++
        emptyList()
      },
      deleteFile = { id ->
        deleted += id
        true
      },
    )

    assertEquals(1, listCalls)
    assertTrue(deleted.isEmpty())
  }

  @Test
  fun breaksEarlyOnceDriveBecomesEmptyInsteadOfRunningAllMaxPasses() = runTest {
    var listCalls = 0
    val deleted = mutableListOf<String>()

    DriveWipeCoordinator.wipeAll(
      maxPasses = 5,
      listFileIds = {
        listCalls++
        if (listCalls == 1) listOf("f1", "f2") else emptyList()
      },
      deleteFile = { id ->
        deleted += id
        true
      },
    )

    assertEquals(2, listCalls)
    assertEquals(listOf("f1", "f2"), deleted)
  }

  @Test
  fun retriesAcrossPassesWhenTransientDeleteFailureClearsAndDriveBecomesEmpty() = runTest {
    val remaining = linkedSetOf("f1", "f2")
    var listCalls = 0
    var f1Attempts = 0

    DriveWipeCoordinator.wipeAll(
      maxPasses = 5,
      listFileIds = {
        listCalls++
        remaining.toList()
      },
      deleteFile = { id ->
        if (id == "f1" && f1Attempts++ == 0) {
          throw DriveHttpException(500, "transient 500")
        }
        remaining.remove(id)
        true
      },
    )

    assertEquals(3, listCalls)
    assertTrue(remaining.isEmpty())
  }

  @Test
  fun throwsServerErrorWhenFilesStillRemainAfterMaxPasses() = runTest {
    var listCalls = 0
    val attempts = mutableListOf<String>()

    val error =
      runCatching {
        DriveWipeCoordinator.wipeAll(
          maxPasses = 3,
          listFileIds = {
            listCalls++
            listOf("f1", "f2")
          },
          deleteFile = { id ->
            attempts += id
            if (id == "f2") throw DriveHttpException(503, "Drive unavailable")
            true
          },
        )
      }.exceptionOrNull()

    assertTrue(error is DriveHttpException)
    assertEquals(503, (error as DriveHttpException).statusCode)
    assertEquals(4, listCalls)
    assertEquals(listOf("f1", "f2", "f1", "f2", "f1", "f2"), attempts)
  }

  @Test
  fun throwsNetworkExceptionWhenDeleteFailsOfflineAndFilesRemain() = runTest {
    val error =
      runCatching {
        DriveWipeCoordinator.wipeAll(
          maxPasses = 2,
          listFileIds = { listOf("f1") },
          deleteFile = { throw UnknownHostException("offline") },
        )
      }.exceptionOrNull()

    assertTrue(error is UnknownHostException)
  }

  @Test
  fun throwsIOExceptionWhenDeleteReturnsFalseOrFilesStillRemainOnFinalCheck() = runTest {
    val falseDeleteErr =
      runCatching {
        DriveWipeCoordinator.wipeAll(
          maxPasses = 2,
          listFileIds = { listOf("f1") },
          deleteFile = { false },
        )
      }.exceptionOrNull()

    assertTrue(falseDeleteErr is IOException)

    val lingeringFileErr =
      runCatching {
        DriveWipeCoordinator.wipeAll(
          maxPasses = 2,
          listFileIds = { listOf("f1") },
          deleteFile = { true },
        )
      }.exceptionOrNull()

    assertTrue(lingeringFileErr is IOException)
  }

  @Test
  fun succeedsWhenLastPassDeletesFinalFileAndFinalVerificationIsEmpty() = runTest {
    val remaining = linkedSetOf("f1")
    var listCalls = 0
    var deleteCalls = 0

    DriveWipeCoordinator.wipeAll(
      maxPasses = 2,
      listFileIds = {
        listCalls++
        remaining.toList()
      },
      deleteFile = { id ->
        deleteCalls++
        if (deleteCalls == 1) throw DriveHttpException(500, "retry me")
        remaining.remove(id)
        true
      },
    )

    assertEquals(3, listCalls)
    assertTrue(remaining.isEmpty())
  }

  @Test
  fun propagates401ImmediatelySoDriveAuthRetryCanRefreshAndFinishWipe() = runTest {
    val deletedWithToken = mutableListOf<Pair<String, String>>()
    val remaining = linkedSetOf("f1", "f2")

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<Unit, String>(
        initialToken = "expired-token",
        clearToken = {},
        authorize = { AuthTokenResponse("fresh-token", hasResolution = false) },
      ) { token ->
        DriveWipeCoordinator.wipeAll(
          maxPasses = 5,
          listFileIds = { remaining.toList() },
          deleteFile = { id ->
            deletedWithToken += token to id
            if (token == "expired-token") throw DriveHttpException(401, "HTTP 401")
            remaining.remove(id)
            true
          },
        )
      }

    assertEquals(TokenRetryOutcome.Succeeded(Unit, "fresh-token"), outcome)
    assertTrue(remaining.isEmpty())
    assertEquals(
      listOf(
        "expired-token" to "f1",
        "fresh-token" to "f1",
        "fresh-token" to "f2",
      ),
      deletedWithToken,
    )
  }

  @Test
  fun rethrowsCancellationExceptionImmediately() = runTest {
    var cancelled = false
    try {
      DriveWipeCoordinator.wipeAll(
        listFileIds = { listOf("f1", "f2") },
        deleteFile = { throw CancellationException("cancelled") },
      )
    } catch (_: CancellationException) {
      cancelled = true
    }

    assertTrue(cancelled)
  }

  @Test
  fun abortsImmediatelyWhenSessionGuardInvalidatedMidWipe() = runTest {
    val guard = SyncSessionGuard()
    val gen = guard.currentGeneration()
    var deletedCount = 0

    val error =
      runCatching {
        DriveWipeCoordinator.wipeAll(
          listFileIds = { listOf("f1", "f2") },
          deleteFile = {
            deletedCount++
            guard.advanceGeneration()
            true
          },
          ensureSessionActive = { guard.ensureCurrent(gen) },
        )
      }.exceptionOrNull()

    assertTrue(error is SyncSessionInvalidatedException)
    assertEquals(1, deletedCount)
  }

  @Test
  fun revokeAndDeleteResponseCodesFollowExpectedSemantics() {
    assertTrue(DriveWipeCoordinator.isRevokeResponseAccepted(200))
    assertTrue(DriveWipeCoordinator.isRevokeResponseAccepted(204))
    assertTrue(DriveWipeCoordinator.isRevokeResponseAccepted(299))
    assertTrue(DriveWipeCoordinator.isRevokeResponseAccepted(400))
    assertFalse(DriveWipeCoordinator.isRevokeResponseAccepted(401))
    assertFalse(DriveWipeCoordinator.isRevokeResponseAccepted(403))
    assertFalse(DriveWipeCoordinator.isRevokeResponseAccepted(500))

    assertTrue(DriveWipeCoordinator.isDeleteResponseSuccess(200))
    assertTrue(DriveWipeCoordinator.isDeleteResponseSuccess(204))
    assertTrue(DriveWipeCoordinator.isDeleteResponseSuccess(404))
    assertFalse(DriveWipeCoordinator.isDeleteResponseSuccess(400))
    assertFalse(DriveWipeCoordinator.isDeleteResponseSuccess(401))
    assertFalse(DriveWipeCoordinator.isDeleteResponseSuccess(500))
  }
}
