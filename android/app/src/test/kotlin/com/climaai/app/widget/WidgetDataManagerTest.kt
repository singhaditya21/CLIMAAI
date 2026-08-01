package com.climaai.app.widget

import androidx.test.core.app.ApplicationProvider
import com.climaai.app.data.CurrentWeather
import com.climaai.app.data.DailyWeather
import com.climaai.app.data.Location
import com.climaai.app.data.WeatherResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Round-trips widget data through the real SharedPreferences.
 *
 * `getData` did not exist at all until recently, despite ExtraLargeWeatherWidget
 * calling it — the app could not compile. These tests pin the contract between
 * what `updateWidgets` writes and what `getData` reads back, since the two are
 * only coupled by string keys.
 */
@RunWith(RobolectricTestRunner::class)
// Stock Application: the app's own schedules WorkManager on startup, which
// cannot initialise under Robolectric and is irrelevant to widget storage.
@Config(application = android.app.Application::class)
class WidgetDataManagerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun current(
        temperature: Double = 18.4,
        weatherCode: Int = 3,
        description: String = "Overcast",
    ) = CurrentWeather(
        temperature = temperature,
        feelsLike = temperature - 0.5,
        humidity = 61,
        windSpeed = 12.3,
        windDirection = 210,
        precipitation = 0.0,
        weatherCode = weatherCode,
        weatherDescription = description,
        cloudCover = 75,
        pressure = 1013.2,
        visibility = 10000.0,
        uvIndex = 4.2,
        isDay = true,
        timestamp = Date(),
    )

    private fun day(date: String, high: Double, low: Double, code: Int = 0) = DailyWeather(
        date = date,
        temperatureMax = high,
        temperatureMin = low,
        sunrise = "2026-07-31T05:20",
        sunset = "2026-07-31T20:45",
        precipitationSum = 1.2,
        precipitationProbability = 30,
        weatherCode = code,
        weatherDescription = "Clear sky",
        windSpeedMax = 18.0,
        windDirection = 220,
        uvIndexMax = 5.5,
    )

    private fun response(daily: List<DailyWeather>) = WeatherResponse(
        current = current(),
        hourly = emptyList(),
        daily = daily,
        airQuality = null,
        location = Location(latitude = 37.7749, longitude = -122.4194, elevation = null),
        timezone = "UTC",
    )

    @Test
    fun `getData returns placeholders before anything has been stored`() {
        WidgetDataManager.clearData(context)

        val data = WidgetDataManager.getData(context)

        // A widget added before the first refresh must still render.
        assertEquals("—", data.locationName)
        assertEquals(0, data.currentTemp)
        assertTrue(data.dailyForecast.isEmpty())
        assertEquals("—", data.lastUpdated)
    }

    @Test
    fun `current conditions round-trip`() {
        WidgetDataManager.clearData(context)

        WidgetDataManager.updateWidgets(
            context = context,
            weather = response(listOf(day("2026-07-31", 22.5, 13.1))),
            locationName = "San Francisco",
            latitude = 37.7749,
            longitude = -122.4194,
        )
        val data = WidgetDataManager.getData(context)

        assertEquals("San Francisco", data.locationName)
        assertEquals(18, data.currentTemp)          // 18.4 rounds to 18
        assertEquals("Overcast", data.condition)
        assertEquals(61, data.humidity)
        assertEquals(12, data.windSpeed)            // 12.3 rounds to 12
    }

    @Test
    fun `coordinates round-trip so background refresh can reuse the last location`() {
        WidgetDataManager.clearData(context)

        WidgetDataManager.updateWidgets(
            context = context,
            weather = response(listOf(day("2026-07-31", 22.5, 13.1))),
            locationName = "San Francisco",
            latitude = 37.7749,
            longitude = -122.4194,
        )
        val data = WidgetDataManager.getData(context)

        // Stored as Float, so compare with tolerance rather than equality.
        assertEquals(37.7749, data.latitude!!, 0.001)
        assertEquals(-122.4194, data.longitude!!, 0.001)
    }

    @Test
    fun `coordinates are null when never supplied`() {
        WidgetDataManager.clearData(context)

        WidgetDataManager.updateWidgets(
            context = context,
            weather = response(listOf(day("2026-07-31", 22.5, 13.1))),
            locationName = "Nowhere",
        )
        val data = WidgetDataManager.getData(context)

        assertEquals(null, data.latitude)
        assertEquals(null, data.longitude)
    }

    @Test
    fun `daily forecast round-trips and is capped at seven days`() {
        WidgetDataManager.clearData(context)
        val tenDays = (1..10).map { day("2026-08-%02d".format(it), 20.0 + it, 10.0 + it) }

        WidgetDataManager.updateWidgets(context, response(tenDays), "Somewhere")
        val data = WidgetDataManager.getData(context)

        assertEquals(7, data.dailyForecast.size)
        assertEquals(21, data.dailyForecast[0].high)
        assertEquals(11, data.dailyForecast[0].low)
    }

    @Test
    fun `first forecast day is labelled Today and the rest get weekday names`() {
        WidgetDataManager.clearData(context)
        val days = listOf(
            day("2026-08-01", 22.0, 12.0),
            day("2026-08-02", 23.0, 13.0),
            day("2026-08-03", 24.0, 14.0),
        )

        WidgetDataManager.updateWidgets(context, response(days), "Somewhere")
        val labels = WidgetDataManager.getData(context).dailyForecast.map { it.dayName }

        assertEquals("Today", labels[0])
        // Real weekday names, not the placeholder.
        assertTrue("expected a weekday, got ${labels[1]}", labels[1] != "—")
        assertTrue("expected a weekday, got ${labels[2]}", labels[2] != "—")
    }

    @Test
    fun `forecast spanning a month boundary parses`() {
        // The pollen service had a month-end date bug; this guards the same shape
        // in the widget path.
        WidgetDataManager.clearData(context)
        val days = listOf(
            day("2026-07-30", 22.0, 12.0),
            day("2026-07-31", 23.0, 13.0),
            day("2026-08-01", 24.0, 14.0),
            day("2026-08-02", 25.0, 15.0),
        )

        WidgetDataManager.updateWidgets(context, response(days), "Somewhere")
        val forecast = WidgetDataManager.getData(context).dailyForecast

        assertEquals(4, forecast.size)
        assertTrue(forecast.none { it.dayName == "—" })
    }

    @Test
    fun `weather codes map to distinct icons`() {
        WidgetDataManager.clearData(context)
        val days = listOf(
            day("2026-08-01", 22.0, 12.0, code = 0),   // clear
            day("2026-08-02", 22.0, 12.0, code = 3),   // overcast
            day("2026-08-03", 22.0, 12.0, code = 61),  // rain
            day("2026-08-04", 22.0, 12.0, code = 71),  // snow
            day("2026-08-05", 22.0, 12.0, code = 95),  // thunderstorm
        )

        WidgetDataManager.updateWidgets(context, response(days), "Somewhere")
        val icons = WidgetDataManager.getData(context).dailyForecast.map { it.icon }

        assertEquals(5, icons.size)
        assertTrue("icons should not all be identical: $icons", icons.toSet().size > 1)
        assertTrue("no icon should be blank", icons.none { it.isBlank() })
    }

    @Test
    fun `a later update replaces the previous one`() {
        WidgetDataManager.clearData(context)

        WidgetDataManager.updateWidgets(
            context, response(listOf(day("2026-08-01", 22.0, 12.0))), "First"
        )
        WidgetDataManager.updateWidgets(
            context, response(listOf(day("2026-08-01", 30.0, 20.0))), "Second"
        )
        val data = WidgetDataManager.getData(context)

        assertEquals("Second", data.locationName)
        assertEquals(30, data.dailyForecast[0].high)
    }
}
