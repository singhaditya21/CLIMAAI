package com.climaai.app.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import com.climaai.app.data.HourlyWeather
import com.climaai.app.data.TemperatureUnit
import com.climaai.app.data.UnitsPrefs
import com.climaai.app.data.WeatherResponse
import com.climaai.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Stands in for any reading that has never been fetched. Deliberately not a
 * number: a widget with no data must not be mistakable for one reporting 20°
 * and clear skies, which is what the old defaults rendered as.
 */
internal const val NO_DATA = "—"

/** Muted line used where a description would go when there is no reading. */
internal const val NO_DATA_LABEL = "No data"

/**
 * Manages widget data updates from the main app
 */
object WidgetDataManager {

    private const val PREFS_NAME = "widget_data"

    /**
     * How old a stored reading may get before a widget render asks for a new one.
     * Matches the periodic refresh interval, so it only fires when the periodic
     * work has not actually run — Doze, a force-stop, work never rescheduled.
     */
    private const val STALE_AFTER_MS = 30 * 60 * 1000L

    /**
     * Floor on how often a render may ask for a fetch. Without it, a reading that
     * cannot be refreshed — no location known, no network — would have every
     * widget re-enqueue on every redraw for as long as it stayed stale.
     */
    private const val REFRESH_REQUEST_INTERVAL_MS = 5 * 60 * 1000L

    /**
     * Hourly readings kept for the large widget's strip. Five, because the labels
     * are now real clock times rather than "Now/1h/2h" and a sixth column does
     * not fit across the widget.
     */
    private const val HOURLY_SLOTS = 5

    /**
     * Update all widgets with new weather data.
     *
     * The repaint is fired and forgotten, which is fine from the foreground app
     * but not from a Worker — see [applyWeather].
     */
    fun updateWidgets(
        context: Context,
        weather: WeatherResponse,
        locationName: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        store(context, weather, locationName, latitude, longitude)
        CoroutineScope(Dispatchers.IO).launch { refreshWidgets(context) }
    }

    /**
     * Store a reading and repaint every widget, returning only once they have
     * been repainted.
     *
     * A Worker must use this rather than [updateWidgets]: once `doWork` returns,
     * the process can be killed, and a repaint launched into a free-floating
     * scope would go with it.
     */
    suspend fun applyWeather(
        context: Context,
        weather: WeatherResponse,
        locationName: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        store(context, weather, locationName, latitude, longitude)
        refreshWidgets(context)
    }

    private fun store(
        context: Context,
        weather: WeatherResponse,
        locationName: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        with(prefs.edit()) {
            // Coordinates, so background refreshes can reuse the last location
            // when none is supplied in their input data.
            latitude?.let { putFloat("latitude", it.toFloat()) }
            longitude?.let { putFloat("longitude", it.toFloat()) }
            putLong("last_updated", System.currentTimeMillis())

            // Daily forecast for the extra-large widget
            weather.daily.take(7).forEachIndexed { index, day ->
                putFloat("daily_${index}_high", day.temperatureMax.toFloat())
                putFloat("daily_${index}_low", day.temperatureMin.toFloat())
                putInt("daily_${index}_code", day.weatherCode)
                putString("daily_${index}_date", day.date)
            }
            putInt("daily_count", weather.daily.take(7).size)

            // Current weather
            putFloat("temperature", weather.current.temperature.toFloat())
            putFloat("feels_like", weather.current.feelsLike.toFloat())
            putInt("weather_code", weather.current.weatherCode)
            putString("weather_description", weather.current.weatherDescription)
            putInt("humidity", weather.current.humidity)
            putFloat("wind_speed", weather.current.windSpeed.toFloat())
            putFloat("uv_index", weather.current.uvIndex.toFloat())
            putString("location_name", locationName)

            // Air quality
            weather.airQuality?.let { aq ->
                putInt("aqi", aq.aqi)
                putString("aqi_category", aq.category)
            }

            // Hourly forecast for the large widget's strip
            val upcoming = upcomingHours(weather.hourly)
            upcoming.forEachIndexed { index, hourly ->
                putFloat("hourly_${index}_temp", hourly.temperature.toFloat())
                putInt("hourly_${index}_code", hourly.weatherCode)
                putLong("hourly_${index}_time", hourly.time.time)
            }
            // Without a count a reader cannot tell a stored hour from a leftover
            // of a previous, longer update.
            putInt("hourly_count", upcoming.size)

            apply()
        }
    }

