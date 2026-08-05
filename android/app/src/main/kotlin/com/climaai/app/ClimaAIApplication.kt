package com.climaai.app

import android.app.Application
import com.climaai.app.billing.BillingManager
import com.climaai.app.service.NotificationService
import com.climaai.app.work.WorkScheduler

/**
 * Application class for ClimaAI.
 * Initializes global services on app start.
 */
class ClimaAIApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize billing
        val billingManager = BillingManager.getInstance(this)
        billingManager.startConnection()

        // Create the notification channels. This belongs here rather than in
        // MainActivity because WeatherWorker posts alerts on a schedule whether
        // or not the activity has ever been started, and from minSdk 26 the OS
        // drops any notification whose channel does not exist yet.
        NotificationService(this).initialize()

        // Schedule background weather refresh for widgets
        WorkScheduler.scheduleWeatherRefresh(this, intervalMinutes = 30)
    }
}
