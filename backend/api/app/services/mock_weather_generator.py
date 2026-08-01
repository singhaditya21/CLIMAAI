"""
Mock Weather Data Generator - Ultra-realistic weather simulation.
Generates production-quality mock data for demos without external API dependencies.
"""
import math
import random
from typing import Dict, List, Optional
from datetime import datetime, timedelta, date, timezone
from enum import Enum
from pydantic import BaseModel


class WeatherScenario(str, Enum):
    """Pre-defined weather scenarios."""
    CLEAR_SUNNY = "clear_sunny"
    PARTLY_CLOUDY = "partly_cloudy"
    OVERCAST = "overcast"
    LIGHT_RAIN = "light_rain"
    HEAVY_RAIN = "heavy_rain"
    THUNDERSTORM = "thunderstorm"
    SNOW = "snow"
    FOG = "fog"
    HEATWAVE = "heatwave"
    COLD_SNAP = "cold_snap"


class CityProfile(BaseModel):
    """Weather profile for a specific city."""
    name: str
    latitude: float
    longitude: float
    timezone: str
    base_temp_summer: float  # Average summer high
    base_temp_winter: float  # Average winter low
    humidity_avg: int
    precipitation_days: int  # Per month average
    elevation: int
    climate_type: str  # tropical, desert, temperate, continental, polar


# Pre-defined city profiles for realistic weather
CITY_PROFILES = {
    # North America
    "new_york": CityProfile(
        name="New York City", latitude=40.7128, longitude=-74.0060,
        timezone="America/New_York", base_temp_summer=29, base_temp_winter=-2,
        humidity_avg=65, precipitation_days=11, elevation=10, climate_type="humid_continental"
    ),
    "miami": CityProfile(
        name="Miami", latitude=25.7617, longitude=-80.1918,
        timezone="America/New_York", base_temp_summer=33, base_temp_winter=20,
        humidity_avg=75, precipitation_days=14, elevation=2, climate_type="tropical"
    ),
    "phoenix": CityProfile(
        name="Phoenix", latitude=33.4484, longitude=-112.0740,
        timezone="America/Phoenix", base_temp_summer=42, base_temp_winter=12,
        humidity_avg=25, precipitation_days=3, elevation=340, climate_type="desert"
    ),
    "seattle": CityProfile(
        name="Seattle", latitude=47.6062, longitude=-122.3321,
        timezone="America/Los_Angeles", base_temp_summer=24, base_temp_winter=4,
        humidity_avg=75, precipitation_days=18, elevation=56, climate_type="oceanic"
    ),
    "denver": CityProfile(
        name="Denver", latitude=39.7392, longitude=-104.9903,
        timezone="America/Denver", base_temp_summer=32, base_temp_winter=-3,
        humidity_avg=45, precipitation_days=9, elevation=1609, climate_type="semi_arid"
    ),
    "san_francisco": CityProfile(
        name="San Francisco", latitude=37.7749, longitude=-122.4194,
        timezone="America/Los_Angeles", base_temp_summer=21, base_temp_winter=10,
        humidity_avg=70, precipitation_days=10, elevation=16, climate_type="mediterranean"
    ),
    "chicago": CityProfile(
        name="Chicago", latitude=41.8781, longitude=-87.6298,
        timezone="America/Chicago", base_temp_summer=28, base_temp_winter=-8,
        humidity_avg=65, precipitation_days=11, elevation=182, climate_type="humid_continental"
    ),
    # International
    "london": CityProfile(
        name="London", latitude=51.5074, longitude=-0.1278,
        timezone="Europe/London", base_temp_summer=23, base_temp_winter=4,
        humidity_avg=75, precipitation_days=15, elevation=11, climate_type="oceanic"
    ),
    "tokyo": CityProfile(
        name="Tokyo", latitude=35.6762, longitude=139.6503,
        timezone="Asia/Tokyo", base_temp_summer=31, base_temp_winter=5,
        humidity_avg=70, precipitation_days=12, elevation=40, climate_type="humid_subtropical"
    ),
    "mumbai": CityProfile(
        name="Mumbai", latitude=19.0760, longitude=72.8777,
        timezone="Asia/Kolkata", base_temp_summer=34, base_temp_winter=25,
        humidity_avg=75, precipitation_days=15, elevation=14, climate_type="tropical_monsoon"
    ),
    "sydney": CityProfile(
        name="Sydney", latitude=-33.8688, longitude=151.2093,
        timezone="Australia/Sydney", base_temp_summer=26, base_temp_winter=12,
        humidity_avg=65, precipitation_days=12, elevation=58, climate_type="humid_subtropical"
    ),
    "dubai": CityProfile(
        name="Dubai", latitude=25.2048, longitude=55.2708,
        timezone="Asia/Dubai", base_temp_summer=42, base_temp_winter=19,
        humidity_avg=55, precipitation_days=2, elevation=16, climate_type="desert"
    ),
}

