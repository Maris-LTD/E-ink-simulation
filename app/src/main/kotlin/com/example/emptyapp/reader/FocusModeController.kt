package com.example.emptyapp.reader

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.R

class FocusModeController(private val activity: Activity) {
  private val notificationManager =
    activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private var previousInterruptionFilter: Int? = null
  private var dndAppliedByUs = false

  fun hasDndPermission(): Boolean = notificationManager.isNotificationPolicyAccessGranted

  fun applyFocusMode(enabled: Boolean): Boolean {
    if (enabled) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      return enableDndIfPermitted()
    } else {
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      restoreDndIfNeeded()
      return true
    }
  }

  fun onLeaveForeground() {
    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    restoreDndIfNeeded()
  }

  fun openDndPermissionSettings() {
    val intent =
      Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    if (runCatching { activity.startActivity(intent) }.isFailure) {
      val msg = LocaleHelper.wrap(activity).getString(R.string.error_open_system_settings)
      Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
    }
  }

  private fun enableDndIfPermitted(): Boolean {
    if (!hasDndPermission()) return false
    if (!dndAppliedByUs) {
      previousInterruptionFilter = notificationManager.currentInterruptionFilter
    }
    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
    dndAppliedByUs = true
    return true
  }

  private fun restoreDndIfNeeded() {
    if (!dndAppliedByUs) return
    if (hasDndPermission()) {
      val restoreTo = previousInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
      notificationManager.setInterruptionFilter(restoreTo)
    }
    dndAppliedByUs = false
    previousInterruptionFilter = null
  }
}
