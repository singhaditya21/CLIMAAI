"""
Precipitation nowcast router.
Provides "rain in X minutes" style alerts.
"""
from fastapi import APIRouter, Depends, Query
from typing import Optional
from pydantic import BaseModel
from ..services.weather_service import WeatherService


router = APIRouter(prefix="/api/v1/weather", tags=["precipitation"])


class PrecipitationNowcast(BaseModel):
    """Precipitation nowcast response."""
    has_precipitation: bool
    precipitation_in_minutes: Optional[int] = None
    precipitation_ends_in_minutes: Optional[int] = None
    intensity: str  # none, light, moderate, heavy
    precipitation_type: str  # none, rain, snow, mixed
    probability: int  # 0-100
    summary: str


def get_weather_service() -> WeatherService:
    """Dependency to get weather service instance."""
    return WeatherService()


def _get_precipitation_type(weather_code: int) -> str:
    """Determine precipitation type from WMO weather code."""
    if weather_code in [51, 53, 55, 61, 63, 65, 80, 81, 82]:
        return "rain"
    elif weather_code in [71, 73, 75, 77, 85, 86]:
        return "snow"
    elif weather_code in [56, 57, 66, 67]:
        return "mixed"
    elif weather_code in [95, 96, 99]:
        return "rain"  # Thunderstorm
    return "none"


def _get_intensity(weather_code: int, precipitation_mm: float) -> str:
    """Determine precipitation intensity."""
    if precipitation_mm == 0:
        return "none"
    
    # Light: codes ending in 1, 3, 5 for drizzle/slight
    if weather_code in [51, 61, 71, 80, 85]:
        return "light"
    # Moderate
    elif weather_code in [53, 63, 73, 81]:
        return "moderate"
    # Heavy
    elif weather_code in [55, 65, 75, 82, 86, 95, 96, 99]:
        return "heavy"
    
    # Fallback based on mm
    if precipitation_mm < 0.5:
        return "light"
    elif precipitation_mm < 2.5:
        return "moderate"
    return "heavy"


@router.get("/nowcast", response_model=PrecipitationNowcast)
async def get_precipitation_nowcast(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    service: WeatherService = Depends(get_weather_service)
) -> PrecipitationNowcast:
    """
    Get precipitation nowcast for the next 2 hours.
    
    Analyzes hourly forecast data to predict when precipitation will
    start or stop, providing "Rain in X minutes" style alerts.
    """
    # Fetch weather data
    weather = await service.get_current_weather(latitude, longitude)
    hourly = weather.hourly[:6]  # Next 6 hours
    
    if not hourly:
        return PrecipitationNowcast(
            has_precipitation=False,
            intensity="none",
            precipitation_type="none",
            probability=0,
            summary="No forecast data available"
        )
    
    # Check current precipitation
    current_precip = hourly[0].precipitation > 0
    current_prob = hourly[0].precipitation_probability
    current_code = hourly[0].weather_code
    
    # Find when precipitation starts/stops
    precipitation_in_minutes = None
    precipitation_ends_in_minutes = None
    
    if current_precip:
        # Currently precipitating - find when it stops
        for i, hour in enumerate(hourly[1:], 1):
            if hour.precipitation == 0 and hour.precipitation_probability < 30:
                precipitation_ends_in_minutes = i * 60  # Hours to minutes
                break
    else:
        # Not currently precipitating - find when it starts
        for i, hour in enumerate(hourly):
            if hour.precipitation > 0 or hour.precipitation_probability >= 50:
                precipitation_in_minutes = i * 60
                current_code = hour.weather_code
                current_prob = hour.precipitation_probability
                break
    
    # Determine type and intensity
    precip_type = _get_precipitation_type(current_code)
    intensity = _get_intensity(current_code, hourly[0].precipitation if current_precip else 0)
    
    # Build summary
    if current_precip:
        if precipitation_ends_in_minutes:
            if precipitation_ends_in_minutes <= 60:
                summary = f"{precip_type.title()} stopping in about {precipitation_ends_in_minutes} minutes"
            else:
                hours = precipitation_ends_in_minutes // 60
                summary = f"{precip_type.title()} expected for {hours} more hour{'s' if hours > 1 else ''}"
        else:
            summary = f"{intensity.title()} {precip_type} continuing"
    elif precipitation_in_minutes is not None:
        if precipitation_in_minutes == 0:
            summary = f"{precip_type.title()} starting very soon"
        elif precipitation_in_minutes <= 60:
            summary = f"{precip_type.title()} expected in {precipitation_in_minutes} minutes"
        else:
            hours = precipitation_in_minutes // 60
            summary = f"{precip_type.title()} expected in {hours} hour{'s' if hours > 1 else ''}"
    else:
        summary = "No precipitation expected in the next 6 hours"
    
    return PrecipitationNowcast(
        has_precipitation=current_precip or precipitation_in_minutes is not None,
        precipitation_in_minutes=precipitation_in_minutes,
        precipitation_ends_in_minutes=precipitation_ends_in_minutes if current_precip else None,
        intensity=intensity if current_precip else "none",
        precipitation_type=precip_type,
        probability=current_prob,
        summary=summary
    )
