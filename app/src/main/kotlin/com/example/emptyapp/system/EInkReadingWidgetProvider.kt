package com.example.emptyapp.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.example.emptyapp.EmptyApplication
import com.example.emptyapp.LocaleHelper
import com.example.emptyapp.MainActivity
import com.example.emptyapp.R
import com.example.emptyapp.ReaderActivity
import com.example.emptyapp.data.book.mostRecentlyReadBook

class EInkReadingWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    appWidgetIds.forEach { widgetId ->
      updateWidget(context, appWidgetManager, widgetId)
    }
  }

  companion object {
    fun refreshAll(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, EInkReadingWidgetProvider::class.java))
      ids.forEach { updateWidget(context, manager, it) }
    }

    private fun updateWidget(
      context: Context,
      manager: AppWidgetManager,
      widgetId: Int,
    ) {
      val container = (context.applicationContext as? EmptyApplication)?.container
      val books = container?.library?.books?.value.orEmpty()
      val currentBook = books.mostRecentlyReadBook()
      val stats = container?.stats?.stats?.value
      val todayMin = (stats?.todaySeconds() ?: 0) / 60
      val goalMin = stats?.dailyGoalMinutes ?: 20
      val streak = stats?.currentStreak ?: 0

      val localized = LocaleHelper.wrap(context)
      val views = RemoteViews(context.packageName, R.layout.widget_eink_reading)
      if (currentBook != null) {
        val pct = (currentBook.progress * 100).toInt()
        views.setTextViewText(R.id.widget_book_title, currentBook.title)
        views.setTextViewText(
          R.id.widget_book_progress,
          localized.getString(R.string.widget_progress_format, pct),
        )
      } else {
        views.setTextViewText(R.id.widget_book_title, localized.getString(R.string.widget_no_book))
        views.setTextViewText(R.id.widget_book_progress, localized.getString(R.string.widget_tap_to_open))
      }
      views.setTextViewText(
        R.id.widget_today_stats,
        localized.getString(R.string.widget_stats_format, todayMin, goalMin, streak),
      )

      val launchIntent =
        if (currentBook != null) ReaderActivity.intentFor(context, currentBook.id)
        else android.content.Intent(context, MainActivity::class.java)
      val pendingIntent =
        PendingIntent.getActivity(
          context,
          widgetId,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
      manager.updateAppWidget(widgetId, views)
    }
  }
}
