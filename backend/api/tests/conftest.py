"""Shared pytest fixtures.

The suite runs against a real Postgres rather than SQLite. Several routers issue
Postgres-only SQL — jsonb casts in the notification handlers, `= ANY(:array)` in
the device-token update — so SQLite would happily pass tests that say nothing
about production behaviour.

Point TEST_DATABASE_URL at any Postgres to override the default. The database is
created if missing and the two schema files are applied to it; tables are
truncated between tests.
"""
import asyncio
import os
from pathlib import Path
from urllib.parse import urlparse, urlunparse

import asyncpg
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

BACKEND_DIR = Path(__file__).resolve().parents[2]
SCHEMA_FILES = (BACKEND_DIR / "init.sql", BACKEND_DIR / "002_add_features.sql")

TEST_DATABASE_URL = os.getenv(
    "TEST_DATABASE_URL",
    "postgresql://climaai:climaai123@localhost:5432/climaai_test",
)

# Every table the schema creates, ordered so truncation is unambiguous.
TABLES = (
    "weather_alerts",
    "device_tokens",
    "favorite_locations",
    "weather_history",
    "subscriptions",
    "users",
)


def _maintenance_dsn(dsn: str) -> str:
    """Same server, but the `postgres` database, for CREATE DATABASE."""
    parts = urlparse(dsn)
    return urlunparse(parts._replace(path="/postgres"))


def _database_name(dsn: str) -> str:
    return urlparse(dsn).path.lstrip("/")


async def _provision() -> None:
    """Create the test database if absent and apply the schema files."""
    admin = await asyncpg.connect(_maintenance_dsn(TEST_DATABASE_URL))
    try:
        name = _database_name(TEST_DATABASE_URL)
        exists = await admin.fetchval("SELECT 1 FROM pg_database WHERE datname = $1", name)
        if not exists:
            # Identifier cannot be parameterised; name comes from our own config.
            await admin.execute(f'CREATE DATABASE "{name}"')
    finally:
        await admin.close()

    conn = await asyncpg.connect(TEST_DATABASE_URL)
    try:
        for path in SCHEMA_FILES:
            # asyncpg's execute() uses the simple query protocol when given no
            # arguments, so multi-statement scripts run as-is.
            await conn.execute(path.read_text())
    finally:
        await conn.close()


@pytest.fixture(scope="session")
def database():
    """Provision the test database once per session.

    Deliberately not autouse: tests that need no database (weather parsing, for
    instance) must still run on a machine with no Postgres.

    Uses asyncio.run() from a sync fixture to sidestep pytest-asyncio's
    event-loop scoping rules for session-scoped async fixtures.
    """
    try:
        asyncio.run(_provision())
    except (asyncpg.PostgresError, OSError) as exc:
        message = (
            f"Cannot reach Postgres at {TEST_DATABASE_URL!r}: {exc}. "
            "Start Postgres or set TEST_DATABASE_URL."
        )
        # In CI a missing database is a failure, never a silent skip — a green
        # run with everything skipped is worse than a red one.
        if os.getenv("CI"):
            pytest.fail(message, pytrace=False)
        pytest.skip(message, allow_module_level=True)
    yield


@pytest.fixture(scope="session")
def engine(database):
    # NullPool is required: pytest-asyncio gives each test its own event loop,
    # and pooled asyncpg connections cannot cross loops.
    return create_async_engine(
        TEST_DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://"),
        poolclass=NullPool,
    )


@pytest_asyncio.fixture
async def db_session(engine):
    """A session bound to the test database, with tables cleared first."""
    async with engine.begin() as conn:
        from sqlalchemy import text

        await conn.execute(
            text(f"TRUNCATE {', '.join(TABLES)} RESTART IDENTITY CASCADE")
        )

    factory = async_sessionmaker(engine, expire_on_commit=False, autoflush=False)
    async with factory() as session:
        yield session


@pytest_asyncio.fixture
async def client(db_session):
    """HTTP client for the app, with get_db pointed at the test database.

    ASGITransport does not run lifespan events, so the app's own engine (which
    targets the development database) is never touched.
    """
    from app.database import get_db
    from app.main import app

    async def override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()


@pytest_asyncio.fixture
async def auth_client(client):
    """A client already registered and carrying a bearer token."""
    payload = {
        "email": "fixture-user@example.com",
        "password": "Test1234!",
        "full_name": "Fixture User",
        "platform": "ios",
    }
    response = await client.post("/api/auth/register", json=payload)
    assert response.status_code == 201, response.text
    token = response.json()["access_token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return client
