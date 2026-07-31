"""
ClimaAI FastAPI Application.
Main entry point for the weather API backend.
"""
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
import time
from .config import get_settings
from .database import init_db
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
    await init_db()
    print("✅ Database initialized")
    
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


# Health check
@app.get("/health", tags=["health"])
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "app": settings.APP_NAME,
        "version": settings.APP_VERSION,
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


# Register routers
app.include_router(users_router)
app.include_router(weather_router)
app.include_router(ai_router)
app.include_router(subscriptions_router)
app.include_router(locations_router)
app.include_router(alerts_router)
app.include_router(notifications_router)
app.include_router(personalization_router)
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
