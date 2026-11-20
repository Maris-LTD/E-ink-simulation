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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
import com.example.emptyapp.ui.eink.CheckIcon
import com.example.emptyapp.ui.eink.ChevronRightIcon
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkSerifFamily
import com.example.emptyapp.ui.eink.EInkShapes
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
      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)

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

      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
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
          shape = EInkShapes.Control,
        ) {
          EInkText(stringResource(R.string.tutorial_prev))
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          repeat(ONBOARDING_PAGE_COUNT) { index ->
            val selected = index == pagerState.currentPage
            if (selected) {
              Box(
                modifier =
                  Modifier.size(width = 20.dp, height = 6.dp)
                    .clip(EInkShapes.Pill)
                    .background(MaterialTheme.colorScheme.primary),
              )
            } else {
              Box(
                modifier =
                  Modifier.size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
              )
            }
          }
        }

        val isLast = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1
        Button(
          onClick = {
            if (isLast) onDismiss()
            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
          },
          shape = EInkShapes.Control,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
          EInkText(
            text = if (isLast) stringResource(R.string.tutorial_start) else stringResource(R.string.tutorial_next),
            color = MaterialTheme.colorScheme.onPrimary,
          )
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
        .clip(EInkShapes.Card)
        .background(previewBg, EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EInkText(
      stringResource(R.string.onboard_sample_title),
      style = MaterialTheme.typography.titleMedium.copy(fontFamily = EInkSerifFamily),
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
  OnboardingIllustration(step = 1, modifier = Modifier.fillMaxWidth().height(132.dp))
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
  OnboardingIllustration(step = 2, modifier = Modifier.fillMaxWidth().height(132.dp))
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
  OnboardingIllustration(step = 3, modifier = Modifier.fillMaxWidth().height(132.dp))
  Button(
    onClick = onImportBook,
    shape = EInkShapes.Control,
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
    shape = EInkShapes.Control,
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
        .clip(EInkShapes.Control)
        .background(
          if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
          EInkShapes.Control,
        )
        .border(
          width = if (selected) EInkBorder.Card else EInkBorder.Hairline,
          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          shape = EInkShapes.Control,
        )
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
        .clip(EInkShapes.Card)
        .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics(mergeDescendants = true) {}
        .padding(horizontal = 14.dp, vertical = 6.dp),
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
        .clip(EInkShapes.Card)
        .background(
          if (active) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
          EInkShapes.Card,
        )
        .border(
          width = if (active) EInkBorder.Active else EInkBorder.Card,
          color = if (active) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
          shape = EInkShapes.Card,
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EInkText(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(8.dp))
    if (active) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CheckIcon(modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        EInkText(
          text = activeLabel,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    } else {
      ChevronRightIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
    }
  }
}

@Composable
private fun OnboardingIllustration(step: Int, modifier: Modifier = Modifier) {
  val ink = MaterialTheme.colorScheme.onBackground
  val muted = MaterialTheme.colorScheme.outline
  val surface = MaterialTheme.colorScheme.surfaceVariant
  val bg = MaterialTheme.colorScheme.background
  Box(
    modifier =
      modifier
        .clip(EInkShapes.Hero)
        .background(surface, EInkShapes.Hero)
        .border(EInkBorder.Hairline, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Hero),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
      val w = size.width
      val h = size.height
      val stroke = 1.8.dp.toPx()
      when (step) {
        1 -> {
          val pw = h * 0.62f
          val px = (w - pw) / 2f
          drawRoundRect(bg, Offset(px, 0f), Size(pw, h), CornerRadius(10.dp.toPx()))
          drawRoundRect(ink, Offset(px, 0f), Size(pw, h), CornerRadius(10.dp.toPx()), style = Stroke(stroke))
          drawLine(muted, Offset(px + pw * 0.36f, h * 0.08f), Offset(px + pw * 0.64f, h * 0.08f), stroke, StrokeCap.Round)
          drawRoundRect(surface, Offset(px + pw * 0.18f, h * 0.18f), Size(pw * 0.64f, h * 0.15f), CornerRadius(4.dp.toPx()))
          drawRoundRect(surface, Offset(px + pw * 0.12f, h * 0.48f), Size(pw * 0.76f, h * 0.38f), CornerRadius(6.dp.toPx()))
          drawRoundRect(ink, Offset(px + pw * 0.12f, h * 0.48f), Size(pw * 0.76f, h * 0.38f), CornerRadius(6.dp.toPx()), style = Stroke(stroke * 0.75f))
          drawLine(ink, Offset(px + pw * 0.22f, h * 0.60f), Offset(px + pw * 0.76f, h * 0.60f), stroke * 0.7f, StrokeCap.Round)
          drawLine(muted, Offset(px + pw * 0.22f, h * 0.70f), Offset(px + pw * 0.68f, h * 0.70f), stroke * 0.7f, StrokeCap.Round)
          drawLine(muted, Offset(px + pw * 0.22f, h * 0.78f), Offset(px + pw * 0.52f, h * 0.78f), stroke * 0.7f, StrokeCap.Round)
        }
        2 -> {
          val pw = h * 0.62f
          val px = (w - pw) / 2f
          drawRoundRect(bg, Offset(px, 0f), Size(pw, h), CornerRadius(10.dp.toPx()))
          drawRoundRect(ink, Offset(px, 0f), Size(pw, h), CornerRadius(10.dp.toPx()), style = Stroke(stroke))
          drawRoundRect(surface, Offset(px + pw * 0.14f, h * 0.14f), Size(pw * 0.72f, h * 0.30f), CornerRadius(5.dp.toPx()))
          drawRoundRect(ink, Offset(px + pw * 0.14f, h * 0.14f), Size(pw * 0.72f, h * 0.30f), CornerRadius(5.dp.toPx()), style = Stroke(stroke * 0.75f))
          val r = pw * 0.065f
          for (i in 0 until 4) {
            val cx = px + pw * (0.20f + i * 0.20f)
            drawCircle(surface, r, Offset(cx, h * 0.64f))
            drawCircle(ink, r, Offset(cx, h * 0.64f), style = Stroke(stroke * 0.7f))
          }
          drawRoundRect(muted, Offset(px + pw * 0.14f, h * 0.80f), Size(pw * 0.72f, h * 0.10f), CornerRadius(4.dp.toPx()))
        }
        else -> {
          val bw = w * 0.38f
          val bh = h * 0.72f
          val bx = (w - bw) / 2f
          val by = (h - bh) / 2f
          drawRoundRect(bg, Offset(bx, by), Size(bw, bh), CornerRadius(4.dp.toPx()))
          drawRoundRect(ink, Offset(bx, by), Size(bw, bh), CornerRadius(4.dp.toPx()), style = Stroke(stroke))
          drawLine(ink, Offset(w / 2f, by), Offset(w / 2f, by + bh), stroke)
          listOf(0.28f, 0.45f, 0.62f).forEach { frac ->
            drawLine(muted, Offset(bx + bw * 0.12f, by + bh * frac), Offset(w / 2f - bw * 0.10f, by + bh * frac), stroke * 0.75f, StrokeCap.Round)
            drawLine(muted, Offset(w / 2f + bw * 0.10f, by + bh * frac), Offset(bx + bw * 0.88f, by + bh * frac), stroke * 0.75f, StrokeCap.Round)
          }
          val badgeC = Offset(bx + bw + 10.dp.toPx(), by + bh * 0.5f)
          val br = 12.dp.toPx()
          drawCircle(ink, br, badgeC)
          drawLine(bg, Offset(badgeC.x - br * 0.42f, badgeC.y), Offset(badgeC.x + br * 0.42f, badgeC.y), stroke, StrokeCap.Round)
          drawLine(bg, Offset(badgeC.x, badgeC.y - br * 0.42f), Offset(badgeC.x, badgeC.y + br * 0.42f), stroke, StrokeCap.Round)
        }
      }
    }
  }
}
