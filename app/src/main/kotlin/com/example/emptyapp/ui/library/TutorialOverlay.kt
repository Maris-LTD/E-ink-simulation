package com.example.emptyapp.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.emptyapp.R
import com.example.emptyapp.data.billing.ProFeature
import com.example.emptyapp.data.billing.ProFeatureGate
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.EInkTone
import com.example.emptyapp.reader.LockScreenReadingNotifier
import com.example.emptyapp.system.EInkSystemSetupHelper
import com.example.emptyapp.ui.eink.EInkText
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 4

@Composable
fun TutorialOverlay(
  settings: EInkSettings,
  onUpdateSettings: ((EInkSettings) -> EInkSettings) -> Unit,
  onImportBook: () -> Unit,
  onMarkSeen: () -> Unit = {},
  onDismiss: () -> Unit,
  initialPage: Int = 0,
  proGate: ProFeatureGate? = null,
  onRequirePro: (ProFeature) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val pagerState = rememberPagerState(
    initialPage = initialPage.coerceIn(0, ONBOARDING_PAGE_COUNT - 1),
    pageCount = { ONBOARDING_PAGE_COUNT },
  )
  val scope = rememberCoroutineScope()

  var wallpaperActive by remember { mutableStateOf(EInkSystemSetupHelper.isLiveWallpaperActive(context)) }
  var launcherActive by remember { mutableStateOf(EInkSystemSetupHelper.isInkifyDefaultLauncher(context)) }
  var notificationGranted by remember { mutableStateOf(LockScreenReadingNotifier.hasPermission(context)) }

  fun refreshSystemStates() {
    wallpaperActive = EInkSystemSetupHelper.isLiveWallpaperActive(context)
    launcherActive = EInkSystemSetupHelper.isInkifyDefaultLauncher(context)
    notificationGranted = LockScreenReadingNotifier.hasPermission(context)
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        refreshSystemStates()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val notifPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notificationGranted = granted || LockScreenReadingNotifier.hasPermission(context)
      if (notificationGranted) {
        onUpdateSettings { it.copy(lockScreenReader = true) }
      }
    }

  BackHandler(onBack = onDismiss)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent().changes.forEach { it.consume() }
            }
          }
        }
        .padding(16.dp),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        EInkText(stringResource(R.string.onboard_header), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onDismiss) {
          EInkText(stringResource(R.string.tutorial_skip), color = MaterialTheme.colorScheme.secondary)
        }
      }
      HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)

      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) { pageIndex ->
        Column(
          modifier =
            Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          when (pageIndex) {
            0 -> StepDisplaySetup(settings = settings, onUpdateSettings = onUpdateSettings)
            1 ->
              StepLockScreenSetup(
                wallpaperActive = wallpaperActive,
                notificationActive = notificationGranted && settings.lockScreenReader,
                onSetWallpaper = {
                  EInkSystemSetupHelper.openLiveWallpaperPicker(context)
                },
                onEnableNotification = {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !LockScreenReadingNotifier.hasPermission(context)
                  ) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  } else {
                    notificationGranted = true
                    onUpdateSettings { it.copy(lockScreenReader = true) }
                  }
                },
              )
            2 ->
              StepLauncherSetup(
                launcherActive = launcherActive,
                onSetLauncher = {
                  onMarkSeen()
                  scope.launch { pagerState.scrollToPage(3) }
                  EInkSystemSetupHelper.openHomeLauncherSettings(context)
                },
                onSkipStep = {
                  scope.launch { pagerState.animateScrollToPage(3) }
                },
              )
            else ->
              StepReadyToRead(
                onImportBook = {
                  onDismiss()
                  onImportBook()
                },
                onGoToLibrary = onDismiss,
              )
          }
        }
      }

      HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = {
            if (pagerState.currentPage > 0) {
              scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
          },
          enabled = pagerState.currentPage > 0,
        ) {
          EInkText(stringResource(R.string.tutorial_prev))
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          repeat(ONBOARDING_PAGE_COUNT) { index ->
            val selected = index == pagerState.currentPage
            Box(
              modifier =
                Modifier.size(if (selected) 10.dp else 7.dp)
                  .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                  ),
            )
          }
        }

        val isLast = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1
        OutlinedButton(
          onClick = {
            if (isLast) onDismiss()
            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
          },
        ) {
          EInkText(if (isLast) stringResource(R.string.tutorial_start) else stringResource(R.string.tutorial_next))
        }
      }
    }
  }
}

