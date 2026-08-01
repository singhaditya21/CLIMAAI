"""Multi-source weather aggregation.

This service was the largest untested surface in the backend: it fans out to a
dozen providers, each with its own response shape, rate budget and key
requirement. Every provider is stubbed here — no network.
"""
import pytest

from app.services.multi_weather_service import (
    SOURCE_DAILY_LIMITS,
    MultiSourceWeatherService,
    _as_percent,
    _to_float,
)


class _StubResponse:
    def __init__(self, payload, status_code=200):
        self._payload = payload
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"HTTP {self.status_code}")

    def json(self):
        return self._payload


class _StubClient:
    """Serves a canned payload and records what was requested.

    Needs is_closed: the service's `client` property replaces anything that
    reports itself closed with a real httpx client.
    """

    is_closed = False

    def __init__(self, payload, status_code=200):
        self._payload = payload
        self._status_code = status_code
        self.calls = []

    async def get(self, url, params=None, headers=None, **kwargs):
        self.calls.append((url, params))
        return _StubResponse(self._payload, self._status_code)


@pytest.fixture
def service():
    svc = MultiSourceWeatherService(config={})
    svc._client = None
    return svc


# --------------------------------------------------------------------------
# Value coercion
# --------------------------------------------------------------------------

@pytest.mark.parametrize(
    "raw,expected",
    [("21.5", 21.5), ("0", 0.0), (3, 3.0), (None, None), ("", None), ("n/a", None)],
)
def test_to_float_tolerates_string_and_junk(raw, expected):
    """wttr.in returns every numeric field as a string."""
    assert _to_float(raw) == expected


@pytest.mark.parametrize(
    "raw,expected", [(0.62, 62.0), (1, 100.0), (0, 0.0), (None, None), ("x", None)]
)
def test_as_percent_converts_dark_sky_fractions(raw, expected):
    """Dark Sky-style humidity is 0..1; the rest of the app uses 0..100."""
    assert _as_percent(raw) == expected


# --------------------------------------------------------------------------
# Key-gated providers
# --------------------------------------------------------------------------

async def test_pirate_weather_returns_none_without_a_key(service):
    assert await service._fetch_pirate_weather(51.5, -0.12) is None


async def test_weatherapi_returns_none_without_a_key(service):
    assert await service._fetch_weatherapi(51.5, -0.12) is None


async def test_pirate_weather_parses_minutely_precipitation():
    """Minutely precipitation is the reason to use this source at all."""
    svc = MultiSourceWeatherService(config={"PIRATE_WEATHER_API_KEY": "test-key"})
    svc._client = _StubClient(
        {
            "currently": {
                "temperature": 18.4,
                "apparentTemperature": 17.9,
                "humidity": 0.62,
                "windSpeed": 12.0,
                "windBearing": 210,
                "pressure": 1013.0,
                "uvIndex": 4,
                "summary": "Light rain",
            },
            "minutely": {"data": [{"time": i, "precipIntensity": 0.5,
                                   "precipProbability": 0.8} for i in range(90)]},
            "alerts": [{"title": "Flood Watch"}],
        }
    )

    result = await svc._fetch_pirate_weather(40.7, -74.0)

    assert result["source"] == "pirate_weather"
    assert result["current"]["temperature"] == 18.4
    # Humidity normalised from the 0..1 fraction.
    assert result["current"]["humidity"] == 62.0
    # Capped at 60 minutes even though 90 were returned.
    assert len(result["minutely_precipitation"]) == 60
    assert result["alert_count"] == 1


async def test_weatherapi_parses_air_quality():
    """This provider returns AQI in the same call, saving a round trip."""
    svc = MultiSourceWeatherService(config={"WEATHERAPI_KEY": "test-key"})
    svc._client = _StubClient(
        {
            "current": {
                "temp_c": 22.0,
                "feelslike_c": 21.0,
                "humidity": 55,
                "wind_kph": 14.0,
                "wind_degree": 180,
                "pressure_mb": 1012.0,
                "vis_km": 10.0,
                "uv": 5.0,
                "cloud": 40,
                "condition": {"text": "Partly cloudy"},
                "air_quality": {"pm2_5": 8.1, "pm10": 12.4, "o3": 60.0, "no2": 15.2},
            }
        }
    )

    result = await svc._fetch_weatherapi(51.5, -0.12)

    assert result["current"]["temperature"] == 22.0
    assert result["current"]["weather_description"] == "Partly cloudy"
    assert result["air_quality"]["pm2_5"] == 8.1


async def test_weatherapi_handles_missing_air_quality():
    svc = MultiSourceWeatherService(config={"WEATHERAPI_KEY": "test-key"})
    svc._client = _StubClient({"current": {"temp_c": 22.0}})

    result = await svc._fetch_weatherapi(51.5, -0.12)

    assert result["air_quality"] is None


# --------------------------------------------------------------------------
# No-key providers
# --------------------------------------------------------------------------

async def test_wttr_coerces_its_string_numerics(service):
    service._client = _StubClient(
        {
            "current_condition": [
                {
                    "temp_C": "29",
                    "FeelsLikeC": "31",
                    "humidity": "48",
                    "windspeedKmph": "11",
                    "winddirDegree": "250",
                    "pressure": "1009",
                    "visibility": "10",
                    "uvIndex": "7",
                    "cloudcover": "25",
                    "weatherDesc": [{"value": "Sunny"}],
                }
            ]
        }
    )

    result = await service._fetch_wttr(43.65, -79.38)

    assert result["current"]["temperature"] == 29.0
    assert isinstance(result["current"]["humidity"], float)
    assert result["current"]["weather_description"] == "Sunny"


async def test_wttr_swallows_upstream_failure(service):
    """A volunteer-run service going down must not fail the aggregate."""
    service._client = _StubClient({}, status_code=503)

    assert await service._fetch_wttr(43.65, -79.38) is None


# --------------------------------------------------------------------------
# Rate budgets
# --------------------------------------------------------------------------

def test_every_registered_source_has_a_daily_budget():
    """A source with no budget silently falls back to a generic default."""
    svc = MultiSourceWeatherService(config={})
    registered = {
        name
        for name in dir(svc)
        if name.startswith("_fetch_")
    }
    known = {f"_fetch_{s}" for s in SOURCE_DAILY_LIMITS}

    missing = registered - known - {"_fetch_"}
    assert not missing, f"providers without a daily budget: {sorted(missing)}"


def test_rate_limiter_blocks_once_the_budget_is_spent():
    svc = MultiSourceWeatherService(config={})
    limit = SOURCE_DAILY_LIMITS["wttr"]

    for _ in range(limit):
        assert svc.rate_limiter.is_allowed("wttr")
        svc.rate_limiter._counts["wttr"] += 1

    assert svc.rate_limiter.is_allowed("wttr") is False
