package com.climaai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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

        // Chained off the location result rather than fired next to it: two
        // permission dialogs launched in the same frame race each other and the
        // second one is dropped.
        requestNotificationPermissionIfNeeded()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Nothing to do with the answer. NotificationService re-checks the
        // permission before every post, so a refusal just means no notifications.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Has to run before setContent. NavHost reads its start destination once,
        // on the first composition, and this is the only thing that loads the
        // persisted onboarding flag — without it every cold start replays the
        // onboarding deck.
        authViewModel.initialize(this)

        // Fetches the plans the paywall renders as cards; with no call the
        // paywall lists nothing to buy.
        subscriptionViewModel.initialize(this)

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
                        subscriptionViewModel = subscriptionViewModel,
                        onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded
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
            requestNotificationPermissionIfNeeded()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /**
     * Ask for POST_NOTIFICATIONS, but only once the user has been through
     * onboarding.
     *
     * The permission is declared in the manifest and NotificationService gates
     * every post on it, but nothing ever asked for it, so on API 33+ that gate
     * was permanently closed. Android grants the system prompt a very small
     * number of showings before it starts auto-denying in silence, so it is
     * spent after onboarding has explained what the alerts are for — either from
     * the end of the deck, or on launch for someone who onboarded already.
     */
    private fun requestNotificationPermissionIfNeeded() {
        // Pre-33 the permission is granted at install time.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (!authViewModel.onboardingState.value.hasCompletedOnboarding) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
