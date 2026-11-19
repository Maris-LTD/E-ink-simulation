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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.billing.BillingConstants
import com.example.emptyapp.data.billing.BillingMessage
import com.example.emptyapp.data.billing.BillingProductUi
import com.example.emptyapp.ui.eink.EInkText

@Composable
internal fun PaywallTierCard(
  title: String,
  subtitle: String,
  price: String,
  badge: String?,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val borderWidth = if (isSelected) 2.dp else 1.dp
  val borderColor = if (isSelected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
  val bg = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .background(bg)
        .border(borderWidth, borderColor)
        .clickable(onClick = onClick)
        .semantics {
          role = Role.RadioButton
          selected = isSelected
        }
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.background(
          if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
        )
          .border(1.5.dp, MaterialTheme.colorScheme.outline)
          .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
      EInkText(
        text = if (isSelected) "●" else "○",
        style = MaterialTheme.typography.labelLarge,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
      )
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        EInkText(text = title, style = MaterialTheme.typography.titleSmall)
        if (badge != null) {
          Box(
            modifier =
              Modifier.background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            EInkText(
              text = badge,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
        }
      }
      EInkText(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
      )
    }
    EInkText(text = price, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
internal fun PaywallTipJarSection(
  products: Map<String, BillingProductUi>,
  onTip: (String) -> Unit,
) {
  val coffeePrice = products[BillingConstants.PRODUCT_TIP_COFFEE]?.formattedPrice
  val bookPrice = products[BillingConstants.PRODUCT_TIP_BOOK]?.formattedPrice
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    EInkText(
      text = stringResource(R.string.paywall_tip_header),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    EInkText(
      text = stringResource(R.string.paywall_tip_desc),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.secondary,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(
        onClick = { onTip(BillingConstants.PRODUCT_TIP_COFFEE) },
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
      ) {
        val label =
          if (coffeePrice != null) "${stringResource(R.string.paywall_tip_coffee)} ($coffeePrice)"
          else stringResource(R.string.paywall_tip_coffee)
        EInkText(text = label, style = MaterialTheme.typography.labelLarge)
      }
      OutlinedButton(
        onClick = { onTip(BillingConstants.PRODUCT_TIP_BOOK) },
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
      ) {
        val label =
          if (bookPrice != null) "${stringResource(R.string.paywall_tip_book)} ($bookPrice)"
          else stringResource(R.string.paywall_tip_book)
        EInkText(text = label, style = MaterialTheme.typography.labelLarge)
      }
    }
  }
}

@Composable
internal fun PaywallMessageBanner(
  message: BillingMessage,
  onDismiss: () -> Unit,
) {
  val text =
    when (message) {
      is BillingMessage.PurchaseSuccess -> stringResource(R.string.paywall_msg_purchase_success)
      is BillingMessage.TipSuccess -> stringResource(R.string.paywall_msg_tip_success)
      BillingMessage.PurchasePending -> stringResource(R.string.paywall_msg_pending)
      BillingMessage.PurchaseCanceled -> stringResource(R.string.paywall_msg_canceled)
      BillingMessage.PurchasesRestored -> stringResource(R.string.paywall_msg_restored)
      BillingMessage.NoPurchasesToRestore -> stringResource(R.string.paywall_msg_no_restore)
      is BillingMessage.Error -> stringResource(R.string.paywall_msg_error, message.responseCode, message.debugMessage)
    }
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.5.dp, MaterialTheme.colorScheme.outline)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EInkText(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    EInkText(
      text = "✕",
      style = MaterialTheme.typography.labelLarge,
      modifier =
        Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
          .clickable(onClick = onDismiss)
          .padding(8.dp),
    )
  }
}
