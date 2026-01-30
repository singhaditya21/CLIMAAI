"""
AI service for generating weather insights using OpenAI.
Converts raw weather data into natural language and actionable recommendations.
"""
import json
from typing import Optional
from datetime import datetime
import redis.asyncio as redis
from openai import AsyncOpenAI
from ..config import get_settings
from ..schemas.weather import WeatherResponse
from ..schemas.ai import (
    AIInsightsResponse,
    DailySummary,
    OutfitRecommendation,
    ActivityRecommendation,
    HealthInsight,
    TravelRiskAnalysis,
    RiskLevel,
)

settings = get_settings()


class AIService:
    """Service for generating AI-powered weather insights."""
    
    def __init__(self):
        self.redis_client: Optional[redis.Redis] = None
        if settings.OPENAI_API_KEY and settings.ENABLE_AI_INSIGHTS:
            self.openai_client = AsyncOpenAI(api_key=settings.OPENAI_API_KEY)
        else:
            self.openai_client = None
    
    async def _get_redis(self) -> redis.Redis:
        """Get or create Redis client."""
        if self.redis_client is None:
            self.redis_client = await redis.from_url(settings.REDIS_URL)
        return self.redis_client
    
    def _get_cache_key(self, latitude: float, longitude: float, insight_type: str) -> str:
        """Generate cache key for AI insights."""
        date_str = datetime.now().strftime("%Y-%m-%d")
        return f"ai:{insight_type}:{latitude:.2f}:{longitude:.2f}:{date_str}"
    
    async def _get_cached_insight(self, cache_key: str) -> Optional[dict]:
        """Get cached AI insight."""
        try:
            redis_client = await self._get_redis()
            cached = await redis_client.get(cache_key)
            if cached:
                return json.loads(cached)
        except Exception as e:
            print(f"Redis get error: {e}")
        return None
    
    async def _set_cached_insight(self, cache_key: str, data: dict):
        """Set cached AI insight."""
        try:
            redis_client = await self._get_redis()
            await redis_client.setex(cache_key, settings.AI_CACHE_TTL, json.dumps(data))
        except Exception as e:
            print(f"Redis set error: {e}")
    
    def _build_weather_context(self, weather: WeatherResponse) -> str:
        """Build weather context string for AI prompts."""
        current = weather.current
        today = weather.daily[0] if weather.daily else None
        
        context = f"""Current Weather:
- Temperature: {current.temperature}°C (feels like {current.feels_like}°C)
- Condition: {current.weather_description}
- Humidity: {current.humidity}%
- Wind: {current.wind_speed} km/h
- Precipitation: {current.precipitation} mm
- UV Index: {current.uv_index}
- Cloud Cover: {current.cloud_cover}%
"""
        
        if today:
            context += f"""
Today's Forecast:
- High: {today.temperature_max}°C, Low: {today.temperature_min}°C
- Sunrise: {today.sunrise}, Sunset: {today.sunset}
- Rain Chance: {today.precipitation_probability}%
- Max UV Index: {today.uv_index_max}
"""
        
        if weather.air_quality:
            aq = weather.air_quality
            context += f"""
Air Quality:
- AQI: {aq.aqi} ({aq.category})
- PM2.5: {aq.pm2_5}, PM10: {aq.pm10}
- {aq.health_recommendation}
"""
        
        return context
    
    async def generate_daily_summary(self, weather: WeatherResponse, location_name: str = "your location") -> DailySummary:
        """Generate AI daily weather summary."""
        cache_key = self._get_cache_key(
            weather.location["latitude"],
            weather.location["longitude"],
            "daily_summary"
        )
        
        # Check cache
        cached = await self._get_cached_insight(cache_key)
        if cached:
            return DailySummary(**cached)
        
        if not self.openai_client:
            return self._fallback_daily_summary(weather)
        
        weather_context = self._build_weather_context(weather)
        
        prompt = f"""You are a friendly weather assistant. Create a daily weather summary for {location_name}.

{weather_context}

Generate a JSON response with this structure:
{{
  "title": "short catchy headline (max 50 chars)",
  "summary": "2-3 sentence natural language summary of the day's weather",
  "highlights": ["key point 1", "key point 2", "key point 3"],
  "warnings": ["warning 1 if any severe weather"],
  "best_times": {{"outdoor_activities": "time range", "exercise": "time range"}}
}}

Be conversational, helpful, and concise. Focus on what people need to know to plan their day."""
        
        try:
            response = await self.openai_client.chat.completions.create(
                model=settings.OPENAI_MODEL,
                messages=[
                    {"role": "system", "content": "You are a helpful weather assistant that provides clear, actionable weather summaries."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.7,
                max_tokens=500,
            )
            
            result = json.loads(response.choices[0].message.content)
            summary = DailySummary(**result)
            
            # Cache the result
            await self._set_cached_insight(cache_key, summary.model_dump(mode="json"))
            
            return summary
            
        except Exception as e:
            print(f"AI generation error: {e}")
            return self._fallback_daily_summary(weather)
    
    async def generate_outfit_recommendation(self, weather: WeatherResponse) -> OutfitRecommendation:
        """Generate AI outfit recommendation."""
        cache_key = self._get_cache_key(
            weather.location["latitude"],
            weather.location["longitude"],
            "outfit"
        )
        
        # Check cache
        cached = await self._get_cached_insight(cache_key)
        if cached:
            return OutfitRecommendation(**cached)
        
        if not self.openai_client:
            return self._fallback_outfit_recommendation(weather)
        
        weather_context = self._build_weather_context(weather)
        
        prompt = f"""Based on this weather, recommend what to wear:

{weather_context}

Generate a JSON response with this structure:
{{
  "summary": "brief outfit suggestion (1 sentence)",
  "details": "detailed clothing recommendations (2-3 sentences)",
  "accessories": ["umbrella", "sunglasses", "hat", etc.],
  "layer_recommendation": "layering advice based on temperature changes"
}}

Be practical and specific."""
        
        try:
            response = await self.openai_client.chat.completions.create(
                model=settings.OPENAI_MODEL,
                messages=[
                    {"role": "system", "content": "You are a fashion-aware weather assistant helping people dress appropriately."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.7,
                max_tokens=300,
            )
            
            result = json.loads(response.choices[0].message.content)
            outfit = OutfitRecommendation(**result)
            
            await self._set_cached_insight(cache_key, outfit.model_dump(mode="json"))
            
            return outfit
            
        except Exception as e:
            print(f"AI generation error: {e}")
            return self._fallback_outfit_recommendation(weather)
    
    async def generate_activity_recommendations(self, weather: WeatherResponse) -> list[ActivityRecommendation]:
        """Generate AI activity recommendations."""
        cache_key = self._get_cache_key(
            weather.location["latitude"],
            weather.location["longitude"],
            "activities"
        )
        
        # Check cache
        cached = await self._get_cached_insight(cache_key)
        if cached:
            return [ActivityRecommendation(**item) for item in cached]
        
        if not self.openai_client:
            return self._fallback_activity_recommendations(weather)
        
        weather_context = self._build_weather_context(weather)
        
        # Get hourly summary
        hourly_summary = ""
        if len(weather.hourly) >= 12:
            hourly_summary = f"Next 12 hours temps: {weather.hourly[0].temperature}°C to {weather.hourly[11].temperature}°C"
        
        prompt = f"""Based on this weather, recommend outdoor activities:

{weather_context}
{hourly_summary}

Generate a JSON response with an array of 3-5 activities:
{{
  "activities": [
    {{
      "activity": "activity name",
      "suitability_score": 0-100,
      "best_time": "time range or 'now'",
      "reasoning": "why this is good/bad now",
      "precautions": ["safety tip 1", "safety tip 2"]
    }}
  ]
}}

Include diverse activities: running, cycling, hiking, beach, picnic, etc."""
        
        try:
            response = await self.openai_client.chat.completions.create(
                model=settings.OPENAI_MODEL,
                messages=[
                    {"role": "system", "content": "You are an outdoor activity expert helping people plan their day."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.7,
                max_tokens=600,
            )
            
            result = json.loads(response.choices[0].message.content)
            activities = [ActivityRecommendation(**item) for item in result["activities"]]
            
            await self._set_cached_insight(cache_key, [a.model_dump(mode="json") for a in activities])
            
            return activities
            
        except Exception as e:
            print(f"AI generation error: {e}")
            return self._fallback_activity_recommendations(weather)
    
    async def generate_health_insights(self, weather: WeatherResponse) -> HealthInsight:
        """Generate health-related weather insights."""
        current = weather.current
        air_quality = weather.air_quality
        
        # UV Risk
        uv = current.uv_index
        if uv < 3:
            uv_risk = RiskLevel.LOW
            uv_advice = "Low UV risk. Sun protection not required."
        elif uv < 6:
            uv_risk = RiskLevel.MODERATE
            uv_advice = "Moderate UV risk. Wear sunscreen if outdoors for extended periods."
        elif uv < 8:
            uv_risk = RiskLevel.HIGH
            uv_advice = "High UV risk. Sunscreen, hat, and sunglasses recommended."
        else:
            uv_risk = RiskLevel.VERY_HIGH
            uv_advice = "Very high UV risk. Minimize sun exposure between 10am-4pm. Wear protective clothing."
        
        # Air Quality Risk
        if air_quality:
            aqi = air_quality.aqi
            if aqi <= 50:
                aq_risk = RiskLevel.LOW
                aq_advice = air_quality.health_recommendation
            elif aqi <= 100:
                aq_risk = RiskLevel.MODERATE
                aq_advice = air_quality.health_recommendation
            elif aqi <= 150:
                aq_risk = RiskLevel.HIGH
                aq_advice = air_quality.health_recommendation
            else:
                aq_risk = RiskLevel.VERY_HIGH
                aq_advice = air_quality.health_recommendation
        else:
            aq_risk = RiskLevel.LOW
            aq_advice = "Air quality data unavailable."
        
        # Heat Stress Risk
        feels_like = current.feels_like
        if feels_like < 27:
            heat_risk = RiskLevel.LOW
            heat_advice = "Comfortable temperature for outdoor activities."
        elif feels_like < 32:
            heat_risk = RiskLevel.MODERATE
            heat_advice = "Stay hydrated during outdoor activities."
        elif feels_like < 40:
            heat_risk = RiskLevel.HIGH
            heat_advice = "High heat stress risk. Limit strenuous outdoor activities. Stay hydrated."
        else:
            heat_risk = RiskLevel.VERY_HIGH
            heat_advice = "Extreme heat. Avoid outdoor activities. Stay in air-conditioned spaces."
        
        # General health tips
        health_tips = []
        if current.humidity > 80:
            health_tips.append("High humidity may cause discomfort. Stay in ventilated areas.")
        if current.precipitation > 0:
            health_tips.append("Wet conditions may increase slip hazards.")
        if current.wind_speed > 40:
            health_tips.append("Strong winds. Secure loose objects and be cautious outdoors.")
        
        return HealthInsight(
            uv_risk=uv_risk,
            uv_advice=uv_advice,
            air_quality_risk=aq_risk,
            air_quality_advice=aq_advice,
            heat_stress_risk=heat_risk,
            heat_stress_advice=heat_advice,
            general_health_tips=health_tips,
        )
    
    async def generate_travel_risk_analysis(
        self,
        weather: WeatherResponse,
        destination: str = "your destination"
    ) -> TravelRiskAnalysis:
        """Generate AI-powered travel risk analysis."""
        cache_key = self._get_cache_key(
            weather.location["latitude"],
            weather.location["longitude"],
            "travel_risk"
        )
        
        # Check cache
        cached = await self._get_cached_insight(cache_key)
        if cached:
            return TravelRiskAnalysis(**cached)
        
        if not self.openai_client:
            return self._fallback_travel_risk_analysis(weather)
        
        weather_context = self._build_weather_context(weather)
        
        # Build hourly context for route planning
        hourly_context = ""
        if len(weather.hourly) >= 12:
            temps = [h.temperature for h in weather.hourly[:12]]
            precips = [h.precipitation for h in weather.hourly[:12]]
            hourly_context = f"""
Next 12 Hours:
- Temperature range: {min(temps):.1f}°C to {max(temps):.1f}°C
- Total precipitation: {sum(precips):.1f}mm
- Peak rain: {max(precips):.1f}mm/hour
"""
        
        prompt = f"""Analyze travel safety and conditions for {destination}:

{weather_context}
{hourly_context}

Generate a JSON response with this structure:
{{
  "overall_risk_level": "LOW|MODERATE|HIGH|VERY_HIGH",
  "risk_score": 0-100 (0=safest, 100=most dangerous),
  "summary": "brief travel safety summary (1-2 sentences)",
  "risk_factors": [
    {{
      "factor": "weather/visibility/road conditions/etc",
      "severity": "LOW|MODERATE|HIGH|VERY_HIGH",
      "description": "explanation of this specific risk"
    }}
  ],
  "recommendations": [
    "specific actionable recommendation 1",
    "specific actionable recommendation 2"
  ],
  "best_departure_time": "recommended time range for travel",
  "worst_conditions_expected": "when conditions will be worst"
}}

Consider:
- Weather conditions (rain, wind, temperature, visibility)
- Air quality impact on travel
- Road safety (wet roads, ice, visibility)
- Time-specific patterns (when conditions improve/worsen)

Be specific and actionable."""

        try:
            response = await self.openai_client.chat.completions.create(
                model=settings.OPENAI_MODEL,
                messages=[
                    {"role": "system", "content": "You are a travel safety expert analyzing weather conditions for safe journey planning."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                temperature=0.7,
                max_tokens=700,
            )
            
            result = json.loads(response.choices[0].message.content)
            
            # Convert string risk level to RiskLevel enum
            risk_level_str = result.get("overall_risk_level", "MODERATE")
            result["overall_risk_level"] = RiskLevel[risk_level_str]
            
            # Convert risk factors severity to RiskLevel enum
            for factor in result.get("risk_factors", []):
                severity_str = factor.get("severity", "LOW")
                factor["severity"] = RiskLevel[severity_str]
            
            travel_risk = TravelRiskAnalysis(**result)
            
            await self._set_cached_insight(cache_key, travel_risk.model_dump(mode="json"))
            
            return travel_risk
            
        except Exception as e:
            print(f"AI generation error for travel risk: {e}")
            return self._fallback_travel_risk_analysis(weather)
    
    async def generate_complete_insights(
        self,
        weather: WeatherResponse,
        location_name: str = "your location",
        include_travel: bool = False
    ) -> AIInsightsResponse:
        """Generate complete AI insights package."""
        # Generate all insights in parallel would be ideal, but for simplicity, sequential
        daily_summary = await self.generate_daily_summary(weather, location_name)
        outfit = await self.generate_outfit_recommendation(weather)
        activities = await self.generate_activity_recommendations(weather)
        health = await self.generate_health_insights(weather)
        
        travel = None
        if include_travel:
            travel = await self.generate_travel_risk_analysis(weather, location_name)
        
        return AIInsightsResponse(
            daily_summary=daily_summary,
            outfit=outfit,
            activities=activities,
            health=health,
            travel=travel,
            cached=False,
        )
    
    # Fallback methods for when AI is not available
    def _fallback_daily_summary(self, weather: WeatherResponse) -> DailySummary:
        """Generate basic daily summary without AI."""
        current = weather.current
        today = weather.daily[0] if weather.daily else None
        
        title = f"{current.weather_description}, {int(current.temperature)}°C"
        summary = f"Today's weather: {current.weather_description.lower()} with temperatures around {int(current.temperature)}°C. "
        
        if today:
            summary += f"High of {int(today.temperature_max)}°C and low of {int(today.temperature_min)}°C. "
            if today.precipitation_probability > 50:
                summary += f"{today.precipitation_probability}% chance of rain."
        
        highlights = [
            f"Temperature: {int(current.temperature)}°C",
            f"Humidity: {current.humidity}%",
            f"Wind: {int(current.wind_speed)} km/h",
        ]
        
        warnings = []
        if current.uv_index > 7:
            warnings.append("High UV index - sun protection recommended")
        
        return DailySummary(
            title=title,
            summary=summary,
            highlights=highlights,
            warnings=warnings,
            best_times={},
        )
    
    def _fallback_outfit_recommendation(self, weather: WeatherResponse) -> OutfitRecommendation:
        """Generate basic outfit recommendation without AI."""
        temp = weather.current.temperature
        
        if temp < 0:
            summary = "Bundle up! Heavy winter clothing needed."
            details = "Wear a heavy coat, warm layers, gloves, and a hat."
            accessories = ["winter coat", "gloves", "hat", "scarf"]
        elif temp < 10:
            summary = "Cool weather. Dress in layers."
            details = "A jacket or sweater with long pants recommended."
            accessories = ["jacket", "light scarf"]
        elif temp < 20:
            summary = "Mild weather. Light layers recommended."
            details = "Long sleeves or a light sweater should be comfortable."
            accessories = []
        elif temp < 30:
            summary = "Warm weather. Light clothing recommended."
            details = "T-shirt and shorts or light pants are ideal."
            accessories = ["sunglasses"]
        else:
            summary = "Hot! Stay cool with minimal lightweight clothing."
            details = "Wear breathable, light-colored clothing. Stay hydrated."
            accessories = ["sunglasses", "hat", "sunscreen"]
        
        if weather.current.precipitation > 0:
            accessories.append("umbrella")
        
        return OutfitRecommendation(
            summary=summary,
            details=details,
            accessories=accessories,
            layer_recommendation="Adjust layers based on indoor/outdoor transitions.",
        )
    
    def _fallback_activity_recommendations(self, weather: WeatherResponse) -> list[ActivityRecommendation]:
        """Generate basic activity recommendations without AI."""
        temp = weather.current.temperature
        precip = weather.current.precipitation
        
        activities = []
        
        # Running
        if precip == 0 and 5 <= temp <= 25:
            activities.append(ActivityRecommendation(
                activity="Running",
                suitability_score=85,
                best_time="morning or evening",
                reasoning="Good temperature and no rain",
                precautions=["Stay hydrated", "Wear appropriate footwear"],
            ))
        
        # Cycling
        if precip == 0 and temp > 0:
            activities.append(ActivityRecommendation(
                activity="Cycling",
                suitability_score=75,
                best_time="now",
                reasoning="Dry conditions",
                precautions=["Wear helmet", "Check wind speed"],
            ))
        
        return activities[:3]
    
    def _fallback_travel_risk_analysis(self, weather: WeatherResponse) -> TravelRiskAnalysis:
        """Generate basic travel risk analysis without AI."""
        current = weather.current
        air_quality = weather.air_quality
        
        # Calculate risk score based on conditions
        risk_score = 0
        risk_factors = []
        
        # Weather conditions
        if current.precipitation > 5:
            risk_score += 30
            risk_factors.append({
                "factor": "Heavy rainfall",
                "severity": RiskLevel.HIGH if current.precipitation > 10 else RiskLevel.MODERATE,
                "description": f"Heavy rain ({current.precipitation}mm) may reduce visibility and create hazardous road conditions."
            })
        elif current.precipitation > 0:
            risk_score += 15
            risk_factors.append({
                "factor": "Light rain",
                "severity": RiskLevel.LOW,
                "description": "Light rain may cause slippery roads. Drive carefully."
            })
        
        # Wind conditions
        if current.wind_speed > 50:
            risk_score += 25
            risk_factors.append({
                "factor": "Strong winds",
                "severity": RiskLevel.HIGH,
                "description": f"Very strong winds ({current.wind_speed} km/h) may affect vehicle control, especially for high-sided vehicles."
            })
        elif current.wind_speed > 30:
            risk_score += 10
            risk_factors.append({
                "factor": "Moderate winds",
                "severity": RiskLevel.MODERATE,
                "description": f"Moderate winds ({current.wind_speed} km/h) may affect driving comfort."
            })
        
        # Visibility (based on cloud cover and precipitation)
        if current.cloud_cover > 90 and current.precipitation > 2:
            risk_score += 20
            risk_factors.append({
                "factor": "Poor visibility",
                "severity": RiskLevel.HIGH,
                "description": "Heavy clouds and precipitation may significantly reduce visibility."
            })
        
        # Air quality
        if air_quality and air_quality.aqi > 150:
            risk_score += 15
            risk_factors.append({
                "factor": "Poor air quality",
                "severity": RiskLevel.HIGH,
                "description": f"Poor air quality (AQI: {air_quality.aqi}) may affect health during travel."
            })
        
        # Temperature extremes
        if current.temperature < -5:
            risk_score += 20
            risk_factors.append({
                "factor": "Freezing conditions",
                "severity": RiskLevel.HIGH,
                "description": "Very cold temperatures may cause icy roads. Drive with extreme caution."
            })
        elif current.temperature > 40:
            risk_score += 15
            risk_factors.append({
                "factor": "Extreme heat",
                "severity": RiskLevel.MODERATE,
                "description": "Extreme heat may affect vehicle performance and driver comfort."
            })
        
        # Determine overall risk level
        if risk_score >= 60:
            overall_risk = RiskLevel.VERY_HIGH
            summary = "Travel not recommended. Hazardous conditions present."
        elif risk_score >= 40:
            overall_risk = RiskLevel.HIGH
            summary = "Travel should be avoided if possible. Significant risks present."
        elif risk_score >= 20:
            overall_risk = RiskLevel.MODERATE
            summary = "Travel with caution. Some weather-related risks present."
        else:
            overall_risk = RiskLevel.LOW
            summary = "Generally safe travel conditions. Normal precautions apply."
        
        # Generate recommendations
        recommendations = []
        if current.precipitation > 0:
            recommendations.append("Drive at reduced speed and maintain safe following distance")
        if current.wind_speed > 30:
            recommendations.append("Be alert for sudden gusts, especially on exposed routes")
        if air_quality and air_quality.aqi > 100:
            recommendations.append("Keep windows closed and use vehicle air recirculation")
        if len(recommendations) == 0:
            recommendations.append("Follow standard safe driving practices")
        
        # Determine best departure time
        if len(weather.hourly) >= 12:
            # Find hour with least precipitation
            min_precip_hour = min(range(12), key=lambda i: weather.hourly[i].precipitation)
            best_time = f"Around {min_precip_hour} hours from now"
            worst_time = f"Around {max(range(12), key=lambda i: weather.hourly[i].precipitation)} hours from now"
        else:
            best_time = "Current conditions are relatively stable"
            worst_time = "No significant changes expected"
        
        return TravelRiskAnalysis(
            overall_risk_level=overall_risk,
            risk_score=min(risk_score, 100),
            summary=summary,
            risk_factors=risk_factors,
            recommendations=recommendations,
            best_departure_time=best_time,
            worst_conditions_expected=worst_time,
        )
    
    async def close(self):
        """Close Redis connection."""
        if self.redis_client:
            await self.redis_client.close()
