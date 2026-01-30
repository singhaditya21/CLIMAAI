"""
Pydantic schemas package.
"""
from .weather import (
    CurrentWeather,
    HourlyWeather,
    DailyWeather,
    AirQuality,
    WeatherResponse,
)
from .ai import (
    OutfitRecommendation,
    ActivityRecommendation,
    HealthInsight,
    TravelRiskAnalysis,
    DailySummary,
    AIInsightsResponse,
    ActivityType,
    RiskLevel,
)
from .user import (
    UserCreate,
    UserLogin,
    UserUpdate,
    UserResponse,
    TokenResponse,
    UserPreferences,
)
from .subscription import (
    SubscriptionCreate,
    SubscriptionValidate,
    SubscriptionResponse,
    SubscriptionStatusResponse,
    WebhookEvent,
)

__all__ = [
    # Weather
    "CurrentWeather",
    "HourlyWeather",
    "DailyWeather",
    "AirQuality",
    "WeatherResponse",
    # AI
    "OutfitRecommendation",
    "ActivityRecommendation",
    "HealthInsight",
    "TravelRiskAnalysis",
    "DailySummary",
    "AIInsightsResponse",
    "ActivityType",
    "RiskLevel",
    # User
    "UserCreate",
    "UserLogin",
    "UserUpdate",
    "UserResponse",
    "TokenResponse",
    "UserPreferences",
    # Subscription
    "SubscriptionCreate",
    "SubscriptionValidate",
    "SubscriptionResponse",
    "SubscriptionStatusResponse",
    "WebhookEvent",
]
