"""
Weather alerts router.
"""
from fastapi import APIRouter, Depends, HTTPException, Query, Path
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List
from ..models import User
from ..services.auth import get_current_user
from ..services.weather_service import WeatherService
from ..services.alerts_service import AlertsService, get_alerts_service
from ..database import get_db

router = APIRouter(prefix="/alerts", tags=["alerts"])


@router.get("")
async def get_weather_alerts(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    location_name: str = Query("your location", max_length=100),
    current_user: User = Depends(get_current_user),
    alerts_service: AlertsService = Depends(get_alerts_service)
):
    """
    Get active weather alerts for a location.
    
    Analyzes current and forecasted weather to identify:
    - Temperature extremes
    - High winds
    - Heavy precipitation
    - Poor air quality
    - High UV index
    - Upcoming severe weather
    """
    weather_service = WeatherService()
    
    try:
        # Get weather data
        weather = await weather_service.get_current_weather(latitude, longitude)
        
        # Evaluate alerts
        alerts = await alerts_service.evaluate_alerts(weather, location_name)
        
        # Convert to dict format
        alerts_data = []
        for alert in alerts:
            alerts_data.append({
                "alert_type": alert.alert_type,
                "severity": alert.severity.value,
                "title": alert.title,
                "message": alert.message,
                "metadata": alert.metadata,
                "expires_at": alert.expires_at.isoformat() if alert.expires_at else None
            })
        
        return {
            "location": location_name,
            "latitude": latitude,
            "longitude": longitude,
            "alert_count": len(alerts_data),
            "alerts": alerts_data
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()


@router.get("/history")
async def get_alert_history(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    alerts_service: AlertsService = Depends(get_alerts_service)
):
    """
    Get user's alert history.
    """
    
    try:
        alerts = await alerts_service.get_active_alerts(current_user.id, db)
        
        return {
            "user_id": current_user.id,
            "alert_count": len(alerts),
            "alerts": alerts
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/{alert_id}/dismiss")
async def dismiss_alert(
    alert_id: int = Path(..., ge=1),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    alerts_service: AlertsService = Depends(get_alerts_service)
):
    """
    Dismiss a weather alert.
    """
    
    try:
        await alerts_service.dismiss_alert(alert_id, current_user.id, db)
        
        return {"message": "Alert dismissed successfully"}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
