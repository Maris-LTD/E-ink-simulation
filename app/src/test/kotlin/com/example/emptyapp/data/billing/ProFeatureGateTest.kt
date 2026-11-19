package com.example.emptyapp.data.billing

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProFeatureGateTest {
  private lateinit var dir: File
  private lateinit var store: EntitlementStore

  @Before
  fun setUp() {
    dir = createTempDirectory().toFile()
    store = EntitlementStore(dir)
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  @Test
  fun initializesStateFlowFromPersistedStore() {
    val saved =
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_LIFETIME,
        purchaseType = PurchaseType.LIFETIME,
        purchaseTimeEpochMs = 100L,
        lastVerifiedAtEpochMs = 200L,
      )
    store.write(saved)

    val gate = ProFeatureGate(store)

    assertTrue(gate.isPro)
    assertEquals(saved, gate.entitlement.value)
  }

  @Test
  fun freeUserDeniedAllNonPdfFeaturesEvenWithPageZero() {
    val gate = ProFeatureGate(store)
    val nonPdfFeatures =
      listOf(
        ProFeature.DRIVE_SYNC,
        ProFeature.LOCKSCREEN_WALLPAPER,
        ProFeature.EINK_LAUNCHER,
        ProFeature.FOCUS_MODE,
      )

    assertFalse(gate.isPro)
    for (feature in nonPdfFeatures) {
      assertFalse(gate.hasAccess(feature))
      assertFalse(gate.hasAccess(feature, pdfPageIndexZeroBased = 0))
    }
  }

  @Test
  fun freeUserHasFivePagePdfReflowPreviewBoundary() {
    val gate = ProFeatureGate(store)

    assertTrue(gate.hasAccess(ProFeature.ADAPTIVE_PDF_REFLOW, pdfPageIndexZeroBased = 0))
    assertTrue(gate.hasAccess(ProFeature.ADAPTIVE_PDF_REFLOW, pdfPageIndexZeroBased = 4))
    assertFalse(gate.hasAccess(ProFeature.ADAPTIVE_PDF_REFLOW, pdfPageIndexZeroBased = 5))
    assertFalse(gate.hasAccess(ProFeature.ADAPTIVE_PDF_REFLOW, pdfPageIndexZeroBased = -1))
    assertFalse(gate.hasAccess(ProFeature.ADAPTIVE_PDF_REFLOW, pdfPageIndexZeroBased = null))
  }

  @Test
  fun proUserHasAccessToAllFeaturesRegardlessOfPageIndex() {
    val gate = ProFeatureGate(store)
    gate.updateEntitlement(
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_ANNUAL,
        purchaseType = PurchaseType.SUBSCRIPTION,
      )
    )

    assertTrue(gate.isPro)
    for (feature in ProFeature.entries) {
      assertTrue(gate.hasAccess(feature))
      assertTrue(gate.hasAccess(feature, pdfPageIndexZeroBased = 0))
      assertTrue(gate.hasAccess(feature, pdfPageIndexZeroBased = 5))
      assertTrue(gate.hasAccess(feature, pdfPageIndexZeroBased = 99))
    }
  }

  @Test
  fun updateEntitlementAndClearUpdateStateFlowAndPersist() {
    val gate = ProFeatureGate(store)
    val active =
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_MONTHLY,
        purchaseType = PurchaseType.SUBSCRIPTION,
        purchaseTimeEpochMs = 1234L,
        lastVerifiedAtEpochMs = 5678L,
      )

    gate.updateEntitlement(active)
    assertEquals(active, gate.entitlement.value)
    assertTrue(gate.isPro)
    assertEquals(active, store.read())

    gate.clear()
    assertEquals(ProEntitlement(), gate.entitlement.value)
    assertFalse(gate.isPro)
    assertEquals(ProEntitlement(), store.read())
    assertFalse(gate.hasAccess(ProFeature.ADAPTIVE_PDF_REFLOW, pdfPageIndexZeroBased = 5))
  }
}
