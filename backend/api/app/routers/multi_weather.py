"""
Multi-Source Weather Router
Exposes endpoints for aggregated weather data from 18+ external APIs.
"""

from datetime import datetime, timezone

from fastapi import APIRouter, Query, HTTPException
from typing import Optional
from app.services.multi_weather_service import MultiSourceWeatherService
from app.config import get_settings

router = APIRouter(prefix="/api/weather", tags=["multi-source-weather"])

# Initialize the service with config
settings = get_settings()
weather_service = MultiSourceWeatherService(config={
    "OPENWEATHERMAP_API_KEY": settings.OPENWEATHERMAP_API_KEY,
    "WEATHERBIT_API_KEY": settings.WEATHERBIT_API_KEY,
    "STORMGLASS_API_KEY": settings.STORMGLASS_API_KEY,
    "OPENUV_API_KEY": settings.OPENUV_API_KEY,
    "METEOBLUE_API_KEY": settings.METEOBLUE_API_KEY,
    "NOAA_API_KEY": settings.NOAA_API_KEY,
    "PIRATE_WEATHER_API_KEY": settings.PIRATE_WEATHER_API_KEY,
    "WEATHERAPI_KEY": settings.WEATHERAPI_KEY,
})


@router.get("/multi-source")
async def get_multi_source_weather(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    sources: Optional[str] = Query(None, description="Comma-separated list of sources to query")
):
    """
    Get aggregated weather data from multiple external APIs.
    
    Available sources: open_meteo, 7timer, met_norway, nws, dwd, wttr,
    openweathermap, weatherapi, pirate_weather, weatherbit, stormglass, openuv
    """
    source_list = None
    if sources:
        source_list = [s.strip() for s in sources.split(",")]
    
    try:
        result = await weather_service.get_multi_source_weather(
            latitude=latitude,
            longitude=longitude,
            sources=source_list
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Multi-source weather error: {str(e)}")


@router.get("/uv")
async def get_uv_index(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180)
):
    """Get real-time UV index from OpenUV."""
    result = await weather_service.get_uv_index(latitude, longitude)
    if result is None:
        raise HTTPException(
            status_code=503,
            detail="UV index unavailable. OpenUV API key may not be configured."
        )
    return result


@router.get("/marine")
async def get_marine_weather(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180)
):
    """Get marine weather data from Storm Glass."""
    result = await weather_service.get_marine_weather(latitude, longitude)
    if result is None:
        raise HTTPException(
            status_code=503,
            detail="Marine weather unavailable. Storm Glass API key may not be configured."
        )
    return result


# GET /api/weather/alerts deliberately lives in weather.py, not here. Both
# routers share the /api/weather prefix and weather.py is registered first, so a
# duplicate here never serves a request — but it *does* overwrite the real
# route's entry in the generated OpenAPI schema, which is worse than dead code:
# the published contract then describes a response the API never returns.
@router.get("/alerts/{state}")
async def get_state_weather_alerts(state: str):
    """Get active weather alerts for a US state (e.g., 'CA', 'NY')."""
    if len(state) != 2:
        raise HTTPException(status_code=400, detail="State must be a 2-letter code (e.g., 'CA')")
    return await weather_service.get_nws_state_alerts(state.upper())


@router.get("/historical")
async def get_historical_weather(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    start_date: str = Query(..., description="Start date in YYYY-MM-DD format"),
    end_date: str = Query(..., description="End date in YYYY-MM-DD format")
):
    """Get historical weather data from Open-Meteo Archive API."""
    return await weather_service.get_historical_weather(latitude, longitude, start_date, end_date)


@router.get("/usage")
async def get_api_usage():
    """
    Get current rate limit usage for all external weather API sources.
    Shows daily budget, calls used today, and remaining quota.
    """
    usage = weather_service.get_rate_limit_usage()
    return {
        "date": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
        "sources": usage,
        "summary": {
            "total_used": sum(s["used"] for s in usage.values()),
            "total_budget": sum(s["limit"] for s in usage.values()),
            "sources_exhausted": [k for k, v in usage.items() if v["remaining"] <= 0],
        }
    }
