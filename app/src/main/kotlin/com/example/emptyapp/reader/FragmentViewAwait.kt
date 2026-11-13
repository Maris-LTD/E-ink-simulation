package com.example.emptyapp.reader

import android.view.View
import androidx.fragment.app.Fragment
import kotlinx.coroutines.delay

suspend fun Fragment.awaitView(): View {
  while (view == null) delay(16)
  return requireView()
}
