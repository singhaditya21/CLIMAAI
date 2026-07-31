"""
Push notifications management router.
"""
from fastapi import APIRouter, Depends, HTTPException, Body
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Optional
from ..models import User
from ..services.auth import get_current_user
from ..services.notification_service import NotificationService
from ..database import get_db

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.post("/register-device")
async def register_device(
    token: str = Body(..., min_length=10),
    platform: str = Body(..., pattern="^(ios|android)$"),
    device_info: Optional[dict] = Body(None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Register a device token for push notifications.
    
    **Platform**: ios or android
    **Device Info**: Optional metadata (model, os_version, etc.)
    """
    notification_service = NotificationService()
    
    try:
        success = await notification_service.register_device_token(
            user_id=current_user.id,
            token=token,
            platform=platform,
            device_info=device_info,
            db=db
        )
        
        if success:
            return {
                "message": "Device registered successfully",
                "platform": platform
            }
        else:
            raise HTTPException(status_code=500, detail="Failed to register device")
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/unregister-device")
async def unregister_device(
    token: str = Body(..., min_length=10),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Unregister a device token (e.g., on logout).
    """
    notification_service = NotificationService()
    
    try:
        success = await notification_service.unregister_device_token(token, db)
        
        if success:
            return {"message": "Device unregistered successfully"}
        else:
            raise HTTPException(status_code=500, detail="Failed to unregister device")
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/test")
async def send_test_notification(
    title: str = Body("Test Notification", max_length=100),
    message: str = Body("This is a test notification from ClimaAI", max_length=500),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Send a test notification to all user's devices.
    
    Useful for testing notification setup.
    """
    notification_service = NotificationService()
    
    try:
        success_count = await notification_service.send_to_user(
            user_id=current_user.id,
            title=title,
            body=message,
            data={"type": "test"},
            db=db
        )
        
        return {
            "message": f"Test notification sent to {success_count} device(s)",
            "devices_notified": success_count
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/preferences")
async def get_notification_preferences(
    current_user: User = Depends(get_current_user),
):
    """
    Get user's notification preferences.
    """
    preferences = current_user.notification_preferences or {
        "weather_alerts": True,
        "daily_summary": False,
        "severe_weather": True
    }
    
    return {"preferences": preferences}


@router.put("/preferences")
async def update_notification_preferences(
    weather_alerts: Optional[bool] = Body(None),
    daily_summary: Optional[bool] = Body(None),
    severe_weather: Optional[bool] = Body(None),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Update user's notification preferences.
    """
    try:
        # Build preferences dict
        preferences = current_user.notification_preferences or {}
        
        if weather_alerts is not None:
            preferences["weather_alerts"] = weather_alerts
        if daily_summary is not None:
            preferences["daily_summary"] = daily_summary
        if severe_weather is not None:
            preferences["severe_weather"] = severe_weather
        
        # Update user preferences
        import json
        await db.execute(
            text("""
            UPDATE users
            SET notification_preferences = CAST(:preferences AS jsonb)
            WHERE id = :user_id
            """),
            {"preferences": json.dumps(preferences), "user_id": current_user.id}
        )
        await db.commit()
        
        return {
            "message": "Notification preferences updated",
            "preferences": preferences
        }
        
    except Exception as e:
        await db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
