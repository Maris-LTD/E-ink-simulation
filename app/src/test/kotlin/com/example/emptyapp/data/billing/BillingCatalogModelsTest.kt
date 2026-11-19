package com.example.emptyapp.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingCatalogModelsTest {
  @Test
  fun selectBestSubscriptionOfferReturnsNullWhenEmpty() {
    assertNull(BillingCatalogHelper.selectBestSubscriptionOffer(emptyList()))
  }

  @Test
  fun selectBestSubscriptionOfferPrioritizesFreeTrialOffer() {
    val baseOffer =
      SubscriptionOfferInfo(
        offerToken = "base-token",
        pricingPhases =
          listOf(
            PricingPhaseInfo(
              priceAmountMicros = 11_990_000L,
              formattedPrice = "$11.99",
              billingPeriod = "P1Y",
              recurrenceMode = 1,
            )
          ),
      )
    val trialOffer =
      SubscriptionOfferInfo(
        offerToken = "trial-token",
        pricingPhases =
          listOf(
            PricingPhaseInfo(
              priceAmountMicros = 0L,
              formattedPrice = "Free",
              billingPeriod = "P1W",
              recurrenceMode = 2,
            ),
            PricingPhaseInfo(
              priceAmountMicros = 11_990_000L,
              formattedPrice = "$11.99",
              billingPeriod = "P1Y",
              recurrenceMode = 1,
            ),
          ),
      )

    val selected = BillingCatalogHelper.selectBestSubscriptionOffer(listOf(baseOffer, trialOffer))

    assertEquals("trial-token", selected?.offerToken)
    assertTrue(selected?.hasFreeTrial == true)
    assertEquals("P1W", selected?.freeTrialPeriod)
    assertEquals("$11.99", selected?.formattedPrice)
    assertEquals(11_990_000L, selected?.priceAmountMicros)
    assertEquals("P1Y", selected?.billingPeriod)
  }

  @Test
  fun selectBestSubscriptionOfferFallsBackToFirstWhenNoTrial() {
    val first =
      SubscriptionOfferInfo(
        offerToken = "monthly-token",
        pricingPhases =
          listOf(
            PricingPhaseInfo(
              priceAmountMicros = 2_490_000L,
              formattedPrice = "$2.49",
              billingPeriod = "P1M",
              recurrenceMode = 1,
            )
          ),
      )

    val selected = BillingCatalogHelper.selectBestSubscriptionOffer(listOf(first))

    assertEquals("monthly-token", selected?.offerToken)
    assertFalse(selected?.hasFreeTrial == true)
    assertNull(selected?.freeTrialPeriod)
    assertEquals("$2.49", selected?.formattedPrice)
  }

  @Test
  fun resolveProEntitlementPrefersLifetimeOverSubscription() {
    val records =
      listOf(
        PurchaseRecord(
          productIds = listOf(BillingConstants.PRODUCT_ANNUAL),
          isPurchased = true,
          isPending = false,
          purchaseTimeEpochMs = 2000L,
        ),
        PurchaseRecord(
          productIds = listOf(BillingConstants.PRODUCT_LIFETIME),
          isPurchased = true,
          isPending = false,
          purchaseTimeEpochMs = 1000L,
        ),
      )

    val resolved =
      BillingCatalogHelper.resolveProEntitlement(
        records = records,
        current = ProEntitlement(),
        nowEpochMs = 9999L,
      )

    assertTrue(resolved.isPro)
    assertEquals(BillingConstants.PRODUCT_LIFETIME, resolved.productId)
    assertEquals(PurchaseType.LIFETIME, resolved.purchaseType)
    assertEquals(1000L, resolved.purchaseTimeEpochMs)
    assertEquals(9999L, resolved.lastVerifiedAtEpochMs)
    assertFalse(resolved.isPending)
  }

  @Test
  fun resolveProEntitlementPrefersAnnualOverMonthlySubscription() {
    val records =
      listOf(
        PurchaseRecord(
          productIds = listOf(BillingConstants.PRODUCT_MONTHLY),
          isPurchased = true,
          isPending = false,
          purchaseTimeEpochMs = 3000L,
        ),
        PurchaseRecord(
          productIds = listOf(BillingConstants.PRODUCT_ANNUAL),
          isPurchased = true,
          isPending = false,
          purchaseTimeEpochMs = 1500L,
        ),
      )

    val resolved =
      BillingCatalogHelper.resolveProEntitlement(
        records = records,
        current = ProEntitlement(),
        nowEpochMs = 5000L,
      )

    assertTrue(resolved.isPro)
    assertEquals(BillingConstants.PRODUCT_ANNUAL, resolved.productId)
    assertEquals(PurchaseType.SUBSCRIPTION, resolved.purchaseType)
    assertEquals(1500L, resolved.purchaseTimeEpochMs)
  }

  @Test
  fun resolveProEntitlementIgnoresTipProductsAndResetsToFree() {
    val records =
      listOf(
        PurchaseRecord(
          productIds = listOf(BillingConstants.PRODUCT_TIP_COFFEE),
          isPurchased = true,
          isPending = false,
          purchaseTimeEpochMs = 1000L,
        )
      )
    val previousPro =
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_MONTHLY,
        purchaseType = PurchaseType.SUBSCRIPTION,
      )

    val resolved =
      BillingCatalogHelper.resolveProEntitlement(
        records = records,
        current = previousPro,
        nowEpochMs = 8000L,
      )

    assertEquals(ProEntitlement(), resolved)
    assertFalse(resolved.isPro)
  }

  @Test
  fun resolveProEntitlementPendingPreservesExistingProStatus() {
    val pending =
      listOf(
        PurchaseRecord(
          productIds = listOf(BillingConstants.PRODUCT_LIFETIME),
          isPurchased = false,
          isPending = true,
          purchaseTimeEpochMs = 4000L,
        )
      )

    val fromFree = BillingCatalogHelper.resolveProEntitlement(pending, ProEntitlement(), 7000L)
    assertFalse(fromFree.isPro)
    assertTrue(fromFree.isPending)

    val existingPro =
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_MONTHLY,
        purchaseType = PurchaseType.SUBSCRIPTION,
      )
    val fromPro = BillingCatalogHelper.resolveProEntitlement(pending, existingPro, 7000L)
    assertTrue(fromPro.isPro)
    assertTrue(fromPro.isPending)
    assertEquals(BillingConstants.PRODUCT_MONTHLY, fromPro.productId)
  }

  @Test
  fun productClassificationHelpers() {
    assertTrue(BillingCatalogHelper.isTipProduct(BillingConstants.PRODUCT_TIP_COFFEE))
    assertTrue(BillingCatalogHelper.isTipProduct(BillingConstants.PRODUCT_TIP_BOOK))
    assertFalse(BillingCatalogHelper.isTipProduct(BillingConstants.PRODUCT_LIFETIME))

    assertTrue(BillingCatalogHelper.isProProduct(BillingConstants.PRODUCT_MONTHLY))
    assertTrue(BillingCatalogHelper.isProProduct(BillingConstants.PRODUCT_ANNUAL))
    assertTrue(BillingCatalogHelper.isProProduct(BillingConstants.PRODUCT_LIFETIME))
    assertFalse(BillingCatalogHelper.isProProduct(BillingConstants.PRODUCT_TIP_COFFEE))
  }
}
