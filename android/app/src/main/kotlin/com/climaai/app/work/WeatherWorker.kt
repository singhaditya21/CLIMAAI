package com.climaai.app.work

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.climaai.app.data.NotificationPrefsManager
import com.climaai.app.data.WeatherRepository
import com.climaai.app.data.WeatherResult
import com.climaai.app.data.cache.generateLocationKey
import com.climaai.app.service.NotificationService
import com.climaai.app.widget.SmallWeatherWidget
import com.climaai.app.widget.MediumWeatherWidget
import com.climaai.app.widget.LargeWeatherWidget
import com.climaai.app.widget.WidgetDataManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for background weather refresh.
 * Updates widgets and triggers notifications based on weather conditions.
 */
class WeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val TAG = "WeatherWorker"
        const val WORK_NAME = "weather_refresh"
        
        // Input data keys
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_FORCE_NOTIFICATION = "force_notification"
    }
    
    private val repository = WeatherRepository(applicationContext)
    private val notificationPrefs = NotificationPrefsManager(applicationContext)
    private val notificationService = NotificationService(applicationContext)
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting weather refresh work")
        
        try {
            // Get saved location from widget data or use default
            val widgetData = WidgetDataManager.getData(applicationContext)
            val lat = inputData.getDouble(KEY_LATITUDE, widgetData?.latitude ?: 37.7749)
            val lon = inputData.getDouble(KEY_LONGITUDE, widgetData?.longitude ?: -122.4194)
            val forceNotification = inputData.getBoolean(KEY_FORCE_NOTIFICATION, false)
            
            // Fetch weather (uses cache if recent)
            val result = repository.getWeather(lat, lon, forceRefresh = false)
            
            when (result) {
                is WeatherResult.Success -> {
                    val weather = result.data
                    
                    // Update widget data
                    WidgetDataManager.updateWidgets(
                        context = applicationContext,
                        weather = weather,
                        locationName = weather.location?.name ?: "Current Location",
                        latitude = lat,
                        longitude = lon
                    )
                    
                    // Update all widgets
                    SmallWeatherWidget().updateAll(applicationContext)
                    MediumWeatherWidget().updateAll(applicationContext)
                    LargeWeatherWidget().updateAll(applicationContext)
                    
                    Log.d(TAG, "Widgets updated successfully")
                    
                    // Check notification conditions
                    checkAndSendNotifications(weather, forceNotification)
                    
                    return Result.success()
                }
                is WeatherResult.Error -> {
                    Log.e(TAG, "Weather fetch failed: ${result.message}")
                    return Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            return Result.retry()
        }
    }
    
    private suspend fun checkAndSendNotifications(
        weather: com.climaai.app.data.WeatherResponse,
        forceNotification: Boolean
    ) {
        val prefs = notificationPrefs.getPreferencesSnapshot()
        
        // Check for rain in the next few hours
        if (prefs.rainAlertsEnabled) {
            val rainSoon = weather.hourly.take(3).any { hour ->
                hour.precipitationProbability > 60 || hour.weatherCode in 51..82
            }
            
            if (rainSoon && (forceNotification || notificationPrefs.canSendRainAlert())) {
                val rainIndex = weather.hourly.take(3).indexOfFirst {
                    it.precipitationProbability > 60 || it.weatherCode in 51..82
                }
                // indexOfFirst returns -1 when none match, and getOrNull(-1) is null.
                val rainHour = weather.hourly.getOrNull(rainIndex)

                if (rainHour != null) {
                    notificationService.sendRainAlert(
                        minutesUntilRain = rainIndex * 60,
                        intensity = when (rainHour.weatherCode) {
                            in 63..67, in 81..82 -> "heavy"
                            in 61..62, 80 -> "moderate"
                            else -> "light"
                        }
                    )
                    notificationPrefs.recordRainAlert()
                    Log.d(TAG, "Rain alert sent")
                }
            }
        }
        
        // Check for severe weather
        if (prefs.severeWeatherEnabled) {
            val severeCode = weather.current.weatherCode in 95..99
            
            if (severeCode && (forceNotification || notificationPrefs.canSendSevereAlert())) {
                notificationService.sendSevereWeatherAlert(
                    title = "⛈️ Thunderstorm Warning",
                    message = "Thunderstorm activity detected in your area. Seek shelter if outdoors.",
                    severity = "moderate"
                )
                notificationPrefs.recordSevereAlert()
                Log.d(TAG, "Severe weather alert sent")
            }
        }
        
        // Check for high UV
        if (prefs.uvAlertsEnabled) {
            val highUV = weather.current.uvIndex >= 8
            
            if (highUV && (forceNotification || notificationPrefs.canSendUVAlert())) {
                notificationService.sendUVWarning(
                    uvIndex = weather.current.uvIndex.toInt()
                )
                notificationPrefs.recordUVAlert()
                Log.d(TAG, "UV alert sent")
            }
        }
    }
}

/**
 * Worker for daily weather summary notification.
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val TAG = "DailySummaryWorker"
        const val WORK_NAME = "daily_summary"
    }
    
    private val repository = WeatherRepository(applicationContext)
    private val notificationPrefs = NotificationPrefsManager(applicationContext)
    private val notificationService = NotificationService(applicationContext)
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Sending daily summary")
        
        // Check if enabled
        val enabled = notificationPrefs.dailySummaryEnabled.first()
        if (!enabled) {
            Log.d(TAG, "Daily summary disabled, skipping")
            return Result.success()
        }
        
        try {
            val widgetData = WidgetDataManager.getData(applicationContext)
            val lat = widgetData?.latitude ?: 37.7749
            val lon = widgetData?.longitude ?: -122.4194
            
            val result = repository.getWeather(lat, lon, forceRefresh = true)
            
            when (result) {
                is WeatherResult.Success -> {
                    val weather = result.data
                    val today = weather.daily.firstOrNull()
                    
                    if (today != null) {
                        notificationService.sendDailyBriefing(
                            summary = "Today: ${weather.current.weatherDescription}",
                            highTemp = today.temperatureMax.toInt(),
                            lowTemp = today.temperatureMin.toInt(),
                            precipChance = today.precipitationProbability
                        )
                        Log.d(TAG, "Daily summary sent")
                    }
                    
                    return Result.success()
                }
                is WeatherResult.Error -> {
                    Log.e(TAG, "Failed to fetch weather for summary")
                    return Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Daily summary failed", e)
            return Result.retry()
        }
    }
}
