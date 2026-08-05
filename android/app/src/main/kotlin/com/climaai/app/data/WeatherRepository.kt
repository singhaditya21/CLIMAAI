package com.climaai.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.climaai.app.billing.BillingManager
import com.climaai.app.data.cache.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("climaai_prefs")

/**
 * Weather repository with caching layer.
 * Implements cache-first strategy with rate limiting.
 */
class WeatherRepository(private val context: Context) {
    
    private val api = ApiClient.api
    private val cache = WeatherCacheDatabase.getInstance(context).weatherCacheDao()
    private val refreshTracker = RefreshTracker(context)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "WeatherRepository"
        private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
    }
    
    // =========================================================================
    // Weather Data with Caching
    // =========================================================================
    
    /**
     * Get weather data with cache-first strategy.
     * @param lat Latitude
     * @param lon Longitude
     * @param forceRefresh If true, bypass cache (still rate-limited)
     * @param isPro Pro users get higher rate limits
     * @return Result with weather data and metadata
     */
    suspend fun getWeather(
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false,
        isPro: Boolean = false
    ): WeatherResult {
        val locationKey = generateLocationKey(lat, lon)
        
        // Step 1: Check cache first
        val cached = cache.getWeather(locationKey)
        val cacheValid = cached != null && CachePolicy.isCurrentWeatherValid(cached.cachedAt)
        
        // If cache is valid and not forcing refresh, return cached
        if (cacheValid && !forceRefresh) {
            Log.d(TAG, "Returning cached weather for $locationKey")
            return try {
                val weather = gson.fromJson(cached!!.weatherJson, WeatherResponse::class.java)
                WeatherResult.Success(
                    data = weather,
                    fromCache = true,
                    cachedAt = cached.cachedAt
                )
            } catch (e: Exception) {
                // Cache corrupted, try network
                fetchWeatherFromNetwork(lat, lon, locationKey, isPro)
            }
        }
        
        // Step 2: Check rate limits
        val refreshStatus = refreshTracker.checkRefreshAllowed(isPro)
        if (!refreshStatus.allowed) {
            // Rate limited - return cached if available, otherwise error
            return if (cached != null) {
                try {
                    val weather = gson.fromJson(cached.weatherJson, WeatherResponse::class.java)
                    WeatherResult.Success(
                        data = weather,
                        fromCache = true,
                        cachedAt = cached.cachedAt,
                        rateLimited = true,
                        rateLimitMessage = refreshStatus.reason
                    )
                } catch (e: Exception) {
                    WeatherResult.Error(refreshStatus.reason ?: "Rate limited", cached = null)
                }
            } else {
                WeatherResult.Error(refreshStatus.reason ?: "Rate limited", cached = null)
            }
        }
        
        // Step 3: Fetch from network
        return fetchWeatherFromNetwork(lat, lon, locationKey, isPro)
    }
    
    private suspend fun fetchWeatherFromNetwork(
        lat: Double,
        lon: Double,
        locationKey: String,
        isPro: Boolean
    ): WeatherResult {
        return try {
            val response = api.getWeather(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                val weather = response.body()!!
                val now = System.currentTimeMillis()
                
                // Cache the response
                cache.insertWeather(
                    CachedWeather(
                        locationKey = locationKey,
                        latitude = lat,
                        longitude = lon,
                        locationName = weather.location?.name,
                        weatherJson = gson.toJson(weather),
                        cachedAt = now,
                        lastFetchedAt = now
                    )
                )
                
                // Record successful refresh
                refreshTracker.recordRefresh()
                
                Log.d(TAG, "Fetched and cached weather for $locationKey")
                WeatherResult.Success(
                    data = weather,
                    fromCache = false,
                    cachedAt = now
                )
            } else {
                // Network error - try to return cached
                refreshTracker.recordFailure()
                returnCachedOrError(locationKey, "Failed to fetch weather: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getWeather network error", e)
            refreshTracker.recordFailure()
            returnCachedOrError(locationKey, e.message ?: "Network error")
        }
    }
    
    private suspend fun returnCachedOrError(locationKey: String, errorMessage: String): WeatherResult {
        val cached = cache.getWeather(locationKey)
        return if (cached != null) {
            try {
                val weather = gson.fromJson(cached.weatherJson, WeatherResponse::class.java)
                WeatherResult.Success(
                    data = weather,
                    fromCache = true,
                    cachedAt = cached.cachedAt,
                    networkError = true,
                    networkErrorMessage = errorMessage
                )
            } catch (e: Exception) {
                WeatherResult.Error(errorMessage, cached = null)
            }
        } else {
            WeatherResult.Error(errorMessage, cached = null)
        }
    }
    
    /**
     * Get last refresh timestamp for UI.
     */
    suspend fun getLastRefreshTime(): Long {
        return refreshTracker.getLastRefreshTime()
    }
    
    /**
     * Check if refresh is allowed.
     */
    suspend fun canRefresh(isPro: Boolean = false): RefreshStatus {
        return refreshTracker.checkRefreshAllowed(isPro)
    }
    
    /**
     * Get cached weather without network call.
     */
    suspend fun getCachedWeather(lat: Double, lon: Double): WeatherResponse? {
        val locationKey = generateLocationKey(lat, lon)
        val cached = cache.getWeather(locationKey) ?: return null
        return try {
            gson.fromJson(cached.weatherJson, WeatherResponse::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    // =========================================================================
    // AI Insights with Caching
    // =========================================================================
    
    suspend fun getAIInsights(lat: Double, lon: Double): Result<AIInsightsResponse> {
        val locationKey = generateLocationKey(lat, lon)
        
        // Check cache first
        val cached = cache.getAIInsights(locationKey)
        if (cached != null && CachePolicy.isCacheValid(cached.cachedAt, CachePolicy.AI_INSIGHTS_TTL.inWholeMilliseconds)) {
            return try {
                val insights = gson.fromJson(cached.insightsJson, AIInsightsResponse::class.java)
                Result.success(insights)
            } catch (e: Exception) {
                fetchAIInsightsFromNetwork(lat, lon, locationKey)
            }
        }
        
        return fetchAIInsightsFromNetwork(lat, lon, locationKey)
    }
    
    private suspend fun fetchAIInsightsFromNetwork(lat: Double, lon: Double, locationKey: String): Result<AIInsightsResponse> {
        return try {
            val response = api.getAIInsights(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                val insights = response.body()!!
                
                // Cache the response
                cache.insertAIInsights(
                    CachedAIInsights(
                        locationKey = locationKey,
                        latitude = lat,
                        longitude = lon,
                        insightsJson = gson.toJson(insights),
                        cachedAt = System.currentTimeMillis()
                    )
                )
                
                Result.success(insights)
            } else {
                Result.failure(Exception("Failed to fetch AI insights: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAIInsights error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Authentication
    // =========================================================================
    
    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = api.login(email, password)
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveAuthToken(tokenResponse.accessToken)
                saveUserEmail(email)
                ApiClient.setAuthToken(tokenResponse.accessToken)
                Result.success(tokenResponse.user)
            } else {
                Result.failure(Exception("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "login error", e)
            Result.failure(e)
        }
    }
    
    suspend fun register(email: String, password: String, fullName: String?): Result<User> {
        return try {
            val response = api.register(
                UserRegister(
                    email = email,
                    password = password,
                    fullName = fullName,
                    platform = "android",
                    deviceToken = null
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                saveAuthToken(tokenResponse.accessToken)
                saveUserEmail(email)
                ApiClient.setAuthToken(tokenResponse.accessToken)
                Result.success(tokenResponse.user)
            } else {
                Result.failure(Exception("Registration failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "register error", e)
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(AUTH_TOKEN_KEY)
            prefs.remove(USER_EMAIL_KEY)
        }
        ApiClient.setAuthToken(null)
    }
    
    suspend fun restoreSession(): Boolean {
        val token = getSavedToken()
        return if (token != null) {
            ApiClient.setAuthToken(token)
            true
        } else {
            false
        }
    }

    /**
     * Permanently delete the account (DELETE /api/auth/me, 204 on success).
     *
     * Local auth state is dropped only after the server confirms — clearing it
     * on failure would sign the user out of an account that still exists.
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val response = api.deleteAccount()
            if (response.isSuccessful) {
                logout()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete account (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteAccount error", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // Subscription
    // =========================================================================
    
    suspend fun getSubscriptionStatus(): Result<SubscriptionStatus> {
        return try {
            val response = api.getSubscriptionStatus()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get subscription status"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSubscriptionStatus error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Open the server-side entitlement for a completed Play purchase.
     *
     * The backend re-validates the token with Google before activating, so a
     * forged call cannot mint a subscription — this only reports the purchase.
     */
    suspend fun activateSubscription(purchaseToken: String, productId: String): Result<Subscription> {
        // Backend SubscriptionPlan knows exactly "monthly" and "annual".
        // PRODUCT_LIFETIME has no server-side counterpart (and the paywall does
        // not sell it); mapping it to a plan it is not would corrupt the row.
        val plan = when (productId) {
            BillingManager.PRODUCT_MONTHLY -> "monthly"
            BillingManager.PRODUCT_YEARLY -> "annual"
            else -> return Result.failure(
                Exception("No backend plan corresponds to product '$productId'")
            )
        }

        return try {
            val response = api.activateSubscription(
                SubscriptionActivateRequest(
                    platform = "google",
                    plan = plan,
                    receipt_data = purchaseToken
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Subscription activation failed (HTTP ${response.code()})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "activateSubscription error", e)
            Result.failure(e)
        }
    }

    suspend fun validateReceipt(purchaseToken: String, productId: String): Result<ReceiptValidationResponse> {
        return try {
            val response = api.validateReceipt(
                ReceiptValidationRequest(
                    platform = "google",
                    receipt_data = purchaseToken,
                    product_id = productId
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Receipt validation failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "validateReceipt error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Alerts
    // =========================================================================
    
    suspend fun getAlerts(lat: Double, lon: Double, locationName: String = "your location"): Result<com.climaai.app.data.api.AlertsResponse> {
        return try {
            val response = api.getAlerts(lat, lon, locationName)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch alerts: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAlerts error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getAlertHistory(): Result<com.climaai.app.data.api.AlertHistoryResponse> {
        return try {
            val response = api.getAlertHistory()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch alert history"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAlertHistory error", e)
            Result.failure(e)
        }
    }
    
    suspend fun dismissAlert(alertId: Int): Result<Boolean> {
        return try {
            val response = api.dismissAlert(alertId)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to dismiss alert"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "dismissAlert error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Health: Pollen, Flu, Migraine
    // =========================================================================
    
    suspend fun getPollenForecast(lat: Double, lon: Double, days: Int = 5): Result<com.climaai.app.data.api.PollenForecastResponse> {
        return try {
            val response = api.getPollenForecast(lat, lon, days)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch pollen forecast"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPollenForecast error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getTodaysPollen(lat: Double, lon: Double): Result<com.climaai.app.data.api.PollenTodayResponse> {
        return try {
            val response = api.getTodaysPollen(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch today's pollen"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTodaysPollen error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getFluRisk(lat: Double, lon: Double): Result<com.climaai.app.data.api.FluRiskResponse> {
        return try {
            val response = api.getFluRisk(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch flu risk"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFluRisk error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getMigraineRisk(lat: Double, lon: Double): Result<com.climaai.app.data.api.MigraineRiskResponse> {
        return try {
            val response = api.getMigraineRisk(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch migraine risk"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMigraineRisk error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Activities
    // =========================================================================
    
    suspend fun getAllActivities(lat: Double, lon: Double): Result<com.climaai.app.data.api.AllActivitiesResponse> {
        return try {
            val response = api.getAllActivities(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch activities"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllActivities error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getActivityForecast(activity: String, lat: Double, lon: Double): Result<com.climaai.app.data.api.ActivityDetailResponse> {
        return try {
            val response = api.getActivityForecast(activity, lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch activity forecast"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getActivityForecast error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Locations
    // =========================================================================
    
    suspend fun searchLocations(query: String, limit: Int = 10): Result<com.climaai.app.data.api.LocationSearchResponse> {
        return try {
            val response = api.searchLocations(query, limit)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to search locations"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchLocations error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getFavoriteLocations(): Result<com.climaai.app.data.api.FavoriteLocationsResponse> {
        return try {
            val response = api.getFavoriteLocations()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch favorites"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFavoriteLocations error", e)
            Result.failure(e)
        }
    }
    
    suspend fun addFavoriteLocation(name: String, lat: Double, lon: Double, isDefault: Boolean = false): Result<com.climaai.app.data.api.AddFavoriteResponse> {
        return try {
            val response = api.addFavoriteLocation(name, lat, lon, isDefault)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to add favorite"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "addFavoriteLocation error", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteFavoriteLocation(locationId: Int): Result<Boolean> {
        return try {
            val response = api.deleteFavoriteLocation(locationId)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete favorite"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFavoriteLocation error", e)
            Result.failure(e)
        }
    }
    
    suspend fun setDefaultLocation(locationId: Int): Result<Boolean> {
        return try {
            val response = api.setDefaultLocation(locationId)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to set default location"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "setDefaultLocation error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Notifications
    // =========================================================================
    
    suspend fun registerDevice(token: String, platform: String = "android", deviceInfo: Map<String, Any>? = null): Result<com.climaai.app.data.api.DeviceRegisterResponse> {
        return try {
            val body = mutableMapOf<String, Any>(
                "token" to token,
                "platform" to platform
            )
            deviceInfo?.let { body["device_info"] = it }
            val response = api.registerDevice(body)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to register device"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerDevice error", e)
            Result.failure(e)
        }
    }
    
    suspend fun unregisterDevice(token: String): Result<Boolean> {
        return try {
            val response = api.unregisterDevice(mapOf("token" to token))
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to unregister device"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "unregisterDevice error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getNotificationPreferences(): Result<com.climaai.app.data.api.NotificationPreferencesResponse> {
        return try {
            val response = api.getNotificationPreferences()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get notification preferences"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getNotificationPreferences error", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateNotificationPreferences(
        weatherAlerts: Boolean? = null,
        dailySummary: Boolean? = null,
        severeWeather: Boolean? = null
    ): Result<com.climaai.app.data.api.NotificationPreferencesUpdateResponse> {
        return try {
            val body = mutableMapOf<String, Boolean>()
            weatherAlerts?.let { body["weather_alerts"] = it }
            dailySummary?.let { body["daily_summary"] = it }
            severeWeather?.let { body["severe_weather"] = it }
            val response = api.updateNotificationPreferences(body)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update notification preferences"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateNotificationPreferences error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Personalization
    // =========================================================================
    
    suspend fun trackEvent(eventType: String, eventData: Map<String, Any>, weatherContext: Map<String, Any>? = null): Result<Boolean> {
        return try {
            val request = com.climaai.app.data.api.TrackEventRequest(eventType, eventData, weatherContext)
            val response = api.trackEvent(request)
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to track event"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "trackEvent error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getPersonalizedRecommendations(
        temperature: Double,
        humidity: Int,
        uvIndex: Double,
        precipProbability: Int
    ): Result<com.climaai.app.data.api.PersonalizedContentResponse> {
        return try {
            val response = api.getPersonalizedRecommendations(temperature, humidity, uvIndex, precipProbability)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get personalized recommendations"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPersonalizedRecommendations error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Precipitation Nowcast
    // =========================================================================
    
    suspend fun getPrecipitationNowcast(lat: Double, lon: Double): Result<com.climaai.app.data.api.PrecipitationNowcast> {
        return try {
            val response = api.getPrecipitationNowcast(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch precipitation nowcast"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPrecipitationNowcast error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Travel Risk (Premium)
    // =========================================================================
    
    suspend fun getTravelRisk(lat: Double, lon: Double, destination: String = "your destination"): Result<TravelRiskAnalysis> {
        return try {
            val response = api.getTravelRisk(lat, lon, destination)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch travel risk"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTravelRisk error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // NWS Weather Alerts
    // =========================================================================
    
    suspend fun getWeatherAlertsNWS(lat: Double, lon: Double): Result<com.climaai.app.data.api.NWSAlertsResponse> {
        return try {
            val response = api.getWeatherAlertsNWS(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch NWS alerts"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getWeatherAlertsNWS error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // Multi-Source Weather
    // =========================================================================
    
    suspend fun getMultiSourceWeather(lat: Double, lon: Double, sources: String? = null): Result<com.climaai.app.data.api.MultiSourceWeatherResponse> {
        return try {
            val response = api.getMultiSourceWeather(lat, lon, sources)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch multi-source weather"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMultiSourceWeather error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getUVIndex(lat: Double, lon: Double): Result<com.climaai.app.data.api.UVIndexData> {
        return try {
            val response = api.getUVIndex(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch UV index"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUVIndex error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getMarineWeather(lat: Double, lon: Double): Result<com.climaai.app.data.api.MarineWeatherData> {
        return try {
            val response = api.getMarineWeather(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch marine weather"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMarineWeather error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getHistoricalWeather(lat: Double, lon: Double, startDate: String, endDate: String): Result<com.climaai.app.data.api.HistoricalWeatherData> {
        return try {
            val response = api.getHistoricalWeather(lat, lon, startDate, endDate)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch historical weather"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHistoricalWeather error", e)
            Result.failure(e)
        }
    }
    
    // =========================================================================
    // DataStore Helpers
    // =========================================================================
    
    private suspend fun saveAuthToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[AUTH_TOKEN_KEY] = token
        }
    }
    
    private suspend fun saveUserEmail(email: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_EMAIL_KEY] = email
        }
    }
    
    private suspend fun getSavedToken(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[AUTH_TOKEN_KEY]
        }.first()
    }
    
    val userEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USER_EMAIL_KEY]
    }
}

/**
 * Result wrapper for weather fetch with cache metadata.
 */
sealed class WeatherResult {
    data class Success(
        val data: WeatherResponse,
        val fromCache: Boolean,
        val cachedAt: Long,
        val rateLimited: Boolean = false,
        val rateLimitMessage: String? = null,
        val networkError: Boolean = false,
        val networkErrorMessage: String? = null
    ) : WeatherResult()
    
    data class Error(
        val message: String,
        val cached: WeatherResponse?
    ) : WeatherResult()
}
