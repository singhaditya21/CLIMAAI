package com.climaai.app.data.cache

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

private val Context.refreshDataStore by preferencesDataStore("refresh_tracker")

/**
 * Tracks refresh counts and enforces rate limits.
 * Uses DataStore to persist counts across app restarts.
 */
class RefreshTracker(private val context: Context) {
    
    companion object {
        private val LAST_REFRESH_KEY = longPreferencesKey("last_refresh_at")
        private val HOURLY_COUNT_KEY = intPreferencesKey("hourly_refresh_count")
        private val HOURLY_RESET_KEY = longPreferencesKey("hourly_reset_at")
        private val DAILY_COUNT_KEY = intPreferencesKey("daily_refresh_count")
        private val DAILY_RESET_KEY = longPreferencesKey("daily_reset_at")
        private val CONSECUTIVE_FAILURES_KEY = intPreferencesKey("consecutive_failures")
        
        // Per-API type counters
        private val TOTAL_API_CALLS_KEY = intPreferencesKey("total_api_calls")
        private val WEATHER_CALLS_KEY = intPreferencesKey("weather_api_calls_today")
        private val AQI_CALLS_KEY = intPreferencesKey("aqi_api_calls_today")
        private val GEOCODING_CALLS_KEY = intPreferencesKey("geocoding_api_calls_today")
        private val API_CALLS_RESET_KEY = longPreferencesKey("api_calls_reset_at")
    }
    
    /**
     * Get the timestamp of the last refresh.
     */
    suspend fun getLastRefreshTime(): Long {
        return context.refreshDataStore.data.map { prefs ->
            prefs[LAST_REFRESH_KEY] ?: 0L
        }.first()
    }
    
    /**
     * Record a successful refresh.
     */
    suspend fun recordRefresh() {
        val now = System.currentTimeMillis()
        
        context.refreshDataStore.edit { prefs ->
            prefs[LAST_REFRESH_KEY] = now
            prefs[CONSECUTIVE_FAILURES_KEY] = 0
            
            // Update hourly count
            val hourlyResetAt = prefs[HOURLY_RESET_KEY] ?: 0L
            if (now - hourlyResetAt > 3600_000) {
                // Reset hourly counter
                prefs[HOURLY_COUNT_KEY] = 1
                prefs[HOURLY_RESET_KEY] = now
            } else {
                val currentCount = prefs[HOURLY_COUNT_KEY] ?: 0
                prefs[HOURLY_COUNT_KEY] = currentCount + 1
            }
            
            // Update daily count
            val dailyResetAt = prefs[DAILY_RESET_KEY] ?: 0L
            if (!isSameDay(dailyResetAt, now)) {
                // Reset daily counter
                prefs[DAILY_COUNT_KEY] = 1
                prefs[DAILY_RESET_KEY] = now
            } else {
                val currentCount = prefs[DAILY_COUNT_KEY] ?: 0
                prefs[DAILY_COUNT_KEY] = currentCount + 1
            }
        }
    }
    
    /**
     * Record an API call by type (weather, aqi, geocoding).
     * Increments both per-type daily counter and total lifetime counter.
     */
    suspend fun recordApiCall(type: ApiCallType) {
        val now = System.currentTimeMillis()
        
        context.refreshDataStore.edit { prefs ->
            // Reset daily API counters if new day
            val apiResetAt = prefs[API_CALLS_RESET_KEY] ?: 0L
            if (!isSameDay(apiResetAt, now)) {
                prefs[WEATHER_CALLS_KEY] = 0
                prefs[AQI_CALLS_KEY] = 0
                prefs[GEOCODING_CALLS_KEY] = 0
                prefs[API_CALLS_RESET_KEY] = now
            }
            
            // Increment per-type counter
            val key = when (type) {
                ApiCallType.WEATHER -> WEATHER_CALLS_KEY
                ApiCallType.AIR_QUALITY -> AQI_CALLS_KEY
                ApiCallType.GEOCODING -> GEOCODING_CALLS_KEY
            }
            val current = prefs[key] ?: 0
            prefs[key] = current + 1
            
            // Increment total lifetime counter
            val total = prefs[TOTAL_API_CALLS_KEY] ?: 0
            prefs[TOTAL_API_CALLS_KEY] = total + 1
        }
    }
    
    /**
     * Record a failed refresh attempt.
     */
    suspend fun recordFailure() {
        context.refreshDataStore.edit { prefs ->
            val current = prefs[CONSECUTIVE_FAILURES_KEY] ?: 0
            prefs[CONSECUTIVE_FAILURES_KEY] = current + 1
        }
    }
    
    /**
     * Get current hourly refresh count.
     */
    suspend fun getHourlyCount(): Int {
        val now = System.currentTimeMillis()
        return context.refreshDataStore.data.map { prefs ->
            val resetAt = prefs[HOURLY_RESET_KEY] ?: 0L
            if (now - resetAt > 3600_000) {
                0  // Hour has passed, count is effectively 0
            } else {
                prefs[HOURLY_COUNT_KEY] ?: 0
            }
        }.first()
    }
    
    /**
     * Get current daily refresh count.
     */
    suspend fun getDailyCount(): Int {
        val now = System.currentTimeMillis()
        return context.refreshDataStore.data.map { prefs ->
            val resetAt = prefs[DAILY_RESET_KEY] ?: 0L
            if (!isSameDay(resetAt, now)) {
                0  // New day, count is effectively 0
            } else {
                prefs[DAILY_COUNT_KEY] ?: 0
            }
        }.first()
    }
    
