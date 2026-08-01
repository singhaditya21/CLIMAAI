"""
Activity Forecast Service - Optimal time predictions for outdoor activities.
Calculates activity scores based on weather conditions.
"""
from typing import Dict, List, Optional, Tuple
from datetime import datetime, timedelta
from pydantic import BaseModel
from enum import Enum


class ActivityType(str, Enum):
    """Supported outdoor activities."""
    RUNNING = "running"
    CYCLING = "cycling"
    GOLF = "golf"
    HIKING = "hiking"
    BEACH = "beach"
    SKIING = "skiing"
    TENNIS = "tennis"
    PHOTOGRAPHY = "photography"


class TimeWindow(BaseModel):
    """Time window with activity score."""
    start_time: datetime
    end_time: datetime
    score: int  # 0-100
    rating: str  # "Excellent", "Good", "Fair", "Poor"
    conditions_summary: str


class ActivityForecast(BaseModel):
    """Activity forecast for a specific activity type."""
    activity: str
    activity_display_name: str
    today_score: int
    today_rating: str
    best_time_today: Optional[TimeWindow]
    best_time_week: Optional[TimeWindow]
    hourly_scores: List[Dict]  # [{time, score, factors}]
    recommendations: List[str]
    factors_used: List[str]


class ActivityForecastService:
    """
    Service for calculating optimal activity times based on weather.
    
    Each activity has different ideal conditions:
    - Running: Temp 10-15°C, humidity <70%, low wind, good AQI
    - Cycling: Temp 15-22°C, humidity <65%, wind <20km/h, no rain
    - Golf: Temp 18-28°C, humidity <70%, wind <15km/h, no rain, moderate UV
    - Hiking: Temp 15-25°C, good visibility, low precipitation, moderate humidity
    - Beach: Temp 25-35°C, high UV, low wind, no rain
    - Skiing: Temp <0°C, fresh snow, moderate wind
    """
    
    # Ideal conditions for each activity
    ACTIVITY_CONDITIONS = {
        ActivityType.RUNNING: {
            "temp_min": 8, "temp_max": 18,
            "humidity_max": 75,
            "wind_max": 25,
            "rain_max": 0.5,
            "aqi_max": 100,
            "display_name": "Running",
            "icon": "🏃",
            "key_factors": ["temperature", "humidity", "air_quality", "wind"]
        },
        ActivityType.CYCLING: {
            "temp_min": 12, "temp_max": 25,
            "humidity_max": 65,
            "wind_max": 20,
            "rain_max": 0,
            "visibility_min": 8000,
            "display_name": "Cycling",
            "icon": "🚴",
            "key_factors": ["temperature", "wind", "rain_probability", "visibility"]
        },
        ActivityType.GOLF: {
            "temp_min": 15, "temp_max": 30,
            "humidity_max": 70,
            "wind_max": 15,
            "rain_max": 0,
            "uv_max": 8,
            "display_name": "Golf",
            "icon": "⛳",
            "key_factors": ["temperature", "wind", "rain_probability", "uv_index"]
        },
        ActivityType.HIKING: {
            "temp_min": 10, "temp_max": 28,
            "humidity_max": 80,
            "precipitation_prob_max": 30,
            "visibility_min": 10000,
            "display_name": "Hiking",
            "icon": "🥾",
            "key_factors": ["temperature", "precipitation", "visibility", "humidity"]
        },
        ActivityType.BEACH: {
            "temp_min": 25, "temp_max": 38,
            "rain_max": 0,
            "wind_max": 25,
            "uv_min": 4,
            "display_name": "Beach",
            "icon": "🏖️",
            "key_factors": ["temperature", "rain_probability", "uv_index", "wind"]
        },
        ActivityType.SKIING: {
            "temp_max": 2,
            "wind_max": 30,
            "visibility_min": 5000,
            "display_name": "Skiing",
            "icon": "⛷️",
            "key_factors": ["temperature", "snow", "wind", "visibility"]
        },
        ActivityType.TENNIS: {
            "temp_min": 15, "temp_max": 32,
            "humidity_max": 70,
            "wind_max": 15,
            "rain_max": 0,
            "display_name": "Tennis",
            "icon": "🎾",
            "key_factors": ["temperature", "wind", "rain_probability", "humidity"]
        },
        ActivityType.PHOTOGRAPHY: {
            "rain_max": 0.1,
            "visibility_min": 15000,
            "display_name": "Outdoor Photography",
            "icon": "📷",
            "key_factors": ["visibility", "rain_probability", "cloud_cover", "golden_hour"]
        },
    }
    
    def calculate_activity_score(
        self,
        activity: ActivityType,
        temperature: float,
        humidity: int,
        wind_speed: float,
        precipitation: float,
        precipitation_probability: int,
        uv_index: float,
        visibility: float = 10000,
        aqi: int = 50,
        cloud_cover: int = 50,
        is_day: bool = True
    ) -> Tuple[int, str, List[str]]:
        """
        Calculate activity suitability score.
        
        Returns:
            Tuple of (score 0-100, rating, list of reasons)
        """
        conditions = self.ACTIVITY_CONDITIONS.get(activity, {})
        score = 100
        deductions = []
        
        # Temperature scoring
        temp_min = conditions.get("temp_min", -50)
        temp_max = conditions.get("temp_max", 50)
        
        if temperature < temp_min:
            penalty = min(30, (temp_min - temperature) * 3)
            score -= penalty
            deductions.append(f"Too cold ({temperature:.0f}°C)")
        elif temperature > temp_max:
            penalty = min(30, (temperature - temp_max) * 3)
            score -= penalty
            deductions.append(f"Too hot ({temperature:.0f}°C)")
        
        # Humidity scoring
        humidity_max = conditions.get("humidity_max", 100)
        if humidity > humidity_max:
            penalty = min(20, (humidity - humidity_max) * 0.5)
            score -= penalty
            deductions.append(f"High humidity ({humidity}%)")
        
        # Wind scoring
        wind_max = conditions.get("wind_max", 100)
        if wind_speed > wind_max:
            penalty = min(25, (wind_speed - wind_max) * 1.5)
            score -= penalty
            deductions.append(f"Windy ({wind_speed:.0f} km/h)")
        
        # Precipitation scoring
        rain_max = conditions.get("rain_max", 10)
        if precipitation > rain_max:
            penalty = min(40, precipitation * 10)
            score -= penalty
            deductions.append(f"Rain ({precipitation:.1f} mm)")
        
        precip_prob_max = conditions.get("precipitation_prob_max", 100)
        if precipitation_probability > precip_prob_max:
            penalty = min(15, (precipitation_probability - precip_prob_max) * 0.3)
            score -= penalty
            deductions.append(f"{precipitation_probability}% rain chance")
        
        # UV scoring (for beach, want high; for golf, want moderate)
        uv_max = conditions.get("uv_max", 11)
        uv_min = conditions.get("uv_min", 0)
        
        if uv_index > uv_max:
            penalty = min(15, (uv_index - uv_max) * 2)
            score -= penalty
            deductions.append(f"High UV ({uv_index:.0f})")
        elif activity == ActivityType.BEACH and uv_index < uv_min:
            penalty = min(15, (uv_min - uv_index) * 3)
            score -= penalty
            deductions.append(f"Low UV ({uv_index:.0f})")
        
        # Visibility scoring
        visibility_min = conditions.get("visibility_min", 0)
        if visibility < visibility_min:
            penalty = min(20, (visibility_min - visibility) / 500)
            score -= penalty
            deductions.append("Poor visibility")
        
        # AQI scoring (primarily for running)
        aqi_max = conditions.get("aqi_max", 300)
        if aqi > aqi_max:
            penalty = min(30, (aqi - aqi_max) * 0.3)
            score -= penalty
            deductions.append(f"Poor air quality (AQI {aqi})")
        
        # Night penalty for most activities
        if not is_day and activity not in [ActivityType.PHOTOGRAPHY]:
            score -= 20
            deductions.append("Nighttime")
        
        # Ensure score is in valid range
        score = max(0, min(100, int(score)))
        
        # Determine rating
        if score >= 80:
            rating = "Excellent"
        elif score >= 60:
            rating = "Good"
        elif score >= 40:
            rating = "Fair"
        else:
            rating = "Poor"
        
        return score, rating, deductions
    
    def get_activity_forecast(
        self,
        activity: ActivityType,
        hourly_forecast: List[Dict],
        current_aqi: int = 50
    ) -> ActivityForecast:
        """
        Generate activity forecast from hourly weather data.
        
        Args:
            activity: Activity type
            hourly_forecast: List of hourly weather dicts with keys:
                time, temperature, humidity, wind_speed, precipitation,
                precipitation_probability, uv_index, cloud_cover
            current_aqi: Current air quality index
        
        Returns:
            ActivityForecast with scores and recommendations
        """
        conditions = self.ACTIVITY_CONDITIONS.get(activity, {})
        hourly_scores = []
        best_hour = None
        best_score = 0
        
        for hour in hourly_forecast:
            # Calculate score for this hour
            score, rating, factors = self.calculate_activity_score(
                activity=activity,
                temperature=hour.get("temperature", 20),
                humidity=hour.get("humidity", 50),
                wind_speed=hour.get("wind_speed", 0),
                precipitation=hour.get("precipitation", 0),
                precipitation_probability=hour.get("precipitation_probability", 0),
                uv_index=hour.get("uv_index", 5),
                visibility=hour.get("visibility", 10000),
                aqi=current_aqi,
                cloud_cover=hour.get("cloud_cover", 50),
                is_day=hour.get("is_day", True)
            )
            
            hourly_scores.append({
                "time": hour.get("time"),
                "score": score,
                "rating": rating,
                "factors": factors
            })
            
            # Track best hour
            if score > best_score:
                best_score = score
                best_hour = hour
        
        # Calculate today's average score (first 12 daylight hours)
        today_scores = [h["score"] for h in hourly_scores[:12] if h["score"] > 0]
        today_avg = sum(today_scores) / len(today_scores) if today_scores else 0
        
        if today_avg >= 80:
            today_rating = "Excellent"
        elif today_avg >= 60:
            today_rating = "Good"
        elif today_avg >= 40:
            today_rating = "Fair"
        else:
            today_rating = "Poor"
        
        # Best time window
        best_window = None
        if best_hour:
            best_time = best_hour.get("time")
            if isinstance(best_time, str):
                best_time = datetime.fromisoformat(best_time)
            best_window = TimeWindow(
                start_time=best_time,
                end_time=best_time + timedelta(hours=2),
                score=best_score,
                rating="Excellent" if best_score >= 80 else "Good" if best_score >= 60 else "Fair",
                conditions_summary=self._summarize_conditions(best_hour, activity)
            )
        
        # Generate recommendations
        recommendations = self._generate_recommendations(
            activity, today_avg, hourly_scores, conditions
        )
        
        return ActivityForecast(
            activity=activity.value,
            activity_display_name=f"{conditions.get('icon', '🏃')} {conditions.get('display_name', activity.value)}",
            today_score=int(today_avg),
            today_rating=today_rating,
            best_time_today=best_window,
            best_time_week=best_window,  # Would need weekly data
            hourly_scores=hourly_scores[:24],  # First 24 hours
            recommendations=recommendations,
            factors_used=conditions.get("key_factors", [])
        )
    
    def _summarize_conditions(self, hour: Dict, activity: ActivityType) -> str:
        """Generate summary of conditions for a time slot."""
        temp = hour.get("temperature", 20)
        wind = hour.get("wind_speed", 0)
        precip_prob = hour.get("precipitation_probability", 0)
        
        parts = [f"{temp:.0f}°C"]
        
        if wind > 10:
            parts.append(f"wind {wind:.0f} km/h")
        else:
            parts.append("calm")
        
        if precip_prob > 20:
            parts.append(f"{precip_prob}% rain")
        else:
            parts.append("dry")
        
        return ", ".join(parts)
    
    def _generate_recommendations(
        self,
        activity: ActivityType,
        today_score: float,
        hourly_scores: List[Dict],
        conditions: Dict
    ) -> List[str]:
        """Generate activity-specific recommendations."""
        recommendations = []
        
        if today_score >= 80:
            recommendations.append(f"✅ Excellent conditions for {conditions.get('display_name', 'activity')} today!")
        elif today_score >= 60:
            recommendations.append("👍 Good conditions, plan for the best window.")
        elif today_score >= 40:
            recommendations.append("⚠️ Fair conditions - check hourly forecast.")
        else:
            recommendations.append("❌ Poor conditions - consider indoor alternatives.")
        
        # Find best window
        best_hours = [h for h in hourly_scores if h["score"] >= 70]
        if best_hours:
            best = best_hours[0]
            time = best.get("time")
            if isinstance(time, str):
                try:
                    time = datetime.fromisoformat(time)
                except:
                    pass
            if isinstance(time, datetime):
                recommendations.append(f"🕐 Best time: {time.strftime('%I:%M %p')} ({best['score']}/100)")
        
        # Activity-specific tips
        if activity == ActivityType.RUNNING:
            morning_scores = [h["score"] for h in hourly_scores[:6]]
            evening_scores = [h["score"] for h in hourly_scores[17:20] if len(hourly_scores) > 17]
            
            if morning_scores and evening_scores:
                if sum(morning_scores) / len(morning_scores) > sum(evening_scores) / max(1, len(evening_scores)):
                    recommendations.append("🌅 Morning run recommended")
                else:
                    recommendations.append("🌆 Evening run recommended")
        
        elif activity == ActivityType.GOLF:
            recommendations.append("💡 Book tee time 2-3 hours before sunset for cooler temps")
        
        elif activity == ActivityType.HIKING:
            recommendations.append("🥾 Start early for longer trails")
        
        return recommendations[:4]  # Limit to 4 recommendations
    
    def get_all_activities_summary(
        self,
        hourly_forecast: List[Dict],
        current_aqi: int = 50
    ) -> List[Dict]:
        """
        Get summary scores for all activities.
        
        Returns sorted list of activities with today's scores.
        """
        summaries = []
        
        for activity in ActivityType:
            forecast = self.get_activity_forecast(
                activity, hourly_forecast, current_aqi
            )
            
            conditions = self.ACTIVITY_CONDITIONS.get(activity, {})
            
            summaries.append({
                "activity": activity.value,
                "display_name": conditions.get("display_name", activity.value),
                "icon": conditions.get("icon", "🏃"),
                "score": forecast.today_score,
                "rating": forecast.today_rating,
                "best_time": forecast.best_time_today.start_time.strftime("%I:%M %p") if forecast.best_time_today else None
            })
        
        # Sort by score descending
        summaries.sort(key=lambda x: x["score"], reverse=True)
        
        return summaries
