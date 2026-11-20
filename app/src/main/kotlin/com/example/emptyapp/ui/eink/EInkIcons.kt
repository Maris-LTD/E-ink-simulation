package com.example.emptyapp.ui.eink

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BackArrowIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.7.dp.toPx()
    val cy = size.height * 0.5f
    drawLine(tint, Offset(size.width * 0.78f, cy), Offset(size.width * 0.24f, cy), s, StrokeCap.Round)
    val p = Path().apply {
      moveTo(size.width * 0.48f, size.height * 0.24f)
      lineTo(size.width * 0.22f, cy)
      lineTo(size.width * 0.48f, size.height * 0.76f)
    }
    drawPath(p, tint, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

@Composable
fun CloseIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.7.dp.toPx()
    val a = 0.24f
    val b = 0.76f
    drawLine(tint, Offset(size.width * a, size.height * a), Offset(size.width * b, size.height * b), s, StrokeCap.Round)
    drawLine(tint, Offset(size.width * b, size.height * a), Offset(size.width * a, size.height * b), s, StrokeCap.Round)
  }
}

@Composable
fun PlusIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.8.dp.toPx()
    drawLine(tint, Offset(size.width * 0.5f, size.height * 0.2f), Offset(size.width * 0.5f, size.height * 0.8f), s, StrokeCap.Round)
    drawLine(tint, Offset(size.width * 0.2f, size.height * 0.5f), Offset(size.width * 0.8f, size.height * 0.5f), s, StrokeCap.Round)
  }
}

@Composable
fun MinusIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.8.dp.toPx()
    drawLine(tint, Offset(size.width * 0.22f, size.height * 0.5f), Offset(size.width * 0.78f, size.height * 0.5f), s, StrokeCap.Round)
  }
}

@Composable
fun SearchIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.6.dp.toPx()
    val r = size.minDimension * 0.29f
    val c = Offset(size.width * 0.42f, size.height * 0.42f)
    drawCircle(tint, r, c, style = Stroke(s))
    drawLine(tint, Offset(c.x + r * 0.72f, c.y + r * 0.72f), Offset(size.width * 0.84f, size.height * 0.84f), s, StrokeCap.Round)
  }
}

@Composable
fun BookmarkIcon(filled: Boolean, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
      moveTo(w * 0.24f, h * 0.14f)
      lineTo(w * 0.76f, h * 0.14f)
      lineTo(w * 0.76f, h * 0.88f)
      lineTo(w * 0.5f, h * 0.68f)
      lineTo(w * 0.24f, h * 0.88f)
      close()
    }
    if (filled) drawPath(path, tint, style = Fill)
    drawPath(path, tint, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
  }
}

@Composable
fun SettingsGearIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val m = size.minDimension
    val s = 1.5.dp.toPx()
    drawCircle(tint, m * 0.14f, Offset(cx, cy), style = Stroke(s))
    drawCircle(tint, m * 0.28f, Offset(cx, cy), style = Stroke(s))
    for (i in 0 until 8) {
      val a = (i * PI / 4.0).toFloat()
      val dx = cos(a)
      val dy = sin(a)
      drawLine(tint, Offset(cx + m * 0.28f * dx, cy + m * 0.28f * dy), Offset(cx + m * 0.42f * dx, cy + m * 0.42f * dy), s * 1.1f, StrokeCap.Round)
    }
  }
}

@Composable
fun OverflowDotsIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val cx = size.width / 2f
    val r = 1.5.dp.toPx()
    listOf(0.24f, 0.50f, 0.76f).forEach { y -> drawCircle(tint, r, Offset(cx, size.height * y)) }
  }
}

@Composable
fun TrashIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val s = 1.5.dp.toPx()
    drawLine(tint, Offset(w * 0.18f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), s, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.38f, h * 0.16f), Offset(w * 0.62f, h * 0.16f), s, StrokeCap.Round)
    drawRoundRect(tint, Offset(w * 0.26f, h * 0.28f), Size(w * 0.48f, h * 0.56f), CornerRadius(2.dp.toPx()), style = Stroke(s))
    drawLine(tint, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.42f, h * 0.70f), s, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.58f, h * 0.70f), s, StrokeCap.Round)
  }
}

