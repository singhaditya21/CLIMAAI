package com.climaai.app.location

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production-grade Location Manager with:
 * - Adaptive accuracy based on network conditions
 * - Location caching for offline use
 * - Freshness validation
 * - Timeout handling
 * - Network state awareness
 * - Graceful degradation
 * - Battery optimization
 */
class UltraLocationManager(private val context: Context) {

    companion object {
        private const val TAG = "UltraLocationManager"
        private const val PREFS_NAME = "climaai_location_cache"
        private const val KEY_CACHED_LAT = "cached_lat"
        private const val KEY_CACHED_LON = "cached_lon"
        private const val KEY_CACHED_TIMESTAMP = "cached_timestamp"
        private const val KEY_CACHED_NAME = "cached_name"
        private const val KEY_CACHED_ACCURACY = "cached_accuracy"
        
        // Configuration
        private const val MAX_LOCATION_AGE_MS = 5 * 60 * 1000L // 5 minutes
        private const val HIGH_ACCURACY_TIMEOUT_MS = 15_000L
        private const val BALANCED_ACCURACY_TIMEOUT_MS = 30_000L
        private const val MINIMUM_ACCURACY_METERS = 100f
        private const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L // 1 hour
    }

    // MARK: - Location Quality

    enum class LocationQuality(val displayName: String, val accuracyThreshold: Float) {
        GPS("GPS", 10f),           // < 10m
        WIFI("WiFi", 100f),        // 10-100m
        CELL("Cell Tower", 3000f), // 100-3000m
        CACHED("Cached", 5000f),   // From storage
        UNKNOWN("Unknown", 10000f);

        companion object {
            fun from(accuracy: Float): LocationQuality = when {
                accuracy < 10f -> GPS
                accuracy < 100f -> WIFI
                accuracy < 3000f -> CELL
                else -> UNKNOWN
            }
        }
    }

    // MARK: - Network State

    enum class NetworkState {
        WIFI, CELLULAR, OFFLINE, UNKNOWN
    }

    // MARK: - Location Error

    sealed class LocationError : Exception() {
        object PermissionDenied : LocationError()
        object LocationUnavailable : LocationError()
        object Timeout : LocationError()
        object NetworkUnavailable : LocationError()
        data class Unknown(override val message: String?) : LocationError()
    }

    // MARK: - Location Result

    data class LocationResult(
        val location: Location,
        val name: String?,
        val quality: LocationQuality,
        val isFromCache: Boolean
    )

    // MARK: - State Flows

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _locationName = MutableStateFlow("Loading...")
    val locationName: StateFlow<String> = _locationName.asStateFlow()

    private val _networkState = MutableStateFlow(NetworkState.UNKNOWN)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _locationQuality = MutableStateFlow(LocationQuality.UNKNOWN)
    val locationQuality: StateFlow<LocationQuality> = _locationQuality.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _error = MutableStateFlow<LocationError?>(null)
    val error: StateFlow<LocationError?> = _error.asStateFlow()

