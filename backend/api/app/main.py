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

    yield
    
    # Shutdown
    print("👋 Shutting down ClimaAI API...")
    await close_weather_service()
    print("✅ Weather service closed")


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
app.include_router(demo_router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.DEBUG,
    )
