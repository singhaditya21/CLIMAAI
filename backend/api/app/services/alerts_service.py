"""
Severe Weather Alerts Service - NWS API Integration.
Provides real-time severe weather alerts for US locations.
"""
import httpx
import json
from typing import Any, Dict, List, Optional
from datetime import datetime, timezone
from enum import Enum
import redis.asyncio as redis
from pydantic import BaseModel
from ..config import get_settings

settings = get_settings()


def _as_aware(value: datetime) -> datetime:
    """Treat a naive datetime as UTC so comparisons never mix the two kinds."""
    return value if value.tzinfo else value.replace(tzinfo=timezone.utc)


class AlertSeverity(str, Enum):
    """NWS Alert severity levels."""
    EXTREME = "Extreme"
    SEVERE = "Severe"
    MODERATE = "Moderate"
    MINOR = "Minor"
    UNKNOWN = "Unknown"


class AlertUrgency(str, Enum):
    """NWS Alert urgency levels."""
    IMMEDIATE = "Immediate"
    EXPECTED = "Expected"
    FUTURE = "Future"
    PAST = "Past"
    UNKNOWN = "Unknown"


class AlertCertainty(str, Enum):
    """NWS Alert certainty levels."""
    OBSERVED = "Observed"
    LIKELY = "Likely"
    POSSIBLE = "Possible"
    UNLIKELY = "Unlikely"
    UNKNOWN = "Unknown"


class WeatherAlert(BaseModel):
    """Single weather alert."""
    id: str
    event: str  # "Tornado Warning", "Flash Flood Watch", etc.
    headline: str
    description: str
    instruction: Optional[str]  # What to do
    severity: AlertSeverity
    urgency: AlertUrgency
    certainty: AlertCertainty
    onset: Optional[datetime]  # When it starts
    expires: Optional[datetime]  # When it ends
    sender: str  # NWS office
    areas: List[str]  # Affected areas
    
    @property
    def is_active(self) -> bool:
        """Check if alert is currently active.

        onset/expires are parsed from NWS timestamps and carry an offset, so the
        comparison has to be timezone-aware. With datetime.now(timezone.utc) this raised
        "can't compare offset-naive and offset-aware datetimes" — but only for
        locations that actually had alerts, so it stayed hidden anywhere the feed
        came back empty.
        """
        now = datetime.now(timezone.utc)
        if self.expires and _as_aware(self.expires) < now:
            return False
        if self.onset and _as_aware(self.onset) > now:
            return False
        return True
    
    @property
    def priority_score(self) -> int:
        """Calculate priority score for sorting (higher = more urgent)."""
        severity_scores = {
            AlertSeverity.EXTREME: 100,
            AlertSeverity.SEVERE: 75,
            AlertSeverity.MODERATE: 50,
            AlertSeverity.MINOR: 25,
            AlertSeverity.UNKNOWN: 10
        }
        urgency_scores = {
            AlertUrgency.IMMEDIATE: 100,
            AlertUrgency.EXPECTED: 75,
            AlertUrgency.FUTURE: 50,
            AlertUrgency.PAST: 0,
            AlertUrgency.UNKNOWN: 10
        }
        return severity_scores.get(self.severity, 0) + urgency_scores.get(self.urgency, 0)


class AlertsResponse(BaseModel):
    """Response containing weather alerts."""
    alerts: List[WeatherAlert]
    # Point lookups pass {"latitude": .., "longitude": ..}; the state
    # endpoint passes {"state": "NY"}, so this cannot be Dict[str, float].
    location: Dict[str, Any]
    updated: datetime
    total_count: int
    
    @property
    def has_severe(self) -> bool:
        """Check if any severe or extreme alerts."""
        return any(
            a.severity in [AlertSeverity.SEVERE, AlertSeverity.EXTREME]
            for a in self.alerts
        )


