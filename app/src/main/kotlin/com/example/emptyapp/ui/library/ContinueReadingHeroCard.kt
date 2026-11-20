package com.example.emptyapp.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.data.book.mostRecentlyReadBook
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText
import com.example.emptyapp.ui.eink.OverflowDotsIcon
import java.io.File

internal fun resolveHeroBook(
  books: List<BookEntry>,
  isAllOrReadingTab: Boolean,
  query: String,
  selectedAuthor: String?,
): BookEntry? {
  if (!isAllOrReadingTab || query.isNotBlank() || selectedAuthor != null) return null
  return books.mostRecentlyReadBook()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContinueReadingHeroCard(
  book: BookEntry,
  grayscaleCover: Boolean,
  coverFile: File?,
  onOpen: () -> Unit,
  onToggleCompleted: (Boolean) -> Unit = {},
  onShowDetails: () -> Unit = {},
  onRemove: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val hasProgress = book.progress > 0f
  val pct = (book.progress.coerceIn(0f, 1f) * 100).toInt()
  var menuExpanded by remember { mutableStateOf(false) }
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(EInkShapes.Hero)
        .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Hero)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Hero)
        .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true })
        .padding(start = 14.dp, top = 10.dp, bottom = 14.dp, end = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    BookCover(
      book = book,
      coverFile = coverFile,
      compact = false,
      grayscale = grayscaleCover,
      modifier = Modifier.size(width = 64.dp, height = 96.dp),
    )
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        EInkText(
          text = stringResource(if (hasProgress) R.string.lib_continue_reading else R.string.lib_start_reading),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f),
        )
        Box {
          val actionsCd = stringResource(R.string.cd_book_actions)
          Box(
            modifier =
              Modifier.size(40.dp)
                .clickable { menuExpanded = true }
                .semantics { contentDescription = actionsCd },
            contentAlignment = Alignment.Center,
          ) {
            OverflowDotsIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
          }
          BookActionMenu(
            book = book,
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onOpen = onOpen,
            onToggleCompleted = onToggleCompleted,
            onShowDetails = onShowDetails,
            onDelete = onRemove,
          )
        }
      }
      EInkText(
        text = book.title,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (book.author.isNotBlank()) {
        EInkText(
          text = book.author,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.secondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(4.dp)
                .clip(EInkShapes.Pill)
                .background(MaterialTheme.colorScheme.outlineVariant),
          ) {
            if (book.progress > 0f) {
              Box(
                modifier =
                  Modifier.fillMaxWidth(book.progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
              )
            }
          }
          EInkText(
            text = "${book.format.label} · $pct%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        Box(
          modifier =
            Modifier.clip(EInkShapes.Pill)
              .background(MaterialTheme.colorScheme.primary)
              .padding(horizontal = 12.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center,
        ) {
          EInkText(
            text = stringResource(if (hasProgress) R.string.lib_resume_btn else R.string.lib_read_btn),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
    }
  }
}
