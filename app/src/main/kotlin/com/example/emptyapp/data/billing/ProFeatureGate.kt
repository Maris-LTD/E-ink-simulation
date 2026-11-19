package com.example.emptyapp.data.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProFeatureGate(
  private val store: EntitlementStore,
  private val isIntegrityValid: () -> Boolean = { true },
) {
  private val lock = Any()
  private val mutableEntitlement = MutableStateFlow(store.read())

  val entitlement: StateFlow<ProEntitlement> = mutableEntitlement.asStateFlow()

  val isPro: Boolean
    get() = mutableEntitlement.value.isPro && isIntegrityValid()

  fun hasAccess(feature: ProFeature, pdfPageIndexZeroBased: Int? = null): Boolean {
    if (isPro) return true
    if (
      feature == ProFeature.ADAPTIVE_PDF_REFLOW &&
        pdfPageIndexZeroBased != null &&
        pdfPageIndexZeroBased in 0 until BillingConstants.PDF_FREE_PREVIEW_PAGES
    ) {
      return true
    }
    return false
  }

  fun updateEntitlement(entitlement: ProEntitlement) {
    synchronized(lock) {
      store.write(entitlement)
      mutableEntitlement.value = entitlement
    }
  }

  fun clear() {
    synchronized(lock) {
      val empty = ProEntitlement()
      store.write(empty)
      mutableEntitlement.value = empty
    }
  }
}
