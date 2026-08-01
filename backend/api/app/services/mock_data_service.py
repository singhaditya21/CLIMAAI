"""
Unified Mock Data Service - Central service for all mock weather data.
Use this for production demos without external API dependencies.
"""
from typing import Dict, List, Optional, Any
from datetime import datetime, timezone
from enum import Enum

from .mock_weather_generator import (
    MockWeatherGenerator,
    WeatherScenario,
    CITY_PROFILES
)
from .mock_services import (
    MockNowcastGenerator,
    MockRadarGenerator,
    MockAlertsGenerator,
    MockPollenGenerator,
    PrecipitationScenario
)
from .activity_service import ActivityForecastService
from .health_index_service import HealthIndexService


class DemoScenario(str, Enum):
    """
    Pre-configured demo scenarios for different presentations.
    """
    PERFECT_DAY = "perfect_day"          # Sunny, clear, ideal for activities
    RAINY_DAY = "rainy_day"              # Rainy with nowcast showing rain ending
    SEVERE_WEATHER = "severe_weather"    # Thunderstorm with alerts
    HIGH_POLLEN = "high_pollen"          # Peak allergy season
    WINTER_STORM = "winter_storm"        # Snow, winter warnings
    HEATWAVE = "heatwave"                # Extreme heat
    VARIED = "varied"                    # Mix of conditions


