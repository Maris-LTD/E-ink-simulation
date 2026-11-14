package com.example.emptyapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.annotation.HighlightEntry
import com.example.emptyapp.ui.eink.EInkText

@Composable
fun LibraryHighlightList(
  highlights: List<HighlightEntry>,
  onOpenHighlight: (bookId: String, locatorJson: String) -> Unit,
  onRemove: (String) -> Unit,
) {
  if (highlights.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
      EInkText(
        text = stringResource(R.string.lib_highlights_empty),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(highlights, key = { it.id }) { item ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onOpenHighlight(item.bookId, item.locatorJson) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Row(modifier = Modifier.weight(1f).height(IntrinsicSize.Min)) {
          Box(
            modifier =
              Modifier.width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline),
          )
          Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            EInkText(
              text = item.quote,
              style = MaterialTheme.typography.bodyLarge,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis,
            )
            if (item.note.isNotBlank()) {
              EInkText(
                text = stringResource(R.string.highlight_note_prefix, item.note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
            EInkText(
              text = "${item.bookTitle} · ${(item.progress * 100).toInt()}%",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
        TextButton(onClick = { onRemove(item.id) }) {
          EInkText(
            text = stringResource(R.string.action_delete),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
      HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}
