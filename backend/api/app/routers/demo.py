"""
Demo API Router - Endpoints for production demos with mock data.
Provides complete mock weather data without external API dependencies.
"""
from fastapi import APIRouter, Query, HTTPException
from typing import Optional

from ..services.mock_data_service import (
    MockDataService,
    DemoScenario,
    get_demo_cities,
    get_demo_scenarios
)
from ..services.mock_weather_generator import WeatherScenario
from ..services.mock_services import PrecipitationScenario

router = APIRouter(prefix="/demo", tags=["demo"])


# Initialize mock service
mock_service = MockDataService(seed=42)  # Seed for reproducible demos


@router.get("/")
async def get_demo_info():
    """
    Get information about available demo endpoints.
    
    Perfect for onboarding and testing the API without external dependencies.
    """
    return {
        "message": "Welcome to ClimaAI Demo API",
        "description": "Production-ready mock data for weather app demos",
        "endpoints": {
            "GET /demo/weather": "Complete weather data with all features",
            "GET /demo/cities": "List of demo cities",
            "GET /demo/scenarios": "Available demo scenarios",
            "GET /demo/current": "Current conditions only",
            "GET /demo/forecast/hourly": "48-hour forecast",
            "GET /demo/forecast/daily": "16-day forecast",
            "GET /demo/nowcast": "Minute-by-minute precipitation",
            "GET /demo/alerts": "Severe weather alerts",
            "GET /demo/pollen": "Pollen forecast",
            "GET /demo/activities": "Activity recommendations",
            "GET /demo/radar": "Radar animation frames"
        },
        "sample_request": "/demo/weather?city=new_york&scenario=perfect_day"
    }


@router.get("/cities")
async def list_demo_cities():
    """
    Get list of pre-configured demo cities.
    
    Use these city keys with the `city` parameter for realistic location-based weather.
    """
    cities = get_demo_cities()
    return {
        "cities": cities,
        "usage": "Pass city key as 'city' parameter, e.g., ?city=new_york"
    }


@router.get("/scenarios")
async def list_demo_scenarios():
    """
    Get list of available demo scenarios.
    
    Scenarios provide pre-configured weather conditions for demos:
    - perfect_day: Sunny, clear, ideal conditions
    - rainy_day: Light rain with clearing forecast
    - severe_weather: Thunderstorm with alerts
    - high_pollen: Peak allergy season
    - winter_storm: Snow and winter warnings
    - heatwave: Extreme heat
    """
    return {
        "scenarios": get_demo_scenarios(),
        "usage": "Pass scenario as parameter, e.g., ?scenario=severe_weather"
    }


@router.get("/weather")
async def get_complete_mock_weather(
    latitude: Optional[float] = Query(None, ge=-90, le=90, description="Latitude"),
    longitude: Optional[float] = Query(None, ge=-180, le=180, description="Longitude"),
    city: Optional[str] = Query(None, description="City key (e.g., 'new_york', 'tokyo')"),
    scenario: Optional[str] = Query(None, description="Demo scenario (e.g., 'perfect_day', 'rainy_day')")
):
    """
    Get complete mock weather data package.
    
    Includes:
    - Current conditions (temp, humidity, wind, etc.)
    - 48-hour hourly forecast
    - 16-day daily forecast with moon phases
    - 2-hour minute-by-minute nowcast
    - Air quality index
    - 5-day pollen forecast
    - Severe weather alerts
    - Activity recommendations (8 activities)
    - Health indices (flu/migraine risk)
    
    Parameters:
    - Either (latitude, longitude) OR city key
    - Optional scenario for themed demo
    """
    # Resolve coordinates
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    
    # Parse scenario
    demo_scenario = None
    if scenario:
        try:
            demo_scenario = DemoScenario(scenario)
        except ValueError:
            raise HTTPException(
                status_code=400,
                detail=f"Invalid scenario: {scenario}. Valid options: {list(DemoScenario)}"
            )
    
    return mock_service.get_complete_weather(lat, lon, scenario=demo_scenario)


