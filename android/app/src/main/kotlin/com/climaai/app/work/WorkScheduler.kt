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

    /** Refresh interval used when a caller has no opinion. */
    const val DEFAULT_INTERVAL_MINUTES = 30

    /**
     * Schedule periodic weather refresh for widget updates.
     * @param context Application context
     * @param intervalMinutes Refresh interval (15, 30, or 60)
     *
     * Deliberately carries no coordinates. A PeriodicWorkRequest keeps the input
     * data it was built with for the life of the schedule, so a location baked in
     * here would go on being used long after the user had left it; the worker
     * resolves the location per run instead. The version that did pass
     * coordinates wrote `latitude ?: 0.0`, which put the key in the input data
     * with a value of 0.0 whenever the caller had none — making the worker's own
     * fallback unreachable and sending every refresh to the Gulf of Guinea.
     */
    fun scheduleWeatherRefresh(
        context: Context,
        intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WeatherWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(constraints)
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
     * Trigger an immediate weather refresh (one-time) for a location the caller
     * knows.
     *
     * The app should call this whenever it resolves or changes location: the
     * periodic schedule carries none, and the worker's own fallback can only ever
     * be as fresh as the last fix the location manager cached.
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

        // REPLACE: these coordinates are newer than whatever a queued refresh is
        // carrying, and a refresh of the place the user just left is wasted work.
        WorkManager.getInstance(context).enqueueUniqueWork(
            WeatherWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )

        Log.d(TAG, "Triggered immediate weather refresh")
    }

    /**
     * Refresh once for whatever location the worker can resolve for itself.
     *
     * Used by the widgets, which can tell their reading has aged out but have no
     * idea where it should come from.
     */
    fun requestWidgetRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setConstraints(constraints)
            .build()

        // KEEP: every widget on the home screen redraws at once, and each must
        // not cancel the fetch the previous one just started.
        WorkManager.getInstance(context).enqueueUniqueWork(
            WeatherWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )

        Log.d(TAG, "Requested widget refresh")
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
}
