package com.example.emptyapp.ui.eink

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GRAIN_SIZE = 128

private object PaperGrainBrushHolder {
  @Volatile var cachedBrush: ShaderBrush? = null

  suspend fun getOrCreate(): ShaderBrush {
    cachedBrush?.let { return it }
    val created =
      withContext(Dispatchers.Default) {
        ShaderBrush(ImageShader(createPaperGrainTile(), TileMode.Repeated, TileMode.Repeated))
      }
    cachedBrush = created
    return created
  }
}

@Composable
fun PaperGrainOverlay(modifier: Modifier = Modifier, alpha: Float = 0.055f) {
  if (alpha <= 0f) return
  val brush by
    produceState(initialValue = PaperGrainBrushHolder.cachedBrush) {
      if (value == null) {
        value = PaperGrainBrushHolder.getOrCreate()
      }
    }
  val activeBrush = brush ?: return
  Canvas(modifier = modifier.fillMaxSize()) {
    drawRect(brush = activeBrush, alpha = alpha)
  }
}

private fun createPaperGrainTile(): ImageBitmap {
  val pixels = IntArray(GRAIN_SIZE * GRAIN_SIZE)
  var seed = 0x5DEECE66DL
  for (i in pixels.indices) {
    seed = (seed * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
    val value = ((seed ushr 16) and 0xFFL).toInt()
    val shade = if (value > 140) 255 else if (value < 90) 0 else value
    val a = if (value in 90..140) 40 else 180
    pixels[i] = (a shl 24) or (shade shl 16) or (shade shl 8) or shade
  }
  return Bitmap.createBitmap(pixels, GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
}
