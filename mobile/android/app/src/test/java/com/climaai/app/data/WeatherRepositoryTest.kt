package com.climaai.app.data

import android.content.Context
import com.climaai.app.data.cache.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@ExperimentalCoroutinesApi
class WeatherRepositoryTest {

    private lateinit var context: Context
    private lateinit var api: ClimaAIApi
    private lateinit var cache: WeatherCacheDao
    private lateinit var refreshTracker: RefreshTracker

    private lateinit var repository: WeatherRepository

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        api = mockk()
        cache = mockk(relaxed = true)
        refreshTracker = mockk(relaxed = true)

        repository = WeatherRepository(context, api, cache, refreshTracker)
    }

    @Test
    fun `getWeather returns cached data when cache is valid and no force refresh`() = runTest {
        // Given
        val lat = 37.77
        val lon = -122.42
        val locationKey = "37.77_-122.42"
        val cachedWeather = CachedWeather(
            locationKey = locationKey,
            latitude = lat,
            longitude = lon,
            locationName = "San Francisco",
            weatherJson = "{}", // We assume Gson handles empty JSON gracefully or we verify interaction
            cachedAt = System.currentTimeMillis(), // valid cache
            lastFetchedAt = System.currentTimeMillis()
        )

        coEvery { cache.getWeather(locationKey) } returns cachedWeather

        // When
        val result = repository.getWeather(lat, lon, forceRefresh = false)

        // Then
        assertTrue("Result should be success", result is WeatherResult.Success)
        val success = result as WeatherResult.Success
        assertTrue("Should return from cache", success.fromCache)
        coVerify(exactly = 0) { api.getWeather(any(), any()) }
    }

    @Test
    fun `getWeather fetches from network when cache is missing`() = runTest {
        // Given
        val lat = 37.77
        val lon = -122.42
        val locationKey = "37.77_-122.42"

        coEvery { cache.getWeather(locationKey) } returns null
        coEvery { refreshTracker.checkRefreshAllowed(any()) } returns RefreshStatus(true, null, 0)

        val mockWeatherResponse = mockk<WeatherResponse>(relaxed = true)
        coEvery { api.getWeather(lat, lon) } returns Response.success(mockWeatherResponse)

        // When
        val result = repository.getWeather(lat, lon, forceRefresh = false)

        // Then
        assertTrue(result is WeatherResult.Success)
        val success = result as WeatherResult.Success
        assertFalse(success.fromCache)
        coVerify(exactly = 1) { api.getWeather(lat, lon) }
        coVerify(exactly = 1) { cache.insertWeather(any()) }
    }

    @Test
    fun `getWeather returns rate limited error when refresh not allowed and no cache`() = runTest {
        // Given
        val lat = 37.77
        val lon = -122.42
        val locationKey = "37.77_-122.42"

        coEvery { cache.getWeather(locationKey) } returns null
        coEvery { refreshTracker.checkRefreshAllowed(any()) } returns RefreshStatus(false, "Rate limited", 10)

        // When
        val result = repository.getWeather(lat, lon, forceRefresh = false)

        // Then
        assertTrue(result is WeatherResult.Error)
        val error = result as WeatherResult.Error
        assertEquals("Rate limited", error.message)
        assertNull(error.cached)
        coVerify(exactly = 0) { api.getWeather(any(), any()) }
    }
}
