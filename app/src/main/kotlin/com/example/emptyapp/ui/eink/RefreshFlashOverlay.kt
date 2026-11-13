package com.example.emptyapp.ui.eink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

private enum class FlashPhase { None, Black, White }

private const val FLASH_PHASE_MILLIS = 80L

@Composable
fun RefreshFlashOverlay(trigger: Int, modifier: Modifier = Modifier) {
  var phase by remember { mutableStateOf(FlashPhase.None) }
  LaunchedEffect(trigger) {
    if (trigger == 0) return@LaunchedEffect
    phase = FlashPhase.Black
    delay(FLASH_PHASE_MILLIS)
    phase = FlashPhase.White
    delay(FLASH_PHASE_MILLIS)
    phase = FlashPhase.None
  }
  if (phase != FlashPhase.None) {
    val color = if (phase == FlashPhase.Black) Color.Black else Color.White
    Box(modifier = modifier.fillMaxSize().background(color))
  }
}