    /**
     * Repaint every widget from whatever is currently stored.
     *
     * Each type is repainted independently so that a failure for one — none of
     * that type on the home screen, a host that has gone away — does not skip
     * the rest.
     */
    suspend fun refreshWidgets(context: Context) {
        val widgets: List<GlanceAppWidget> = listOf(
            SmallWeatherWidget(),
            MediumWeatherWidget(),
            LargeWeatherWidget(),
            ExtraLargeWeatherWidget()
        )
        widgets.forEach { widget ->
            runCatching { widget.updateAll(context) }
        }
    }

    /**
     * Ask for a background re-fetch when the stored reading has aged out.
     *
     * Widgets are only ever redrawn by something else — the launcher, the
     * periodic worker, the host after a reboot — and nothing in the foreground
     * app pushes to them. Without this a widget keeps showing whatever the last
     * successful background run left behind, for as long as that run stays the
     * most recent one.
     */
    fun requestRefreshIfStale(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        if (now - prefs.getLong("last_updated", 0L) < STALE_AFTER_MS) return
        if (now - prefs.getLong("last_refresh_requested", 0L) < REFRESH_REQUEST_INTERVAL_MS) return

        prefs.edit().putLong("last_refresh_requested", now).apply()
        WorkScheduler.requestWidgetRefresh(context)
    }

    /**
     * Read back everything the widgets need, from the same prefs [updateWidgets]
     * writes.
     *
     * Nothing here invents a reading. When no update has ever been stored,
     * [WidgetData.hasData] is false and the widgets render an explicit no-data
     * state — a temperature default of 20° and a clear-sky code are
     * indistinguishable from a real reading on a home screen, and someone
     * dresses for them.
     */
    fun getData(context: Context): WidgetData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val lastUpdatedMs = prefs.getLong("last_updated", 0L)
        val hasData = lastUpdatedMs != 0L

        // Stored readings are Celsius, the app's wire format; the user's display
        // unit is applied here, at the one point every widget reads through, so
        // a Fahrenheit user is never shown a Celsius number on the home screen.
        // runBlocking is the same trade WidgetPrefs.getWidgetConfig makes: a
        // widget render is not a composition and has no way to collect a Flow.
        val unit = runBlocking { UnitsPrefs(context).temperatureUnit.first() }

        val dailyCount = prefs.getInt("daily_count", 0)
        val forecast = (0 until dailyCount).map { index ->
            val code = prefs.getInt("daily_${index}_code", 0)
            DayForecast(
                dayName = dayLabel(prefs.getString("daily_${index}_date", null), index),
                icon = weatherEmoji(code),
                high = unit.displayed(prefs.getFloat("daily_${index}_high", 0f)),
                low = unit.displayed(prefs.getFloat("daily_${index}_low", 0f))
            )
        }

        val hourlyCount = prefs.getInt("hourly_count", 0)
        val hourly: List<HourForecast> = if (hourlyCount == 0) emptyList() else {
            val hourFormat = hourFormat(context)
            (0 until hourlyCount).map { index ->
                HourForecast(
                    // The real clock time the reading belongs to. The strip used
                    // to be labelled "Now/1h/2h…" over arithmetic on the current
                    // temperature, which said nothing about any actual hour.
                    label = hourFormat.format(Date(prefs.getLong("hourly_${index}_time", 0L))),
                    icon = weatherEmoji(prefs.getInt("hourly_${index}_code", 0)),
                    temp = unit.displayed(prefs.getFloat("hourly_${index}_temp", 0f))
                )
            }
        }

        // An insight describes a set of conditions, so it expires with them.
        val insightAt = prefs.getLong("ai_insight_at", 0L)
        val insight = prefs.getString("ai_insight", null)?.takeIf { insightAt >= lastUpdatedMs }

