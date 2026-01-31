"""
Health API router - Pollen, Air Quality, and Health insights.
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from typing import Optional
from ..models import User
from ..schemas.pollen import PollenForecastResponse, PollenTypeResponse, DailyPollenResponse, PollenLevel
from ..services.pollen_service import PollenService
from ..services.auth import get_optional_user
from ..database import get_db
from sqlalchemy.ext.asyncio import AsyncSession

router = APIRouter(prefix="/health", tags=["health"])


@router.get("/pollen", response_model=PollenForecastResponse)
async def get_pollen_forecast(
    latitude: float = Query(..., ge=-90, le=90, description="Latitude"),
    longitude: float = Query(..., ge=-180, le=180, description="Longitude"),
    days: int = Query(5, ge=1, le=5, description="Forecast days (1-5)")
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
    pollen_service = PollenService()
    
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
    finally:
        await pollen_service.close()


@router.get("/pollen/today")
async def get_todays_pollen(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180)
):
    """
    Get today's pollen levels (simplified view).
    
    Returns just today's levels and recommendations.
    """
    pollen_service = PollenService()
    
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
    finally:
        await pollen_service.close()
