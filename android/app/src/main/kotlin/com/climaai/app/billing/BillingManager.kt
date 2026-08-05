package com.climaai.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing subscriptions.
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {
    
    companion object {
        private const val TAG = "BillingManager"
        
        // Product IDs - must match Play Console
        const val PRODUCT_MONTHLY = "climaai_pro_monthly"
        const val PRODUCT_YEARLY = "climaai_pro_yearly"
        const val PRODUCT_LIFETIME = "climaai_pro_lifetime"
        
        @Volatile
        private var INSTANCE: BillingManager? = null
        
        fun getInstance(context: Context): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        // Billing 8 removed the no-argument enablePendingPurchases(). This is the
        // documented equivalent of what it used to do. Prepaid plans are left off
        // because none of the three products is a prepaid subscription — turning
        // them on would make Play surface prepaid top-ups we do not sell.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()
    
    private val _connectionState = MutableStateFlow(BillingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BillingConnectionState> = _connectionState.asStateFlow()
    
    private val _subscriptionStatus = MutableStateFlow(SubscriptionState())
    val subscriptionStatus: StateFlow<SubscriptionState> = _subscriptionStatus.asStateFlow()
    
    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()
    
    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress: StateFlow<Boolean> = _purchaseInProgress.asStateFlow()

    // Fresh purchases only — onPurchasesUpdated, not the queryPurchases restore
    // path, which replays every owned purchase on each connection and would
    // re-announce them on every app start. An event, not state: each completed
    // purchase must be reported to the backend exactly once per occurrence.
    // Buffered so tryEmit succeeds from Play's callback thread with no scope.
    private val _purchaseCompleted = MutableSharedFlow<CompletedPurchase>(extraBufferCapacity = 4)
    val purchaseCompleted: SharedFlow<CompletedPurchase> = _purchaseCompleted.asSharedFlow()
    
    // =========================================================================
    // Connection
    // =========================================================================
    
    fun startConnection() {
        if (billingClient.isReady) {
            Log.d(TAG, "BillingClient already connected")
            return
        }
        
        _connectionState.value = BillingConnectionState.CONNECTING
        
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient connected")
                    _connectionState.value = BillingConnectionState.CONNECTED
                    queryProducts()
                    queryPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _connectionState.value = BillingConnectionState.ERROR
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "BillingClient disconnected")
                _connectionState.value = BillingConnectionState.DISCONNECTED
            }
        })
    }
    
    fun endConnection() {
        billingClient.endConnection()
        _connectionState.value = BillingConnectionState.DISCONNECTED
    }
    
    // =========================================================================
    // Query Products
    // =========================================================================
    
    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_LIFETIME)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        // Billing 8 hands back a QueryProductDetailsResult instead of a bare list.
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val fetched = productDetailsResult.productDetailsList
                _products.value = fetched
                Log.d(TAG, "Found ${fetched.size} products")

                // A product that is missing, unpublished or misconfigured in Play
                // Console now comes back here rather than being silently dropped
                // from the list. Without this the paywall would just show one
                // fewer plan and nothing would say why.
                productDetailsResult.unfetchedProductList.forEach { unfetched ->
                    Log.e(
                        TAG,
                        "Product unavailable: ${unfetched.productId} " +
                            "(status ${unfetched.statusCode})"
                    )
                }
            } else {
                Log.e(TAG, "Product query failed: ${billingResult.debugMessage}")
            }
        }
    }
    
    // =========================================================================
    // Query Purchases
    // =========================================================================
    
    private fun queryPurchases() {
        // Query subscriptions
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
        
        // Query one-time purchases (lifetime)
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            }
        }
    }
    
    private fun processPurchases(purchases: List<Purchase>) {
        val activePurchase = purchases.firstOrNull { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        
        if (activePurchase != null) {
            // Acknowledge if needed
            if (!activePurchase.isAcknowledged) {
                acknowledgePurchase(activePurchase)
            }
            
            val productId = activePurchase.products.firstOrNull() ?: ""
            _subscriptionStatus.value = SubscriptionState(
                isPro = true,
                productId = productId,
                planName = getPlanName(productId),
                expiryTime = activePurchase.purchaseTime + getSubscriptionDuration(productId)
            )
            Log.d(TAG, "Active subscription: $productId")
        } else {
            _subscriptionStatus.value = SubscriptionState(isPro = false)
            Log.d(TAG, "No active subscription")
        }
    }
    
    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            } else {
                Log.e(TAG, "Acknowledgement failed: ${billingResult.debugMessage}")
            }
        }
    }
    
    // =========================================================================
    // Launch Purchase
    // =========================================================================
    
    fun launchPurchase(activity: Activity, productDetails: ProductDetails): Boolean {
        if (_purchaseInProgress.value) {
            Log.w(TAG, "Purchase already in progress")
            return false
        }
        
        _purchaseInProgress.value = true
        
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        
        val productDetailsParams = if (offerToken != null) {
            // Subscription
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        } else {
            // One-time purchase
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        }
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _purchaseInProgress.value = false
            Log.e(TAG, "Launch failed: ${billingResult.debugMessage}")
            return false
        }
        
        return true
    }
    
    // =========================================================================
    // PurchasesUpdatedListener
    // =========================================================================
    
    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        _purchaseInProgress.value = false
        
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { updated ->
                    processPurchases(updated)

                    updated
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .forEach { purchase ->
                            val productId = purchase.products.firstOrNull() ?: return@forEach
                            _purchaseCompleted.tryEmit(
                                CompletedPurchase(productId, purchase.purchaseToken)
                            )
                        }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Purchase cancelled by user")
            }
            else -> {
                Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }
    
    // =========================================================================
    // Helpers
    // =========================================================================
    
    private fun getPlanName(productId: String): String {
        return when (productId) {
            PRODUCT_MONTHLY -> "Monthly"
            PRODUCT_YEARLY -> "Yearly"
            PRODUCT_LIFETIME -> "Lifetime"
            else -> "Pro"
        }
    }
    
    private fun getSubscriptionDuration(productId: String): Long {
        return when (productId) {
            PRODUCT_MONTHLY -> 30L * 24 * 60 * 60 * 1000  // 30 days
            PRODUCT_YEARLY -> 365L * 24 * 60 * 60 * 1000  // 365 days
            PRODUCT_LIFETIME -> Long.MAX_VALUE
            else -> 0L
        }
    }
    
    /**
     * Get formatted price for a product.
     */
    fun getFormattedPrice(productId: String): String? {
        val product = _products.value.find { it.productId == productId }
        return product?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases
            ?.pricingPhaseList?.firstOrNull()?.formattedPrice
            ?: product?.oneTimePurchaseOfferDetails?.formattedPrice
    }
    
    /**
     * Check if user has active premium subscription.
     */
    fun isPro(): Boolean = _subscriptionStatus.value.isPro
    
    /**
     * Restore purchases (force refresh).
     */
    fun restorePurchases() {
        if (billingClient.isReady) {
            queryPurchases()
        } else {
            startConnection()
        }
    }
}

enum class BillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class SubscriptionState(
    val isPro: Boolean = false,
    val productId: String = "",
    val planName: String = "",
    val expiryTime: Long = 0
)

/**
 * A purchase Play just confirmed, carrying exactly what the backend's
 * /api/subscriptions/activate needs: which product, and the token it can
 * re-validate with Google.
 */
data class CompletedPurchase(
    val productId: String,
    val purchaseToken: String
)
