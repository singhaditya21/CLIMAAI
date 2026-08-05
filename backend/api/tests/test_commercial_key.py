"""Commercial Open-Meteo licence switch. No database, no network.

Open-Meteo's free tier is licensed for non-commercial use only; the paid API
lives on the customer- hosts and authenticates with an ``apikey`` query
parameter. Buying a licence must be a pure configuration change — set
OPEN_METEO_API_KEY and done — so these tests pin the URL construction both
ways and prove every backend call site actually routes through the switch.
A call site still hitting the free host after the flip is a licence
violation nothing else would catch.
"""
import httpx
import pytest
from urllib.parse import urlsplit

from app.config import Settings, get_settings
from app.services.multi_weather_service import MultiSourceWeatherService
from app.services.nowcast_service import NowcastService
from app.services.weather_service import WeatherService

KEY = "test-licence-key"


def _settings(key: str = "") -> Settings:
    # _env_file=None: a developer's .env must not leak into these assertions.
    return Settings(OPEN_METEO_API_KEY=key, _env_file=None)


class _RecordingClient:
    """Records (url, params) and serves a canned payload, or raises.

    is_closed: MultiSourceWeatherService's `client` property replaces anything
    that reports itself closed with a real httpx client.
    """

    is_closed = False

    def __init__(self, payload=None):
        self._payload = payload
        self.calls = []

    async def get(self, url, params=None, headers=None, **kwargs):
        self.calls.append((url, dict(params or {})))
        if self._payload is None:
            raise httpx.ConnectError("recorded, not fetched")

        class _Response:
            def raise_for_status(self_inner):
                return None

            def json(self_inner):
                return self._payload

        return _Response()


# The code default, bypassing any developer .env that may still pin the dead
# pre-fix air-quality host (or a real licence key) into the cached Settings.
_DEFAULT_AQ_URL = Settings.model_fields["OPEN_METEO_AIR_QUALITY_URL"].default


@pytest.fixture
def licensed(monkeypatch):
    """Flip the process-wide cached Settings to the licensed state."""
    settings = get_settings()
    monkeypatch.setattr(settings, "OPEN_METEO_API_KEY", KEY)
    monkeypatch.setattr(settings, "OPEN_METEO_AIR_QUALITY_URL", _DEFAULT_AQ_URL)


@pytest.fixture
def unlicensed(monkeypatch):
    """Pin the free tier even if the developer's environment holds a key."""
    settings = get_settings()
    monkeypatch.setattr(settings, "OPEN_METEO_API_KEY", "")
    monkeypatch.setattr(settings, "OPEN_METEO_AIR_QUALITY_URL", _DEFAULT_AQ_URL)


# --------------------------------------------------------------------------
# URL construction
# --------------------------------------------------------------------------

def test_without_a_key_the_request_is_untouched():
    url, params = _settings("").open_meteo_request(
        "https://api.open-meteo.com/v1/forecast", {"latitude": 1.0}
    )

    assert url == "https://api.open-meteo.com/v1/forecast"
    assert params == {"latitude": 1.0}


@pytest.mark.parametrize(
    "free_host,licensed_host",
    [
        ("api.open-meteo.com", "customer-api.open-meteo.com"),
        ("air-quality-api.open-meteo.com", "customer-air-quality-api.open-meteo.com"),
        ("archive-api.open-meteo.com", "customer-archive-api.open-meteo.com"),
        ("geocoding-api.open-meteo.com", "customer-geocoding-api.open-meteo.com"),
    ],
)
def test_every_free_host_maps_to_its_customer_twin(free_host, licensed_host):
    """The paid API prefixes each host with customer- — same shape throughout."""
    url, params = _settings(KEY).open_meteo_request(
        f"https://{free_host}/v1/thing", {"latitude": 1.0}
    )

    assert url == f"https://{licensed_host}/v1/thing"
    assert params["apikey"] == KEY
    assert params["latitude"] == 1.0


def test_path_survives_the_host_switch():
    url, _ = _settings(KEY).open_meteo_request(
        "https://api.open-meteo.com/v1/forecast", {}
    )

    assert urlsplit(url).path == "/v1/forecast"
    assert urlsplit(url).scheme == "https"


