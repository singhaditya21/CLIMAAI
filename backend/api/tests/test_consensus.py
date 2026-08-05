"""Multi-source consensus aggregation.

The audit's central finding: the service queries a dozen providers and then
keeps the first answer, discarding the disagreement — the only genuinely
valuable output of querying many models. The consensus block keeps it. These
tests drive the aggregation math directly with synthetic source payloads — no
network.
"""
import pytest

from app.services.multi_weather_service import MultiSourceWeatherService


class _StubResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class _StubClient:
    """Serves a canned payload; is_closed keeps the service from replacing it."""

    is_closed = False

    def __init__(self, payload):
        self._payload = payload

    async def get(self, url, params=None, headers=None, **kwargs):
        return _StubResponse(self._payload)


@pytest.fixture
def service():
    return MultiSourceWeatherService(config={})


def _src(source, **current):
    """Synthetic per-source payload in the shape the fetchers return."""
    return {"source": source, "current": current}


# --------------------------------------------------------------------------
# Agreement / disagreement
# --------------------------------------------------------------------------

def test_agreeing_sources_yield_high_confidence(service):
    results = {
        "open_meteo": _src("open_meteo", temperature=20.0, wind_speed=15.0),
        "weatherapi": _src("weatherapi", temperature=19.4, wind_speed=16.0),
        "met_norway": _src("met_norway", temperature=20.6, wind_speed=4.0),  # 14.4 km/h
    }

    consensus = service._compute_consensus(results)

    temp = consensus["temperature"]
    assert temp == {
        "median": 20.0,
        "min": 19.4,
        "max": 20.6,
        "spread": 1.2,
        "source_count": 3,
    }
    assert consensus["wind_speed"]["spread"] == 1.6
    assert consensus["confidence"] == "high"
    assert consensus["sources"] == ["met_norway", "open_meteo", "weatherapi"]
    assert consensus["summary"] == "Sources agree on temperature and wind speed."


def test_disagreeing_temperatures_yield_low_confidence(service):
    # 6.5 C apart: coat weather on one forecast, t-shirt on the other.
    results = {
        "open_meteo": _src("open_meteo", temperature=12.0),
        "weatherapi": _src("weatherapi", temperature=18.5),
    }

    consensus = service._compute_consensus(results)

    assert consensus["temperature"]["spread"] == 6.5
    assert consensus["confidence"] == "low"
    assert consensus["summary"] == "Sources disagree on temperature."


def test_moderate_spread_yields_medium_confidence(service):
    results = {
        "open_meteo": _src("open_meteo", temperature=12.0),
        "weatherapi": _src("weatherapi", temperature=15.5),
    }

    consensus = service._compute_consensus(results)

    assert consensus["confidence"] == "medium"
    assert consensus["summary"] == "Sources differ somewhat on temperature."


def test_overall_confidence_is_the_weakest_variable(service):
    """Temperature agreement must not paper over rain disagreement."""
    results = {
        "open_meteo": {
            "source": "open_meteo",
            "current": {"temperature": 20.0},
            "daily": {"precipitation_probability_max": [80]},
        },
        "pirate_weather": _src(
            "pirate_weather", temperature=20.4, precipitation_probability=15.0
        ),
    }

    consensus = service._compute_consensus(results)

    assert consensus["temperature"]["spread"] == 0.4
    assert consensus["precipitation_probability"]["spread"] == 65.0
    assert consensus["confidence"] == "low"
    assert consensus["summary"] == (
        "Sources agree on temperature; they disagree on chance of rain."
    )


# --------------------------------------------------------------------------
# Null states — never a plausible invented value
# --------------------------------------------------------------------------

def test_single_source_yields_no_consensus(service):
    """One answer has zero spread by construction; reporting it would fake
    agreement."""
    results = {"open_meteo": _src("open_meteo", temperature=20.0, wind_speed=10.0)}

    assert service._compute_consensus(results) is None


def test_no_results_yields_no_consensus(service):
    assert service._compute_consensus({}) is None


def test_sources_without_comparable_variables_yield_no_consensus(service):
    """Two sources responded, but neither carries a consensus variable."""
    results = {
        "nws": {"source": "nws", "alert_count": 0, "alerts": []},
        "stormglass": {"source": "stormglass", "wave_height": 1.2},
    }

    assert service._compute_consensus(results) is None


def test_variable_reported_by_one_source_is_null(service):
    """Consensus exists for temperature, but wind is a single voice."""
    results = {
        "open_meteo": _src("open_meteo", temperature=20.0, wind_speed=15.0),
        "wttr": _src("wttr", temperature=20.5),
    }

    consensus = service._compute_consensus(results)

    assert consensus["temperature"]["source_count"] == 2
    assert consensus["wind_speed"] is None
    assert consensus["precipitation_probability"] is None
    assert consensus["summary"] == "Sources agree on temperature."