@Composable
private fun StepHeader(tag: String, title: String, desc: String) {
  EInkText(tag, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
  EInkText(title, style = MaterialTheme.typography.headlineSmall)
  EInkText(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun StepDisplaySetup(
  settings: EInkSettings,
  onUpdateSettings: ((EInkSettings) -> EInkSettings) -> Unit,
) {
  StepHeader(
    tag = stringResource(R.string.onboard_s1_tag),
    title = stringResource(R.string.onboard_s1_title),
    desc = stringResource(R.string.onboard_s1_desc),
  )

  val previewBg = Color(settings.tone.background)
  val previewFg = Color(settings.tone.text)
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(previewBg)
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    EInkText(
      stringResource(R.string.onboard_sample_title),
      style = MaterialTheme.typography.titleSmall,
      color = previewFg,
    )
    EInkText(
      stringResource(R.string.onboard_sample_body),
      style = MaterialTheme.typography.bodyMedium,
      color = previewFg,
    )
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ToneChip(
      label = stringResource(R.string.onboard_tone_contrast),
      selected = settings.tone == EInkTone.HIGH_CONTRAST,
      onClick = { onUpdateSettings { it.copy(tone = EInkTone.HIGH_CONTRAST, paperGrain = false) } },
      modifier = Modifier.weight(1f),
    )
    ToneChip(
      label = stringResource(R.string.onboard_tone_paper),
      selected = settings.tone == EInkTone.PAPER,
      onClick = { onUpdateSettings { it.copy(tone = EInkTone.PAPER, paperGrain = true) } },
      modifier = Modifier.weight(1f),
    )
    ToneChip(
      label = stringResource(R.string.onboard_tone_gray),
      selected = settings.tone == EInkTone.SOFT_GRAY,
      onClick = { onUpdateSettings { it.copy(tone = EInkTone.SOFT_GRAY, paperGrain = false) } },
      modifier = Modifier.weight(1f),
    )
  }

  ToggleOptionRow(
    label = stringResource(R.string.onboard_toggle_flash),
    checked = settings.flashEnabled,
    onCheckedChange = { on -> onUpdateSettings { it.copy(flashEnabled = on) } },
  )
  ToggleOptionRow(
    label = stringResource(R.string.settings_paper_grain),
    checked = settings.paperGrain,
    onCheckedChange = { on -> onUpdateSettings { it.copy(paperGrain = on) } },
  )
}

@Composable
private fun StepLockScreenSetup(
  wallpaperActive: Boolean,
  notificationActive: Boolean,
  onSetWallpaper: () -> Unit,
  onEnableNotification: () -> Unit,
) {
  StepHeader(
    tag = stringResource(R.string.onboard_s2_tag),
    title = stringResource(R.string.onboard_s2_title),
    desc = stringResource(R.string.onboard_s2_desc),
  )
  OnboardingIllustration(step = 1, modifier = Modifier.fillMaxWidth().height(120.dp))
  ActionSetupCard(
    title = stringResource(R.string.onboard_btn_wallpaper),
    active = wallpaperActive,
    activeLabel = stringResource(R.string.onboard_status_enabled),
    onClick = onSetWallpaper,
  )
  ActionSetupCard(
    title = stringResource(R.string.onboard_btn_notification),
    active = notificationActive,
    activeLabel = stringResource(R.string.onboard_status_enabled),
    onClick = onEnableNotification,
  )
}

@Composable
private fun StepLauncherSetup(
  launcherActive: Boolean,
  onSetLauncher: () -> Unit,
  onSkipStep: () -> Unit,
) {
  StepHeader(
    tag = stringResource(R.string.onboard_s3_tag),
    title = stringResource(R.string.onboard_s3_title),
    desc = stringResource(R.string.onboard_s3_desc),
  )
  OnboardingIllustration(step = 2, modifier = Modifier.fillMaxWidth().height(120.dp))
  ActionSetupCard(
    title = stringResource(R.string.onboard_btn_launcher),
    active = launcherActive,
    activeLabel = stringResource(R.string.onboard_status_active),
    onClick = onSetLauncher,
  )
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
    TextButton(onClick = onSkipStep) {
      EInkText(stringResource(R.string.onboard_later), color = MaterialTheme.colorScheme.secondary)
    }
  }
}

@Composable
private fun StepReadyToRead(
  onImportBook: () -> Unit,
  onGoToLibrary: () -> Unit,
) {
  StepHeader(
    tag = stringResource(R.string.onboard_s4_tag),
    title = stringResource(R.string.onboard_s4_title),
    desc = stringResource(R.string.onboard_s4_desc),
  )
  OnboardingIllustration(step = 3, modifier = Modifier.fillMaxWidth().height(120.dp))
  Button(
    onClick = onImportBook,
    modifier = Modifier.fillMaxWidth(),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
      ),
  ) {
    EInkText(stringResource(R.string.onboard_btn_import), color = MaterialTheme.colorScheme.onPrimary)
  }
  OutlinedButton(
    onClick = onGoToLibrary,
    modifier = Modifier.fillMaxWidth(),
  ) {
    EInkText(stringResource(R.string.onboard_btn_library))
  }
}

