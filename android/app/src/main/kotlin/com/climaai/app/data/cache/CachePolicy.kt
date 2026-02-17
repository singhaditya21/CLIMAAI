package com.climaai.app.data.cache

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Cache policy constants and validation logic.
 * Controls API cost by enforcing refresh cooldowns and cache TTLs.
 */
object CachePolicy {
    
    // ==========================================================================
    // Cache TTLs (Time-to-Live)
    // ==========================================================================
    
    /** Current weather cache validity */
    val CURRENT_WEATHER_TTL = 10.minutes
    
    /** Hourly forecast cache validity */
    val HOURLY_FORECAST_TTL = 30.minutes
    
    /** Daily forecast cache validity */
    val DAILY_FORECAST_TTL = 60.minutes
    
    /** AI insights cache validity (expensive API) */
    val AI_INSIGHTS_TTL = 30.minutes
    
    // ==========================================================================
    // Refresh Rate Limits
    // ==========================================================================
    
    /** Minimum time between user-initiated refreshes */
    val MIN_REFRESH_COOLDOWN = 30.seconds
    
    /** Maximum refreshes allowed per hour */
    const val MAX_REFRESHES_PER_HOUR = 10
    
    /** Maximum refreshes allowed per day (cost control) */
    const val MAX_REFRESHES_PER_DAY = 48
    
    /** Backoff multiplier on consecutive failures */
    const val BACKOFF_MULTIPLIER = 2.0
    
    /** Maximum backoff time */
    val MAX_BACKOFF = 5.minutes
    
    // ==========================================================================
    // Free vs Pro Limits
    // ==========================================================================
    
    /** Pro users get more frequent refreshes */
    val PRO_REFRESH_COOLDOWN = 15.seconds
    
    /** Pro users get higher daily cap */
    const val PRO_MAX_REFRESHES_PER_DAY = 96
    
    // ==========================================================================
    // Validation Functions
    // ==========================================================================
    
    /**
     * Check if cached data is still valid based on timestamp and TTL.
     * @param cachedAt Timestamp when data was cached (millis)
     * @param ttlMillis TTL in milliseconds
     * @return true if cache is still valid
     */
    fun isCacheValid(cachedAt: Long, ttlMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        return (now - cachedAt) < ttlMillis
    }
    
    /**
     * Check if current weather cache is valid.
     */
    fun isCurrentWeatherValid(cachedAt: Long): Boolean {
        return isCacheValid(cachedAt, CURRENT_WEATHER_TTL.inWholeMilliseconds)
    }
    
    /**
     * Check if user can initiate a refresh (cooldown enforcement).
     * @param lastRefreshAt Timestamp of last refresh (millis)
     * @param isPro Whether user has Pro subscription
     * @return true if refresh is allowed
     */
    fun canRefresh(lastRefreshAt: Long, isPro: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        val cooldown = if (isPro) PRO_REFRESH_COOLDOWN else MIN_REFRESH_COOLDOWN
        return (now - lastRefreshAt) >= cooldown.inWholeMilliseconds
    }
    
    /**
     * Get remaining cooldown time in seconds.
     * @return Seconds remaining, or 0 if no cooldown
     */
    fun getRemainingCooldown(lastRefreshAt: Long, isPro: Boolean = false): Int {
        val now = System.currentTimeMillis()
        val cooldown = if (isPro) PRO_REFRESH_COOLDOWN else MIN_REFRESH_COOLDOWN
        val elapsed = now - lastRefreshAt
        val remaining = cooldown.inWholeMilliseconds - elapsed
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }
    
    /**
     * Calculate backoff delay after consecutive failures.
     * @param consecutiveFailures Number of consecutive failures
     * @return Backoff delay in milliseconds
     */
    fun calculateBackoff(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0
        
        val baseDelay = MIN_REFRESH_COOLDOWN.inWholeMilliseconds
        val backoff = (baseDelay * Math.pow(BACKOFF_MULTIPLIER, consecutiveFailures.toDouble())).toLong()
        return minOf(backoff, MAX_BACKOFF.inWholeMilliseconds)
    }
    
    /**
     * Check if hourly refresh cap has been exceeded.
     * @param refreshesThisHour Number of refreshes in current hour
     * @return true if cap exceeded
     */
    fun isHourlyCapExceeded(refreshesThisHour: Int): Boolean {
        return refreshesThisHour >= MAX_REFRESHES_PER_HOUR
    }
    
    /**
     * Check if daily refresh cap has been exceeded.
     * @param refreshesToday Number of refreshes today
     * @param isPro Whether user has Pro subscription
     * @return true if cap exceeded
     */
    fun isDailyCapExceeded(refreshesToday: Int, isPro: Boolean = false): Boolean {
        val cap = if (isPro) PRO_MAX_REFRESHES_PER_DAY else MAX_REFRESHES_PER_DAY
        return refreshesToday >= cap
    }
    
    /**
     * Format "last updated" time for UI display.
     * @param timestamp Timestamp of last update (millis)
     * @return Human-readable string like "Just now", "5 min ago", etc.
     */
    fun formatLastUpdated(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val elapsed = now - timestamp
        
        return when {
            elapsed < 60_000 -> "Just now"
            elapsed < 3600_000 -> {
                val minutes = (elapsed / 60_000).toInt()
                "$minutes min ago"
            }
            elapsed < 86400_000 -> {
                val hours = (elapsed / 3600_000).toInt()
                if (hours == 1) "1 hour ago" else "$hours hours ago"
            }
            else -> {
                val days = (elapsed / 86400_000).toInt()
                if (days == 1) "1 day ago" else "$days days ago"
            }
        }
    }
}
