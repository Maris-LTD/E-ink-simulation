package com.example.emptyapp.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.reader.EInkFont
import com.example.emptyapp.reader.EInkSettings
import com.example.emptyapp.reader.OrientationLock
import com.example.emptyapp.reader.pdf.PdfReadingMode
import java.util.Locale

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
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outline)
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (isReflowable) {
      ChoiceRow(
        label = stringResource(R.string.settings_font),
        options = EInkFont.entries,
        selected = settings.font,
        nameOf = { if (it == EInkFont.SERIF) "Serif" else "Sans" },
        onSelect = { font -> onChange { it.copy(font = font) } },
      )
      StepperRow(
        label = stringResource(R.string.settings_font_size),
        value = "${(settings.fontScale * 100).toInt()}%",
        onMinus = { onChange { it.copy(fontScale = (it.fontScale - 0.1).coerceAtLeast(0.7)) } },
        onPlus = { onChange { it.copy(fontScale = (it.fontScale + 0.1).coerceAtMost(4.0)) } },
      )
      StepperRow(
        label = stringResource(R.string.settings_font_weight),
        value = String.format(Locale.US, "%.2f", settings.fontWeight),
        onMinus = { onChange { it.copy(fontWeight = (it.fontWeight - 0.1).coerceAtLeast(1.0)) } },
        onPlus = { onChange { it.copy(fontWeight = (it.fontWeight + 0.1).coerceAtMost(2.5)) } },
      )
      StepperRow(
        label = stringResource(R.string.settings_line_height),
        value = String.format(Locale.US, "%.1f", settings.lineHeight),
        onMinus = { onChange { it.copy(lineHeight = (it.lineHeight - 0.1).coerceAtLeast(1.0)) } },
        onPlus = { onChange { it.copy(lineHeight = (it.lineHeight + 0.1).coerceAtMost(2.2)) } },
      )
      StepperRow(
        label = stringResource(R.string.settings_paragraph_spacing),
        value = String.format(Locale.US, "%.1f", settings.paragraphSpacing),
        onMinus = { onChange { it.copy(paragraphSpacing = (it.paragraphSpacing - 0.2).coerceAtLeast(0.0)) } },
        onPlus = { onChange { it.copy(paragraphSpacing = (it.paragraphSpacing + 0.2).coerceAtMost(2.0)) } },
      )
      SwitchRow(stringResource(R.string.settings_justify), settings.justifyText) { on ->
        onChange { it.copy(justifyText = on) }
      }
      StepperRow(
        label = stringResource(R.string.settings_margins),
        value = String.format(Locale.US, "%.1f", settings.pageMargins),
        onMinus = { onChange { it.copy(pageMargins = (it.pageMargins - 0.2).coerceAtLeast(0.0)) } },
        onPlus = { onChange { it.copy(pageMargins = (it.pageMargins + 0.2).coerceAtMost(3.0)) } },
      )
    }
    if (isPdf) {
      val origLabel = stringResource(R.string.pdf_mode_original)
      val colLabel = stringResource(R.string.pdf_mode_column)
      val reflowLabel = stringResource(R.string.pdf_mode_reflow)
      val availableModes =
        if (pdfReflowAllowed) {
          listOf(PdfReadingMode.SMART_ORIGINAL, PdfReadingMode.GUIDED_COLUMN, PdfReadingMode.HYBRID_REFLOW)
        } else {
          listOf(PdfReadingMode.SMART_ORIGINAL)
        }
      ChoiceRow(
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
        SwitchRow(stringResource(R.string.settings_pdf_auto_crop), settings.pdfAutoCrop) { on ->
          onChange { it.copy(pdfAutoCrop = on) }
        }
      }
    }
    SwitchRow(stringResource(R.string.settings_grayscale), settings.grayscale) { on -> onChange { it.copy(grayscale = on) } }
    StepperRow(
      label = stringResource(R.string.settings_contrast),
      value = String.format(Locale.US, "%.2f", settings.contrast),
      onMinus = { onChange { it.copy(contrast = (it.contrast - 0.05f).coerceAtLeast(0.8f)) } },
      onPlus = { onChange { it.copy(contrast = (it.contrast + 0.05f).coerceAtMost(1.8f)) } },
    )
    SwitchRow(stringResource(R.string.settings_paper_grain), settings.paperGrain) { on -> onChange { it.copy(paperGrain = on) } }
    SwitchRow(stringResource(R.string.onboard_toggle_flash), settings.flashEnabled) { on -> onChange { it.copy(flashEnabled = on) } }
    SwitchRow(stringResource(R.string.settings_ghosting), settings.ghosting) { on -> onChange { it.copy(ghosting = on) } }
    SwitchRow(stringResource(R.string.settings_reader_footer), settings.readerFooter) { on -> onChange { it.copy(readerFooter = on) } }
    SwitchRow(stringResource(R.string.settings_focus_mode), settings.focusMode) { on -> onToggleFocusMode(on) }
    SwitchRow(stringResource(R.string.settings_volume_turn_pages), settings.volumeButtonsTurnPages) { on ->
      onChange { it.copy(volumeButtonsTurnPages = on) }
    }
    SwitchRow(stringResource(R.string.settings_lockscreen_reader), settings.lockScreenReader) { on ->
      if (onToggleLockScreenReader != null) {
        onToggleLockScreenReader(on)
      } else {
        onChange { it.copy(lockScreenReader = on) }
      }
    }
    val autoLabel = stringResource(R.string.orientation_auto)
    val portraitLabel = stringResource(R.string.orientation_portrait)
    val landscapeLabel = stringResource(R.string.orientation_landscape)
    ChoiceRow(
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
  }
}

@Composable
private fun <T> ChoiceRow(
  label: String,
  options: List<T>,
  selected: T,
  nameOf: (T) -> String,
  onSelect: (T) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      options.forEach { option ->
        val isSelected = option == selected
        val chipModifier =
          Modifier.semantics {
            role = Role.RadioButton
            this.selected = isSelected
          }
        if (isSelected) {
          Button(onClick = { onSelect(option) }, modifier = chipModifier) { Text(nameOf(option)) }
        } else {
          OutlinedButton(onClick = { onSelect(option) }, modifier = chipModifier) { Text(nameOf(option)) }
        }
      }
    }
  }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onMinus) { Text("−") }
      Text(value, style = MaterialTheme.typography.bodyLarge)
      OutlinedButton(onClick = onPlus) { Text("+") }
    }
  }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics(mergeDescendants = true) {}
        .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Switch(checked = checked, onCheckedChange = null)
  }
}
