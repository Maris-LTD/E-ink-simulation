package com.example.emptyapp.ui.library

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.emptyapp.data.stats.DailyReadingStat
import com.example.emptyapp.data.stats.ReadingBadge
import com.example.emptyapp.data.stats.ReadingGamification
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.data.sync.SyncActionResult
import com.example.emptyapp.data.sync.SyncUiState
import com.example.emptyapp.system.EInkWallpaperService
import com.example.emptyapp.ui.eink.EInkText
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
  highlightsCount: Int,
  syncState: SyncUiState = SyncUiState(),
  onSignInAndSync: suspend () -> SyncActionResult = { SyncActionResult.Synced },
  onHandleSyncConsent: suspend (Intent?) -> SyncActionResult = { SyncActionResult.Synced },
  onSignOutSync: () -> Unit = {},
  onChangeGoalMinutes: (Int) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val consentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
      scope.launch { onHandleSyncConsent(res.data) }
    }
  fun triggerSync() {
    scope.launch {
      val result = onSignInAndSync()
      if (result is SyncActionResult.NeedsConsent) {
        consentLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent).build())
      }
    }
  }
  val todaySeconds = stats.todaySeconds()
  val todayMinutes = todaySeconds / 60
  val goalMinutes = stats.dailyGoalMinutes.coerceAtLeast(5)
  val progress = (todaySeconds.toFloat() / (goalMinutes * 60f)).coerceIn(0f, 1f)
  val today = LocalDate.now()
  val weekSeries = ReadingStatsRepository.recentDaysSeries(stats.dailyStats, days = 7, today = today)
  val heatmapDays = ReadingStatsRepository.recentDaysSeries(stats.dailyStats, days = 84, today = today)
  val activeDaysCount = stats.dailyStats.count { it.secondsRead > 0 }
  val avgMinutes7d = (weekSeries.sumOf { it.secondsRead } / 60) / 7
  val pagesPerHour =
    if (stats.totalSeconds >= 60L) ((stats.totalPagesTurned * 3600L) / stats.totalSeconds).toInt()
    else 0
  val rank = ReadingGamification.rankFor(stats, completedBooksCount, highlightsCount)
  val badges = ReadingGamification.badgesFor(stats, completedBooksCount, highlightsCount)

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
      GoogleSyncCard(
        syncState = syncState,
        onSyncOrSignIn = ::triggerSync,
        onSignOut = onSignOutSync,
      )
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.outline)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
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
            EInkText(
              text = "Cấp ${rank.level} · ${rank.title}",
              style = MaterialTheme.typography.titleMedium,
            )
            EInkText(
              text = "${rank.xp} XP · Chuỗi: ${stats.currentStreak} ngày (Kỷ lục: ${stats.longestStreak})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(10.dp)
              .border(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          if (rank.progressToNext > 0f) {
            Box(
              modifier =
                Modifier.fillMaxWidth(rank.progressToNext)
                  .fillMaxHeight()
                  .background(MaterialTheme.colorScheme.primary),
            )
          }
        }
        val nextLabel =
          rank.nextLevelXp?.let { target ->
            "Còn ${(target - rank.xp).coerceAtLeast(0)} XP để lên ${rank.nextLevelTitle}"
          } ?: "Đã đạt cấp bậc cao nhất"
        EInkText(
          text = nextLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
        )
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

      SevenDayBarChartCard(
        weekSeries = weekSeries,
        goalMinutes = goalMinutes,
        avgMinutes7d = avgMinutes7d,
        pagesPerHour = pagesPerHour,
      )

      ReadingHeatmapCard(
        days = heatmapDays,
        goalSeconds = goalMinutes * 60,
        activeDaysCount = activeDaysCount,
      )

      AchievementsCard(badges = badges)

      SystemEInkModeCard()
    }
  }
}

