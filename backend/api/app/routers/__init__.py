"""
Routers package.
"""
from .users import router as users_router
from .weather import router as weather_router
from .ai import router as ai_router
from .subscriptions import router as subscriptions_router
from .locations import router as locations_router
from .alerts import router as alerts_router
from .notifications import router as notifications_router

__all__ = [
    "users_router",
    "weather_router",
    "ai_router",
    "subscriptions_router",
    "locations_router",
    "alerts_router",
    "notifications_router",
]

