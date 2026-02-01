package com.climaai.app.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.climaai.app.ads.AdManager
import com.climaai.app.billing.BillingConnectionState
import com.climaai.app.billing.BillingManager
import com.climaai.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Subscription tier enum
 */
enum class SubscriptionTier {
    FREE,
    TRIAL,
    PREMIUM_MONTHLY,
    PREMIUM_ANNUAL,
    PREMIUM_LIFETIME
}

/**
 * Subscription state
 */
data class SubscriptionState(
    val isPremium: Boolean = false,
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val expiresAt: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val plans: List<SubscriptionPlan> = emptyList(),
    val trialDays: Int = 7,
    val trialDaysRemaining: Int = 0,
    val isBillingConnected: Boolean = false
)

/**
 * ViewModel for subscription and in-app purchases.
 * Integrates with Google Play Billing via BillingManager.
 */
class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val billingManager = BillingManager.getInstance(application)
    private val proFeaturesManager = ProFeaturesManager(application)
    
    private val _state = MutableStateFlow(SubscriptionState())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    // Convenience properties
    val isPremium: Boolean get() = _state.value.isPremium

    companion object {
        const val PRODUCT_MONTHLY = BillingManager.PRODUCT_MONTHLY
        const val PRODUCT_ANNUAL = BillingManager.PRODUCT_YEARLY
        const val PRODUCT_LIFETIME = BillingManager.PRODUCT_LIFETIME
    }

    init {
        observeBillingState()
    }

    /**
     * Initialize subscription state
     */
    fun initialize(context: Context) {
        billingManager.startConnection()
        loadPlans()
    }

    /**
     * Observe billing manager state changes
     */
    private fun observeBillingState() {
        viewModelScope.launch {
            // Combine billing connection and subscription status
            combine(
                billingManager.connectionState,
                billingManager.subscriptionStatus,
                billingManager.purchaseInProgress,
                proFeaturesManager.isPro,
                proFeaturesManager.trialDaysRemaining
            ) { connectionState, subStatus, purchaseInProgress, isPro, trialDays ->
                
                val tier = when {
                    subStatus.productId == PRODUCT_MONTHLY -> SubscriptionTier.PREMIUM_MONTHLY
                    subStatus.productId == PRODUCT_ANNUAL -> SubscriptionTier.PREMIUM_ANNUAL
                    subStatus.productId == PRODUCT_LIFETIME -> SubscriptionTier.PREMIUM_LIFETIME
                    trialDays > 0 -> SubscriptionTier.TRIAL
                    else -> SubscriptionTier.FREE
                }
                
                _state.value.copy(
                    isPremium = isPro,
                    currentTier = tier,
                    isLoading = purchaseInProgress,
                    isBillingConnected = connectionState == BillingConnectionState.CONNECTED,
                    trialDaysRemaining = trialDays
                )
            }.collect { newState ->
                _state.value = newState
                
                // Update ad visibility based on premium status
                AdManager.setAdsEnabled(!newState.isPremium)
            }
        }
    }

    /**
     * Load available subscription plans
     */
    private fun loadPlans() {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getPlans()
                
                if (response.isSuccessful && response.body() != null) {
                    val plansResponse = response.body()!!
                    _state.value = _state.value.copy(
                        plans = plansResponse.plans,
                        trialDays = plansResponse.trial.duration_days
                    )
                }
            } catch (e: Exception) {
                // Use default plans with prices from billing if available
                _state.value = _state.value.copy(
                    plans = listOf(
                        SubscriptionPlan(
                            id = "premium_monthly",
                            name = "Premium Monthly",
                            price = billingManager.getFormattedPrice(PRODUCT_MONTHLY) ?: "$4.99",
                            period = "month",
                            features = listOf(
                                "16-day extended forecast",
                                "Unlimited AI insights",
                                "All activity forecasts",
                                "Advanced radar maps",
                                "No ads"
                            )
                        ),
                        SubscriptionPlan(
                            id = "premium_annual",
                            name = "Premium Annual",
                            price = billingManager.getFormattedPrice(PRODUCT_ANNUAL) ?: "$39.99",
                            period = "year",
                            features = listOf(
                                "Everything in Monthly",
                                "Save 33% ($20/year)",
                                "Priority support"
                            )
                        )
                    )
                )
            }
        }
    }

    /**
     * Start 7-day free trial
     */
    fun startTrial(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            val success = proFeaturesManager.startTrial()
            
            if (success) {
                _state.value = _state.value.copy(
                    isPremium = true,
                    currentTier = SubscriptionTier.TRIAL,
                    isLoading = false
                )
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Trial already used. Please subscribe to continue."
                )
            }
        }
    }

    /**
     * Purchase subscription via Google Play Billing
     */
    fun purchaseSubscription(activity: Activity, productId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            // Map UI product ID to billing product ID
            val billingProductId = when (productId) {
                "com.climaai.premium.monthly", PRODUCT_MONTHLY -> PRODUCT_MONTHLY
                "com.climaai.premium.annual", PRODUCT_ANNUAL -> PRODUCT_ANNUAL
                else -> PRODUCT_MONTHLY
            }
            
            // Find product details
            val products = billingManager.products.value
            val product = products.find { it.productId == billingProductId }
            
            if (product == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Product not available. Please try again later."
                )
                return@launch
            }
            
            // Launch purchase flow
            val success = billingManager.launchPurchase(activity, product)
            
            if (!success) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Could not start purchase. Please try again."
                )
            }
            // Purchase result handled by PurchasesUpdatedListener in BillingManager
        }
    }

    /**
     * Restore purchases from Google Play
     */
    fun restorePurchases(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            billingManager.restorePurchases()
            
            // Wait a moment for restore to complete
            kotlinx.coroutines.delay(1500)
            
            val isPro = billingManager.isPro()
            
            _state.value = _state.value.copy(
                isLoading = false,
                error = if (!isPro) "No active subscription found" else null
            )
            
            // Sync with local manager
            proFeaturesManager.updateFromBilling(billingManager)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        // Don't end connection here as it might be used by other screens
    }
}
