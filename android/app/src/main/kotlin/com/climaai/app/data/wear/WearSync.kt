package com.climaai.app.data.wear

import android.content.Context
import android.util.Log
import com.climaai.app.data.WeatherResponse
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Publishes the essentials of the latest reading to the paired watch over the
 * Wearable Data Layer.
 *
 * Fire-and-forget by design: the watch is a bystander to the fetch, never a
 * participant in it. [publish] returns immediately, the write happens on a
 * background scope, and every failure mode — no watch paired, Play services
 * unavailable, a slow Bluetooth bridge — ends in a debug log the caller never
 * sees.
 */
object WearSync {

    private const val TAG = "WearSync"

    // The DataItem path and keys :wear's PhoneWeatherListenerService reads.
    // The two modules share no code, only this contract — keep them in step.
    private const val PATH = "/climaai/weather"
    private const val KEY_LOCATION = "location"
    private const val KEY_TEMP_C = "temp_c"
    private const val KEY_FEELS_LIKE_C = "feels_like_c"
    private const val KEY_CONDITION_CODE = "condition_code"
    private const val KEY_CONDITION_TEXT = "condition_text"
    private const val KEY_IS_DAY = "is_day"
    private const val KEY_HIGH_C = "high_c"
    private const val KEY_LOW_C = "low_c"
    private const val KEY_HUMIDITY_PCT = "humidity_pct"
    private const val KEY_WIND_KMH = "wind_kmh"
    private const val KEY_UPDATED_AT_MS = "updated_at_ms"

    // Nothing user-visible waits on this; the deadline only stops a phone with
    // no watch from keeping a delivery attempt alive in the background.
    private const val TIMEOUT_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Safe to call with no watch paired; the attempt dies quietly. */
    fun publish(context: Context, weather: WeatherResponse) {
        // The caller is typically a viewmodel; its context must not be captured
        // by a write that can outlive it.
        val appContext = context.applicationContext
        scope.launch {
            try {
                withTimeout(TIMEOUT_MS) {
                    Wearable.getDataClient(appContext)
                        .putDataItem(request(weather))
                        .await()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Wear publish skipped: ${e.message}")
            }
        }
    }

    private fun request(weather: WeatherResponse): PutDataRequest {
        val current = weather.current
        val today = weather.daily.firstOrNull()

        return PutDataMapRequest.create(PATH).apply {
            // Optional values are conveyed by absence, not by a sentinel a
            // watch face could mistake for a reading.
            weather.location.name?.let { dataMap.putString(KEY_LOCATION, it) }
            dataMap.putDouble(KEY_TEMP_C, current.temperature)
            dataMap.putDouble(KEY_FEELS_LIKE_C, current.feelsLike)
            dataMap.putInt(KEY_CONDITION_CODE, current.weatherCode)
            dataMap.putString(KEY_CONDITION_TEXT, current.weatherDescription)
            dataMap.putBoolean(KEY_IS_DAY, current.isDay)
            today?.let {
                dataMap.putDouble(KEY_HIGH_C, it.temperatureMax)
                dataMap.putDouble(KEY_LOW_C, it.temperatureMin)
            }
            dataMap.putInt(KEY_HUMIDITY_PCT, current.humidity)
            dataMap.putDouble(KEY_WIND_KMH, current.windSpeed)
            // The reading's own time, not the publish time: a cached response
            // relayed now is still a reading taken then, and the watch's
            // staleness cutoff must judge it by that.
            dataMap.putLong(KEY_UPDATED_AT_MS, current.timestamp.time)
        }.asPutDataRequest()
            // Weather is glanced at, not awaited — but without this the Data
            // Layer may batch the sync for up to half an hour, longer than the
            // reading stays current.
            .setUrgent()
    }
}
