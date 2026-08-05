package com.climaai.wear.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.climaai.wear.complication.ConditionComplicationService
import com.climaai.wear.complication.TemperatureComplicationService
import com.climaai.wear.tile.WeatherTileService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A reading something actually took — the watch's own fetch or the phone's
 * published one. Fields the source did not supply stay null — every surface
 * renders those as "--" rather than as a number.
 *
 * Temperatures are Celsius and wind is km/h, matching the phone app.
 */
data class WearWeatherData(
    val temperature: Int,
    val feelsLike: Int?,
    val condition: String,
    val conditionIcon: String,
    val high: Int?,
    val low: Int?,
    val humidity: Int?,
    val windSpeed: Int?,
    val location: String,
    val daily: List<WearDayForecast>,
    val observedAtMillis: Long
) {
    /**
     * A reading served from cache after a failed refresh is still true, but it
     * is no longer "now" — surfaces with room for it say when it was taken.
     */
    val isStale: Boolean
        get() = System.currentTimeMillis() - observedAtMillis >= FRESH_MS

    /** The reading's age in words, for the surfaces that show one past its fresh window. */
    fun updatedAgo(nowMillis: Long = System.currentTimeMillis()): String {
        val minutes = ((nowMillis - observedAtMillis) / 60_000L).coerceAtLeast(0)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            else -> "${minutes / 60}h ago"
        }
    }
}

/** One day of the forecast. [date] is the API's ISO date, labelled at render time. */
data class WearDayForecast(
    val date: String,
    val icon: String,
    val high: Int,
    val low: Int
)

/** What the live surfaces get. There is deliberately no sample fallback. */
sealed interface WearWeather {
    data class Available(val data: WearWeatherData) : WearWeather
    data class Unavailable(val reason: NoDataReason) : WearWeather
}

/**
 * Why there is nothing to show. [label] is short enough for a tile or a
 * complication; [description] is what a screen or a content description says.
 */
enum class NoDataReason(val label: String, val description: String) {
    LOCATION_PERMISSION("Location off", "Location permission is off"),
    LOCATION_UNKNOWN("No location", "Waiting for a location fix"),
    FETCH_FAILED("No data", "Could not reach the weather service")
}

/**
 * Weather for the watch, from whichever source has the youngest reading: the
 * one the phone published to the Data Layer ([PhoneWeatherListenerService]
 * keeps [PhoneWeatherStore] current) or the watch's own fetch. The fetch is
 * kept — not replaced — so the module still stands alone the way its manifest
 * claims to (`com.google.android.wearable.standalone`).
 */
object WearWeatherRepository {

    private const val TAG = "WearWeatherRepo"

    private const val PREFS_NAME = "wear_weather_cache"
    private const val KEY_READING = "reading_json"

    private val api = WearWeatherApi.create()
    private val gson = Gson()
    private val refreshLock = Mutex()

    /** Cache-first. Refreshes only when what we hold is too old to call current. */
    suspend fun getWeather(context: Context): WearWeather = withContext(Dispatchers.IO) {
        freshest(context, FRESH_MS)?.let { return@withContext WearWeather.Available(it) }

        // The tile and both complications wake on the same alarm; serialising
        // here means that burst costs one network call, not three.
        refreshLock.withLock {
            freshest(context, FRESH_MS)?.let { return@withLock WearWeather.Available(it) }

            when (val refreshed = fetch(context)) {
                is WearWeather.Available -> refreshed
                is WearWeather.Unavailable ->
                    // Falling back to a stale-but-still-honest reading beats a
                    // blank watch face when the watch is briefly off-network —
                    // and a phone reading is what carries a watch that has no
                    // location permission or network of its own at all.
                    freshest(context, MAX_AGE_MS)
                        ?.let { WearWeather.Available(it) }
                        ?: refreshed
            }
        }
    }

    /**
     * The youngest reading on hand within [maxAgeMs], from either source. Age,
     * not origin, is what ranks them: a watch fetch from this hour beats a
     * phone reading from last night, and the other way round.
     */
    private fun freshest(context: Context, maxAgeMs: Long): WearWeatherData? =
        listOfNotNull(cached(context, maxAgeMs), PhoneWeatherStore.reading(context, maxAgeMs))
            .maxByOrNull { it.observedAtMillis }

