"""
Weather service integrating with Open-Meteo API.
Handles data fetching, caching, and transformation.
"""
import httpx
import json
import math
from typing import List, Optional, Tuple
from datetime import datetime, timedelta, date, timezone
import redis.asyncio as redis
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from ..models import WeatherHistory
from ..config import get_settings
from ..schemas.weather import (
    CurrentWeather,
    HourlyWeather,
    DailyWeather,
    AirQuality,
    WeatherResponse,
)

settings = get_settings()

_weather_service: Optional['WeatherService'] = None


class WeatherService:
    """Service for fetching and managing weather data."""
    
    # WMO Weather interpretation codes
    WEATHER_CODES = {
        0: "Clear sky",
        1: "Mainly clear",
        2: "Partly cloudy",
        3: "Overcast",
        45: "Foggy",
        48: "Depositing rime fog",
        51: "Light drizzle",
        53: "Moderate drizzle",
        55: "Dense drizzle",
        61: "Slight rain",
        63: "Moderate rain",
        65: "Heavy rain",
        71: "Slight snow",
        73: "Moderate snow",
        75: "Heavy snow",
        77: "Snow grains",
        80: "Slight rain showers",
        81: "Moderate rain showers",
        82: "Violent rain showers",
        85: "Slight snow showers",
        86: "Heavy snow showers",
        95: "Thunderstorm",
        96: "Thunderstorm with slight hail",
        99: "Thunderstorm with heavy hail",
    }
    
    def __init__(self, http_client: Optional[httpx.AsyncClient] = None, redis_client: Optional[redis.Redis] = None):
        self.redis_client: Optional[redis.Redis] = redis_client
        self.http_client = http_client or httpx.AsyncClient(timeout=30.0)
    
    async def _get_redis(self) -> redis.Redis:
        """Get or create Redis client."""
        if self.redis_client is None:
            self.redis_client = await redis.from_url(settings.REDIS_URL)
        return self.redis_client
    
    def _get_cache_key(self, latitude: float, longitude: float, data_type: str) -> str:
        """Generate cache key for weather data."""
        return f"weather:{data_type}:{latitude:.2f}:{longitude:.2f}"
    
    async def _get_cached_data(self, cache_key: str) -> Optional[dict]:
        """Get cached weather data."""
        try:
            redis_client = await self._get_redis()
            cached = await redis_client.get(cache_key)
            if cached:
                return json.loads(cached)
        except Exception as e:
            print(f"Redis get error: {e}")
        return None
    
    async def _set_cached_data(self, cache_key: str, data: dict, ttl: int = None):
        """Set cached weather data."""
        try:
            redis_client = await self._get_redis()
            ttl = ttl or settings.WEATHER_CACHE_TTL
            await redis_client.setex(cache_key, ttl, json.dumps(data))
        except Exception as e:
            print(f"Redis set error: {e}")
    
    def _get_weather_description(self, code: int) -> str:
        """Get human-readable weather description from WMO code."""
        return self.WEATHER_CODES.get(code, "Unknown")
    
    def _round_coord(self, coord: float) -> float:
        """Round coordinates to ~1km grid (2 decimal places) for history."""
        return round(coord, 2)

    def _calculate_aqi(self, pm2_5: float, pm10: float, no2: float, o3: float) -> Tuple[int, str, str]:
        """
        Calculate AQI using simplified US EPA standard.
        Returns: (aqi_value, category, health_recommendation)
        """
        # Simplified AQI calculation based on PM2.5 (most common pollutant)
        if pm2_5 <= 12:
            aqi = int((50 / 12) * pm2_5)
            category = "Good"
            health = "Air quality is satisfactory, and air pollution poses little or no risk."
        elif pm2_5 <= 35.4:
            aqi = int(50 + ((100 - 50) / (35.4 - 12.1)) * (pm2_5 - 12.1))
            category = "Moderate"
            health = "Unusually sensitive people should consider reducing prolonged outdoor exertion."
        elif pm2_5 <= 55.4:
            aqi = int(100 + ((150 - 100) / (55.4 - 35.5)) * (pm2_5 - 35.5))
            category = "Unhealthy for Sensitive Groups"
            health = "Children, older adults, and people with lung disease should reduce prolonged outdoor exertion."
        elif pm2_5 <= 150.4:
            aqi = int(150 + ((200 - 150) / (150.4 - 55.5)) * (pm2_5 - 55.5))
            category = "Unhealthy"
            health = "Everyone should reduce prolonged outdoor exertion."
        elif pm2_5 <= 250.4:
            aqi = int(200 + ((300 - 200) / (250.4 - 150.5)) * (pm2_5 - 150.5))
            category = "Very Unhealthy"
            health = "Everyone should avoid prolonged outdoor exertion."
        else:
            aqi = int(300 + ((500 - 300) / (500.4 - 250.5)) * (pm2_5 - 250.5))
            category = "Hazardous"
            health = "Everyone should avoid all outdoor exertion."
        
        return min(aqi, 500), category, health
    
    async def get_weather_history(
        self,
        latitude: float,
        longitude: float,
        hours: int,
        db: AsyncSession
    ) -> List[WeatherHistory]:
        """Get historical weather data."""
        # Query using rounded coordinates to handle GPS drift
        lat_rounded = self._round_coord(latitude)
        lon_rounded = self._round_coord(longitude)

        stmt = select(WeatherHistory).where(
            WeatherHistory.latitude == lat_rounded,
            WeatherHistory.longitude == lon_rounded,
            WeatherHistory.created_at >= datetime.now(timezone.utc) - timedelta(hours=hours)
        ).order_by(WeatherHistory.created_at.asc())

        result = await db.execute(stmt)
        return result.scalars().all()

    async def get_current_weather(
        self,
        latitude: float,
        longitude: float,
        use_cache: bool = True,
        db: Optional[AsyncSession] = None
    ) -> WeatherResponse:
        """
        Get current weather, hourly, and daily forecasts.
        """
        cache_key = self._get_cache_key(latitude, longitude, "complete")
        
        # Check cache
        if use_cache:
            cached_data = await self._get_cached_data(cache_key)
            if cached_data:
                cached_data["cached"] = True
                return WeatherResponse(**cached_data)
        
        # Fetch from Open-Meteo
        url = f"{settings.OPEN_METEO_BASE_URL}/forecast"
        params = {
            "latitude": latitude,
            "longitude": longitude,
            "current": [
                "temperature_2m",
                "apparent_temperature",
                "relative_humidity_2m",
                "dew_point_2m",
                "precipitation",
                "weather_code",
                "cloud_cover",
                "pressure_msl",
                "surface_pressure",
                "wind_speed_10m",
                "wind_direction_10m",
                "is_day",
            ],
            "hourly": [
                "temperature_2m",
                "apparent_temperature",
                "precipitation_probability",
                "precipitation",
                "weather_code",
                "wind_speed_10m",
                "wind_direction_10m",
                "relative_humidity_2m",
                "cloud_cover",
                "uv_index",
            ],
            "daily": [
                "temperature_2m_max",
                "temperature_2m_min",
                "sunrise",
                "sunset",
                "precipitation_sum",
                "snowfall_sum",
                "precipitation_probability_max",
                "weather_code",
                "wind_speed_10m_max",
                "wind_direction_10m_dominant",
                "uv_index_max",
            ],
            "timezone": "auto",
            "forecast_days": 16,  # Maximum for free tier
        }
        
        # Free host, or the licensed customer- host when a key is configured
        url, params = settings.open_meteo_request(url, params)
        try:
            response = await self.http_client.get(url, params=params)
            response.raise_for_status()
            data = response.json()
        except httpx.HTTPError as e:
            raise Exception(f"Failed to fetch weather data: {str(e)}")
        
        # Get air quality data
        air_quality = await self._get_air_quality(latitude, longitude)
        
        # Parse current weather
        current_data = data.get("current", {})
        current = CurrentWeather(
            temperature=current_data.get("temperature_2m", 0),
            feels_like=current_data.get("apparent_temperature", 0),
            feels_like_shade=current_data.get("apparent_temperature", 0),  # OpenMeteo is shade-based
            humidity=current_data.get("relative_humidity_2m", 0),
            dew_point=current_data.get("dew_point_2m"),
            wind_speed=current_data.get("wind_speed_10m", 0),
            wind_direction=current_data.get("wind_direction_10m", 0),
            precipitation=current_data.get("precipitation", 0),
            weather_code=current_data.get("weather_code", 0),
            weather_description=self._get_weather_description(current_data.get("weather_code", 0)),
            cloud_cover=current_data.get("cloud_cover", 0),
            pressure=current_data.get("pressure_msl", 0),
            visibility=10000,  # Open-Meteo doesn't provide this, default to 10km
            uv_index=0,  # Will be from hourly
            is_day=bool(current_data.get("is_day", 1)),
            timestamp=datetime.fromisoformat(current_data.get("time", datetime.now(timezone.utc).isoformat())),
        )
        
        # Parse hourly forecast (next 24 hours)
        hourly_data = data.get("hourly", {})
        hourly = []
        for i in range(min(24, len(hourly_data.get("time", [])))):
            hourly.append(HourlyWeather(
                time=datetime.fromisoformat(hourly_data["time"][i]),
                temperature=hourly_data["temperature_2m"][i],
                feels_like=hourly_data["apparent_temperature"][i],
                feels_like_shade=hourly_data["apparent_temperature"][i],
                precipitation_probability=hourly_data["precipitation_probability"][i] or 0,
                precipitation=hourly_data["precipitation"][i],
                weather_code=hourly_data["weather_code"][i],
                weather_description=self._get_weather_description(hourly_data["weather_code"][i]),
                wind_speed=hourly_data["wind_speed_10m"][i],
                wind_direction=hourly_data["wind_direction_10m"][i],
                humidity=hourly_data["relative_humidity_2m"][i],
                cloud_cover=hourly_data["cloud_cover"][i],
                uv_index=hourly_data["uv_index"][i],
            ))
        
        # Update current UV index from hourly
        if hourly:
            current.uv_index = hourly[0].uv_index
        
        # Parse daily forecast
        daily_data = data.get("daily", {})
        daily = []

        def day_value(field: str, index: int):
            """Read daily_data[field][index], tolerating absent or short arrays."""
            values = daily_data.get(field) or []
            return values[index] if index < len(values) else None

        # Open-Meteo pads its daily arrays out to forecast_days but leaves the
        # trailing day null until that day's data is published, so the last entry
        # is routinely all-null. Skip any day missing a field DailyWeather requires
        # rather than coercing to 0, which would surface as a real 0°C forecast.
        required_daily = (
            "temperature_2m_max", "temperature_2m_min", "sunrise", "sunset",
            "precipitation_sum", "weather_code", "wind_speed_10m_max",
            "wind_direction_10m_dominant", "uv_index_max",
        )

        for i in range(len(daily_data.get("time", []))):
            if any(day_value(field, i) is None for field in required_daily):
                continue

            # Calculate moon phase for this date
            forecast_date = date.fromisoformat(daily_data["time"][i])
            moon_phase = self._calculate_moon_phase(forecast_date)
            moon_phase_name = self._get_moon_phase_name(moon_phase)
            
            daily.append(DailyWeather(
                date=daily_data["time"][i],
                temperature_max=daily_data["temperature_2m_max"][i],
                temperature_min=daily_data["temperature_2m_min"][i],
                sunrise=daily_data["sunrise"][i],
                sunset=daily_data["sunset"][i],
                precipitation_sum=daily_data["precipitation_sum"][i],
                snow_accumulation=day_value("snowfall_sum", i) or 0,
                precipitation_probability=day_value("precipitation_probability_max", i) or 0,
                weather_code=daily_data["weather_code"][i],
                weather_description=self._get_weather_description(daily_data["weather_code"][i]),
                wind_speed_max=daily_data["wind_speed_10m_max"][i],
                wind_direction=daily_data["wind_direction_10m_dominant"][i],
                uv_index_max=daily_data["uv_index_max"][i],
                moon_phase=moon_phase,
                moon_phase_name=moon_phase_name,
            ))
        
        # Build response
        weather_response = WeatherResponse(
            current=current,
            hourly=hourly,
            daily=daily,
            air_quality=air_quality,
            location={
                "latitude": latitude,
                "longitude": longitude,
                "elevation": data.get("elevation", 0),
            },
            timezone=data.get("timezone", "UTC"),
            cached=False,
        )
        
        # Save history if DB session is provided
        if db:
            try:
                history_entry = WeatherHistory(
                    latitude=self._round_coord(latitude),
                    longitude=self._round_coord(longitude),
                    temperature=current.temperature,
                    pressure_msl=current.pressure,
                    humidity=current.humidity,
                )
                db.add(history_entry)
                # No commit here, handled by dependency
            except Exception as e:
                print(f"Failed to save weather history: {e}")

        # Cache the response
        await self._set_cached_data(cache_key, weather_response.model_dump(mode="json"))
        
        return weather_response
    
    async def _get_air_quality(self, latitude: float, longitude: float) -> Optional[AirQuality]:
        """Get air quality data from Open-Meteo."""
        cache_key = self._get_cache_key(latitude, longitude, "air_quality")
        
        # Check cache
        cached_data = await self._get_cached_data(cache_key)
        if cached_data:
            return AirQuality(**cached_data)
        
        url = f"{settings.OPEN_METEO_AIR_QUALITY_URL}/air-quality"
        params = {
            "latitude": latitude,
            "longitude": longitude,
            "current": ["pm10", "pm2_5", "carbon_monoxide", "nitrogen_dioxide", "sulphur_dioxide", "ozone"],
        }
        # Free host, or the licensed customer- host when a key is configured
        url, params = settings.open_meteo_request(url, params)

        try:
            response = await self.http_client.get(url, params=params)
            response.raise_for_status()
            data = response.json()
            
            current_aq = data.get("current", {})
            pm2_5 = current_aq.get("pm2_5", 0) or 0
            pm10 = current_aq.get("pm10", 0) or 0
            no2 = current_aq.get("nitrogen_dioxide", 0) or 0
            o3 = current_aq.get("ozone", 0) or 0
            
            aqi, category, health_rec = self._calculate_aqi(pm2_5, pm10, no2, o3)
            
            air_quality = AirQuality(
                aqi=aqi,
                pm2_5=pm2_5,
                pm10=pm10,
                carbon_monoxide=current_aq.get("carbon_monoxide", 0) or 0,
                nitrogen_dioxide=no2,
                ozone=o3,
                sulphur_dioxide=current_aq.get("sulphur_dioxide", 0) or 0,
                category=category,
                health_recommendation=health_rec,
            )
            
            # Cache for 1 hour
            await self._set_cached_data(cache_key, air_quality.model_dump(mode="json"), ttl=3600)
            
            return air_quality
            
        except Exception as e:
            print(f"Failed to fetch air quality: {e}")
            return None
    
    async def close(self):
        """Close HTTP and Redis connections."""
        await self.http_client.aclose()
        if self.redis_client:
            await self.redis_client.close()
    
    def _calculate_moon_phase(self, target_date: date) -> float:
        """
        Calculate moon phase for a given date.
        
        Uses a simplified algorithm based on known new moon dates.
        Returns a value from 0 to 1:
        - 0 or 1 = New Moon
        - 0.25 = First Quarter
        - 0.5 = Full Moon
        - 0.75 = Last Quarter
        """
        # Known new moon: January 6, 2000 at 18:14 UTC
        known_new_moon = datetime(2000, 1, 6, 18, 14, tzinfo=timezone.utc)
        
        # Synodic month (average lunar cycle)
        synodic_month = 29.530588853
        
        # Calculate days since known new moon. Both sides must be UTC-aware —
        # the epoch above carries tzinfo, so the target has to as well.
        target_datetime = datetime.combine(
            target_date, datetime.min.time(), tzinfo=timezone.utc
        )
        days_since = (target_datetime - known_new_moon).total_seconds() / 86400
        
        # Calculate current position in lunar cycle (0 to 1)
        cycles = days_since / synodic_month
        phase = cycles - math.floor(cycles)
        
        return round(phase, 4)
    
    def _get_moon_phase_name(self, phase: float) -> str:
        """
        Get human-readable moon phase name.
        
        Args:
            phase: Moon phase value from 0 to 1
            
        Returns:
            Moon phase name string
        """
        if phase < 0.0625 or phase >= 0.9375:
            return "New Moon 🌑"
        elif phase < 0.1875:
            return "Waxing Crescent 🌒"
        elif phase < 0.3125:
            return "First Quarter 🌓"
        elif phase < 0.4375:
            return "Waxing Gibbous 🌔"
        elif phase < 0.5625:
            return "Full Moon 🌕"
        elif phase < 0.6875:
            return "Waning Gibbous 🌖"
        elif phase < 0.8125:
            return "Last Quarter 🌗"
        else:
            return "Waning Crescent 🌘"


def get_weather_service() -> WeatherService:
    """Get the global WeatherService instance."""
    global _weather_service
    if _weather_service is None:
        _weather_service = WeatherService()
    return _weather_service


async def close_weather_service():
    """Close the global WeatherService instance."""
    global _weather_service
    if _weather_service:
        await _weather_service.close()
        _weather_service = None
