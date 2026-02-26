package com.climaai.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages AdMob ads in the app.
 * 
 * Note: This is a mock implementation. In production:
 * 1. Add Google Mobile Ads SDK dependency
 * 2. Add AdMob App ID to AndroidManifest.xml
 * 3. Replace mock views with actual AdView implementations
 */
object AdManager {
    
    private const val TAG = "AdManager"
    
    // Test ad unit IDs (replace with real IDs in production)
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111" // Test banner
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // Test interstitial
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _adsEnabled = MutableStateFlow(true)
    val adsEnabled: StateFlow<Boolean> = _adsEnabled.asStateFlow()
    
    /**
     * Initialize the Mobile Ads SDK.
     * Call this in Application.onCreate()
     */
    fun initialize(context: Context) {
        // In production:
        // MobileAds.initialize(context) { initializationStatus ->
        //     _isInitialized.value = true
        //     Log.d(TAG, "AdMob initialized")
        // }
        
        // Mock initialization
        _isInitialized.value = true
        Log.d(TAG, "AdManager initialized (mock)")
    }
    
    /**
     * Disable ads for premium users.
     */
    fun setAdsEnabled(enabled: Boolean) {
        _adsEnabled.value = enabled
        Log.d(TAG, "Ads enabled: $enabled")
    }
    
    /**
     * Load an interstitial ad.
     */
    fun loadInterstitial(context: Context, onLoaded: () -> Unit, onFailed: (String) -> Unit) {
        // In production:
        // InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, AdRequest.Builder().build(),
        //     object : InterstitialAdLoadCallback() {
        //         override fun onAdLoaded(ad: InterstitialAd) { ... }
        //         override fun onAdFailedToLoad(error: LoadAdError) { ... }
        //     }
        // )
        
        // Mock: always succeed
        onLoaded()
    }
    
    /**
     * Show the interstitial ad.
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        // In production:
        // interstitialAd?.show(activity)
        // interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
        //     override fun onAdDismissedFullScreenContent() { onDismissed() }
        // }
        
        // Mock: immediately dismiss
        onDismissed()
    }
}

/**
 * Composable banner ad view.
 * Shows a banner ad for non-premium users.
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    isPremium: Boolean = false
) {
    val context = LocalContext.current
    val adsEnabled by AdManager.adsEnabled.collectAsState()
    
    // Don't show ads for premium users
    if (isPremium || !adsEnabled) {
        return
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        AndroidView(
            factory = { ctx ->
                // In production, replace with:
                // AdView(ctx).apply {
                //     setAdSize(AdSize.BANNER)
                //     adUnitId = AdManager.BANNER_AD_UNIT_ID
                //     loadAd(AdRequest.Builder().build())
                // }
                
                // Mock banner view
                FrameLayout(ctx).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#2A2A2A"))
                    addView(android.widget.TextView(ctx).apply {
                        text = "Ad"
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 12f
                        gravity = android.view.Gravity.CENTER
                    }, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    ))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Composable for native ad view (more integrated look).
 */
@Composable
fun NativeAdView(
    modifier: Modifier = Modifier,
    isPremium: Boolean = false
) {
    val adsEnabled by AdManager.adsEnabled.collectAsState()
    
    if (isPremium || !adsEnabled) {
        return
    }
    
    // Native ad implementation would go here
    // For now, use banner as placeholder
    BannerAdView(modifier = modifier, isPremium = isPremium)
}
