package com.example.emptyapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.reader.EInkFont
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.EInkTone
import com.example.emptyapp.reader.OrientationLock
import com.example.emptyapp.reader.pdf.PdfReadingMode
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText
import java.util.Locale

internal enum class ReaderSettingsTab {
  TEXT,
  DISPLAY,
  CONTROLS,
}

internal fun availableSettingsTabs(isReflowable: Boolean, isPdf: Boolean): List<ReaderSettingsTab> =
  if (isReflowable || isPdf) {
    listOf(ReaderSettingsTab.TEXT, ReaderSettingsTab.DISPLAY, ReaderSettingsTab.CONTROLS)
  } else {
    listOf(ReaderSettingsTab.DISPLAY, ReaderSettingsTab.CONTROLS)
  }

@Composable
fun ReaderSettingsPanel(
  settings: EInkSettings,
  isReflowable: Boolean = true,
  isPdf: Boolean = false,
  pdfMode: PdfReadingMode = PdfReadingMode.SMART_ORIGINAL,
  pdfReflowAllowed: Boolean = true,
  onPdfModeChange: (PdfReadingMode) -> Unit = {},
  onToggleFocusMode: (Boolean) -> Unit = {},
  onToggleLockScreenReader: ((Boolean) -> Unit)? = null,
  onChange: ((EInkSettings) -> EInkSettings) -> Unit,
  modifier: Modifier = Modifier,
) {
  val tabs = availableSettingsTabs(isReflowable = isReflowable, isPdf = isPdf)
  var selectedTab by rememberSaveable { mutableStateOf(tabs.first()) }
  val activeTab = if (selectedTab in tabs) selectedTab else tabs.first()
  val panelShape = RoundedCornerShape(16.dp)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(panelShape)
        .background(MaterialTheme.colorScheme.background, panelShape)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, panelShape)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .clip(EInkShapes.Pill)
          .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Pill)
          .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      tabs.forEach { tab ->
        val isActive = tab == activeTab
        val labelRes =
          when (tab) {
            ReaderSettingsTab.TEXT -> if (isReflowable) R.string.settings_tab_text else R.string.settings_tab_pdf
            ReaderSettingsTab.DISPLAY -> R.string.settings_tab_display
            ReaderSettingsTab.CONTROLS -> R.string.settings_tab_controls
          }
        Box(
          modifier =
            Modifier.weight(1f)
              .clip(EInkShapes.Pill)
              .background(
                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                EInkShapes.Pill,
              )
              .clickable { selectedTab = tab }
              .semantics {
                role = Role.Tab
                selected = isActive
              }
              .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge.copy(
              fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
            maxLines = 1,
          )
        }
      }
    }

    Column(
      modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      when (activeTab) {
        ReaderSettingsTab.TEXT ->
          TextAndPdfSettingsTab(
            settings = settings,
            isReflowable = isReflowable,
            isPdf = isPdf,
            pdfMode = pdfMode,
            pdfReflowAllowed = pdfReflowAllowed,
            onPdfModeChange = onPdfModeChange,
            onChange = onChange,
          )
        ReaderSettingsTab.DISPLAY -> DisplaySettingsTab(settings = settings, onChange = onChange)
        ReaderSettingsTab.CONTROLS ->
          ControlsSettingsTab(
            settings = settings,
            onToggleFocusMode = onToggleFocusMode,
            onToggleLockScreenReader = onToggleLockScreenReader,
            onChange = onChange,
          )
      }
    }
  }
}

