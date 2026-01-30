package com.climaai.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("climaai_prefs")

class WeatherRepository(private val context: Context) {
    
    private val api = ApiClient.api
    
    companion object {
        private const val TAG = "WeatherRepository"
        private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
    }
    
    // =========================================================================
    // Weather Data
    // =========================================================================
    
    suspend fun getWeather(lat: Double, lon: Double): Result<WeatherResponse> {
        return try {
            val response = api.getWeather(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch weather: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getWeather error", e)
            Result.failure(e)
        }
    }
    
    suspend fun getAIInsights(lat: Double, lon: Double): Result<AIInsightsResponse> {
        return try {
            val response = api.getAIInsights(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
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
