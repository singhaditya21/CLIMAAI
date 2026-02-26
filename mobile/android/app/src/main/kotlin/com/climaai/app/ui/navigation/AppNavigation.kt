package com.climaai.app.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.climaai.app.ui.screens.*
import com.climaai.app.ui.viewmodel.AuthViewModel
import com.climaai.app.ui.viewmodel.SubscriptionViewModel
import com.climaai.app.ui.viewmodel.WeatherViewModel
import com.climaai.app.ui.viewmodel.WeatherState

sealed class Screen(val route: String) {
    // Auth flow
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    
    // Main flow
    object Home : Screen("home")
    object Forecast : Screen("forecast")
    object AIInsights : Screen("ai_insights")
    object Settings : Screen("settings")
    
    // Feature screens
    object Radar : Screen("radar")
    object AirQuality : Screen("air_quality")
    object Pollen : Screen("pollen")
    object Paywall : Screen("paywall")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    weatherViewModel: WeatherViewModel,
    authViewModel: AuthViewModel,
    subscriptionViewModel: SubscriptionViewModel
) {
    val context = LocalContext.current
    val onboardingState by authViewModel.onboardingState.collectAsState()
    
    // Determine start destination
    val startDestination = remember(onboardingState.hasCompletedOnboarding) {
        if (onboardingState.hasCompletedOnboarding) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ==================== Auth Flow ====================
        
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                authViewModel = authViewModel,
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Register.route) {
            RegisterScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }
        
        // ==================== Main Flow ====================
        
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = weatherViewModel,
                onNavigateToForecast = { navController.navigate(Screen.Forecast.route) },
                onNavigateToAI = { navController.navigate(Screen.AIInsights.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        
        composable(Screen.Forecast.route) {
            ForecastScreen(
                viewModel = weatherViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.AIInsights.route) {
            AIInsightsScreen(
                viewModel = weatherViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = weatherViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // ==================== Feature Screens ====================
        
        composable(Screen.Radar.route) {
            val weather by weatherViewModel.state.collectAsState()
            val latitude = (weather as? WeatherState.Success)?.data?.location?.latitude ?: 37.7749
            val longitude = (weather as? WeatherState.Success)?.data?.location?.longitude ?: -122.4194
            
            RadarMapScreen(
                latitude = latitude,
                longitude = longitude,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.AirQuality.route) {
            val weather by weatherViewModel.state.collectAsState()
            val airQuality = (weather as? WeatherState.Success)?.data?.airQuality
            
            AirQualityDetailScreen(
                airQuality = airQuality,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Pollen.route) {
            PollenScreen(
                pollenData = null,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Paywall.route) {
            PaywallScreen(
                subscriptionViewModel = subscriptionViewModel,
                onDismiss = { navController.popBackStack() },
                onSubscriptionSuccess = { navController.popBackStack() }
            )
        }
    }
}
