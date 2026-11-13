package com.example.emptyapp.ui.eink

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
fun RefreshFlashOverlay(
  ghostBitmap: ImageBitmap?,
  ghostAlpha: Float,
  modifier: Modifier = Modifier,
) {
  if (ghostBitmap != null && ghostAlpha > 0f) {
    Image(
      bitmap = ghostBitmap,
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      alpha = ghostAlpha,
      modifier = modifier.fillMaxSize(),
    )
  }
}
