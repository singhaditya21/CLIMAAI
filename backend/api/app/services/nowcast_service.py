"""
Nowcast Service - Minute-by-minute precipitation predictions.
Equivalent to AccuWeather's MinuteCast® feature.
Uses Open-Meteo's 15-minute precipitation forecast with interpolation.
"""
import httpx
import json
from typing import Dict, List, Optional, Tuple
from datetime import datetime, timedelta
import redis.asyncio as redis
from pydantic import BaseModel
from ..config import get_settings

settings = get_settings()


class NowcastMinute(BaseModel):
    """Single minute precipitation data."""
    time: datetime
    precipitation: float  # mm
    precipitation_probability: int  # 0-100
    intensity: str  # none, light, moderate, heavy
    is_precipitation: bool


class NowcastResponse(BaseModel):
    """Nowcast response with minute-by-minute precipitation."""
    location: Dict[str, float]
    timezone: str
    summary: str
    precipitation_start: Optional[datetime]  # When rain starts
    precipitation_end: Optional[datetime]    # When rain stops
    total_precipitation: float               # Total mm in forecast period
    minutes: List[NowcastMinute]             # Minute-by-minute data
    last_updated: datetime


class NowcastService:
    """
    Service for minute-by-minute precipitation nowcasting.
    
    Uses Open-Meteo's 15-minute precipitation forecast and interpolates
    to provide minute-level granularity similar to AccuWeather MinuteCast.
    """
    
    OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"
    
    def __init__(self):
        self.redis_client: Optional[redis.Redis] = None
        self.http_client = httpx.AsyncClient(timeout=30.0)
    
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
    
    def _classify_intensity(self, precipitation_mm: float) -> str:
        """
        Classify precipitation intensity based on mm/hour equivalent.
        
        Light: < 2.5 mm/h
        Moderate: 2.5 - 7.5 mm/h
        Heavy: > 7.5 mm/h
        """
        # Convert 15-min to hourly rate for classification
        hourly_rate = precipitation_mm * 4
        
        if hourly_rate < 0.1:
            return "none"
        elif hourly_rate < 2.5:
            return "light"
        elif hourly_rate < 7.5:
            return "moderate"
        else:
            return "heavy"
    
    def _interpolate_minutes(
        self, 
        data_15min: List[Tuple[datetime, float, int]]
    ) -> List[NowcastMinute]:
        """
        Interpolate 15-minute data to per-minute data.
        
        Uses linear interpolation to smooth precipitation values
        between 15-minute intervals.
        """
        minutes = []
        
        for i, (time_15, precip_15, prob_15) in enumerate(data_15min):
            # Get next data point for interpolation
            if i < len(data_15min) - 1:
                next_time, next_precip, next_prob = data_15min[i + 1]
            else:
                next_precip, next_prob = precip_15, prob_15
            
            # Generate 15 minutes of data
            for minute_offset in range(15):
                # Stop if we've generated 2 hours (120 minutes)
                if len(minutes) >= 120:
                    break
                
                # Linear interpolation
                fraction = minute_offset / 15.0
                interp_precip = precip_15 + (next_precip - precip_15) * fraction
                interp_prob = int(prob_15 + (next_prob - prob_15) * fraction)
                
                # Per-minute precipitation (divide 15-min total)
                minute_precip = interp_precip / 15.0
                
                current_time = time_15 + timedelta(minutes=minute_offset)
                intensity = self._classify_intensity(interp_precip)
                
                minutes.append(NowcastMinute(
                    time=current_time,
                    precipitation=round(minute_precip, 3),
                    precipitation_probability=max(0, min(100, interp_prob)),
                    intensity=intensity,
                    is_precipitation=minute_precip > 0.01
                ))
            
            if len(minutes) >= 120:
                break
        
        return minutes[:120]  # Cap at 2 hours
    
    def _find_precipitation_window(
        self, 
        minutes: List[NowcastMinute]
    ) -> Tuple[Optional[datetime], Optional[datetime]]:
        """
        Find when precipitation starts and stops.
        
        Returns (start_time, end_time) or (None, None) if no precipitation.
        """
        start_time = None
        end_time = None
        
        for minute in minutes:
            if minute.is_precipitation:
                if start_time is None:
                    start_time = minute.time
                end_time = minute.time
        
        return start_time, end_time
    
    def _generate_summary(
        self,
        minutes: List[NowcastMinute],
        start: Optional[datetime],
        end: Optional[datetime],
        total: float
    ) -> str:
        """Generate human-readable nowcast summary."""
        if not minutes:
            return "No precipitation data available."
        
        now = minutes[0].time if minutes else datetime.utcnow()
        
        # Count precipitation minutes
        rain_minutes = sum(1 for m in minutes if m.is_precipitation)
        
        if rain_minutes == 0:
            return "No precipitation expected in the next 2 hours."
        
        # Check if currently raining
        currently_raining = minutes[0].is_precipitation if minutes else False
        
        if currently_raining:
            if end:
                # Calculate minutes until rain stops
                mins_until_stop = int((end - now).total_seconds() / 60)
                if mins_until_stop <= 5:
                    return "Rain ending in the next few minutes."
                elif mins_until_stop <= 30:
                    return f"Rain expected to stop in about {mins_until_stop} minutes."
                else:
                    return f"Rain will continue for the next {mins_until_stop} minutes."
            else:
                return "Rain expected to continue for the next 2 hours."
        else:
            if start:
                # Calculate minutes until rain starts
                mins_until_start = int((start - now).total_seconds() / 60)
                intensity = next((m.intensity for m in minutes if m.time == start), "light")
                
                if mins_until_start <= 5:
                    return f"{intensity.title()} rain starting in the next few minutes."
                elif mins_until_start <= 30:
                    return f"{intensity.title()} rain expected in about {mins_until_start} minutes."
                else:
                    return f"{intensity.title()} rain expected in {mins_until_start} minutes."
            else:
                return "No precipitation expected in the next 2 hours."
    
    async def get_nowcast(
        self, 
        latitude: float, 
        longitude: float
    ) -> NowcastResponse:
        """
        Get minute-by-minute precipitation nowcast.
        
        Fetches 15-minute precipitation data from Open-Meteo and
        interpolates to provide minute-level granularity.
        
        Returns 2-hour (120 minutes) forecast similar to MinuteCast.
        """
        # Check cache first
        cache_key = f"nowcast:{latitude:.3f}:{longitude:.3f}"
        
        try:
            redis_client = await self._get_redis()
            cached = await redis_client.get(cache_key)
            if cached:
                data = json.loads(cached)
                # Parse back to response
                return NowcastResponse(**data)
        except Exception as e:
            print(f"Redis cache error: {e}")
        
        # Fetch from Open-Meteo with 15-minute resolution
        params = {
            "latitude": latitude,
            "longitude": longitude,
            "minutely_15": "precipitation,precipitation_probability",
            "forecast_minutely_15": 16,  # 16 * 15min = 4 hours of data
            "timezone": "auto"
        }
        
        response = await self.http_client.get(self.OPEN_METEO_URL, params=params)
        response.raise_for_status()
        data = response.json()
        
        # Parse 15-minute data
        minutely_data = data.get("minutely_15", {})
        times = minutely_data.get("time", [])
        precip = minutely_data.get("precipitation", [])
        probs = minutely_data.get("precipitation_probability", [])
        
        # Combine into tuples
        data_15min = []
        for i, time_str in enumerate(times):
            time_dt = datetime.fromisoformat(time_str)
            p = precip[i] if i < len(precip) else 0
            prob = probs[i] if i < len(probs) else 0
            data_15min.append((time_dt, p or 0, prob or 0))
        
        # Interpolate to minute-level
        minutes = self._interpolate_minutes(data_15min)
        
        # Find precipitation window
        start_time, end_time = self._find_precipitation_window(minutes)
        
        # Calculate total precipitation
        total_precip = sum(m.precipitation for m in minutes)
        
        # Generate summary
        summary = self._generate_summary(minutes, start_time, end_time, total_precip)
        
        result = NowcastResponse(
            location={"latitude": latitude, "longitude": longitude},
            timezone=data.get("timezone", "UTC"),
            summary=summary,
            precipitation_start=start_time,
            precipitation_end=end_time,
            total_precipitation=round(total_precip, 2),
            minutes=minutes,
            last_updated=datetime.utcnow()
        )
        
        # Cache for 5 minutes (nowcast data changes rapidly)
        try:
            redis_client = await self._get_redis()
            cache_data = result.model_dump(mode="json")
            await redis_client.setex(cache_key, 300, json.dumps(cache_data, default=str))
        except Exception as e:
            print(f"Redis cache set error: {e}")
        
        return result


_nowcast_service: Optional[NowcastService] = None


def get_nowcast_service() -> NowcastService:
    """Get the global NowcastService instance."""
    global _nowcast_service
    if _nowcast_service is None:
        _nowcast_service = NowcastService()
    return _nowcast_service


async def close_nowcast_service():
    """Close the global NowcastService instance."""
    global _nowcast_service
    if _nowcast_service:
        await _nowcast_service.close()
        _nowcast_service = None
