package com.climaai.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.climaai.app.billing.BillingManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.proStatusStore by preferencesDataStore("pro_status")

/**
 * Manages Pro (premium) feature flags.
 * Combines local cache with billing status for fast access.
 */
class ProFeaturesManager(private val context: Context) {
    
    companion object {
        private val IS_PRO = booleanPreferencesKey("is_pro")
        private val PLAN_NAME = stringPreferencesKey("plan_name")
        private val EXPIRY_TIME = longPreferencesKey("expiry_time")
        private val TRIAL_END_TIME = longPreferencesKey("trial_end_time")
        
        // Trial duration: 7 days
        const val TRIAL_DURATION_MS = 7L * 24 * 60 * 60 * 1000
    }
    
    // =========================================================================
    // Feature Flags
    // =========================================================================
    
    /**
     * Check if user has Pro access (subscription or trial).
     */
    val isPro: Flow<Boolean> = context.proStatusStore.data.map { prefs ->
        val isPro = prefs[IS_PRO] ?: false
        val expiryTime = prefs[EXPIRY_TIME] ?: 0L
        val trialEndTime = prefs[TRIAL_END_TIME] ?: 0L
        
        val now = System.currentTimeMillis()
        
        // Pro if: active subscription OR active trial
        isPro && (expiryTime == 0L || expiryTime > now) || 
            (trialEndTime > 0 && trialEndTime > now)
    }
    
    /**
     * Check if user is on trial.
     */
    val isOnTrial: Flow<Boolean> = context.proStatusStore.data.map { prefs ->
        val isPro = prefs[IS_PRO] ?: false
        val trialEndTime = prefs[TRIAL_END_TIME] ?: 0L
        
        !isPro && trialEndTime > System.currentTimeMillis()
    }
    
    /**
     * Get current plan name.
     */
    val planName: Flow<String?> = context.proStatusStore.data.map { prefs ->
        prefs[PLAN_NAME]
    }
    
    /**
     * Days remaining in trial.
     */
    val trialDaysRemaining: Flow<Int> = context.proStatusStore.data.map { prefs ->
        val trialEndTime = prefs[TRIAL_END_TIME] ?: 0L
        val remaining = trialEndTime - System.currentTimeMillis()
        if (remaining > 0) {
            (remaining / (24 * 60 * 60 * 1000)).toInt() + 1
        } else {
            0
        }
    }
    
    // =========================================================================
    // Premium Feature Checks
    // =========================================================================
    
    /**
     * Extended forecast (14 days vs 7 days).
     */
    suspend fun hasExtendedForecast(): Boolean = isPro.first()
    
    /**
     * AI insights with detailed recommendations.
     */
    suspend fun hasAdvancedAIInsights(): Boolean = isPro.first()
    
    /**
     * Radar maps.
     */
    suspend fun hasRadarMaps(): Boolean = isPro.first()
    
    /**
     * Pollen and air quality details.
     */
    suspend fun hasHealthFeatures(): Boolean = isPro.first()
    
    /**
     * Ad-free experience.
     */
    suspend fun isAdFree(): Boolean = isPro.first()
    
    /**
     * Faster refresh cooldowns.
     */
    suspend fun hasFasterRefresh(): Boolean = isPro.first()
    
    /**
     * Widget customization.
     */
    suspend fun hasWidgetCustomization(): Boolean = isPro.first()
    
    // =========================================================================
    // Status Updates
    // =========================================================================
    
    /**
     * Update Pro status from BillingManager.
     */
    suspend fun updateFromBilling(billingManager: BillingManager) {
        val status = billingManager.subscriptionStatus.value
        
        context.proStatusStore.edit { prefs ->
            prefs[IS_PRO] = status.isPro
            prefs[PLAN_NAME] = status.planName
            prefs[EXPIRY_TIME] = status.expiryTime
        }
    }
    
    /**
     * Start a free trial.
     */
    suspend fun startTrial(): Boolean {
        val currentTrialEnd = context.proStatusStore.data.map { prefs ->
            prefs[TRIAL_END_TIME] ?: 0L
        }.first()
        
        // Only allow trial once
        if (currentTrialEnd > 0) {
            return false
        }
        
        context.proStatusStore.edit { prefs ->
            prefs[TRIAL_END_TIME] = System.currentTimeMillis() + TRIAL_DURATION_MS
        }
        
        return true
    }
    
    /**
     * Clear trial status (for testing).
     */
    suspend fun clearTrial() {
        context.proStatusStore.edit { prefs ->
            prefs.remove(TRIAL_END_TIME)
        }
    }
    
    /**
     * Sync status (call on app start).
     */
    suspend fun syncStatus(billingManager: BillingManager) {
        updateFromBilling(billingManager)
    }
    
    // =========================================================================
    // Snapshot for synchronous access
    // =========================================================================
    
    suspend fun getSnapshot(): ProStatus {
        val prefs = context.proStatusStore.data.first()
        val isPro = prefs[IS_PRO] ?: false
        val planName = prefs[PLAN_NAME]
        val expiryTime = prefs[EXPIRY_TIME] ?: 0L
        val trialEndTime = prefs[TRIAL_END_TIME] ?: 0L
        val now = System.currentTimeMillis()
        
        return ProStatus(
            isPro = isPro && (expiryTime == 0L || expiryTime > now),
            isOnTrial = !isPro && trialEndTime > now,
            planName = planName,
            trialDaysRemaining = if (trialEndTime > now) {
                ((trialEndTime - now) / (24 * 60 * 60 * 1000)).toInt() + 1
            } else 0
        )
    }
}

/**
 * Snapshot of Pro status for UI.
 */
data class ProStatus(
    val isPro: Boolean,
    val isOnTrial: Boolean,
    val planName: String?,
    val trialDaysRemaining: Int
) {
    /**
     * Has any Pro access (subscription or trial).
     */
    val hasProAccess: Boolean get() = isPro || isOnTrial
    
    /**
     * Display text for subscription status.
     */
    val statusText: String get() = when {
        isPro -> planName ?: "Pro"
        isOnTrial -> "Trial ($trialDaysRemaining days left)"
        else -> "Free"
    }
}
