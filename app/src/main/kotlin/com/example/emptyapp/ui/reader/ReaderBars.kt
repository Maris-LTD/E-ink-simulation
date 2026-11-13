package com.example.emptyapp.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emptyapp.ui.eink.EInkText
import kotlinx.coroutines.delay

@Composable
fun ReaderTopBar(
  title: String,
  showFontControls: Boolean = true,
  onClose: () -> Unit,
  onOpenContents: () -> Unit,
  onBookmark: () -> Unit,
  onDecreaseFont: () -> Unit,
  onIncreaseFont: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onClose) { EInkText("‹", style = MaterialTheme.typography.titleLarge) }
      EInkText(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(end = 4.dp),
      )
      if (showFontControls) {
        TextButton(onClick = onDecreaseFont) { EInkText("A−", style = MaterialTheme.typography.labelLarge) }
        TextButton(onClick = onIncreaseFont) { EInkText("A+", style = MaterialTheme.typography.labelLarge) }
      }
      IconButton(
        onClick = onBookmark,
        modifier = Modifier.semantics { contentDescription = "Lưu dấu trang" },
      ) {
        val ink = MaterialTheme.colorScheme.onBackground
        Canvas(modifier = Modifier.size(20.dp)) {
          val w = size.width
          val h = size.height
          val path =
            Path().apply {
              moveTo(w * 0.2f, h * 0.1f)
              lineTo(w * 0.8f, h * 0.1f)
              lineTo(w * 0.8f, h * 0.9f)
              lineTo(w * 0.5f, h * 0.68f)
              lineTo(w * 0.2f, h * 0.9f)
              close()
            }
          drawPath(path, color = ink, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Miter))
        }
      }
      TextButton(onClick = onOpenContents) { EInkText("Mục lục", style = MaterialTheme.typography.labelLarge) }
    }
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
  }
}

@Composable
fun ReaderBottomBar(
  progress: Float,
  seekEnabled: Boolean,
  onSeekToProgress: (Float) -> Unit,
  onToggleSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var dragging by remember { mutableStateOf(false) }
  var pendingSeek by remember { mutableStateOf(false) }
  var sliderValue by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }

  LaunchedEffect(progress) {
    if (!dragging && !pendingSeek) {
      sliderValue = progress.coerceIn(0f, 1f)
    }
  }
  LaunchedEffect(pendingSeek) {
    if (pendingSeek) {
      delay(700L)
      pendingSeek = false
      sliderValue = progress.coerceIn(0f, 1f)
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.background)
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        .padding(horizontal = 16.dp, vertical = 6.dp),
  ) {
    if (seekEnabled) {
      Slider(
        value = sliderValue,
        onValueChange = {
          dragging = true
          pendingSeek = false
          sliderValue = it
        },
        onValueChangeFinished = {
          dragging = false
          pendingSeek = true
          onSeekToProgress(sliderValue)
        },
        colors =
          SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
          ),
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EInkText("${(sliderValue * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
      TextButton(onClick = onToggleSettings) { EInkText("Cài đặt") }
    }
  }
}
