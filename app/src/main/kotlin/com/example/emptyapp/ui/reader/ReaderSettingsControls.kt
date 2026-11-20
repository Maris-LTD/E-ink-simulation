package com.example.emptyapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText
import com.example.emptyapp.ui.eink.MinusIcon
import com.example.emptyapp.ui.eink.PlusIcon

@Composable
internal fun SettingsSectionTitle(title: String) {
  EInkText(
    text = title,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.secondary,
  )
}

@Composable
internal fun <T> SettingsChoiceRow(
  label: String,
  options: List<T>,
  selected: T,
  nameOf: (T) -> String,
  onSelect: (T) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    EInkText(label, style = MaterialTheme.typography.labelLarge)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      options.forEach { option ->
        val isSelected = option == selected
        Box(
          modifier =
            Modifier.clip(EInkShapes.Pill)
              .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                EInkShapes.Pill,
              )
              .border(
                width = if (isSelected) EInkBorder.Card else EInkBorder.Hairline,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = EInkShapes.Pill,
              )
              .clickable { onSelect(option) }
              .semantics {
                role = Role.RadioButton
                this.selected = isSelected
              }
              .padding(horizontal = 14.dp, vertical = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = nameOf(option),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
internal fun SettingsStepperRow(
  label: String,
  value: String,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EInkText(label, style = MaterialTheme.typography.labelLarge)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(EInkShapes.Control)
            .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Control)
            .border(EInkBorder.Hairline, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Control)
            .clickable(onClick = onMinus),
        contentAlignment = Alignment.Center,
      ) {
        MinusIcon(modifier = Modifier.size(16.dp))
      }
      EInkText(
        text = value,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        modifier = Modifier.padding(horizontal = 4.dp),
      )
      Box(
        modifier =
          Modifier.size(36.dp)
            .clip(EInkShapes.Control)
            .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Control)
            .border(EInkBorder.Hairline, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Control)
            .clickable(onClick = onPlus),
        contentAlignment = Alignment.Center,
      ) {
        PlusIcon(modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
internal fun SettingsSwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .semantics(mergeDescendants = true) {}
        .padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EInkText(label, style = MaterialTheme.typography.labelLarge)
    Switch(checked = checked, onCheckedChange = null)
  }
}
