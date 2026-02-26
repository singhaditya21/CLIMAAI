package com.climaai.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.climaai.app.ui.screens.ForecastScreen
import com.climaai.app.ui.theme.ClimaAITheme
import com.climaai.app.data.model.WeatherResponse
import org.junit.Rule
import org.junit.Test

class WeatherScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows loading state initially`() {
        composeTestRule.setContent {
            ClimaAITheme {
                ForecastScreen(state = WeatherState.Loading)
            }
        }

        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `shows weather data on success`() {
        val mockData = WeatherResponse(
            current = CurrentWeather(
                temperature = 25.0,
                condition = "Sunny"
            ),
            daily = listOf(),
            hourly = listOf()
        )

        composeTestRule.setContent {
            ClimaAITheme {
                ForecastScreen(state = WeatherState.Success(mockData))
            }
        }

        composeTestRule.onNodeWithText("25°").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sunny").assertIsDisplayed()
    }

    @Test
    fun `shows error message on failure`() {
        composeTestRule.setContent {
            ClimaAITheme {
                ForecastScreen(state = WeatherState.Error("Network error"))
            }
        }

        composeTestRule.onNodeWithText("Network error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }
}
