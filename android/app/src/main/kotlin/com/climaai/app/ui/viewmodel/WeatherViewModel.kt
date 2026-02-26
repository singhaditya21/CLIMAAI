package com.climaai.app.ui.viewmodel

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.climaai.app.data.*
import com.climaai.app.data.cache.ApiCallType
import com.climaai.app.data.cache.CachePolicy
import com.climaai.app.data.cache.RefreshTracker
import com.climaai.app.data.cache.UsageSummary
import com.climaai.app.data.repository.OpenMeteoRepository
import com.climaai.app.data.sync.WearableSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WeatherRepository(application)
    private val refreshTracker = RefreshTracker(application)
    
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
    // Cache & Refresh State
    // =========================================================================
    
    private val _lastUpdated = MutableStateFlow<Long?>(null)
    val lastUpdated = _lastUpdated.asStateFlow()
    
    private val _isFromCache = MutableStateFlow(false)
    val isFromCache = _isFromCache.asStateFlow()
    
    private val _canRefresh = MutableStateFlow(true)
    val canRefresh = _canRefresh.asStateFlow()
    
    private val _rateLimitMessage = MutableStateFlow<String?>(null)
    val rateLimitMessage = _rateLimitMessage.asStateFlow()
    
    private val _lastUpdatedText = MutableStateFlow<String?>(null)
    val lastUpdatedText = _lastUpdatedText.asStateFlow()
    
    // =========================================================================
    // API Usage State
    // =========================================================================
    
    private val _usageSummary = MutableStateFlow<UsageSummary?>(null)
    val usageSummary = _usageSummary.asStateFlow()
    
    /** Data source label for current weather data */
    private val _dataSource = MutableStateFlow("Open-Meteo")
    val dataSource = _dataSource.asStateFlow()
    
    init {
        checkSubscriptionStatus()
        refreshUsageStats()
        // Update lastUpdatedText periodically
        viewModelScope.launch {
            while (true) {
                updateLastUpdatedText()
                kotlinx.coroutines.delay(30_000)
            }
        }
    }
    
    fun setLocation(lat: Double, lon: Double, name: String? = null) {
        val locationName = name ?: getLocationName(lat, lon)
        _location.value = LocationData(lat, lon, locationName)
        fetchWeather(lat, lon)
    }
    
    /**
     * Fetch weather using Open-Meteo as primary source (standalone mode).
     * Falls back to backend WeatherRepository if Open-Meteo fails.
     */
    fun fetchWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            _isRefreshing.value = true
            
            // Primary: Use Open-Meteo directly (no backend required)
            val openMeteoResult = OpenMeteoRepository.getWeather(lat, lon)
            
            openMeteoResult.fold(
                onSuccess = { weather ->
                    // Track API calls
                    refreshTracker.recordApiCall(ApiCallType.WEATHER)
                    if (weather.airQuality != null) {
                        refreshTracker.recordApiCall(ApiCallType.AIR_QUALITY)
                    }
                    refreshTracker.recordRefresh()
                    
                    val enrichedWeather = enrichLocationName(weather, lat, lon)
                    
                    _weatherState.value = WeatherState.Success(enrichedWeather)
                    _lastUpdated.value = System.currentTimeMillis()
                    _isFromCache.value = false
                    _dataSource.value = "Open-Meteo"
                    updateLastUpdatedText()
                    refreshUsageStats()
                    
                    // Sync with Wearable
                    WearableSyncManager.syncWeather(getApplication(), enrichedWeather)

                    Log.d("WeatherViewModel", "Weather loaded from Open-Meteo")
                    
                    // Try to fetch AI insights from backend (graceful degradation)
                    fetchAIInsights(lat, lon)
                },
                onFailure = { error ->
                    Log.w("WeatherViewModel", "Open-Meteo failed, trying backend", error)
                    
                    // Fallback: Try backend WeatherRepository
                    val backendResult = repository.getWeather(
                        lat = lat,
                        lon = lon,
                        forceRefresh = false,
                        isPro = _isPremium.value
                    )
                    _dataSource.value = "Backend"
                    handleWeatherResult(backendResult, lat, lon)
                }
            )
            
            _isRefreshing.value = false
            updateRefreshState()
        }
    }
    
    /**
     * Force refresh (user-initiated pull-to-refresh).
     */
    fun forceRefresh() {
        val loc = _location.value ?: return
        
        viewModelScope.launch {
            _isRefreshing.value = true
            _rateLimitMessage.value = null
            
            // Check rate limit
            val status = refreshTracker.checkRefreshAllowed(_isPremium.value)
            if (!status.allowed) {
                _rateLimitMessage.value = status.reason
                _canRefresh.value = false
                _isRefreshing.value = false
                return@launch
            }
            
            // Primary: Use Open-Meteo directly
            val openMeteoResult = OpenMeteoRepository.getWeather(loc.latitude, loc.longitude)
            
            openMeteoResult.fold(
                onSuccess = { weather ->
                    // Track API calls
                    refreshTracker.recordApiCall(ApiCallType.WEATHER)
                    if (weather.airQuality != null) {
                        refreshTracker.recordApiCall(ApiCallType.AIR_QUALITY)
                    }
                    refreshTracker.recordRefresh()
                    
                    val enrichedWeather = enrichLocationName(weather, loc.latitude, loc.longitude)
                    
                    _weatherState.value = WeatherState.Success(enrichedWeather)
                    _lastUpdated.value = System.currentTimeMillis()
                    _isFromCache.value = false
                    _dataSource.value = "Open-Meteo"
                    updateLastUpdatedText()
                    refreshUsageStats()
                    
                    // Sync with Wearable
                    WearableSyncManager.syncWeather(getApplication(), enrichedWeather)

                    // Try AI insights
                    fetchAIInsights(loc.latitude, loc.longitude)
                },
                onFailure = { error ->
                    // Fallback: Try backend
                    val result = repository.getWeather(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        forceRefresh = true,
                        isPro = _isPremium.value
                    )
                    _dataSource.value = "Backend"
                    handleWeatherResult(result, loc.latitude, loc.longitude)
                }
            )
            
            _isRefreshing.value = false
            updateRefreshState()
        }
    }
    
    /**
     * Enrich weather response with reverse-geocoded location name.
     */
    private suspend fun enrichLocationName(weather: WeatherResponse, lat: Double, lon: Double): WeatherResponse {
        // First try Android Geocoder
        val androidName = getLocationName(lat, lon)
        if (androidName != "Current Location") {
            return weather.copy(
                location = weather.location.copy(name = androidName)
            )
        }
        
        // Fallback: Try Nominatim
        val nominatimName = OpenMeteoRepository.getLocationName(lat, lon)
        if (nominatimName != null) {
            refreshTracker.recordApiCall(ApiCallType.GEOCODING)
            refreshUsageStats()
            return weather.copy(
                location = weather.location.copy(name = nominatimName)
            )
        }
        
        return weather.copy(
            location = weather.location.copy(name = _location.value?.name ?: "Current Location")
        )
    }
    
    private suspend fun handleWeatherResult(result: WeatherResult, lat: Double, lon: Double) {
        when (result) {
            is WeatherResult.Success -> {
                _weatherState.value = WeatherState.Success(result.data)
                _lastUpdated.value = result.cachedAt
                _isFromCache.value = result.fromCache
                updateLastUpdatedText()
                refreshUsageStats()
                
                // Sync with Wearable
                WearableSyncManager.syncWeather(getApplication(), result.data)

                if (result.rateLimited) {
                    _rateLimitMessage.value = result.rateLimitMessage
                }
                
                if (result.networkError) {
                    _rateLimitMessage.value = "Showing cached data. ${result.networkErrorMessage}"
                }
                
                fetchAIInsights(lat, lon)
            }
            is WeatherResult.Error -> {
                _weatherState.value = WeatherState.Error(result.message)
                _rateLimitMessage.value = result.message
            }
        }
    }
    
    private suspend fun updateRefreshState() {
        val status = refreshTracker.checkRefreshAllowed(_isPremium.value)
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
    
    /** Refresh usage statistics for UI display */
    fun refreshUsageStats() {
        viewModelScope.launch {
            _usageSummary.value = refreshTracker.getUsageSummary(_isPremium.value)
        }
    }
    
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
                    Log.w("WeatherViewModel", "AI insights unavailable: ${error.message}")
                    _aiInsightsState.value = AIInsightsState.Error("AI insights unavailable (no backend)")
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
