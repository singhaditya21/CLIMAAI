"""
Pydantic schemas for user management.
"""
from pydantic import BaseModel, EmailStr, Field, field_validator
from typing import Optional
from uuid import UUID
from datetime import datetime


class UserPreferences(BaseModel):
    """User preferences schema."""
    temperature_unit: str = Field(default="celsius", pattern="^(celsius|fahrenheit)$")
    wind_speed_unit: str = Field(default="kmh", pattern="^(kmh|mph|ms)$")
    precipitation_unit: str = Field(default="mm", pattern="^(mm|inch)$")
    time_format: str = Field(default="24h", pattern="^(24h|12h)$")
    notifications_enabled: bool = True
    theme: str = Field(default="auto", pattern="^(light|dark|auto)$")


class UserCreate(BaseModel):
    """Schema for user registration."""
    email: EmailStr
    password: str = Field(..., min_length=8, max_length=100)
    full_name: Optional[str] = Field(None, max_length=255)
    platform: str = Field(..., pattern="^(ios|android)$")
    device_token: Optional[str] = None
    
    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        """Validate password strength."""
        if len(v) < 8:
            raise ValueError("Password must be at least 8 characters")
        if not any(c.isdigit() for c in v):
            raise ValueError("Password must contain at least one digit")
        if not any(c.isalpha() for c in v):
            raise ValueError("Password must contain at least one letter")
        return v


class UserLogin(BaseModel):
    """Schema for user login."""
    email: EmailStr
    password: str


class UserUpdate(BaseModel):
    """Schema for user updates."""
    full_name: Optional[str] = None
    preferences: Optional[UserPreferences] = None
    default_latitude: Optional[str] = None
    default_longitude: Optional[str] = None
    default_location_name: Optional[str] = None
    device_token: Optional[str] = None


class UserResponse(BaseModel):
    """Schema for user response."""
    id: UUID
    email: str
    full_name: Optional[str]
    is_active: bool
    is_verified: bool
    platform: Optional[str]
    preferences: dict
    default_location_name: Optional[str]
    created_at: datetime
    
    class Config:
        from_attributes = True


class TokenResponse(BaseModel):
    """Schema for JWT token response."""
    access_token: str
    token_type: str = "bearer"
    user: UserResponse
