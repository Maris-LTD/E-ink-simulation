package com.example.emptyapp.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emptyapp.reader.EInkTone
import com.example.emptyapp.ui.eink.EInkText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun ReaderTopBar(
  title: String,
  showFontControls: Boolean = true,
  onClose: () -> Unit,
  onOpenSearch: () -> Unit,
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
        onClick = onOpenSearch,
        modifier = Modifier.semantics { contentDescription = "Tìm trong sách" },
      ) {
        val ink = MaterialTheme.colorScheme.onBackground
        Canvas(modifier = Modifier.size(19.dp)) {
          val r = size.minDimension * 0.3f
          val center = Offset(size.width * 0.42f, size.height * 0.42f)
          val stroke = 2.dp.toPx()
          drawCircle(color = ink, radius = r, center = center, style = Stroke(width = stroke))
          drawLine(
            color = ink,
            start = Offset(center.x + r * 0.7f, center.y + r * 0.7f),
            end = Offset(size.width * 0.85f, size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
          )
        }
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
  tone: EInkTone,
  brightness: Float,
  onToneChange: (EInkTone) -> Unit,
  onBrightnessChange: (Float) -> Unit,
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
    ThemeAndBrightnessRow(
      tone = tone,
      brightness = brightness,
      onToneChange = onToneChange,
      onBrightnessChange = onBrightnessChange,
    )
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

@Composable
private fun ThemeAndBrightnessRow(
  tone: EInkTone,
  brightness: Float,
  onToneChange: (EInkTone) -> Unit,
  onBrightnessChange: (Float) -> Unit,
) {
  val isAuto = brightness < 0f
  val displayValue = if (isAuto) 0.5f else brightness.coerceIn(0.02f, 1f)
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      EInkTone.entries.forEach { option ->
        val selected = option == tone
        Box(
          modifier =
            Modifier.size(22.dp)
              .clip(CircleShape)
              .background(Color(option.background))
              .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
              )
              .clickable { onToneChange(option) }
              .semantics { contentDescription = toneLabel(option) }
        )
      }
    }
    Slider(
      value = displayValue,
      onValueChange = { onBrightnessChange(it.coerceIn(0.02f, 1f)) },
      valueRange = 0.02f..1f,
      modifier = Modifier.weight(1f),
      colors =
        SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.primary,
          activeTrackColor = MaterialTheme.colorScheme.primary,
          inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
    BrightnessSunBadge(
      label = if (isAuto) "A" else "${(displayValue * 100).roundToInt()}",
      contentDescription = if (isAuto) "Độ sáng tự động" else "Độ sáng ${(displayValue * 100).roundToInt()}%",
      onClick = { onBrightnessChange(if (isAuto) 0.5f else -1f) },
    )
  }
}

@Composable
private fun BrightnessSunBadge(
  label: String,
  contentDescription: String,
  onClick: () -> Unit,
) {
  val ink = MaterialTheme.colorScheme.onBackground
  Box(
    modifier =
      Modifier.size(34.dp)
        .clickable(onClick = onClick)
        .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val r = size.minDimension * 0.28f
      val rayInner = size.minDimension * 0.36f
      val rayOuter = size.minDimension * 0.48f
      val stroke = 1.4.dp.toPx()
      for (i in 0 until 8) {
        val angle = (i * PI / 4.0).toFloat()
        val dx = cos(angle)
        val dy = sin(angle)
        drawLine(
          color = ink,
          start = Offset(cx + rayInner * dx, cy + rayInner * dy),
          end = Offset(cx + rayOuter * dx, cy + rayOuter * dy),
          strokeWidth = stroke,
          cap = StrokeCap.Round,
        )
      }
      drawCircle(color = ink, radius = r, style = Stroke(width = stroke))
    }
    EInkText(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp),
      maxLines = 1,
    )
  }
}

private fun toneLabel(tone: EInkTone): String =
  when (tone) {
    EInkTone.PAPER -> "Giấy"
    EInkTone.WARM -> "Ấm"
    EInkTone.HIGH_CONTRAST -> "Trắng"
    EInkTone.SOFT_GRAY -> "Xám"
    EInkTone.DARK -> "Đêm"
  }
