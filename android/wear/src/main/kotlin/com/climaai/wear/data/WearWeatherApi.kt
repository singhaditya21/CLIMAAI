package com.climaai.wear.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo — the same keyless API the phone app uses, so the watch can fetch
 * its own weather instead of waiting on a phone that may not be nearby.
 *
 * Only the fields the watch actually renders are requested: the phone's 16-day
 * hourly payload is several hundred kilobytes the watch would throw away.
 *
 * Values come back in the API defaults — Celsius and km/h — matching the phone.
 */
interface WearWeatherApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): Response<WearForecastResponse>

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/"

        private const val CURRENT_PARAMS =
            "temperature_2m,relative_humidity_2m,is_day,weather_code,wind_speed_10m"

        private const val DAILY_PARAMS =
            "weather_code,temperature_2m_max,temperature_2m_min"

        fun create(): WearWeatherApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WearWeatherApi::class.java)
        }
    }
}

// Every field is nullable on purpose. Gson leaves absent numbers at zero, and a
// zero degrees / zero percent reading is indistinguishable from a real one on a
// watch face — exactly the kind of invented value this data path must not
// produce. Nullable fields force each surface to decide what to show instead.

data class WearForecastResponse(
    @SerializedName("current") val current: WearCurrentDto?,
    @SerializedName("daily") val daily: WearDailyDto?
)

data class WearCurrentDto(
    @SerializedName("temperature_2m") val temperature: Double?,
    @SerializedName("relative_humidity_2m") val humidity: Int?,
    @SerializedName("is_day") val isDay: Int?,
    @SerializedName("weather_code") val weatherCode: Int?,
    @SerializedName("wind_speed_10m") val windSpeed: Double?
)

data class WearDailyDto(
    @SerializedName("time") val time: List<String>?,
    @SerializedName("weather_code") val weatherCode: List<Int>?,
    @SerializedName("temperature_2m_max") val tempMax: List<Double>?,
    @SerializedName("temperature_2m_min") val tempMin: List<Double>?
)

/**
 * WMO weather codes to the words and glyphs the watch shows. Kept in step with
 * the phone's WeatherCodeMapper; the wear module does not depend on :app.
 */
object WearWeatherCodes {

    fun description(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    fun icon(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1, 2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 61, 63, 65 -> "🌧️"
        56, 57, 66, 67 -> "🌨️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        80, 81, 82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "🌤️"
    }
}
