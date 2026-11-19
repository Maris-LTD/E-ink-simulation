package com.example.emptyapp.data.sync

import kotlinx.coroutines.CancellationException

data class AuthTokenResponse<out R>(
  val accessToken: String?,
  val hasResolution: Boolean,
  val resolution: R? = null,
)

sealed interface TokenRetryOutcome<out T, out R> {
  data class Succeeded<T>(val value: T, val tokenUsed: String) : TokenRetryOutcome<T, Nothing>
  data class NeedsResolution<R>(val resolution: R?) : TokenRetryOutcome<Nothing, R>
  data class Failed(val error: Throwable, val authExpired: Boolean) : TokenRetryOutcome<Nothing, Nothing>
}

internal object DriveAuthRetry {
  private const val MAX_CAUSE_DEPTH = 8

  fun isAuthExpiredError(err: Throwable?): Boolean {
    var current = err
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
      when (current) {
        is DriveHttpException -> if (current.statusCode == 401) return true
        is DriveRemoteReadException -> if (current.statusCode == 401) return true
      }
      if (current.cause === current) break
      current = current.cause
      depth++
    }
    return false
  }

  suspend fun <T, R> executeWithSingle401Retry(
    initialToken: String,
    clearToken: suspend (String) -> Unit,
    authorize: suspend () -> AuthTokenResponse<R>,
    operation: suspend (String) -> T,
  ): TokenRetryOutcome<T, R> {
    val firstError =
      try {
        return TokenRetryOutcome.Succeeded(value = operation(initialToken), tokenUsed = initialToken)
      } catch (e: CancellationException) {
        throw e
      } catch (err: Throwable) {
        err
      }

    if (!isAuthExpiredError(firstError)) {
      return TokenRetryOutcome.Failed(error = firstError, authExpired = false)
    }

    clearTokenSafely(clearToken, initialToken)

    val refreshed =
      try {
        authorize()
      } catch (e: CancellationException) {
        throw e
      } catch (authErr: Throwable) {
        return TokenRetryOutcome.Failed(error = authErr, authExpired = true)
      }

    if (refreshed.hasResolution) {
      return TokenRetryOutcome.NeedsResolution(refreshed.resolution)
    }

    val newToken = refreshed.accessToken
    if (newToken.isNullOrBlank() || newToken == initialToken) {
      return TokenRetryOutcome.Failed(error = firstError, authExpired = true)
    }

    return try {
      TokenRetryOutcome.Succeeded(value = operation(newToken), tokenUsed = newToken)
    } catch (e: CancellationException) {
      throw e
    } catch (retryErr: Throwable) {
      val retryExpired = isAuthExpiredError(retryErr)
      if (retryExpired) {
        clearTokenSafely(clearToken, newToken)
      }
      TokenRetryOutcome.Failed(error = retryErr, authExpired = retryExpired)
    }
  }

  private suspend fun clearTokenSafely(
    clearToken: suspend (String) -> Unit,
    token: String,
  ) {
    try {
      clearToken(token)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Throwable) {
    }
  }
}
