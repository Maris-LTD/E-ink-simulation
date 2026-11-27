package com.example.emptyapp.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayBillingManager(
  context: Context,
  private val featureGate: ProFeatureGate,
  private val scope: CoroutineScope,
) {
  private val mutableState = MutableStateFlow(BillingState())
  val state: StateFlow<BillingState> = mutableState.asStateFlow()

  private val mutableMessages = MutableSharedFlow<BillingMessage>(extraBufferCapacity = 16)
  val messages: SharedFlow<BillingMessage> = mutableMessages.asSharedFlow()

  private val productDetailsById = ConcurrentHashMap<String, ProductDetails>()
  private val isConnecting = AtomicBoolean(false)

  private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
    when (result.responseCode) {
      BillingResponseCode.OK -> scope.launch { handlePurchases(purchases.orEmpty(), emitMessages = true) }
      BillingResponseCode.USER_CANCELED -> mutableMessages.tryEmit(BillingMessage.PurchaseCanceled)
      BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refreshPurchases(userInitiatedRestore = true) }
      else -> mutableMessages.tryEmit(BillingMessage.Error(result.responseCode, result.debugMessage))
    }
  }

  private val billingClient: BillingClient =
    BillingClient.newBuilder(context.applicationContext)
      .setListener(purchasesListener)
      .enablePendingPurchases(
        PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build()
      )
      .enableAutoServiceReconnection()
      .build()

  private val stateListener =
    object : BillingClientStateListener {
      override fun onBillingSetupFinished(result: BillingResult) {
        isConnecting.set(false)
        if (result.responseCode == BillingResponseCode.OK) {
          mutableState.update { it.copy(isConnected = true) }
          scope.launch {
            queryCatalog()
            refreshPurchases()
          }
        } else {
          mutableState.update { it.copy(isConnected = false) }
          mutableMessages.tryEmit(BillingMessage.Error(result.responseCode, result.debugMessage))
        }
      }

      override fun onBillingServiceDisconnected() {
        isConnecting.set(false)
        mutableState.update { it.copy(isConnected = false) }
      }
    }

  fun startConnection() {
    if (billingClient.isReady || !isConnecting.compareAndSet(false, true)) return
    billingClient.startConnection(stateListener)
  }

  suspend fun queryCatalog() {
    if (!billingClient.isReady) {
      startConnection()
      return
    }
    mutableState.update { it.copy(isLoadingCatalog = true) }
    val (subs, subsResult) =
      queryDetails(ProductType.SUBS, listOf(BillingConstants.PRODUCT_MONTHLY, BillingConstants.PRODUCT_ANNUAL))
    val (inapp, inappResult) =
      queryDetails(
        ProductType.INAPP,
        listOf(BillingConstants.PRODUCT_LIFETIME, BillingConstants.PRODUCT_TIP_COFFEE, BillingConstants.PRODUCT_TIP_BOOK),
      )
    val mapped = LinkedHashMap<String, BillingProductUi>(subs.size + inapp.size)
    (subs + inapp).forEach { details ->
      productDetailsById[details.productId] = details
      details.toBillingProductUi()?.let { mapped[it.productId] = it }
    }
    mutableState.update { it.copy(isLoadingCatalog = false, products = mapped) }
    val failedResult =
      when {
        subsResult.responseCode != BillingResponseCode.OK -> subsResult
        inappResult.responseCode != BillingResponseCode.OK -> inappResult
        else -> null
      }
    if (failedResult != null) {
      mutableMessages.tryEmit(BillingMessage.Error(failedResult.responseCode, failedResult.debugMessage))
    }
  }

  suspend fun refreshPurchases(userInitiatedRestore: Boolean = false) {
    if (!billingClient.isReady) {
      startConnection()
      if (userInitiatedRestore) {
        mutableMessages.tryEmit(BillingMessage.Error(BillingResponseCode.SERVICE_DISCONNECTED, "Billing service not connected"))
      }
      return
    }
    if (userInitiatedRestore) mutableState.update { it.copy(isRestoring = true) }
    val subsResult = billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build())
    val inappResult = billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build())
    val failed =
      when {
        subsResult.billingResult.responseCode != BillingResponseCode.OK -> subsResult.billingResult
        inappResult.billingResult.responseCode != BillingResponseCode.OK -> inappResult.billingResult
        else -> null
      }
    if (failed != null) {
      if (userInitiatedRestore) {
        mutableState.update { it.copy(isRestoring = false) }
        mutableMessages.tryEmit(BillingMessage.Error(failed.responseCode, failed.debugMessage))
      }
      return
    }
    handlePurchases(subsResult.purchasesList + inappResult.purchasesList, emitMessages = false, resetIfEmpty = true)
    if (userInitiatedRestore) {
      mutableState.update { it.copy(isRestoring = false) }
      mutableMessages.tryEmit(if (featureGate.isPro) BillingMessage.PurchasesRestored else BillingMessage.NoPurchasesToRestore)
    }
  }

  fun launchPurchase(activity: Activity, productId: String): BillingResult {
    if (!billingClient.isReady) {
      startConnection()
      val msg = "Billing service not connected"
      mutableMessages.tryEmit(BillingMessage.Error(BillingResponseCode.SERVICE_DISCONNECTED, msg))
      return BillingResult.newBuilder().setResponseCode(BillingResponseCode.SERVICE_DISCONNECTED).setDebugMessage(msg).build()
    }
    val details = productDetailsById[productId]
    if (details == null) {
      scope.launch { queryCatalog() }
      val msg = "Product not ready: $productId"
      mutableMessages.tryEmit(BillingMessage.Error(BillingResponseCode.ITEM_UNAVAILABLE, msg))
      return BillingResult.newBuilder().setResponseCode(BillingResponseCode.ITEM_UNAVAILABLE).setDebugMessage(msg).build()
    }
    val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
    if (details.productType == ProductType.SUBS) {
      val offerToken = mutableState.value.products[productId]?.offerToken ?: details.subscriptionOfferDetails?.firstOrNull()?.offerToken
      if (offerToken.isNullOrBlank()) {
        val msg = "No active subscription offer: $productId"
        mutableMessages.tryEmit(BillingMessage.Error(BillingResponseCode.ITEM_UNAVAILABLE, msg))
        return BillingResult.newBuilder().setResponseCode(BillingResponseCode.ITEM_UNAVAILABLE).setDebugMessage(msg).build()
      }
      paramsBuilder.setOfferToken(offerToken)
    }
    val flowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(paramsBuilder.build())).build()
    val result = billingClient.launchBillingFlow(activity, flowParams)
    if (result.responseCode != BillingResponseCode.OK && result.responseCode != BillingResponseCode.USER_CANCELED) {
      mutableMessages.tryEmit(BillingMessage.Error(result.responseCode, result.debugMessage))
    }
    return result
  }

  private suspend fun queryDetails(productType: String, productIds: List<String>): Pair<List<ProductDetails>, BillingResult> {
    val products =
      productIds.map { id ->
        QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(productType).build()
      }
    val result = billingClient.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(products).build())
    val list = if (result.billingResult.responseCode == BillingResponseCode.OK) result.productDetailsList.orEmpty() else emptyList()
    return list to result.billingResult
  }

  private suspend fun handlePurchases(purchases: List<Purchase>, emitMessages: Boolean, resetIfEmpty: Boolean = false) {
    val records = ArrayList<PurchaseRecord>(purchases.size)
    for (purchase in purchases) {
      val isPurchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED
      val isPending = purchase.purchaseState == Purchase.PurchaseState.PENDING
      if (isPurchased && purchase.products.any(BillingCatalogHelper::isTipProduct)) {
        val consumed = billingClient.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
        if (emitMessages && consumed.billingResult.responseCode == BillingResponseCode.OK) {
          mutableMessages.tryEmit(BillingMessage.TipSuccess(purchase.products.firstOrNull().orEmpty()))
        }
        continue
      }
      if (isPurchased && !purchase.isAcknowledged) {
        billingClient.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
      }
      if (purchase.products.any(BillingCatalogHelper::isProProduct)) {
        records.add(
          PurchaseRecord(
            productIds = purchase.products,
            isPurchased = isPurchased,
            isPending = isPending,
            purchaseTimeEpochMs = purchase.purchaseTime,
            isAcknowledged = purchase.isAcknowledged,
          )
        )
      }
    }
    if (records.isEmpty() && !resetIfEmpty) return
    val current = featureGate.entitlement.value
    val resolved = BillingCatalogHelper.resolveProEntitlement(records, current)
    if (resolved.isPro || resolved.isPending || (resetIfEmpty && !current.isPending)) {
      featureGate.updateEntitlement(resolved)
    }
    if (emitMessages) {
      if (resolved.isPro && resolved.productId != null) {
        mutableMessages.tryEmit(BillingMessage.PurchaseSuccess(resolved.productId))
      } else if (resolved.isPending) {
        mutableMessages.tryEmit(BillingMessage.PurchasePending)
      }
    }
  }
}
