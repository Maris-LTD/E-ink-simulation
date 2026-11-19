package com.example.emptyapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.emptyapp.R
import com.example.emptyapp.ui.eink.EInkText

@Composable
fun HighlightNoteDialog(
  quote: String,
  note: String,
  onNoteChange: (String) -> Unit,
  onSave: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.background)
          .border(1.5.dp, MaterialTheme.colorScheme.outline)
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      EInkText(stringResource(R.string.highlight_note_title), style = MaterialTheme.typography.titleMedium)
      EInkText(
        text = "“$quote”",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        placeholder = { Text(stringResource(R.string.highlight_note_placeholder)) },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth(),
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
        Button(onClick = { onSave(note) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_save)) }
      }
    }
  }
}
