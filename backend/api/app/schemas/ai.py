"""
Pydantic schemas for AI insights.
"""
from pydantic import BaseModel, Field
from typing import List, Optional
from enum import Enum


class ActivityType(str, Enum):
    """Outdoor activity types."""
    RUNNING = "running"
    CYCLING = "cycling"
    HIKING = "hiking"
    BEACH = "beach"
    GOLF = "golf"
    SPORTS = "sports"
    GENERAL = "general"


class RiskLevel(str, Enum):
    """Risk levels for various conditions."""
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    VERY_HIGH = "very_high"


class OutfitRecommendation(BaseModel):
    """AI-generated outfit recommendation."""
    summary: str = Field(..., description="Brief outfit suggestion")
    details: str = Field(..., description="Detailed clothing recommendations")
    accessories: List[str] = Field(default=[], description="Suggested accessories")
    layer_recommendation: str = Field(..., description="Layering advice")


class ActivityRecommendation(BaseModel):
    """AI-generated activity recommendation."""
    activity: str
    suitability_score: int = Field(..., ge=0, le=100)
    best_time: str
    reasoning: str
    precautions: List[str] = Field(default=[])


class HealthInsight(BaseModel):
    """Health-related weather insights."""
    uv_risk: RiskLevel
    uv_advice: str
    air_quality_risk: RiskLevel
    air_quality_advice: str
    heat_stress_risk: RiskLevel
    heat_stress_advice: str
    allergy_risk: Optional[RiskLevel] = None
    allergy_advice: Optional[str] = None
    general_health_tips: List[str] = Field(default=[])


class TravelRiskAnalysis(BaseModel):
    """Travel weather risk analysis."""
    overall_risk: RiskLevel
    summary: str
    severe_weather_alerts: List[str] = Field(default=[])
    travel_tips: List[str] = Field(default=[])
    best_travel_times: List[str] = Field(default=[])
    worst_travel_times: List[str] = Field(default=[])


class DailySummary(BaseModel):
    """AI-generated daily weather summary."""
    title: str = Field(..., description="Short headline")
    summary: str = Field(..., description="Natural language summary")
    highlights: List[str] = Field(..., description="Key points to know")
    warnings: List[str] = Field(default=[], description="Weather warnings")
    best_times: dict = Field(default={}, description="Best times for various activities")


class AIInsightsResponse(BaseModel):
    """Complete AI insights response."""
    daily_summary: DailySummary
    outfit: OutfitRecommendation
    activities: List[ActivityRecommendation]
    health: HealthInsight
    travel: Optional[TravelRiskAnalysis] = None
    cached: bool = Field(default=False)
