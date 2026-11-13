package com.example.emptyapp.ui.eink

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun EInkText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  style: TextStyle = LocalTextStyle.current,
  minLines: Int = 1,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
  strokeWidthPx: Float = 2.0f,
) {
  val resolvedColor = if (color != Color.Unspecified) color else style.color
  val strokeStyle =
    remember(style, strokeWidthPx) {
      style.copy(drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round))
    }
  Box(modifier = modifier) {
    Text(
      text = text,
      color = resolvedColor,
      style = strokeStyle,
      minLines = minLines,
      maxLines = maxLines,
      overflow = overflow,
    )
    Text(
      text = text,
      color = resolvedColor,
      style = style,
      minLines = minLines,
      maxLines = maxLines,
      overflow = overflow,
    )
  }
}
