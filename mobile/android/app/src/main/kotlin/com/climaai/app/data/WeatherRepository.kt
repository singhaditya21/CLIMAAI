package com.climaai.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    // Authentication (unchanged)
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
    
    // =========================================================================
    // Subscription (unchanged)
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
