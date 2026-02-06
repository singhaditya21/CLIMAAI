"""
Weather API router.
"""
from fastapi import APIRouter, Depends, HTTPException, Query, Response
from typing import Optional
from ..models import User
from ..schemas.weather import WeatherResponse
from ..schemas.nowcast import NowcastResponse
from ..schemas.radar_alerts import RadarFramesResponse, RadarFrame, AlertsListResponse, WeatherAlertResponse
from ..services.weather_service import WeatherService, get_weather_service
from ..services.nowcast_service import NowcastService
from ..services.radar_service import RadarService
from ..services.alerts_service import AlertsService
from ..services.auth import get_optional_user
from ..services.subscription_service import SubscriptionService
from ..database import get_db
from sqlalchemy.ext.asyncio import AsyncSession
from datetime import datetime

router = APIRouter(prefix="/api/weather", tags=["weather"])


@router.get("/current", response_model=WeatherResponse)
async def get_current_weather(
    latitude: float = Query(..., ge=-90, le=90, description="Latitude"),
    longitude: float = Query(..., ge=-180, le=180, description="Longitude"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
    weather_service: WeatherService = Depends(get_weather_service)
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
    
    try:
        # Get weather data (pass db for history tracking)
        weather = await weather_service.get_current_weather(latitude, longitude, db=db)
        
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


@router.get("/hourly", response_model=dict)
async def get_hourly_forecast(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    hours: int = Query(24, ge=1, le=168, description="Number of hours (max 168 for premium)"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
    weather_service: WeatherService = Depends(get_weather_service)
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
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        return {
            "hourly": weather.hourly[:hours],
            "timezone": weather.timezone,
            "location": weather.location,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/daily", response_model=dict)
async def get_daily_forecast(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    days: int = Query(7, ge=1, le=16, description="Number of days (max 16 for premium)"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
    weather_service: WeatherService = Depends(get_weather_service)
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
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        return {
            "daily": weather.daily[:days],
            "timezone": weather.timezone,
            "location": weather.location,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/air-quality", response_model=dict)
async def get_air_quality(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    weather_service: WeatherService = Depends(get_weather_service)
):
    """
    Get air quality data.
    
    Available to all users.
    """
    
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


@router.get("/nowcast", response_model=NowcastResponse)
async def get_nowcast(
    latitude: float = Query(..., ge=-90, le=90, description="Latitude"),
    longitude: float = Query(..., ge=-180, le=180, description="Longitude"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Get minute-by-minute precipitation nowcast.
    
    Similar to AccuWeather's MinuteCast® - provides:
    - 2-hour (120 minutes) precipitation forecast
    - Minute-by-minute precipitation amount and probability
    - Precipitation start/stop times
    - Human-readable summary ("Rain starting in 15 minutes")
    
    **Premium feature** - Available to Premium and Pro subscribers.
    Free users get a limited preview (30 minutes instead of 120).
    """
    nowcast_service = NowcastService()
    
    try:
        # Get full nowcast
        nowcast = await nowcast_service.get_nowcast(latitude, longitude)
        
        # Limit to 30 minutes for free users
        is_premium = False
        if current_user:
            subscription_service = SubscriptionService()
            sub_status = await subscription_service.check_subscription_status(current_user, db)
            is_premium = sub_status.is_premium
        
        if not is_premium:
            # Free tier: only 30 minutes
            nowcast.minutes = nowcast.minutes[:30]
            if nowcast.precipitation_start and nowcast.minutes:
                # Check if start is beyond preview window
                preview_end = nowcast.minutes[-1].time if nowcast.minutes else None
                if preview_end and nowcast.precipitation_start > preview_end:
                    nowcast.summary = "Upgrade to Premium for 2-hour precipitation forecast."
        
        return nowcast
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await nowcast_service.close()


@router.get("/radar/frames", response_model=RadarFramesResponse)
async def get_radar_frames():
    """
    Get available radar frames for animation.
    
    Returns list of past (actual) and nowcast (predicted) radar frames.
    Use the tile_url_template to construct tile URLs:
    {host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png
    
    Example: https://tilecache.rainviewer.com/v2/radar/1706695200/256/5/16/11/2/1_1.png
    """
    radar_service = RadarService()
    
    try:
        frames = await radar_service.get_radar_frames()
        
        # Add datetime strings for convenience
        past_frames = [
            RadarFrame(
                time=f.time,
                path=f.path,
                datetime_str=datetime.utcfromtimestamp(f.time).isoformat() + "Z"
            )
            for f in frames.past
        ]
        
        nowcast_frames = [
            RadarFrame(
                time=f.time,
                path=f.path,
                datetime_str=datetime.utcfromtimestamp(f.time).isoformat() + "Z"
            )
            for f in frames.nowcast
        ]
        
        return RadarFramesResponse(
            host=frames.host,
            generated=frames.generated,
            past=past_frames,
            nowcast=nowcast_frames,
            tile_url_template="{host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png"
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await radar_service.close()


@router.get("/radar/tile/{path:path}/{z}/{x}/{y}")
async def get_radar_tile(
    path: str,
    z: int,
    x: int,
    y: int,
    size: int = Query(256, enum=[256, 512]),
    color: int = Query(2, ge=0, le=4, description="Color scheme: 0=B/W, 1=Original, 2=NOAA, 3=Black, 4=White")
):
    """
    Proxy radar tile from RainViewer.
    
    This endpoint proxies radar tiles to avoid CORS issues.
    Tiles are cached for 5 minutes.
    """
    radar_service = RadarService()
    
    try:
        tile_data = await radar_service.get_tile(
            path=f"/{path}",
            z=z, x=x, y=y,
            size=size,
            color=color
        )
        
        return Response(
            content=tile_data,
            media_type="image/png",
            headers={"Cache-Control": "public, max-age=300"}
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await radar_service.close()


@router.get("/alerts", response_model=AlertsListResponse)
async def get_weather_alerts(
    latitude: float = Query(..., ge=-90, le=90, description="Latitude"),
    longitude: float = Query(..., ge=-180, le=180, description="Longitude")
):
    """
    Get active severe weather alerts for a location.
    
    Uses NWS API - currently US only.
    Returns alerts sorted by priority (most urgent first).
    
    Alert types include:
    - Tornado Warning
    - Severe Thunderstorm Warning
    - Flash Flood Warning
    - Winter Storm Warning
    - Heat Advisory
    - And more...
    """
    alerts_service = AlertsService()
    
    try:
        alerts_response = await alerts_service.get_alerts_by_point(latitude, longitude)
        
        # Convert to response schema
        alert_list = [
            WeatherAlertResponse(
                id=a.id,
                event=a.event,
                headline=a.headline,
                description=a.description,
                instruction=a.instruction,
                severity=a.severity.value,
                urgency=a.urgency.value,
                certainty=a.certainty.value,
                onset=a.onset,
                expires=a.expires,
                sender=a.sender,
                areas=a.areas,
                priority_score=a.priority_score
            )
            for a in alerts_response.alerts
        ]
        
        return AlertsListResponse(
            alerts=alert_list,
            location=alerts_response.location,
            updated=alerts_response.updated,
            total_count=alerts_response.total_count,
            has_severe=alerts_response.has_severe
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await alerts_service.close()


@router.get("/alerts/state/{state}", response_model=AlertsListResponse)
async def get_state_weather_alerts(
    state: str
):
    """
    Get active severe weather alerts for a US state.
    
    State should be a two-letter code (e.g., "NY", "CA", "TX").
    """
    if len(state) != 2:
        raise HTTPException(status_code=400, detail="State must be a two-letter code")
    
    alerts_service = AlertsService()
    
    try:
        alerts_response = await alerts_service.get_alerts_by_state(state.upper())
        
        alert_list = [
            WeatherAlertResponse(
                id=a.id,
                event=a.event,
                headline=a.headline,
                description=a.description,
                instruction=a.instruction,
                severity=a.severity.value,
                urgency=a.urgency.value,
                certainty=a.certainty.value,
                onset=a.onset,
                expires=a.expires,
                sender=a.sender,
                areas=a.areas,
                priority_score=a.priority_score
            )
            for a in alerts_response.alerts
        ]
        
        return AlertsListResponse(
            alerts=alert_list,
            location={"state": state.upper()},
            updated=alerts_response.updated,
            total_count=alerts_response.total_count,
            has_severe=alerts_response.has_severe
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await alerts_service.close()
