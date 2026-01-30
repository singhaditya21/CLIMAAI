"""
Pydantic schemas for subscription management.
"""
from pydantic import BaseModel, Field
from typing import Optional
from uuid import UUID
from datetime import datetime
from ..models.subscription import SubscriptionStatus, SubscriptionPlatform, SubscriptionPlan


class SubscriptionCreate(BaseModel):
    """Schema for creating a subscription."""
    platform: SubscriptionPlatform
    plan: SubscriptionPlan
    receipt_data: str = Field(..., description="Platform-specific receipt/token")


class SubscriptionValidate(BaseModel):
    """Schema for validating a subscription receipt."""
    platform: SubscriptionPlatform
    receipt_data: str


class SubscriptionResponse(BaseModel):
    """Schema for subscription response."""
    id: UUID
    user_id: UUID
    platform: SubscriptionPlatform
    plan: SubscriptionPlan
    status: SubscriptionStatus
    is_trial_used: bool
    trial_start_date: Optional[datetime]
    trial_end_date: Optional[datetime]
    subscription_start_date: Optional[datetime]
    subscription_end_date: Optional[datetime]
    auto_renew: bool
    is_active: bool
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True


class SubscriptionStatusResponse(BaseModel):
    """Schema for subscription status check."""
    has_active_subscription: bool
    is_premium: bool
    subscription: Optional[SubscriptionResponse] = None
    features: dict = Field(default={
        "extended_forecast": False,
        "ai_insights": False,
        "minute_rain": False,
        "severe_alerts": False,
        "air_quality_detailed": False,
        "health_insights": False,
        "travel_analysis": False,
    })


class WebhookEvent(BaseModel):
    """Schema for subscription webhook events."""
    event_type: str
    platform: SubscriptionPlatform
    user_id: Optional[UUID] = None
    transaction_id: Optional[str] = None
    data: dict
