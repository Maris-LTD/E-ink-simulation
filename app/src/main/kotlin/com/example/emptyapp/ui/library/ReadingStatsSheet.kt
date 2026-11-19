package com.example.emptyapp.ui.library

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.emptyapp.reader.AppLanguage
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.EInkTone
import com.example.emptyapp.reader.OrientationLock
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.stats.DailyReadingStat
import com.example.emptyapp.data.stats.ReadingBadge
import com.example.emptyapp.data.stats.ReadingGamification
import com.example.emptyapp.data.stats.ReadingStatsData
import com.example.emptyapp.data.stats.ReadingStatsRepository
import com.example.emptyapp.data.sync.SyncActionResult
import com.example.emptyapp.data.sync.SyncUiState
import com.example.emptyapp.data.sync.SyncedBookProgress
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
  shouldConfirmMeteredBookSync: () -> Boolean = { false },
  onSignInAndSync: suspend (allowBookFiles: Boolean) -> SyncActionResult = { SyncActionResult.Synced },
  onHandleSyncConsent: suspend (Intent?, allowBookFiles: Boolean) -> SyncActionResult = { _, _ -> SyncActionResult.Synced },
  onLaunchSignInAndSync: ((allowBookFiles: Boolean, onNeedConsent: (IntentSenderRequest) -> Unit) -> Unit)? = null,
  onLaunchHandleSyncConsent: ((Intent?, allowBookFiles: Boolean) -> Unit)? = null,
  onToggleSyncBookFiles: (Boolean) -> Unit = {},
  onDeleteCloudBook: suspend (SyncedBookProgress) -> SyncActionResult = { SyncActionResult.Synced },
  onLaunchDeleteCloudBook: ((SyncedBookProgress) -> Unit)? = null,
  onDeleteAllCloudData: suspend () -> SyncActionResult = { SyncActionResult.Synced },
  onLaunchDeleteAllCloudData: (() -> Unit)? = null,
  onSignOutSync: () -> Unit = {},
  onChangeGoalMinutes: (Int) -> Unit,
  settings: EInkSettings = EInkSettings(),
  onUpdateSettings: ((EInkSettings) -> EInkSettings) -> Unit = {},
  currentLanguage: AppLanguage = AppLanguage.SYSTEM,
  onChangeLanguage: (AppLanguage) -> Unit = {},
  writeError: Boolean = false,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val saveErrorMsg = stringResource(R.string.error_save_data)
  LaunchedEffect(writeError) {
    if (writeError) {
      Toast.makeText(context, saveErrorMsg, Toast.LENGTH_SHORT).show()
    }
  }
  var pendingConsentAllowBookFiles by remember { mutableStateOf(syncState.syncBookFiles) }
  var showMeteredSyncConfirm by remember { mutableStateOf(false) }
  val consentLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
      val allowBookFiles = pendingConsentAllowBookFiles
      if (onLaunchHandleSyncConsent != null) {
        onLaunchHandleSyncConsent(res.data, allowBookFiles)
      } else {
        scope.launch { onHandleSyncConsent(res.data, allowBookFiles) }
      }
    }
  fun startSync(allowBookFiles: Boolean) {
    pendingConsentAllowBookFiles = allowBookFiles
    if (onLaunchSignInAndSync != null) {
      onLaunchSignInAndSync(allowBookFiles) { request ->
        runCatching { consentLauncher.launch(request) }
      }
    } else {
      scope.launch {
        val result = onSignInAndSync(allowBookFiles)
        if (result is SyncActionResult.NeedsConsent) {
          runCatching {
            consentLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent).build())
          }
        }
      }
    }
  }
  fun triggerSync() {
    if (shouldConfirmMeteredBookSync()) {
      showMeteredSyncConfirm = true
    } else {
      startSync(allowBookFiles = syncState.syncBookFiles)
    }
  }
  if (showMeteredSyncConfirm) {
    AlertDialog(
      onDismissRequest = { showMeteredSyncConfirm = false },
      containerColor = MaterialTheme.colorScheme.background,
      title = { EInkText(stringResource(R.string.sync_metered_confirm_title), style = MaterialTheme.typography.titleMedium) },
      text = { EInkText(stringResource(R.string.sync_metered_confirm_message), style = MaterialTheme.typography.bodyMedium) },
      confirmButton = {
        Column(horizontalAlignment = Alignment.End) {
          TextButton(
            onClick = {
              showMeteredSyncConfirm = false
              startSync(allowBookFiles = true)
            },
          ) {
            EInkText(stringResource(R.string.sync_metered_allow_books), style = MaterialTheme.typography.labelLarge)
          }
          TextButton(
            onClick = {
              showMeteredSyncConfirm = false
              startSync(allowBookFiles = false)
            },
          ) {
            EInkText(stringResource(R.string.sync_metered_metadata_only), style = MaterialTheme.typography.labelLarge)
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showMeteredSyncConfirm = false }) {
          EInkText(
            stringResource(R.string.action_cancel),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      },
    )
  }
  val todaySeconds = stats.todaySeconds()
  val todayMinutes = todaySeconds / 60
  val goalMinutes = stats.dailyGoalMinutes.coerceAtLeast(5)
  val progress = (todaySeconds.toFloat() / (goalMinutes * 60f)).coerceIn(0f, 1f)
  val weekSeries =
    remember(stats.dailyStats) {
      ReadingStatsRepository.recentDaysSeries(stats.dailyStats, days = 7, today = LocalDate.now())
    }
  val heatmapDays =
    remember(stats.dailyStats) {
      ReadingStatsRepository.recentDaysSeries(stats.dailyStats, days = 84, today = LocalDate.now())
    }
  val activeDaysCount = remember(stats.dailyStats) { stats.dailyStats.count { it.secondsRead > 0 } }
  val avgMinutes7d = remember(weekSeries) { (weekSeries.sumOf { it.secondsRead } / 60) / 7 }
  val pagesPerHour =
    remember(stats.totalSeconds, stats.totalPagesTurned) {
      if (stats.totalSeconds >= 60L) ((stats.totalPagesTurned * 3600L) / stats.totalSeconds).toInt()
      else 0
    }
  val rank =
    remember(stats, completedBooksCount, highlightsCount) {
      ReadingGamification.rankFor(stats, completedBooksCount, highlightsCount, context)
    }
  val badges =
    remember(stats, completedBooksCount, highlightsCount) {
      ReadingGamification.badgesFor(stats, completedBooksCount, highlightsCount, context)
    }

  var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
  Column(
    modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val backCd = stringResource(R.string.cd_back)
      TextButton(
        onClick = onBack,
        modifier =
          Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = backCd },
      ) { EInkText("‹", style = MaterialTheme.typography.titleLarge) }
      EInkText(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleLarge)
    }
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
    Row(modifier = Modifier.fillMaxWidth()) {
      listOf(R.string.stats_tab_overview, R.string.stats_tab_settings).forEachIndexed { index, labelRes ->
        val active = selectedTab == index
        Box(
          modifier =
            Modifier.weight(1f)
              .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
              .clickable { selectedTab = index }
              .semantics {
                role = androidx.compose.ui.semantics.Role.Tab
                selected = active
              }
              .padding(vertical = 11.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.titleMedium,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
          )
        }
      }
    }
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)

    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      if (selectedTab == 1) {
        EInkDisplaySettingsCard(settings = settings, onUpdateSettings = onUpdateSettings)
        LanguageCard(currentLanguage = currentLanguage, onChangeLanguage = onChangeLanguage)
        GoogleSyncCard(
          syncState = syncState,
          onSyncOrSignIn = ::triggerSync,
          onToggleSyncBookFiles = onToggleSyncBookFiles,
          onDeleteCloudBook = { book ->
            if (onLaunchDeleteCloudBook != null) {
              onLaunchDeleteCloudBook(book)
            } else {
              scope.launch { onDeleteCloudBook(book) }
            }
          },
          onDeleteAllCloudData = {
            if (onLaunchDeleteAllCloudData != null) {
              onLaunchDeleteAllCloudData()
            } else {
              scope.launch { onDeleteAllCloudData() }
            }
          },
          onSignOut = onSignOutSync,
        )
        SystemEInkModeCard()
        PrivacyPolicyCard()
        ContactCard()
      } else {
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
              text = stringResource(R.string.stats_rank_format, rank.level, rank.title),
              style = MaterialTheme.typography.titleMedium,
            )
            EInkText(
              text = androidx.compose.ui.res.pluralStringResource(
                R.plurals.stats_xp_streak_plurals,
                stats.currentStreak,
                rank.xp,
                stats.currentStreak,
                stats.longestStreak,
              ),
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
          if (rank.nextLevelXp != null && rank.nextLevelTitle != null) {
            stringResource(R.string.stats_xp_to_next, (rank.nextLevelXp - rank.xp).coerceAtLeast(0), rank.nextLevelTitle)
          } else {
            stringResource(R.string.stats_max_rank)
          }
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
          EInkText(stringResource(R.string.stats_daily_goal), style = MaterialTheme.typography.titleSmall)
          EInkText(
            text = stringResource(R.string.stats_goal_progress, todayMinutes, goalMinutes, (progress * 100).toInt()),
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
          EInkText(stringResource(R.string.stats_set_daily_goal), style = MaterialTheme.typography.bodyMedium)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            GoalStepCircleButton(
              label = "−5",
              contentDesc = stringResource(R.string.cd_goal_decrease),
              onClick = { onChangeGoalMinutes(goalMinutes - 5) },
            )
            EInkText(stringResource(R.string.stats_goal_chip_min, goalMinutes), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            GoalStepCircleButton(
              label = "+5",
              contentDesc = stringResource(R.string.cd_goal_increase),
              onClick = { onChangeGoalMinutes(goalMinutes + 5) },
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        StatSummaryBox(
          label = stringResource(R.string.stats_total_time),
          value = formatDuration(stats.totalSeconds),
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StatSummaryBox(
          label = stringResource(R.string.stats_pages_turned),
          value = "${stats.totalPagesTurned}",
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StatSummaryBox(
          label = stringResource(R.string.stats_books_finished),
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
      }
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
    EInkText(stringResource(R.string.stats_eink_system_title), style = MaterialTheme.typography.titleSmall)
    EInkText(
      text = stringResource(R.string.stats_eink_system_desc),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    OutlinedButton(
      onClick = { com.example.emptyapp.system.EInkSystemSetupHelper.openHomeLauncherSettings(context) },
      modifier = Modifier.fillMaxWidth(),
    ) {
      EInkText(stringResource(R.string.stats_set_launcher))
    }
    OutlinedButton(
      onClick = { com.example.emptyapp.system.EInkSystemSetupHelper.openLiveWallpaperPicker(context) },
      modifier = Modifier.fillMaxWidth(),
    ) {
      EInkText(stringResource(R.string.stats_set_wallpaper))
    }
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
      EInkText(stringResource(R.string.stats_badges), style = MaterialTheme.typography.titleSmall)
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

internal fun buildSevenDayChartAccessibilityDescription(
  chartTitle: String,
  chartSubtitle: String,
  weekSeries: List<DailyReadingStat>,
  minUnit: String,
  isEn: Boolean,
): String {
  val header = "$chartTitle. $chartSubtitle"
  if (weekSeries.isEmpty()) return header
  val dayEntries =
    weekSeries.joinToString(", ") { stat ->
      val minutes = stat.secondsRead / 60
      val weekday = shortWeekdayLabel(stat.date, isEn)
      "$weekday: $minutes$minUnit"
    }
  return "$header. $dayEntries"
}

internal fun buildHeatmapAccessibilityDescription(
  heatmapTitle: String,
  activeDaysSummary: String,
  days: List<DailyReadingStat>,
  goalSeconds: Int,
): String {
  val totalDays = days.size
  val activeInWindow = days.count { it.secondsRead > 0 }
  val effectiveGoal = goalSeconds.coerceAtLeast(60)
  val goalMetCount = days.count { it.secondsRead >= effectiveGoal }
  return "$heatmapTitle. $activeDaysSummary ($activeInWindow/$totalDays, $goalMetCount ≥ 100%)"
}

@Composable
private fun SevenDayBarChartCard(
  weekSeries: List<DailyReadingStat>,
  goalMinutes: Int,
  avgMinutes7d: Int,
  pagesPerHour: Int,
) {
  val maxMinutes = maxOf(goalMinutes, weekSeries.maxOfOrNull { it.secondsRead / 60 } ?: 0, 1)
  val isEn = com.example.emptyapp.LocaleHelper.effectiveLanguageCode(LocalContext.current) == "en"
  val minUnit = if (isEn) "m" else "p"
  val chartTitle = stringResource(R.string.stats_chart_7d)
  val chartSubtitle = stringResource(R.string.stats_chart_7d_sub, avgMinutes7d, pagesPerHour)
  val chartA11yDesc =
    remember(chartTitle, chartSubtitle, weekSeries, minUnit, isEn) {
      buildSevenDayChartAccessibilityDescription(
        chartTitle = chartTitle,
        chartSubtitle = chartSubtitle,
        weekSeries = weekSeries,
        minUnit = minUnit,
        isEn = isEn,
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
      EInkText(chartTitle, style = MaterialTheme.typography.titleSmall)
      EInkText(
        text = chartSubtitle,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .height(128.dp)
          .clearAndSetSemantics { contentDescription = chartA11yDesc },
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
            text = if (minutes > 0) "$minutes$minUnit" else "0",
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
            text = shortWeekdayLabel(stat.date, isEn),
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
  val heatmapTitle = stringResource(R.string.stats_heatmap_12w)
  val activeDaysSummary = stringResource(R.string.stats_heatmap_days, activeDaysCount)
  val heatmapA11yDesc =
    remember(heatmapTitle, activeDaysSummary, days, goalSeconds) {
      buildHeatmapAccessibilityDescription(
        heatmapTitle = heatmapTitle,
        activeDaysSummary = activeDaysSummary,
        days = days,
        goalSeconds = goalSeconds,
      )
    }

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
      EInkText(heatmapTitle, style = MaterialTheme.typography.titleSmall)
      EInkText(
        text = activeDaysSummary,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clearAndSetSemantics { contentDescription = heatmapA11yDesc },
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
      EInkText(stringResource(R.string.stats_less), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
      Spacer(modifier = Modifier.width(6.dp))
      listOf(0f, 0.28f, 0.6f, 1f).forEach { level ->
        val c = if (level == 0f) bg else lerp(bg, fg, level)
        Box(modifier = Modifier.size(12.dp).background(c).border(1.dp, border))
        Spacer(modifier = Modifier.width(4.dp))
      }
      EInkText(stringResource(R.string.stats_more), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
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

private fun shortWeekdayLabel(isoDate: String, isEn: Boolean = false): String {
  val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
  return if (isEn) {
    when (date.dayOfWeek) {
      DayOfWeek.MONDAY -> "Mon"
      DayOfWeek.TUESDAY -> "Tue"
      DayOfWeek.WEDNESDAY -> "Wed"
      DayOfWeek.THURSDAY -> "Thu"
      DayOfWeek.FRIDAY -> "Fri"
      DayOfWeek.SATURDAY -> "Sat"
      DayOfWeek.SUNDAY -> "Sun"
    }
  } else {
    when (date.dayOfWeek) {
      DayOfWeek.MONDAY -> "T2"
      DayOfWeek.TUESDAY -> "T3"
      DayOfWeek.WEDNESDAY -> "T4"
      DayOfWeek.THURSDAY -> "T5"
      DayOfWeek.FRIDAY -> "T6"
      DayOfWeek.SATURDAY -> "T7"
      DayOfWeek.SUNDAY -> "CN"
    }
  }
}

@Composable
private fun GoalStepCircleButton(
  label: String,
  contentDesc: String,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.size(48.dp)
        .clip(CircleShape)
        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
        .clickable(onClick = onClick)
        .semantics { contentDescription = contentDesc },
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
  onToggleSyncBookFiles: (Boolean) -> Unit,
  onDeleteCloudBook: (SyncedBookProgress) -> Unit = {},
  onDeleteAllCloudData: () -> Unit = {},
  onSignOut: () -> Unit,
) {
  var confirmDeleteAll by remember { mutableStateOf(false) }
  var pendingDeleteCloudBook by remember { mutableStateOf<SyncedBookProgress?>(null) }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EInkText(
      text = stringResource(R.string.stats_sync_header),
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
          stringResource(R.string.stats_sync_last, formatted)
        } else {
          stringResource(R.string.stats_sync_never)
        }
      EInkText(
        text = syncState.statusMessage ?: syncTimeText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
      )
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .toggleable(
              value = syncState.syncBookFiles,
              enabled = !syncState.syncing,
              role = Role.Switch,
              onValueChange = onToggleSyncBookFiles,
            )
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        EInkText(
          text = stringResource(R.string.stats_sync_books),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )
        Switch(
          checked = syncState.syncBookFiles,
          onCheckedChange = null,
          enabled = !syncState.syncing,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onSyncOrSignIn,
          enabled = !syncState.syncing,
        ) {
          val btnText =
            when {
              syncState.syncing -> stringResource(R.string.stats_sync_syncing)
              syncState.needsReconnect -> stringResource(R.string.sync_reconnect_btn)
              else -> stringResource(R.string.stats_sync_now)
            }
          EInkText(btnText)
        }
        OutlinedButton(
          onClick = { confirmDeleteAll = true },
          enabled = !syncState.syncing,
        ) {
          EInkText(stringResource(R.string.stats_sync_delete_all_btn))
        }
        TextButton(
          onClick = onSignOut,
          enabled = !syncState.syncing,
        ) {
          EInkText(stringResource(R.string.stats_sign_out), color = MaterialTheme.colorScheme.secondary)
        }
      }
      if (syncState.cloudBooks.isNotEmpty()) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        EInkText(
          text = stringResource(R.string.stats_sync_cloud_books_header, syncState.cloudBooks.size),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          syncState.cloudBooks.forEach { cloudBook ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                  .padding(horizontal = 10.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                EInkText(
                  text = cloudBook.title,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                )
                val meta =
                  listOfNotNull(
                    cloudBook.format.label,
                    cloudBook.author.takeIf { it.isNotBlank() },
                    "${(cloudBook.progress * 100).toInt()}%",
                  ).joinToString(" · ")
                EInkText(
                  text = meta,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                  maxLines = 1,
                )
              }
              TextButton(
                onClick = { pendingDeleteCloudBook = cloudBook },
                enabled = !syncState.syncing,
              ) {
                EInkText(
                  text = stringResource(R.string.stats_sync_delete_book_btn),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                )
              }
            }
          }
        }
      }
    } else {
      EInkText(
        text = stringResource(R.string.stats_sync_intro),
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
        EInkText(if (syncState.syncing) stringResource(R.string.stats_sync_connecting) else stringResource(R.string.stats_sign_in_google))
      }
    }
  }

  if (confirmDeleteAll) {
    AlertDialog(
      onDismissRequest = { confirmDeleteAll = false },
      containerColor = MaterialTheme.colorScheme.background,
      title = { EInkText(stringResource(R.string.stats_sync_delete_all_title), style = MaterialTheme.typography.titleMedium) },
      text = { EInkText(stringResource(R.string.stats_sync_delete_all_message), style = MaterialTheme.typography.bodyMedium) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDeleteAll = false
            onDeleteAllCloudData()
          },
        ) {
          EInkText(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelLarge)
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDeleteAll = false }) {
          EInkText(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
      },
    )
  }

  pendingDeleteCloudBook?.let { targetBook ->
    AlertDialog(
      onDismissRequest = { pendingDeleteCloudBook = null },
      containerColor = MaterialTheme.colorScheme.background,
      title = { EInkText(stringResource(R.string.stats_sync_delete_book_title), style = MaterialTheme.typography.titleMedium) },
      text = {
        EInkText(
          stringResource(R.string.stats_sync_delete_book_message, targetBook.title),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            pendingDeleteCloudBook = null
            onDeleteCloudBook(targetBook)
          },
        ) {
          EInkText(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelLarge)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteCloudBook = null }) {
          EInkText(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
      },
    )
  }
}

@Composable
private fun EInkDisplaySettingsCard(
  settings: EInkSettings,
  onUpdateSettings: ((EInkSettings) -> EInkSettings) -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    EInkText(
      text = stringResource(R.string.settings_display_title),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    EInkText(
      text = stringResource(R.string.settings_tone),
      style = MaterialTheme.typography.bodyMedium,
    )
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EInkTone.entries.forEach { tone ->
        val selected = tone == settings.tone
        val label =
          when (tone) {
            EInkTone.PAPER -> stringResource(R.string.tone_paper)
            EInkTone.WARM -> stringResource(R.string.tone_warm)
            EInkTone.HIGH_CONTRAST -> stringResource(R.string.tone_white)
            EInkTone.SOFT_GRAY -> stringResource(R.string.tone_gray)
            EInkTone.DARK -> stringResource(R.string.tone_dark)
          }
        Box(
          modifier =
            Modifier
              .border(1.5.dp, MaterialTheme.colorScheme.outline)
              .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
              .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onUpdateSettings { it.copy(tone = tone) } },
              )
              .padding(horizontal = 12.dp, vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
          )
        }
      }
    }
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .toggleable(
            value = settings.paperGrain,
            role = Role.Switch,
            onValueChange = { on -> onUpdateSettings { it.copy(paperGrain = on) } },
          )
          .semantics(mergeDescendants = true) {},
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      EInkText(stringResource(R.string.settings_paper_grain), style = MaterialTheme.typography.bodyMedium)
      Switch(
        checked = settings.paperGrain,
        onCheckedChange = null,
      )
    }
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .toggleable(
            value = settings.grayscale,
            role = Role.Switch,
            onValueChange = { on -> onUpdateSettings { it.copy(grayscale = on) } },
          )
          .semantics(mergeDescendants = true) {},
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      EInkText(stringResource(R.string.settings_grayscale), style = MaterialTheme.typography.bodyMedium)
      Switch(
        checked = settings.grayscale,
        onCheckedChange = null,
      )
    }
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .toggleable(
            value = settings.flashEnabled,
            role = Role.Switch,
            onValueChange = { on -> onUpdateSettings { it.copy(flashEnabled = on) } },
          )
          .semantics(mergeDescendants = true) {},
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      EInkText(stringResource(R.string.onboard_toggle_flash), style = MaterialTheme.typography.bodyMedium)
      Switch(
        checked = settings.flashEnabled,
        onCheckedChange = null,
      )
    }
    EInkText(
      text = stringResource(R.string.settings_orientation),
      style = MaterialTheme.typography.bodyMedium,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OrientationLock.entries.forEach { lock ->
        val selected = lock == settings.orientationLock
        val label =
          when (lock) {
            OrientationLock.AUTO -> stringResource(R.string.orientation_auto)
            OrientationLock.PORTRAIT -> stringResource(R.string.orientation_portrait)
            OrientationLock.LANDSCAPE -> stringResource(R.string.orientation_landscape)
          }
        Box(
          modifier =
            Modifier.weight(1f)
              .border(1.5.dp, MaterialTheme.colorScheme.outline)
              .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
              .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onUpdateSettings { it.copy(orientationLock = lock) } },
              )
              .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun LanguageCard(
  currentLanguage: AppLanguage,
  onChangeLanguage: (AppLanguage) -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    EInkText(
      text = stringResource(R.string.stats_language_header),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AppLanguage.entries.forEach { lang ->
        val selected = lang == currentLanguage
        val label =
          when (lang) {
            AppLanguage.SYSTEM -> stringResource(R.string.language_system)
            AppLanguage.VI -> stringResource(R.string.language_vi)
            AppLanguage.EN -> stringResource(R.string.language_en)
          }
        Box(
          modifier =
            Modifier.weight(1f)
              .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
              )
              .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
              .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { if (!selected) onChangeLanguage(lang) },
              )
              .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
private fun PrivacyPolicyCard() {
  val context = LocalContext.current
  val policyUrl = stringResource(R.string.privacy_policy_url)
  var showDialog by remember { mutableStateOf(false) }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EInkText(
      text = stringResource(R.string.stats_privacy_header),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    EInkText(
      text = stringResource(R.string.stats_privacy_desc),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { showDialog = true }) {
        EInkText(stringResource(R.string.stats_privacy_view_btn))
      }
      TextButton(
        onClick = {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl))
          runCatching { context.startActivity(intent) }
        },
      ) {
        EInkText(stringResource(R.string.stats_privacy_web_btn), color = MaterialTheme.colorScheme.secondary)
      }
    }
  }

  if (showDialog) {
    AlertDialog(
      onDismissRequest = { showDialog = false },
      containerColor = MaterialTheme.colorScheme.background,
      title = { EInkText(stringResource(R.string.privacy_dialog_title), style = MaterialTheme.typography.titleMedium) },
      text = {
        Column(
          modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState()),
        ) {
          EInkText(
            text = stringResource(R.string.privacy_dialog_body),
            style = MaterialTheme.typography.bodySmall,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showDialog = false }) {
          EInkText(stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge)
        }
      },
    )
  }
}

@Composable
private fun ContactCard() {
  val context = LocalContext.current
  val email = stringResource(R.string.contact_email)
  val emailSubject = stringResource(R.string.stats_email_subject)
  val noEmailAppMsg = stringResource(R.string.stats_no_email_app)
  val copiedEmailMsg = stringResource(R.string.stats_copied_email, email)
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EInkText(
      text = stringResource(R.string.stats_contact_header),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    EInkText(
      text = stringResource(R.string.stats_contact_desc),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    EInkText(text = email, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = {
          val intent =
            Intent(Intent.ACTION_SENDTO).apply {
              data = Uri.parse("mailto:$email?subject=${Uri.encode(emailSubject)}")
            }
          val launched = runCatching { context.startActivity(intent) }.isSuccess
          if (!launched) {
            Toast.makeText(context, noEmailAppMsg, Toast.LENGTH_SHORT).show()
          }
        },
      ) {
        EInkText(stringResource(R.string.stats_send_email))
      }
      TextButton(
        onClick = {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          clipboard.setPrimaryClip(ClipData.newPlainText("Email", email))
          Toast.makeText(context, copiedEmailMsg, Toast.LENGTH_SHORT).show()
        },
      ) {
        EInkText(stringResource(R.string.stats_copy), color = MaterialTheme.colorScheme.secondary)
      }
    }
  }
}

@Composable
private fun formatDuration(totalSeconds: Long): String {
  val totalMinutes = totalSeconds / 60L
  val hours = totalMinutes / 60L
  val minutes = totalMinutes % 60L
  val isEn = com.example.emptyapp.LocaleHelper.effectiveLanguageCode(LocalContext.current) == "en"
  val hUnit = if (isEn) "h" else "g"
  val mUnit = if (isEn) "m" else "p"
  return if (hours > 0L) "${hours}$hUnit ${minutes}$mUnit" else "${minutes}$mUnit"
}
