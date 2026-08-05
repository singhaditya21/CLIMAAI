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


class InsightSource(str, Enum):
    """Where insight text came from, so clients never dress templates up as AI.

    Without OPENAI_API_KEY (or with ENABLE_AI_INSIGHTS off) every insight is
    rule-generated template text; a client has no way to tell unless the
    response says so.
    """
    RULES = "rules"
    LLM = "llm"


def _generated_by():
    """The default is RULES on every model, not LLM: cached insights written
    before this field existed deserialize without it, and mislabelling LLM text
    as rules is harmless where the reverse is dishonest."""
    return Field(
        default=InsightSource.RULES,
        description="'llm' when a language model wrote this, 'rules' for template text",
    )


class OutfitRecommendation(BaseModel):
    """AI-generated outfit recommendation."""
    summary: str = Field(..., description="Brief outfit suggestion")
    details: str = Field(..., description="Detailed clothing recommendations")
    accessories: List[str] = Field(default=[], description="Suggested accessories")
    layer_recommendation: str = Field(..., description="Layering advice")
    generated_by: InsightSource = _generated_by()


class ActivityRecommendation(BaseModel):
    """AI-generated activity recommendation."""
    activity: str
    suitability_score: int = Field(..., ge=0, le=100)
    best_time: str
    reasoning: str
    precautions: List[str] = Field(default=[])
    generated_by: InsightSource = _generated_by()


class HealthInsight(BaseModel):
    """Health-related weather insights.

    Always rule-computed from UV/AQI/heat thresholds — no LLM is involved even
    when one is configured — so generated_by stays at its RULES default.
    """
    uv_risk: RiskLevel
    uv_advice: str
    air_quality_risk: RiskLevel
    air_quality_advice: str
    heat_stress_risk: RiskLevel
    heat_stress_advice: str
    allergy_risk: Optional[RiskLevel] = None
    allergy_advice: Optional[str] = None
    general_health_tips: List[str] = Field(default=[])
    generated_by: InsightSource = _generated_by()


class TravelRiskAnalysis(BaseModel):
    """Travel weather risk analysis."""
    overall_risk: RiskLevel
    summary: str
    severe_weather_alerts: List[str] = Field(default=[])
    travel_tips: List[str] = Field(default=[])
    best_travel_times: List[str] = Field(default=[])
    worst_travel_times: List[str] = Field(default=[])
    generated_by: InsightSource = _generated_by()


class DailySummary(BaseModel):
    """AI-generated daily weather summary."""
    title: str = Field(..., description="Short headline")
    summary: str = Field(..., description="Natural language summary")
    highlights: List[str] = Field(..., description="Key points to know")
    warnings: List[str] = Field(default=[], description="Weather warnings")
    best_times: dict = Field(default={}, description="Best times for various activities")
    generated_by: InsightSource = _generated_by()


class AIInsightsResponse(BaseModel):
    """Complete AI insights response."""
    daily_summary: DailySummary
    outfit: OutfitRecommendation
    activities: List[ActivityRecommendation]
    health: HealthInsight
    travel: Optional[TravelRiskAnalysis] = None
    cached: bool = Field(default=False)
    generated_by: InsightSource = Field(
        default=InsightSource.RULES,
        description=(
            "'llm' only when every section a language model *can* write actually "
            "was written by one. Health is always rule-computed and does not "
            "count against this. Each section also carries its own flag."
        ),
    )
