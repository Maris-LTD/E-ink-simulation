package com.example.emptyapp

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAppCachePolicyTest {
  @Test
  fun skipsPackageRemovedBroadcastWhenReplacing() {
    assertFalse(shouldRefreshAppsForPackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, isReplacing = true))
    assertTrue(shouldRefreshAppsForPackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, isReplacing = false))
    assertTrue(shouldRefreshAppsForPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, isReplacing = true))
    assertTrue(shouldRefreshAppsForPackageBroadcast(Intent.ACTION_PACKAGE_CHANGED, isReplacing = false))
  }
}
