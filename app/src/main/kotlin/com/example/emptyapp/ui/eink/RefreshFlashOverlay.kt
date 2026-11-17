package com.example.emptyapp.ui.eink

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay

@Composable
fun RefreshFlashOverlay(
  ghostBitmap: ImageBitmap?,
  ghostAlpha: Float,
  flashTrigger: Int = 0,
  modifier: Modifier = Modifier,
) {
  var flashPhase by remember { mutableIntStateOf(0) }
  var lastSeenTrigger by remember { mutableIntStateOf(flashTrigger) }

  LaunchedEffect(flashTrigger) {
    if (flashTrigger > lastSeenTrigger) {
      lastSeenTrigger = flashTrigger
      flashPhase = 1
      delay(60L)
      flashPhase = 2
      delay(60L)
      flashPhase = 0
    } else {
      lastSeenTrigger = flashTrigger
    }
  }

  if (ghostBitmap != null && ghostAlpha > 0f && flashPhase == 0) {
    Image(
      bitmap = ghostBitmap,
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      alpha = ghostAlpha,
      modifier = modifier.fillMaxSize(),
    )
  }
  if (flashPhase != 0) {
    val flashColor = if (flashPhase == 1) Color.Black else Color.White
    Box(modifier = modifier.fillMaxSize().background(flashColor))
  }
}
