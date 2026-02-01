package com.climaai.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.climaai.app.data.NotificationPrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Broadcast receiver to reschedule work after device boot.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Device booted, rescheduling work")
            
            // Use pending result to keep receiver alive during async work
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Reschedule weather refresh
                    WorkScheduler.scheduleWeatherRefresh(context, intervalMinutes = 30)
                    
                    // Reschedule daily summary if enabled
                    val notificationPrefs = NotificationPrefsManager(context)
                    val dailyEnabled = notificationPrefs.dailySummaryEnabled.first()
                    
                    if (dailyEnabled) {
                        val hour = notificationPrefs.dailySummaryHour.first()
                        WorkScheduler.scheduleDailySummary(context, hour)
                    }
                    
                    Log.d(TAG, "Work rescheduled successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reschedule work", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
