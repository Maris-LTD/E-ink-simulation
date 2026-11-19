package com.example.emptyapp.data.billing

data class BillingProductUi(
  val productId: String,
  val title: String,
  val description: String,
  val formattedPrice: String,
  val priceAmountMicros: Long,
  val priceCurrencyCode: String,
  val isSubscription: Boolean,
  val hasFreeTrial: Boolean = false,
  val freeTrialPeriod: String? = null,
  val billingPeriod: String? = null,
  val offerToken: String? = null,
)

data class BillingState(
  val isConnected: Boolean = false,
  val isLoadingCatalog: Boolean = false,
  val isRestoring: Boolean = false,
  val products: Map<String, BillingProductUi> = emptyMap(),
)

sealed interface BillingMessage {
  data class PurchaseSuccess(val productId: String) : BillingMessage

  data class TipSuccess(val productId: String) : BillingMessage

  data object PurchasePending : BillingMessage

  data object PurchaseCanceled : BillingMessage

  data object PurchasesRestored : BillingMessage

  data object NoPurchasesToRestore : BillingMessage

  data class Error(val responseCode: Int, val debugMessage: String) : BillingMessage
}

data class PricingPhaseInfo(
  val priceAmountMicros: Long,
  val formattedPrice: String,
  val billingPeriod: String,
  val recurrenceMode: Int,
)

data class SubscriptionOfferInfo(
  val offerToken: String,
  val pricingPhases: List<PricingPhaseInfo>,
)

data class SelectedSubscriptionOffer(
  val offerToken: String,
  val formattedPrice: String,
  val priceAmountMicros: Long,
  val billingPeriod: String?,
  val hasFreeTrial: Boolean,
  val freeTrialPeriod: String?,
)

data class PurchaseRecord(
  val productIds: List<String>,
  val isPurchased: Boolean,
  val isPending: Boolean,
  val purchaseTimeEpochMs: Long,
  val isAcknowledged: Boolean = true,
)

object BillingCatalogHelper {
  val PRO_SUBSCRIPTION_IDS =
    setOf(
      BillingConstants.PRODUCT_MONTHLY,
      BillingConstants.PRODUCT_ANNUAL,
    )

  val TIP_PRODUCT_IDS =
    setOf(
      BillingConstants.PRODUCT_TIP_COFFEE,
      BillingConstants.PRODUCT_TIP_BOOK,
    )

  fun isTipProduct(productId: String): Boolean = productId in TIP_PRODUCT_IDS

  fun isProProduct(productId: String): Boolean =
    productId in PRO_SUBSCRIPTION_IDS || productId == BillingConstants.PRODUCT_LIFETIME

  fun selectBestSubscriptionOffer(offers: List<SubscriptionOfferInfo>): SelectedSubscriptionOffer? {
    if (offers.isEmpty()) return null
    val chosen =
      offers.firstOrNull { offer -> offer.pricingPhases.any { it.priceAmountMicros == 0L } }
        ?: offers.first()
    val trialPhase = chosen.pricingPhases.firstOrNull { it.priceAmountMicros == 0L }
    val paidPhase =
      chosen.pricingPhases.lastOrNull { it.priceAmountMicros > 0L }
        ?: chosen.pricingPhases.lastOrNull()
    return SelectedSubscriptionOffer(
      offerToken = chosen.offerToken,
      formattedPrice = paidPhase?.formattedPrice.orEmpty(),
      priceAmountMicros = paidPhase?.priceAmountMicros ?: 0L,
      billingPeriod = paidPhase?.billingPeriod,
      hasFreeTrial = trialPhase != null,
      freeTrialPeriod = trialPhase?.billingPeriod,
    )
  }

  fun resolveProEntitlement(
    records: List<PurchaseRecord>,
    current: ProEntitlement,
    nowEpochMs: Long = System.currentTimeMillis(),
  ): ProEntitlement {
    val proRecords = records.filter { record -> record.productIds.any(::isProProduct) }
    val purchased = proRecords.filter { it.isPurchased }
    if (purchased.isNotEmpty()) {
      val lifetime =
        purchased.firstOrNull { BillingConstants.PRODUCT_LIFETIME in it.productIds }
      if (lifetime != null) {
        return ProEntitlement(
          isPro = true,
          productId = BillingConstants.PRODUCT_LIFETIME,
          purchaseType = PurchaseType.LIFETIME,
          purchaseTimeEpochMs = lifetime.purchaseTimeEpochMs,
          lastVerifiedAtEpochMs = nowEpochMs,
          isPending = false,
        )
      }
      val bestSub =
        purchased
          .sortedWith(
            compareByDescending<PurchaseRecord> { BillingConstants.PRODUCT_ANNUAL in it.productIds }
              .thenByDescending { it.purchaseTimeEpochMs }
          )
          .first()
      val subProductId = bestSub.productIds.firstOrNull { it in PRO_SUBSCRIPTION_IDS }
      return ProEntitlement(
        isPro = true,
        productId = subProductId,
        purchaseType = PurchaseType.SUBSCRIPTION,
        purchaseTimeEpochMs = bestSub.purchaseTimeEpochMs,
        lastVerifiedAtEpochMs = nowEpochMs,
        isPending = false,
      )
    }

    if (proRecords.any { it.isPending }) {
      return current.copy(
        isPending = true,
        lastVerifiedAtEpochMs = nowEpochMs,
      )
    }

    return ProEntitlement()
  }
}
