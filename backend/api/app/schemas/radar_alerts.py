"""
Radar and Alerts schemas for API responses.
"""
from pydantic import BaseModel
from typing import List, Optional, Dict
from datetime import datetime
from enum import Enum


# Radar Schemas

class RadarFrame(BaseModel):
    """Single radar frame."""
    time: int  # Unix timestamp
    path: str  # Tile path
    datetime_str: Optional[str] = None  # Human-readable time
    
    class Config:
        json_schema_extra = {
            "example": {
                "time": 1706695200,
                "path": "/v2/radar/1706695200/256",
                "datetime_str": "2026-01-31T11:00:00Z"
            }
        }


class RadarFramesResponse(BaseModel):
    """Available radar frames."""
    host: str
    generated: int
    past: List[RadarFrame]  # Actual radar
    nowcast: List[RadarFrame]  # Predicted
    tile_url_template: str  # How to construct tile URLs
    
    class Config:
        json_schema_extra = {
            "example": {
                "host": "https://tilecache.rainviewer.com",
                "generated": 1706695200,
                "past": [],
                "nowcast": [],
                "tile_url_template": "{host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png"
            }
        }


# Alert Schemas

class AlertSeverity(str, Enum):
    EXTREME = "Extreme"
    SEVERE = "Severe"
    MODERATE = "Moderate"
    MINOR = "Minor"
    UNKNOWN = "Unknown"


class AlertUrgency(str, Enum):
    IMMEDIATE = "Immediate"
    EXPECTED = "Expected"
    FUTURE = "Future"
    PAST = "Past"
    UNKNOWN = "Unknown"


class WeatherAlertResponse(BaseModel):
    """Single weather alert."""
    id: str
    event: str
    headline: str
    description: str
    instruction: Optional[str]
    severity: str
    urgency: str
    certainty: str
    onset: Optional[datetime]
    expires: Optional[datetime]
    sender: str
    areas: List[str]
    priority_score: int


class AlertsListResponse(BaseModel):
    """List of weather alerts."""
    alerts: List[WeatherAlertResponse]
    location: Dict[str, float]
    updated: datetime
    total_count: int
    has_severe: bool
