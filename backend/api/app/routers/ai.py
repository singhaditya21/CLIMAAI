"""
AI insights router (Premium feature).
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from ..models import User
from ..schemas.ai import AIInsightsResponse, OutfitRecommendation, ActivityRecommendation, HealthInsight, TravelRiskAnalysis
from ..services.weather_service import WeatherService
from ..services.ai_service import AIService
from ..services.auth import get_current_user
from ..services.subscription_service import SubscriptionService
from ..database import get_db
from typing import List

router = APIRouter(prefix="/api", tags=["ai-insights"])


async def require_premium(current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    """Dependency to require premium subscription."""
    subscription_service = SubscriptionService()
    sub_status = await subscription_service.check_subscription_status(current_user, db)
    
    if not sub_status.is_premium:
        raise HTTPException(
            status_code=403,
            detail="Premium subscription required for AI insights. Start your 7-day free trial!"
        )
    
    return current_user


@router.get("/insights", response_model=AIInsightsResponse)
async def get_ai_insights(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    location_name: str = Query("your location", max_length=100),
    current_user: User = Depends(require_premium),
    db: AsyncSession = Depends(get_db)
):
    """
    Get complete AI-powered weather insights.
    
    **Premium feature** - Includes:
    - Daily weather summary in natural language
    - Outfit recommendations
    - Activity suggestions with timing
    - Health insights (UV, air quality, heat stress)
    """
    weather_service = WeatherService()
    ai_service = AIService()
    
    try:
        # Get weather data
        weather = await weather_service.get_current_weather(latitude, longitude)
        
        # Generate AI insights
        insights = await ai_service.generate_complete_insights(weather, location_name)
        
        return insights
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()
        await ai_service.close()


@router.get("/summary", response_model=dict)
async def get_daily_summary(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    location_name: str = Query("your location", max_length=100),
    current_user: User = Depends(require_premium),
):
    """
    Get AI-generated daily weather summary.
    
    **Premium feature**
    """
    weather_service = WeatherService()
    ai_service = AIService()
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        summary = await ai_service.generate_daily_summary(weather, location_name)
        
        return {"summary": summary}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()
        await ai_service.close()


@router.get("/outfit", response_model=OutfitRecommendation)
async def get_outfit_recommendation(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    current_user: User = Depends(require_premium),
):
    """
    Get AI outfit recommendation based on weather.
    
    **Premium feature** - "What should I wear today?"
    """
    weather_service = WeatherService()
    ai_service = AIService()
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        outfit = await ai_service.generate_outfit_recommendation(weather)
        
        return outfit
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()
        await ai_service.close()


@router.get("/activities", response_model=List[ActivityRecommendation])
async def get_activity_recommendations(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    current_user: User = Depends(require_premium),
):
    """
    Get AI activity recommendations based on weather.
    
    **Premium feature** - Outdoor activity suggestions with timing
    """
    weather_service = WeatherService()
    ai_service = AIService()
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        activities = await ai_service.generate_activity_recommendations(weather)
        
        return activities
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()
        await ai_service.close()


@router.get("/health", response_model=HealthInsight)
async def get_health_insights(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    current_user: User = Depends(require_premium),
):
    """
    Get health-related weather insights.
    
    **Premium feature** - UV risk, air quality impact, heat stress, allergy info
    """
    weather_service = WeatherService()
    ai_service = AIService()
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        health = await ai_service.generate_health_insights(weather)
        
        return health
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()
        await ai_service.close()


@router.get("/travel-risk", response_model=TravelRiskAnalysis)
async def get_travel_risk_analysis(
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    destination: str = Query("your destination", max_length=200),
    current_user: User = Depends(require_premium),
):
    """
    Get AI-powered travel risk analysis based on weather conditions.
    
    **Premium feature** - Analyzes:
    - Overall travel safety risk score
    - Weather-related risk factors (rain, wind, visibility, temperature)
    - Air quality impact on travel
    - Road condition assessment
    - Best departure times
    - Specific safety recommendations
    """
    weather_service = WeatherService()
    ai_service = AIService()
    
    try:
        weather = await weather_service.get_current_weather(latitude, longitude)
        travel_risk = await ai_service.generate_travel_risk_analysis(weather, destination)
        
        return travel_risk
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await weather_service.close()
        await ai_service.close()

