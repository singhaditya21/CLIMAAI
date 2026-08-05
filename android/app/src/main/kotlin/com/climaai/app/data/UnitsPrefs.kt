package com.climaai.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.unitsDataStore: DataStore<Preferences> by preferencesDataStore(name = "units_prefs")

/**
 * The unit temperatures are displayed in.
 *
 * Every source the app reads reports Celsius — Open-Meteo is asked for no
 * `temperature_unit`, and Celsius is its default — so Celsius is the wire format
 * throughout and conversion belongs at the point of display, not in the models.
 */
enum class TemperatureUnit(val id: String, val symbol: String) {
    CELSIUS("celsius", "°C"),
    FAHRENHEIT("fahrenheit", "°F");

    fun fromCelsius(celsius: Double): Double = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }

    companion object {
        fun fromId(id: String): TemperatureUnit = entries.find { it.id == id } ?: CELSIUS
    }
}

/**
 * Manages unit preferences using DataStore.
 */
class UnitsPrefs(private val context: Context) {

    companion object {
        private val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
    }

    val temperatureUnit: Flow<TemperatureUnit> = context.unitsDataStore.data.map { prefs ->
        TemperatureUnit.fromId(prefs[TEMPERATURE_UNIT] ?: TemperatureUnit.CELSIUS.id)
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.unitsDataStore.edit { prefs ->
            prefs[TEMPERATURE_UNIT] = unit.id
        }
    }
}
