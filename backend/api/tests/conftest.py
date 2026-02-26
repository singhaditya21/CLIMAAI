import pytest
import pytest_asyncio
import asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.pool import StaticPool
import os
import sys

# Ensure app is importable
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app.database import Base, get_db
from app.main import app
from app.config import get_settings, Settings
# Import models to register them with Base
from app.models import User, Subscription, WeatherHistory

TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

@pytest_asyncio.fixture(scope="session")
async def test_engine():
    """Create a test engine that persists for the entire session."""
    engine = create_async_engine(
        TEST_DATABASE_URL,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    yield engine

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)

    await engine.dispose()

@pytest_asyncio.fixture(scope="function")
async def db_session(test_engine):
    """Create a new database session for a test."""
    TestingSessionLocal = async_sessionmaker(
        test_engine,
        class_=AsyncSession,
        expire_on_commit=False,
        autoflush=False,
        autocommit=False,
    )

    async with TestingSessionLocal() as session:
        yield session
        # Rollback transaction after test
        await session.rollback()

@pytest_asyncio.fixture(scope="function")
async def client(db_session):
    """Create a test client with overridden dependencies."""

    async def override_get_db():
        yield db_session

    def override_get_settings():
        return Settings(
            DATABASE_URL=TEST_DATABASE_URL,
            JWT_SECRET="test-secret",
            OPENAI_API_KEY="sk-test-key"
        )

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_settings] = override_get_settings

    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as ac:
        yield ac

    app.dependency_overrides.clear()