@Composable
private fun ToneChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 10.dp, horizontal = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    EInkText(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
    )
  }
}

@Composable
private fun ToggleOptionRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics(mergeDescendants = true) {}
        .padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EInkText(label, style = MaterialTheme.typography.titleSmall)
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
private fun ActionSetupCard(
  title: String,
  active: Boolean,
  activeLabel: String,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .background(
          if (active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          else MaterialTheme.colorScheme.background,
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EInkText(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(8.dp))
    EInkText(
      text = if (active) activeLabel else "›",
      style = MaterialTheme.typography.labelLarge,
      color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
    )
  }
}

@Composable
private fun OnboardingIllustration(step: Int, modifier: Modifier = Modifier) {
  val ink = MaterialTheme.colorScheme.onBackground
  val surface = MaterialTheme.colorScheme.surfaceVariant
  Box(
    modifier = modifier.border(1.5.dp, MaterialTheme.colorScheme.outline).background(surface),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
      val w = size.width
      val h = size.height
      val stroke = 2.5.dp.toPx()
      when (step) {
        1 -> {
          val pw = h * 0.62f
          val px = (w - pw) / 2f
          drawRoundRect(
            color = ink,
            topLeft = Offset(px, 0f),
            size = Size(pw, h),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(stroke),
          )
          drawRect(color = ink, topLeft = Offset(px + pw * 0.14f, h * 0.34f), size = Size(pw * 0.72f, h * 0.14f), style = Stroke(stroke * 0.8f))
          drawRect(color = ink, topLeft = Offset(px + pw * 0.14f, h * 0.56f), size = Size(pw * 0.72f, h * 0.32f), style = Stroke(stroke * 0.9f))
          drawLine(ink, Offset(px + pw * 0.22f, h * 0.66f), Offset(px + pw * 0.78f, h * 0.66f), strokeWidth = stroke * 0.7f)
          drawLine(ink, Offset(px + pw * 0.22f, h * 0.76f), Offset(px + pw * 0.70f, h * 0.76f), strokeWidth = stroke * 0.7f)
        }
        2 -> {
          val pw = h * 0.62f
          val px = (w - pw) / 2f
          drawRoundRect(
            color = ink,
            topLeft = Offset(px, 0f),
            size = Size(pw, h),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(stroke),
          )
          drawRect(color = ink, topLeft = Offset(px + pw * 0.15f, h * 0.16f), size = Size(pw * 0.70f, h * 0.24f), style = Stroke(stroke * 0.8f))
          for (i in 0 until 4) {
            val bx = px + pw * (0.16f + i * 0.18f)
            drawRect(color = ink, topLeft = Offset(bx, h * 0.54f), size = Size(pw * 0.13f, pw * 0.13f), style = Stroke(stroke * 0.8f))
          }
        }
        else -> {
          val bw = w * 0.34f
          val bh = h * 0.74f
          val bx = (w - bw) / 2f
          val by = (h - bh) / 2f
          drawRect(color = ink, topLeft = Offset(bx, by), size = Size(bw, bh), style = Stroke(stroke))
          drawLine(ink, Offset(w / 2f, by), Offset(w / 2f, by + bh), strokeWidth = stroke)
          drawLine(ink, Offset(w * 0.68f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
          drawLine(ink, Offset(w * 0.73f, h * 0.38f), Offset(w * 0.73f, h * 0.62f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
      }
    }
  }
}
