package com.example.emptyapp.data.billing

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class EntitlementStoreTest {
  private lateinit var dir: File

  @Before
  fun setUp() {
    dir = createTempDirectory().toFile()
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  @Test
  fun missingFileReturnsDefaultEntitlement() {
    val loaded = EntitlementStore(dir).read()

    assertEquals(ProEntitlement(), loaded)
    assertFalse(loaded.isPro)
    assertEquals(PurchaseType.NONE, loaded.purchaseType)
  }

  @Test
  fun saveAndLoadRoundTripPreservesAllFields() {
    val store = EntitlementStore(dir)
    val expected =
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_ANNUAL,
        purchaseType = PurchaseType.SUBSCRIPTION,
        purchaseTimeEpochMs = 1_700_000_000_000L,
        lastVerifiedAtEpochMs = 1_700_086_400_000L,
        isPending = false,
      )

    store.write(expected)

    assertEquals(expected, EntitlementStore(dir).read())
  }

  @Test
  fun corruptedFileFallsBackToDefaultAndAllowsSubsequentWrite() {
    File(dir, EntitlementStore.DEFAULT_FILE_NAME).writeText("{not-valid-json")
    val store = EntitlementStore(dir)

    assertEquals(ProEntitlement(), store.read())

    val lifetime =
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_LIFETIME,
        purchaseType = PurchaseType.LIFETIME,
        purchaseTimeEpochMs = 1_700_000_123_000L,
        lastVerifiedAtEpochMs = 1_700_000_123_000L,
        isPending = false,
      )
    store.write(lifetime)

    assertEquals(lifetime, store.read())
  }

  @Test
  fun clearResetsPersistedEntitlementToDefault() {
    val store = EntitlementStore(dir)
    store.write(
      ProEntitlement(
        isPro = true,
        productId = BillingConstants.PRODUCT_MONTHLY,
        purchaseType = PurchaseType.SUBSCRIPTION,
      )
    )

    store.clear()

    assertEquals(ProEntitlement(), store.read())
  }
}
