package com.climaai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.climaai.app.ui.navigation.AppNavigation
import com.climaai.app.ui.theme.ClimaAITheme
import com.climaai.app.ui.viewmodel.WeatherViewModel
import com.climaai.app.ui.viewmodel.AuthViewModel
import com.climaai.app.ui.viewmodel.SubscriptionViewModel

class MainActivity : ComponentActivity() {

    private val weatherViewModel: WeatherViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private val subscriptionViewModel: SubscriptionViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Whether or not permission was granted, hand off to the view model. It
        // owns the whole resolve-or-fail decision, including the message shown
        // when permission is refused — there is no second copy of that logic
        // here to drift out of sync.
        weatherViewModel.loadWeatherForCurrentLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request location permission
        checkLocationPermission()

        setContent {
            ClimaAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        weatherViewModel = weatherViewModel,
                        authViewModel = authViewModel,
                        subscriptionViewModel = subscriptionViewModel
                    )
                }
            }
        }
    }
    
    private fun checkLocationPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            weatherViewModel.loadWeatherForCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}
