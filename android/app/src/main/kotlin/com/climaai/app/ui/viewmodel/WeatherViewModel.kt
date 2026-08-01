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
import com.climaai.app.location.UltraLocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "WeatherViewModel"

        /** Hard ceiling on how long the app may show its loading spinner. */
        const val LOCATION_TIMEOUT_MS = 20_000L
    }

    private val repository = WeatherRepository(application)
    private val refreshTracker = RefreshTracker(application)
    private val locationManager = UltraLocationManager(application)
    
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
    
    // =========================================================================
    // New API State Flows
    // =========================================================================
    
    private val _alertsState = MutableStateFlow<List<com.climaai.app.data.api.AlertData>>(emptyList())
    val alertsState = _alertsState.asStateFlow()
    
    private val _pollenState = MutableStateFlow<com.climaai.app.data.api.PollenTodayResponse?>(null)
    val pollenState = _pollenState.asStateFlow()
    
    private val _precipNowcast = MutableStateFlow<com.climaai.app.data.api.PrecipitationNowcast?>(null)
    val precipNowcast = _precipNowcast.asStateFlow()
    
    private val _fluRisk = MutableStateFlow<com.climaai.app.data.api.FluRiskResponse?>(null)
    val fluRisk = _fluRisk.asStateFlow()
    
    private val _migraineRisk = MutableStateFlow<com.climaai.app.data.api.MigraineRiskResponse?>(null)
    val migraineRisk = _migraineRisk.asStateFlow()
    
    private val _activities = MutableStateFlow<List<com.climaai.app.data.api.ActivityForecastItem>>(emptyList())
    val activities = _activities.asStateFlow()
    
    private val _favoriteLocations = MutableStateFlow<List<com.climaai.app.data.api.FavoriteLocationData>>(emptyList())
    val favoriteLocations = _favoriteLocations.asStateFlow()
    
    private val _nwsAlerts = MutableStateFlow<List<com.climaai.app.data.api.NWSAlert>>(emptyList())
    val nwsAlerts = _nwsAlerts.asStateFlow()
    
    private val _multiSourceWeather = MutableStateFlow<com.climaai.app.data.api.MultiSourceWeatherResponse?>(null)
    val multiSourceWeather = _multiSourceWeather.asStateFlow()
    
    private val _uvIndex = MutableStateFlow<com.climaai.app.data.api.UVIndexData?>(null)
    val uvIndex = _uvIndex.asStateFlow()
    
    private val _marineWeather = MutableStateFlow<com.climaai.app.data.api.MarineWeatherData?>(null)
    val marineWeather = _marineWeather.asStateFlow()
    
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
    
    /**
     * Resolve the device's location and load weather for it.
     *
     * This is the app's entry point on launch and the target of every retry.
     * It is written so that the Loading state is *always* left, whatever the
     * location stack does: the resolve is wrapped in a hard timeout and every
     * outcome — success, failure, or timeout — writes a terminal state.
     *
     * That matters because the previous implementation had no bound at all. It
     * asked for a balanced-accuracy fix and waited on a callback that, with no
     * network location provider available, simply never fired. The app sat on
     * its spinner indefinitely with nothing in the log, and the Retry button
     * was a no-op because it bailed out when no location had been set yet.
     */
    fun loadWeatherForCurrentLocation() {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            _isRefreshing.value = true

            // The manager applies its own per-request timeouts; this one is a
            // backstop so a hung callback down in Play services can never hold
            // the UI hostage. It is deliberately longer than the manager's.
            val result = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                runCatching { locationManager.requestLocation() }
            }

            when {
                result == null -> {
                    Log.w(TAG, "Location timed out after ${LOCATION_TIMEOUT_MS}ms")
                    _isRefreshing.value = false
                    _weatherState.value = WeatherState.Error(
                        "Couldn't get your location in time. Check that location is " +
                            "switched on, then try again."
                    )
                }
                result.isSuccess -> {
                    val fix = result.getOrThrow()
                    Log.d(TAG, "Location resolved: quality=${fix.quality} cached=${fix.isFromCache}")
                    setLocation(fix.location.latitude, fix.location.longitude, fix.name)
                }
                else -> {
                    val error = result.exceptionOrNull()
                    Log.w(TAG, "Location unavailable", error)
                    _isRefreshing.value = false
                    _weatherState.value = WeatherState.Error(describeLocationError(error))
                }
            }
        }
    }

    private fun describeLocationError(error: Throwable?): String = when (error) {
        is UltraLocationManager.LocationError.PermissionDenied ->
            "ClimaAI needs location permission to show your local weather. " +
                "Grant it in Settings, then try again."
        is UltraLocationManager.LocationError.NetworkUnavailable ->
            "You appear to be offline, and there's no recent location saved."
        is UltraLocationManager.LocationError.Timeout ->
            "Couldn't get your location in time. Try again."
        else ->
            "Couldn't determine your location. Check that location is switched on, " +
                "then try again."
    }

    fun setLocation(lat: Double, lon: Double, name: String? = null) {
        viewModelScope.launch {
            // Geocoding is blocking I/O, so it is resolved off the main thread
            // before the location is published.
            val locationName = name ?: withContext(Dispatchers.IO) { getLocationName(lat, lon) }
            _location.value = LocationData(lat, lon, locationName)
            fetchWeather(lat, lon)
        }
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
                    
                    Log.d("WeatherViewModel", "Weather loaded from Open-Meteo")
                    
                    // Try to fetch AI insights from backend (graceful degradation)
                    fetchAIInsights(lat, lon)
                    
                    // Fetch additional data in parallel
                    fetchAdditionalData(lat, lon)
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
        // No location yet means the initial resolve failed, and this call is the
        // user tapping Retry. Bailing out here is what made that button do
        // nothing; go back and resolve the location instead.
        val loc = _location.value ?: run {
            loadWeatherForCurrentLocation()
            return
        }

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
                    
                    // Try AI insights
                    fetchAIInsights(loc.latitude, loc.longitude)
                    
                    // Fetch additional data in parallel
                    fetchAdditionalData(loc.latitude, loc.longitude)
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
     * Fetch additional data from all new API endpoints (non-blocking).
     */
    private fun fetchAdditionalData(lat: Double, lon: Double) {
        viewModelScope.launch {
            // Fetch in parallel using multiple coroutines
            launch { fetchAlerts(lat, lon) }
            launch { fetchPollen(lat, lon) }
            launch { fetchPrecipNowcast(lat, lon) }
            launch { fetchHealthRisks(lat, lon) }
            launch { fetchActivities(lat, lon) }
            launch { fetchNWSAlerts(lat, lon) }
            launch { fetchMultiSourceWeather(lat, lon) }
            launch { fetchUVIndex(lat, lon) }
            launch { fetchMarineWeather(lat, lon) }
            launch { fetchFavoriteLocations() }
        }
    }
    
    private suspend fun fetchAlerts(lat: Double, lon: Double) {
        repository.getAlerts(lat, lon).fold(
            onSuccess = { response -> _alertsState.value = response.alerts },
            onFailure = { Log.w("WeatherViewModel", "Alerts unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchPollen(lat: Double, lon: Double) {
        repository.getTodaysPollen(lat, lon).fold(
            onSuccess = { response -> _pollenState.value = response },
            onFailure = { Log.w("WeatherViewModel", "Pollen unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchPrecipNowcast(lat: Double, lon: Double) {
        repository.getPrecipitationNowcast(lat, lon).fold(
            onSuccess = { response -> _precipNowcast.value = response },
            onFailure = { Log.w("WeatherViewModel", "Precip nowcast unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchHealthRisks(lat: Double, lon: Double) {
        repository.getFluRisk(lat, lon).fold(
            onSuccess = { response -> _fluRisk.value = response },
            onFailure = { Log.w("WeatherViewModel", "Flu risk unavailable: ${it.message}") }
        )
        repository.getMigraineRisk(lat, lon).fold(
            onSuccess = { response -> _migraineRisk.value = response },
            onFailure = { Log.w("WeatherViewModel", "Migraine risk unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchActivities(lat: Double, lon: Double) {
        repository.getAllActivities(lat, lon).fold(
            onSuccess = { response -> _activities.value = response.activities },
            onFailure = { Log.w("WeatherViewModel", "Activities unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchNWSAlerts(lat: Double, lon: Double) {
        repository.getWeatherAlertsNWS(lat, lon).fold(
            onSuccess = { response -> _nwsAlerts.value = response.alerts },
            onFailure = { Log.w("WeatherViewModel", "NWS alerts unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchMultiSourceWeather(lat: Double, lon: Double) {
        repository.getMultiSourceWeather(lat, lon).fold(
            onSuccess = { response -> _multiSourceWeather.value = response },
            onFailure = { Log.w("WeatherViewModel", "Multi-source unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchUVIndex(lat: Double, lon: Double) {
        repository.getUVIndex(lat, lon).fold(
            onSuccess = { response -> _uvIndex.value = response },
            onFailure = { Log.w("WeatherViewModel", "UV index unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchMarineWeather(lat: Double, lon: Double) {
        repository.getMarineWeather(lat, lon).fold(
            onSuccess = { response -> _marineWeather.value = response },
            onFailure = { Log.w("WeatherViewModel", "Marine weather unavailable: ${it.message}") }
        )
    }
    
    private suspend fun fetchFavoriteLocations() {
        repository.getFavoriteLocations().fold(
            onSuccess = { response -> _favoriteLocations.value = response.favorites },
            onFailure = { Log.w("WeatherViewModel", "Favorites unavailable: ${it.message}") }
        )
    }
    
    // =========================================================================
    // Location Search & Favorites (public)
    // =========================================================================
    
    fun searchLocations(query: String) {
        viewModelScope.launch {
            repository.searchLocations(query).fold(
                onSuccess = { /* emit through a callback or flow as needed */ },
                onFailure = { Log.w("WeatherViewModel", "Location search failed: ${it.message}") }
            )
        }
    }
    
    fun addFavorite(name: String, lat: Double, lon: Double, isDefault: Boolean = false) {
        viewModelScope.launch {
            repository.addFavoriteLocation(name, lat, lon, isDefault).fold(
                onSuccess = { fetchFavoriteLocations() },
                onFailure = { Log.w("WeatherViewModel", "Add favorite failed: ${it.message}") }
            )
        }
    }
    
    fun removeFavorite(locationId: Int) {
        viewModelScope.launch {
            repository.deleteFavoriteLocation(locationId).fold(
                onSuccess = { fetchFavoriteLocations() },
                onFailure = { Log.w("WeatherViewModel", "Remove favorite failed: ${it.message}") }
            )
        }
    }
    
    // =========================================================================
    // Personalization (public)
    // =========================================================================
    
    fun trackUserEvent(eventType: String, eventData: Map<String, Any>) {
        viewModelScope.launch {
            repository.trackEvent(eventType, eventData)
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
                
                if (result.rateLimited) {
                    _rateLimitMessage.value = result.rateLimitMessage
                }
                
                if (result.networkError) {
                    _rateLimitMessage.value = "Showing cached data. ${result.networkErrorMessage}"
                }
                
                fetchAIInsights(lat, lon)
                fetchAdditionalData(lat, lon)
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
    
    override fun onCleared() {
        super.onCleared()
        // The manager registers a connectivity callback; release it.
        locationManager.destroy()
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
