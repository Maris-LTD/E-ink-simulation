package com.example.emptyapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.reader.EInkTone
import com.example.emptyapp.ui.eink.BackArrowIcon
import com.example.emptyapp.ui.eink.BookmarkIcon
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText
import com.example.emptyapp.ui.eink.FocusMoonIcon
import com.example.emptyapp.ui.eink.SearchIcon
import com.example.emptyapp.ui.eink.SlidersIcon
import com.example.emptyapp.ui.eink.SunIcon
import com.example.emptyapp.ui.eink.TocListIcon
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun ReaderTopBar(
  title: String,
  showFontControls: Boolean = true,
  showSearch: Boolean = true,
  isBookmarked: Boolean = false,
  pdfModeLabel: String? = null,
  onTogglePdfMode: (() -> Unit)? = null,
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
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val backCd = stringResource(R.string.cd_back)
      IconButton(
        onClick = onClose,
        modifier = Modifier.size(48.dp).semantics { contentDescription = backCd },
      ) {
        BackArrowIcon(modifier = Modifier.size(20.dp))
      }
      EInkText(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(end = 6.dp),
      )
      if (pdfModeLabel != null && onTogglePdfMode != null) {
        Box(
          modifier =
            Modifier.padding(end = 4.dp)
              .clip(EInkShapes.Badge)
              .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Badge)
              .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Badge)
              .clickable(onClick = onTogglePdfMode)
              .padding(horizontal = 10.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(pdfModeLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
      }
      if (showFontControls) {
        val decreaseFontCd = stringResource(R.string.cd_decrease_font)
        val increaseFontCd = stringResource(R.string.cd_increase_font)
        TextButton(
          onClick = onDecreaseFont,
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
          modifier =
            Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
              .semantics { contentDescription = decreaseFontCd },
        ) {
          EInkText("A−", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        TextButton(
          onClick = onIncreaseFont,
          contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
          modifier =
            Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
              .semantics { contentDescription = increaseFontCd },
        ) {
          EInkText("A+", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
      }
      if (showSearch) {
        val searchCd = stringResource(R.string.cd_search_in_book)
        IconButton(
          onClick = onOpenSearch,
          modifier = Modifier.size(48.dp).semantics { contentDescription = searchCd },
        ) {
          SearchIcon(modifier = Modifier.size(19.dp))
        }
      }
      val bookmarkCd = stringResource(R.string.cd_save_bookmark)
      val bookmarkStateDesc =
        stringResource(if (isBookmarked) R.string.cd_bookmark_saved_state else R.string.cd_bookmark_not_saved_state)
      IconButton(
        onClick = onBookmark,
        modifier =
          Modifier.size(48.dp).semantics {
            contentDescription = bookmarkCd
            stateDescription = bookmarkStateDesc
          },
      ) {
        BookmarkIcon(filled = isBookmarked, modifier = Modifier.size(19.dp))
      }
      val contentsCd = stringResource(R.string.cd_contents)
      IconButton(
        onClick = onOpenContents,
        modifier = Modifier.size(48.dp).semantics { contentDescription = contentsCd },
      ) {
        TocListIcon(modifier = Modifier.size(19.dp))
      }
    }
    HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
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
  onBrightnessChangeFinished: () -> Unit,
  onSeekToProgress: (Float) -> Unit,
  focusMode: Boolean,
  onToggleFocusMode: () -> Unit,
  onToggleSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var dragging by remember { mutableStateOf(false) }
  var pendingSeek by remember { mutableStateOf(false) }
  var sliderValue by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }

  LaunchedEffect(progress) {
    if (!dragging && !pendingSeek) sliderValue = progress.coerceIn(0f, 1f)
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
        .clip(EInkShapes.SheetTop)
        .background(MaterialTheme.colorScheme.background, EInkShapes.SheetTop)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.SheetTop)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (seekEnabled) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
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
          modifier = Modifier.weight(1f),
          colors =
            SliderDefaults.colors(
              thumbColor = MaterialTheme.colorScheme.primary,
              activeTrackColor = MaterialTheme.colorScheme.primary,
              inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        EInkText(
          text = "${(sliderValue * 100).toInt()}%",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
      }
    }
    BrightnessStrip(
      brightness = brightness,
      onBrightnessChange = onBrightnessChange,
      onBrightnessChangeFinished = onBrightnessChangeFinished,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FocusModeToggleChip(enabled = focusMode, onToggle = onToggleFocusMode)
      val settingsCd = stringResource(R.string.cd_reader_settings)
      Row(
        modifier =
          Modifier.defaultMinSize(minHeight = 48.dp)
            .clip(EInkShapes.Pill)
            .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Pill)
            .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Pill)
            .clickable(onClick = onToggleSettings)
            .semantics { contentDescription = settingsCd }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SlidersIcon(modifier = Modifier.size(16.dp))
        EInkText(stringResource(R.string.reader_settings_button), style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
private fun ToneSwatchStrip(
  tone: EInkTone,
  onToneChange: (EInkTone) -> Unit,
) {
  val toneDesc = stringResource(R.string.reader_tone_label)
  Row(
    modifier = Modifier.fillMaxWidth().semantics { contentDescription = toneDesc },
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EInkTone.entries.forEach { option ->
      val isSelected = option == tone
      val optionLabel = toneLabel(option)
      Column(
        modifier =
          Modifier.clip(EInkShapes.Control)
            .clickable { onToneChange(option) }
            .semantics {
              contentDescription = optionLabel
              role = Role.RadioButton
              selected = isSelected
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Box(
          modifier =
            Modifier.size(26.dp)
              .clip(CircleShape)
              .background(Color(option.background), CircleShape)
              .border(
                width = if (isSelected) 2.dp else EInkBorder.Card,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
              ),
        )
        EInkText(
          text = optionLabel,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
          ),
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun BrightnessStrip(
  brightness: Float,
  onBrightnessChange: (Float) -> Unit,
  onBrightnessChangeFinished: () -> Unit,
) {
  val isAuto = brightness < 0f
  val displayValue = if (isAuto) 0.5f else brightness.coerceIn(0.02f, 1f)
  val brightnessDesc = stringResource(R.string.reader_brightness_label)
  val toggleCd =
    if (isAuto) stringResource(R.string.cd_brightness_auto)
    else stringResource(R.string.cd_brightness_pct, (displayValue * 100).roundToInt())
  Row(
    modifier = Modifier.fillMaxWidth().semantics { contentDescription = brightnessDesc },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    SunIcon(modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
    Slider(
      value = displayValue,
      onValueChange = { onBrightnessChange(it.coerceIn(0.02f, 1f)) },
      onValueChangeFinished = onBrightnessChangeFinished,
      valueRange = 0.02f..1f,
      modifier = Modifier.weight(1f),
      colors =
        SliderDefaults.colors(
          thumbColor = MaterialTheme.colorScheme.primary,
          activeTrackColor = MaterialTheme.colorScheme.primary,
          inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
    Box(
      modifier =
        Modifier.clip(EInkShapes.Pill)
          .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Pill)
          .border(EInkBorder.Hairline, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Pill)
          .clickable {
            onBrightnessChange(if (isAuto) 0.5f else -1f)
            onBrightnessChangeFinished()
          }
          .semantics { contentDescription = toggleCd }
          .padding(horizontal = 10.dp, vertical = 5.dp),
      contentAlignment = Alignment.Center,
    ) {
      EInkText(
        text = if (isAuto) "Auto" else "${(displayValue * 100).roundToInt()}%",
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun FocusModeToggleChip(
  enabled: Boolean,
  onToggle: () -> Unit,
) {
  val bg = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
  val fg = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
  val focusCd =
    if (enabled) stringResource(R.string.cd_focus_disable) else stringResource(R.string.cd_focus_enable)
  Row(
    modifier =
      Modifier.clip(EInkShapes.Pill)
        .background(bg, EInkShapes.Pill)
        .border(
          width = if (enabled) EInkBorder.Card else EInkBorder.Hairline,
          color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          shape = EInkShapes.Pill,
        )
        .clickable(onClick = onToggle)
        .padding(horizontal = 14.dp, vertical = 8.dp)
        .semantics { contentDescription = focusCd },
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FocusMoonIcon(modifier = Modifier.size(16.dp), tint = fg)
    EInkText(
      text = if (enabled) stringResource(R.string.reader_focus_on) else stringResource(R.string.reader_focus_off),
      color = fg,
      style = MaterialTheme.typography.labelLarge,
    )
  }
}

@Composable
private fun toneLabel(tone: EInkTone): String =
  when (tone) {
    EInkTone.PAPER -> stringResource(R.string.tone_paper)
    EInkTone.WARM -> stringResource(R.string.tone_warm)
    EInkTone.HIGH_CONTRAST -> stringResource(R.string.tone_white)
    EInkTone.SOFT_GRAY -> stringResource(R.string.tone_gray)
    EInkTone.DARK -> stringResource(R.string.tone_dark)
  }
