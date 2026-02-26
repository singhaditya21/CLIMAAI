package com.climaai.app.work

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Helper class to schedule and manage WorkManager jobs.
 */
object WorkScheduler {
    
    private const val TAG = "WorkScheduler"
    
    /**
     * Schedule periodic weather refresh for widget updates.
     * @param context Application context
     * @param intervalMinutes Refresh interval (15, 30, or 60)
     */
    fun scheduleWeatherRefresh(
        context: Context,
        intervalMinutes: Int = 30,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val inputData = workDataOf(
            WeatherWorker.KEY_LATITUDE to (latitude ?: 0.0),
            WeatherWorker.KEY_LONGITUDE to (longitude ?: 0.0)
        )
        
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(WeatherWorker.WORK_NAME)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WeatherWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        
        Log.d(TAG, "Scheduled weather refresh every $intervalMinutes minutes")
    }
    
    /**
     * Schedule daily summary notification at specified hour.
     * @param context Application context
     * @param hour Hour of day (0-23) for notification
     */
    fun scheduleDailySummary(context: Context, hour: Int) {
        // Calculate initial delay to the target hour
        val now = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            
            // If target time has passed today, schedule for tomorrow
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        val initialDelayMs = targetTime.timeInMillis - now.timeInMillis
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                30, TimeUnit.MINUTES
            )
            .addTag(DailySummaryWorker.WORK_NAME)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailySummaryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        
        Log.d(TAG, "Scheduled daily summary at $hour:00")
    }
    
    /**
     * Trigger an immediate weather refresh (one-time).
     */
    fun triggerImmediateRefresh(
        context: Context,
        latitude: Double,
        longitude: Double,
        forceNotification: Boolean = false
    ) {
        val inputData = workDataOf(
            WeatherWorker.KEY_LATITUDE to latitude,
            WeatherWorker.KEY_LONGITUDE to longitude,
            WeatherWorker.KEY_FORCE_NOTIFICATION to forceNotification
        )
        
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        
        WorkManager.getInstance(context).enqueue(request)
        
        Log.d(TAG, "Triggered immediate weather refresh")
    }
    
    /**
     * Cancel all scheduled work.
     */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
        Log.d(TAG, "Cancelled all work")
    }
    
    /**
     * Cancel weather refresh work.
     */
    fun cancelWeatherRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeatherWorker.WORK_NAME)
        Log.d(TAG, "Cancelled weather refresh work")
    }
    
    /**
     * Cancel daily summary work.
     */
    fun cancelDailySummary(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DailySummaryWorker.WORK_NAME)
        Log.d(TAG, "Cancelled daily summary work")
    }
    
    /**
     * Update location for weather refresh.
     */
    fun updateLocation(context: Context, latitude: Double, longitude: Double) {
        // Cancel existing and reschedule with new location
        scheduleWeatherRefresh(context, 30, latitude, longitude)
    }
}
