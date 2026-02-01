package com.climaai.app.data.api

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo API - Free weather data, no API key required.
 * https://open-meteo.com/
 * 
 * Rate limit: 10,000 requests/day for non-commercial use.
 */
interface OpenMeteoApi {
    
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("daily") daily: String = DAILY_PARAMS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 16
    ): Response<OpenMeteoWeatherResponse>
    
    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        
        const val CURRENT_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature," +
            "is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover," +
            "pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
            "uv_index,visibility,dew_point_2m"
        
        const val HOURLY_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature," +
            "precipitation_probability,precipitation,rain,showers,snowfall,weather_code," +
            "cloud_cover,visibility,uv_index,is_day"
        
        const val DAILY_PARAMS = "weather_code,temperature_2m_max,temperature_2m_min," +
            "apparent_temperature_max,apparent_temperature_min,sunrise,sunset," +
            "uv_index_max,precipitation_sum,rain_sum,showers_sum,snowfall_sum," +
            "precipitation_probability_max,wind_speed_10m_max,wind_direction_10m_dominant"
        
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
 * Open-Meteo Air Quality API - Free pollen and AQI data.
 * https://open-meteo.com/en/docs/air-quality-api
 */
interface OpenMeteoAirQualityApi {
    
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_PARAMS,
        @Query("hourly") hourly: String = HOURLY_PARAMS,
        @Query("forecast_days") forecastDays: Int = 5
    ): Response<OpenMeteoAirQualityResponse>
    
    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"
        
        const val CURRENT_PARAMS = "us_aqi,pm10,pm2_5,carbon_monoxide,nitrogen_dioxide," +
            "sulphur_dioxide,ozone,uv_index,alder_pollen,birch_pollen,grass_pollen," +
            "mugwort_pollen,olive_pollen,ragweed_pollen"
        
        const val HOURLY_PARAMS = "pm10,pm2_5,us_aqi,uv_index,alder_pollen,birch_pollen," +
            "grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"
        
        fun create(): OpenMeteoAirQualityApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenMeteoAirQualityApi::class.java)
        }
    }
}

/**
 * Nominatim Geocoding API - Free location search, no API key.
 * https://nominatim.openstreetmap.org/
 * 
 * Rate limit: 1 request/second
 */
interface NominatimApi {
    
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 10,
        @Query("addressdetails") addressDetails: Int = 1
    ): Response<List<NominatimResult>>
    
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

/**
 * RainViewer API - Free radar data.
 * https://www.rainviewer.com/api.html
 * 
 * Rate limit: 1000 requests/minute
 */
interface RainViewerApi {
    
    @GET("public/weather-maps.json")
    suspend fun getRadarFrames(): Response<RainViewerMapsResponse>
    
    companion object {
        const val BASE_URL = "https://api.rainviewer.com/"
        
        // Tile URL format: https://tilecache.rainviewer.com/v2/radar/{timestamp}/256/{z}/{x}/{y}/2/1_1.png
        fun getRadarTileUrl(timestamp: Long, z: Int, x: Int, y: Int): String {
            return "https://tilecache.rainviewer.com/v2/radar/$timestamp/256/$z/$x/$y/2/1_1.png"
        }
        
        fun create(): RainViewerApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RainViewerApi::class.java)
        }
    }
}