@Composable
private fun TextAndPdfSettingsTab(
  settings: EInkSettings,
  isReflowable: Boolean,
  isPdf: Boolean,
  pdfMode: PdfReadingMode,
  pdfReflowAllowed: Boolean,
  onPdfModeChange: (PdfReadingMode) -> Unit,
  onChange: ((EInkSettings) -> EInkSettings) -> Unit,
) {
  if (isReflowable) {
    SettingsSectionTitle(stringResource(R.string.settings_section_typography))
    SettingsChoiceRow(
      label = stringResource(R.string.settings_font),
      options = EInkFont.entries,
      selected = settings.font,
      nameOf = { if (it == EInkFont.SERIF) "Serif" else "Sans" },
      onSelect = { font -> onChange { it.copy(font = font) } },
    )
    SettingsStepperRow(
      label = stringResource(R.string.settings_font_size),
      value = "${(settings.fontScale * 100).toInt()}%",
      onMinus = { onChange { it.copy(fontScale = (it.fontScale - 0.1).coerceAtLeast(0.7)) } },
      onPlus = { onChange { it.copy(fontScale = (it.fontScale + 0.1).coerceAtMost(4.0)) } },
    )
    SettingsStepperRow(
      label = stringResource(R.string.settings_font_weight),
      value = String.format(Locale.US, "%.2f", settings.fontWeight),
      onMinus = { onChange { it.copy(fontWeight = (it.fontWeight - 0.1).coerceAtLeast(1.0)) } },
      onPlus = { onChange { it.copy(fontWeight = (it.fontWeight + 0.1).coerceAtMost(2.5)) } },
    )
    SettingsStepperRow(
      label = stringResource(R.string.settings_line_height),
      value = String.format(Locale.US, "%.1f", settings.lineHeight),
      onMinus = { onChange { it.copy(lineHeight = (it.lineHeight - 0.1).coerceAtLeast(1.0)) } },
      onPlus = { onChange { it.copy(lineHeight = (it.lineHeight + 0.1).coerceAtMost(2.2)) } },
    )
    SettingsStepperRow(
      label = stringResource(R.string.settings_paragraph_spacing),
      value = String.format(Locale.US, "%.1f", settings.paragraphSpacing),
      onMinus = { onChange { it.copy(paragraphSpacing = (it.paragraphSpacing - 0.2).coerceAtLeast(0.0)) } },
      onPlus = { onChange { it.copy(paragraphSpacing = (it.paragraphSpacing + 0.2).coerceAtMost(2.0)) } },
    )
    SettingsSwitchRow(stringResource(R.string.settings_justify), settings.justifyText) { on ->
      onChange { it.copy(justifyText = on) }
    }
    SettingsStepperRow(
      label = stringResource(R.string.settings_margins),
      value = String.format(Locale.US, "%.1f", settings.pageMargins),
      onMinus = { onChange { it.copy(pageMargins = (it.pageMargins - 0.2).coerceAtLeast(0.0)) } },
      onPlus = { onChange { it.copy(pageMargins = (it.pageMargins + 0.2).coerceAtMost(3.0)) } },
    )
  }
  if (isPdf) {
    if (isReflowable) {
      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
    }
    SettingsSectionTitle(stringResource(R.string.settings_section_layout))
    val origLabel = stringResource(R.string.pdf_mode_original)
    val colLabel = stringResource(R.string.pdf_mode_column)
    val reflowLabel = stringResource(R.string.pdf_mode_reflow)
    val availableModes =
      if (pdfReflowAllowed) {
        listOf(PdfReadingMode.SMART_ORIGINAL, PdfReadingMode.GUIDED_COLUMN, PdfReadingMode.HYBRID_REFLOW)
      } else {
        listOf(PdfReadingMode.SMART_ORIGINAL)
      }
    SettingsChoiceRow(
      label = stringResource(R.string.settings_pdf_mode),
      options = availableModes,
      selected = pdfMode,
      nameOf = {
        when (it) {
          PdfReadingMode.SMART_ORIGINAL -> origLabel
          PdfReadingMode.GUIDED_COLUMN -> colLabel
          PdfReadingMode.HYBRID_REFLOW -> reflowLabel
        }
      },
      onSelect = onPdfModeChange,
    )
    if (pdfMode == PdfReadingMode.SMART_ORIGINAL) {
      SettingsSwitchRow(stringResource(R.string.settings_pdf_auto_crop), settings.pdfAutoCrop) { on ->
        onChange { it.copy(pdfAutoCrop = on) }
      }
    }
  }
}