# WMO Weather Codes mapping
WMO_CODES = {
    0: ("Clear sky", "☀️"),
    1: ("Mainly clear", "🌤️"),
    2: ("Partly cloudy", "⛅"),
    3: ("Overcast", "☁️"),
    45: ("Foggy", "🌫️"),
    48: ("Depositing rime fog", "🌫️"),
    51: ("Light drizzle", "🌧️"),
    53: ("Moderate drizzle", "🌧️"),
    55: ("Dense drizzle", "🌧️"),
    61: ("Slight rain", "🌧️"),
    63: ("Moderate rain", "🌧️"),
    65: ("Heavy rain", "🌧️"),
    71: ("Slight snow", "🌨️"),
    73: ("Moderate snow", "🌨️"),
    75: ("Heavy snow", "❄️"),
    77: ("Snow grains", "🌨️"),
    80: ("Slight rain showers", "🌦️"),
    81: ("Moderate rain showers", "🌦️"),
    82: ("Violent rain showers", "⛈️"),
    85: ("Slight snow showers", "🌨️"),
    86: ("Heavy snow showers", "❄️"),
    95: ("Thunderstorm", "⛈️"),
    96: ("Thunderstorm with slight hail", "⛈️"),
    99: ("Thunderstorm with heavy hail", "⛈️"),
}


