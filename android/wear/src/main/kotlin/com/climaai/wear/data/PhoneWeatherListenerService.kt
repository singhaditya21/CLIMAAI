package com.climaai.wear.data

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlin.math.roundToInt

/**
 * Receives the reading the phone's WearSync publishes to the Data Layer and
 * persists it in [PhoneWeatherStore].
 *
 * Manifest-registered: Play services starts this on a matching DataItem change
 * whether or not anything of ours is running, which is what lets a phone
 * refresh land on the watch face while the watch app has never been opened.
 */
class PhoneWeatherListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != PATH) continue

            val reading = DataMapItem.fromDataItem(event.dataItem).dataMap.toReading()
            if (reading == null) {
                // A payload missing its essentials is a contract drift between
                // the modules, not something to render around.
                Log.w(TAG, "Discarding phone reading with missing essentials")
                continue
            }

            PhoneWeatherStore.store(this, reading)
            // Same reason the repository pushes after its own fetch: without
            // this the tile and complications sit on their previous state until
            // their refresh alarm fires up to 30 minutes later.
            WearWeatherRepository.notifySurfaces(this)
        }
    }

    /** Null when the payload lacks the values nothing may be invented for. */
    private fun DataMap.toReading(): WearWeatherData? {
        if (!containsKey(KEY_TEMP_C) || !containsKey(KEY_CONDITION_CODE) || !containsKey(KEY_UPDATED_AT_MS)) {
            return null
        }

        val code = getInt(KEY_CONDITION_CODE)
        val isDay = getBoolean(KEY_IS_DAY, true)

        return WearWeatherData(
            temperature = getDouble(KEY_TEMP_C).roundToInt(),
            feelsLike = doubleOrNull(KEY_FEELS_LIKE_C)?.roundToInt(),
            // The phone's wording when it sent one; our own mapping of the code
            // otherwise. Both describe the same measured condition.
            condition = getString(KEY_CONDITION_TEXT)?.takeIf { it.isNotBlank() }
                ?: WearWeatherCodes.description(code),
            conditionIcon = WearWeatherCodes.icon(code, isDay),
            high = doubleOrNull(KEY_HIGH_C)?.roundToInt(),
            low = doubleOrNull(KEY_LOW_C)?.roundToInt(),
            humidity = if (containsKey(KEY_HUMIDITY_PCT)) getInt(KEY_HUMIDITY_PCT) else null,
            windSpeed = doubleOrNull(KEY_WIND_KMH)?.roundToInt(),
            // An absent name renders as nothing — the surfaces have no honest
            // stand-in for a place.
            location = getString(KEY_LOCATION).orEmpty(),
            // The phone sends today's essentials only; the 7-day forecast
            // remains a watch-fetch feature and its absence hides the chip.
            daily = emptyList(),
            observedAtMillis = getLong(KEY_UPDATED_AT_MS)
        )
    }

    /** DataMap's getDouble defaults an absent key to 0.0 — a fake reading, hence this. */
    private fun DataMap.doubleOrNull(key: String): Double? =
        if (containsKey(key)) getDouble(key) else null

    private companion object {
        const val TAG = "PhoneWeatherListener"

        // The DataItem path and keys :app's WearSync writes. The two modules
        // share no code, only this contract — keep them in step.
        const val PATH = "/climaai/weather"
        const val KEY_LOCATION = "location"
        const val KEY_TEMP_C = "temp_c"
        const val KEY_FEELS_LIKE_C = "feels_like_c"
        const val KEY_CONDITION_CODE = "condition_code"
        const val KEY_CONDITION_TEXT = "condition_text"
        const val KEY_IS_DAY = "is_day"
        const val KEY_HIGH_C = "high_c"
        const val KEY_LOW_C = "low_c"
        const val KEY_HUMIDITY_PCT = "humidity_pct"
        const val KEY_WIND_KMH = "wind_kmh"
        const val KEY_UPDATED_AT_MS = "updated_at_ms"
    }
}