@Composable
private fun DisplaySettingsTab(
  settings: EInkSettings,
  onChange: ((EInkSettings) -> EInkSettings) -> Unit,
) {
  SettingsSectionTitle(stringResource(R.string.settings_section_eink))
  val paperLabel = stringResource(R.string.tone_paper)
  val warmLabel = stringResource(R.string.tone_warm)
  val whiteLabel = stringResource(R.string.tone_white)
  val grayLabel = stringResource(R.string.tone_gray)
  val darkLabel = stringResource(R.string.tone_dark)
  SettingsChoiceRow(
    label = stringResource(R.string.settings_tone),
    options = EInkTone.entries,
    selected = settings.tone,
    nameOf = {
      when (it) {
        EInkTone.PAPER -> paperLabel
        EInkTone.WARM -> warmLabel
        EInkTone.HIGH_CONTRAST -> whiteLabel
        EInkTone.SOFT_GRAY -> grayLabel
        EInkTone.DARK -> darkLabel
      }
    },
    onSelect = { tone -> onChange { it.copy(tone = tone) } },
  )
  SettingsSwitchRow(stringResource(R.string.settings_grayscale), settings.grayscale) { on ->
    onChange { it.copy(grayscale = on) }
  }
  SettingsStepperRow(
    label = stringResource(R.string.settings_contrast),
    value = String.format(Locale.US, "%.2f", settings.contrast),
    onMinus = { onChange { it.copy(contrast = (it.contrast - 0.05f).coerceAtLeast(0.8f)) } },
    onPlus = { onChange { it.copy(contrast = (it.contrast + 0.05f).coerceAtMost(1.8f)) } },
  )
  SettingsSwitchRow(stringResource(R.string.settings_paper_grain), settings.paperGrain) { on ->
    onChange { it.copy(paperGrain = on) }
  }
  SettingsSwitchRow(stringResource(R.string.settings_ghosting), settings.ghosting) { on ->
    onChange { it.copy(ghosting = on) }
  }
  HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
  SettingsSectionTitle(stringResource(R.string.settings_section_layout))
  val autoLabel = stringResource(R.string.orientation_auto)
  val portraitLabel = stringResource(R.string.orientation_portrait)
  val landscapeLabel = stringResource(R.string.orientation_landscape)
  SettingsChoiceRow(
    label = stringResource(R.string.settings_orientation),
    options = OrientationLock.entries,
    selected = settings.orientationLock,
    nameOf = {
      when (it) {
        OrientationLock.AUTO -> autoLabel
        OrientationLock.PORTRAIT -> portraitLabel
        OrientationLock.LANDSCAPE -> landscapeLabel
      }
    },
    onSelect = { lock -> onChange { it.copy(orientationLock = lock) } },
  )
  SettingsSwitchRow(stringResource(R.string.settings_reader_footer), settings.readerFooter) { on ->
    onChange { it.copy(readerFooter = on) }
  }
}

@Composable
private fun ControlsSettingsTab(
  settings: EInkSettings,
  onToggleFocusMode: (Boolean) -> Unit,
  onToggleLockScreenReader: ((Boolean) -> Unit)?,
  onChange: ((EInkSettings) -> EInkSettings) -> Unit,
) {
  SettingsSectionTitle(stringResource(R.string.settings_section_reading))
  SettingsSwitchRow(stringResource(R.string.settings_focus_mode), settings.focusMode) { on ->
    onToggleFocusMode(on)
  }
  SettingsSwitchRow(stringResource(R.string.settings_volume_turn_pages), settings.volumeButtonsTurnPages) { on ->
    onChange { it.copy(volumeButtonsTurnPages = on) }
  }
  SettingsSwitchRow(stringResource(R.string.settings_lockscreen_reader), settings.lockScreenReader) { on ->
    if (onToggleLockScreenReader != null) {
      onToggleLockScreenReader(on)
    } else {
      onChange { it.copy(lockScreenReader = on) }
    }
  }
}
