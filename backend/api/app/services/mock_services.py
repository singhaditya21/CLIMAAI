"""
Mock Nowcast Service - Realistic minute-by-minute precipitation simulation.
"""
import random
from typing import Dict, List, Optional
from datetime import datetime, timedelta
from enum import Enum


class PrecipitationScenario(str, Enum):
    """Pre-defined precipitation scenarios for demos."""
    CLEAR = "clear"
    RAIN_STARTING_SOON = "rain_starting_soon"
    RAIN_ENDING_SOON = "rain_ending_soon"
    STEADY_RAIN = "steady_rain"
    PASSING_SHOWER = "passing_shower"
    THUNDERSTORM_APPROACHING = "thunderstorm_approaching"
    SNOW_STARTING = "snow_starting"


class MockNowcastGenerator:
    """
    Generates realistic minute-by-minute precipitation forecasts.
    
    Scenarios:
    - Clear weather (no precipitation)
    - Rain starting in X minutes
    - Rain ending in X minutes
    - Passing shower (starts and stops)
    - Thunderstorm approaching (heavy rain spike)
    """
    
    SCENARIO_DESCRIPTIONS = {
        PrecipitationScenario.CLEAR: "Clear skies for the next 2 hours",
        PrecipitationScenario.RAIN_STARTING_SOON: "Rain expected to start in {min} minutes",
        PrecipitationScenario.RAIN_ENDING_SOON: "Rain expected to stop in {min} minutes",
        PrecipitationScenario.STEADY_RAIN: "Steady rain continuing for the next 2 hours",
        PrecipitationScenario.PASSING_SHOWER: "Brief shower expected, clearing in {min} minutes",
        PrecipitationScenario.THUNDERSTORM_APPROACHING: "Thunderstorm approaching in {min} minutes",
        PrecipitationScenario.SNOW_STARTING: "Snow expected to begin in {min} minutes",
    }
    
    def generate_nowcast(
        self,
        latitude: float,
        longitude: float,
        scenario: Optional[PrecipitationScenario] = None,
        reference_time: Optional[datetime] = None,
        minutes: int = 120
    ) -> Dict:
        """
        Generate minute-by-minute precipitation forecast.
        
        Args:
            latitude: Location latitude
            longitude: Location longitude
            scenario: Force a specific scenario (random if None)
            reference_time: Start time (defaults to now)
            minutes: Forecast duration (default 120)
            
        Returns:
            Nowcast data with minutely precipitation
        """
        now = reference_time or datetime.utcnow()
        
        # Select scenario
        if scenario is None:
            # Weight towards clear weather for realism
            scenarios = list(PrecipitationScenario)
            weights = [40, 15, 15, 10, 10, 5, 5]
            scenario = random.choices(scenarios, weights=weights)[0]
        
        # Generate precipitation data based on scenario
        minutely = self._generate_scenario_data(scenario, now, minutes)
        
        # Calculate summary stats
        total_precip = sum(m["precipitation"] for m in minutely)
        precipitation_start = None
        precipitation_end = None
        max_intensity = max(m["precipitation"] for m in minutely)
        
        for i, m in enumerate(minutely):
            if m["precipitation"] > 0.1 and precipitation_start is None:
                precipitation_start = m["time"]
            if m["precipitation"] > 0.1:
                precipitation_end = m["time"]
        
        # Generate summary
        if scenario == PrecipitationScenario.CLEAR:
            summary = "No precipitation expected for the next 2 hours."
        elif scenario == PrecipitationScenario.RAIN_STARTING_SOON:
            start_min = next((i for i, m in enumerate(minutely) if m["precipitation"] > 0.1), 30)
            summary = f"🌧️ Rain starting in {start_min} minutes. Grab an umbrella!"
        elif scenario == PrecipitationScenario.RAIN_ENDING_SOON:
            end_min = next((i for i, m in enumerate(minutely) if m["precipitation"] < 0.1), 20)
            summary = f"☀️ Rain stopping in {end_min} minutes."
        elif scenario == PrecipitationScenario.STEADY_RAIN:
            summary = "🌧️ Steady rain continuing. Stay dry!"
        elif scenario == PrecipitationScenario.PASSING_SHOWER:
            summary = "🌦️ Brief shower passing through."
        elif scenario == PrecipitationScenario.THUNDERSTORM_APPROACHING:
            start_min = next((i for i, m in enumerate(minutely) if m["precipitation"] > 2), 25)
            summary = f"⛈️ Thunderstorm approaching in {start_min} minutes. Take shelter!"
        elif scenario == PrecipitationScenario.SNOW_STARTING:
            start_min = next((i for i, m in enumerate(minutely) if m["precipitation"] > 0.1), 20)
            summary = f"🌨️ Snow beginning in {start_min} minutes."
        else:
            summary = "Weather conditions updating..."
        
        return {
            "summary": summary,
            "scenario": scenario.value,
            "precipitation_start": precipitation_start,
            "precipitation_end": precipitation_end,
            "total_precipitation": round(total_precip, 2),
            "max_intensity": round(max_intensity, 2),
            "minutes": minutely,
            "location": {
                "latitude": latitude,
                "longitude": longitude
            },
            "generated_at": now.isoformat(),
            "forecast_end": (now + timedelta(minutes=minutes)).isoformat()
        }
    
    def _generate_scenario_data(
        self,
        scenario: PrecipitationScenario,
        start_time: datetime,
        minutes: int
    ) -> List[Dict]:
        """Generate minutely data for a specific scenario."""
        data = []
        
        for i in range(minutes):
            time = start_time + timedelta(minutes=i)
            precip = self._get_precipitation_for_scenario(scenario, i, minutes)
            
            data.append({
                "time": time.isoformat(),
                "minute": i,
                "precipitation": round(precip, 2),
                "precipitation_probability": min(100, int(precip * 100) if precip > 0.1 else random.randint(0, 15)),
                "is_precipitation": precip > 0.1
            })
        
        return data
    
    def _get_precipitation_for_scenario(
        self,
        scenario: PrecipitationScenario,
        minute: int,
        total_minutes: int
    ) -> float:
        """Calculate precipitation for a given minute in a scenario."""
        
        if scenario == PrecipitationScenario.CLEAR:
            return 0.0
        
        elif scenario == PrecipitationScenario.RAIN_STARTING_SOON:
            # Rain starts around minute 25-35
            start_minute = 30 + random.randint(-5, 5)
            if minute < start_minute:
                return 0.0
            else:
                # Ramp up precipitation
                intensity = min(1.5, (minute - start_minute) * 0.05)
                return intensity + random.uniform(-0.2, 0.3)
        
        elif scenario == PrecipitationScenario.RAIN_ENDING_SOON:
            # Rain currently falling, stops around minute 20-30
            end_minute = 25 + random.randint(-5, 5)
            if minute > end_minute:
                return 0.0
            else:
                # Ramp down
                intensity = max(0, 1.0 - minute * 0.04)
                return intensity + random.uniform(-0.1, 0.2)
        
        elif scenario == PrecipitationScenario.STEADY_RAIN:
            # Consistent moderate rain with variations
            base = 0.8 + random.uniform(-0.3, 0.4)
            return max(0.2, base)
        
        elif scenario == PrecipitationScenario.PASSING_SHOWER:
            # Bell curve - peaks around minute 30-40
            peak_minute = 35
            width = 20
            intensity = 2.0 * pow(2.718, -((minute - peak_minute) ** 2) / (2 * width ** 2))
            return max(0, intensity + random.uniform(-0.2, 0.3))
        
        elif scenario == PrecipitationScenario.THUNDERSTORM_APPROACHING:
            # Heavy rain spike around minute 25-45
            start = 25
            peak = 40
            end = 80
            
            if minute < start:
                return 0.0
            elif minute < peak:
                intensity = 4.0 * (minute - start) / (peak - start)
                return intensity + random.uniform(-0.5, 1.0)
            elif minute < end:
                intensity = 4.0 * (1 - (minute - peak) / (end - peak))
                return max(0.5, intensity + random.uniform(-0.5, 0.5))
            else:
                return random.uniform(0, 0.3)
        
        elif scenario == PrecipitationScenario.SNOW_STARTING:
            # Snow starts around minute 20
            start_minute = 20 + random.randint(-5, 5)
            if minute < start_minute:
                return 0.0
            else:
                # Light to moderate snow
                intensity = min(0.8, (minute - start_minute) * 0.02)
                return intensity + random.uniform(-0.1, 0.15)
        
        return 0.0


