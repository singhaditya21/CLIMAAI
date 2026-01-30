"""
Weather API router.
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from typing import Optional
from ..models import User
from ..schemas.weather import WeatherResponse
from ..services.weather_service import WeatherService
from ..services.auth import get_optional_user
from ..services.subscription_service import SubscriptionService
from ..database import get_db
from sqlalchemy.ext.asyncio import AsyncSession

router = APIRouter(prefix="/weather", tags=["weather"])


@router.get("/current", response_model=WeatherResponse)
async def get_current_weather(
    latitude: float = Query(..., ge=-90, le=90, description="Latitude"),
    longitude: float = Query(..., ge=-180, le=180, description="Longitude"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Get current weather and forecast.
    
    Free tier includes:
    - Current weather
    - 24-hour hourly forecast
    - 7-day daily forecast
    - Basic air quality
    
    Premium includes:
    - 16-day forecast
    - Detailed air quality breakdown
    """
    weather_service = WeatherService()
    
    try:
        # Get weather data
        weather = await weather_service.get_current_weather(latitude, longitude)
        
        # If user is not premium, limit forecast days
        if current_user:
            subscription_service = SubscriptionService()
            sub_status = await subscription_service.check_subscription_status(current_user, db)
            
            if not sub_status.is_premium:
                # Limit to 7 days for free users
                weather.daily = weather.daily[:7]
        else:
            # Not authenticated, limit to 7 days
            weather.daily = weather.daily[:7]
        
        return weather
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()


@router.get("/hourly", response_model=dict)
async def get_hourly_forecast(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    hours: int = Query(24, ge=1, le=168, description="Number of hours (max 168 for premium)"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Get hourly forecast.
    
    Free: 24 hours
    Premium: Up to 7 days (168 hours)
    """
    # Check subscription for extended hours
    max_hours = 24
    if current_user:
        subscription_service = SubscriptionService()
        sub_status = await subscription_service.check_subscription_status(current_user, db)
        if sub_status.is_premium:
            max_hours = 168
    
    if hours > max_hours:
        raise HTTPException(
            status_code=403,
            detail=f"Free tier limited to {max_hours} hours. Upgrade to premium for extended forecast."
        )
    
    weather_service = WeatherService()
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        return {
            "hourly": weather.hourly[:hours],
            "timezone": weather.timezone,
            "location": weather.location,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()


@router.get("/daily", response_model=dict)
async def get_daily_forecast(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    days: int = Query(7, ge=1, le=16, description="Number of days (max 16 for premium)"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Get daily forecast.
    
    Free: 7 days
    Premium: Up to 16 days
    """
    # Check subscription for extended days
    max_days = 7
    if current_user:
        subscription_service = SubscriptionService()
        sub_status = await subscription_service.check_subscription_status(current_user, db)
        if sub_status.is_premium:
            max_days = 16
    
    if days > max_days:
        raise HTTPException(
            status_code=403,
            detail=f"Free tier limited to {max_days} days. Upgrade to premium for extended forecast."
        )
    
    weather_service = WeatherService()
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        return {
            "daily": weather.daily[:days],
            "timezone": weather.timezone,
            "location": weather.location,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()


@router.get("/air-quality", response_model=dict)
async def get_air_quality(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
):
    """
    Get air quality data.
    
    Available to all users.
    """
    weather_service = WeatherService()
    
    try:
        air_quality = await weather_service._get_air_quality(latitude, longitude)
        if not air_quality:
            raise HTTPException(status_code=404, detail="Air quality data not available for this location")
        
        return {
            "air_quality": air_quality,
            "location": {
                "latitude": latitude,
                "longitude": longitude,
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()