class AlertsService:
    """
    Service for fetching severe weather alerts from NWS API.
    
    NWS API is free and provides:
    - Real-time weather alerts
    - Alert geometry (affected areas)
    - Alert history (past 7 days)
    
    US only - for international, would need different sources.
    """
    
    NWS_API = "https://api.weather.gov"
    USER_AGENT = "ClimaAI/1.0 (contact@climaai.com)"  # Required by NWS
    
    def __init__(self):
        self.redis_client: Optional[redis.Redis] = None
        self.http_client = httpx.AsyncClient(
            timeout=30.0,
            headers={"User-Agent": self.USER_AGENT}
        )
    
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
    
    def _parse_datetime(self, dt_str: Optional[str]) -> Optional[datetime]:
        """Parse ISO datetime string."""
        if not dt_str:
            return None
        try:
            # Handle Z suffix
            if dt_str.endswith('Z'):
                dt_str = dt_str[:-1] + '+00:00'
            return datetime.fromisoformat(dt_str)
        except (ValueError, TypeError):
            return None
    
    def _parse_alert(self, feature: dict) -> Optional[WeatherAlert]:
        """Parse a GeoJSON feature into a WeatherAlert."""
        try:
            props = feature.get("properties", {})
            
            # Parse areas affected
            areas = []
            if props.get("areaDesc"):
                areas = [a.strip() for a in props["areaDesc"].split(";")]
            
            return WeatherAlert(
                id=props.get("id", ""),
                event=props.get("event", "Unknown"),
                headline=props.get("headline", ""),
                description=props.get("description", ""),
                instruction=props.get("instruction"),
                severity=AlertSeverity(props.get("severity", "Unknown")),
                urgency=AlertUrgency(props.get("urgency", "Unknown")),
                certainty=AlertCertainty(props.get("certainty", "Unknown")),
                onset=self._parse_datetime(props.get("onset")),
                expires=self._parse_datetime(props.get("expires")),
                sender=props.get("senderName", "NWS"),
                areas=areas
            )
        except Exception as e:
            print(f"Error parsing alert: {e}")
            return None
    
    async def get_alerts_by_point(
        self,
        latitude: float,
        longitude: float
    ) -> AlertsResponse:
        """
        Get active weather alerts for a specific location.
        
        Args:
            latitude: Location latitude
            longitude: Location longitude
            
        Returns:
            AlertsResponse with active alerts
        """
        # Check cache first (valid for 2 minutes)
        cache_key = f"alerts:{latitude:.2f}:{longitude:.2f}"
        
        try:
            redis_client = await self._get_redis()
            cached = await redis_client.get(cache_key)
            if cached:
                data = json.loads(cached)
                return AlertsResponse(**data)
        except Exception as e:
            print(f"Redis cache error: {e}")
        
        # Fetch from NWS API
        url = f"{self.NWS_API}/alerts/active"
        params = {
            "point": f"{latitude},{longitude}",
            "status": "actual",
            "message_type": "alert,update"
        }
        
        response = await self.http_client.get(url, params=params)
        if response.status_code == 400:
            # The NWS only covers the United States and its territories, and
            # returns 400 for a point outside them. That is a legitimate "no
            # alerts here", not a server error.
            return AlertsResponse(
                alerts=[],
                location={"latitude": latitude, "longitude": longitude},
                updated=datetime.now(timezone.utc),
                total_count=0,
            )
        response.raise_for_status()
        data = response.json()

        # Parse alerts
        alerts = []
        for feature in data.get("features", []):
            alert = self._parse_alert(feature)
            if alert and alert.is_active:
                alerts.append(alert)
        
        # Sort by priority (most urgent first)
        alerts.sort(key=lambda a: a.priority_score, reverse=True)
        
        result = AlertsResponse(
            alerts=alerts,
            location={"latitude": latitude, "longitude": longitude},
            updated=datetime.now(timezone.utc),
            total_count=len(alerts)
        )
        
        # Cache for 2 minutes
        try:
            redis_client = await self._get_redis()
            cache_data = result.model_dump(mode="json")
            await redis_client.setex(cache_key, 120, json.dumps(cache_data, default=str))
        except Exception as e:
            print(f"Redis cache set error: {e}")
        
        return result
    
    async def get_alerts_by_state(self, state: str) -> AlertsResponse:
        """
        Get active weather alerts for a US state.
        
        Args:
            state: Two-letter state code (e.g., "NY", "CA")
            
        Returns:
            AlertsResponse with active alerts for the state
        """
        url = f"{self.NWS_API}/alerts/active/area/{state.upper()}"
        
        response = await self.http_client.get(url)
        response.raise_for_status()
        data = response.json()
        
        alerts = []
        for feature in data.get("features", []):
            alert = self._parse_alert(feature)
            if alert and alert.is_active:
                alerts.append(alert)
        
        alerts.sort(key=lambda a: a.priority_score, reverse=True)
        
        return AlertsResponse(
            alerts=alerts,
            location={"state": state},
            updated=datetime.now(timezone.utc),
            total_count=len(alerts)
        )
    
    async def check_should_notify(
        self,
        latitude: float,
        longitude: float,
        min_severity: AlertSeverity = AlertSeverity.MODERATE
    ) -> Optional[WeatherAlert]:
        """
        Check if there's an alert worth notifying about.
        
        Returns the highest priority alert that meets severity threshold,
        or None if no alerts warrant notification.
        """
        alerts = await self.get_alerts_by_point(latitude, longitude)
        
        severity_order = [
            AlertSeverity.EXTREME,
            AlertSeverity.SEVERE,
            AlertSeverity.MODERATE,
            AlertSeverity.MINOR
        ]
        
        min_index = severity_order.index(min_severity) if min_severity in severity_order else 3
        
        for alert in alerts.alerts:
            if alert.severity in severity_order[:min_index + 1]:
                return alert
        
        return None


_alerts_service: Optional[AlertsService] = None


def get_alerts_service() -> AlertsService:
    """Get the global AlertsService instance."""
    global _alerts_service
    if _alerts_service is None:
        _alerts_service = AlertsService()
    return _alerts_service


async def close_alerts_service():
    """Close the global AlertsService instance."""
    global _alerts_service
    if _alerts_service:
        await _alerts_service.close()
        _alerts_service = None
