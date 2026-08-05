"""
Database configuration with SQLAlchemy async engine.
Provides session management, degraded-mode state and base models.
"""
import asyncio
from fastapi import HTTPException
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy import Column, DateTime, func, text
from typing import AsyncGenerator
from .config import get_settings

settings = get_settings()

# On a fresh GCP project the Secret Manager DATABASE_URL exists but is empty,
# and create_async_engine("") raises at import time — turning "no database yet"
# into a crash loop before the lifespan even runs. The sentinel is an RFC 2606
# reserved TLD: syntactically valid, guaranteed to fail DNS fast, impossible to
# mistake for a real host.
_UNCONFIGURED_URL = "postgresql+asyncpg://unconfigured.invalid/unconfigured"
_configured_url = settings.DATABASE_URL.strip()

# asyncpg's default connect timeout is 60s, which multiplies into Cloud Run's
# readiness window when the host is unreachable. Module-level (not Settings) so
# tests can shrink it without rebuilding the cached settings object.
DB_STARTUP_TIMEOUT = 10.0

# Create async engine
engine_args = {
    "echo": settings.DEBUG,
    "pool_pre_ping": True,
}

if "sqlite" in settings.DATABASE_URL:
    engine_args["connect_args"] = {"check_same_thread": False}
else:
    engine_args["pool_size"] = 10
    engine_args["max_overflow"] = 20
    # Bounds how long any request that does touch an unreachable database can
    # stall before erroring, instead of asyncpg's 60s default.
    engine_args["connect_args"] = {"timeout": DB_STARTUP_TIMEOUT}

engine = create_async_engine(
    _configured_url or _UNCONFIGURED_URL,
    **engine_args
)

# Session factory
AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False,
)

# Set by init_db() at startup. Defaults to True because httpx's ASGITransport
# never runs the lifespan: the test suite overrides get_db and must not trip
# the 503 guard. A failed startup probe flips it for the process lifetime —
# on Cloud Run, configuring DATABASE_URL redeploys and therefore reboots.
_db_available = True


def is_db_available() -> bool:
    """Whether the startup probe reached the database."""
    return _db_available


async def require_db() -> None:
    """Router-level guard for DB-backed routers.

    When the app booted without a database this answers an honest 503 up front,
    instead of letting each handler discover the outage via a connect timeout.
    """
    if not _db_available:
        raise HTTPException(
            status_code=503,
            detail=(
                "This endpoint needs a database and none is configured or "
                "reachable. Set the DATABASE_URL secret and redeploy; "
                "weather and consensus endpoints work without it."
            ),
        )


async def database_status() -> str:
    """Component state for /health: 'ok' or 'unavailable'.

    When startup already found no database, answer from the flag without
    probing — probing an unreachable host would add a connect timeout to every
    health check Cloud Run sends.
    """
    if not _db_available:
        return "unavailable"

    async def _ping():
        async with engine.connect() as conn:
            await conn.execute(text("SELECT 1"))

    try:
        await asyncio.wait_for(_ping(), timeout=5.0)
        return "ok"
    except Exception:
        return "unavailable"


class Base(DeclarativeBase):
    """Base model with common timestamp fields."""
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """Dependency for getting database sessions."""
    async with AsyncSessionLocal() as session:
        try:
            yield session
            # In degraded mode the commit would be the session's first network
            # touch: skip it so DB-optional endpoints (weather history
            # piggybacking) return fast instead of waiting out a timeout.
            if _db_available:
                await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


async def init_db() -> bool:
    """Initialize database tables; report reachability instead of raising.

    A fresh deployment has an empty DATABASE_URL secret and no database behind
    it. Crashing here would crash-loop the first deploy and teach the user the
    pipeline is broken when it isn't — so a missing or unreachable database
    flips the process into degraded mode and startup continues.
    """
    global _db_available

    if not _configured_url:
        _db_available = False
        print("⚠️ DATABASE_URL is empty — starting without a database")
        return False

    async def _create_all():
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)

    try:
        await asyncio.wait_for(_create_all(), timeout=DB_STARTUP_TIMEOUT)
    except Exception as exc:
        # Broad by design: DNS failure, refused TCP, bad credentials and the
        # wait_for timeout must all land in the same degraded mode.
        _db_available = False
        print(f"⚠️ Database unreachable ({exc!r}) — starting degraded")
        # wait_for can abandon a half-open connection; drop it.
        await engine.dispose()
        return False

    _db_available = True
    return True
