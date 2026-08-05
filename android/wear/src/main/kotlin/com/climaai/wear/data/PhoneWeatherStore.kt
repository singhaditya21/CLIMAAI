package com.climaai.wear.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson

/**
 * The last reading the phone published, kept in prefs so the tile, the
 * complications and the screens can read it long after the delivering
 * [PhoneWeatherListenerService] instance is gone.
 *
 * Kept apart from the repository's own fetch cache on purpose: the two are
 * different claims — "the watch measured this" versus "the phone said this" —
 * and [WearWeatherRepository] arbitrates between them by age.
 */
object PhoneWeatherStore {

    private const val TAG = "PhoneWeatherStore"

    private const val PREFS_NAME = "phone_weather_store"
    private const val KEY_READING = "reading_json"

    private val gson = Gson()

    fun store(context: Context, data: WearWeatherData) {
        prefs(context).edit().putString(KEY_READING, gson.toJson(data)).apply()
    }

    /** The stored phone reading, or null when there is none younger than [maxAgeMs]. */
    fun reading(context: Context, maxAgeMs: Long): WearWeatherData? {
        val json = prefs(context).getString(KEY_READING, null) ?: return null
        val data = try {
            gson.fromJson(json, WearWeatherData::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Discarding unreadable phone reading", e)
            null
        } ?: return null

        return data.takeIf { System.currentTimeMillis() - it.observedAtMillis < maxAgeMs }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