@Composable
private fun SystemEInkModeCard() {
  val context = LocalContext.current
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    EInkText("Giao diện E-Ink toàn máy", style = MaterialTheme.typography.titleSmall)
    EInkText(
      text = "Đặt làm Màn hình chính tối giản hoặc bật Hình nền động & Widget E-Ink.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    OutlinedButton(
      onClick = { openHomeLauncherSettings(context) },
      modifier = Modifier.fillMaxWidth(),
    ) {
      EInkText("Đặt làm Màn hình chính (E-Ink Launcher)")
    }
    OutlinedButton(
      onClick = { openLiveWallpaperPicker(context) },
      modifier = Modifier.fillMaxWidth(),
    ) {
      EInkText("Đặt Hình nền động E-Ink")
    }
  }
}

private fun openHomeLauncherSettings(context: Context) {
  val intent = Intent(Settings.ACTION_HOME_SETTINGS)
  runCatching { context.startActivity(intent) }
}

private fun openLiveWallpaperPicker(context: Context) {
  val direct =
    Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
      putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        ComponentName(context, EInkWallpaperService::class.java),
      )
    }
  val launched = runCatching { context.startActivity(direct) }.isSuccess
  if (!launched) {
    runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
  }
}

@Composable
private fun AchievementsCard(badges: List<ReadingBadge>) {
  val unlockedCount = badges.count { it.unlocked }
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
      EInkText("Huy hiệu thành tựu", style = MaterialTheme.typography.titleSmall)
      EInkText(
        text = "$unlockedCount / ${badges.size}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    badges.chunked(2).forEach { pair ->
      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        pair.forEach { badge ->
          BadgeCard(badge = badge, modifier = Modifier.weight(1f).fillMaxHeight())
        }
      }
    }
  }
}

