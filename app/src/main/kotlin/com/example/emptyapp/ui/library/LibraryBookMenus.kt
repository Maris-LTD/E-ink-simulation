package com.example.emptyapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText
import java.util.Locale

internal fun shouldShowLibraryProBadge(
  isPro: Boolean,
  hasBilling: Boolean,
  totalPagesTurned: Int,
  totalReadingSeconds: Long,
): Boolean = hasBilling && (isPro || totalPagesTurned >= 5 || totalReadingSeconds >= 60L)

internal fun shouldShowSetupBanner(
  dismissed: Boolean,
  lockFeaturesCount: Int,
  hasStartedReading: Boolean,
): Boolean = !dismissed && lockFeaturesCount < 2 && hasStartedReading

internal fun formatFileSizeBytes(bytes: Long): String {
  if (bytes <= 0L) return "0 KB"
  if (bytes < 1024L) return "< 1 KB"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return "${kb.toInt()} KB"
  val mb = kb / 1024.0
  return String.format(Locale.US, "%.1f MB", mb)
}

@Composable
internal fun ImportSourceMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onPickFiles: () -> Unit,
  onPickFolder: () -> Unit,
) {
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismiss,
    modifier =
      Modifier.clip(EInkShapes.Card)
        .background(MaterialTheme.colorScheme.background, EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card),
  ) {
    DropdownMenuItem(
      text = { EInkText(stringResource(R.string.lib_import_menu_files), style = MaterialTheme.typography.bodyMedium) },
      onClick = {
        onDismiss()
        onPickFiles()
      },
    )
    HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
    DropdownMenuItem(
      text = { EInkText(stringResource(R.string.lib_import_menu_folder), style = MaterialTheme.typography.bodyMedium) },
      onClick = {
        onDismiss()
        onPickFolder()
      },
    )
  }
}

@Composable
internal fun BookActionMenu(
  book: BookEntry,
  expanded: Boolean,
  onDismiss: () -> Unit,
  onOpen: () -> Unit,
  onToggleCompleted: (Boolean) -> Unit,
  onShowDetails: () -> Unit,
  onDelete: () -> Unit,
) {
  val isCompleted = book.progress >= 0.99f
  DropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismiss,
    modifier =
      Modifier.clip(EInkShapes.Card)
        .background(MaterialTheme.colorScheme.background, EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card),
  ) {
    DropdownMenuItem(
      text = { EInkText(stringResource(R.string.lib_action_open), style = MaterialTheme.typography.bodyMedium) },
      onClick = {
        onDismiss()
        onOpen()
      },
    )
    DropdownMenuItem(
      text = {
        EInkText(
          stringResource(if (isCompleted) R.string.lib_action_mark_unread else R.string.lib_action_mark_finished),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      onClick = {
        onDismiss()
        onToggleCompleted(!isCompleted)
      },
    )
    DropdownMenuItem(
      text = { EInkText(stringResource(R.string.lib_action_details), style = MaterialTheme.typography.bodyMedium) },
      onClick = {
        onDismiss()
        onShowDetails()
      },
    )
    HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
    DropdownMenuItem(
      text = { EInkText(stringResource(R.string.action_delete), style = MaterialTheme.typography.bodyMedium) },
      onClick = {
        onDismiss()
        onDelete()
      },
    )
  }
}

@Composable
internal fun BookDetailsDialog(
  book: BookEntry,
  fileSizeBytes: Long,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    shape = EInkShapes.Card,
    containerColor = MaterialTheme.colorScheme.background,
    title = { EInkText(stringResource(R.string.lib_details_title), style = MaterialTheme.typography.titleMedium) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EInkText(book.title, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
        BookDetailRow(
          label = stringResource(R.string.lib_details_author),
          value = book.author.ifBlank { stringResource(R.string.lib_details_author_unknown) },
        )
        BookDetailRow(
          label = stringResource(R.string.lib_details_format),
          value = book.format.label,
        )
        BookDetailRow(
          label = stringResource(R.string.lib_details_progress),
          value = "${(book.progress * 100).toInt()}%",
        )
        BookDetailRow(
          label = stringResource(R.string.lib_details_size),
          value = formatFileSizeBytes(fileSizeBytes),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        EInkText(stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge)
      }
    },
  )
}

@Composable
private fun BookDetailRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    EInkText(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
    EInkText(text = value, style = MaterialTheme.typography.bodySmall)
  }
}