def test_customer_host_is_not_double_prefixed():
    url, params = _settings(KEY).open_meteo_request(
        "https://customer-api.open-meteo.com/v1/forecast", {}
    )

    assert url == "https://customer-api.open-meteo.com/v1/forecast"
    assert params["apikey"] == KEY


def test_self_hosted_instance_is_never_rewritten():
    """A self-hosted Open-Meteo takes no key and has no customer- twin."""
    url, params = _settings(KEY).open_meteo_request(
        "https://meteo.internal.example/v1/forecast", {"latitude": 1.0}
    )

    assert url == "https://meteo.internal.example/v1/forecast"
    assert "apikey" not in params


def test_caller_params_are_not_mutated():
    original = {"latitude": 1.0}
    _settings(KEY).open_meteo_request("https://api.open-meteo.com/v1/forecast", original)

    assert original == {"latitude": 1.0}


# --------------------------------------------------------------------------
# Call sites — each service must route through the switch
# --------------------------------------------------------------------------

async def test_weather_service_forecast_uses_licensed_host(licensed):
    service = WeatherService(http_client=_RecordingClient(), redis_client=None)

    with pytest.raises(Exception, match="Failed to fetch weather data"):
        await service.get_current_weather(12.34, 56.78, use_cache=False)

    url, params = service.http_client.calls[0]
    assert urlsplit(url).netloc == "customer-api.open-meteo.com"
    assert params["apikey"] == KEY


async def test_weather_service_forecast_stays_free_without_a_key(unlicensed):
    service = WeatherService(http_client=_RecordingClient(), redis_client=None)

    with pytest.raises(Exception, match="Failed to fetch weather data"):
        await service.get_current_weather(12.34, 56.78, use_cache=False)

    url, params = service.http_client.calls[0]
    assert urlsplit(url).netloc == "api.open-meteo.com"
    assert "apikey" not in params


async def test_weather_service_air_quality_uses_licensed_host(licensed):
    service = WeatherService(
        http_client=_RecordingClient({"current": {"pm2_5": 5.0, "pm10": 8.0}}),
        redis_client=None,
    )

    await service._get_air_quality(12.34, 56.78)

    url, params = service.http_client.calls[0]
    assert urlsplit(url).netloc == "customer-air-quality-api.open-meteo.com"
    assert params["apikey"] == KEY


async def test_weather_service_air_quality_free_host_actually_resolves(unlicensed):
    """Regression: air-quality.open-meteo.com (no -api) does not exist in DNS."""
    service = WeatherService(
        http_client=_RecordingClient({"current": {"pm2_5": 5.0, "pm10": 8.0}}),
        redis_client=None,
    )

    await service._get_air_quality(12.34, 56.78)

    url, _ = service.http_client.calls[0]
    assert urlsplit(url).netloc == "air-quality-api.open-meteo.com"


async def test_nowcast_uses_licensed_host(licensed):
    service = NowcastService()
    service.http_client = _RecordingClient()

    with pytest.raises(httpx.ConnectError):
        await service.get_nowcast(12.34, 56.78)

    url, params = service.http_client.calls[0]
    assert urlsplit(url).netloc == "customer-api.open-meteo.com"
    assert params["apikey"] == KEY


async def test_multi_source_fetcher_uses_licensed_host(licensed):
    service = MultiSourceWeatherService(config={})
    service._client = _RecordingClient({"current": {}, "daily": {}})

    await service._fetch_open_meteo(12.34, 56.78)

    url, params = service._client.calls[0]
    assert urlsplit(url).netloc == "customer-api.open-meteo.com"
    assert params["apikey"] == KEY


async def test_multi_source_fetcher_stays_free_without_a_key(unlicensed):
    service = MultiSourceWeatherService(config={})
    service._client = _RecordingClient({"current": {}, "daily": {}})

    await service._fetch_open_meteo(12.34, 56.78)

    url, params = service._client.calls[0]
    assert urlsplit(url).netloc == "api.open-meteo.com"
    assert "apikey" not in params


async def test_historical_archive_uses_licensed_host(licensed):
    service = MultiSourceWeatherService(config={})
    service._client = _RecordingClient({"daily": {}})

    await service.get_historical_weather(12.34, 56.78, "2026-01-01", "2026-01-07")

    url, params = service._client.calls[0]
    assert urlsplit(url).netloc == "customer-archive-api.open-meteo.com"
    assert params["apikey"] == KEY
