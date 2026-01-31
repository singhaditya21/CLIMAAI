"""
Nowcast schemas for minute-by-minute precipitation data.
"""
from pydantic import BaseModel
from typing import List, Optional, Dict
from datetime import datetime


class NowcastMinute(BaseModel):
    """Single minute precipitation data point."""
    time: datetime
    precipitation: float  # mm
    precipitation_probability: int  # 0-100%
    intensity: str  # none, light, moderate, heavy
    is_precipitation: bool
    
    class Config:
        json_schema_extra = {
            "example": {
                "time": "2026-01-31T11:15:00",
                "precipitation": 0.05,
                "precipitation_probability": 65,
                "intensity": "light",
                "is_precipitation": True
            }
        }


class NowcastResponse(BaseModel):
    """
    Minute-by-minute precipitation nowcast response.
    Provides 2-hour (120 minutes) precipitation forecast.
    """
    location: Dict[str, float]
    timezone: str
    summary: str  # Human-readable summary like "Rain starting in 15 minutes"
    precipitation_start: Optional[datetime]  # When rain starts (None if no rain)
    precipitation_end: Optional[datetime]    # When rain stops (None if no rain)
    total_precipitation: float               # Total mm expected
    minutes: List[NowcastMinute]             # 120 minute-by-minute data points
    last_updated: datetime
    
    class Config:
        json_schema_extra = {
            "example": {
                "location": {"latitude": 40.7128, "longitude": -74.0060},
                "timezone": "America/New_York",
                "summary": "Light rain starting in 12 minutes.",
                "precipitation_start": "2026-01-31T11:27:00",
                "precipitation_end": "2026-01-31T12:45:00",
                "total_precipitation": 2.35,
                "minutes": [
                    {
                        "time": "2026-01-31T11:15:00",
                        "precipitation": 0.0,
                        "precipitation_probability": 25,
                        "intensity": "none",
                        "is_precipitation": False
                    }
                ],
                "last_updated": "2026-01-31T11:15:00"
            }
        }