@Composable
private fun BadgeCard(
  badge: ReadingBadge,
  modifier: Modifier = Modifier,
) {
  val borderColor =
    if (badge.unlocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
  val borderWidth = if (badge.unlocked) 1.5.dp else 1.dp
  Row(
    modifier = modifier.border(borderWidth, borderColor).padding(10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier =
        Modifier.size(24.dp)
          .clip(CircleShape)
          .background(if (badge.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
          .border(1.5.dp, borderColor, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (badge.unlocked) {
        EInkText(
          text = "✓",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      EInkText(
        text = badge.title,
        style = MaterialTheme.typography.labelLarge,
        color = if (badge.unlocked) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
      )
      EInkText(
        text = badge.description,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
  }
}

@Composable
private fun SevenDayBarChartCard(
  weekSeries: List<DailyReadingStat>,
  goalMinutes: Int,
  avgMinutes7d: Int,
  pagesPerHour: Int,
) {
  val maxMinutes = maxOf(goalMinutes, weekSeries.maxOfOrNull { it.secondsRead / 60 } ?: 0, 1)
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
      EInkText("Biểu đồ 7 ngày", style = MaterialTheme.typography.titleSmall)
      EInkText(
        text = "TB ${avgMinutes7d}p/ngày · ${pagesPerHour} trang/giờ",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().height(128.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Bottom,
    ) {
      weekSeries.forEach { stat ->
        val minutes = stat.secondsRead / 60
        val fraction = (minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
        val metGoal = minutes >= goalMinutes
        Column(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Bottom,
        ) {
          EInkText(
            text = if (minutes > 0) "${minutes}p" else "0",
            style = MaterialTheme.typography.labelSmall,
            color = if (metGoal) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.secondary,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth(0.62f)
                  .fillMaxHeight(fraction.coerceAtLeast(if (minutes > 0) 0.08f else 0.03f))
                  .background(
                    if (metGoal) MaterialTheme.colorScheme.primary
                    else if (minutes > 0) lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground, 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant
                  ),
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          EInkText(
            text = shortWeekdayLabel(stat.date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}

@Composable
private fun ReadingHeatmapCard(
  days: List<DailyReadingStat>,
  goalSeconds: Int,
  activeDaysCount: Int,
) {
  val bg = MaterialTheme.colorScheme.background
  val fg = MaterialTheme.colorScheme.onBackground
  val border = MaterialTheme.colorScheme.outlineVariant
  val weeks = days.chunked(7)

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EInkText("Lịch sử 12 tuần", style = MaterialTheme.typography.titleSmall)
      EInkText(
        text = "$activeDaysCount ngày đã đọc",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      weeks.forEach { week ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          week.forEach { stat ->
            val cellColor = heatmapCellColor(stat.secondsRead, goalSeconds, bg, fg)
            Box(
              modifier =
                Modifier.size(16.dp)
                  .background(cellColor)
                  .border(1.dp, border),
            )
          }
        }
      }
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EInkText("Ít", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
      Spacer(modifier = Modifier.width(6.dp))
      listOf(0f, 0.28f, 0.6f, 1f).forEach { level ->
        val c = if (level == 0f) bg else lerp(bg, fg, level)
        Box(modifier = Modifier.size(12.dp).background(c).border(1.dp, border))
        Spacer(modifier = Modifier.width(4.dp))
      }
      EInkText("Nhiều", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
  }
}

private fun heatmapCellColor(
  seconds: Int,
  goalSeconds: Int,
  bg: androidx.compose.ui.graphics.Color,
  fg: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color {
  if (seconds <= 0) return bg
  val ratio = seconds.toFloat() / goalSeconds.coerceAtLeast(60).toFloat()
  return when {
    ratio >= 1f -> fg
    ratio >= 0.5f -> lerp(bg, fg, 0.6f)
    else -> lerp(bg, fg, 0.28f)
  }
}

private fun shortWeekdayLabel(isoDate: String): String {
  val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
  return when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "T2"
    DayOfWeek.TUESDAY -> "T3"
    DayOfWeek.WEDNESDAY -> "T4"
    DayOfWeek.THURSDAY -> "T5"
    DayOfWeek.FRIDAY -> "T6"
    DayOfWeek.SATURDAY -> "T7"
    DayOfWeek.SUNDAY -> "CN"
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

@Composable
private fun GoogleSyncCard(
  syncState: SyncUiState,
  onSyncOrSignIn: () -> Unit,
  onSignOut: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EInkText(
      text = "ĐỒNG BỘ GOOGLE DRIVE",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    if (syncState.isSignedIn) {
      EInkText(
        text = syncState.displayName?.takeIf { it.isNotBlank() }?.let { "$it (${syncState.email})" } ?: syncState.email.orEmpty(),
        style = MaterialTheme.typography.titleSmall,
      )
      val syncTimeText =
        if (syncState.lastSyncedAt > 0L) {
          val formatted = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(syncState.lastSyncedAt))
          "Lần đồng bộ cuối: $formatted"
        } else {
          "Chưa đồng bộ"
        }
      EInkText(
        text = syncState.statusMessage ?: syncTimeText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = onSyncOrSignIn,
          enabled = !syncState.syncing,
        ) {
          EInkText(if (syncState.syncing) "Đang đồng bộ..." else "Đồng bộ ngay")
        }
        TextButton(
          onClick = onSignOut,
          enabled = !syncState.syncing,
        ) {
          EInkText("Đăng xuất", color = MaterialTheme.colorScheme.secondary)
        }
      }
    } else {
      EInkText(
        text = "Đăng nhập Google để tự động đồng bộ tiến độ đọc, dấu trang, highlight và thống kê giữa các thiết bị (0đ, lưu trên Drive AppData riêng).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
      )
      if (!syncState.statusMessage.isNullOrBlank()) {
        EInkText(
          text = syncState.statusMessage,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
      OutlinedButton(
        onClick = onSyncOrSignIn,
        enabled = !syncState.syncing,
      ) {
        EInkText(if (syncState.syncing) "Đang kết nối..." else "Đăng nhập với Google")
      }
    }
  }
}

private fun formatDuration(totalSeconds: Long): String {
  val totalMinutes = totalSeconds / 60L
  val hours = totalMinutes / 60L
  val minutes = totalMinutes % 60L
  return if (hours > 0L) "${hours}g ${minutes}p" else "${minutes}p"
}
