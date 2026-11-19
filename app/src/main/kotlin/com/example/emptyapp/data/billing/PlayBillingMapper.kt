package com.example.emptyapp.data.billing

import com.android.billingclient.api.ProductDetails

internal fun ProductDetails.toBillingProductUi(): BillingProductUi? {
  if (productType == com.android.billingclient.api.BillingClient.ProductType.SUBS) {
    val offers =
      subscriptionOfferDetails.orEmpty().map { offer ->
        SubscriptionOfferInfo(
          offerToken = offer.offerToken,
          pricingPhases =
            offer.pricingPhases.pricingPhaseList.map { phase ->
              PricingPhaseInfo(
                priceAmountMicros = phase.priceAmountMicros,
                formattedPrice = phase.formattedPrice,
                billingPeriod = phase.billingPeriod,
                recurrenceMode = phase.recurrenceMode,
              )
            },
        )
      }
    val selected = BillingCatalogHelper.selectBestSubscriptionOffer(offers) ?: return null
    val currencyCode =
      subscriptionOfferDetails
        ?.firstOrNull { it.offerToken == selected.offerToken }
        ?.pricingPhases
        ?.pricingPhaseList
        ?.lastOrNull()
        ?.priceCurrencyCode
        .orEmpty()
    return BillingProductUi(
      productId = productId,
      title = title,
      description = description,
      formattedPrice = selected.formattedPrice,
      priceAmountMicros = selected.priceAmountMicros,
      priceCurrencyCode = currencyCode,
      isSubscription = true,
      hasFreeTrial = selected.hasFreeTrial,
      freeTrialPeriod = selected.freeTrialPeriod,
      billingPeriod = selected.billingPeriod,
      offerToken = selected.offerToken,
    )
  }
  val oneTime = oneTimePurchaseOfferDetails ?: return null
  return BillingProductUi(
    productId = productId,
    title = title,
    description = description,
    formattedPrice = oneTime.formattedPrice,
    priceAmountMicros = oneTime.priceAmountMicros,
    priceCurrencyCode = oneTime.priceCurrencyCode,
    isSubscription = false,
  )
}
