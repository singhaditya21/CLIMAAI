"""
Pydantic schemas for weather data.
"""
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime


class CurrentWeather(BaseModel):
    """Current weather conditions."""
    temperature: float = Field(..., description="Temperature in degrees")
    feels_like: float = Field(..., description="Apparent temperature")
    humidity: int = Field(..., ge=0, le=100, description="Relative humidity %")
    dew_point: Optional[float] = Field(None, description="Dew point temperature")
    wind_speed: float = Field(..., description="Wind speed")
    wind_direction: int = Field(..., ge=0, le=360, description="Wind direction in degrees")
    precipitation: float = Field(..., description="Precipitation amount")
    weather_code: int = Field(..., description="WMO weather code")
    weather_description: str = Field(..., description="Human-readable weather description")
    cloud_cover: int = Field(..., ge=0, le=100, description="Cloud cover %")
    pressure: float = Field(..., description="Sea level pressure")
    visibility: float = Field(..., description="Visibility in meters")
    uv_index: float = Field(..., description="UV index")
    is_day: bool = Field(..., description="Day or night")
    timestamp: datetime


class HourlyWeather(BaseModel):
    """Hourly weather forecast."""
    time: datetime
    temperature: float
    feels_like: float
    precipitation_probability: int = Field(..., ge=0, le=100)
    precipitation: float
    weather_code: int
    weather_description: str
    wind_speed: float
    wind_direction: int
    humidity: int
    cloud_cover: int
    uv_index: float


class DailyWeather(BaseModel):
    """Daily weather forecast."""
    date: str
    temperature_max: float
    temperature_min: float
    sunrise: str
    sunset: str
    precipitation_sum: float
    precipitation_probability: int = Field(..., ge=0, le=100)
    weather_code: int
    weather_description: str
    wind_speed_max: float
    wind_direction: int
    uv_index_max: float
    # Moon phase data
    moon_phase: Optional[float] = Field(None, description="Moon phase (0=new, 0.5=full, 1=new)")
    moon_phase_name: Optional[str] = Field(None, description="Moon phase name")
    moonrise: Optional[str] = Field(None, description="Moonrise time")
    moonset: Optional[str] = Field(None, description="Moonset time")


class AirQuality(BaseModel):
    """Air quality data."""
    aqi: int = Field(..., description="Air Quality Index (US EPA standard)")
    pm2_5: float = Field(..., description="Particulate Matter 2.5")
    pm10: float = Field(..., description="Particulate Matter 10")
    carbon_monoxide: float = Field(..., description="Carbon Monoxide")
    nitrogen_dioxide: float = Field(..., description="Nitrogen Dioxide")
    ozone: float = Field(..., description="Ozone")
    sulphur_dioxide: float = Field(..., description="Sulphur Dioxide")
    category: str = Field(..., description="Good, Moderate, Unhealthy, etc.")
    health_recommendation: str


class PollenData(BaseModel):
    """Pollen and allergy data."""
    grass_pollen: float = Field(0, description="Grass pollen grains/m³")
    tree_pollen: float = Field(0, description="Birch pollen grains/m³")
    weed_pollen: float = Field(0, description="Ragweed pollen grains/m³")
    
    grass_level: str = Field("Low", description="Grass pollen level")
    tree_level: str = Field("Low", description="Tree pollen level")
    weed_level: str = Field("Low", description="Weed pollen level")
    
    overall_risk: str = Field("Low", description="Overall allergy risk")
    recommendation: str = Field("", description="Health recommendation for allergy sufferers")


class WeatherResponse(BaseModel):
    """Complete weather response."""
    current: CurrentWeather
    hourly: List[HourlyWeather]
    daily: List[DailyWeather]
    air_quality: Optional[AirQuality] = None
    pollen: Optional[PollenData] = None
    location: dict = Field(..., description="Location metadata")
    timezone: str
    cached: bool = Field(default=False, description="Whether data was served from cache")

