package com.climaai.app.data.repository

import android.util.Log
import com.climaai.app.data.api.*
import com.climaai.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Weather data source using Open-Meteo API.
 * Free, no API key required.
 * 
 * Use as fallback when backend is unavailable, or as primary source
 * if operating in standalone mode.
 */
object OpenMeteoRepository {
    
    private const val TAG = "OpenMeteoRepository"
    private val api = OpenMeteoApi.create()
    
    /**
     * Fetch weather directly from Open-Meteo.
     */
    suspend fun getWeather(lat: Double, lon: Double): Result<WeatherResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getWeather(lat, lon)
            
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                Result.success(mapToWeatherResponse(data, lat, lon))
            } else {
                Log.e(TAG, "API error: ${response.code()}")
                Result.failure(Exception("Failed to fetch weather: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Map Open-Meteo response to app's WeatherResponse model.
     */
    private fun mapToWeatherResponse(
        data: OpenMeteoWeatherResponse,
        lat: Double,
        lon: Double
    ): WeatherResponse {
        val current = data.current ?: throw Exception("No current weather")
        val hourly = data.hourly
        val daily = data.daily
        
        val isDay = current.isDay == 1
        
        return WeatherResponse(
            location = LocationInfo(
                name = "Current Location", // Will be enriched by reverse geocoding
                region = "",
                country = "",
                latitude = lat,
                longitude = lon,
                timezone = data.timezone
            ),
            current = CurrentWeather(
                temperature = current.temperature,
                feelsLike = current.feelsLike,
                condition = WeatherCodeMapper.getDescription(current.weatherCode),
                conditionCode = current.weatherCode,
                icon = WeatherCodeMapper.getIcon(current.weatherCode, isDay),
                humidity = current.humidity,
                windSpeed = current.windSpeed,
                windDirection = current.windDirection,
                windGust = current.windGusts,
                pressure = current.pressure,
                visibility = (current.visibility ?: 10000.0) / 1000, // Convert to km
                uvIndex = current.uvIndex,
                cloudCover = current.cloudCover,
                dewPoint = current.dewPoint,
                isDay = isDay,
                lastUpdated = current.time
            ),
            hourly = hourly?.let { mapHourlyForecast(it) } ?: emptyList(),
            daily = daily?.let { mapDailyForecast(it) } ?: emptyList(),
            alerts = emptyList(),
            airQuality = null // Fetched separately from Air Quality API
        )
    }
    
    private fun mapHourlyForecast(hourly: OpenMeteoHourly): List<HourlyForecast> {
        return hourly.time.mapIndexed { index, time ->
            val isDay = hourly.isDay?.getOrNull(index) == 1
            HourlyForecast(
                time = time,
                temperature = hourly.temperature.getOrNull(index) ?: 0.0,
                feelsLike = hourly.feelsLike.getOrNull(index) ?: 0.0,
                condition = WeatherCodeMapper.getDescription(hourly.weatherCode.getOrNull(index) ?: 0),
                conditionCode = hourly.weatherCode.getOrNull(index) ?: 0,
                icon = WeatherCodeMapper.getIcon(hourly.weatherCode.getOrNull(index) ?: 0, isDay),
                humidity = hourly.humidity.getOrNull(index) ?: 0,
                precipProbability = hourly.precipProbability.getOrNull(index) ?: 0,
                precipitation = hourly.precipitation.getOrNull(index) ?: 0.0,
                windSpeed = null,
                windDirection = null,
                uvIndex = hourly.uvIndex?.getOrNull(index),
                isDay = isDay
            )
        }.take(48) // 48 hours
    }
    
    private fun mapDailyForecast(daily: OpenMeteoDaily): List<DailyForecast> {
        return daily.time.mapIndexed { index, date ->
            DailyForecast(
                date = date,
                tempMax = daily.tempMax.getOrNull(index) ?: 0.0,
                tempMin = daily.tempMin.getOrNull(index) ?: 0.0,
                condition = WeatherCodeMapper.getDescription(daily.weatherCode.getOrNull(index) ?: 0),
                conditionCode = daily.weatherCode.getOrNull(index) ?: 0,
                icon = WeatherCodeMapper.getIcon(daily.weatherCode.getOrNull(index) ?: 0, true),
                precipProbability = daily.precipProbabilityMax.getOrNull(index) ?: 0,
                precipSum = daily.precipSum.getOrNull(index) ?: 0.0,
                windSpeedMax = daily.windSpeedMax.getOrNull(index) ?: 0.0,
                uvIndexMax = daily.uvIndexMax.getOrNull(index) ?: 0.0,
                sunrise = daily.sunrise.getOrNull(index) ?: "",
                sunset = daily.sunset.getOrNull(index) ?: ""
            )
        }
    }
}
