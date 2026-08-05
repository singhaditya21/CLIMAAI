package com.climaai.wear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Where the watch is, and what to call that place on screen. */
data class WearPlace(
    val latitude: Double,
    val longitude: Double,
    val name: String
)

/**
 * The watch's own location fix.
 *
 * Fused location is used rather than raw LocationManager because it is the only
 * provider that falls back to the tethered phone's position on watches without
 * their own GPS.
 */
object WearLocationProvider {

    private const val TAG = "WearLocation"

    // A fix this old is still good enough for weather — the watch has not left
    // the forecast cell in ten minutes — and reusing it keeps GPS powered down.
    private const val MAX_FIX_AGE_MS = 10 * 60 * 1000L

    // Short on purpose: a tile request that takes tens of seconds is treated as
    // hung, and the fallbacks below (the remembered place, then the cached
    // reading) are better than a blank tile.
    private const val FIX_TIMEOUT_MS = 8_000L

    private const val PREFS_NAME = "wear_location_cache"
    private const val KEY_LATITUDE = "latitude"
    private const val KEY_LONGITUDE = "longitude"
    private const val KEY_NAME = "name"
    private const val KEY_FIXED_AT = "fixed_at"

    // How long the last known place may stand in for a live fix. Android only
    // hands location to a background process with ACCESS_BACKGROUND_LOCATION,
    // which this app does not ask for, so the tile and the complications would
    // otherwise be stuck on "--" between app launches. Weather fetched for the
    // place the watch was in yesterday is real weather, and every surface names
    // that place — but a day is as far as that stays defensible.
    private const val MAX_REMEMBERED_PLACE_AGE_MS = 24 * 60 * 60 * 1000L

    /**
     * Background surfaces (the tile, the complications) cannot ask for the
     * permission, so they have to check it and degrade rather than assume.
     */
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The current place, the last one we knew of, or null when the watch has
     * never had a usable fix. Callers must have checked [hasPermission] first —
     * a remembered place is not a licence to keep using it once the user has
     * taken the permission away.
     */
    @Suppress("MissingPermission")
    suspend fun currentPlace(context: Context): WearPlace? = withContext(Dispatchers.IO) {
        val client = LocationServices.getFusedLocationProviderClient(context)

        val recent = runCatching { client.lastLocation.await() }
            .onFailure { Log.w(TAG, "lastLocation failed", it) }
            .getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.time < MAX_FIX_AGE_MS }

        val location = recent ?: requestFix(client)
            ?: return@withContext rememberedPlace(context)

        WearPlace(location.latitude, location.longitude, describe(context, location))
            .also { remember(context, it) }
    }

    @Suppress("MissingPermission")
    private suspend fun requestFix(client: FusedLocationProviderClient): Location? =
        // The duration below is the fused client's own budget; the outer timeout
        // is the backstop for a client that never calls back at all, which would
        // otherwise hang a tile request until the system killed it.
        withTimeoutOrNull(FIX_TIMEOUT_MS + 5_000L) {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(FIX_TIMEOUT_MS)
                .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
                .build()

            runCatching { client.getCurrentLocation(request, null).await() }
                .onFailure { Log.w(TAG, "getCurrentLocation failed", it) }
                .getOrNull()
        }

    /** Blocking — only called from [currentPlace]'s IO context. */
    private fun describe(context: Context, location: Location): String {
        if (!Geocoder.isPresent()) return coordinates(location)

        return try {
            // The callback overload needs API 33 and this module ships to API 30
            // watches, so the blocking call stays.
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()

            // Outside towns the geocoder routinely has no locality, and falling
            // straight through to the country would label the watch "France".
            address?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                ?: coordinates(location)
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed", e)
            coordinates(location)
        }
    }

    /** Last resort: the fix we actually have, never a stand-in place name. */
    private fun coordinates(location: Location): String =
        String.format(Locale.US, "%.2f, %.2f", location.latitude, location.longitude)

    private fun remember(context: Context, place: WearPlace) {
        prefs(context).edit()
            .putFloat(KEY_LATITUDE, place.latitude.toFloat())
            .putFloat(KEY_LONGITUDE, place.longitude.toFloat())
            .putString(KEY_NAME, place.name)
            .putLong(KEY_FIXED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun rememberedPlace(context: Context): WearPlace? {
        val prefs = prefs(context)
        val fixedAt = prefs.getLong(KEY_FIXED_AT, 0L)
        if (fixedAt == 0L || System.currentTimeMillis() - fixedAt > MAX_REMEMBERED_PLACE_AGE_MS) {
            return null
        }

        val latitude = prefs.getFloat(KEY_LATITUDE, Float.NaN)
        val longitude = prefs.getFloat(KEY_LONGITUDE, Float.NaN)
        val name = prefs.getString(KEY_NAME, null)
        if (latitude.isNaN() || longitude.isNaN() || name == null) return null

        return WearPlace(latitude.toDouble(), longitude.toDouble(), name)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
