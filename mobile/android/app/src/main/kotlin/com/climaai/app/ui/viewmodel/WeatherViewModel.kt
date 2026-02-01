package com.climaai.app.ui.viewmodel

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.climaai.app.data.*
import com.climaai.app.data.cache.CachePolicy
import com.climaai.app.data.cache.RefreshStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WeatherRepository(application)
    
    // Weather state
    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val weatherState = _weatherState.asStateFlow()
    
    // Expose for navigation compatibility
    val state = weatherState
    
    // AI Insights state
    private val _aiInsightsState = MutableStateFlow<AIInsightsState>(AIInsightsState.Loading)
    val aiInsightsState = _aiInsightsState.asStateFlow()
    
    // Location state
    private val _location = MutableStateFlow<LocationData?>(null)
    val location = _location.asStateFlow()
    
    // Subscription state
    private val _isPremium = MutableStateFlow(false)
    val isPremium = _isPremium.asStateFlow()
    
    private val _subscriptionStatus = MutableStateFlow<SubscriptionStatus?>(null)
    val subscriptionStatus = _subscriptionStatus.asStateFlow()
    
    // UI state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    
    // =========================================================================
    // NEW: Cache & Refresh State
    // =========================================================================
    
    /** Timestamp of last successful data update */
    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated = _lastUpdated.asStateFlow()
    
    /** Whether data is from cache */
    private val _isFromCache = MutableStateFlow(false)
    val isFromCache = _isFromCache.asStateFlow()
    
    /** Whether refresh is currently allowed (cooldown check) */
    private val _canRefresh = MutableStateFlow(true)
    val canRefresh = _canRefresh.asStateFlow()
    
    /** Rate limit message to show user */
    private val _rateLimitMessage = MutableStateFlow<String?>(null)
    val rateLimitMessage = _rateLimitMessage.asStateFlow()
    
    /** Human-readable "last updated" text */
    private val _lastUpdatedText = MutableStateFlow<String?>(null)
    val lastUpdatedText = _lastUpdatedText.asStateFlow()
    
    init {
        checkSubscriptionStatus()
        // Update lastUpdatedText periodically
        viewModelScope.launch {
            while (true) {
                updateLastUpdatedText()
                kotlinx.coroutines.delay(30_000) // Update every 30s
            }
        }
    }
    
    fun setLocation(lat: Double, lon: Double, name: String? = null) {
        val locationName = name ?: getLocationName(lat, lon)
        _location.value = LocationData(lat, lon, locationName)
        fetchWeather(lat, lon)
    }
    
    /**
     * Fetch weather with caching (normal flow).
     */
    fun fetchWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            _isRefreshing.value = true
            
            val result = repository.getWeather(
                lat = lat,
                lon = lon,
                forceRefresh = false,
                isPro = _isPremium.value
            )
            
            handleWeatherResult(result, lat, lon)
            _isRefreshing.value = false
            updateRefreshState()
        }
    }
    
    /**
     * Force refresh (user-initiated pull-to-refresh).
     * Respects rate limits and shows feedback.
     */
    fun forceRefresh() {
        val loc = _location.value ?: return
        
        viewModelScope.launch {
            // Check if refresh is allowed
            val status = repository.canRefresh(_isPremium.value)
            if (!status.allowed) {
                _rateLimitMessage.value = status.reason
                _canRefresh.value = false
                return@launch
            }
            
            _isRefreshing.value = true
            _rateLimitMessage.value = null
            
            val result = repository.getWeather(
                lat = loc.latitude,
                lon = loc.longitude,
                forceRefresh = true,
                isPro = _isPremium.value
            )
            
            handleWeatherResult(result, loc.latitude, loc.longitude)
            _isRefreshing.value = false
            updateRefreshState()
        }
    }
    
    private suspend fun handleWeatherResult(result: WeatherResult, lat: Double, lon: Double) {
        when (result) {
            is WeatherResult.Success -> {
                _weatherState.value = WeatherState.Success(result.data)
                _lastUpdated.value = result.cachedAt
                _isFromCache.value = result.fromCache
                updateLastUpdatedText()
                
                // Show rate limit message if applicable
                if (result.rateLimited) {
                    _rateLimitMessage.value = result.rateLimitMessage
                }
                
                // Show network error but with cached data
                if (result.networkError) {
                    _rateLimitMessage.value = "Showing cached data. ${result.networkErrorMessage}"
                }
                
                // Fetch AI insights (also cached)
                fetchAIInsights(lat, lon)
            }
            is WeatherResult.Error -> {
                _weatherState.value = WeatherState.Error(result.message)
                _rateLimitMessage.value = result.message
            }
        }
    }
    
    private suspend fun updateRefreshState() {
        val status = repository.canRefresh(_isPremium.value)
        _canRefresh.value = status.allowed
    }
    
    private fun updateLastUpdatedText() {
        val timestamp = _lastUpdated.value
        _lastUpdatedText.value = if (timestamp != null && timestamp > 0) {
            CachePolicy.formatLastUpdated(timestamp)
        } else {
            null
        }
    }
    
    /**
     * Clear rate limit message (after user dismisses).
     */
    fun clearRateLimitMessage() {
        _rateLimitMessage.value = null
    }
    
    fun fetchAIInsights(lat: Double, lon: Double) {
        viewModelScope.launch {
            _aiInsightsState.value = AIInsightsState.Loading
            
            repository.getAIInsights(lat, lon).fold(
                onSuccess = { insights ->
                    _aiInsightsState.value = AIInsightsState.Success(insights)
                },
                onFailure = { error ->
                    _aiInsightsState.value = AIInsightsState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
    
    fun refresh() {
        forceRefresh()
    }
    
    private fun checkSubscriptionStatus() {
        viewModelScope.launch {
            repository.getSubscriptionStatus().fold(
                onSuccess = { status ->
                    _subscriptionStatus.value = status
                    _isPremium.value = status.isPremium
                },
                onFailure = { /* Use free tier defaults */ }
            )
        }
    }
    
    private fun getLocationName(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(getApplication(), Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.locality ?: "Current Location"
        } catch (e: Exception) {
            Log.e("WeatherViewModel", "Geocoder error", e)
            "Current Location"
        }
    }
}

// State classes
sealed class WeatherState {
    object Loading : WeatherState()
    data class Success(val data: WeatherResponse) : WeatherState()
    data class Error(val message: String) : WeatherState()
}

sealed class AIInsightsState {
    object Loading : AIInsightsState()
    data class Success(val data: AIInsightsResponse) : AIInsightsState()
    data class Error(val message: String) : AIInsightsState()
}

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val name: String
)