    private suspend fun fetch(context: Context): WearWeather {
        if (!WearLocationProvider.hasPermission(context)) {
            return WearWeather.Unavailable(NoDataReason.LOCATION_PERMISSION)
        }

        val place = WearLocationProvider.currentPlace(context)
            ?: return WearWeather.Unavailable(NoDataReason.LOCATION_UNKNOWN)

        val data = try {
            val response = api.getForecast(place.latitude, place.longitude)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                Log.w(TAG, "Open-Meteo returned ${response.code()}")
                return WearWeather.Unavailable(NoDataReason.FETCH_FAILED)
            }
            body.toWeatherData(place.name)
                ?: return WearWeather.Unavailable(NoDataReason.FETCH_FAILED)
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed", e)
            return WearWeather.Unavailable(NoDataReason.FETCH_FAILED)
        }

        store(context, data)
        notifySurfaces(context)
        return WearWeather.Available(data)
    }

    /** Null when the response carries no usable current reading. */
    private fun WearForecastResponse.toWeatherData(locationName: String): WearWeatherData? {
        val now = current ?: return null
        val temperature = now.temperature ?: return null
        val code = now.weatherCode ?: return null

        val days = daily?.let { d ->
            val dates = d.time ?: emptyList()
            dates.indices.mapNotNull { i ->
                val high = d.tempMax?.getOrNull(i) ?: return@mapNotNull null
                val low = d.tempMin?.getOrNull(i) ?: return@mapNotNull null
                WearDayForecast(
                    date = dates[i],
                    // A day with no code of its own gets the generic glyph; it
                    // must not borrow today's condition.
                    icon = WearWeatherCodes.icon(d.weatherCode?.getOrNull(i) ?: UNKNOWN_WEATHER_CODE),
                    high = high.roundToInt(),
                    low = low.roundToInt()
                )
            }
        } ?: emptyList()

        return WearWeatherData(
            temperature = temperature.roundToInt(),
            feelsLike = now.feelsLike?.roundToInt(),
            condition = WearWeatherCodes.description(code),
            conditionIcon = WearWeatherCodes.icon(code, isDay = (now.isDay ?: 1) == 1),
            high = days.firstOrNull()?.high,
            low = days.firstOrNull()?.low,
            humidity = now.humidity,
            windSpeed = now.windSpeed?.roundToInt(),
            location = locationName,
            daily = days,
            observedAtMillis = System.currentTimeMillis()
        )
    }

    /** The stored reading, or null when there is none younger than [maxAgeMs]. */
    private fun cached(context: Context, maxAgeMs: Long): WearWeatherData? {
        val json = prefs(context).getString(KEY_READING, null) ?: return null
        val data = try {
            gson.fromJson(json, WearWeatherData::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Discarding unreadable cached reading", e)
            null
        } ?: return null

        return data.takeIf { System.currentTimeMillis() - it.observedAtMillis < maxAgeMs }
    }

    private fun store(context: Context, data: WearWeatherData) {
        prefs(context).edit().putString(KEY_READING, gson.toJson(data)).apply()
    }

    /**
     * Push the fresh reading out. Without this the tile and complications sit on
     * their previous state — including the "--" they show before the user has
     * granted location — until their own refresh alarm fires up to 30 minutes
     * later.
     *
     * Internal so [PhoneWeatherListenerService] can do the same when a phone
     * reading lands.
     */
    internal fun notifySurfaces(context: Context) {
        try {
            TileService.getUpdater(context).requestUpdate(WeatherTileService::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Tile update request failed", e)
        }

        listOf(TemperatureComplicationService::class.java, ConditionComplicationService::class.java)
            .forEach { service ->
                try {
                    ComplicationDataSourceUpdateRequester
                        .create(context, ComponentName(context, service))
                        .requestUpdateAll()
                } catch (e: Exception) {
                    Log.w(TAG, "Complication update request failed for ${service.simpleName}", e)
                }
            }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/** Anything the mapper cannot attribute to a real WMO code. */
private const val UNKNOWN_WEATHER_CODE = -1

// Serve a cached reading younger than this without touching the network. It
// matches the tile's freshness interval and the complications' update period, so
// one refresh cycle costs one request rather than three.
private const val FRESH_MS = 30 * 60 * 1000L

// Past this, a cached reading is not worth showing at all: an hours-old
// temperature presented as "now" is the same lie as a hardcoded one.
private const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