class MockDataService:
    """
    Unified service for generating all mock weather data.
    
    Usage:
        service = MockDataService()
        
        # Get complete weather package for a location
        data = service.get_complete_weather(
            latitude=40.7128,
            longitude=-74.0060,
            scenario=DemoScenario.PERFECT_DAY
        )
        
        # Or get individual components
        current = service.get_current_weather(lat, lon)
        nowcast = service.get_nowcast(lat, lon)
        alerts = service.get_alerts(lat, lon)
    """
    
    def __init__(self, seed: Optional[int] = None):
        """
        Initialize all mock generators.
        
        Args:
            seed: Optional random seed for reproducibility
        """
        self.weather_gen = MockWeatherGenerator(seed)
        self.nowcast_gen = MockNowcastGenerator()
        self.radar_gen = MockRadarGenerator()
        self.alerts_gen = MockAlertsGenerator()
        self.pollen_gen = MockPollenGenerator()
        self.activity_service = ActivityForecastService()
        self.health_service = HealthIndexService()
    
    def get_complete_weather(
        self,
        latitude: float,
        longitude: float,
        scenario: Optional[DemoScenario] = None,
        reference_time: Optional[datetime] = None
    ) -> Dict[str, Any]:
        """
        Get complete weather data package for a location.
        
        Includes:
        - Current conditions
        - Hourly forecast (48h)
        - Daily forecast (16 days)
        - Nowcast (2h precipitation)
        - Air quality
        - Pollen forecast
        - Active alerts
        - Activity recommendations
        - Health indices
        
        Args:
            latitude: Location latitude
            longitude: Location longitude
            scenario: Demo scenario for themed data
            reference_time: Reference time (defaults to now)
            
        Returns:
            Complete weather data dictionary
        """
        now = reference_time or datetime.now(timezone.utc)
        
        # Get weather scenario based on demo scenario
        weather_scenario = self._demo_to_weather_scenario(scenario)
        precip_scenario = self._demo_to_precip_scenario(scenario)
        
        # Generate all data
        current = self.weather_gen.generate_current_weather(
            latitude, longitude, 
            scenario=weather_scenario,
            reference_time=now
        )
        
        hourly = self.weather_gen.generate_hourly_forecast(
            latitude, longitude,
            hours=48,
            reference_time=now
        )
        
        daily = self.weather_gen.generate_daily_forecast(
            latitude, longitude,
            days=16,
            reference_time=now
        )
        
        nowcast = self.nowcast_gen.generate_nowcast(
            latitude, longitude,
            scenario=precip_scenario,
            reference_time=now
        )
        
        air_quality = self.weather_gen.generate_air_quality(latitude, longitude)
        
        pollen = self.pollen_gen.generate_pollen_forecast(
            latitude, longitude,
            days=5,
            reference_date=now.date()
        )
        
        alerts = self._get_scenario_alerts(scenario, latitude, longitude, now)
        
        # Calculate activity scores
        hourly_for_activities = [
            {
                "time": h["time"],
                "temperature": h["temperature"],
                "humidity": h["humidity"],
                "wind_speed": h["wind_speed"],
                "precipitation": h["precipitation"],
                "precipitation_probability": h["precipitation_probability"],
                "uv_index": h["uv_index"],
                "cloud_cover": h["cloud_cover"],
                "visibility": 10000,
                "is_day": h.get("is_day", True)
            }
            for h in hourly
        ]
        
        activities = self.activity_service.get_all_activities_summary(
            hourly_for_activities,
            air_quality.get("aqi", 50)
        )
        
        # Calculate health indices
        flu_risk = self.health_service.calculate_flu_risk(
            temperature=current["temperature"],
            humidity=current["humidity"],
            current_date=now.date(),
            latitude=latitude
        )
        
        migraine_risk = self.health_service.calculate_migraine_risk(
            current_pressure=current["pressure"],
            pressure_history=[current["pressure"]] * 24,
            humidity=current["humidity"],
            temperature=current["temperature"]
        )
        
        return {
            "current": current,
            "hourly": hourly,
            "daily": daily,
            "nowcast": nowcast,
            "air_quality": air_quality,
            "pollen": pollen,
            "alerts": alerts,
            "activities": activities,
            "health": {
                "flu_risk": {
                    "level": flu_risk.risk_level.value,
                    "score": flu_risk.risk_score,
                    "factors": flu_risk.factors,
                    "recommendations": flu_risk.recommendations
                },
                "migraine_risk": {
                    "level": migraine_risk.risk_level.value,
                    "score": migraine_risk.risk_score,
                    "pressure_trend": migraine_risk.pressure_trend,
                    "triggers": migraine_risk.triggers
                }
            },
            "location": current["location"],
            "timezone": current["location"].get("timezone", "UTC"),
            "generated_at": now.isoformat(),
            "demo_scenario": scenario.value if scenario else None,
            "cached": False
        }
    
    def get_current_weather(
        self,
        latitude: float,
        longitude: float,
        scenario: Optional[WeatherScenario] = None
    ) -> Dict:
        """Get current weather conditions."""
        return self.weather_gen.generate_current_weather(
            latitude, longitude, scenario=scenario
        )
    
    def get_hourly_forecast(
        self,
        latitude: float,
        longitude: float,
        hours: int = 48
    ) -> List[Dict]:
        """Get hourly forecast."""
        return self.weather_gen.generate_hourly_forecast(
            latitude, longitude, hours=hours
        )
    
    def get_daily_forecast(
        self,
        latitude: float,
        longitude: float,
        days: int = 16
    ) -> List[Dict]:
        """Get daily forecast."""
        return self.weather_gen.generate_daily_forecast(
            latitude, longitude, days=days
        )
    
    def get_nowcast(
        self,
        latitude: float,
        longitude: float,
        scenario: Optional[PrecipitationScenario] = None
    ) -> Dict:
        """Get minute-by-minute precipitation forecast."""
        return self.nowcast_gen.generate_nowcast(
            latitude, longitude, scenario=scenario
        )
    
    def get_radar_frames(self) -> Dict:
        """Get radar animation frames."""
        return self.radar_gen.generate_radar_frames()
    
    def get_alerts(
        self,
        latitude: float,
        longitude: float,
        alert_type: Optional[str] = None
    ) -> List[Dict]:
        """Get weather alerts for a location."""
        return self.alerts_gen.generate_alerts(
            latitude, longitude, scenario=alert_type
        )
    
    def get_pollen(
        self,
        latitude: float,
        longitude: float,
        days: int = 5
    ) -> Dict:
        """Get pollen forecast."""
        return self.pollen_gen.generate_pollen_forecast(
            latitude, longitude, days=days
        )
    
    def get_activities(
        self,
        latitude: float,
        longitude: float
    ) -> List[Dict]:
        """Get activity forecast summaries."""
        hourly = self.get_hourly_forecast(latitude, longitude, 24)
        air_quality = self.weather_gen.generate_air_quality(latitude, longitude)
        
        hourly_data = [
            {
                "time": h["time"],
                "temperature": h["temperature"],
                "humidity": h["humidity"],
                "wind_speed": h["wind_speed"],
                "precipitation": h["precipitation"],
                "precipitation_probability": h["precipitation_probability"],
                "uv_index": h["uv_index"],
                "cloud_cover": h["cloud_cover"],
                "visibility": 10000,
                "is_day": h.get("is_day", True)
            }
            for h in hourly
        ]
        
        return self.activity_service.get_all_activities_summary(
            hourly_data, air_quality.get("aqi", 50)
        )
    
    def get_available_scenarios(self) -> Dict[str, str]:
        """Get list of available demo scenarios with descriptions."""
        return {
            DemoScenario.PERFECT_DAY.value: "Sunny, clear skies, perfect for all activities",
            DemoScenario.RAINY_DAY.value: "Light rain with clearing forecast",
            DemoScenario.SEVERE_WEATHER.value: "Thunderstorm approaching with alerts",
            DemoScenario.HIGH_POLLEN.value: "Peak allergy season conditions",
            DemoScenario.WINTER_STORM.value: "Snow and winter weather warning",
            DemoScenario.HEATWAVE.value: "Extreme heat advisory",
            DemoScenario.VARIED.value: "Mix of different conditions"
        }
    
    def get_sample_cities(self) -> List[Dict]:
        """Get list of pre-configured city profiles."""
        return [
            {
                "key": key,
                "name": profile.name,
                "latitude": profile.latitude,
                "longitude": profile.longitude,
                "climate": profile.climate_type
            }
            for key, profile in CITY_PROFILES.items()
        ]
    
    # Private helper methods
    def _demo_to_weather_scenario(self, demo: Optional[DemoScenario]) -> Optional[WeatherScenario]:
        """Convert demo scenario to weather scenario."""
        if not demo:
            return None
        
        mapping = {
            DemoScenario.PERFECT_DAY: WeatherScenario.CLEAR_SUNNY,
            DemoScenario.RAINY_DAY: WeatherScenario.LIGHT_RAIN,
            DemoScenario.SEVERE_WEATHER: WeatherScenario.THUNDERSTORM,
            DemoScenario.HIGH_POLLEN: WeatherScenario.PARTLY_CLOUDY,
            DemoScenario.WINTER_STORM: WeatherScenario.SNOW,
            DemoScenario.HEATWAVE: WeatherScenario.HEATWAVE,
        }
        return mapping.get(demo)
    
    def _demo_to_precip_scenario(self, demo: Optional[DemoScenario]) -> Optional[PrecipitationScenario]:
        """Convert demo scenario to precipitation scenario."""
        if not demo:
            return None
        
        mapping = {
            DemoScenario.PERFECT_DAY: PrecipitationScenario.CLEAR,
            DemoScenario.RAINY_DAY: PrecipitationScenario.RAIN_ENDING_SOON,
            DemoScenario.SEVERE_WEATHER: PrecipitationScenario.THUNDERSTORM_APPROACHING,
            DemoScenario.HIGH_POLLEN: PrecipitationScenario.CLEAR,
            DemoScenario.WINTER_STORM: PrecipitationScenario.SNOW_STARTING,
            DemoScenario.HEATWAVE: PrecipitationScenario.CLEAR,
        }
        return mapping.get(demo)
    
    def _get_scenario_alerts(
        self,
        scenario: Optional[DemoScenario],
        lat: float,
        lon: float,
        now: datetime
    ) -> List[Dict]:
        """Get alerts appropriate for the scenario."""
        if scenario == DemoScenario.SEVERE_WEATHER:
            return self.alerts_gen.generate_alerts(lat, lon, "thunderstorm", now)
        elif scenario == DemoScenario.WINTER_STORM:
            return self.alerts_gen.generate_alerts(lat, lon, "winter", now)
        elif scenario == DemoScenario.HEATWAVE:
            return self.alerts_gen.generate_alerts(lat, lon, "heat", now)
        elif scenario in [DemoScenario.PERFECT_DAY, DemoScenario.HIGH_POLLEN]:
            return []  # No alerts for nice weather
        else:
            # Random - 30% chance of alerts
            return self.alerts_gen.generate_alerts(lat, lon, reference_time=now)


# Convenience functions for quick access
def get_mock_weather(latitude: float, longitude: float, scenario: str = None) -> Dict:
    """Quick function to get mock weather data."""
    service = MockDataService()
    demo_scenario = DemoScenario(scenario) if scenario else None
    return service.get_complete_weather(latitude, longitude, scenario=demo_scenario)


def get_demo_cities() -> List[Dict]:
    """Get list of demo cities."""
    return MockDataService().get_sample_cities()


def get_demo_scenarios() -> Dict[str, str]:
    """Get available demo scenarios."""
    return MockDataService().get_available_scenarios()
