package com.example.emptyapp.ui.monet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.billing.ProEntitlement
import com.example.emptyapp.data.billing.ProFeature
import com.example.emptyapp.data.billing.PurchaseType
import com.example.emptyapp.ui.eink.CheckIcon
import com.example.emptyapp.ui.eink.CloseIcon
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText

@Composable
internal fun PaywallTopBar(onDismiss: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EInkText(text = stringResource(R.string.paywall_title), style = MaterialTheme.typography.titleLarge)
      Box(
        modifier =
          Modifier.clip(EInkShapes.Badge)
            .background(MaterialTheme.colorScheme.primary, EInkShapes.Badge)
            .padding(horizontal = 7.dp, vertical = 2.dp),
      ) {
        EInkText(
          text = stringResource(R.string.paywall_badge_pro),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
    val closeCd = stringResource(R.string.action_close)
    IconButton(
      onClick = onDismiss,
      modifier = Modifier.size(48.dp).semantics { contentDescription = closeCd },
    ) {
      CloseIcon(modifier = Modifier.size(18.dp))
    }
  }
}

@Composable
internal fun PaywallContextHeader(feature: ProFeature?) {
  val (titleRes, bodyRes) =
    when (feature) {
      ProFeature.ADAPTIVE_PDF_REFLOW -> R.string.paywall_header_pdf_reflow to R.string.paywall_sub_pdf_reflow
      ProFeature.DRIVE_SYNC -> R.string.paywall_header_drive_sync to R.string.paywall_sub_drive_sync
      ProFeature.FOCUS_MODE -> R.string.paywall_header_focus to R.string.paywall_sub_focus
      null -> R.string.paywall_header_general to R.string.paywall_sub_general
    }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    EInkText(text = stringResource(titleRes), style = MaterialTheme.typography.headlineMedium)
    EInkText(
      text = stringResource(bodyRes),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.secondary,
    )
  }
}

@Composable
internal fun PaywallFeatureList() {
  val features =
    listOf(
      R.string.paywall_feat_pdf_title to R.string.paywall_feat_pdf_desc,
      R.string.paywall_feat_sync_title to R.string.paywall_feat_sync_desc,
      R.string.paywall_feat_focus_title to R.string.paywall_feat_focus_desc,
    )
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    features.forEach { (titleRes, descRes) ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
      ) {
        CheckIcon(modifier = Modifier.padding(top = 3.dp).size(15.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          EInkText(text = stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
          EInkText(
            text = stringResource(descRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}

@Composable
internal fun PaywallTrustBadges() {
  val badges =
    listOf(
      R.string.paywall_trust_ads,
      R.string.paywall_trust_offline,
      R.string.paywall_trust_restore,
    )
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(EInkShapes.Card)
        .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Card)
        .border(EInkBorder.Hairline, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    badges.forEach { badgeRes ->
      EInkText(
        text = "• " + stringResource(badgeRes),
        style = MaterialTheme.typography.labelMedium,
      )
    }
  }
}

@Composable
internal fun ActiveProSummaryCard(entitlement: ProEntitlement) {
  val descRes =
    if (entitlement.purchaseType == PurchaseType.LIFETIME) R.string.paywall_active_lifetime
    else R.string.paywall_active_subscription
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(EInkShapes.Card)
        .border(EInkBorder.Active, MaterialTheme.colorScheme.outline, EInkShapes.Card)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier.clip(EInkShapes.Badge)
          .background(MaterialTheme.colorScheme.primary, EInkShapes.Badge)
          .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
      EInkText(
        text = stringResource(R.string.paywall_active_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
    EInkText(text = stringResource(descRes), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
internal fun PendingBannerCard() {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .clip(EInkShapes.Card)
        .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
        .padding(12.dp),
  ) {
    EInkText(
      text = stringResource(R.string.paywall_active_pending),
      style = MaterialTheme.typography.bodySmall,
    )
  }
}
