package com.climaai.wear.data.api

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * Open-Meteo API - Free weather data.
 */
interface OpenMeteoApi {

    @Headers("User-Agent: ClimaAI-WearOS/1.0")
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1
    ): Response<OpenMeteoWeatherResponse>

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        const val CURRENT_PARAMS = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,is_day"

        const val DAILY_PARAMS = "temperature_2m_max,temperature_2m_min"

        fun create(): OpenMeteoApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenMeteoApi::class.java)
        }
    }
}

/**
 * Nominatim Geocoding API - Free location search.
 */
interface NominatimApi {

    @Headers("User-Agent: ClimaAI-WearOS/1.0")
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1
    ): Response<NominatimResult>

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/"

        fun create(): NominatimApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NominatimApi::class.java)
        }
    }
}
