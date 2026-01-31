"""
Pollen Forecast Service - Google Pollen API Integration.
Provides detailed pollen counts for tree, grass, and weed allergens.
"""
import httpx
import json
from typing import Dict, List, Optional
from datetime import datetime, date
import redis.asyncio as redis
from pydantic import BaseModel
from enum import Enum
from ..config import get_settings

settings = get_settings()


class PollenLevel(str, Enum):
    """Pollen risk levels."""
    NONE = "none"
    VERY_LOW = "very_low"
    LOW = "low"
    MODERATE = "moderate"
    HIGH = "high"
    VERY_HIGH = "very_high"


class PollenType(BaseModel):
    """Pollen data for a specific type (tree/grass/weed)."""
    display_name: str  # "Tree Pollen", "Grass Pollen", etc.
    level: PollenLevel
    index: int  # 0-5 scale
    species: List[str]  # Contributing species
    health_advice: Optional[str]
    
    @property
    def color(self) -> str:
        """Color for UI display."""
        colors = {
            PollenLevel.NONE: "#4CAF50",
            PollenLevel.VERY_LOW: "#8BC34A",
            PollenLevel.LOW: "#CDDC39",
            PollenLevel.MODERATE: "#FFC107",
            PollenLevel.HIGH: "#FF9800",
            PollenLevel.VERY_HIGH: "#F44336"
        }
        return colors.get(self.level, "#9E9E9E")


class DailyPollen(BaseModel):
    """Daily pollen forecast."""
    date: date
    tree: PollenType
    grass: PollenType
    weed: PollenType
    overall_index: int  # Highest of all types
    overall_level: PollenLevel
    

class PollenResponse(BaseModel):
    """Complete pollen forecast response."""
    location: Dict[str, float]
    forecast: List[DailyPollen]  # Up to 5 days
    last_updated: datetime
    health_recommendations: List[str]