@router.get("/current")
async def get_mock_current_weather(
    latitude: Optional[float] = Query(None, ge=-90, le=90),
    longitude: Optional[float] = Query(None, ge=-180, le=180),
    city: Optional[str] = Query(None),
    weather: Optional[str] = Query(None, description="Force weather: clear_sunny, light_rain, snow, etc.")
):
    """
    Get current weather conditions.
    
    Optionally force a specific weather condition for demos.
    """
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    
    weather_scenario = None
    if weather:
        try:
            weather_scenario = WeatherScenario(weather)
        except ValueError:
            valid = [w.value for w in WeatherScenario]
            raise HTTPException(
                status_code=400,
                detail=f"Invalid weather: {weather}. Valid: {valid}"
            )
    
    return mock_service.get_current_weather(lat, lon, scenario=weather_scenario)


@router.get("/forecast/hourly")
async def get_mock_hourly_forecast(
    latitude: Optional[float] = Query(None, ge=-90, le=90),
    longitude: Optional[float] = Query(None, ge=-180, le=180),
    city: Optional[str] = Query(None),
    hours: int = Query(48, ge=1, le=168, description="Number of hours")
):
    """
    Get hourly weather forecast.
    
    Returns up to 168 hours (7 days) of hourly data.
    """
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    return {
        "hourly": mock_service.get_hourly_forecast(lat, lon, hours=hours),
        "count": hours
    }


@router.get("/forecast/daily")
async def get_mock_daily_forecast(
    latitude: Optional[float] = Query(None, ge=-90, le=90),
    longitude: Optional[float] = Query(None, ge=-180, le=180),
    city: Optional[str] = Query(None),
    days: int = Query(16, ge=1, le=16, description="Number of days")
):
    """
    Get daily weather forecast with moon phases.
    
    Returns up to 16 days of daily forecasts.
    """
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    return {
        "daily": mock_service.get_daily_forecast(lat, lon, days=days),
        "count": days
    }


@router.get("/nowcast")
async def get_mock_nowcast(
    latitude: Optional[float] = Query(None, ge=-90, le=90),
    longitude: Optional[float] = Query(None, ge=-180, le=180),
    city: Optional[str] = Query(None),
    scenario: Optional[str] = Query(None, description="Precipitation scenario")
):
    """
    Get minute-by-minute precipitation forecast.
    
    Scenarios:
    - clear: No precipitation
    - rain_starting_soon: Rain begins in ~30 min
    - rain_ending_soon: Current rain stopping
    - steady_rain: Continuous rain
    - passing_shower: Brief shower
    - thunderstorm_approaching: Heavy storm coming
    - snow_starting: Snow beginning
    """
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    
    precip_scenario = None
    if scenario:
        try:
            precip_scenario = PrecipitationScenario(scenario)
        except ValueError:
            valid = [s.value for s in PrecipitationScenario]
            raise HTTPException(
                status_code=400,
                detail=f"Invalid scenario: {scenario}. Valid: {valid}"
            )
    
    return mock_service.get_nowcast(lat, lon, scenario=precip_scenario)


@router.get("/alerts")
async def get_mock_alerts(
    latitude: Optional[float] = Query(None, ge=-90, le=90),
    longitude: Optional[float] = Query(None, ge=-180, le=180),
    city: Optional[str] = Query(None),
    alert_type: Optional[str] = Query(None, description="Force alert type: tornado, thunderstorm, flood, heat, winter, hurricane")
):
    """
    Get severe weather alerts for a location.
    
    Force a specific alert type for demos, or get random alerts
    (60% chance of no alerts for realism).
    """
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    return {
        "alerts": mock_service.get_alerts(lat, lon, alert_type=alert_type),
        "count": len(mock_service.get_alerts(lat, lon, alert_type=alert_type))
    }


