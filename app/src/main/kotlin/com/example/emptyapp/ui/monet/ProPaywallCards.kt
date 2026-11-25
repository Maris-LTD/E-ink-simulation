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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.emptyapp.R
import com.example.emptyapp.data.billing.BillingConstants
import com.example.emptyapp.data.billing.BillingMessage
import com.example.emptyapp.data.billing.BillingProductUi
import com.example.emptyapp.ui.eink.CloseIcon
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
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
  val borderWidth = if (isSelected) EInkBorder.Active else EInkBorder.Card
  val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
  val bg = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .defaultMinSize(minHeight = 58.dp)
        .clip(EInkShapes.Card)
        .background(bg, EInkShapes.Card)
        .border(borderWidth, borderColor, EInkShapes.Card)
        .clickable(onClick = onClick)
        .semantics {
          role = Role.RadioButton
          selected = isSelected
        }
        .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(20.dp)
          .clip(CircleShape)
          .border(
            width = if (isSelected) 2.dp else EInkBorder.Card,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shape = CircleShape,
          ),
      contentAlignment = Alignment.Center,
    ) {
      if (isSelected) {
        Box(
          modifier =
            Modifier.size(10.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
      }
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
              Modifier.clip(EInkShapes.Badge)
                .background(MaterialTheme.colorScheme.primary, EInkShapes.Badge)
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
  val coffeePrice =
    products[BillingConstants.PRODUCT_TIP_COFFEE]?.formattedPrice
      ?: stringResource(R.string.tip_coffee_fallback_price)
  val bookPrice =
    products[BillingConstants.PRODUCT_TIP_BOOK]?.formattedPrice
      ?: stringResource(R.string.tip_book_fallback_price)
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .clip(EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
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
        shape = EInkShapes.Control,
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
      ) {
        EInkText(
          text = "${stringResource(R.string.paywall_tip_coffee)} ($coffeePrice)",
          style = MaterialTheme.typography.labelLarge,
        )
      }
      OutlinedButton(
        onClick = { onTip(BillingConstants.PRODUCT_TIP_BOOK) },
        shape = EInkShapes.Control,
        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
      ) {
        EInkText(
          text = "${stringResource(R.string.paywall_tip_book)} ($bookPrice)",
          style = MaterialTheme.typography.labelLarge,
        )
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
        .clip(EInkShapes.Card)
        .background(MaterialTheme.colorScheme.surfaceVariant, EInkShapes.Card)
        .border(EInkBorder.Card, MaterialTheme.colorScheme.outlineVariant, EInkShapes.Card)
        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    EInkText(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    Box(
      modifier =
        Modifier.size(36.dp)
          .clip(CircleShape)
          .clickable(onClick = onDismiss),
      contentAlignment = Alignment.Center,
    ) {
      CloseIcon(modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
    }
  }
}