class PollenService:
    """
    Service for fetching pollen forecast data.
    
    Uses Google Pollen API for detailed pollen data:
    - Tree, grass, weed pollen levels
    - Species-specific information
    - 5-day forecast
    - Health recommendations
    
    Free tier: 5,000 calls/month
    API key required from Google Cloud Console
    """
    
    GOOGLE_POLLEN_API = "https://pollen.googleapis.com/v1/forecast:lookup"
    
    def __init__(self):
        self.redis_client: Optional[redis.Redis] = None
        self.http_client = httpx.AsyncClient(timeout=30.0)
        self.api_key = getattr(settings, 'GOOGLE_POLLEN_API_KEY', None)
    
    async def _get_redis(self) -> redis.Redis:
        """Get or create Redis client."""
        if self.redis_client is None:
            self.redis_client = await redis.from_url(settings.REDIS_URL)
        return self.redis_client
    
    async def close(self):
        """Close HTTP client and Redis connection."""
        await self.http_client.aclose()
        if self.redis_client:
            await self.redis_client.close()
    
    def _parse_level(self, index: int) -> PollenLevel:
        """Convert numeric index to PollenLevel."""
        if index == 0:
            return PollenLevel.NONE
        elif index == 1:
            return PollenLevel.VERY_LOW
        elif index == 2:
            return PollenLevel.LOW
        elif index == 3:
            return PollenLevel.MODERATE
        elif index == 4:
            return PollenLevel.HIGH
        else:
            return PollenLevel.VERY_HIGH
    
    def _parse_pollen_type(
        self,
        data: dict,
        type_name: str
    ) -> PollenType:
        """Parse pollen type data from API response."""
        type_info = data.get(type_name, {})
        index = type_info.get("index", 0) or 0
        
        species = []
        health_advice = None
        
        # Extract plant species info if available
        plant_info = type_info.get("plantInfo", [])
        for plant in plant_info:
            if plant.get("displayName"):
                species.append(plant["displayName"])
            if plant.get("healthRecommendations"):
                health_advice = plant["healthRecommendations"]
        
        display_names = {
            "tree": "Tree Pollen",
            "grass": "Grass Pollen",
            "weed": "Weed Pollen"
        }
        
        return PollenType(
            display_name=display_names.get(type_name, type_name.title()),
            level=self._parse_level(index),
            index=index,
            species=species[:5],  # Limit to top 5
            health_advice=health_advice
        )
    
    def _generate_health_recommendations(
        self,
        tree: PollenType,
        grass: PollenType,
        weed: PollenType
    ) -> List[str]:
        """Generate health recommendations based on pollen levels."""
        recommendations = []
        
        high_levels = [PollenLevel.HIGH, PollenLevel.VERY_HIGH]
        moderate_levels = [PollenLevel.MODERATE]
        
        # Check each pollen type
        if tree.level in high_levels:
            recommendations.append(f"🌳 High tree pollen: Consider staying indoors during morning hours")
        if grass.level in high_levels:
            recommendations.append(f"🌾 High grass pollen: Avoid freshly cut lawns")
        if weed.level in high_levels:
            recommendations.append(f"🌿 High weed pollen (ragweed): Keep windows closed")
        
        # General recommendations for any high level
        if any(p.level in high_levels for p in [tree, grass, weed]):
            recommendations.extend([
                "💊 Take allergy medication proactively",
                "😷 Consider wearing a mask outdoors",
                "🚿 Shower after outdoor activities"
            ])
        elif any(p.level in moderate_levels for p in [tree, grass, weed]):
            recommendations.extend([
                "⚠️ Moderate pollen levels: sensitive individuals may experience symptoms",
                "🕐 Best outdoor time: after rain or in evening"
            ])
        else:
            recommendations.append("✅ Pollen levels are low - good conditions for outdoor activities")
        
        return recommendations
    
    async def get_pollen_forecast(
        self,
        latitude: float,
        longitude: float,
        days: int = 5
    ) -> PollenResponse:
        """
        Get pollen forecast for a location.
        
        Args:
            latitude: Location latitude
            longitude: Location longitude
            days: Number of days (1-5)
            
        Returns:
            PollenResponse with daily forecasts
        """
        # Check cache first (valid for 6 hours)
        cache_key = f"pollen:{latitude:.2f}:{longitude:.2f}"
        
        try:
            redis_client = await self._get_redis()
            cached = await redis_client.get(cache_key)
            if cached:
                data = json.loads(cached)
                return PollenResponse(**data)
        except Exception as e:
            print(f"Redis cache error: {e}")
        
        # If no API key, return mock data for development
        if not self.api_key:
            return self._get_mock_pollen_data(latitude, longitude)
        
        # Fetch from Google Pollen API
        params = {
            "key": self.api_key,
            "location.latitude": latitude,
            "location.longitude": longitude,
            "days": min(days, 5)
        }
        
        response = await self.http_client.get(self.GOOGLE_POLLEN_API, params=params)
        response.raise_for_status()
        data = response.json()
        
        # Parse daily forecasts
        daily_forecasts = []
        for day_data in data.get("dailyInfo", []):
            date_info = day_data.get("date", {})
            forecast_date = date(
                year=date_info.get("year", 2026),
                month=date_info.get("month", 1),
                day=date_info.get("day", 1)
            )
            
            pollen_info = day_data.get("pollenTypeInfo", {})
            
            tree = self._parse_pollen_type(pollen_info, "tree")
            grass = self._parse_pollen_type(pollen_info, "grass")
            weed = self._parse_pollen_type(pollen_info, "weed")
            
            overall_index = max(tree.index, grass.index, weed.index)
            
            daily_forecasts.append(DailyPollen(
                date=forecast_date,
                tree=tree,
                grass=grass,
                weed=weed,
                overall_index=overall_index,
                overall_level=self._parse_level(overall_index)
            ))
        
        # Generate recommendations based on today's levels
        health_recs = []
        if daily_forecasts:
            today = daily_forecasts[0]
            health_recs = self._generate_health_recommendations(
                today.tree, today.grass, today.weed
            )
        
        result = PollenResponse(
            location={"latitude": latitude, "longitude": longitude},
            forecast=daily_forecasts,
            last_updated=datetime.utcnow(),
            health_recommendations=health_recs
        )
        
        # Cache for 6 hours
        try:
            redis_client = await self._get_redis()
            cache_data = result.model_dump(mode="json")
            await redis_client.setex(cache_key, 21600, json.dumps(cache_data, default=str))
        except Exception as e:
            print(f"Redis cache set error: {e}")
        
        return result
    
    def _get_mock_pollen_data(
        self,
        latitude: float,
        longitude: float
    ) -> PollenResponse:
        """Generate mock pollen data for development without API key."""
        today = date.today()
        
        # Simulate seasonal pollen patterns
        month = today.month
        
        # Tree pollen peaks in spring (March-May)
        # Grass pollen peaks in summer (May-July)
        # Weed pollen peaks in fall (August-October)
        
        if 3 <= month <= 5:
            tree_level, grass_level, weed_level = 4, 2, 1
        elif 5 <= month <= 7:
            tree_level, grass_level, weed_level = 2, 4, 2
        elif 8 <= month <= 10:
            tree_level, grass_level, weed_level = 1, 2, 4
        else:
            tree_level, grass_level, weed_level = 1, 1, 1
        
        forecasts = []
        for i in range(5):
            forecast_date = date(today.year, today.month, today.day + i)
            
            tree = PollenType(
                display_name="Tree Pollen",
                level=self._parse_level(tree_level),
                index=tree_level,
                species=["Oak", "Birch", "Maple"],
                health_advice="Limit outdoor exposure in morning" if tree_level >= 3 else None
            )
            
            grass = PollenType(
                display_name="Grass Pollen",
                level=self._parse_level(grass_level),
                index=grass_level,
                species=["Timothy", "Bermuda", "Kentucky Bluegrass"],
                health_advice="Avoid freshly cut grass" if grass_level >= 3 else None
            )
            
            weed = PollenType(
                display_name="Weed Pollen",
                level=self._parse_level(weed_level),
                index=weed_level,
                species=["Ragweed", "Sagebrush", "Pigweed"],
                health_advice="Keep windows closed" if weed_level >= 3 else None
            )
            
            overall = max(tree_level, grass_level, weed_level)
            
            forecasts.append(DailyPollen(
                date=forecast_date,
                tree=tree,
                grass=grass,
                weed=weed,
                overall_index=overall,
                overall_level=self._parse_level(overall)
            ))
        
        health_recs = self._generate_health_recommendations(
            forecasts[0].tree, forecasts[0].grass, forecasts[0].weed
        )
        
        return PollenResponse(
            location={"latitude": latitude, "longitude": longitude},
            forecast=forecasts,
            last_updated=datetime.utcnow(),
            health_recommendations=health_recs
        )
