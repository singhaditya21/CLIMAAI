"""
Health Index Services - Flu Risk, Migraine Forecast, and other health metrics.
"""
from typing import Dict, List, Optional, Tuple
from datetime import datetime, date
from pydantic import BaseModel
from enum import Enum


class RiskLevel(str, Enum):
    """Health risk levels."""
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    VERY_HIGH = "very_high"


class FluRisk(BaseModel):
    """Flu risk assessment."""
    risk_level: RiskLevel
    risk_score: int  # 0-100
    factors: List[str]
    recommendations: List[str]
    seasonal_context: str


class MigraineRisk(BaseModel):
    """Migraine trigger assessment."""
    risk_level: RiskLevel
    risk_score: int  # 0-100
    pressure_change: float  # mb in last 24h
    # "rising rapidly", "rising", "stable", "falling", "falling rapidly"
    pressure_trend: str
    triggers: List[str]
    recommendations: List[str]


class HealthIndexService:
    """
    Service for calculating health-related weather indices.
    
    Flu Risk:
    - Based on temperature, humidity, and seasonal factors
    - Flu virus spreads better in cold, dry conditions
    - Peak season: October-March in Northern Hemisphere
    
    Migraine Forecast:
    - Barometric pressure changes are a known trigger
    - Rapid drops (>6mb in 24h) significantly increase risk
    - Humidity extremes also contribute
    """
    
    def calculate_flu_risk(
        self,
        temperature: float,
        humidity: int,
        current_date: date,
        latitude: float = 40.0  # For hemisphere detection
    ) -> FluRisk:
        """
        Calculate flu risk based on weather conditions.
        
        The flu virus thrives in:
        - Cold temperatures (0-10°C ideal)
        - Low humidity (<40%)
        - Winter months (Oct-Mar in Northern Hemisphere)
        
        Returns:
            FluRisk with score and recommendations
        """
        score = 0
        factors = []
        
        # Temperature factor (cold = higher risk)
        if temperature < 0:
            score += 30
            factors.append("Very cold temperatures favor virus survival")
        elif temperature < 10:
            score += 25
            factors.append("Cold temperatures increase transmission")
        elif temperature < 15:
            score += 15
            factors.append("Cool temperatures slightly elevate risk")
        elif temperature > 25:
            score += 5
            factors.append("Warm temperatures reduce virus viability")
        
        # Humidity factor (dry = higher risk)
        if humidity < 30:
            score += 35
            factors.append("Very dry air increases airborne transmission")
        elif humidity < 40:
            score += 25
            factors.append("Low humidity favors virus spread")
        elif humidity < 50:
            score += 10
            factors.append("Moderate humidity - some viral survival")
        elif humidity > 70:
            score -= 5
            factors.append("High humidity reduces airborne transmission")
        
        # Seasonal factor
        month = current_date.month
        is_northern = latitude >= 0
        
        # Flu season: Oct-Mar (Northern) or Apr-Sep (Southern)
        if is_northern:
            if month in [12, 1, 2]:
                score += 30
                factors.append("Peak flu season (winter)")
            elif month in [11, 3]:
                score += 20
                factors.append("Elevated flu activity (shoulder season)")
            elif month in [10, 4]:
                score += 10
                factors.append("Flu season beginning/ending")
            else:
                score -= 10
                factors.append("Off-season for flu")
        else:
            if month in [6, 7, 8]:
                score += 30
                factors.append("Peak flu season (winter)")
            elif month in [5, 9]:
                score += 20
                factors.append("Elevated flu activity")
            else:
                score -= 10
                factors.append("Off-season for flu")
        
        # Clamp score
        score = max(0, min(100, score))
        
        # Determine risk level
        if score >= 70:
            risk_level = RiskLevel.VERY_HIGH
        elif score >= 50:
            risk_level = RiskLevel.HIGH
        elif score >= 30:
            risk_level = RiskLevel.MODERATE
        else:
            risk_level = RiskLevel.LOW
        
        # Generate recommendations
        recommendations = self._get_flu_recommendations(risk_level, factors)
        
        # Seasonal context
        if is_northern:
            season_context = "Northern Hemisphere flu season typically peaks December-February"
        else:
            season_context = "Southern Hemisphere flu season typically peaks June-August"
        
        return FluRisk(
            risk_level=risk_level,
            risk_score=score,
            factors=factors[:3],  # Top 3 factors
            recommendations=recommendations,
            seasonal_context=season_context
        )
    
    def calculate_migraine_risk(
        self,
        current_pressure: float,
        pressure_history: List[float],  # Last 24 hours
        humidity: int,
        temperature: float
    ) -> MigraineRisk:
        """
        Calculate migraine trigger risk based on weather.
        
        Key triggers:
        - Rapid pressure changes (>6mb in 24h)
        - Falling pressure (storm approaching)
        - Extreme humidity (<30% or >80%)
        - Temperature extremes
        
        Returns:
            MigraineRisk with trigger assessment
        """
        score = 0
        triggers = []
        
        # Calculate pressure change
        if pressure_history:
            oldest_pressure = pressure_history[0]
            pressure_change = current_pressure - oldest_pressure
        else:
            pressure_change = 0.0
        
        # Pressure trend
        if pressure_change < -3:
            pressure_trend = "falling rapidly"
            score += 40
            triggers.append(f"Rapid pressure drop ({abs(pressure_change):.1f} mb)")
        elif pressure_change < -1:
            pressure_trend = "falling"
            score += 20
            triggers.append("Falling barometric pressure")
        elif pressure_change > 3:
            pressure_trend = "rising rapidly"
            score += 25
            triggers.append("Rapid pressure increase")
        elif pressure_change > 1:
            pressure_trend = "rising"
            score += 10
        else:
            pressure_trend = "stable"
        
        # Humidity triggers
        if humidity < 30:
            score += 20
            triggers.append("Very dry air")
        elif humidity > 80:
            score += 15
            triggers.append("High humidity")
        
        # Temperature extremes
        if temperature < -5 or temperature > 35:
            score += 15
            triggers.append("Extreme temperature")
        
        # Clamp score
        score = max(0, min(100, score))
        
        # Determine risk level
        if score >= 60:
            risk_level = RiskLevel.VERY_HIGH
        elif score >= 40:
            risk_level = RiskLevel.HIGH
        elif score >= 20:
            risk_level = RiskLevel.MODERATE
        else:
            risk_level = RiskLevel.LOW
        
        # Generate recommendations
        recommendations = self._get_migraine_recommendations(risk_level, triggers)
        
        return MigraineRisk(
            risk_level=risk_level,
            risk_score=score,
            pressure_change=round(pressure_change, 1),
            pressure_trend=pressure_trend,
            triggers=triggers if triggers else ["No significant triggers detected"],
            recommendations=recommendations
        )
    
    def _get_flu_recommendations(
        self,
        risk_level: RiskLevel,
        factors: List[str]
    ) -> List[str]:
        """Generate flu prevention recommendations."""
        recommendations = []
        
        if risk_level in [RiskLevel.VERY_HIGH, RiskLevel.HIGH]:
            recommendations.extend([
                "💉 Ensure your flu vaccination is up to date",
                "😷 Consider wearing a mask in crowded spaces",
                "🧼 Wash hands frequently for at least 20 seconds",
                "🏠 Stay home if you feel unwell"
            ])
        elif risk_level == RiskLevel.MODERATE:
            recommendations.extend([
                "💧 Stay hydrated to keep mucous membranes moist",
                "🧼 Practice good hand hygiene",
                "😴 Get adequate sleep to support immune function"
            ])
        else:
            recommendations.extend([
                "✅ Low flu risk - maintain normal precautions",
                "💪 Good time for outdoor activities"
            ])
        
        return recommendations[:4]
    
    def _get_migraine_recommendations(
        self,
        risk_level: RiskLevel,
        triggers: List[str]
    ) -> List[str]:
        """Generate migraine prevention recommendations."""
        recommendations = []
        
        if risk_level in [RiskLevel.VERY_HIGH, RiskLevel.HIGH]:
            recommendations.extend([
                "💊 Take preventive medication if prescribed",
                "💧 Stay well hydrated",
                "🕶️ Avoid bright lights and loud sounds",
                "😴 Maintain regular sleep schedule"
            ])
        elif risk_level == RiskLevel.MODERATE:
            recommendations.extend([
                "⚠️ Be aware of potential triggers today",
                "💧 Drink extra water",
                "☕ Monitor caffeine intake"
            ])
        else:
            recommendations.extend([
                "✅ Low trigger conditions",
                "🏃 Good day for normal activities"
            ])
        
        return recommendations[:4]
