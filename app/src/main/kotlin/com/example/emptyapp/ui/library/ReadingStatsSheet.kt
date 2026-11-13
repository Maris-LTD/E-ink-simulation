package com.example.emptyapp.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.emptyapp.data.stats.DailyReadingStat
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.ui.eink.EInkText

@Composable
fun ReaderAvatarIcon(modifier: Modifier = Modifier) {
  val ink = MaterialTheme.colorScheme.onBackground
  Canvas(modifier = modifier) {
    val stroke = 1.6.dp.toPx()
    val headRadius = size.minDimension * 0.19f
    val headCenter = Offset(size.width / 2f, size.height * 0.36f)
    drawCircle(
      color = ink,
      radius = headRadius,
      center = headCenter,
      style = Stroke(width = stroke),
    )
    drawArc(
      color = ink,
      startAngle = 195f,
      sweepAngle = 150f,
      useCenter = false,
      topLeft = Offset(size.width * 0.2f, size.height * 0.56f),
      size = Size(size.width * 0.6f, size.height * 0.44f),
      style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
  }
}

@Composable
fun ReadingStatsScreen(
  stats: ReadingStatsData,
  completedBooksCount: Int,
  onChangeGoalMinutes: (Int) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val todaySeconds = stats.todaySeconds()
  val todayMinutes = todaySeconds / 60
  val goalMinutes = stats.dailyGoalMinutes.coerceAtLeast(5)
  val progress = (todaySeconds.toFloat() / (goalMinutes * 60f)).coerceIn(0f, 1f)
  val recentDays = stats.dailyStats.sortedByDescending { it.date }.take(7)

  Column(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(onClick = onBack) { EInkText("‹", style = MaterialTheme.typography.titleLarge) }
      EInkText("Hồ sơ & Thống kê", style = MaterialTheme.typography.titleLarge)
    }
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)

    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier =
            Modifier.size(52.dp)
              .clip(CircleShape)
              .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          ReaderAvatarIcon(modifier = Modifier.size(38.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          EInkText("Độc giả eInk", style = MaterialTheme.typography.titleMedium)
          EInkText(
            text = "Chuỗi hiện tại: ${stats.currentStreak} ngày",
            style = MaterialTheme.typography.bodyMedium,
          )
          EInkText(
            text = "Kỷ lục chuỗi dài nhất: ${stats.longestStreak} ngày",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }

      Column(
        modifier =
          Modifier.fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          EInkText("Mục tiêu hôm nay", style = MaterialTheme.typography.titleSmall)
          EInkText(
            text = "$todayMinutes / $goalMinutes phút (${(progress * 100).toInt()}%)",
            style = MaterialTheme.typography.labelLarge,
          )
        }
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(16.dp)
              .border(1.5.dp, MaterialTheme.colorScheme.outline),
        ) {
          if (progress > 0f) {
            Box(
              modifier =
                Modifier.fillMaxWidth(progress)
                  .fillMaxHeight()
                  .background(MaterialTheme.colorScheme.primary),
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          EInkText("Đặt mục tiêu hằng ngày", style = MaterialTheme.typography.bodyMedium)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            GoalStepCircleButton(label = "−5", onClick = { onChangeGoalMinutes(goalMinutes - 5) })
            EInkText("${goalMinutes}p", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            GoalStepCircleButton(label = "+5", onClick = { onChangeGoalMinutes(goalMinutes + 5) })
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        StatSummaryBox(
          label = "Tổng thời gian",
          value = formatDuration(stats.totalSeconds),
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StatSummaryBox(
          label = "Trang đã lật",
          value = "${stats.totalPagesTurned}",
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StatSummaryBox(
          label = "Sách đã xong",
          value = "$completedBooksCount",
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
      }

      Column(
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        EInkText("7 ngày gần nhất", style = MaterialTheme.typography.titleSmall)
        if (recentDays.isEmpty()) {
          EInkText(
            text = "Chưa có dữ liệu đọc. Hãy mở một cuốn sách để bắt đầu ghi nhận.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        } else {
          recentDays.forEach { day ->
            RecentDayRow(stat = day, goalSeconds = goalMinutes * 60)
          }
        }
      }
    }
  }
}

@Composable
private fun RecentDayRow(
  stat: DailyReadingStat,
  goalSeconds: Int,
) {
  val minutes = stat.secondsRead / 60
  val metGoal = stat.secondsRead >= goalSeconds
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EInkText(stat.date, style = MaterialTheme.typography.bodyMedium)
    EInkText(
      text = "${minutes}p · ${stat.pagesTurned} trang${if (metGoal) " · Đạt" else ""}",
      style = MaterialTheme.typography.labelMedium,
      color = if (metGoal) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
    )
  }
}

@Composable
private fun GoalStepCircleButton(
  label: String,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.size(36.dp)
        .clip(CircleShape)
        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    EInkText(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
  }
}

@Composable
private fun StatSummaryBox(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        .padding(horizontal = 8.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
  ) {
    EInkText(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    EInkText(
      label,
      style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
      color = MaterialTheme.colorScheme.secondary,
    )
  }
}

private fun formatDuration(totalSeconds: Long): String {
  val totalMinutes = totalSeconds / 60L
  val hours = totalMinutes / 60L
  val minutes = totalMinutes % 60L
  return if (hours > 0L) "${hours}g ${minutes}p" else "${minutes}p"
}
