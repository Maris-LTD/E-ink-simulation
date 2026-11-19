package com.example.emptyapp.data.sync

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncErrorFormatterTest {
  private val strings =
    GoogleDriveSyncManager.SyncErrorStrings(
      offline = "No internet connection",
      timeout = "Google Drive connection timed out. Please retry.",
      authExpired = "Google Drive session expired. Please reconnect.",
      quotaOrPermission = "Google Drive storage is full or permission was denied",
      serverError = { code -> "Google Drive is temporarily unavailable ($code)" },
      httpError = { code -> "Sync error (HTTP $code)" },
      playServicesError = "Unable to connect to Google Play Services",
      genericFailed = "Sync failed",
    )

  @Test
  fun classifiesDriveRemoteReadExceptionByStatusCode() {
    assertEquals(
      strings.authExpired,
      GoogleDriveSyncManager.formatSyncError(
        DriveRemoteReadException("Remote read failed (HTTP 401)", statusCode = 401),
        strings,
      ),
    )
    assertEquals(
      strings.quotaOrPermission,
      GoogleDriveSyncManager.formatSyncError(
        DriveRemoteReadException("Remote read failed (HTTP 403)", statusCode = 403),
        strings,
      ),
    )
    assertEquals(
      strings.quotaOrPermission,
      GoogleDriveSyncManager.formatSyncError(
        DriveRemoteReadException("Remote read failed (HTTP 507)", statusCode = 507),
        strings,
      ),
    )
    assertEquals(
      "Google Drive is temporarily unavailable (429)",
      GoogleDriveSyncManager.formatSyncError(
        DriveRemoteReadException("Remote read failed (HTTP 429)", statusCode = 429),
        strings,
      ),
    )
    assertEquals(
      "Google Drive is temporarily unavailable (503)",
      GoogleDriveSyncManager.formatSyncError(
        DriveRemoteReadException("Remote read failed (HTTP 503)", statusCode = 503),
        strings,
      ),
    )
    assertEquals(
      "Sync error (HTTP 400)",
      GoogleDriveSyncManager.formatSyncError(
        DriveRemoteReadException("Remote read failed (HTTP 400)", statusCode = 400),
        strings,
      ),
    )
  }

  @Test
  fun classifiesDriveRemoteReadExceptionByDriveHttpExceptionCauseWhenStatusCodeMissing() {
    val err =
      DriveRemoteReadException(
        message = "Remote read failed",
        statusCode = null,
        cause = DriveHttpException(429, "HTTP 429"),
      )
    assertEquals(
      "Google Drive is temporarily unavailable (429)",
      GoogleDriveSyncManager.formatSyncError(err, strings),
    )
  }

  @Test
  fun keepsParseOrMetadataRemoteReadMessageWhenNoHttpStatusCodeIsPresent() {
    val parseErr =
      DriveRemoteReadException(
        message = "Couldn't read your Google Drive data (parse). Sync stopped so nothing gets overwritten.",
        statusCode = null,
      )
    assertEquals(parseErr.message, GoogleDriveSyncManager.formatSyncError(parseErr, strings))
  }

  @Test
  fun classifiesDriveHttpExceptionAndNetworkExceptions() {
    assertEquals(
      strings.offline,
      GoogleDriveSyncManager.formatSyncError(UnknownHostException("www.googleapis.com"), strings),
    )
    assertEquals(
      strings.offline,
      GoogleDriveSyncManager.formatSyncError(ConnectException("Network unreachable"), strings),
    )
    assertEquals(
      strings.timeout,
      GoogleDriveSyncManager.formatSyncError(SocketTimeoutException("Read timed out"), strings),
    )
    assertEquals(
      strings.quotaOrPermission,
      GoogleDriveSyncManager.formatSyncError(DriveHttpException(403, "Forbidden"), strings),
    )
    assertEquals(
      "Google Drive is temporarily unavailable (502)",
      GoogleDriveSyncManager.formatSyncError(DriveHttpException(502, "Bad Gateway"), strings),
    )
    assertEquals(
      strings.playServicesError,
      GoogleDriveSyncManager.formatSyncError(ApiException(Status.RESULT_INTERNAL_ERROR), strings),
    )
    assertEquals(
      strings.genericFailed,
      GoogleDriveSyncManager.formatSyncError(IOException("Raw technical stream error"), strings),
    )
  }

  @Test(expected = CancellationException::class)
  fun rethrowsCancellationExceptionInsteadOfReturningSyncFailed() {
    GoogleDriveSyncManager.formatSyncError(CancellationException("Stats screen popped"), strings)
  }
}
