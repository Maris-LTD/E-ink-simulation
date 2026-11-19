package com.example.emptyapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.util.Date
import kotlinx.coroutines.delay

@Composable
fun ReaderFooterBar(
  title: String,
  progress: Float,
  inkColor: Color,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var clockText by remember(context) { mutableStateOf(formatClock(context)) }
  LaunchedEffect(context, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      while (true) {
        clockText = formatClock(context)
        val nowMs = System.currentTimeMillis()
        val delayToNextMinute = (60_000L - (nowMs % 60_000L) + 50L).coerceIn(1_000L, 60_000L)
        delay(delayToNextMinute)
      }
    }
  }

  val clamped = progress.coerceIn(0f, 1f)
  val mutedInk = inkColor.copy(alpha = 0.65f)
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        .padding(horizontal = 16.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(inkColor.copy(alpha = 0.2f))) {
      Box(modifier = Modifier.fillMaxWidth(clamped).height(1.dp).background(inkColor))
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = mutedInk,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(end = 12.dp),
      )
      Text(
        text = "${(clamped * 100).toInt()}%  ·  $clockText",
        style = MaterialTheme.typography.labelSmall,
        color = mutedInk,
      )
    }
  }
}

private fun formatClock(context: android.content.Context): String =
  DateFormat.getTimeFormat(context).format(Date())
