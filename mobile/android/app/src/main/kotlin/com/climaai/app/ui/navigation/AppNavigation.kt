package com.climaai.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.climaai.app.ui.screens.*
import com.climaai.app.ui.viewmodel.WeatherViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Forecast : Screen("forecast")
    object AIInsights : Screen("ai_insights")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: WeatherViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToForecast = { navController.navigate(Screen.Forecast.route) },
                onNavigateToAI = { navController.navigate(Screen.AIInsights.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        
        composable(Screen.Forecast.route) {
            ForecastScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.AIInsights.route) {
            AIInsightsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
