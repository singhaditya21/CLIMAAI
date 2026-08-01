package com.climaai.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.climaai.app.MainActivity
import com.climaai.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Notification types for the app
 */
enum class NotificationType(
    val channelId: String,
    val channelName: String,
    val importance: Int
) {
    WEATHER_ALERT(
        "weather_alerts",
        "Weather Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ),
    DAILY_BRIEFING(
        "daily_briefing",
        "Daily Weather Briefing",
        NotificationManager.IMPORTANCE_DEFAULT
    ),
    RAIN_ALERT(
        "rain_alerts",
        "Rain Alerts",
        NotificationManager.IMPORTANCE_HIGH
    ),
    UV_WARNING(
        "uv_warnings",
        "UV Warnings",
        NotificationManager.IMPORTANCE_DEFAULT
    ),
    POLLEN_ALERT(
        "pollen_alerts",
        "Pollen Alerts",
        NotificationManager.IMPORTANCE_DEFAULT
    )
}

/**
 * Service for managing local notifications
 */
class NotificationService(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Initialize notification channels
     */
    fun initialize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationType.values().forEach { type ->
                val channel = NotificationChannel(
                    type.channelId,
                    type.channelName,
                    type.importance
                ).apply {
                    description = "Notifications for ${type.channelName.lowercase()}"
                    enableVibration(type.importance >= NotificationManager.IMPORTANCE_HIGH)
                    enableLights(true)
                }
                
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Check if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    /**
     * Send severe weather alert
     */
    fun sendSevereWeatherAlert(
        title: String,
        message: String,
        severity: String = "moderate"
    ) {
        val priority = when (severity.lowercase()) {
            "extreme", "severe" -> NotificationCompat.PRIORITY_MAX
            "moderate" -> NotificationCompat.PRIORITY_HIGH
            else -> NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = createNotification(
            channelId = NotificationType.WEATHER_ALERT.channelId,
            title = "⚠️ $title",
            message = message,
            priority = priority
        )

        showNotification(NotificationType.WEATHER_ALERT.ordinal * 100 + 1, notification)
    }

    /**
     * Send rain alert
     */
    fun sendRainAlert(minutesUntilRain: Int, intensity: String = "light") {
        val emoji = when (intensity.lowercase()) {
            "heavy" -> "🌧️"
            "moderate" -> "🌦️"
            else -> "🌂"
        }

        val title = "$emoji Rain Starting Soon"
        val message = when {
            minutesUntilRain <= 5 -> "Rain expected in the next 5 minutes"
            minutesUntilRain <= 15 -> "Rain expected in about $minutesUntilRain minutes"
            else -> "Rain expected in the next hour"
        }

        val notification = createNotification(
            channelId = NotificationType.RAIN_ALERT.channelId,
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_HIGH
        )

        showNotification(NotificationType.RAIN_ALERT.ordinal * 100 + 1, notification)
    }

    /**
     * Send UV warning
     */
    fun sendUVWarning(uvIndex: Int) {
        val (title, message) = when {
            uvIndex >= 11 -> Pair(
                "🔴 Extreme UV Alert",
                "UV index is $uvIndex. Avoid sun exposure, use SPF 50+ if outside."
            )
            uvIndex >= 8 -> Pair(
                "🟠 Very High UV Warning",
                "UV index is $uvIndex. Minimize sun exposure, wear protective clothing."
            )
            uvIndex >= 6 -> Pair(
                "🟡 High UV Alert",
                "UV index is $uvIndex. Use SPF 30+ sunscreen, wear a hat."
            )
            else -> return // No warning needed
        }

        val notification = createNotification(
            channelId = NotificationType.UV_WARNING.channelId,
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        showNotification(NotificationType.UV_WARNING.ordinal * 100 + 1, notification)
    }

    /**
     * Send pollen alert
     */
    fun sendPollenAlert(level: Int, type: String = "pollen") {
        if (level < 3) return // Only alert for moderate and above

        val (emoji, severity) = when {
            level >= 5 -> Pair("🔴", "Very High")
            level >= 4 -> Pair("🟠", "High")
            else -> Pair("🟡", "Moderate")
        }

        val notification = createNotification(
            channelId = NotificationType.POLLEN_ALERT.channelId,
            title = "$emoji $severity Pollen Alert",
            message = "${type.replaceFirstChar { it.uppercase() }} levels are $severity today. Consider taking antihistamines.",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        showNotification(NotificationType.POLLEN_ALERT.ordinal * 100 + 1, notification)
    }

    // scheduleMorningBriefing() was removed here. It was never called, and it
    // scheduled by holding a coroutine open with delay() — which does not
    // survive process death, as its own comment acknowledged. DailySummaryWorker
    // (registered in WorkScheduler) does this properly through WorkManager.

    /**
     * Send daily briefing notification
     */
    /**
     * Send the morning briefing.
     *
     * Takes the day's actual figures. It previously took no arguments and sent a
     * hardcoded "Partly cloudy, high of 72°F" regardless of the real forecast,
     * while WeatherWorker was already fetching the real numbers to pass in.
     */
    fun sendDailyBriefing(
        summary: String,
        highTemp: Int,
        lowTemp: Int,
        precipChance: Int
    ) {
        val rain = if (precipChance >= 30) " · $precipChance% chance of rain" else ""
        val notification = createNotification(
            channelId = NotificationType.DAILY_BRIEFING.channelId,
            title = "☀️ Good Morning!",
            message = "$summary · High $highTemp° / Low $lowTemp°$rain",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        showNotification(NotificationType.DAILY_BRIEFING.ordinal * 100 + 1, notification)
    }

    /**
     * Create a notification
     */
    private fun createNotification(
        channelId: String,
        title: String,
        message: String,
        priority: Int
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Replace with app icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    /**
     * Show a notification
     */
    private fun showNotification(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        
        notificationManager.notify(id, notification)
    }

    /**
     * Cancel a notification
     */
    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
