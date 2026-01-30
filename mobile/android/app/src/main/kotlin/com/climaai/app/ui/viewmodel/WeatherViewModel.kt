package com.climaai.app.ui.viewmodel

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.climaai.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WeatherRepository(application)
    
    // Weather state
    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Loading)
    val weatherState = _weatherState.asStateFlow()
    
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
    
    init {
        checkSubscriptionStatus()
    }
    
    fun setLocation(lat: Double, lon: Double, name: String? = null) {
        val locationName = name ?: getLocationName(lat, lon)
        _location.value = LocationData(lat, lon, locationName)
        fetchWeather(lat, lon)
    }
    
    fun fetchWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            _weatherState.value = WeatherState.Loading
            _isRefreshing.value = true
            
            repository.getWeather(lat, lon).fold(
                onSuccess = { weather ->
                    _weatherState.value = WeatherState.Success(weather)
                    // Also fetch AI insights
                    fetchAIInsights(lat, lon)
                },
                onFailure = { error ->
                    _weatherState.value = WeatherState.Error(error.message ?: "Unknown error")
                }
            )
            
            _isRefreshing.value = false
        }
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
        _location.value?.let { loc ->
            fetchWeather(loc.latitude, loc.longitude)
        }
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
