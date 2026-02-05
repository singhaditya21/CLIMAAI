package com.climaai.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.climaai.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authentication state
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Onboarding state
 */
data class OnboardingState(
    val hasCompletedOnboarding: Boolean = false,
    val currentPage: Int = 0
)

/**
 * ViewModel for authentication and onboarding
 */
class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _onboardingState = MutableStateFlow(OnboardingState())
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // Preferences keys
    private val PREFS_NAME = "climaai_auth"
    private val KEY_TOKEN = "auth_token"
    private val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    /**
     * Initialize auth state from stored token
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        val onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

        _onboardingState.value = OnboardingState(hasCompletedOnboarding = onboardingComplete)

        if (token != null) {
            ApiClient.setAuthToken(token)
            loadCurrentUser()
        }
    }

    /**
     * Login with email and password
     */
    fun login(email: String, password: String, context: Context) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            try {
                val response = ApiClient.api.login(email, password)

                if (response.isSuccessful && response.body() != null) {
                    val tokenResponse = response.body()!!
                    saveToken(context, tokenResponse.accessToken)
                    ApiClient.setAuthToken(tokenResponse.accessToken)
                    loadCurrentUser()
                } else {
                    val errorBody = response.errorBody()?.string()
                    _authState.value = AuthState.Error(
                        errorBody ?: "Login failed. Please check your credentials."
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message ?: "Network error. Please try again."
                )
            }
        }
    }

    /**
     * Register new user
     */
    fun register(name: String, email: String, password: String, context: Context) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            try {
                val user = UserRegister(
                    email = email,
                    password = password,
                    fullName = name,
                    deviceToken = null
                )
                val response = ApiClient.api.register(user)

                if (response.isSuccessful && response.body() != null) {
                    val tokenResponse = response.body()!!
                    saveToken(context, tokenResponse.accessToken)
                    ApiClient.setAuthToken(tokenResponse.accessToken)
                    loadCurrentUser()
                } else {
                    val errorBody = response.errorBody()?.string()
                    _authState.value = AuthState.Error(
                        errorBody ?: "Registration failed. Email may already be in use."
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message ?: "Network error. Please try again."
                )
            }
        }
    }

    /**
     * Load current user profile
     */
    private fun loadCurrentUser() {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getCurrentUser()

                if (response.isSuccessful && response.body() != null) {
                    _authState.value = AuthState.Authenticated(response.body()!!)
                    _isLoggedIn.value = true
                } else {
                    _authState.value = AuthState.Error("Session expired. Please login again.")
                    _isLoggedIn.value = false
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Failed to load user profile.")
                _isLoggedIn.value = false
            }
        }
    }

    /**
     * Logout user
     */
    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_TOKEN).apply()
        ApiClient.setAuthToken(null)
        _authState.value = AuthState.Idle
        _isLoggedIn.value = false
    }

    /**
     * Save token to SharedPreferences
     */
    private fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /**
     * Skip login and continue as guest
     */
    fun continueAsGuest() {
        _authState.value = AuthState.Idle
        _isLoggedIn.value = false
    }

    /**
     * Onboarding navigation
     */
    fun nextOnboardingPage() {
        val current = _onboardingState.value.currentPage
        if (current < 3) {
            _onboardingState.value = _onboardingState.value.copy(currentPage = current + 1)
        }
    }

    fun previousOnboardingPage() {
        val current = _onboardingState.value.currentPage
        if (current > 0) {
            _onboardingState.value = _onboardingState.value.copy(currentPage = current - 1)
        }
    }

    fun completeOnboarding(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        _onboardingState.value = _onboardingState.value.copy(hasCompletedOnboarding = true)
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }
}
