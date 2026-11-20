package com.example.emptyapp.ui.library

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.ui.eink.EInkText
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
internal fun SettingsGearIcon(modifier: Modifier = Modifier) {
  val ink = MaterialTheme.colorScheme.onBackground
  Canvas(modifier = modifier) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val minDim = size.minDimension
    val stroke = 1.6.dp.toPx()
    val rInner = minDim * 0.15f
    val rOuter = minDim * 0.29f
    val rTip = minDim * 0.43f
    drawCircle(color = ink, radius = rInner, center = Offset(cx, cy), style = Stroke(width = stroke))
    drawCircle(color = ink, radius = rOuter, center = Offset(cx, cy), style = Stroke(width = stroke))
    for (i in 0 until 8) {
      val angle = (i * PI / 4.0).toFloat()
      val dx = cos(angle)
      val dy = sin(angle)
      drawLine(
        color = ink,
        start = Offset(cx + rOuter * dx, cy + rOuter * dy),
        end = Offset(cx + rTip * dx, cy + rTip * dy),
        strokeWidth = stroke * 1.15f,
        cap = StrokeCap.Round,
      )
    }
  }
}

@Composable
internal fun OverflowDotsIcon(modifier: Modifier = Modifier) {
  val ink = MaterialTheme.colorScheme.onBackground
  Canvas(modifier = modifier) {
    val cx = size.width / 2f
    val r = 1.6.dp.toPx()
    listOf(0.24f, 0.50f, 0.76f).forEach { frac ->
      drawCircle(color = ink, radius = r, center = Offset(cx, size.height * frac))
    }
  }
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
      Modifier.background(MaterialTheme.colorScheme.background)
        .border(1.5.dp, MaterialTheme.colorScheme.outline),
  ) {
    DropdownMenuItem(
      text = { EInkText(stringResource(R.string.lib_import_menu_files), style = MaterialTheme.typography.bodyMedium) },
      onClick = {
        onDismiss()
        onPickFiles()
      },
    )
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
      Modifier.background(MaterialTheme.colorScheme.background)
        .border(1.5.dp, MaterialTheme.colorScheme.outline),
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
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
    containerColor = MaterialTheme.colorScheme.background,
    title = { EInkText(stringResource(R.string.lib_details_title), style = MaterialTheme.typography.titleMedium) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EInkText(book.title, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
