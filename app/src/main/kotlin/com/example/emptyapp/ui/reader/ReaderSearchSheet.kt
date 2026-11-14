package com.example.emptyapp.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.ui.eink.EInkText
import org.readium.r2.shared.publication.Locator

@Composable
fun ReaderSearchSheet(
  state: SearchUiState,
  onSearch: (String) -> Unit,
  onLoadMore: () -> Unit,
  onSelectLocator: (Locator) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var query by rememberSaveable {
    mutableStateOf(
      when (state) {
        is SearchUiState.Searching -> state.query
        is SearchUiState.Results -> state.query
        is SearchUiState.Error -> state.query
        SearchUiState.Idle -> ""
      }
    )
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EInkText(stringResource(R.string.search_in_book_title), style = MaterialTheme.typography.titleMedium)
      TextButton(onClick = onDismiss) { EInkText(stringResource(R.string.action_close)) }
    }
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val ink = MaterialTheme.colorScheme.onBackground
      Box(
        modifier =
          Modifier.weight(1f)
            .border(1.5.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        if (query.isEmpty()) {
          EInkText(
            text = stringResource(R.string.search_in_book_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          )
        }
        BasicTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink),
          cursorBrush = SolidColor(ink),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      OutlinedButton(onClick = { onSearch(query) }) { EInkText(stringResource(R.string.action_search)) }
    }
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)

    when (state) {
      SearchUiState.Idle -> {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          EInkText(
            stringResource(R.string.search_in_book_hint),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      is SearchUiState.Searching -> {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          EInkText(stringResource(R.string.search_in_book_loading, state.query), style = MaterialTheme.typography.bodyLarge)
        }
      }
      is SearchUiState.Error -> {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          EInkText(state.message, style = MaterialTheme.typography.bodyLarge)
        }
      }
      is SearchUiState.Results -> {
        if (state.items.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EInkText(stringResource(R.string.search_in_book_empty, state.query), style = MaterialTheme.typography.bodyLarge)
          }
        } else {
          EInkText(
            text = stringResource(R.string.search_in_book_count, state.items.size, if (state.hasMore) "+" else ""),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.items) { locator ->
              SearchResultRow(locator = locator, onClick = { onSelectLocator(locator) })
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.hasMore) {
              item {
                Box(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  OutlinedButton(onClick = onLoadMore) { EInkText(stringResource(R.string.search_in_book_load_more)) }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SearchResultRow(
  locator: Locator,
  onClick: () -> Unit,
) {
  val chapterTitle = locator.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.search_chapter_fallback)
  val pct = ((locator.locations.totalProgression ?: 0.0) * 100).toInt()
  val snippet =
    buildAnnotatedString {
      append(locator.text.before.orEmpty().trimStart())
      locator.text.highlight?.let { match ->
        withStyle(
          SpanStyle(
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
          )
        ) {
          append(match)
        }
      }
      append(locator.text.after.orEmpty().trimEnd())
    }

  Column(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EInkText(
        text = chapterTitle,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(end = 8.dp),
      )
      EInkText("$pct%", style = MaterialTheme.typography.labelMedium)
    }
    if (snippet.isNotEmpty()) {
      Text(
        text = snippet,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
