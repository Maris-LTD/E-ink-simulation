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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.emptyapp.R
import com.example.emptyapp.data.annotation.BookmarkEntry
import com.example.emptyapp.data.annotation.HighlightEntry
import com.example.emptyapp.ui.eink.EInkText
import org.readium.r2.shared.publication.Link

private enum class ContentsTab(val labelRes: Int) {
  TOC(R.string.tab_toc),
  BOOKMARKS(R.string.tab_bookmarks),
  HIGHLIGHTS(R.string.tab_highlights),
}

private data class TocItem(val link: Link, val depth: Int)

@Composable
fun ReaderContentsSheet(
  toc: List<Link>,
  bookmarks: List<BookmarkEntry>,
  highlights: List<HighlightEntry>,
  onSelectToc: (Link) -> Unit,
  onSelectLocatorJson: (String) -> Unit,
  onRemoveBookmark: (String) -> Unit,
  onRemoveHighlight: (String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableStateOf(ContentsTab.TOC) }
  val flatToc = remember(toc) { flattenToc(toc) }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .border(1.5.dp, MaterialTheme.colorScheme.outline),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      EInkText(stringResource(R.string.contents_title), style = MaterialTheme.typography.titleLarge)
      TextButton(onClick = onDismiss) { EInkText(stringResource(R.string.action_close)) }
    }
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)
    Row(modifier = Modifier.fillMaxWidth()) {
      ContentsTab.entries.forEach { tab ->
        val active = tab == selectedTab
        Box(
          modifier =
            Modifier.weight(1f)
              .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
              .clickable { selectedTab = tab }
              .padding(vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
          )
        }
      }
    }
    HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline)

    when (selectedTab) {
      ContentsTab.TOC -> TocList(flatToc, onSelectToc)
      ContentsTab.BOOKMARKS -> BookmarkList(bookmarks, onSelectLocatorJson, onRemoveBookmark)
      ContentsTab.HIGHLIGHTS -> HighlightList(highlights, onSelectLocatorJson, onRemoveHighlight)
    }
  }
}

@Composable
private fun TocList(items: List<TocItem>, onSelect: (Link) -> Unit) {
  if (items.isEmpty()) {
    EmptySection(stringResource(R.string.toc_empty))
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(items) { item ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onSelect(item.link) }
            .padding(start = (16 + item.depth * 16).dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
      ) {
        EInkText(
          text = item.link.title?.takeIf { it.isNotBlank() } ?: item.link.href.toString(),
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}

@Composable
private fun BookmarkList(
  bookmarks: List<BookmarkEntry>,
  onSelect: (String) -> Unit,
  onRemove: (String) -> Unit,
) {
  if (bookmarks.isEmpty()) {
    EmptySection(stringResource(R.string.bookmarks_empty))
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(bookmarks, key = { it.id }) { item ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onSelect(item.locatorJson) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          EInkText(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
          EInkText(
            "${(item.progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
          )
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

@Composable
private fun HighlightList(
  highlights: List<HighlightEntry>,
  onSelect: (String) -> Unit,
  onRemove: (String) -> Unit,
) {
  if (highlights.isEmpty()) {
    EmptySection(stringResource(R.string.highlights_empty))
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(highlights, key = { it.id }) { item ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onSelect(item.locatorJson) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          EInkText(
            text = "“${item.quote}”",
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
            text = "${(item.progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
          )
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

@Composable
private fun EmptySection(message: String) {
  Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    EInkText(
      text = message,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.secondary,
    )
  }
}

private fun flattenToc(links: List<Link>, depth: Int = 0): List<TocItem> =
  links.flatMap { link -> listOf(TocItem(link, depth)) + flattenToc(link.children, depth + 1) }
