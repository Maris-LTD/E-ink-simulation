package com.example.emptyapp.reader

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.emptyapp.R
import com.example.emptyapp.ReaderActivity

object LockScreenReadingNotifier {
  private const val CHANNEL_ID = "reading_lockscreen"
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
  ) {
    if (!hasPermission(context)) return
    ensureChannel(context)
    val intent = ReaderActivity.intentFor(context, bookId)
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        bookId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText("Đang đọc · $pct% — Chạm để đọc tiếp")
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOnlyAlertOnce(true)
        .setContentIntent(pendingIntent)
        .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  fun cancel(context: Context) {
    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        "Đọc trên màn hình khóa",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
      }
    manager.createNotificationChannel(channel)
  }
}
