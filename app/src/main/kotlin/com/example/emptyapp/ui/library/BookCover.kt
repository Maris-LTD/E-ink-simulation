package com.example.emptyapp.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.ui.eink.CoverBitmapCache
import com.example.emptyapp.ui.eink.EInkText
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BookCover(
  book: BookEntry,
  coverFile: File?,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  val path = coverFile?.absolutePath
  var bitmap by remember(path) { mutableStateOf(CoverBitmapCache.get(path)) }
  if (bitmap == null && coverFile != null) {
    LaunchedEffect(path) {
      bitmap = withContext(Dispatchers.IO) { CoverBitmapCache.decodeAndCache(coverFile) }
    }
  }

  Box(
    modifier =
      modifier
        .background(MaterialTheme.colorScheme.background)
        .border(1.5.dp, MaterialTheme.colorScheme.outline),
    contentAlignment = Alignment.Center,
  ) {
    val current = bitmap
    if (current != null) {
      Image(
        bitmap = current,
        contentDescription = book.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Column(
        modifier = Modifier.fillMaxSize().padding(if (compact) 4.dp else 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        EInkText(
          text = book.format.label,
          style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
        if (!compact) {
          EInkText(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