class MockRadarGenerator:
    """
    Generates mock radar frame data for animation.
    
    Simulates RainViewer-style radar tiles with timestamps.
    """
    
    def generate_radar_frames(
        self,
        reference_time: Optional[datetime] = None,
        past_frames: int = 12,
        nowcast_frames: int = 6
    ) -> Dict:
        """
        Generate radar frame metadata.
        
        Args:
            reference_time: Current time (defaults to now)
            past_frames: Number of past frames (5-min intervals)
            nowcast_frames: Number of forecast frames
            
        Returns:
            Radar frame data with tile URLs
        """
        now = reference_time or datetime.utcnow()
        
        # Generate past frames (every 5 minutes)
        past = []
        for i in range(past_frames, 0, -1):
            frame_time = now - timedelta(minutes=i * 5)
            timestamp = int(frame_time.timestamp())
            past.append({
                "time": timestamp,
                "path": f"/v2/radar/{timestamp}/256",
                "datetime": frame_time.isoformat(),
                "type": "past"
            })
        
        # Generate nowcast frames
        nowcast = []
        for i in range(1, nowcast_frames + 1):
            frame_time = now + timedelta(minutes=i * 10)
            timestamp = int(frame_time.timestamp())
            nowcast.append({
                "time": timestamp,
                "path": f"/v2/radar/{timestamp}/256",
                "datetime": frame_time.isoformat(),
                "type": "nowcast"
            })
        
        return {
            "version": "2.0",
            "generated": int(now.timestamp()),
            "host": "https://tilecache.rainviewer.com",
            "radar": {
                "past": past,
                "nowcast": nowcast
            },
            "satellite": {
                "infrared": []  # Could add satellite frames
            },
            "tile_url_template": "{host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png"
        }
    
    def generate_mock_tile_data(
        self,
        z: int,
        x: int,
        y: int,
        timestamp: int,
        scenario: str = "scattered"
    ) -> bytes:
        """
        Generate mock radar tile image data.
        
        For production demo, return a colored tile based on scenario.
        In real implementation, would return actual PNG data.
        """
        # This would return actual radar tile PNG data
        # For now, return placeholder metadata
        return b"MOCK_RADAR_TILE_DATA"


