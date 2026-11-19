package com.example.emptyapp.ui.monet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.ui.eink.EInkText

@Composable
fun PdfProPreviewLimitCard(
  pageIndex: Int,
  backgroundColor: Color,
  textColor: Color,
  onCenterTap: () -> Unit,
  onUnlockPro: () -> Unit,
  onUseStandardPdf: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(backgroundColor)
        .pointerInput(Unit) { detectTapGestures(onTap = { onCenterTap() }) }
        .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 420.dp)
          .fillMaxWidth()
          .background(backgroundColor)
          .border(1.5.dp, textColor)
          .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      EInkText(
        text = stringResource(R.string.pdf_pro_preview_title, pageIndex + 1),
        style = MaterialTheme.typography.titleMedium,
        color = textColor,
      )
      EInkText(
        text = stringResource(R.string.pdf_pro_preview_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
      )
      Button(
        onClick = onUnlockPro,
        shape = RectangleShape,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = textColor,
            contentColor = backgroundColor,
          ),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
      ) {
        EInkText(
          text = stringResource(R.string.pdf_pro_preview_unlock),
          style = MaterialTheme.typography.labelLarge,
          color = backgroundColor,
        )
      }
      OutlinedButton(
        onClick = onUseStandardPdf,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
      ) {
        EInkText(
          text = stringResource(R.string.pdf_pro_preview_standard),
          style = MaterialTheme.typography.labelLarge,
          color = textColor,
        )
      }
    }
  }
}