class MockWeatherGenerator:
    """
    Generates ultra-realistic mock weather data.
    
    Features:
    - City-specific weather patterns
    - Seasonal variations
    - Time-of-day temperature curves
    - Realistic forecasts with gradual changes
    - Configurable weather scenarios
    """
    
    def __init__(self, seed: Optional[int] = None):
        """Initialize with optional seed for reproducibility."""
        if seed:
            random.seed(seed)
        self._scenario_cache: Dict[str, Dict] = {}
    
    def get_city_profile(self, latitude: float, longitude: float) -> CityProfile:
        """Find closest city profile or generate a generic one."""
        # Find closest known city
        min_distance = float('inf')
        closest_city = None
        
        for city_key, profile in CITY_PROFILES.items():
            distance = math.sqrt(
                (profile.latitude - latitude) ** 2 + 
                (profile.longitude - longitude) ** 2
            )
            if distance < min_distance:
                min_distance = distance
                closest_city = profile
        
        # If within 5 degrees, use the known profile
        if min_distance < 5 and closest_city:
            return closest_city
        
        # Generate a generic profile based on latitude
        return self._generate_generic_profile(latitude, longitude)
    
    def _generate_generic_profile(self, lat: float, lon: float) -> CityProfile:
        """Generate a generic profile based on coordinates."""
        abs_lat = abs(lat)
        
        # Determine climate based on latitude
        if abs_lat < 23.5:  # Tropical
            return CityProfile(
                name=f"Location ({lat:.2f}, {lon:.2f})",
                latitude=lat, longitude=lon,
                timezone="UTC", base_temp_summer=32, base_temp_winter=25,
                humidity_avg=75, precipitation_days=12, elevation=50,
                climate_type="tropical"
            )
        elif abs_lat < 35:  # Subtropical
            return CityProfile(
                name=f"Location ({lat:.2f}, {lon:.2f})",
                latitude=lat, longitude=lon,
                timezone="UTC", base_temp_summer=30, base_temp_winter=12,
                humidity_avg=65, precipitation_days=8, elevation=100,
                climate_type="subtropical"
            )
        elif abs_lat < 55:  # Temperate
            return CityProfile(
                name=f"Location ({lat:.2f}, {lon:.2f})",
                latitude=lat, longitude=lon,
                timezone="UTC", base_temp_summer=25, base_temp_winter=2,
                humidity_avg=60, precipitation_days=10, elevation=200,
                climate_type="temperate"
            )
        else:  # Polar/Subarctic
            return CityProfile(
                name=f"Location ({lat:.2f}, {lon:.2f})",
                latitude=lat, longitude=lon,
                timezone="UTC", base_temp_summer=15, base_temp_winter=-15,
                humidity_avg=70, precipitation_days=12, elevation=50,
                climate_type="subarctic"
            )
    
    def _get_seasonal_factor(self, day_of_year: int, latitude: float) -> float:
        """
        Calculate seasonal temperature adjustment.
        Returns a factor from -1 (winter) to 1 (summer).
        
        Accounts for hemisphere differences.
        """
        # Northern hemisphere: summer = July (day 182)
        # Southern hemisphere: summer = January (day 1)
        peak_day = 182 if latitude >= 0 else 1
        
        # Sinusoidal variation over the year
        days_from_peak = min(abs(day_of_year - peak_day), 365 - abs(day_of_year - peak_day))
        return math.cos(days_from_peak * math.pi / 182.5)
    
    def _get_diurnal_factor(self, hour: int) -> float:
        """
        Calculate time-of-day temperature factor.
        Returns a factor from -1 (coldest at 5am) to 1 (warmest at 3pm).
        """
        # Maximum temperature around 3 PM (hour 15)
        # Minimum temperature around 5 AM (hour 5)
        return math.sin((hour - 5) * math.pi / 12) if 5 <= hour <= 17 else -1 + (hour - 17) / 12 if hour > 17 else -1 + hour / 5
    
    def generate_current_weather(
        self,
        latitude: float,
        longitude: float,
        scenario: Optional[WeatherScenario] = None,
        reference_time: Optional[datetime] = None
    ) -> Dict:
        """
        Generate realistic current weather data.
        
        Args:
            latitude: Location latitude
            longitude: Location longitude
            scenario: Optional forced weather scenario
            reference_time: Reference time (defaults to now)
            
        Returns:
            Dict with current weather data
        """
        now = reference_time or datetime.now(timezone.utc)
        profile = self.get_city_profile(latitude, longitude)
        
        # Calculate base temperature
        day_of_year = now.timetuple().tm_yday
        hour = now.hour
        
        seasonal = self._get_seasonal_factor(day_of_year, latitude)
        diurnal = self._get_diurnal_factor(hour)
        
        # Interpolate between summer and winter temps
        base_temp = (profile.base_temp_summer + profile.base_temp_winter) / 2
        temp_range = (profile.base_temp_summer - profile.base_temp_winter) / 2
        
        temperature = base_temp + (seasonal * temp_range) + (diurnal * 5)
        temperature += random.uniform(-2, 2)  # Daily variation
        
        # Determine weather code
        if scenario:
            weather_code = self._scenario_to_weather_code(scenario)
        else:
            weather_code = self._generate_weather_code(profile, temperature, day_of_year)
        
        # Adjust temperature for weather conditions
        if weather_code in [61, 63, 65, 80, 81, 82]:  # Rain
            temperature -= random.uniform(2, 5)
        elif weather_code in [71, 73, 75]:  # Snow
            temperature = min(temperature, 0)
        
        # Generate other parameters
        humidity = self._generate_humidity(profile, weather_code)
        wind_speed = self._generate_wind_speed(weather_code, profile)
        wind_direction = random.randint(0, 360)
        pressure = self._generate_pressure(weather_code)
        visibility = self._generate_visibility(weather_code)
        uv_index = self._generate_uv_index(hour, weather_code, abs(latitude))
        cloud_cover = self._generate_cloud_cover(weather_code)
        
        # Calculate feels like (simplified heat index / wind chill)
        feels_like = self._calculate_feels_like(temperature, humidity, wind_speed)
        
        # Calculate dew point
        dew_point = self._calculate_dew_point(temperature, humidity)
        
        is_day = 6 <= hour <= 20
        
        return {
            "temperature": round(temperature, 1),
            "feels_like": round(feels_like, 1),
            "humidity": humidity,
            "dew_point": round(dew_point, 1),
            "wind_speed": round(wind_speed, 1),
            "wind_direction": wind_direction,
            "precipitation": self._generate_precipitation(weather_code),
            "weather_code": weather_code,
            "weather_description": WMO_CODES.get(weather_code, ("Unknown", "❓"))[0],
            "weather_icon": WMO_CODES.get(weather_code, ("Unknown", "❓"))[1],
            "cloud_cover": cloud_cover,
            "pressure": pressure,
            "visibility": visibility,
            "uv_index": round(uv_index, 1),
            "is_day": is_day,
            "timestamp": now.isoformat(),
            "location": {
                "name": profile.name,
                "latitude": latitude,
                "longitude": longitude,
                "elevation": profile.elevation,
                "timezone": profile.timezone
            }
        }
    
    def generate_hourly_forecast(
        self,
        latitude: float,
        longitude: float,
        hours: int = 48,
        reference_time: Optional[datetime] = None
    ) -> List[Dict]:
        """Generate realistic hourly forecast."""
        now = reference_time or datetime.now(timezone.utc)
        profile = self.get_city_profile(latitude, longitude)
        
        hourly = []
        
        # Start with current conditions and evolve
        current = self.generate_current_weather(latitude, longitude, reference_time=now)
        prev_weather_code = current["weather_code"]
        
        for i in range(hours):
            forecast_time = now + timedelta(hours=i)
            day_of_year = forecast_time.timetuple().tm_yday
            hour = forecast_time.hour
            
            # Calculate temperature with natural progression
            seasonal = self._get_seasonal_factor(day_of_year, latitude)
            diurnal = self._get_diurnal_factor(hour)
            
            base_temp = (profile.base_temp_summer + profile.base_temp_winter) / 2
            temp_range = (profile.base_temp_summer - profile.base_temp_winter) / 2
            
            temperature = base_temp + (seasonal * temp_range) + (diurnal * 6)
            temperature += random.uniform(-1, 1)
            
            # Weather transitions (gradual changes)
            weather_code = self._evolve_weather_code(prev_weather_code, profile, hour)
            prev_weather_code = weather_code
            
            # Adjust for weather
            if weather_code in [61, 63, 65, 80, 81, 82]:
                temperature -= random.uniform(2, 4)
            
            humidity = self._generate_humidity(profile, weather_code)
            feels_like = self._calculate_feels_like(temperature, humidity, 10)
            
            is_day = 6 <= hour <= 20
            
            hourly.append({
                "time": forecast_time.isoformat(),
                "temperature": round(temperature, 1),
                "feels_like": round(feels_like, 1),
                "precipitation_probability": self._generate_precip_probability(weather_code),
                "precipitation": self._generate_precipitation(weather_code),
                "weather_code": weather_code,
                "weather_description": WMO_CODES.get(weather_code, ("Unknown", "❓"))[0],
                "wind_speed": round(self._generate_wind_speed(weather_code, profile), 1),
                "wind_direction": random.randint(0, 360),
                "humidity": humidity,
                "cloud_cover": self._generate_cloud_cover(weather_code),
                "uv_index": round(self._generate_uv_index(hour, weather_code, abs(latitude)), 1),
                "is_day": is_day
            })
        
        return hourly
    
    def generate_daily_forecast(
        self,
        latitude: float,
        longitude: float,
        days: int = 16,
        reference_time: Optional[datetime] = None
    ) -> List[Dict]:
        """Generate realistic daily forecast with moon phases."""
        today = (reference_time or datetime.now(timezone.utc)).date()
        profile = self.get_city_profile(latitude, longitude)
        
        daily = []
        prev_weather_code = random.choice([0, 1, 2, 3])
        
        for i in range(days):
            forecast_date = today + timedelta(days=i)
            day_of_year = forecast_date.timetuple().tm_yday
            
            # Calculate temperatures
            seasonal = self._get_seasonal_factor(day_of_year, latitude)
            
            base_temp = (profile.base_temp_summer + profile.base_temp_winter) / 2
            temp_range = (profile.base_temp_summer - profile.base_temp_winter) / 2
            
            avg_temp = base_temp + (seasonal * temp_range)
            temp_max = avg_temp + random.uniform(4, 7)
            temp_min = avg_temp - random.uniform(4, 7)
            
            # Evolve weather
            weather_code = self._evolve_weather_code(prev_weather_code, profile, 12)
            prev_weather_code = weather_code
            
            # Adjust for weather
            if weather_code in [61, 63, 65]:
                temp_max -= 3
                temp_min -= 1
            
            # Calculate sun times (simplified)
            sunrise_hour = 6 + int((1 - seasonal) * 1.5)
            sunset_hour = 18 + int(seasonal * 1.5)
            
            sunrise = datetime.combine(forecast_date, datetime.min.time().replace(hour=sunrise_hour, minute=random.randint(0, 59)))
            sunset = datetime.combine(forecast_date, datetime.min.time().replace(hour=sunset_hour, minute=random.randint(0, 59)))
            
            # Calculate moon phase
            moon_phase = self._calculate_moon_phase(forecast_date)
            
            daily.append({
                "date": forecast_date.isoformat(),
                "temperature_max": round(temp_max, 1),
                "temperature_min": round(temp_min, 1),
                "sunrise": sunrise.isoformat(),
                "sunset": sunset.isoformat(),
                "precipitation_sum": round(self._generate_precipitation(weather_code) * random.uniform(0, 24), 1),
                "precipitation_probability": self._generate_precip_probability(weather_code),
                "weather_code": weather_code,
                "weather_description": WMO_CODES.get(weather_code, ("Unknown", "❓"))[0],
                "wind_speed_max": round(self._generate_wind_speed(weather_code, profile) * 1.5, 1),
                "wind_direction": random.randint(0, 360),
                "uv_index_max": round(self._generate_uv_index(12, weather_code, abs(latitude)) * 1.2, 1),
                "moon_phase": round(moon_phase, 4),
                "moon_phase_name": self._get_moon_phase_name(moon_phase)
            })
        
        return daily
    
    def generate_air_quality(
        self,
        latitude: float,
        longitude: float
    ) -> Dict:
        """Generate realistic air quality data."""
        profile = self.get_city_profile(latitude, longitude)
        
        # Urban areas have worse air quality
        base_aqi = 30 if profile.climate_type in ["oceanic", "mediterranean"] else 50
        
        # Add city-specific factors
        if "mumbai" in profile.name.lower() or "delhi" in profile.name.lower():
            base_aqi = 120
        elif "beijing" in profile.name.lower():
            base_aqi = 100
        
        aqi = base_aqi + random.randint(-15, 25)
        aqi = max(0, min(300, aqi))
        
        # Calculate pollutants based on AQI
        pm25 = aqi * 0.3 + random.uniform(-5, 5)
        pm10 = pm25 * 1.5 + random.uniform(-5, 10)
        
        # Determine category
        if aqi <= 50:
            category = "Good"
            health_rec = "Air quality is satisfactory. Enjoy outdoor activities."
        elif aqi <= 100:
            category = "Moderate"
            health_rec = "Unusually sensitive people should consider limiting prolonged outdoor exertion."
        elif aqi <= 150:
            category = "Unhealthy for Sensitive Groups"
            health_rec = "Children, older adults, and people with respiratory conditions should limit outdoor exertion."
        elif aqi <= 200:
            category = "Unhealthy"
            health_rec = "Everyone should reduce prolonged outdoor exertion."
        else:
            category = "Very Unhealthy"
            health_rec = "Everyone should avoid outdoor exertion."
        
        return {
            "aqi": aqi,
            "pm2_5": round(max(0, pm25), 1),
            "pm10": round(max(0, pm10), 1),
            "carbon_monoxide": round(random.uniform(0.1, 0.5), 2),
            "nitrogen_dioxide": round(random.uniform(5, 40), 1),
            "ozone": round(random.uniform(20, 80), 1),
            "sulphur_dioxide": round(random.uniform(1, 10), 1),
            "category": category,
            "health_recommendation": health_rec
        }
    
    # Helper methods
    def _scenario_to_weather_code(self, scenario: WeatherScenario) -> int:
        """Convert scenario to WMO weather code."""
        mapping = {
            WeatherScenario.CLEAR_SUNNY: 0,
            WeatherScenario.PARTLY_CLOUDY: 2,
            WeatherScenario.OVERCAST: 3,
            WeatherScenario.LIGHT_RAIN: 61,
            WeatherScenario.HEAVY_RAIN: 65,
            WeatherScenario.THUNDERSTORM: 95,
            WeatherScenario.SNOW: 73,
            WeatherScenario.FOG: 45,
            WeatherScenario.HEATWAVE: 0,
            WeatherScenario.COLD_SNAP: 0,
        }
        return mapping.get(scenario, 0)
    
    def _generate_weather_code(self, profile: CityProfile, temp: float, day_of_year: int) -> int:
        """Generate appropriate weather code based on climate."""
        # Tropical: more rain
        if profile.climate_type == "tropical":
            codes = [0, 1, 2, 3, 61, 63, 80, 81, 95]
            weights = [15, 15, 20, 15, 10, 10, 5, 5, 5]
        # Desert: mostly clear
        elif profile.climate_type == "desert":
            codes = [0, 1, 2, 3]
            weights = [60, 25, 10, 5]
        # Oceanic: often cloudy/rainy
        elif profile.climate_type == "oceanic":
            codes = [0, 1, 2, 3, 45, 51, 61, 63, 80]
            weights = [10, 15, 20, 25, 5, 5, 10, 5, 5]
        else:  # Default temperate
            codes = [0, 1, 2, 3, 61, 63, 71, 73]
            weights = [20, 20, 20, 15, 10, 8, 4, 3] if temp > 0 else [15, 15, 15, 15, 5, 5, 15, 15]
        
        # Normalize weights
        total = sum(weights)
        weights = [w / total for w in weights]
        
        return random.choices(codes, weights=weights)[0]
    
    def _evolve_weather_code(self, prev_code: int, profile: CityProfile, hour: int) -> int:
        """Evolve weather gradually (no sudden changes)."""
        # 80% chance to keep similar weather
        if random.random() < 0.8:
            # Small variations
            if prev_code in [0, 1, 2, 3]:  # Clear to cloudy
                return random.choice([0, 1, 2, 3])
            elif prev_code in [61, 63, 65]:  # Rain variations
                return random.choice([61, 63, 65, 2, 3])
            else:
                return prev_code
        
        # 20% chance for weather change
        return self._generate_weather_code(profile, 20, 180)
    
    def _generate_humidity(self, profile: CityProfile, weather_code: int) -> int:
        """Generate realistic humidity."""
        base = profile.humidity_avg
        
        if weather_code in [61, 63, 65, 80, 81, 82, 95]:  # Rain
            base = min(95, base + 20)
        elif weather_code == 0:  # Clear
            base = max(20, base - 15)
        
        return max(10, min(100, base + random.randint(-10, 10)))
    
    def _generate_wind_speed(self, weather_code: int, profile: CityProfile) -> float:
        """Generate realistic wind speed in km/h."""
        base = 10
        
        if weather_code in [95, 96, 99]:  # Thunderstorm
            base = 35
        elif weather_code in [65, 82]:  # Heavy rain
            base = 25
        elif "chicago" in profile.name.lower():  # Windy City
            base = 20
        
        return max(0, base + random.uniform(-5, 10))
    
    def _generate_pressure(self, weather_code: int) -> float:
        """Generate barometric pressure in hPa."""
        base = 1013.25  # Standard pressure
        
        if weather_code in [61, 63, 65, 95]:  # Rain/storm
            base -= random.uniform(5, 15)
        elif weather_code == 0:  # Clear
            base += random.uniform(2, 8)
        
        return round(base + random.uniform(-3, 3), 1)
    
    def _generate_visibility(self, weather_code: int) -> float:
        """Generate visibility in meters."""
        if weather_code in [45, 48]:  # Fog
            return random.uniform(100, 1000)
        elif weather_code in [65, 75]:  # Heavy precip
            return random.uniform(2000, 5000)
        elif weather_code in [61, 63, 73]:  # Moderate precip
            return random.uniform(5000, 10000)
        else:
            return random.uniform(15000, 50000)
    
    def _generate_uv_index(self, hour: int, weather_code: int, latitude: float) -> float:
        """Generate UV index."""
        if not (8 <= hour <= 18):
            return 0
        
        # Peak at solar noon
        time_factor = 1 - abs(hour - 13) / 5
        
        # Latitude factor (higher at equator)
        lat_factor = max(0.2, 1 - latitude / 60)
        
        base_uv = 10 * time_factor * lat_factor
        
        # Cloud reduction
        if weather_code in [3, 45]:  # Overcast/fog
            base_uv *= 0.3
        elif weather_code in [2]:  # Partly cloudy
            base_uv *= 0.7
        elif weather_code in [61, 63, 65]:  # Rain
            base_uv *= 0.2
        
        return max(0, base_uv + random.uniform(-1, 1))
    
    def _generate_cloud_cover(self, weather_code: int) -> int:
        """Generate cloud cover percentage."""
        if weather_code == 0:
            return random.randint(0, 10)
        elif weather_code == 1:
            return random.randint(10, 30)
        elif weather_code == 2:
            return random.randint(30, 60)
        elif weather_code == 3:
            return random.randint(80, 100)
        elif weather_code in [45, 48]:
            return 100
        elif weather_code in [61, 63, 65, 71, 73, 75, 80, 81, 82, 95]:
            return random.randint(70, 100)
        else:
            return random.randint(30, 70)
    
    def _generate_precipitation(self, weather_code: int) -> float:
        """Generate precipitation amount in mm."""
        if weather_code in [51, 53]:
            return random.uniform(0.1, 0.5)
        elif weather_code in [55, 61]:
            return random.uniform(0.5, 2)
        elif weather_code in [63, 80]:
            return random.uniform(2, 5)
        elif weather_code in [65, 81, 82]:
            return random.uniform(5, 15)
        elif weather_code in [71]:
            return random.uniform(0.5, 2)
        elif weather_code in [73, 85]:
            return random.uniform(2, 5)
        elif weather_code in [75, 86]:
            return random.uniform(5, 10)
        elif weather_code in [95, 96, 99]:
            return random.uniform(3, 20)
        else:
            return 0
    
    def _generate_precip_probability(self, weather_code: int) -> int:
        """Generate precipitation probability."""
        if weather_code == 0:
            return random.randint(0, 5)
        elif weather_code in [1, 2]:
            return random.randint(5, 20)
        elif weather_code == 3:
            return random.randint(20, 40)
        elif weather_code in [45, 48]:
            return random.randint(10, 30)
        elif weather_code in [51, 53, 55, 61]:
            return random.randint(60, 80)
        elif weather_code in [63, 65, 80, 81, 82]:
            return random.randint(80, 100)
        elif weather_code in [71, 73, 75]:
            return random.randint(70, 95)
        elif weather_code in [95, 96, 99]:
            return random.randint(85, 100)
        else:
            return random.randint(10, 30)
    
    def _calculate_feels_like(self, temp: float, humidity: int, wind: float) -> float:
        """Calculate feels-like temperature."""
        if temp >= 27 and humidity >= 40:
            # Heat index
            hi = temp + 0.5 * (humidity - 40) / 10
            return hi
        elif temp <= 10 and wind > 5:
            # Wind chill
            wc = 13.12 + 0.6215 * temp - 11.37 * (wind ** 0.16) + 0.3965 * temp * (wind ** 0.16)
            return wc
        else:
            return temp
    
    def _calculate_dew_point(self, temp: float, humidity: int) -> float:
        """Calculate dew point using Magnus formula."""
        a = 17.27
        b = 237.7
        alpha = ((a * temp) / (b + temp)) + math.log(humidity / 100)
        return (b * alpha) / (a - alpha)
    
    def _calculate_moon_phase(self, target_date: date) -> float:
        """Calculate moon phase (0 = new, 0.5 = full)."""
        known_new_moon = datetime(2000, 1, 6, 18, 14)
        synodic_month = 29.530588853
        
        target = datetime.combine(target_date, datetime.min.time())
        days_since = (target - known_new_moon).total_seconds() / 86400
        cycles = days_since / synodic_month
        
        return cycles - math.floor(cycles)
    
    def _get_moon_phase_name(self, phase: float) -> str:
        """Get moon phase name."""
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
