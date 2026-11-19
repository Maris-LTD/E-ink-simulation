package com.example.emptyapp.data.billing

import kotlinx.serialization.Serializable

@Serializable
enum class PurchaseType {
  NONE,
  SUBSCRIPTION,
  LIFETIME,
}

@Serializable
data class ProEntitlement(
  val isPro: Boolean = false,
  val productId: String? = null,
  val purchaseType: PurchaseType = PurchaseType.NONE,
  val purchaseTimeEpochMs: Long = 0L,
  val lastVerifiedAtEpochMs: Long = 0L,
  val isPending: Boolean = false,
)

object BillingConstants {
  const val PRODUCT_MONTHLY = "inkify_pro_monthly"
  const val PRODUCT_ANNUAL = "inkify_pro_annual"
  const val PRODUCT_LIFETIME = "inkify_pro_lifetime"
  const val PRODUCT_TIP_COFFEE = "inkify_tip_coffee"
  const val PRODUCT_TIP_BOOK = "inkify_tip_book"
  const val PDF_FREE_PREVIEW_PAGES = 5
}
