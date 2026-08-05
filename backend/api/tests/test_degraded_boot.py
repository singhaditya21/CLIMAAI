"""Degraded boot: the app must serve without a reachable database.

On a fresh GCP project the Secret Manager DATABASE_URL is empty and the first
deploy has no database behind it. A crash-looping container would teach the
user the pipeline is broken when it isn't. The contract under test:

  * startup completes against an unroutable database host,
  * /health answers 200 and reports honest per-component states,
  * DB-backed routers answer 503 with a clear detail,
  * the DB-free weather path keeps returning 200.

No fixture from conftest is used: those need a real Postgres, which is exactly
what this file must run without.
"""
from datetime import datetime, timezone

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

# RFC 5737 TEST-NET-1 — guaranteed never routed, so connection attempts hang
# until a timeout rather than being refused: the worst case for boot.
UNROUTABLE_DB_URL = "postgresql+asyncpg://climaai:wrong@192.0.2.1:5432/climaai"


def _weather_fixture():
    from app.schemas.weather import (
        CurrentWeather,
        DailyWeather,
        HourlyWeather,
        WeatherResponse,
    )

    now = datetime.now(timezone.utc)
    return WeatherResponse(
        current=CurrentWeather(
            temperature=21.0,
            feels_like=21.5,
            humidity=50,
            wind_speed=3.0,
            wind_direction=180,
            precipitation=0.0,
            weather_code=1,
            weather_description="Mainly clear",
            cloud_cover=20,
            pressure=1013.0,
            visibility=10000.0,
            uv_index=3.0,
            is_day=True,
            timestamp=now,
        ),
        hourly=[
            HourlyWeather(
                time=now,
                temperature=21.0,
                feels_like=21.5,
                precipitation_probability=10,
                precipitation=0.0,
                weather_code=1,
                weather_description="Mainly clear",
                wind_speed=3.0,
                wind_direction=180,
                humidity=50,
                cloud_cover=20,
                uv_index=3.0,
            )
        ],
        daily=[
            DailyWeather(
                date="2026-08-05",
                temperature_max=25.0,
                temperature_min=15.0,
                sunrise="06:00",
                sunset="20:00",
                precipitation_sum=0.0,
                precipitation_probability=10,
                weather_code=1,
                weather_description="Mainly clear",
                wind_speed_max=5.0,
                wind_direction=180,
                uv_index_max=5.0,
            )
        ],
        location={"latitude": 40.7, "longitude": -74.0},
        timezone="UTC",
    )


class _StubWeatherService:
    """The upstream provider is not under test — the degraded wiring is."""

    async def get_current_weather(self, latitude, longitude, use_cache=True, db=None):
        return _weather_fixture()


@pytest_asyncio.fixture
async def degraded_client(monkeypatch):
    from app import database
    from app.main import app
    from app.services.weather_service import get_weather_service

    bad_engine = create_async_engine(UNROUTABLE_DB_URL, poolclass=NullPool)
    monkeypatch.setattr(database, "engine", bad_engine)
    monkeypatch.setattr(
        database,
        "AsyncSessionLocal",
        async_sessionmaker(bad_engine, expire_on_commit=False, autoflush=False),
    )
    # Setting the flag to its current value registers it with monkeypatch, so
    # the flip made by init_db during boot is undone for the rest of the suite.
    monkeypatch.setattr(database, "_db_available", database._db_available)
    # The blackholed host above never answers; keep the startup probe short.
    monkeypatch.setattr(database, "DB_STARTUP_TIMEOUT", 0.5)

    app.dependency_overrides[get_weather_service] = _StubWeatherService
    try:
        # ASGITransport never runs lifespan events, so run them explicitly:
        # "the app boots with no database" is the claim under test.
        async with app.router.lifespan_context(app):
            assert database.is_db_available() is False
            transport = ASGITransport(app=app)
            async with AsyncClient(transport=transport, base_url="http://test") as ac:
                yield ac
    finally:
        app.dependency_overrides.pop(get_weather_service, None)
        await bad_engine.dispose()


async def test_health_stays_200_and_reports_components(degraded_client):
    response = await degraded_client.get("/health")

    assert response.status_code == 200
    body = response.json()
    # Cloud Run must not kill a container that is up but waiting for its
    # database — top-level status stays healthy, the components tell the truth.
    assert body["status"] == "healthy"
    assert body["components"]["api"] == "ok"
    assert body["components"]["database"] == "unavailable"
    # Redis may or may not exist on the machine running this suite; the
    # contract is only that the key is present and honest.
    assert body["components"]["redis"] in ("ok", "unavailable")


async def test_db_backed_router_answers_503(degraded_client):
    response = await degraded_client.post(
        "/api/auth/register",
        json={
            "email": "degraded@example.com",
            "password": "Test1234!",
            "full_name": "Degraded Boot",
            "platform": "ios",
        },
    )

    assert response.status_code == 503
    detail = response.json()["detail"]
    assert "database" in detail.lower()
    assert "DATABASE_URL" in detail


async def test_weather_still_serves_without_database(degraded_client):
    response = await degraded_client.get(
        "/api/weather/current", params={"latitude": 40.7, "longitude": -74.0}
    )

    assert response.status_code == 200
    body = response.json()
    assert body["current"]["temperature"] == 21.0
    assert len(body["daily"]) == 1
