package com.climaai.app.billing

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Feature definitions with premium gating.
 * All premium features are centralized here for easy management.
 */
sealed class Feature(
    val id: String,
    val name: String,
    val description: String,
    val requiresPremium: Boolean
) {
    // Weather Features
    object ExtendedForecast : Feature(
        id = "extended_forecast",
        name = "16-Day Forecast",
        description = "Extended weather forecast up to 16 days",
        requiresPremium = true
    )
    
    object MinuteCast : Feature(
        id = "minute_cast",
        name = "Minute-by-Minute",
        description = "Precipitation forecast for the next 2 hours",
        requiresPremium = true
    )
    
    object HourlyExtended : Feature(
        id = "hourly_extended",
        name = "48-Hour Hourly",
        description = "Extended hourly forecast up to 48 hours",
        requiresPremium = true
    )
    
    // Widget Features
    object UnlimitedWidgets : Feature(
        id = "unlimited_widgets",
        name = "Unlimited Widgets",
        description = "Add as many widgets as you want",
        requiresPremium = true
    )
    
    object WidgetCustomization : Feature(
        id = "widget_customization",
        name = "Widget Customization",
        description = "Customize widget colors, style, and transparency",
        requiresPremium = true
    )
    
    object LargeWidgets : Feature(
        id = "large_widgets",
        name = "Large Widgets",
        description = "Access to medium, large, and extra-large widgets",
        requiresPremium = true
    )
    
    // Appearance Features
    object CustomThemes : Feature(
        id = "custom_themes",
        name = "Custom Themes",
        description = "Choose from multiple app themes and accent colors",
        requiresPremium = true
    )
    
    object IconPacks : Feature(
        id = "icon_packs",
        name = "Icon Packs",
        description = "Choose different weather icon styles",
        requiresPremium = true
    )
    
    object FontCustomization : Feature(
        id = "font_customization",
        name = "Font Size",
        description = "Adjust app font size for readability",
        requiresPremium = true
    )
    
    object OledTheme : Feature(
        id = "oled_theme",
        name = "OLED Dark Mode",
        description = "True black theme for OLED displays",
        requiresPremium = true
    )
    
    // Notification Features
    object RainAlerts : Feature(
        id = "rain_alerts",
        name = "Rain Alerts",
        description = "Get notified before it starts raining",
        requiresPremium = true
    )
    
    object PollenAlerts : Feature(
        id = "pollen_alerts",
        name = "Pollen Alerts",
        description = "Notifications when pollen levels are high",
        requiresPremium = true
    )
    
    object UvAlerts : Feature(
        id = "uv_alerts",
        name = "UV Alerts",
        description = "Notifications when UV levels are dangerous",
        requiresPremium = true
    )
    
    object CustomSchedule : Feature(
        id = "custom_schedule",
        name = "Custom Alert Schedule",
        description = "Set custom notification times",
        requiresPremium = true
    )
    
    // AI Features
    object UnlimitedAI : Feature(
        id = "unlimited_ai",
        name = "Unlimited AI Insights",
        description = "Get unlimited personalized weather insights",
        requiresPremium = true
    )
    
    // Extras
    object AdFree : Feature(
        id = "ad_free",
        name = "Ad-Free Experience",
        description = "Remove all advertisements",
        requiresPremium = true
    )
    
    object RadarAnimation : Feature(
        id = "radar_animation",
        name = "Radar Animation",
        description = "Animated weather radar playback",
        requiresPremium = true
    )
    
    object HealthInsights : Feature(
        id = "health_insights",
        name = "Health Insights",
        description = "Weather-based health recommendations",
        requiresPremium = true
    )
    
    // Free Features (for completeness)
    object BasicForecast : Feature(
        id = "basic_forecast",
        name = "7-Day Forecast",
        description = "Standard weather forecast",
        requiresPremium = false
    )
    
    object BasicHourly : Feature(
        id = "basic_hourly",
        name = "24-Hour Hourly",
        description = "Hourly forecast for next 24 hours",
        requiresPremium = false
    )
    
    object SmallWidget : Feature(
        id = "small_widget",
        name = "Small Widget",
        description = "Basic weather widget",
        requiresPremium = false
    )
    
    object DailySummary : Feature(
        id = "daily_summary",
        name = "Daily Summary",
        description = "Daily weather notification",
        requiresPremium = false
    )
    
    object SevereWeather : Feature(
        id = "severe_weather",
        name = "Severe Weather Alerts",
        description = "Critical weather warnings",
        requiresPremium = false
    )
    
    companion object {
        val allFeatures: List<Feature> = listOf(
            // Premium
            ExtendedForecast, MinuteCast, HourlyExtended,
            UnlimitedWidgets, WidgetCustomization, LargeWidgets,
            CustomThemes, IconPacks, FontCustomization, OledTheme,
            RainAlerts, PollenAlerts, UvAlerts, CustomSchedule,
            UnlimitedAI, AdFree, RadarAnimation, HealthInsights,
            // Free
            BasicForecast, BasicHourly, SmallWidget, DailySummary, SevereWeather
        )
        
        val premiumFeatures = allFeatures.filter { it.requiresPremium }
        val freeFeatures = allFeatures.filter { !it.requiresPremium }
    }
}

/**
 * Feature gate for checking if features are enabled.
 */
object FeatureGate {
    
    private var billingManager: BillingManager? = null
    
    /**
     * Initialize with BillingManager reference.
     */
    fun init(context: Context) {
        billingManager = BillingManager.getInstance(context)
    }
    
    /**
     * Check if a feature is enabled for the current user.
     */
    fun isEnabled(feature: Feature): Boolean {
        return if (feature.requiresPremium) {
            billingManager?.isPro() == true
        } else {
            true
        }
    }
    
    /**
     * Flow for observing premium status changes.
     */
    fun isPremiumFlow(): Flow<Boolean>? {
        return billingManager?.subscriptionState?.map { it.isPremium }
    }
    
    /**
     * Check premium status synchronously.
     */
    fun isPremium(): Boolean {
        return billingManager?.isPro() == true
    }
    
    /**
     * Get list of features available to current user.
     */
    fun getAvailableFeatures(): List<Feature> {
        val premium = isPremium()
        return Feature.allFeatures.filter { !it.requiresPremium || premium }
    }
    
    /**
     * Get list of locked features for upsell display.
     */
    fun getLockedFeatures(): List<Feature> {
        return if (isPremium()) emptyList() else Feature.premiumFeatures
    }
}

/**
 * Extension function for easy feature checking in Composables.
 */
fun Feature.isEnabled(): Boolean = FeatureGate.isEnabled(this)
