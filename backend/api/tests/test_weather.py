import pytest
from httpx import AsyncClient
from unittest.mock import AsyncMock
from datetime import datetime
import sys
import os

# Ensure app is importable
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app.schemas.weather import WeatherResponse, CurrentWeather, HourlyWeather, DailyWeather

@pytest.fixture
def mock_weather_data():
    return WeatherResponse(
        current=CurrentWeather(
            temperature=20.0,
            feels_like=21.0,
            humidity=50,
            wind_speed=10.0,
            wind_direction=180,
            precipitation=0.0,
            weather_code=0,
            weather_description="Sunny",
            cloud_cover=0,
            pressure=1013.0,
            visibility=10000.0,
            uv_index=5.0,
            is_day=True,
            timestamp=datetime.utcnow()
        ),
        hourly=[
            HourlyWeather(
                time=datetime.utcnow(),
                temperature=20.0,
                feels_like=21.0,
                precipitation_probability=0,
                precipitation=0.0,
                weather_code=0,
                weather_description="Sunny",
                wind_speed=10.0,
                wind_direction=180,
                humidity=50,
                cloud_cover=0,
                uv_index=5.0
            ) for _ in range(24)
        ],
        daily=[
            DailyWeather(
                date="2023-01-01",
                temperature_max=25.0,
                temperature_min=15.0,
                sunrise="06:00",
                sunset="18:00",
                precipitation_sum=0.0,
                precipitation_probability=0,
                weather_code=0,
                weather_description="Sunny",
                wind_speed_max=15.0,
                wind_direction=180,
                uv_index_max=6.0
            ) for _ in range(7)
        ],
        location={"latitude": 51.5, "longitude": -0.1},
        timezone="UTC",
        cached=False
    )

@pytest.mark.asyncio
async def test_get_current_weather(client: AsyncClient, mock_weather_data):
    from app.services.weather_service import get_weather_service
    from app.main import app

    mock_service = AsyncMock()
    mock_service.get_current_weather.return_value = mock_weather_data

    app.dependency_overrides[get_weather_service] = lambda: mock_service

    response = await client.get("/api/weather/current?latitude=51.5&longitude=-0.1")
    assert response.status_code == 200
    data = response.json()
    assert data["current"]["temperature"] == 20.0

    app.dependency_overrides.clear()