        val weatherCode = prefs.getInt("weather_code", 0)
        return WidgetData(
            hasData = hasData,
            locationName = prefs.getString("location_name", NO_DATA) ?: NO_DATA,
            currentTemp = unit.displayed(prefs.getFloat("temperature", 0f)),
            feelsLike = unit.displayed(prefs.getFloat("feels_like", 0f)),
            weatherIcon = if (hasData) weatherEmoji(weatherCode) else NO_DATA,
            condition = prefs.getString("weather_description", NO_DATA) ?: NO_DATA,
            high = forecast.firstOrNull()?.high,
            low = forecast.firstOrNull()?.low,
            humidity = prefs.getInt("humidity", 0),
            windSpeed = prefs.getFloat("wind_speed", 0f).roundToInt(),
            dailyForecast = forecast,
            hourlyForecast = hourly,
            aiInsight = insight,
            lastUpdated = if (lastUpdatedMs == 0L) NO_DATA else timeFormat().format(Date(lastUpdatedMs)),
            latitude = prefs.getFloat("latitude", Float.NaN).takeIf { !it.isNaN() }?.toDouble(),
            longitude = prefs.getFloat("longitude", Float.NaN).takeIf { !it.isNaN() }?.toDouble()
        )
    }

    /** A stored Celsius reading as the whole number the widgets print. */
    private fun TemperatureUnit.displayed(celsius: Float): Int =
        fromCelsius(celsius.toDouble()).roundToInt()

    // SimpleDateFormat is not thread safe and widgets render concurrently, so
    // these are built per call rather than shared.
    private fun timeFormat() = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** The device's own clock convention, so an hour label is never off by twelve. */
    private fun hourFormat(context: Context) = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "HH:mm" else "h a",
        Locale.getDefault()
    )

    /**
     * The next few hourly readings.
     *
     * Open-Meteo returns the whole day starting at midnight, so simply taking the
     * first entries would fill the strip with hours that have already passed. An
     * empty result means the response held nothing still ahead of us, and the
     * strip says so rather than showing yesterday.
     */
    private fun upcomingHours(hourly: List<HourlyWeather>): List<HourlyWeather> {
        // Keep the hour already in progress: its reading is still the current one.
        val from = System.currentTimeMillis() - 60 * 60 * 1000L
        return hourly.filter { it.time.time >= from }.take(HOURLY_SLOTS)
    }

    /** "Today" for the first entry, otherwise a short weekday name. */
    private fun dayLabel(isoDate: String?, index: Int): String {
        if (index == 0) return "Today"
        val parsed = isoDate?.let {
            runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it) }.getOrNull()
        } ?: return NO_DATA
        return SimpleDateFormat("EEE", Locale.getDefault()).format(parsed)
    }

    private fun weatherEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67 -> "🌧️"
        in 71..77 -> "❄️"
        in 80..82 -> "🌧️"
        in 95..99 -> "⛈️"
        else -> "🌤️"
    }

    /**
     * Update AI insight text for large widget.
     */
    fun updateAIInsight(context: Context, insight: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ai_insight", insight)
            // Stamped so [getData] can drop it once a newer reading has landed:
            // the text describes particular conditions, not the app in general.
            .putLong("ai_insight_at", System.currentTimeMillis())
            .apply()

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { LargeWeatherWidget().updateAll(context) }
        }
    }

    /**
     * Clear all widget data
     */
    fun clearData(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

/**
 * Everything the widgets render, read from the shared prefs by
 * [WidgetDataManager.getData]. Temperatures are already in the user's chosen
 * display unit — widgets print them with a bare degree sign and must not
 * convert again.
 */
data class WidgetData(
    /**
     * False until a real reading has been stored. Every reading below is a
     * placeholder in that case and must not be rendered as a measurement.
     */
    val hasData: Boolean,
    val locationName: String,
    val currentTemp: Int,
    val feelsLike: Int,
    val weatherIcon: String,
    val condition: String,
    /** Null when the response carried no daily forecast. */
    val high: Int?,
    val low: Int?,
    val humidity: Int,
    val windSpeed: Int,
    val dailyForecast: List<DayForecast>,
    val hourlyForecast: List<HourForecast>,
    /** Null unless an insight was written for this reading. */
    val aiInsight: String?,
    val lastUpdated: String,
    val latitude: Double?,
    val longitude: Double?
)

/** One stored hourly reading. [label] is the real clock time it belongs to. */
data class HourForecast(
    val label: String,
    val icon: String,
    val temp: Int
)