def test_non_numeric_values_are_excluded(service):
    results = {
        "open_meteo": _src("open_meteo", temperature=20.0),
        "wttr": _src("wttr", temperature="21"),  # junk survives a fetcher bug
        "dwd": _src("dwd", temperature=None),
        "weatherapi": _src("weatherapi", temperature=20.8),
    }

    consensus = service._compute_consensus(results)

    assert consensus["temperature"]["source_count"] == 2
    assert consensus["sources"] == ["open_meteo", "weatherapi"]


# --------------------------------------------------------------------------
# Unit normalization
# --------------------------------------------------------------------------

def test_wind_speeds_are_normalized_before_comparison(service):
    """5 m/s and 18 km/h are the same wind; raw numbers would fabricate a
    13-unit disagreement out of a unit mismatch."""
    results = {
        "met_norway": _src("met_norway", temperature=20.0, wind_speed=5.0),  # m/s
        "wttr": _src("wttr", temperature=20.0, wind_speed=18.0),             # km/h
    }

    consensus = service._compute_consensus(results)

    wind = consensus["wind_speed"]
    assert wind["min"] == wind["max"] == 18.0
    assert wind["spread"] == 0.0


def test_7timer_categorical_wind_is_excluded(service):
    """7timer's wind10m.speed is a 1-8 category, not a physical speed."""
    results = {
        "7timer": _src("7timer", temperature=20.0, wind_speed=3),
        "wttr": _src("wttr", temperature=21.0, wind_speed=18.0),
    }

    consensus = service._compute_consensus(results)

    # Only one comparable wind value remains, so wind consensus is null —
    # but 7timer's temperature is real Celsius and still counts.
    assert consensus["wind_speed"] is None
    assert consensus["temperature"]["source_count"] == 2


def test_open_meteo_precipitation_comes_from_the_daily_block(service):
    results = {
        "open_meteo": {
            "source": "open_meteo",
            "current": {"temperature": 20.0},
            "daily": {"precipitation_probability_max": [30, 60]},  # [0] is today
        },
        "pirate_weather": _src(
            "pirate_weather", temperature=20.0, precipitation_probability=45.0
        ),
    }

    consensus = service._compute_consensus(results)

    precip = consensus["precipitation_probability"]
    assert precip["min"] == 30.0
    assert precip["max"] == 45.0
    assert precip["spread"] == 15.0
    assert consensus["confidence"] == "high"


# --------------------------------------------------------------------------
# Threshold boundaries
# --------------------------------------------------------------------------

@pytest.mark.parametrize(
    "variable,spread,expected",
    [
        ("temperature", 2.0, "high"),
        ("temperature", 2.1, "medium"),
        ("temperature", 5.0, "medium"),
        ("temperature", 5.1, "low"),
        ("precipitation_probability", 20.0, "high"),
        ("precipitation_probability", 40.0, "medium"),
        ("precipitation_probability", 40.1, "low"),
        ("wind_speed", 10.0, "high"),
        ("wind_speed", 20.0, "medium"),
        ("wind_speed", 20.1, "low"),
    ],
)
def test_confidence_thresholds(service, variable, spread, expected):
    assert service._confidence_level(variable, spread) == expected


# --------------------------------------------------------------------------
# Wiring
# --------------------------------------------------------------------------

async def test_multi_source_response_carries_consensus(service, monkeypatch):
    payloads = {
        "open_meteo": _src("open_meteo", temperature=20.0),
        "met_norway": _src("met_norway", temperature=20.4),
    }

    async def fake_fetch(name, fetcher, lat, lon):
        return payloads.get(name)

    monkeypatch.setattr(service, "_safe_fetch", fake_fetch)

    result = await service.get_multi_source_weather(
        51.5, -0.12, sources=["open_meteo", "met_norway"]
    )

    consensus = result["consensus"]
    assert consensus["temperature"]["source_count"] == 2
    assert consensus["confidence"] == "high"


async def test_multi_source_consensus_is_null_when_sources_fail(service, monkeypatch):
    async def fake_fetch(name, fetcher, lat, lon):
        return _src("open_meteo", temperature=20.0) if name == "open_meteo" else None

    monkeypatch.setattr(service, "_safe_fetch", fake_fetch)

    result = await service.get_multi_source_weather(51.5, -0.12)

    assert result["consensus"] is None


async def test_pirate_weather_extracts_daily_precipitation_probability():
    """The Dark Sky daily block is already in the response; surfacing today's
    precipProbability costs no extra request and feeds the consensus."""
    svc = MultiSourceWeatherService(config={"PIRATE_WEATHER_API_KEY": "test-key"})
    svc._client = _StubClient(
        {
            "currently": {"temperature": 18.4, "windSpeed": 5.0},
            "daily": {"data": [{"precipProbability": 0.35}]},
        }
    )

    result = await svc._fetch_pirate_weather(40.7, -74.0)

    assert result["current"]["precipitation_probability"] == 35.0


async def test_pirate_weather_precipitation_is_null_without_daily_block():
    svc = MultiSourceWeatherService(config={"PIRATE_WEATHER_API_KEY": "test-key"})
    svc._client = _StubClient({"currently": {"temperature": 18.4}})

    result = await svc._fetch_pirate_weather(40.7, -74.0)

    assert result["current"]["precipitation_probability"] is None
