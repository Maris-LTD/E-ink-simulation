package com.example.emptyapp.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.emptyapp.EmptyApplication

class LockScreenNavReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val delta =
      when (intent.action) {
        ACTION_PREV -> -1
        ACTION_NEXT -> 1
        else -> return
      }
    val app = context.applicationContext as? EmptyApplication ?: return
    val updated = app.container.lockScreenReading.stepSnippet(delta) ?: return
    val coverPath =
      app.container.library.books.value
        .firstOrNull { it.id == updated.bookId }
        ?.let { app.container.library.coverFileOf(it)?.absolutePath }
    LockScreenReadingNotifier.show(
      context = context,
      bookId = updated.bookId,
      title = updated.title,
      progress = updated.progress,
      author = updated.author,
      coverPath = coverPath,
      snippet = updated.currentSnippet,
      snippetIndex = updated.currentSnippetIndex,
      snippetCount = updated.snippets.size,
    )
  }

  companion object {
    const val ACTION_PREV = "com.example.emptyapp.LOCKSCREEN_PREV"
    const val ACTION_NEXT = "com.example.emptyapp.LOCKSCREEN_NEXT"
  }
}
