"""
ClimaAI FastAPI Application.
Main entry point for the weather API backend.
"""
from fastapi import Depends, FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
import asyncio
import time
import redis.asyncio as redis
from .config import get_settings
from .database import database_status, init_db, require_db
from .services.weather_service import get_weather_service, close_weather_service
from .services.radar_service import get_radar_service, close_radar_service
from .services.pollen_service import get_pollen_service, close_pollen_service
from .services.alerts_service import get_alerts_service, close_alerts_service
from .services.nowcast_service import get_nowcast_service, close_nowcast_service
from .routers import (
    users_router,
    weather_router,
    ai_router,
    subscriptions_router,
    locations_router,
    alerts_router,
    notifications_router
)
from .routers.personalization import router as personalization_router
from .routers.precipitation import router as precipitation_router
from .routers.health import router as health_router
from .routers.demo import router as demo_router
from .routers.multi_weather import router as multi_weather_router

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan events."""
    # Startup
    print("🚀 Starting ClimaAI API...")
    # init_db reports an absent/unreachable database instead of raising: the
    # first deploy on a fresh project has an empty DATABASE_URL secret, and a
    # crash loop here would read as a broken pipeline. Weather and consensus
    # need no DB; the DB-backed routers answer 503 via require_db.
    if await init_db():
        print("✅ Database initialized")
    else:
        print("⚠️ Running degraded: DB-backed endpoints return 503 until DATABASE_URL is set")

    # Initialize weather service
    get_weather_service()
    print("✅ Weather service initialized")

    # Initialize radar service
    get_radar_service()
    print("✅ Radar service initialized")

    # Remaining shared services — each holds a pooled HTTP client and a Redis
    # connection, so they are created once rather than per request.
    get_pollen_service()
    get_alerts_service()
    get_nowcast_service()
    print("✅ Pollen, alerts and nowcast services initialized")

    yield

    # Shutdown
    print("👋 Shutting down ClimaAI API...")
    await close_weather_service()
    print("✅ Weather service closed")

    await close_radar_service()
    print("✅ Radar service closed")

    await close_pollen_service()
    await close_alerts_service()
    await close_nowcast_service()
    print("✅ Pollen, alerts and nowcast services closed")


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="AI-powered weather insights API for ClimaAI mobile apps",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Request timing middleware
@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    """Add processing time to response headers."""
    start_time = time.time()
    response = await call_next(request)
    process_time = time.time() - start_time
    response.headers["X-Process-Time"] = str(process_time)
    return response


# Exception handlers
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """Global exception handler."""
    print(f"❌ Unhandled exception: {exc}")
    return JSONResponse(
        status_code=500,
        content={
            "detail": "Internal server error",
            "error": str(exc) if settings.DEBUG else "Something went wrong"
        }
    )


async def _redis_status() -> str:
    """Component state for /health: 'ok' or 'unavailable'."""
    url = settings.REDIS_URL.strip()
    if not url:
        return "unavailable"
    client = redis.from_url(url)
    try:
        await asyncio.wait_for(client.ping(), timeout=2.0)
        return "ok"
    except Exception:
        return "unavailable"
    finally:
        await client.aclose()


# Health check
@app.get("/health", tags=["health"])
async def health_check():
    """Health check with per-component status.

    Answers 200 whenever the process serves traffic: Cloud Run must not kill a
    container that is up but still waiting for its database to be configured.
    The components object is where degradation shows.
    """
    return {
        "status": "healthy",
        "app": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "components": {
            "api": "ok",
            "database": await database_status(),
            "redis": await _redis_status(),
        },
    }


@app.get("/", tags=["root"])
async def root():
    """Root endpoint."""
    return {
        "app": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "docs": "/docs",
        "message": "Welcome to ClimaAI API 🌤️"
    }


# Register routers. Routers whose every endpoint needs the database (directly
# or through get_current_user) carry the require_db guard: when the app booted
# without a database they answer 503 with a clear detail instead of surfacing
# a connect timeout as a 500 deep inside a handler. Weather, consensus,
# precipitation and the health indices stay fully functional without a DB.
_db_backed = [Depends(require_db)]
app.include_router(users_router, dependencies=_db_backed)
app.include_router(weather_router)
# AI insights are premium-gated, and verifying premium needs the database.
app.include_router(ai_router, dependencies=_db_backed)
app.include_router(subscriptions_router, dependencies=_db_backed)
app.include_router(locations_router, dependencies=_db_backed)
app.include_router(alerts_router, dependencies=_db_backed)
app.include_router(notifications_router, dependencies=_db_backed)
app.include_router(personalization_router, dependencies=_db_backed)
app.include_router(precipitation_router)
app.include_router(health_router)
app.include_router(multi_weather_router)

# /demo serves generated mock data and is opt-in so it never ships in production
if settings.DEMO_MODE:
    app.include_router(demo_router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.DEBUG,
    )