@router.get("/pollen")
async def get_mock_pollen(
    latitude: Optional[float] = Query(None, ge=-90, le=90),
    longitude: Optional[float] = Query(None, ge=-180, le=180),
    city: Optional[str] = Query(None),
    days: int = Query(5, ge=1, le=5)
):
    """
    Get pollen forecast with seasonal variation.
    
    Returns tree, grass, and weed pollen levels
    adjusted for hemisphere and time of year.
    """
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    return mock_service.get_pollen(lat, lon, days=days)


@router.get("/activities")
async def get_mock_activities(
    latitude: Optional[float] = Query(None, ge=-90, le=90),
    longitude: Optional[float] = Query(None, ge=-180, le=180),
    city: Optional[str] = Query(None)
):
    """
    Get activity recommendations based on weather.
    
    Returns scores for 8 activities:
    running, cycling, golf, hiking, beach, skiing, tennis, photography
    """
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    return {
        "activities": mock_service.get_activities(lat, lon),
        "count": 8
    }


@router.get("/radar")
async def get_mock_radar():
    """
    Get radar animation frame metadata.
    
    Returns timestamps and URLs for radar tile animation.
    Compatible with standard map tile libraries.
    """
    return mock_service.get_radar_frames()


@router.get("/health")
async def get_mock_health(
    latitude: Optional[float] = Query(None, ge=-90, le=90),
    longitude: Optional[float] = Query(None, ge=-180, le=180),
    city: Optional[str] = Query(None)
):
    """
    Get health-related weather indices.
    
    Returns:
    - Flu risk assessment
    - Migraine trigger forecast
    - Air quality
    - Pollen summary
    """
    lat, lon = _resolve_coordinates(latitude, longitude, city)
    
    full = mock_service.get_complete_weather(lat, lon)
    
    return {
        "flu_risk": full["health"]["flu_risk"],
        "migraine_risk": full["health"]["migraine_risk"],
        "air_quality": full["air_quality"],
        "pollen_summary": {
            "overall_level": full["pollen"]["forecast"][0]["overall_level"] if full["pollen"]["forecast"] else "unknown",
            "recommendations": full["pollen"]["health_recommendations"]
        }
    }


# Showcase endpoints for specific demo flows

@router.get("/showcase/perfect-day")
async def showcase_perfect_day(city: str = Query("san_francisco")):
    """Showcase: Perfect weather day scenario."""
    lat, lon = _resolve_coordinates(None, None, city)
    return mock_service.get_complete_weather(lat, lon, scenario=DemoScenario.PERFECT_DAY)


@router.get("/showcase/severe-weather")
async def showcase_severe_weather(city: str = Query("chicago")):
    """Showcase: Severe weather with alerts."""
    lat, lon = _resolve_coordinates(None, None, city)
    return mock_service.get_complete_weather(lat, lon, scenario=DemoScenario.SEVERE_WEATHER)


@router.get("/showcase/allergy-season")
async def showcase_allergy_season(city: str = Query("new_york")):
    """Showcase: High pollen scenario."""
    lat, lon = _resolve_coordinates(None, None, city)
    return mock_service.get_complete_weather(lat, lon, scenario=DemoScenario.HIGH_POLLEN)


# Helper function
def _resolve_coordinates(
    latitude: Optional[float],
    longitude: Optional[float],
    city: Optional[str]
) -> tuple[float, float]:
    """
    Resolve coordinates from parameters.
    
    Priority:
    1. Explicit lat/lon
    2. City key lookup
    3. Default to NYC
    """
    if latitude is not None and longitude is not None:
        return latitude, longitude
    
    if city:
        cities = {c["key"]: (c["latitude"], c["longitude"]) for c in get_demo_cities()}
        if city.lower() in cities:
            return cities[city.lower()]
        else:
            raise HTTPException(
                status_code=400,
                detail=f"Unknown city: {city}. Valid options: {list(cities.keys())}"
            )
    
    # Default to NYC
    return 40.7128, -74.0060
