"""
Personalization Service for ClimaAI

Learns user preferences and provides personalized weather recommendations
based on behavior patterns, time of day, and weather conditions.
"""

from datetime import datetime, timezone
from typing import Dict, List, Optional, Any
from pydantic import BaseModel, Field
from collections import defaultdict


class UserEvent(BaseModel):
    """User interaction event for learning preferences"""
    user_id: str
    event_type: str  # screen_view, notification_click, feature_use, etc.
    event_data: Dict[str, Any]
    weather_context: Optional[Dict[str, Any]] = None
    # default_factory, not a plain default: a bare datetime.now(timezone.utc) is
    # evaluated once at import, so every event got the module-load time.
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class UserPreferenceProfile(BaseModel):
    """Learned user preference profile"""
    user_id: str
    
    # Time preferences
    preferred_briefing_time: str = "07:00"
    active_hours: List[int] = list(range(7, 22))  # Default 7am-10pm
    
    # Weather sensitivity
    rain_sensitivity: float = 0.5  # 0-1, how much they care about rain
    temperature_sensitivity: float = 0.5
    uv_sensitivity: float = 0.5
    
    # Feature preferences
    favorite_features: List[str] = []  # AI insights, radar, forecast, etc.
    notification_preferences: Dict[str, bool] = {
        "rain_alerts": True,
        "severe_weather": True,
        "uv_warnings": True,
        "morning_briefing": True
    }
    
    # Activity patterns
    outdoor_activity_score: float = 0.5  # 0-1, likelihood of outdoor activities
    commute_times: List[str] = []  # e.g. ["08:00", "18:00"]
    
    # Learned thresholds
    cold_threshold: float = 15.0  # Temperature they consider cold
    hot_threshold: float = 28.0  # Temperature they consider hot
    
    last_updated: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class PersonalizationService:
    """
    Learns user preferences from behavior and provides personalized recommendations.
    Uses lightweight in-memory storage with periodic persistence.
    """
    
    def __init__(self, redis_client=None):
        self.redis = redis_client
        self._profiles: Dict[str, UserPreferenceProfile] = {}
        self._events: Dict[str, List[UserEvent]] = defaultdict(list)
    
    async def track_event(self, event: UserEvent) -> None:
        """Track a user interaction event for learning"""
        self._events[event.user_id].append(event)
        
        # Keep only last 1000 events per user
        if len(self._events[event.user_id]) > 1000:
            self._events[event.user_id] = self._events[event.user_id][-1000:]
        
        # Trigger learning if enough events
        if len(self._events[event.user_id]) % 50 == 0:
            await self._learn_preferences(event.user_id)
        
        # Persist to Redis if available
        if self.redis:
            key = f"personalization:events:{event.user_id}"
            await self.redis.lpush(key, event.json())
            await self.redis.ltrim(key, 0, 999)
    
    async def get_profile(self, user_id: str) -> UserPreferenceProfile:
        """Get or create user preference profile"""
        if user_id not in self._profiles:
            # Try loading from Redis
            if self.redis:
                data = await self.redis.get(f"personalization:profile:{user_id}")
                if data:
                    self._profiles[user_id] = UserPreferenceProfile.parse_raw(data)
                else:
                    self._profiles[user_id] = UserPreferenceProfile(user_id=user_id)
            else:
                self._profiles[user_id] = UserPreferenceProfile(user_id=user_id)
        
        return self._profiles[user_id]
    
    async def _learn_preferences(self, user_id: str) -> None:
        """Learn preferences from user events"""
        events = self._events.get(user_id, [])
        if not events:
            return
        
        profile = await self.get_profile(user_id)
        
        # Analyze screen views to find favorite features
        screen_views = [e for e in events if e.event_type == "screen_view"]
        if screen_views:
            feature_counts = defaultdict(int)
            for event in screen_views:
                screen = event.event_data.get("screen", "")
                feature_counts[screen] += 1
            
            # Top 3 most viewed screens
            sorted_features = sorted(feature_counts.items(), key=lambda x: x[1], reverse=True)
            profile.favorite_features = [f[0] for f in sorted_features[:3]]
        
        # Analyze notification interactions
        notification_events = [e for e in events if e.event_type == "notification_click"]
        if notification_events:
            # Learn which notifications they engage with
            for event in notification_events:
                notif_type = event.event_data.get("notification_type")
                if notif_type:
                    profile.notification_preferences[notif_type] = True
        
        # Analyze active hours
        hour_activity = defaultdict(int)
        for event in events:
            hour = event.timestamp.hour
            hour_activity[hour] += 1
        
        if hour_activity:
            # Find hours with significant activity
            active_hours = [h for h, count in hour_activity.items() if count >= 2]
            if active_hours:
                profile.active_hours = sorted(active_hours)
                
                # Infer preferred briefing time (first active hour)
                profile.preferred_briefing_time = f"{min(active_hours):02d}:00"
        
        # Learn temperature preferences from weather context
        weather_events = [e for e in events if e.weather_context]
        if weather_events:
            temps_when_active = []
            for event in weather_events:
                temp = event.weather_context.get("temperature")
                if temp is not None:
                    temps_when_active.append(temp)
            
            if temps_when_active:
                avg_temp = sum(temps_when_active) / len(temps_when_active)
                # Adjust thresholds based on activity temperature
                profile.cold_threshold = avg_temp - 10
                profile.hot_threshold = avg_temp + 10
        
        profile.last_updated = datetime.now(timezone.utc)
        self._profiles[user_id] = profile
        
        # Persist to Redis
        if self.redis:
            await self.redis.set(
                f"personalization:profile:{user_id}",
                profile.json(),
                ex=86400 * 30  # 30 days
            )
    
    async def get_personalized_recommendations(
        self,
        user_id: str,
        weather: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Get personalized recommendations based on user profile and current weather"""
        profile = await self.get_profile(user_id)
        current_hour = datetime.now(timezone.utc).hour
        
        recommendations = {
            "priority_features": profile.favorite_features[:2],
            "notifications": [],
            "activity_suggestions": [],
            "outfit_adjustments": []
        }
        
        temp = weather.get("temperature", 20)
        
        # Temperature-based recommendations
        if temp < profile.cold_threshold:
            recommendations["outfit_adjustments"].append("Bundle up - it's cold for you!")
            recommendations["notifications"].append({
                "type": "cold_alert",
                "message": f"Temperature is {temp}°, below your preferred {profile.cold_threshold}°"
            })
        elif temp > profile.hot_threshold:
            recommendations["outfit_adjustments"].append("Dress light - it's hot for you!")
            recommendations["notifications"].append({
                "type": "heat_alert", 
                "message": f"Temperature is {temp}°, above your preferred {profile.hot_threshold}°"
            })
        
        # Time-based recommendations.
        #
        # The sensitivity gates below read >= rather than >. _learn_preferences
        # never writes uv_sensitivity or outdoor_activity_score, so every
        # profile that has ever existed holds exactly the 0.5 default; a strict
        # > against that default made both branches unreachable for every user
        # rather than merely rare. >= treats the default as "average
        # sensitivity", which is what the thresholds were describing.
        if current_hour in profile.active_hours:
            if weather.get("uv_index", 0) > 6 and profile.uv_sensitivity >= 0.5:
                recommendations["notifications"].append({
                    "type": "uv_warning",
                    "message": "High UV during your active hours - don't forget sunscreen!"
                })
        
        # Outdoor activity likelihood
        if profile.outdoor_activity_score >= 0.5:
            precip_prob = weather.get("precipitation_probability", 0)
            if precip_prob > 50:
                recommendations["activity_suggestions"].append(
                    "Consider indoor alternatives today - rain is likely"
                )
            else:
                recommendations["activity_suggestions"].append(
                    "Great day for outdoor activities!"
                )
        
        return recommendations
    
    async def get_optimal_notification_time(self, user_id: str) -> str:
        """Get the optimal time to send notifications to this user"""
        profile = await self.get_profile(user_id)
        return profile.preferred_briefing_time
    
    async def should_send_notification(
        self,
        user_id: str,
        notification_type: str
    ) -> bool:
        """Check if user wants this type of notification"""
        profile = await self.get_profile(user_id)
        return profile.notification_preferences.get(notification_type, True)


# Singleton instance
personalization_service = PersonalizationService()
