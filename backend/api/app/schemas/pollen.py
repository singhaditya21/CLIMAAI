"""
Pollen schemas for API responses.
"""
from pydantic import BaseModel
from typing import List, Optional, Dict
from datetime import date, datetime
from enum import Enum


class PollenLevel(str, Enum):
    NONE = "none"
    VERY_LOW = "very_low"
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    VERY_HIGH = "very_high"


class PollenTypeResponse(BaseModel):
    """Pollen data for a specific type."""
    display_name: str
    level: PollenLevel
    index: int  # 0-5 scale
    species: List[str]
    health_advice: Optional[str]
    color: str  # Hex color for UI


class DailyPollenResponse(BaseModel):
    """Daily pollen forecast."""
    date: date
    tree: PollenTypeResponse
    grass: PollenTypeResponse
    weed: PollenTypeResponse
    overall_index: int
    overall_level: PollenLevel


class PollenForecastResponse(BaseModel):
    """Complete pollen forecast."""
    location: Dict[str, float]
    forecast: List[DailyPollenResponse]
    last_updated: datetime
    health_recommendations: List[str]
    
    class Config:
        json_schema_extra = {
            "example": {
                "location": {"latitude": 40.7128, "longitude": -74.0060},
                "forecast": [],
                "last_updated": "2026-01-31T11:00:00Z",
                "health_recommendations": ["✅ Pollen levels are low"]
            }
        }
