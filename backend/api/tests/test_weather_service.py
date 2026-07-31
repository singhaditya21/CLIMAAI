"""Weather parsing tests. No database or network — the HTTP client is stubbed.

The null-trailing-day case below is a regression test: Open-Meteo pads its daily
arrays out to forecast_days but leaves the final day null until that day's data
publishes, which made every /weather request fail Pydantic validation.
"""
from datetime import datetime, timedelta

import pytest

from app.services.weather_service import WeatherService


def _forecast_payload(days: int = 16, null_trailing_days: int = 1) -> dict:
    """Build an Open-Meteo-shaped response with optional null trailing days."""
    start = datetime(2026, 7, 31)
    dates = [(start + timedelta(days=i)).strftime("%Y-%m-%d") for i in range(days)]
    hours = [(start + timedelta(hours=i)).strftime("%Y-%m-%dT%H:%M") for i in range(24)]

    def daily(value):
        """Real values, then None for the trailing days."""
        filled = days - null_trailing_days
        return [value] * filled + [None] * null_trailing_days

    return {
        "current": {
            "time": "2026-07-31T12:00",
            "temperature_2m": 18.4,
            "apparent_temperature": 17.9,
            "relative_humidity_2m": 61,
            "dew_point_2m": 11.0,
            "wind_speed_10m": 12.3,
            "wind_direction_10m": 210,
            "precipitation": 0.0,
            "weather_code": 3,
            "cloud_cover": 75,
            "pressure_msl": 1013.2,
            "is_day": 1,
        },
        "hourly": {
            "time": hours,
            "temperature_2m": [18.0] * 24,
            "apparent_temperature": [17.5] * 24,
            "precipitation_probability": [10] * 24,
            "precipitation": [0.0] * 24,
            "weather_code": [3] * 24,
            "wind_speed_10m": [12.0] * 24,
            "wind_direction_10m": [200] * 24,
            "relative_humidity_2m": [60] * 24,
            "cloud_cover": [70] * 24,
            "uv_index": [4.2] * 24,
        },
        "daily": {
            "time": dates,
            "temperature_2m_max": daily(22.5),
            "temperature_2m_min": daily(13.1),
            "sunrise": daily("2026-07-31T05:20"),
            "sunset": daily("2026-07-31T20:45"),
            "precipitation_sum": daily(1.2),
            "snowfall_sum": daily(0.0),
            "precipitation_probability_max": daily(30),
            "weather_code": daily(3),
            "wind_speed_10m_max": daily(18.0),
            "wind_direction_10m_dominant": daily(220),
            "uv_index_max": daily(5.5),
        },
    }


class _StubResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self._payload


class _StubClient:
    """Serves the forecast payload and fails air-quality calls, as happens offline."""

    def __init__(self, payload):
        self._payload = payload
        self.requests = []

    async def get(self, url, params=None, **kwargs):
        self.requests.append(url)
        if "air-quality" in url:
            raise RuntimeError("air quality unavailable")
        return _StubResponse(self._payload)


@pytest.fixture
def service():
    return WeatherService(http_client=None, redis_client=None)


async def test_null_trailing_day_is_skipped_not_zeroed(service):
    """The 16th day is all-null upstream; it must be dropped, not coerced to 0."""
    service.http_client = _StubClient(_forecast_payload(days=16, null_trailing_days=1))

    weather = await service.get_current_weather(51.5, -0.12, use_cache=False)

    assert len(weather.daily) == 15
    assert all(day.temperature_max is not None for day in weather.daily)
    # A zero-coercing implementation would leave a bogus 0.0 entry behind.
    assert not any(day.temperature_max == 0.0 for day in weather.daily)
    assert weather.daily[-1].date == "2026-08-14"


async def test_multiple_null_trailing_days_are_all_skipped(service):
    service.http_client = _StubClient(_forecast_payload(days=16, null_trailing_days=3))

    weather = await service.get_current_weather(51.5, -0.12, use_cache=False)

    assert len(weather.daily) == 13


async def test_complete_payload_keeps_every_day(service):
    service.http_client = _StubClient(_forecast_payload(days=16, null_trailing_days=0))

    weather = await service.get_current_weather(51.5, -0.12, use_cache=False)

    assert len(weather.daily) == 16


async def test_current_conditions_are_parsed(service):
    service.http_client = _StubClient(_forecast_payload())

    weather = await service.get_current_weather(51.5, -0.12, use_cache=False)

    assert weather.current.temperature == 18.4
    assert weather.current.weather_description == "Overcast"
    assert weather.current.is_day is True
    assert len(weather.hourly) == 24


async def test_air_quality_failure_does_not_fail_the_request(service):
    """The stub raises on air-quality; weather should still come back."""
    service.http_client = _StubClient(_forecast_payload())

    weather = await service.get_current_weather(51.5, -0.12, use_cache=False)

    assert weather.air_quality is None
    assert weather.current.temperature == 18.4


@pytest.mark.parametrize(
    "code,expected",
    [(0, "Clear sky"), (3, "Overcast"), (95, "Thunderstorm"), (61, "Slight rain")],
)
def test_weather_code_descriptions(service, code, expected):
    assert service._get_weather_description(code) == expected


def test_unknown_weather_code_is_labelled(service):
    assert service._get_weather_description(12345) == "Unknown"
