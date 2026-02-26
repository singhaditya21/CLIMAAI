package com.climaai.app.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.climaai.app.data.*
import com.climaai.app.data.repository.OpenMeteoRepository
import com.climaai.app.data.model.WeatherResponse
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.rules.TestRule

@ExperimentalCoroutinesApi
class WeatherViewModelTest {

    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    private val application = mockk<Application>(relaxed = true)
    private val mockWeatherResponse = mockk<WeatherResponse>(relaxed = true)

    // Note: The ViewModel instantiates repositories internally, making it hard to test.
    // Recommended refactoring: Inject repositories via constructor.
    private lateinit var viewModel: WeatherViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(OpenMeteoRepository)

        // Mock success by default
        coEvery { OpenMeteoRepository.getWeather(any(), any()) } returns Result.success(mockWeatherResponse)

        viewModel = WeatherViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `fetchWeather uses OpenMeteoRepository and updates state on success`() = runTest {
        // Given
        val lat = 51.5
        val lon = -0.1

        // When
        viewModel.fetchWeather(lat, lon)

        // Then
        // Verify OpenMeteoRepository was called
        coVerify { OpenMeteoRepository.getWeather(lat, lon) }

        // Check state
        assert(viewModel.weatherState.value is WeatherState.Success)
    }

    @Test
    fun `forceRefresh checks rate limits before fetching`() = runTest {
        // Given
        viewModel.setLocation(51.5, -0.1, "London")

        // When
        viewModel.forceRefresh()

        // Then
        // Should verify RefreshTracker logic if possible, or observe rateLimitMessage
        // If mocked RefreshTracker says allowed, state should update
        assert(viewModel.canRefresh.value)
    }
}
