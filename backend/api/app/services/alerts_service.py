"""
Weather alerts service for monitoring severe weather conditions.
"""
from typing import List, Optional
from datetime import datetime, timedelta
from sqlalchemy.ext.asyncio import AsyncSession
from ..schemas.weather import WeatherResponse, CurrentWeather
from ..schemas.ai import RiskLevel


class WeatherAlert:
    """Weather alert model"""
    def __init__(
        self,
        alert_type: str,
        severity: RiskLevel,
        title: str,
        message: str,
        metadata: Optional[dict] = None,
        expires_at: Optional[datetime] = None
    ):
        self.alert_type = alert_type
        self.severity = severity
        self.title = title
        self.message = message
        self.metadata = metadata or {}
        self.expires_at = expires_at or datetime.utcnow() + timedelta(hours=24)


class AlertsService:
    """Service for detecting and managing weather alerts."""
    
    # Alert thresholds
    TEMPERATURE_EXTREME_COLD = -10  # °C
    TEMPERATURE_EXTREME_HOT = 38    # °C
    HIGH_WIND_THRESHOLD = 50         # km/h
    VERY_HIGH_WIND_THRESHOLD = 70    # km/h
    HEAVY_RAIN_THRESHOLD = 10        # mm/hour
    VERY_HEAVY_RAIN_THRESHOLD = 20   # mm/hour
    POOR_AQI_THRESHOLD = 150
    VERY_POOR_AQI_THRESHOLD = 200
    HIGH_UV_THRESHOLD = 8
    
    async def evaluate_alerts(
        self,
        weather: WeatherResponse,
        location_name: str = "your location"
    ) -> List[WeatherAlert]:
        """
        Evaluate weather conditions and generate alerts.
        
        Returns list of active alerts based on current and forecasted conditions.
        """
        alerts = []
        current = weather.current
        air_quality = weather.air_quality
        
        # Temperature extremes
        if current.temperature <= self.TEMPERATURE_EXTREME_COLD:
            alerts.append(WeatherAlert(
                alert_type="temperature_extreme",
                severity=RiskLevel.VERY_HIGH,
                title="⚠️ Extreme Cold Warning",
                message=f"Very cold temperature of {current.temperature:.1f}°C in {location_name}. Frostbite risk. Limit outdoor exposure.",
                metadata={"temperature": current.temperature}
            ))
        elif current.temperature >= self.TEMPERATURE_EXTREME_HOT:
            alerts.append(WeatherAlert(
                alert_type="temperature_extreme",
                severity=RiskLevel.VERY_HIGH,
                title="🌡️ Extreme Heat Warning",
                message=f"Dangerous heat of {current.temperature:.1f}°C in {location_name}. Heat stroke risk. Stay hydrated and indoors.",
                metadata={"temperature": current.temperature}
            ))
        
        # Wind alerts
        if current.wind_speed >= self.VERY_HIGH_WIND_THRESHOLD:
            alerts.append(WeatherAlert(
                alert_type="high_wind",
                severity=RiskLevel.VERY_HIGH,
                title="💨 Severe Wind Warning",
                message=f"Very strong winds of {current.wind_speed:.0f} km/h in {location_name}. Dangerous conditions. Secure loose objects.",
                metadata={"wind_speed": current.wind_speed}
            ))
        elif current.wind_speed >= self.HIGH_WIND_THRESHOLD:
            alerts.append(WeatherAlert(
                alert_type="high_wind",
                severity=RiskLevel.HIGH,
                title="💨 High Wind Alert",
                message=f"Strong winds of {current.wind_speed:.0f} km/h in {location_name}. Use caution outdoors.",
                metadata={"wind_speed": current.wind_speed}
            ))
        
        # Heavy rain alerts
        if current.precipitation >= self.VERY_HEAVY_RAIN_THRESHOLD:
            alerts.append(WeatherAlert(
                alert_type="heavy_rain",
                severity=RiskLevel.VERY_HIGH,
                title="🌧️ Heavy Rain Warning",
                message=f"Very heavy rainfall of {current.precipitation:.1f}mm/hour in {location_name}. Flooding possible. Avoid travel.",
                metadata={"precipitation": current.precipitation}
            ))
        elif current.precipitation >= self.HEAVY_RAIN_THRESHOLD:
            alerts.append(WeatherAlert(
                alert_type="heavy_rain",
                severity=RiskLevel.HIGH,
                title="🌧️ Heavy Rain Alert",
                message=f"Heavy rain of {current.precipitation:.1f}mm/hour in {location_name}. Reduced visibility. Drive carefully.",
                metadata={"precipitation": current.precipitation}
            ))
        
        # Air quality alerts
        if air_quality:
            if air_quality.aqi >= self.VERY_POOR_AQI_THRESHOLD:
                alerts.append(WeatherAlert(
                    alert_type="poor_aqi",
                    severity=RiskLevel.VERY_HIGH,
                    title="😷 Hazardous Air Quality",
                    message=f"Very poor air quality (AQI: {air_quality.aqi}) in {location_name}. Avoid outdoor activities. Health risk for all groups.",
                    metadata={"aqi": air_quality.aqi, "category": air_quality.category}
                ))
            elif air_quality.aqi >= self.POOR_AQI_THRESHOLD:
                alerts.append(WeatherAlert(
                    alert_type="poor_aqi",
                    severity=RiskLevel.HIGH,
                    title="😷 Poor Air Quality Alert",
                    message=f"Unhealthy air quality (AQI: {air_quality.aqi}) in {location_name}. Sensitive groups should limit outdoor exposure.",
                    metadata={"aqi": air_quality.aqi, "category": air_quality.category}
                ))
        
        # UV Index alert
        if current.uv_index >= self.HIGH_UV_THRESHOLD:
            alerts.append(WeatherAlert(
                alert_type="high_uv",
                severity=RiskLevel.HIGH,
                title="☀️ High UV Index Alert",
                message=f"Very high UV index of {current.uv_index:.1f} in {location_name}. Sun protection essential. Avoid midday sun.",
                metadata={"uv_index": current.uv_index}
            ))
        
        # Check hourly forecast for upcoming conditions
        if weather.hourly and len(weather.hourly) >= 6:
            upcoming_alerts = self._check_upcoming_conditions(weather.hourly[:6], location_name)
            alerts.extend(upcoming_alerts)
        
        return alerts
    
    def _check_upcoming_conditions(
        self,
        hourly_forecast: list,
        location_name: str
    ) -> List[WeatherAlert]:
        """Check next 6 hours for developing severe weather."""
        alerts = []
        
        # Check for developing heavy rain
        max_precip = max([h.precipitation for h in hourly_forecast])
        if max_precip >= self.HEAVY_RAIN_THRESHOLD:
            hours_until = next((i for i, h in enumerate(hourly_forecast) 
                               if h.precipitation >= self.HEAVY_RAIN_THRESHOLD), 0)
            alerts.append(WeatherAlert(
                alert_type="heavy_rain",
                severity=RiskLevel.MODERATE,
                title="🌧️ Heavy Rain Expected",
                message=f"Heavy rain expected in {hours_until} hours in {location_name}. Plan accordingly.",
                metadata={"hours_until": hours_until, "max_precipitation": max_precip}
            ))
        
        # Check for temperature drops (rapid cold front)
        temp_change = hourly_forecast[-1].temperature - hourly_forecast[0].temperature
        if temp_change <= -10:
            alerts.append(WeatherAlert(
                alert_type="temperature_change",
                severity=RiskLevel.MODERATE,
                title="❄️ Rapid Temperature Drop Expected",
                message=f"Temperature will drop by {abs(temp_change):.1f}°C in next 6 hours in {location_name}. Dress warmly.",
                metadata={"temperature_change": temp_change}
            ))
        
        return alerts
    
    async def get_active_alerts(
        self,
        user_id: int,
        db: AsyncSession
    ) -> List[dict]:
        """
        Get active alerts for a user from database.
        """
        try:
            query = f"""
                SELECT id, alert_type, severity, title, message, metadata, created_at, expires_at
                FROM weather_alerts
                WHERE user_id = {user_id}
                  AND is_dismissed = FALSE
                  AND (expires_at IS NULL OR expires_at > NOW())
                ORDER BY created_at DESC
                LIMIT 50
            """
            result = await db.execute(query)
            rows = result.fetchall()
            
            alerts = []
            for row in rows:
                alerts.append({
                    "id": row[0],
                    "alert_type": row[1],
                    "severity": row[2],
                    "title": row[3],
                    "message": row[4],
                    "metadata": row[5],
                    "created_at": row[6].isoformat() if row[6] else None,
                    "expires_at": row[7].isoformat() if row[7] else None
                })
            
            return alerts
        except Exception as e:
            print(f"Error fetching alerts: {e}")
            return []
    
    async def save_alert(
        self,
        user_id: int,
        alert: WeatherAlert,
        location_id: Optional[int],
        db: AsyncSession
    ):
        """
        Save an alert to the database.
        """
        try:
            import json
            
            query = f"""
                INSERT INTO weather_alerts 
                (user_id, location_id, alert_type, severity, title, message, metadata, expires_at)
                VALUES 
                ({user_id}, {location_id or 'NULL'}, '{alert.alert_type}', '{alert.severity.value}', 
                 '{alert.title}', '{alert.message}', '{json.dumps(alert.metadata)}'::jsonb, 
                 '{alert.expires_at.isoformat()}')
            """
            await db.execute(query)
            await db.commit()
        except Exception as e:
            await db.rollback()
            print(f"Error saving alert: {e}")
    
    async def dismiss_alert(
        self,
        alert_id: int,
        user_id: int,
        db: AsyncSession
    ):
        """
        Dismiss an alert.
        """
        try:
            query = f"""
                UPDATE weather_alerts
                SET is_dismissed = TRUE
                WHERE id = {alert_id} AND user_id = {user_id}
            """
            await db.execute(query)
            await db.commit()
        except Exception as e:
            await db.rollback()
            print(f"Error dismissing alert: {e}")
