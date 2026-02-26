import pytest
from unittest.mock import AsyncMock, patch
from datetime import datetime

@pytest.fixture
def mock_weather_data():
    return {
        "current": {
            "temperature": 20.0,
            "feels_like": 19.0,
            "humidity": 50,
            "dew_point": 10.0,
            "wind_speed": 5.0,
            "wind_direction": 180,
            "precipitation": 0.0,
            "weather_code": 0,
            "weather_description": "Clear sky",
            "cloud_cover": 0,
            "pressure": 1013.0,
            "visibility": 10000.0,
            "uv_index": 5.0,
            "is_day": True,
            "timestamp": datetime.now()
        },
        "hourly": [],
        "daily": [],
        "air_quality": None,
        "location": {"latitude": 0, "longitude": 0, "name": "Test Location"},
        "timezone": "UTC",
        "cached": False
    }

@pytest.mark.asyncio
async def test_get_current_weather(client, mock_weather_data):
    with patch("app.routers.weather.WeatherService") as MockService:
        service_instance = MockService.return_value
        # Mock the get_current_weather method to be an async mock
        service_instance.get_current_weather = AsyncMock(return_value=AsyncMock(**mock_weather_data))
        # Wait, AsyncMock returning AsyncMock might be tricky if the attribute access matches Schema fields.
        # Better: return an object that matches the Pydantic model or dict.
        # But `router` expects an object with attributes `daily`, `hourly` etc because it does `weather.daily = ...`

        # Let's return a simple object (Namespace or class)
        class MockWeatherResponse:
            def __init__(self, data):
                for k, v in data.items():
                    setattr(self, k, v)

        service_instance.get_current_weather.return_value = MockWeatherResponse(mock_weather_data)
        service_instance.close = AsyncMock()

        response = await client.get("/weather/current?latitude=40.7128&longitude=-74.0060")
        assert response.status_code == 200
        data = response.json()
        assert data["current"]["temperature"] == 20.0
        assert data["timezone"] == "UTC"

@pytest.mark.asyncio
async def test_get_current_weather_validation_error(client):
    response = await client.get("/weather/current?latitude=100&longitude=0")
    assert response.status_code == 422  # Validation error for lat > 90

@pytest.mark.asyncio
async def test_get_hourly_forecast(client, mock_weather_data):
    with patch("app.routers.weather.WeatherService") as MockService:
        service_instance = MockService.return_value

        # Setup mock with some hourly data
        mock_data = mock_weather_data.copy()
        mock_data["hourly"] = [
            {
                "time": datetime.now(),
                "temperature": 20.0,
                "feels_like": 19.0,
                "precipitation_probability": 0,
                "precipitation": 0.0,
                "weather_code": 0,
                "weather_description": "Clear",
                "wind_speed": 5.0,
                "wind_direction": 180,
                "humidity": 50,
                "cloud_cover": 0,
                "uv_index": 5.0
            }
        ]

        class MockWeatherResponse:
            def __init__(self, data):
                for k, v in data.items():
                    setattr(self, k, v)

        service_instance.get_current_weather = AsyncMock(return_value=MockWeatherResponse(mock_data))
        service_instance.close = AsyncMock()

        response = await client.get("/weather/hourly?latitude=40.7&longitude=-74.0&hours=1")
        if response.status_code != 200:
            print(f"Error response: {response.text}")
        assert response.status_code == 200
        data = response.json()
        assert len(data["hourly"]) == 1
