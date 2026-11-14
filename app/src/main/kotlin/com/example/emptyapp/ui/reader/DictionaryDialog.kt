package com.example.emptyapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.emptyapp.R
import com.example.emptyapp.data.dictionary.DictionaryEntry
import com.example.emptyapp.ui.eink.EInkText

sealed interface DictionaryUiState {
  val query: String

  data class Loading(override val query: String) : DictionaryUiState

  data class Success(override val query: String, val entry: DictionaryEntry) : DictionaryUiState

  data class Error(override val query: String, val message: String) : DictionaryUiState
}

@Composable
fun DictionaryDialog(
  state: DictionaryUiState,
  onTranslateExternal: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.background)
          .border(1.5.dp, MaterialTheme.colorScheme.outline)
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      when (state) {
        is DictionaryUiState.Loading -> {
          EInkText(
            text = state.query,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
          EInkText(
            text = stringResource(R.string.dict_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        is DictionaryUiState.Error -> {
          EInkText(
            text = state.query,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
          EInkText(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        is DictionaryUiState.Success -> {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            EInkText(
              text = state.entry.word.ifBlank { state.query },
              style = MaterialTheme.typography.titleMedium,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis,
            )
            state.entry.phonetic?.let { phonetic ->
              EInkText(
                text = phonetic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
          HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
          Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            state.entry.translation?.let { translated ->
              Column(
                modifier =
                  Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text = stringResource(R.string.dict_translation_label),
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                  text = translated,
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onBackground,
                )
              }
            }
            state.entry.meanings.forEach { meaning ->
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (meaning.partOfSpeech.isNotBlank()) {
                  Text(
                    text = meaning.partOfSpeech,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                }
                meaning.definitions.forEachIndexed { index, def ->
                  Text(
                    text = "${index + 1}. ${def.definition}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                  )
                  def.example?.let { ex ->
                    Text(
                      text = "“$ex”",
                      style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                      color = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.padding(start = 12.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { onTranslateExternal(state.query) },
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(R.string.dict_open_external))
        }
        Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.action_close))
        }
      }
    }
  }
}
