package com.example.emptyapp

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

fun extractIncomingBookUri(intent: Intent?): Uri? {
  if (intent == null) return null
  return when (intent.action) {
    Intent.ACTION_VIEW -> intent.data
    Intent.ACTION_SEND ->
      IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    else -> null
  }
}
