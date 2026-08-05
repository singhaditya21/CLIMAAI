"""
Health API router - Pollen, Air Quality, Activities, and Health insights.
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from datetime import date
from ..schemas.pollen import PollenForecastResponse, PollenTypeResponse, DailyPollenResponse, PollenLevel
from ..services.pollen_service import PollenService, get_pollen_service
from ..services.health_index_service import HealthIndexService
from ..services.activity_service import ActivityForecastService, ActivityType
from ..services.weather_service import WeatherService, get_weather_service
from ..database import get_db, require_db
from sqlalchemy.ext.asyncio import AsyncSession

router = APIRouter(prefix="/health", tags=["health"])


@router.get("/pollen", response_model=PollenForecastResponse)
async def get_pollen_forecast(
    latitude: float = Query(..., ge=-90, le=90, description="Latitude"),
    longitude: float = Query(..., ge=-180, le=180, description="Longitude"),
    days: int = Query(5, ge=1, le=5, description="Forecast days (1-5)"),
    pollen_service: PollenService = Depends(get_pollen_service),
):
    """
    Get pollen forecast for a location.
    
    Returns 5-day forecast with:
    - Tree pollen levels (Oak, Birch, Maple, etc.)
    - Grass pollen levels (Timothy, Bermuda, etc.)
    - Weed pollen levels (Ragweed, Sagebrush, etc.)
    - Health recommendations based on levels
    
    Uses Google Pollen API with 5K free calls/month.
    Falls back to seasonal mock data if API key not configured.
    """
    
    try:
        pollen_data = await pollen_service.get_pollen_forecast(
            latitude=latitude,
            longitude=longitude,
            days=days
        )
        
        # Convert to response schema
        daily_responses = [
            DailyPollenResponse(
                date=day.date,
                tree=PollenTypeResponse(
                    display_name=day.tree.display_name,
                    level=PollenLevel(day.tree.level.value),
                    index=day.tree.index,
                    species=day.tree.species,
                    health_advice=day.tree.health_advice,
                    color=day.tree.color
                ),
                grass=PollenTypeResponse(
                    display_name=day.grass.display_name,
                    level=PollenLevel(day.grass.level.value),
                    index=day.grass.index,
                    species=day.grass.species,
                    health_advice=day.grass.health_advice,
                    color=day.grass.color
                ),
                weed=PollenTypeResponse(
                    display_name=day.weed.display_name,
                    level=PollenLevel(day.weed.level.value),
                    index=day.weed.index,
                    species=day.weed.species,
                    health_advice=day.weed.health_advice,
                    color=day.weed.color
                ),
                overall_index=day.overall_index,
                overall_level=PollenLevel(day.overall_level.value)
            )
            for day in pollen_data.forecast
        ]
        
        return PollenForecastResponse(
            location=pollen_data.location,
            forecast=daily_responses,
            last_updated=pollen_data.last_updated,
            health_recommendations=pollen_data.health_recommendations
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/pollen/today")
async def get_todays_pollen(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    pollen_service: PollenService = Depends(get_pollen_service),
):
    """
    Get today's pollen levels (simplified view).
    
    Returns just today's levels and recommendations.
    """
    
    try:
        pollen_data = await pollen_service.get_pollen_forecast(
            latitude=latitude,
            longitude=longitude,
            days=1
        )
        
        if not pollen_data.forecast:
            raise HTTPException(status_code=404, detail="Pollen data not available")
        
        today = pollen_data.forecast[0]
        
        return {
            "date": today.date.isoformat(),
            "location": pollen_data.location,
            "tree": {
                "level": today.tree.level.value,
                "index": today.tree.index,
                "species": today.tree.species[:3]
            },
            "grass": {
                "level": today.grass.level.value,
                "index": today.grass.index,
                "species": today.grass.species[:3]
            },
            "weed": {
                "level": today.weed.level.value,
                "index": today.weed.index,
                "species": today.weed.species[:3]
            },
            "overall": {
                "level": today.overall_level.value,
                "index": today.overall_index
            },
            "health_recommendations": pollen_data.health_recommendations
        }
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/flu-risk")
async def get_flu_risk(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    weather_service: WeatherService = Depends(get_weather_service),
):
    """
    Get current flu risk assessment based on weather.
    
    Factors:
    - Temperature (cold increases risk)
    - Humidity (dry air increases transmission)
    - Seasonal patterns
    """
    health_service = HealthIndexService()
    
    try:
        # Get current weather
        weather = await weather_service.get_current_weather(latitude, longitude)
        
        flu_risk = health_service.calculate_flu_risk(
            temperature=weather.current.temperature,
            humidity=weather.current.humidity,
            current_date=date.today(),
            latitude=latitude
        )
        
        return {
            "risk_level": flu_risk.risk_level.value,
            "risk_score": flu_risk.risk_score,
            "factors": flu_risk.factors,
            "recommendations": flu_risk.recommendations,
            "seasonal_context": flu_risk.seasonal_context,
            "current_conditions": {
                "temperature": weather.current.temperature,
                "humidity": weather.current.humidity
            }
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/migraine-risk")
async def get_migraine_risk(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    # require_db first: without it this endpoint spent 10s timing out against
    # the unconfigured engine and surfaced a 500, while every sibling answered
    # a clean 503 in degraded mode.
    _db_ok: None = Depends(require_db),
    db: AsyncSession = Depends(get_db),
    weather_service: WeatherService = Depends(get_weather_service),
):
    """
    Get migraine trigger risk based on weather conditions.
    
    Key triggers:
    - Barometric pressure changes
    - Humidity extremes
    - Temperature changes
    """
    health_service = HealthIndexService()
    
    try:
        # Get current weather (and save history)
        weather = await weather_service.get_current_weather(latitude, longitude, db=db)

        # Get real pressure history from DB
        history_records = await weather_service.get_weather_history(latitude, longitude, hours=24, db=db)
        
        if history_records:
            pressure_history = [h.pressure_msl for h in history_records if h.pressure_msl is not None]
        else:
            # Fallback if no history yet (first call)
            pressure_history = [weather.current.pressure]
        
        migraine_risk = health_service.calculate_migraine_risk(
            current_pressure=weather.current.pressure,
            pressure_history=pressure_history,
            humidity=weather.current.humidity,
            temperature=weather.current.temperature
        )
        
        return {
            "risk_level": migraine_risk.risk_level.value,
            "risk_score": migraine_risk.risk_score,
            "pressure_change": migraine_risk.pressure_change,
            "pressure_trend": migraine_risk.pressure_trend,
            "triggers": migraine_risk.triggers,
            "recommendations": migraine_risk.recommendations,
            "current_conditions": {
                "pressure": weather.current.pressure,
                "humidity": weather.current.humidity,
                "temperature": weather.current.temperature
            }
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/activities")
async def get_all_activities(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    weather_service: WeatherService = Depends(get_weather_service),
):
    """
    Get activity forecast for all outdoor activities.
    
    Returns scores for: running, cycling, golf, hiking, beach, skiing, tennis, photography.
    Sorted by today's score (best activities first).
    """
    activity_service = ActivityForecastService()
    
    try:
        # Get hourly forecast
        weather = await weather_service.get_current_weather(latitude, longitude)
        
        # Convert to activity service format
        hourly_data = [
            {
                "time": h.time.isoformat(),
                "temperature": h.temperature,
                "humidity": h.humidity,
                "wind_speed": h.wind_speed,
                "precipitation": h.precipitation,
                "precipitation_probability": h.precipitation_probability,
                "uv_index": h.uv_index,
                "cloud_cover": h.cloud_cover,
                "is_day": True  # Simplified
            }
            for h in weather.hourly
        ]
        
        # Get AQI if available
        current_aqi = weather.air_quality.aqi if weather.air_quality else 50
        
        # Get all activity summaries
        summaries = activity_service.get_all_activities_summary(
            hourly_data, current_aqi
        )
        
        return {
            "location": weather.location,
            "activities": summaries,
            "timestamp": weather.current.timestamp.isoformat()
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/activities/{activity}")
async def get_activity_forecast(
    activity: str,
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    weather_service: WeatherService = Depends(get_weather_service),
):
    """
    Get detailed forecast for a specific activity.
    
    Supported activities: running, cycling, golf, hiking, beach, skiing, tennis, photography.
    """
    # Validate activity type
    try:
        activity_type = ActivityType(activity.lower())
    except ValueError:
        valid_activities = [a.value for a in ActivityType]
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid activity. Valid options: {', '.join(valid_activities)}"
        )
    
    activity_service = ActivityForecastService()
    
    try:
        # Get hourly forecast
        weather = await weather_service.get_current_weather(latitude, longitude)
        
        # Convert to activity service format
        hourly_data = [
            {
                "time": h.time.isoformat(),
                "temperature": h.temperature,
                "humidity": h.humidity,
                "wind_speed": h.wind_speed,
                "precipitation": h.precipitation,
                "precipitation_probability": h.precipitation_probability,
                "uv_index": h.uv_index,
                "cloud_cover": h.cloud_cover,
                "visibility": 10000,
                "is_day": True
            }
            for h in weather.hourly
        ]
        
        current_aqi = weather.air_quality.aqi if weather.air_quality else 50
        
        forecast = activity_service.get_activity_forecast(
            activity_type, hourly_data, current_aqi
        )
        
        return {
            "activity": forecast.activity,
            "display_name": forecast.activity_display_name,
            "today_score": forecast.today_score,
            "today_rating": forecast.today_rating,
            "best_time": {
                "start": forecast.best_time_today.start_time.isoformat() if forecast.best_time_today else None,
                "end": forecast.best_time_today.end_time.isoformat() if forecast.best_time_today else None,
                "score": forecast.best_time_today.score if forecast.best_time_today else None,
                "conditions": forecast.best_time_today.conditions_summary if forecast.best_time_today else None
            },
            "hourly_scores": forecast.hourly_scores[:12],  # First 12 hours
            "recommendations": forecast.recommendations,
            "factors_used": forecast.factors_used,
            "location": weather.location
        }
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