@Composable
fun CheckIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val p = Path().apply {
      moveTo(size.width * 0.18f, size.height * 0.52f)
      lineTo(size.width * 0.40f, size.height * 0.74f)
      lineTo(size.width * 0.82f, size.height * 0.28f)
    }
    drawPath(p, tint, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

@Composable
fun ChevronRightIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val p = Path().apply {
      moveTo(size.width * 0.35f, size.height * 0.22f)
      lineTo(size.width * 0.65f, size.height * 0.50f)
      lineTo(size.width * 0.35f, size.height * 0.78f)
    }
    drawPath(p, tint, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

@Composable
fun SlidersIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.5.dp.toPx()
    val r = 2.dp.toPx()
    val ys = listOf(0.28f, 0.50f, 0.72f)
    val kx = listOf(0.36f, 0.66f, 0.44f)
    ys.forEachIndexed { idx, y ->
      val cy = size.height * y
      drawLine(tint, Offset(size.width * 0.14f, cy), Offset(size.width * 0.86f, cy), s, StrokeCap.Round)
      drawCircle(tint, r, Offset(size.width * kx[idx], cy))
    }
  }
}

@Composable
fun TocListIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.6.dp.toPx()
    listOf(0.26f, 0.50f, 0.74f).forEach { y ->
      val cy = size.height * y
      drawCircle(tint, 1.3.dp.toPx(), Offset(size.width * 0.20f, cy))
      drawLine(tint, Offset(size.width * 0.36f, cy), Offset(size.width * 0.84f, cy), s, StrokeCap.Round)
    }
  }
}

@Composable
fun FocusMoonIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.5.dp.toPx()
    val w = size.width
    val h = size.height
    val path = Path().apply {
      moveTo(w * 0.62f, h * 0.16f)
      cubicTo(w * 0.32f, h * 0.20f, w * 0.18f, h * 0.50f, w * 0.34f, h * 0.74f)
      cubicTo(w * 0.50f, h * 0.90f, w * 0.76f, h * 0.82f, w * 0.84f, h * 0.64f)
      cubicTo(w * 0.60f, h * 0.70f, w * 0.44f, h * 0.48f, w * 0.62f, h * 0.16f)
      close()
    }
    drawPath(path, tint, style = Stroke(s, join = StrokeJoin.Round))
  }
}

@Composable
fun BookOpenIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val s = 1.6.dp.toPx()
    drawRoundRect(tint, Offset(w * 0.14f, h * 0.20f), Size(w * 0.72f, h * 0.60f), CornerRadius(2.5.dp.toPx()), style = Stroke(s))
    drawLine(tint, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.80f), s)
    drawLine(tint, Offset(w * 0.24f, h * 0.38f), Offset(w * 0.40f, h * 0.38f), s * 0.85f, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.60f, h * 0.38f), Offset(w * 0.76f, h * 0.38f), s * 0.85f, StrokeCap.Round)
    drawLine(tint, Offset(w * 0.60f, h * 0.52f), Offset(w * 0.72f, h * 0.52f), s * 0.85f, StrokeCap.Round)
  }
}

@Composable
fun SunIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val m = size.minDimension
    val s = 1.4.dp.toPx()
    drawCircle(tint, m * 0.20f, Offset(cx, cy), style = Stroke(s))
    for (i in 0 until 8) {
      val a = (i * PI / 4.0).toFloat()
      val dx = cos(a)
      val dy = sin(a)
      drawLine(tint, Offset(cx + m * 0.31f * dx, cy + m * 0.31f * dy), Offset(cx + m * 0.44f * dx, cy + m * 0.44f * dy), s, StrokeCap.Round)
    }
  }
}

@Composable
fun SortIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onBackground) {
  Canvas(modifier = modifier) {
    val s = 1.5.dp.toPx()
    drawLine(tint, Offset(size.width * 0.16f, size.height * 0.28f), Offset(size.width * 0.84f, size.height * 0.28f), s, StrokeCap.Round)
    drawLine(tint, Offset(size.width * 0.24f, size.height * 0.50f), Offset(size.width * 0.76f, size.height * 0.50f), s, StrokeCap.Round)
    drawLine(tint, Offset(size.width * 0.34f, size.height * 0.72f), Offset(size.width * 0.66f, size.height * 0.72f), s, StrokeCap.Round)
  }
}
