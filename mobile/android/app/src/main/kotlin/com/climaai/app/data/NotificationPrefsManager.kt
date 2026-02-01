package com.climaai.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationPrefsStore by preferencesDataStore("notification_prefs")

/**
 * Manages notification preferences using DataStore.
 */
class NotificationPrefsManager(private val context: Context) {
    
    companion object {
        // Preference keys
        private val DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")
        private val DAILY_SUMMARY_HOUR = intPreferencesKey("daily_summary_hour")
        private val RAIN_ALERTS_ENABLED = booleanPreferencesKey("rain_alerts_enabled")
        private val SEVERE_WEATHER_ENABLED = booleanPreferencesKey("severe_weather_enabled")
        private val UV_ALERTS_ENABLED = booleanPreferencesKey("uv_alerts_enabled")
        private val POLLEN_ALERTS_ENABLED = booleanPreferencesKey("pollen_alerts_enabled")
        
        // Rate limiting keys
        private val LAST_RAIN_ALERT = longPreferencesKey("last_rain_alert_at")
        private val LAST_SEVERE_ALERT = longPreferencesKey("last_severe_alert_at")
        private val LAST_UV_ALERT = longPreferencesKey("last_uv_alert_at")
        private val LAST_POLLEN_ALERT = longPreferencesKey("last_pollen_alert_at")
        
        // Rate limit: minimum 2 hours between same alert types
        const val ALERT_COOLDOWN_MS = 2 * 60 * 60 * 1000L // 2 hours
    }
    
    // =========================================================================
    // Preference Flows (reactive)
    // =========================================================================
    
    val dailySummaryEnabled: Flow<Boolean> = context.notificationPrefsStore.data.map { prefs ->
        prefs[DAILY_SUMMARY_ENABLED] ?: true // Default enabled
    }
    
    val dailySummaryHour: Flow<Int> = context.notificationPrefsStore.data.map { prefs ->
        prefs[DAILY_SUMMARY_HOUR] ?: 7 // Default 7 AM
    }
    
    val rainAlertsEnabled: Flow<Boolean> = context.notificationPrefsStore.data.map { prefs ->
        prefs[RAIN_ALERTS_ENABLED] ?: true
    }
    
    val severeWeatherEnabled: Flow<Boolean> = context.notificationPrefsStore.data.map { prefs ->
        prefs[SEVERE_WEATHER_ENABLED] ?: true
    }
    
    val uvAlertsEnabled: Flow<Boolean> = context.notificationPrefsStore.data.map { prefs ->
        prefs[UV_ALERTS_ENABLED] ?: false // Default disabled
    }
    
    val pollenAlertsEnabled: Flow<Boolean> = context.notificationPrefsStore.data.map { prefs ->
        prefs[POLLEN_ALERTS_ENABLED] ?: false
    }
    
    // =========================================================================
    // Preference Setters
    // =========================================================================
    
    suspend fun setDailySummaryEnabled(enabled: Boolean) {
        context.notificationPrefsStore.edit { prefs ->
            prefs[DAILY_SUMMARY_ENABLED] = enabled
        }
    }
    
    suspend fun setDailySummaryHour(hour: Int) {
        context.notificationPrefsStore.edit { prefs ->
            prefs[DAILY_SUMMARY_HOUR] = hour.coerceIn(0, 23)
        }
    }
    
    suspend fun setRainAlertsEnabled(enabled: Boolean) {
        context.notificationPrefsStore.edit { prefs ->
            prefs[RAIN_ALERTS_ENABLED] = enabled
        }
    }
    
    suspend fun setSevereWeatherEnabled(enabled: Boolean) {
        context.notificationPrefsStore.edit { prefs ->
            prefs[SEVERE_WEATHER_ENABLED] = enabled
        }
    }
    
    suspend fun setUVAlertsEnabled(enabled: Boolean) {
        context.notificationPrefsStore.edit { prefs ->
            prefs[UV_ALERTS_ENABLED] = enabled
        }
    }
    
    suspend fun setPollenAlertsEnabled(enabled: Boolean) {
        context.notificationPrefsStore.edit { prefs ->
            prefs[POLLEN_ALERTS_ENABLED] = enabled
        }
    }
    
    // =========================================================================
    // Rate Limiting
    // =========================================================================
    
    suspend fun canSendRainAlert(): Boolean {
        return canSendAlert(LAST_RAIN_ALERT)
    }
    
    suspend fun canSendSevereAlert(): Boolean {
        return canSendAlert(LAST_SEVERE_ALERT)
    }
    
    suspend fun canSendUVAlert(): Boolean {
        return canSendAlert(LAST_UV_ALERT)
    }
    
    suspend fun canSendPollenAlert(): Boolean {
        return canSendAlert(LAST_POLLEN_ALERT)
    }
    
    private suspend fun canSendAlert(key: androidx.datastore.preferences.core.Preferences.Key<Long>): Boolean {
        val lastSent = context.notificationPrefsStore.data.map { prefs ->
            prefs[key] ?: 0L
        }.first()
        
        return System.currentTimeMillis() - lastSent >= ALERT_COOLDOWN_MS
    }
    
    suspend fun recordRainAlert() {
        recordAlert(LAST_RAIN_ALERT)
    }
    
    suspend fun recordSevereAlert() {
        recordAlert(LAST_SEVERE_ALERT)
    }
    
    suspend fun recordUVAlert() {
        recordAlert(LAST_UV_ALERT)
    }
    
    suspend fun recordPollenAlert() {
        recordAlert(LAST_POLLEN_ALERT)
    }
    
    private suspend fun recordAlert(key: androidx.datastore.preferences.core.Preferences.Key<Long>) {
        context.notificationPrefsStore.edit { prefs ->
            prefs[key] = System.currentTimeMillis()
        }
    }
    
    // =========================================================================
    // Snapshot for synchronous access
    // =========================================================================
    
    suspend fun getPreferencesSnapshot(): NotificationPreferences {
        val prefs = context.notificationPrefsStore.data.first()
        return NotificationPreferences(
            dailySummaryEnabled = prefs[DAILY_SUMMARY_ENABLED] ?: true,
            dailySummaryHour = prefs[DAILY_SUMMARY_HOUR] ?: 7,
            rainAlertsEnabled = prefs[RAIN_ALERTS_ENABLED] ?: true,
            severeWeatherEnabled = prefs[SEVERE_WEATHER_ENABLED] ?: true,
            uvAlertsEnabled = prefs[UV_ALERTS_ENABLED] ?: false,
            pollenAlertsEnabled = prefs[POLLEN_ALERTS_ENABLED] ?: false
        )
    }
}

/**
 * Snapshot of notification preferences for UI state.
 */
data class NotificationPreferences(
    val dailySummaryEnabled: Boolean,
    val dailySummaryHour: Int,
    val rainAlertsEnabled: Boolean,
    val severeWeatherEnabled: Boolean,
    val uvAlertsEnabled: Boolean,
    val pollenAlertsEnabled: Boolean
)
