package com.example.emptyapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun ReaderFooterBar(
  title: String,
  progress: Float,
  inkColor: Color,
  modifier: Modifier = Modifier,
) {
  var clockText by remember { mutableStateOf(formatClock()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(30_000L)
      clockText = formatClock()
    }
  }

  val clamped = progress.coerceIn(0f, 1f)
  val mutedInk = inkColor.copy(alpha = 0.65f)
  Column(
    modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
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

private fun formatClock(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
