package com.example.emptyapp.ui.monet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.ui.eink.CloseIcon
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText

@Composable
fun LibraryProMilestoneNudge(
  onExplorePro: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val dismissCd = stringResource(R.string.cd_dismiss_banner)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(EInkShapes.Card)
        .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      EInkText(
        text = stringResource(R.string.milestone_nudge_title),
        style = MaterialTheme.typography.titleSmall,
      )
      EInkText(
        text = stringResource(R.string.milestone_nudge_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    OutlinedButton(
      onClick = onExplorePro,
      shape = EInkShapes.Control,
      modifier = Modifier.defaultMinSize(minHeight = 40.dp),
    ) {
      EInkText(
        text = stringResource(R.string.milestone_nudge_cta),
        style = MaterialTheme.typography.labelMedium,
      )
    }
    Box(
      modifier =
        Modifier.size(48.dp)
          .clip(CircleShape)
          .clickable(onClick = onDismiss)
          .semantics { contentDescription = dismissCd },
      contentAlignment = Alignment.Center,
    ) {
      CloseIcon(modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
    }
  }
}
