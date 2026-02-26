package com.climaai.app

import android.app.Application
import com.climaai.app.ads.AdManager
import com.climaai.app.billing.BillingManager
import com.climaai.app.work.WorkScheduler

/**
 * Application class for ClimaAI.
 * Initializes global services on app start.
 */
class ClimaAIApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob
        AdManager.initialize(this)
        
        // Initialize billing
        val billingManager = BillingManager.getInstance(this)
        billingManager.startConnection()
        
        // Schedule background weather refresh for widgets
        WorkScheduler.scheduleWeatherRefresh(this, intervalMinutes = 30)
    }
}
