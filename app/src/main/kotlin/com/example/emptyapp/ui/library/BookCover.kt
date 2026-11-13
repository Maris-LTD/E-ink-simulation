package com.example.emptyapp.ui.library

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emptyapp.data.book.BookEntry
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
  val bitmap by
    produceState<ImageBitmap?>(initialValue = null, key1 = coverFile?.absolutePath) {
      value =
        if (coverFile != null && coverFile.exists()) {
          withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(coverFile.absolutePath)?.asImageBitmap() }.getOrNull()
          }
        } else {
          null
        }
    }
  val grayscale =
    remember {
      ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
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
        colorFilter = grayscale,
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
          strokeWidthPx = 1.2f,
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
