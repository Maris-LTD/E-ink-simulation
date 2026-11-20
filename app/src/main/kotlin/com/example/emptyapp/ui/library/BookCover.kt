package com.example.emptyapp.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emptyapp.data.book.BookEntry
import com.example.emptyapp.ui.eink.CoverBitmapCache
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkSerifFamily
import com.example.emptyapp.ui.eink.EInkShapes
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
  grayscale: Boolean = true,
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
        .clip(EInkShapes.Cover)
        .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Cover)
        .border(EInkBorder.Hairline, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Cover),
    contentAlignment = Alignment.Center,
  ) {
    val current = bitmap
    if (current != null) {
      val coverFilter = remember(grayscale) { CoverBitmapCache.colorFilterFor(grayscale) }
      Image(
        bitmap = current,
        contentDescription = book.title,
        contentScale = ContentScale.Crop,
        colorFilter = coverFilter,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Box(modifier = Modifier.fillMaxSize()) {
        Box(
          modifier =
            Modifier.align(Alignment.CenterStart)
              .padding(start = if (compact) 4.dp else 8.dp)
              .width(1.dp)
              .fillMaxHeight()
              .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Column(
          modifier =
            Modifier.fillMaxSize()
              .padding(
                start = if (compact) 8.dp else 14.dp,
                end = if (compact) 5.dp else 10.dp,
                top = if (compact) 5.dp else 10.dp,
                bottom = if (compact) 5.dp else 10.dp,
              ),
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          EInkText(
            text = book.format.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
          if (!compact) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              EInkText(
                text = book.title,
                style = MaterialTheme.typography.titleSmall.copy(
                  fontFamily = EInkSerifFamily,
                  fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
              )
              if (book.author.isNotBlank()) {
                EInkText(
                  text = book.author,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}