    /**
     * Get consecutive failure count for backoff calculation.
     */
    suspend fun getConsecutiveFailures(): Int {
        return context.refreshDataStore.data.map { prefs ->
            prefs[CONSECUTIVE_FAILURES_KEY] ?: 0
        }.first()
    }
    
    /**
     * Check if refresh is allowed based on all rate limits.
     * @param isPro Pro users get higher limits
     * @return RefreshStatus with allowed flag and reason
     */
    suspend fun checkRefreshAllowed(isPro: Boolean = false): RefreshStatus {
        val lastRefresh = getLastRefreshTime()
        val hourlyCount = getHourlyCount()
        val dailyCount = getDailyCount()
        val failures = getConsecutiveFailures()
        
        // Check cooldown
        if (!CachePolicy.canRefresh(lastRefresh, isPro)) {
            val remaining = CachePolicy.getRemainingCooldown(lastRefresh, isPro)
            return RefreshStatus(
                allowed = false,
                reason = "Please wait ${remaining}s before refreshing",
                cooldownSeconds = remaining
            )
        }
        
        // Check backoff from failures
        if (failures > 0) {
            val backoffMs = CachePolicy.calculateBackoff(failures)
            val elapsed = System.currentTimeMillis() - lastRefresh
            if (elapsed < backoffMs) {
                val remainingSeconds = ((backoffMs - elapsed) / 1000).toInt()
                return RefreshStatus(
                    allowed = false,
                    reason = "Network issues. Retry in ${remainingSeconds}s",
                    cooldownSeconds = remainingSeconds
                )
            }
        }
        
        // Check hourly cap
        if (CachePolicy.isHourlyCapExceeded(hourlyCount)) {
            return RefreshStatus(
                allowed = false,
                reason = "Hourly refresh limit reached. Try again later.",
                cooldownSeconds = 0
            )
        }
        
        // Check daily cap
        if (CachePolicy.isDailyCapExceeded(dailyCount, isPro)) {
            return RefreshStatus(
                allowed = false,
                reason = if (isPro) "Daily limit reached" else "Daily limit reached. Upgrade to Pro for more.",
                cooldownSeconds = 0
            )
        }
        
        return RefreshStatus(allowed = true, reason = null, cooldownSeconds = 0)
    }
    
    /**
     * Get refresh statistics for debugging/settings UI.
     */
    suspend fun getStats(): RefreshStats {
        return RefreshStats(
            lastRefreshAt = getLastRefreshTime(),
            hourlyCount = getHourlyCount(),
            dailyCount = getDailyCount(),
            consecutiveFailures = getConsecutiveFailures()
        )
    }
    
    /**
     * Get comprehensive API usage summary for Settings UI.
     */
    suspend fun getUsageSummary(isPro: Boolean = false): UsageSummary {
        val now = System.currentTimeMillis()
        val data = context.refreshDataStore.data.first()
        
        // Check if daily API counters need reset
        val apiResetAt = data[API_CALLS_RESET_KEY] ?: 0L
        val isNewDay = !isSameDay(apiResetAt, now)
        
        return UsageSummary(
            dailyRefreshes = getDailyCount(),
            dailyLimit = if (isPro) CachePolicy.PRO_MAX_REFRESHES_PER_DAY else CachePolicy.MAX_REFRESHES_PER_DAY,
            hourlyRefreshes = getHourlyCount(),
            hourlyLimit = CachePolicy.MAX_REFRESHES_PER_HOUR,
            weatherCallsToday = if (isNewDay) 0 else (data[WEATHER_CALLS_KEY] ?: 0),
            aqiCallsToday = if (isNewDay) 0 else (data[AQI_CALLS_KEY] ?: 0),
            geocodingCallsToday = if (isNewDay) 0 else (data[GEOCODING_CALLS_KEY] ?: 0),
            totalLifetimeCalls = data[TOTAL_API_CALLS_KEY] ?: 0
        )
    }
    
    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}

/**
 * API call type for per-type tracking.
 */
enum class ApiCallType {
    WEATHER,
    AIR_QUALITY,
    GEOCODING
}

/**
 * Result of refresh check.
 */
data class RefreshStatus(
    val allowed: Boolean,
    val reason: String?,
    val cooldownSeconds: Int
)

/**
 * Refresh statistics for UI/debugging.
 */
data class RefreshStats(
    val lastRefreshAt: Long,
    val hourlyCount: Int,
    val dailyCount: Int,
    val consecutiveFailures: Int
)

/**
 * Comprehensive API usage summary for Settings UI.
 */
data class UsageSummary(
    val dailyRefreshes: Int,
    val dailyLimit: Int,
    val hourlyRefreshes: Int,
    val hourlyLimit: Int,
    val weatherCallsToday: Int,
    val aqiCallsToday: Int,
    val geocodingCallsToday: Int,
    val totalLifetimeCalls: Int
) {
    val totalCallsToday: Int get() = weatherCallsToday + aqiCallsToday + geocodingCallsToday
    val dailyUsagePercent: Float get() = if (dailyLimit > 0) dailyRefreshes.toFloat() / dailyLimit else 0f
    val hourlyUsagePercent: Float get() = if (hourlyLimit > 0) hourlyRefreshes.toFloat() / hourlyLimit else 0f
}
