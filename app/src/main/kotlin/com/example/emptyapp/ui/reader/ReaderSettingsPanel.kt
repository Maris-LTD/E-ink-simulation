package com.example.emptyapp.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.emptyapp.reader.EInkFont
import com.example.emptyapp.reader.EInkSettings
import java.util.Locale

@Composable
fun ReaderSettingsPanel(
  settings: EInkSettings,
  isReflowable: Boolean = true,
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
        label = "Phông chữ",
        options = EInkFont.entries,
        selected = settings.font,
        nameOf = { if (it == EInkFont.SERIF) "Serif" else "Sans" },
        onSelect = { font -> onChange { it.copy(font = font) } },
      )
      StepperRow(
        label = "Cỡ chữ",
        value = "${(settings.fontScale * 100).toInt()}%",
        onMinus = { onChange { it.copy(fontScale = (it.fontScale - 0.1).coerceAtLeast(0.7)) } },
        onPlus = { onChange { it.copy(fontScale = (it.fontScale + 0.1).coerceAtMost(4.0)) } },
      )
      StepperRow(
        label = "Độ đậm chữ",
        value = String.format(Locale.US, "%.2f", settings.fontWeight),
        onMinus = { onChange { it.copy(fontWeight = (it.fontWeight - 0.1).coerceAtLeast(1.0)) } },
        onPlus = { onChange { it.copy(fontWeight = (it.fontWeight + 0.1).coerceAtMost(2.5)) } },
      )
      StepperRow(
        label = "Giãn dòng",
        value = String.format(Locale.US, "%.1f", settings.lineHeight),
        onMinus = { onChange { it.copy(lineHeight = (it.lineHeight - 0.1).coerceAtLeast(1.0)) } },
        onPlus = { onChange { it.copy(lineHeight = (it.lineHeight + 0.1).coerceAtMost(2.2)) } },
      )
      StepperRow(
        label = "Lề trang",
        value = String.format(Locale.US, "%.1f", settings.pageMargins),
        onMinus = { onChange { it.copy(pageMargins = (it.pageMargins - 0.2).coerceAtLeast(0.0)) } },
        onPlus = { onChange { it.copy(pageMargins = (it.pageMargins + 0.2).coerceAtMost(3.0)) } },
      )
    }
    SwitchRow("Ảnh xám (đơn sắc)", settings.grayscale) { on -> onChange { it.copy(grayscale = on) } }
    StepperRow(
      label = "Tương phản",
      value = String.format(Locale.US, "%.2f", settings.contrast),
      onMinus = { onChange { it.copy(contrast = (it.contrast - 0.05f).coerceAtLeast(0.8f)) } },
      onPlus = { onChange { it.copy(contrast = (it.contrast + 0.05f).coerceAtMost(1.8f)) } },
    )
    SwitchRow("Vân giấy E-Ink", settings.paperGrain) { on -> onChange { it.copy(paperGrain = on) } }
    SwitchRow("Lưu ảnh mờ (Ghosting)", settings.ghosting) { on -> onChange { it.copy(ghosting = on) } }
    SwitchRow("Thanh tiến độ E-Reader", settings.readerFooter) { on -> onChange { it.copy(readerFooter = on) } }
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
        if (option == selected) {
          Button(onClick = { onSelect(option) }) { Text(nameOf(option)) }
        } else {
          OutlinedButton(onClick = { onSelect(option) }) { Text(nameOf(option)) }
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
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
