package com.example.emptyapp.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.emptyapp.EmptyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LockScreenNavReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val delta =
      when (intent.action) {
        ACTION_PREV -> -1
        ACTION_NEXT -> 1
        else -> return
      }
    val app = context.applicationContext as? EmptyApplication ?: return
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val updated = app.container.lockScreenReading.stepSnippet(delta) ?: return@launch
        val updatedLoc = updated.locatorJson
        if (updatedLoc != null) {
          app.container.library.saveProgress(updated.bookId, updatedLoc, updated.progress)
        }
        val coverPath =
          app.container.library.books.value
            .firstOrNull { it.id == updated.bookId }
            ?.let { app.container.library.coverFileOf(it)?.absolutePath }
        LockScreenReadingNotifier.show(
          context = context.applicationContext,
          bookId = updated.bookId,
          title = updated.title,
          progress = updated.progress,
          author = updated.author,
          coverPath = coverPath,
          snippet = updated.currentSnippet,
          snippetIndex = updated.currentSnippetIndex,
          snippetCount = updated.snippets.size,
          locatorJson = updated.locatorJson,
        )
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    const val ACTION_PREV = "com.example.emptyapp.LOCKSCREEN_PREV"
    const val ACTION_NEXT = "com.example.emptyapp.LOCKSCREEN_NEXT"
  }
}