class MockAlertsGenerator:
    """
    Generates realistic severe weather alerts.
    """
    
    ALERT_TEMPLATES = [
        {
            "event": "Tornado Warning",
            "severity": "Extreme",
            "urgency": "Immediate",
            "certainty": "Observed",
            "headline": "Tornado Warning for {area}",
            "description": "The National Weather Service has issued a Tornado Warning for {area} until {expires}. A confirmed tornado was observed near {location}. TAKE COVER NOW! Move to a basement or interior room on the lowest floor of a sturdy building.",
            "instruction": "TAKE SHELTER NOW in a sturdy building. If outdoors, lie flat in a ditch and cover your head. Mobile homes provide no protection from tornadoes.",
            "color": "#FF0000"
        },
        {
            "event": "Severe Thunderstorm Warning",
            "severity": "Severe",
            "urgency": "Immediate",
            "certainty": "Observed",
            "headline": "Severe Thunderstorm Warning for {area}",
            "description": "The National Weather Service has issued a Severe Thunderstorm Warning for {area} until {expires}. A severe thunderstorm capable of producing damaging winds and large hail is located near {location}, moving {direction} at {speed} mph.",
            "instruction": "Move to an interior room on the lowest floor of a sturdy building. Avoid windows.",
            "color": "#FFA500"
        },
        {
            "event": "Flash Flood Warning",
            "severity": "Severe",
            "urgency": "Immediate",
            "certainty": "Likely",
            "headline": "Flash Flood Warning for {area}",
            "description": "Flash flooding is occurring or imminent in {area}. {amount} inches of rain have fallen in the past {hours} hours. Low-lying areas and roads near streams may flood quickly.",
            "instruction": "Turn around, don't drown! Do not drive through flooded roads. Move to higher ground if in a flood-prone area.",
            "color": "#00FF00"
        },
        {
            "event": "Excessive Heat Warning",
            "severity": "Severe",
            "urgency": "Expected",
            "certainty": "Likely",
            "headline": "Excessive Heat Warning for {area}",
            "description": "Dangerously hot conditions with temperatures of {temp}°F expected in {area}. Heat index values up to {heat_index}°F possible. This level of heat can be life-threatening.",
            "instruction": "Drink plenty of fluids, stay in air-conditioned areas, and limit outdoor activity during peak heat hours.",
            "color": "#FF6600"
        },
        {
            "event": "Winter Storm Warning",
            "severity": "Severe",
            "urgency": "Expected",
            "certainty": "Likely",
            "headline": "Winter Storm Warning for {area}",
            "description": "Heavy snow expected in {area}. Total snow accumulations of {snow_low} to {snow_high} inches expected. Winds gusting up to {wind} mph will cause blowing and drifting snow.",
            "instruction": "Travel could be very difficult to impossible. Prepare now for potential power outages.",
            "color": "#9999FF"
        },
        {
            "event": "Hurricane Warning",
            "severity": "Extreme",
            "urgency": "Immediate",
            "certainty": "Likely",
            "headline": "Hurricane Warning for {area}",
            "description": "Hurricane {name} is approaching {area} with maximum sustained winds of {wind} mph. Life-threatening storm surge, destructive winds, and flooding rain expected.",
            "instruction": "Complete preparations immediately. If ordered to evacuate, do so now. Do not venture outside during the eye of the storm.",
            "color": "#FF00FF"
        }
    ]
    
    def generate_alerts(
        self,
        latitude: float,
        longitude: float,
        scenario: Optional[str] = None,
        reference_time: Optional[datetime] = None
    ) -> List[Dict]:
        """
        Generate sample weather alerts for a location.
        
        Args:
            latitude: Location latitude
            longitude: Location longitude
            scenario: Alert type (or random)
            reference_time: Current time
            
        Returns:
            List of weather alert dictionaries
        """
        now = reference_time or datetime.utcnow()
        
        # Determine area name based on coordinates
        area = self._get_area_name(latitude, longitude)
        
        # 60% chance of no alerts for realism
        if scenario is None and random.random() < 0.6:
            return []
        
        # Select alert template
        if scenario:
            template = next((t for t in self.ALERT_TEMPLATES if scenario.lower() in t["event"].lower()), None)
            if template is None:
                template = random.choice(self.ALERT_TEMPLATES)
        else:
            template = random.choice(self.ALERT_TEMPLATES)
        
        # Generate alert
        expires = now + timedelta(hours=random.randint(2, 6))
        effective = now - timedelta(minutes=random.randint(5, 30))
        
        # Fill in template
        alert = {
            "id": f"urn:oid:2.49.0.1.840.0.{random.randint(1000000, 9999999)}.{now.strftime('%Y%m%d%H%M')}",
            "event": template["event"],
            "severity": template["severity"],
            "urgency": template["urgency"],
            "certainty": template["certainty"],
            "headline": template["headline"].format(area=area),
            "description": template["description"].format(
                area=area,
                expires=expires.strftime("%I:%M %p"),
                location=f"{area} area",
                direction=random.choice(["northeast", "east", "southeast", "south"]),
                speed=random.randint(20, 45),
                amount=round(random.uniform(2, 5), 1),
                hours=random.randint(2, 6),
                temp=random.randint(105, 115),
                heat_index=random.randint(110, 125),
                snow_low=random.randint(6, 10),
                snow_high=random.randint(12, 18),
                wind=random.randint(40, 80),
                name=random.choice(["Maria", "Harvey", "Michael", "Irma", "Ian"])
            ),
            "instruction": template["instruction"],
            "effective": effective.isoformat(),
            "expires": expires.isoformat(),
            "sender": "National Weather Service",
            "sender_name": f"NWS {area}",
            "area_desc": area,
            "color": template["color"],
            "polygon": self._generate_polygon(latitude, longitude)
        }
        
        return [alert]
    
    def _get_area_name(self, lat: float, lon: float) -> str:
        """Get approximate area name from coordinates."""
        # Simple lookup (in production, would use geocoding)
        areas = {
            (40.7, -74.0): "New York City Metro Area",
            (34.0, -118.2): "Los Angeles County",
            (41.8, -87.6): "Chicago Metropolitan Area",
            (29.7, -95.3): "Houston Metro Area",
            (33.4, -112.0): "Phoenix Metro Area",
            (25.7, -80.1): "Miami-Dade County",
            (47.6, -122.3): "Seattle-Tacoma Area",
            (39.7, -104.9): "Denver Metro Area",
        }
        
        # Find closest
        min_dist = float('inf')
        closest_area = "Local Area"
        
        for (area_lat, area_lon), name in areas.items():
            dist = abs(lat - area_lat) + abs(lon - area_lon)
            if dist < min_dist:
                min_dist = dist
                closest_area = name
        
        return closest_area if min_dist < 3 else "Local Area"
    
    def _generate_polygon(self, lat: float, lon: float) -> List[List[float]]:
        """Generate alert polygon coordinates."""
        # Create a rough polygon around the location
        delta = 0.3
        return [
            [lon - delta, lat - delta],
            [lon + delta, lat - delta],
            [lon + delta, lat + delta],
            [lon - delta, lat + delta],
            [lon - delta, lat - delta]
        ]


