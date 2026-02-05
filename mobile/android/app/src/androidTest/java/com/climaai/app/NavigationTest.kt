package com.climaai.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.climaai.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @Test
    fun screenRoutesAreCorrect() {
        assertEquals("onboarding", Screen.Onboarding.route)
        assertEquals("login", Screen.Login.route)
        assertEquals("register", Screen.Register.route)
        assertEquals("home", Screen.Home.route)
        assertEquals("forecast", Screen.Forecast.route)
        assertEquals("ai_insights", Screen.AIInsights.route)
        assertEquals("settings", Screen.Settings.route)
        assertEquals("radar", Screen.Radar.route)
        assertEquals("air_quality", Screen.AirQuality.route)
        assertEquals("pollen", Screen.Pollen.route)
        assertEquals("paywall", Screen.Paywall.route)
    }
}