    // MARK: - Private Properties

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault())

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var locationCallback: LocationCallback? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // MARK: - Initialization

    init {
        setupNetworkMonitor()
        loadCachedLocation()
    }

    private fun setupNetworkMonitor() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkState()
            }

            override fun onLost(network: Network) {
                _networkState.value = NetworkState.OFFLINE
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateNetworkState()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }

        updateNetworkState()
    }

    private fun updateNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        _networkState.value = when {
            capabilities == null -> NetworkState.OFFLINE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.CELLULAR
            else -> NetworkState.UNKNOWN
        }
    }

    // MARK: - Permission Check

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasHighAccuracyPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // MARK: - Request Location (Suspend)

    /**
     * Request current location with adaptive accuracy based on network state.
     * Falls back to cached location if offline or on timeout.
     */
    suspend fun requestLocation(): LocationResult = withContext(Dispatchers.Main) {
        // Check offline mode first
        if (_networkState.value == NetworkState.OFFLINE) {
            val cached = getCachedLocation()
            if (cached != null) {
                _location.value = cached
                _locationQuality.value = LocationQuality.CACHED
                return@withContext LocationResult(
                    location = cached,
                    name = prefs.getString(KEY_CACHED_NAME, null),
                    quality = LocationQuality.CACHED,
                    isFromCache = true
                )
            }
            throw LocationError.NetworkUnavailable
        }

        // Check permission
        if (!hasLocationPermission()) {
            throw LocationError.PermissionDenied
        }

        _isLocating.value = true
        _error.value = null

        try {
            // Determine accuracy and timeout based on network
            val (priority, timeout) = when (_networkState.value) {
                NetworkState.WIFI -> Priority.PRIORITY_HIGH_ACCURACY to HIGH_ACCURACY_TIMEOUT_MS
                NetworkState.CELLULAR -> Priority.PRIORITY_BALANCED_POWER_ACCURACY to BALANCED_ACCURACY_TIMEOUT_MS
                else -> Priority.PRIORITY_LOW_POWER to BALANCED_ACCURACY_TIMEOUT_MS
            }

            val location = getLocationWithFallback(priority, timeout)
            
            _location.value = location
            _locationQuality.value = LocationQuality.from(location.accuracy)
            
            // Cache location
            cacheLocation(location)
            
            // Reverse geocode
            val name = reverseGeocodeSync(location)
            _locationName.value = name ?: formatCoordinates(location)
            
            LocationResult(
                location = location,
                name = name,
                quality = _locationQuality.value,
                isFromCache = false
            )
        } catch (e: Exception) {
            // Try cached location as fallback
            val cached = getCachedLocation()
            if (cached != null && !isLocationStale(cached)) {
                _location.value = cached
                _locationQuality.value = LocationQuality.CACHED
                LocationResult(
                    location = cached,
                    name = prefs.getString(KEY_CACHED_NAME, null),
                    quality = LocationQuality.CACHED,
                    isFromCache = true
                )
            } else {
                _error.value = when (e) {
                    is LocationError -> e
                    else -> LocationError.Unknown(e.message)
                }
                throw e
            }
        } finally {
            _isLocating.value = false
        }
    }

    private suspend fun getLocationWithFallback(priority: Int, timeoutMs: Long): Location {
        return try {
            getCurrentLocationWithTimeout(priority, timeoutMs)
        } catch (e: Exception) {
            Log.w(TAG, "Current location failed, trying last location", e)
            // Try last known location
            val lastLocation = getLastKnownLocation()
            if (lastLocation != null && !isLocationStale(lastLocation)) {
                lastLocation
            } else {
                throw LocationError.LocationUnavailable
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getCurrentLocationWithTimeout(priority: Int, timeoutMs: Long): Location =
        suspendCancellableCoroutine { continuation ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setDurationMillis(timeoutMs)
                .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MS)
                .build()

            val task = fusedLocationClient.getCurrentLocation(request, null)

            task.addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(location)
                } else {
                    continuation.resumeWithException(LocationError.LocationUnavailable)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "getCurrentLocation failed", e)
                continuation.resumeWithException(LocationError.Unknown(e.message))
            }

            // Timeout handling
            scope.launch {
                delay(timeoutMs + 1000) // Extra second buffer
                if (continuation.isActive) {
                    continuation.resumeWithException(LocationError.Timeout)
                }
            }
        }

    @Suppress("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { continuation ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(location)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "lastLocation failed", e)
                continuation.resume(null)
            }
    }

    // MARK: - Continuous Updates

    /**
     * Start continuous location updates for real-time tracking.
     */
    @Suppress("MissingPermission")
    fun startContinuousUpdates(
        onLocation: (LocationResult) -> Unit,
        onError: (LocationError) -> Unit
    ) {
        if (!hasLocationPermission()) {
            onError(LocationError.PermissionDenied)
            return
        }

        val request = LocationRequest.Builder(10_000L) // 10 seconds
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMinUpdateDistanceMeters(50f) // Minimum 50m movement
            .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MS)
            .build()

        locationCallback = object : LocationCallback() {
            // Must be the Play Services type. This class declares its own
            // LocationResult (see above), which shadows it and makes the
            // override silently fail to match.
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.locations.lastOrNull()?.let { location ->
                    _location.value = location
                    _locationQuality.value = LocationQuality.from(location.accuracy)
                    cacheLocation(location)

                    scope.launch {
                        val name = reverseGeocodeSync(location)
                        _locationName.value = name ?: formatCoordinates(location)

                        onLocation(
                            com.climaai.app.location.UltraLocationManager.LocationResult(
                                location = location,
                                name = name,
                                quality = _locationQuality.value,
                                isFromCache = false
                            )
                        )
                    }
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    onError(LocationError.LocationUnavailable)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper()
        )

        _isLocating.value = true
    }

    /**
     * Stop continuous location updates.
     */
    fun stopContinuousUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        _isLocating.value = false
    }

    // MARK: - Caching

    private fun cacheLocation(location: Location) {
        prefs.edit().apply {
            putFloat(KEY_CACHED_LAT, location.latitude.toFloat())
            putFloat(KEY_CACHED_LON, location.longitude.toFloat())
            putLong(KEY_CACHED_TIMESTAMP, System.currentTimeMillis())
            putFloat(KEY_CACHED_ACCURACY, location.accuracy)
            apply()
        }
    }

    private fun loadCachedLocation() {
        val cached = getCachedLocation()
        if (cached != null) {
            _location.value = cached
            _locationQuality.value = LocationQuality.CACHED
            _locationName.value = prefs.getString(KEY_CACHED_NAME, null)
                ?: formatCoordinates(cached)
        }
    }

    private fun getCachedLocation(): Location? {
        val timestamp = prefs.getLong(KEY_CACHED_TIMESTAMP, 0)
        if (timestamp == 0L) return null

        // Check if cache is too old
        if (System.currentTimeMillis() - timestamp > CACHE_MAX_AGE_MS) return null

        val lat = prefs.getFloat(KEY_CACHED_LAT, 0f)
        val lon = prefs.getFloat(KEY_CACHED_LON, 0f)
        val accuracy = prefs.getFloat(KEY_CACHED_ACCURACY, 5000f)

        return Location("cache").apply {
            latitude = lat.toDouble()
            longitude = lon.toDouble()
            this.accuracy = accuracy
            time = timestamp
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }

    private fun isLocationStale(location: Location): Boolean {
        val age = System.currentTimeMillis() - location.time
        return age > MAX_LOCATION_AGE_MS
    }

    // MARK: - Geocoding

    private suspend fun reverseGeocodeSync(location: Location): String? =
        withContext(Dispatchers.IO) {
            if (_networkState.value == NetworkState.OFFLINE) {
                return@withContext prefs.getString(KEY_CACHED_NAME, null)
            }

            try {
                @Suppress("DEPRECATION")
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(
                            location.latitude,
                            location.longitude,
                            1
                        ) { addresses ->
                            continuation.resume(addresses)
                        }
                    }
                } else {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                }

                addresses?.firstOrNull()?.let { address ->
                    val name = buildString {
                        address.locality?.let { append(it) }
                        address.countryName?.let {
                            if (isNotEmpty()) append(", ")
                            append(it)
                        }
                    }.ifEmpty { null }

                    name?.let {
                        prefs.edit().putString(KEY_CACHED_NAME, it).apply()
                    }

                    name
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reverse geocode failed", e)
                prefs.getString(KEY_CACHED_NAME, null)
            }
        }

    private fun formatCoordinates(location: Location): String {
        return String.format(
            Locale.US,
            "%.2f, %.2f",
            location.latitude,
            location.longitude
        )
    }

    // MARK: - Cleanup

    fun destroy() {
        stopContinuousUpdates()
        scope.cancel()
    }
}