class MockPollenGenerator:
    """
    Generates realistic pollen forecasts with seasonal variation.
    """
    
    # Pollen seasons by hemisphere and type
    POLLEN_SEASONS = {
        "northern": {
            "tree": {"peak_months": [3, 4, 5], "off_months": [11, 12, 1, 2]},
            "grass": {"peak_months": [5, 6, 7], "off_months": [10, 11, 12, 1, 2, 3]},
            "weed": {"peak_months": [8, 9, 10], "off_months": [12, 1, 2, 3, 4, 5]}
        },
        "southern": {
            "tree": {"peak_months": [9, 10, 11], "off_months": [5, 6, 7, 8]},
            "grass": {"peak_months": [11, 12, 1], "off_months": [4, 5, 6, 7, 8, 9]},
            "weed": {"peak_months": [2, 3, 4], "off_months": [6, 7, 8, 9, 10, 11]}
        }
    }
    
    TREE_SPECIES = ["Oak", "Birch", "Maple", "Cedar", "Pine", "Elm", "Ash"]
    GRASS_SPECIES = ["Timothy", "Bermuda", "Kentucky Bluegrass", "Ryegrass", "Fescue"]
    WEED_SPECIES = ["Ragweed", "Sagebrush", "Pigweed", "Lamb's Quarters", "Nettle"]
    
    def generate_pollen_forecast(
        self,
        latitude: float,
        longitude: float,
        days: int = 5,
        reference_date: Optional[date] = None
    ) -> Dict:
        """
        Generate realistic pollen forecast.
        
        Args:
            latitude: Location latitude
            longitude: Location longitude
            days: Forecast days
            reference_date: Start date
            
        Returns:
            Pollen forecast data
        """
        today = reference_date or date.today()
        hemisphere = "northern" if latitude >= 0 else "southern"
        seasons = self.POLLEN_SEASONS[hemisphere]
        
        forecast = []
        
        for i in range(days):
            forecast_date = today + timedelta(days=i)
            month = forecast_date.month
            
            # Calculate indices based on season
            tree_index = self._calculate_pollen_index(month, seasons["tree"])
            grass_index = self._calculate_pollen_index(month, seasons["grass"])
            weed_index = self._calculate_pollen_index(month, seasons["weed"])
            
            forecast.append({
                "date": forecast_date.isoformat(),
                "tree": {
                    "display_name": "Tree Pollen",
                    "index": tree_index,
                    "level": self._index_to_level(tree_index),
                    "species": random.sample(self.TREE_SPECIES, min(3, len(self.TREE_SPECIES))),
                    "health_advice": self._get_health_advice(tree_index),
                    "color": self._level_to_color(self._index_to_level(tree_index))
                },
                "grass": {
                    "display_name": "Grass Pollen",
                    "index": grass_index,
                    "level": self._index_to_level(grass_index),
                    "species": random.sample(self.GRASS_SPECIES, min(3, len(self.GRASS_SPECIES))),
                    "health_advice": self._get_health_advice(grass_index),
                    "color": self._level_to_color(self._index_to_level(grass_index))
                },
                "weed": {
                    "display_name": "Weed Pollen",
                    "index": weed_index,
                    "level": self._index_to_level(weed_index),
                    "species": random.sample(self.WEED_SPECIES, min(3, len(self.WEED_SPECIES))),
                    "health_advice": self._get_health_advice(weed_index),
                    "color": self._level_to_color(self._index_to_level(weed_index))
                },
                "overall_index": max(tree_index, grass_index, weed_index),
                "overall_level": self._index_to_level(max(tree_index, grass_index, weed_index))
            })
        
        return {
            "location": {
                "latitude": latitude,
                "longitude": longitude
            },
            "forecast": forecast,
            "last_updated": datetime.utcnow().isoformat(),
            "health_recommendations": self._get_recommendations(forecast[0] if forecast else None)
        }
    
    def _calculate_pollen_index(self, month: int, season_info: Dict) -> int:
        """Calculate pollen index for a month based on season."""
        peak_months = season_info["peak_months"]
        off_months = season_info["off_months"]
        
        if month in peak_months:
            base = random.randint(3, 5)  # High to Very High
        elif month in off_months:
            base = random.randint(0, 1)  # None to Low
        else:
            base = random.randint(1, 3)  # Low to Moderate
        
        return base
    
    def _index_to_level(self, index: int) -> str:
        """Convert numeric index to level string."""
        levels = ["none", "low", "moderate", "high", "very_high"]
        return levels[min(index, 4)]
    
    def _level_to_color(self, level: str) -> str:
        """Get color for pollen level."""
        colors = {
            "none": "#00FF00",
            "low": "#ADFF2F",
            "moderate": "#FFFF00",
            "high": "#FFA500",
            "very_high": "#FF0000"
        }
        return colors.get(level, "#808080")
    
    def _get_health_advice(self, index: int) -> str:
        """Get health advice for pollen level."""
        if index <= 1:
            return "Low pollen levels. Enjoy outdoor activities."
        elif index == 2:
            return "Moderate levels. Sensitive individuals may experience symptoms."
        elif index == 3:
            return "High levels. Limit outdoor activities if sensitive."
        else:
            return "Very high levels. Stay indoors if severely allergic."
    
    def _get_recommendations(self, today_data: Optional[Dict]) -> List[str]:
        """Generate health recommendations."""
        if not today_data:
            return ["Check back for pollen updates."]
        
        max_index = today_data.get("overall_index", 0)
        
        if max_index <= 1:
            return [
                "✅ Low pollen day - great for outdoor activities",
                "💪 Good day for exercise outdoors"
            ]
        elif max_index == 2:
            return [
                "⚠️ Moderate pollen - sensitive individuals may react",
                "💊 Consider antihistamines if you have allergies",
                "🚿 Shower after outdoor activities"
            ]
        elif max_index == 3:
            return [
                "🔴 High pollen - limit outdoor exposure",
                "💊 Take allergy medication",
                "🏠 Keep windows closed",
                "👓 Wear sunglasses outdoors"
            ]
        else:
            return [
                "⛔ Very high pollen - stay indoors if possible",
                "💊 Take allergy medication proactively",
                "🏠 Use air purifiers indoors",
                "🚗 Keep car windows closed",
                "🧣 Wear a mask if going outside"
            ]
