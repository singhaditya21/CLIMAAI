package com.climaai.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.climaai.app.data.NotificationPrefsManager
import com.climaai.app.data.UnitsPrefs
import com.climaai.app.data.WeatherRepository
import com.climaai.app.data.WeatherResult
import com.climaai.app.service.NotificationService
import com.climaai.app.widget.WidgetDataManager
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * The last fix UltraLocationManager wrote to its cache, or null if it has never
 * resolved one.
 *
 * Read straight out of its SharedPreferences rather than through the manager:
 * constructing one registers a connectivity callback and a main-thread scope, and
 * asking it for a fresh fix from a background worker would need the background
 * location permission the app does not hold. The key names below are that class's
 * private constants — they are duplicated here, so they have to move together.
 */
private fun lastKnownFix(context: Context): Pair<Double, Double>? {
    val prefs = context.getSharedPreferences("climaai_location_cache", Context.MODE_PRIVATE)
    // The manager stamps a timestamp with every fix it caches. Zero means there
    // has never been one, and the coordinates would read back as its own 0f
    // default — which is a real place, in the Gulf of Guinea.
    if (prefs.getLong("cached_timestamp", 0L) == 0L) return null
    return prefs.getFloat("cached_lat", 0f).toDouble() to
        prefs.getFloat("cached_lon", 0f).toDouble()
}

/**
 * Where a background refresh should fetch for when it was not handed
 * coordinates: the app's own record of where the user is, falling back to the
 * location the last widget update was for.
 *
 * Null means we genuinely do not know, and the caller must skip the fetch. There
 * is deliberately no default location — weather for somewhere the user has never
 * been, shown under their location's name, is worse than no weather at all.
 */
private fun knownLocation(context: Context): Pair<Double, Double>? {
    lastKnownFix(context)?.let { return it }

    val widgetData = WidgetDataManager.getData(context)
    val lat = widgetData.latitude ?: return null
    val lon = widgetData.longitude ?: return null
    return lat to lon
}

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

        /** Unique name for the one-off refreshes enqueued by WorkScheduler. */
        const val IMMEDIATE_WORK_NAME = "weather_refresh_now"

        // Input data keys
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_FORCE_NOTIFICATION = "force_notification"

        // Channel and id for the notification expedited work shows on API 26-30.
        // Not in NotificationService's channel enum: those are alerts the user
        // opted into, this is plumbing they may silence wholesale.
        private const val REFRESH_CHANNEL_ID = "background_refresh"
        private const val REFRESH_CHANNEL_NAME = "Background refresh"
        private const val REFRESH_NOTIFICATION_ID = 990001
    }

    private val repository = WeatherRepository(applicationContext)
    private val notificationPrefs = NotificationPrefsManager(applicationContext)
    private val notificationService = NotificationService(applicationContext)

    /**
     * On API 26-30 WorkManager runs expedited work as a foreground service, and
     * CoroutineWorker's default implementation throws — which turned every
     * [WorkScheduler.triggerImmediateRefresh] on those releases into a crash.
     * Low importance: this is a refresh in progress, not something to buzz about.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Idempotent; settings the user changed on the channel are kept.
            manager.createNotificationChannel(
                NotificationChannel(
                    REFRESH_CHANNEL_ID,
                    REFRESH_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, REFRESH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Same stand-in as NotificationService
            .setContentTitle("Updating weather")
            .setOngoing(true)
            .build()

        return ForegroundInfo(REFRESH_NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting weather refresh work")

        try {
            val target = resolveLocation()
            if (target == null) {
                // Nothing to fetch for. Retrying would not help — only the app
                // resolving a location will — so this is a success with no work
                // done, and the widgets stay in their no-data state.
                Log.w(TAG, "No known location; skipping refresh")
                return Result.success()
            }
            val (lat, lon) = target
            val forceNotification = inputData.getBoolean(KEY_FORCE_NOTIFICATION, false)

            // Fetch weather (uses cache if recent)
            val result = repository.getWeather(lat, lon, forceRefresh = false)

            when (result) {
                is WeatherResult.Success -> {
                    val weather = result.data

                    // Stores the reading and repaints every widget — including
                    // the extra-large one, which the hand-rolled update list here
                    // left out — before this coroutine returns. A fire-and-forget
                    // repaint can outlive the process once doWork has finished.
                    WidgetDataManager.applyWeather(
                        context = applicationContext,
                        weather = weather,
                        locationName = weather.location.name ?: "Current Location",
                        latitude = lat,
                        longitude = lon
                    )

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

    /**
     * Coordinates for this run, most trustworthy first: the ones handed to it,
     * then whatever [knownLocation] can dig up.
     *
     * Input coordinates are only ever set on a one-off refresh, so they are as
     * fresh as the caller that asked for it. Absence is detected with NaN rather
     * than a plausible default, because `getDouble(key, fallback)` returns the
     * fallback only when the key is *missing* — a key present with a value of 0.0
     * is what made the previous fallback dead code.
     */
    private fun resolveLocation(): Pair<Double, Double>? {
        val lat = inputData.getDouble(KEY_LATITUDE, Double.NaN)
        val lon = inputData.getDouble(KEY_LONGITUDE, Double.NaN)
        if (!lat.isNaN() && !lon.isNaN()) return lat to lon

        return knownLocation(applicationContext)
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
            // Same rule as the refresh worker: no known location means no
            // briefing, rather than a briefing for San Francisco.
            val target = knownLocation(applicationContext)
            if (target == null) {
                Log.w(TAG, "No known location; skipping daily summary")
                return Result.success()
            }
            val (lat, lon) = target

            val result = repository.getWeather(lat, lon, forceRefresh = true)
            
            when (result) {
                is WeatherResult.Success -> {
                    val weather = result.data
                    val today = weather.daily.firstOrNull()

                    if (today != null) {
                        // The briefing prints a bare degree sign, so the numbers
                        // must be in the unit the user reads, not the Celsius the
                        // API reports in.
                        val unit = UnitsPrefs(applicationContext).temperatureUnit.first()
                        notificationService.sendDailyBriefing(
                            summary = "Today: ${weather.current.weatherDescription}",
                            highTemp = unit.fromCelsius(today.temperatureMax).roundToInt(),
                            lowTemp = unit.fromCelsius(today.temperatureMin).roundToInt(),
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
