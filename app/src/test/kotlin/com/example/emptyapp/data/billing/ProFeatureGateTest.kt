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
        ProFeature.FOCUS_MODE,
        ProFeature.LAUNCHER_CUSTOMIZATION,
        ProFeature.ADVANCED_PAPER_THEMES,
        ProFeature.EXPORT_NOTES,
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

  @Test
  fun tamperedBuildDeniesProAccessEvenWhenEntitlementIsPro() {
    store.write(
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_LIFETIME,
        purchaseType = PurchaseType.LIFETIME,
      )
    )
    val gate = ProFeatureGate(store) { false }

    assertFalse(gate.isPro)
    assertFalse(gate.effectiveIsPro(gate.entitlement.value))
    assertFalse(gate.hasAccess(ProFeature.DRIVE_SYNC))
    assertFalse(gate.hasAccess(ProFeature.FOCUS_MODE))
    assertFalse(gate.hasAccess(ProFeature.ADAPTIVE_PDF_REFLOW, pdfPageIndexZeroBased = 10))
  }

  @Test
  fun helperGatesEnforceFreeBoundariesAndUnlockForPro() {
    val gate = ProFeatureGate(store)

    assertTrue(gate.canPinMoreApps(currentCount = 3, isPro = false))
    assertFalse(gate.canPinMoreApps(currentCount = 4, isPro = false))
    assertTrue(gate.canPinMoreApps(currentCount = 10, isPro = true))

    assertTrue(gate.canUseTheme("HIGH_CONTRAST", isPro = false))
    assertTrue(gate.canUseTheme("PAPER", isPro = false))
    assertTrue(gate.canUseTheme("DARK", isPro = false))
    assertFalse(gate.canUseTheme("AMOLED", isPro = false))
    assertFalse(gate.canUseTheme("WARM", isPro = false))
    assertTrue(gate.canUseTheme("AMOLED", isPro = true))
    assertTrue(gate.canUseTheme("WARM", isPro = true))

    assertFalse(gate.canExportNotes(isPro = false))
    assertTrue(gate.canExportNotes(isPro = true))
  }
}
