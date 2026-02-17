package com.climaai.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.climaai.wear.presentation.screens.WeatherScreen
import com.climaai.wear.presentation.screens.ForecastScreen
import com.climaai.wear.presentation.theme.ClimaAIWearTheme

class WearMainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ClimaAIWearTheme {
                WearNavigation()
            }
        }
    }
}

@Composable
fun WearNavigation() {
    val navController = rememberSwipeDismissableNavController()
    
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "weather"
    ) {
        composable("weather") {
            WeatherScreen(
                onNavigateToForecast = { navController.navigate("forecast") }
            )
        }
        composable("forecast") {
            ForecastScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
