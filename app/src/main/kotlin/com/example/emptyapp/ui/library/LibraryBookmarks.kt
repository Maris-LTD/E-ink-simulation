package com.example.emptyapp.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.annotation.BookmarkEntry
import com.example.emptyapp.ui.eink.BookmarkIcon
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkText
import com.example.emptyapp.ui.eink.TrashIcon

@Composable
fun LibraryBookmarkList(
  bookmarks: List<BookmarkEntry>,
  bookTitlesById: Map<String, String>,
  onOpenBookmark: (bookId: String, locatorJson: String) -> Unit,
  onRemove: (String) -> Unit,
) {
  if (bookmarks.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
      EInkText(
        text = stringResource(R.string.lib_bookmarks_empty),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    return
  }
  val deleteCd = stringResource(R.string.action_delete)
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(bookmarks, key = { it.id }) { item ->
      val bookTitle = bookTitlesById[item.bookId]
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onOpenBookmark(item.bookId, item.locatorJson) }
            .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BookmarkIcon(
          filled = false,
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.padding(end = 12.dp).size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          EInkText(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val meta =
            listOfNotNull(
              bookTitle?.takeIf { it.isNotBlank() && it != item.title },
              "${(item.progress * 100).toInt()}%",
            ).joinToString(" · ")
          EInkText(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        IconButton(
          onClick = { onRemove(item.id) },
          modifier = Modifier.size(48.dp).semantics { contentDescription = deleteCd },
        ) {
          TrashIcon(modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
        }
      }
      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
    }
  }
}
