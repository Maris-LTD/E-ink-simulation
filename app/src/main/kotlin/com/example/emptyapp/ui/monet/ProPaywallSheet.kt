package com.example.emptyapp.ui.monet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.emptyapp.R
import com.example.emptyapp.data.billing.BillingConstants
import com.example.emptyapp.data.billing.BillingMessage
import com.example.emptyapp.data.billing.PlayBillingManager
import com.example.emptyapp.data.billing.ProFeature
import com.example.emptyapp.data.billing.ProFeatureGate
import com.example.emptyapp.ui.eink.EInkBorder
import com.example.emptyapp.ui.eink.EInkShapes
import com.example.emptyapp.ui.eink.EInkText
import kotlinx.coroutines.launch

@Composable
fun ProPaywallSheet(
  billing: PlayBillingManager,
  proGate: ProFeatureGate,
  feature: ProFeature? = null,
  highlightedFeature: ProFeature? = feature,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val billingState by billing.state.collectAsStateWithLifecycle()
  val entitlement by proGate.entitlement.collectAsStateWithLifecycle()
  var selectedProductId by remember { mutableStateOf(BillingConstants.PRODUCT_ANNUAL) }
  var message by remember { mutableStateOf<BillingMessage?>(null) }

  LaunchedEffect(billing) {
    if (!billingState.isConnected) billing.startConnection()
    billing.messages.collect { message = it }
  }

  val annualProduct = billingState.products[BillingConstants.PRODUCT_ANNUAL]
  val lifetimeProduct = billingState.products[BillingConstants.PRODUCT_LIFETIME]
  val monthlyProduct = billingState.products[BillingConstants.PRODUCT_MONTHLY]
  val annualPrice = annualProduct?.formattedPrice ?: stringResource(R.string.paywall_tier_annual_fallback_price)
  val lifetimePrice = lifetimeProduct?.formattedPrice ?: stringResource(R.string.paywall_tier_lifetime_fallback_price)
  val monthlyPrice = monthlyProduct?.formattedPrice ?: stringResource(R.string.paywall_tier_monthly_fallback_price)
  val policyUrl = stringResource(R.string.privacy_policy_url)

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background),
    ) {
      PaywallTopBar(onDismiss = onDismiss)
      HorizontalDivider(thickness = EInkBorder.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
      Column(
        modifier =
          Modifier.weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        message?.let { currentMsg ->
          PaywallMessageBanner(message = currentMsg, onDismiss = { message = null })
        }
        if (entitlement.isPro) {
          ActiveProSummaryCard(entitlement = entitlement)
          PaywallTipJarSection(
            products = billingState.products,
            onTip = { productId ->
              context.findActivity()?.let { billing.launchPurchase(it, productId) }
            },
          )
        } else {
          if (entitlement.isPending) {
            PendingBannerCard()
          }
          PaywallContextHeader(feature = highlightedFeature)
          PaywallFeatureList(highlightedFeature = highlightedFeature)
          PaywallTrustBadges()
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PaywallTierCard(
              title = stringResource(R.string.paywall_tier_annual_title),
              subtitle = stringResource(R.string.paywall_tier_annual_trial),
              price = annualPrice,
              badge = stringResource(R.string.paywall_tier_annual_badge),
              isSelected = selectedProductId == BillingConstants.PRODUCT_ANNUAL,
              onClick = { selectedProductId = BillingConstants.PRODUCT_ANNUAL },
            )
            PaywallTierCard(
              title = stringResource(R.string.paywall_tier_lifetime_title),
              subtitle = stringResource(R.string.paywall_tier_lifetime_desc),
              price = lifetimePrice,
              badge = stringResource(R.string.paywall_tier_lifetime_badge),
              isSelected = selectedProductId == BillingConstants.PRODUCT_LIFETIME,
              onClick = { selectedProductId = BillingConstants.PRODUCT_LIFETIME },
            )
            PaywallTierCard(
              title = stringResource(R.string.paywall_tier_monthly_title),
              subtitle = stringResource(R.string.paywall_tier_monthly_desc),
              price = monthlyPrice,
              badge = null,
              isSelected = selectedProductId == BillingConstants.PRODUCT_MONTHLY,
              onClick = { selectedProductId = BillingConstants.PRODUCT_MONTHLY },
            )
          }
          val ctaLabel =
            when {
              billingState.isLoadingCatalog && billingState.products.isEmpty() ->
                stringResource(R.string.paywall_cta_loading)
              selectedProductId == BillingConstants.PRODUCT_ANNUAL && (annualProduct?.hasFreeTrial ?: true) ->
                stringResource(R.string.paywall_cta_trial)
              selectedProductId == BillingConstants.PRODUCT_LIFETIME ->
                stringResource(R.string.paywall_cta_lifetime, lifetimePrice)
              selectedProductId == BillingConstants.PRODUCT_MONTHLY ->
                stringResource(R.string.paywall_cta_subscribe, monthlyPrice)
              else -> stringResource(R.string.paywall_cta_subscribe, annualPrice)
            }
          Button(
            onClick = { context.findActivity()?.let { billing.launchPurchase(it, selectedProductId) } },
            shape = EInkShapes.Control,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
          ) {
            EInkText(
              text = ctaLabel,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onPrimary,
            )
          }
          val disclosureText =
            when (selectedProductId) {
              BillingConstants.PRODUCT_LIFETIME -> stringResource(R.string.paywall_lifetime_disclosure)
              BillingConstants.PRODUCT_MONTHLY -> stringResource(R.string.paywall_sub_disclosure, monthlyPrice)
              else -> stringResource(R.string.paywall_sub_disclosure, annualPrice)
            }
          EInkText(
            text = disclosureText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = { scope.launch { billing.refreshPurchases(userInitiatedRestore = true) } },
            enabled = !billingState.isRestoring,
          ) {
            EInkText(
              text =
                if (billingState.isRestoring) stringResource(R.string.paywall_restoring)
                else stringResource(R.string.paywall_restore_btn),
              style = MaterialTheme.typography.labelLarge,
            )
          }
          TextButton(
            onClick = {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl))
              runCatching { context.startActivity(intent) }
            },
          ) {
            EInkText(
              text = stringResource(R.string.paywall_privacy_link),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }
    }
  }
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
