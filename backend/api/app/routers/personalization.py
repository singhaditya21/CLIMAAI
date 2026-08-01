"""
Personalization API Router

Endpoints for tracking user behavior and getting personalized content.
"""

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from typing import Dict, List, Any, Optional
from datetime import datetime, timezone

from ..services.personalization_service import (
    personalization_service,
    UserEvent,
    UserPreferenceProfile
)
from ..services.auth import get_current_user
from ..models import User


router = APIRouter(prefix="/personalization", tags=["Personalization"])


class TrackEventRequest(BaseModel):
    event_type: str
    event_data: Dict[str, Any]
    weather_context: Optional[Dict[str, Any]] = None


class PersonalizedContentResponse(BaseModel):
    priority_features: List[str]
    notifications: List[Dict[str, Any]]
    activity_suggestions: List[str]
    outfit_adjustments: List[str]
    optimal_notification_time: str


@router.post("/track")
async def track_event(
    request: TrackEventRequest,
    current_user: User = Depends(get_current_user)
) -> Dict[str, str]:
    """
    Track a user interaction event for personalization learning.
    
    Event types:
    - screen_view: User viewed a screen (data: {screen: "home"})
    - notification_click: User clicked a notification (data: {notification_type: "rain_alert"})
    - feature_use: User used a feature (data: {feature: "radar_map"})
    - setting_change: User changed a setting (data: {setting: "temperature_unit", value: "fahrenheit"})
    """
    event = UserEvent(
        user_id=str(current_user.id),
        event_type=request.event_type,
        event_data=request.event_data,
        weather_context=request.weather_context
    )
    
    await personalization_service.track_event(event)
    
    return {"status": "tracked"}


@router.get("/profile")
async def get_profile(
    current_user: User = Depends(get_current_user)
) -> UserPreferenceProfile:
    """Get the learned preference profile for a user"""
    return await personalization_service.get_profile(str(current_user.id))


@router.get("/recommendations")
async def get_recommendations(
    temperature: float = 20.0,
    humidity: int = 50,
    uv_index: float = 5.0,
    precipitation_probability: int = 0,
    current_user: User = Depends(get_current_user)
) -> PersonalizedContentResponse:
    """
    Get personalized recommendations based on current weather and user preferences.
    """
    weather = {
        "temperature": temperature,
        "humidity": humidity,
        "uv_index": uv_index,
        "precipitation_probability": precipitation_probability
    }
    
    recommendations = await personalization_service.get_personalized_recommendations(
        str(current_user.id), weather
    )
    
    optimal_time = await personalization_service.get_optimal_notification_time(str(current_user.id))
    
    return PersonalizedContentResponse(
        priority_features=recommendations.get("priority_features", []),
        notifications=recommendations.get("notifications", []),
        activity_suggestions=recommendations.get("activity_suggestions", []),
        outfit_adjustments=recommendations.get("outfit_adjustments", []),
        optimal_notification_time=optimal_time
    )


@router.get("/should-notify")
async def should_notify(
    notification_type: str,
    current_user: User = Depends(get_current_user)
) -> Dict[str, bool]:
    """Check if a user should receive a specific notification type"""
    should_send = await personalization_service.should_send_notification(
        str(current_user.id), notification_type
    )
    return {"should_send": should_send}


@router.post("/profile/update")
async def update_profile(
    updates: Dict[str, Any],
    current_user: User = Depends(get_current_user)
) -> UserPreferenceProfile:
    """Manually update user preference profile"""
    profile = await personalization_service.get_profile(str(current_user.id))
    
    # Apply updates
    for key, value in updates.items():
        if hasattr(profile, key):
            setattr(profile, key, value)
    
    profile.last_updated = datetime.now(timezone.utc)
    personalization_service._profiles[str(current_user.id)] = profile
    
    return profile
