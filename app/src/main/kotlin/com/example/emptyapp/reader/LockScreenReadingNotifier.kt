package com.example.emptyapp.reader

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.R
import com.example.emptyapp.ReaderActivity
import java.io.File

object LockScreenReadingNotifier {
  private const val CHANNEL_ID = "reading_lockscreen_v2"
  private const val LEGACY_CHANNEL_ID = "reading_lockscreen"
  private const val NOTIFICATION_ID = 4001

  fun hasPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED

  fun show(
    context: Context,
    bookId: String,
    title: String,
    progress: Float,
    author: String? = null,
    coverPath: String? = null,
    snippet: String? = null,
    snippetIndex: Int = 0,
    snippetCount: Int = 0,
    locatorJson: String? = null,
  ) {
    if (!hasPermission(context)) return
    ensureChannel(context)
    val intent = ReaderActivity.intentFor(context, bookId, locatorJson)
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        bookId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val localized = LocaleHelper.wrap(context)
    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
    val statusLine =
      if (snippetCount > 0) {
        localized.getString(R.string.lockscreen_snippet_counter, snippetIndex + 1, snippetCount, pct)
      } else {
        localized.getString(R.string.lockscreen_reading_text, pct)
      }
    val barLabel = "$title · $statusLine"
    val tone =
      (context.applicationContext as? com.example.emptyapp.EmptyApplication)
        ?.container
        ?.settings
        ?.settings
        ?.value
        ?.tone ?: EInkTone.HIGH_CONTRAST
    val btnBg = if (tone == EInkTone.DARK) 0xFF2A2A2A.toInt() else 0xFFEEEEEE.toInt()
    val remoteViews =
      RemoteViews(context.packageName, R.layout.notification_lockscreen_nav).apply {
        setInt(R.id.notif_root, "setBackgroundColor", tone.background)
        setTextColor(R.id.tv_status, tone.text)
        setTextColor(R.id.btn_prev, tone.text)
        setTextColor(R.id.btn_next, tone.text)
        setInt(R.id.btn_prev, "setBackgroundColor", btnBg)
        setInt(R.id.btn_next, "setBackgroundColor", btnBg)
        setTextViewText(R.id.tv_status, barLabel)
        if (snippetCount > 1) {
          val prevIntent =
            PendingIntent.getBroadcast(
              context,
              4101,
              Intent(LockScreenNavReceiver.ACTION_PREV).setPackage(context.packageName),
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
          val nextIntent =
            PendingIntent.getBroadcast(
              context,
              4102,
              Intent(LockScreenNavReceiver.ACTION_NEXT).setPackage(context.packageName),
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
          setViewVisibility(R.id.btn_prev, View.VISIBLE)
          setViewVisibility(R.id.btn_next, View.VISIBLE)
          setOnClickPendingIntent(R.id.btn_prev, prevIntent)
          setOnClickPendingIntent(R.id.btn_next, nextIntent)
        } else {
          setViewVisibility(R.id.btn_prev, View.GONE)
          setViewVisibility(R.id.btn_next, View.GONE)
        }
      }

    val deleteIntent =
      PendingIntent.getBroadcast(
        context,
        4103,
        Intent(LockScreenNavReceiver.ACTION_DISMISSED).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(statusLine)
        .setCustomContentView(remoteViews)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setAutoCancel(true)
        .setDeleteIntent(deleteIntent)
        .setContentIntent(pendingIntent)
        .build()
    setDismissedByUser(context, false)
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  fun setDismissedByUser(context: Context, dismissed: Boolean) {
    context.applicationContext
      .getSharedPreferences("eink_lockscreen_notif", Context.MODE_PRIVATE)
      .edit()
      .putBoolean("dismissed_by_user", dismissed)
      .apply()
  }

  fun isDismissedByUser(context: Context): Boolean =
    context.applicationContext
      .getSharedPreferences("eink_lockscreen_notif", Context.MODE_PRIVATE)
      .getBoolean("dismissed_by_user", false)

  fun cancel(context: Context) {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
  }

  private fun decodeCoverThumbnail(coverPath: String?): Bitmap? {
    if (coverPath.isNullOrBlank()) return null
    val file = File(coverPath)
    if (!file.exists()) return null
    return runCatching {
      val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, opts)
      val maxDim = maxOf(opts.outWidth, opts.outHeight)
      var sample = 1
      while (maxDim / sample > 256) {
        sample *= 2
      }
      BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
      )
    }.getOrNull()
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
      manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }
    val channelName = LocaleHelper.wrap(context).getString(R.string.lockscreen_channel_name)
    val existing = manager.getNotificationChannel(CHANNEL_ID)
    if (existing != null) {
      if (existing.name != channelName) {
        existing.name = channelName
        manager.createNotificationChannel(existing)
      }
      return
    }
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        channelName,
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setSound(null, null)
        enableVibration(false)
        setShowBadge(false)
      }
    manager.createNotificationChannel(channel)
  }
}
