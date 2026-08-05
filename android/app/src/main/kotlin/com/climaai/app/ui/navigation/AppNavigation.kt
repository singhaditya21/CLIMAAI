package com.climaai.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.climaai.app.BuildConfig
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
    object LocationSwitcher : Screen("location_switcher")
    object AppearanceSettings : Screen("appearance_settings")

    // Feature screens
    object Radar : Screen("radar")
    object AirQuality : Screen("air_quality")
    object Pollen : Screen("pollen")
    object Paywall : Screen("paywall")
}

private data class MainTab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

/**
 * Top-level destinations shown in the bottom bar.
 *
 * Radar, air quality and pollen are finished screens that no other screen links
 * to — none of them owns a button anywhere in the app, so before this bar they
 * were registered routes with no way in. Home is included so the bar can get the
 * user back out again.
 */
private val mainTabs = listOf(
    MainTab(Screen.Home, "Today", Icons.Default.WbSunny),
    MainTab(Screen.Radar, "Radar", Icons.Default.Radar),
    MainTab(Screen.AirQuality, "Air", Icons.Default.Air),
    MainTab(Screen.Pollen, "Pollen", Icons.Default.LocalFlorist)
)

@Composable
fun AppNavigation(
    navController: NavHostController,
    weatherViewModel: WeatherViewModel,
    authViewModel: AuthViewModel,
    subscriptionViewModel: SubscriptionViewModel,
    onRequestNotificationPermission: () -> Unit
) {
    // Read once, and deliberately NOT collected as state.
    //
    // NavHost rebuilds its graph whenever startDestination changes, and setting a
    // new graph resets the back stack to that start destination. completeOnboarding()
    // flips this flag to true, so keying on it meant: user taps "Get Started" ->
    // navigate(Login) runs -> the flag flips -> the graph is rebuilt with Home as
    // the start -> the back stack is reset and the Login navigation is discarded.
    // Onboarding therefore fell straight through to Home and the login screen was
    // unreachable in the shipped app despite having an inbound edge in the source.
    //
    // AuthViewModel.initialize() has already loaded the persisted flag in
    // MainActivity.onCreate, before the first composition, so reading .value here
    // is correct on a cold start — which is what keeps onboarding from replaying.
    val startDestination = remember {
        if (authViewModel.onboardingState.value.hasCompletedOnboarding) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // A plain Column rather than a Scaffold: every screen already sits in its own
    // Scaffold and handles system bars there, so an outer one would apply the same
    // insets a second time and push all of them down.
    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f)
        ) {
            // ==================== Auth Flow ====================

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    authViewModel = authViewModel,
                    onComplete = {
                        // Onboarding is where the app explains that it sends rain
                        // and severe-weather alerts, so this is the one moment the
                        // user has context for the notification prompt.
                        onRequestNotificationPermission()

                        // Onboarding used to drop straight to Home, which left the
                        // login screen with no inbound edge at all — nobody could
                        // ever sign in, so nothing auth-gated worked. Login itself
                        // offers "continue as guest" for people who don't want an
                        // account.
                        navController.navigate(Screen.Login.route) {
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
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToLocationSwitcher = {
                        navController.navigate(Screen.LocationSwitcher.route)
                    }
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
                    authViewModel = authViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAppearance = {
                        navController.navigate(Screen.AppearanceSettings.route)
                    },
                    onNavigateToPaywall = {
                        // A licensing gate, not an experiment flag: the app's
                        // weather data is Open-Meteo's free tier, which is
                        // licensed for non-commercial use only, so the app ships
                        // free — paywall dark — until a commercial data licence
                        // exists. The route stays registered and this edge stays
                        // in code so flipping MONETIZATION_ENABLED is the whole
                        // relaunch.
                        if (BuildConfig.MONETIZATION_ENABLED) {
                            navController.navigate(Screen.Paywall.route)
                        }
                    }
                )
            }

            composable(Screen.AppearanceSettings.route) {
                AppearanceSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPaywall = {
                        // Same licensing gate as the Settings edge above.
                        if (BuildConfig.MONETIZATION_ENABLED) {
                            navController.navigate(Screen.Paywall.route)
                        }
                    }
                )
            }

            composable(Screen.LocationSwitcher.route) {
                val activeLocation by weatherViewModel.location.collectAsState()
                val favorites by weatherViewModel.favoriteLocations.collectAsState()

                LocationSwitcherScreen(
                    currentLocation = activeLocation?.let { active ->
                        SavedLocation(
                            id = "active",
                            name = active.name,
                            // Neither the device fix nor a saved favourite carries
                            // a country, and guessing one from the coordinates
                            // would be inventing a fact — the row just omits it.
                            country = "",
                            latitude = active.latitude,
                            longitude = active.longitude,
                            isCurrent = true
                        )
                    },
                    savedLocations = favorites.map { favorite ->
                        SavedLocation(
                            id = favorite.id.toString(),
                            name = favorite.name,
                            country = "",
                            latitude = favorite.latitude,
                            longitude = favorite.longitude
                        )
                    },
                    onLocationSelected = { saved ->
                        weatherViewModel.setLocation(saved.latitude, saved.longitude, saved.name)
                        navController.popBackStack()
                    },
                    onAddLocation = { result ->
                        weatherViewModel.addFavorite(
                            result.name,
                            result.latitude,
                            result.longitude
                        )
                        weatherViewModel.setLocation(
                            result.latitude,
                            result.longitude,
                            result.name
                        )
                        navController.popBackStack()
                    },
                    onDeleteLocation = { saved ->
                        // Favourite ids come from the backend as ints; the sheet
                        // models them as strings. Anything that isn't a backend
                        // row (the active-location entry) has nothing to delete.
                        saved.id.toIntOrNull()?.let { weatherViewModel.removeFavorite(it) }
                    },
                    onUseCurrentLocation = {
                        weatherViewModel.loadWeatherForCurrentLocation()
                        navController.popBackStack()
                    },
                    onDismiss = { navController.popBackStack() }
                )
            }

            // ==================== Feature Screens ====================

            composable(Screen.Radar.route) {
                val activeLocation by weatherViewModel.location.collectAsState()
                val weather by weatherViewModel.state.collectAsState()

                val coordinates = activeLocation?.let { it.latitude to it.longitude }
                    ?: (weather as? WeatherState.Success)?.data?.location
                        ?.let { it.latitude to it.longitude }

                if (coordinates == null) {
                    // This used to fall back to hardcoded San Francisco
                    // coordinates, so a user whose location had not resolved was
                    // shown another city's radar as though it were their own.
                    LocationUnavailable(
                        title = "Radar",
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    RadarMapScreen(
                        latitude = coordinates.first,
                        longitude = coordinates.second,
                        onBack = { navController.popBackStack() }
                    )
                }
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
                val activeLocation by weatherViewModel.location.collectAsState()
                val weather by weatherViewModel.state.collectAsState()

                val coordinates = activeLocation?.let { it.latitude to it.longitude }
                    ?: (weather as? WeatherState.Success)?.data?.location
                        ?.let { it.latitude to it.longitude }

                // pollenData is left null so the screen fetches Open-Meteo itself.
                // WeatherViewModel.pollenState is the backend's PollenTodayResponse,
                // which reports a 0-5 severity index; PollenData is grains/m³ from
                // CAMS. There is no conversion between the two — the old code passed
                // the index off as a concentration, which read as a plausible number
                // that no instrument ever measured.
                PollenScreen(
                    pollenData = null,
                    onBack = { navController.popBackStack() },
                    latitude = coordinates?.first,
                    longitude = coordinates?.second
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

        if (mainTabs.any { it.screen.route == currentRoute }) {
            MainBottomBar(currentRoute = currentRoute, navController = navController)
        }
    }
}

@Composable
private fun MainBottomBar(
    currentRoute: String?,
    navController: NavHostController
) {
    NavigationBar(containerColor = Color(0xFF1A1A2E)) {
        mainTabs.forEach { tab ->
            val selected = currentRoute == tab.screen.route

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(tab.screen.route) {
                            // The tabs are siblings rather than a stack, so Home
                            // stays the single root. Without this, hopping between
                            // tabs piles them up and Back walks the user through
                            // every one on the way out.
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Color.White,
                    indicatorColor = Color(0xFF60A5FA).copy(alpha = 0.25f),
                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                    unselectedTextColor = Color.White.copy(alpha = 0.6f)
                )
            )
        }
    }
}

/**
 * Shown by destinations that cannot say anything truthful until the device's
 * location is known.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationUnavailable(
    title: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E)
                        )
                    )
                )
                .padding(padding)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Your location isn't available yet, so there's nothing to centre " +
                    "this on. Pull to refresh on the home screen once location is on.",
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
