package com.example.emptyapp.data.sync

import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveAuthRetryTest {
  @Test
  fun isAuthExpiredErrorDetects401AcrossExceptionsAndCauses() {
    assertTrue(DriveAuthRetry.isAuthExpiredError(DriveHttpException(401, "HTTP 401")))
    assertFalse(DriveAuthRetry.isAuthExpiredError(DriveHttpException(403, "HTTP 403")))
    assertTrue(DriveAuthRetry.isAuthExpiredError(DriveRemoteReadException("HTTP 401", statusCode = 401)))
    assertFalse(DriveAuthRetry.isAuthExpiredError(DriveRemoteReadException("HTTP 500", statusCode = 500)))
    assertTrue(
      DriveAuthRetry.isAuthExpiredError(
        IllegalStateException("outer", DriveHttpException(401, "HTTP 401"))
      )
    )
    assertFalse(DriveAuthRetry.isAuthExpiredError(UnknownHostException("offline")))
    assertFalse(DriveAuthRetry.isAuthExpiredError(null))
  }

  @Test
  fun succeedsOnFirstAttemptWithoutClearingOrReauthorizing() = runTest {
    val cleared = mutableListOf<String>()
    val attempts = mutableListOf<String>()
    var authorizeCount = 0

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "tok-1",
        clearToken = { cleared += it },
        authorize = {
          authorizeCount++
          AuthTokenResponse("tok-2", hasResolution = false)
        },
        operation = { token ->
          attempts += token
          "ok-$token"
        },
      )

    assertEquals(TokenRetryOutcome.Succeeded("ok-tok-1", "tok-1"), outcome)
    assertEquals(listOf("tok-1"), attempts)
    assertTrue(cleared.isEmpty())
    assertEquals(0, authorizeCount)
  }

  @Test
  fun doesNotClearOrRetryOnNon401Error() = runTest {
    val cleared = mutableListOf<String>()
    val attempts = mutableListOf<String>()
    var authorizeCount = 0
    val serverErr = DriveHttpException(500, "HTTP 500")

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "tok-1",
        clearToken = { cleared += it },
        authorize = {
          authorizeCount++
          AuthTokenResponse("tok-2", hasResolution = false)
        },
        operation = { token ->
          attempts += token
          throw serverErr
        },
      )

    assertTrue(outcome is TokenRetryOutcome.Failed)
    val failed = outcome as TokenRetryOutcome.Failed
    assertSame(serverErr, failed.error)
    assertFalse(failed.authExpired)
    assertEquals(listOf("tok-1"), attempts)
    assertTrue(cleared.isEmpty())
    assertEquals(0, authorizeCount)
  }

  @Test
  fun clearsTokenReauthorizesAndRetriesOnceOn401() = runTest {
    val cleared = mutableListOf<String>()
    val attempts = mutableListOf<String>()
    var authorizeCount = 0

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "tok-dead",
        clearToken = { cleared += it },
        authorize = {
          authorizeCount++
          AuthTokenResponse("tok-fresh", hasResolution = false)
        },
        operation = { token ->
          attempts += token
          if (token == "tok-dead") throw DriveHttpException(401, "HTTP 401")
          "synced-$token"
        },
      )

    assertEquals(TokenRetryOutcome.Succeeded("synced-tok-fresh", "tok-fresh"), outcome)
    assertEquals(listOf("tok-dead"), cleared)
    assertEquals(1, authorizeCount)
    assertEquals(listOf("tok-dead", "tok-fresh"), attempts)
  }

  @Test
  fun clearsTokenAndRetriesOnceOnDriveRemoteReadException401() = runTest {
    val cleared = mutableListOf<String>()
    val attempts = mutableListOf<String>()

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "tok-dead",
        clearToken = { cleared += it },
        authorize = { AuthTokenResponse("tok-fresh", hasResolution = false) },
        operation = { token ->
          attempts += token
          if (token == "tok-dead") throw DriveRemoteReadException("HTTP 401", statusCode = 401)
          "ok"
        },
      )

    assertEquals(TokenRetryOutcome.Succeeded("ok", "tok-fresh"), outcome)
    assertEquals(listOf("tok-dead"), cleared)
    assertEquals(listOf("tok-dead", "tok-fresh"), attempts)
  }

  @Test
  fun stopsAfterSingleRetryWhenSecondAttemptAlsoReturns401() = runTest {
    val cleared = mutableListOf<String>()
    val attempts = mutableListOf<String>()
    var authorizeCount = 0

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "tok-1",
        clearToken = { cleared += it },
        authorize = {
          authorizeCount++
          AuthTokenResponse("tok-$authorizeCount-new", hasResolution = false)
        },
        operation = { token ->
          attempts += token
          throw DriveHttpException(401, "HTTP 401 ($token)")
        },
      )

    assertTrue(outcome is TokenRetryOutcome.Failed)
    val failed = outcome as TokenRetryOutcome.Failed
    assertTrue(failed.authExpired)
    assertEquals(1, authorizeCount)
    assertEquals(listOf("tok-1", "tok-1-new"), attempts)
    assertEquals(listOf("tok-1", "tok-1-new"), cleared)
  }

  @Test
  fun returnsNeedsResolutionWhenReauthorizeRequiresConsent() = runTest {
    val cleared = mutableListOf<String>()
    val attempts = mutableListOf<String>()
    var authorizeCount = 0

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "tok-dead",
        clearToken = { cleared += it },
        authorize = {
          authorizeCount++
          AuthTokenResponse(accessToken = null, hasResolution = true, resolution = "consent-pi")
        },
        operation = { token ->
          attempts += token
          throw DriveHttpException(401, "HTTP 401")
        },
      )

    assertEquals(TokenRetryOutcome.NeedsResolution("consent-pi"), outcome)
    assertEquals(listOf("tok-dead"), cleared)
    assertEquals(1, authorizeCount)
    assertEquals(listOf("tok-dead"), attempts)
  }

  @Test
  fun doesNotRetryWhenReauthorizeReturnsSameDeadTokenOrBlank() = runTest {
    for (returnedToken in listOf("tok-dead", "", "   ", null)) {
      val cleared = mutableListOf<String>()
      val attempts = mutableListOf<String>()
      var authorizeCount = 0
      val err401 = DriveHttpException(401, "HTTP 401")

      val outcome =
        DriveAuthRetry.executeWithSingle401Retry<String, String>(
          initialToken = "tok-dead",
          clearToken = { cleared += it },
          authorize = {
            authorizeCount++
            AuthTokenResponse(returnedToken, hasResolution = false)
          },
          operation = { token ->
            attempts += token
            throw err401
          },
        )

      assertTrue(outcome is TokenRetryOutcome.Failed)
      val failed = outcome as TokenRetryOutcome.Failed
      assertTrue(failed.authExpired)
      assertSame(err401, failed.error)
      assertEquals(listOf("tok-dead"), cleared)
      assertEquals(1, authorizeCount)
      assertEquals(listOf("tok-dead"), attempts)
    }
  }

  @Test
  fun stillReauthorizesAndRetriesIfClearTokenThrowsNonCancellation() = runTest {
    val attempts = mutableListOf<String>()

    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "tok-dead",
        clearToken = { throw IllegalStateException("clear failed") },
        authorize = { AuthTokenResponse("tok-fresh", hasResolution = false) },
        operation = { token ->
          attempts += token
          if (token == "tok-dead") throw DriveHttpException(401, "HTTP 401")
          "recovered"
        },
      )

    assertEquals(TokenRetryOutcome.Succeeded("recovered", "tok-fresh"), outcome)
    assertEquals(listOf("tok-dead", "tok-fresh"), attempts)
  }

  @Test
  fun marksAuthExpiredWhenReauthorizeThrowsAfter401() = runTest {
    val authErr = IllegalStateException("gms unavailable")
    val outcome =
      DriveAuthRetry.executeWithSingle401Retry<String, String>(
        initialToken = "tok-dead",
        clearToken = {},
        authorize = { throw authErr },
        operation = { throw DriveHttpException(401, "HTTP 401") },
      )

    assertTrue(outcome is TokenRetryOutcome.Failed)
    val failed = outcome as TokenRetryOutcome.Failed
    assertSame(authErr, failed.error)
    assertTrue(failed.authExpired)
  }

  @Test(expected = CancellationException::class)
  fun rethrowsCancellationException() = runTest {
    DriveAuthRetry.executeWithSingle401Retry<String, String>(
      initialToken = "tok-1",
      clearToken = {},
      authorize = { AuthTokenResponse("tok-2", hasResolution = false) },
      operation = { throw CancellationException("cancelled") },
    )
  }
}
