"""
Multi-Source Weather Service
Integrates 18+ external weather APIs to provide aggregated weather data.

Rate Limits (enforced per-source, per-day):
  - Open-Meteo:      10,000/day (free tier), 600/min → budget 9,000/day
  - MET Norway:      20 req/sec → budget 5,000/day (be respectful)
  - NWS:             "reasonable use" → budget 2,000/day
  - 7Timer!:         no official limit → budget 500/day (conservative)
  - DWD/Bright Sky:  no explicit limit → budget 5,000/day
  - OpenWeatherMap:   1,000/day (free key)
  - Weatherbit:       500/day (free key)
  - OpenUV:           500/day (free key)
  - Stormglass:       10/day (free key)
  - Meteoblue:        1,000/day (free key)
  - NOAA:             unlimited → budget 2,000/day

All responses are cached in-memory with configurable TTL (5-30 min) to
minimize redundant calls for the same lat/lon.
"""

import httpx
import asyncio
import logging
import time
import hashlib
from typing import Optional, Dict, Any, List
from datetime import datetime, timedelta
from collections import defaultdict

logger = logging.getLogger(__name__)

# Default timeout for all API calls (seconds)
API_TIMEOUT = 10.0

# ============================================================================
# Rate Limit Configuration (daily budget per source)
# ============================================================================
SOURCE_DAILY_LIMITS = {
    "open_meteo":      9_000,  # official: 10,000/day
    "met_norway":      5_000,  # 20 req/sec but be polite
    "nws":             2_000,  # "reasonable use"
    "7timer":            500,  # no official limit; conservative
    "dwd":             5_000,  # no explicit limit
    "openweathermap":  1_000,  # free tier
    "weatherbit":        500,  # free tier
    "openuv":            500,  # free tier
    "stormglass":         10,  # free tier is tiny
    "meteoblue":       1_000,  # free tier
    "noaa":            2_000,  # unlimited but be polite
}

# Cache TTL per source (seconds) — higher for slow-updating sources
SOURCE_CACHE_TTL = {
    "open_meteo":       300,   # 5 min
    "met_norway":       600,   # 10 min
    "nws":              300,   # 5 min  (alerts change fast)
    "7timer":          1800,   # 30 min (updates every 6h)
    "dwd":              600,   # 10 min
    "openweathermap":   600,   # 10 min
    "weatherbit":       600,   # 10 min
    "openuv":          1800,   # 30 min (UV is solar-cycle)
    "stormglass":      1800,   # 30 min (precious quota)
    "meteoblue":        600,   # 10 min
    "noaa":             600,   # 10 min
}

# Minimum seconds between successive calls to the SAME source
SOURCE_MIN_INTERVAL = {
    "open_meteo":        0.1,  # 600/min ≈ 10/sec
    "met_norway":        0.05, # 20/sec
    "nws":               1.0,  # be polite
    "7timer":            2.0,  # conservative
    "dwd":               0.5,
    "openweathermap":    1.0,
    "weatherbit":        1.0,
    "openuv":            2.0,
    "stormglass":       10.0,  # very limited quota
    "meteoblue":         1.0,
    "noaa":              1.0,
}


class _RateLimiter:
    """
    In-memory per-source daily counter + minimum interval enforcer.
    Resets automatically at midnight UTC.
    """
    def __init__(self):
        self._counts: Dict[str, int] = defaultdict(int)
        self._last_call: Dict[str, float] = defaultdict(float)
        self._reset_date: str = self._today()

    @staticmethod
    def _today() -> str:
        return datetime.utcnow().strftime("%Y-%m-%d")

    def _maybe_reset(self):
        today = self._today()
        if today != self._reset_date:
            self._counts.clear()
            self._reset_date = today

    def is_allowed(self, source: str) -> bool:
        """Return True if the source has budget remaining today."""
        self._maybe_reset()
        limit = SOURCE_DAILY_LIMITS.get(source, 1_000)
        return self._counts[source] < limit

    async def wait_and_record(self, source: str):
        """Wait for minimum interval, then record a call."""
        min_interval = SOURCE_MIN_INTERVAL.get(source, 1.0)
        elapsed = time.monotonic() - self._last_call[source]
        if elapsed < min_interval:
            await asyncio.sleep(min_interval - elapsed)

        self._maybe_reset()
        self._counts[source] += 1
        self._last_call[source] = time.monotonic()

    def get_usage(self) -> Dict[str, Dict[str, int]]:
        """Return current usage stats for all sources."""
        self._maybe_reset()
        return {
            source: {
                "used": self._counts.get(source, 0),
                "limit": limit,
                "remaining": limit - self._counts.get(source, 0),
            }
            for source, limit in SOURCE_DAILY_LIMITS.items()
        }


class _ResponseCache:
    """
    Simple in-memory cache keyed by (source, rounded_lat, rounded_lon).
    Coordinates are rounded to 2 decimal places (~1 km) to maximise hits.
    """
    def __init__(self):
        self._store: Dict[str, tuple] = {}   # key → (data, expires_at)

    @staticmethod
    def _key(source: str, lat: float, lon: float) -> str:
        rlat = round(lat, 2)
        rlon = round(lon, 2)
        return f"{source}:{rlat}:{rlon}"

    def get(self, source: str, lat: float, lon: float) -> Optional[Dict]:
        key = self._key(source, lat, lon)
        entry = self._store.get(key)
        if entry is None:
            return None
        data, expires = entry
        if time.monotonic() > expires:
            del self._store[key]
            return None
        return data

    def put(self, source: str, lat: float, lon: float, data: Dict):
        key = self._key(source, lat, lon)
        ttl = SOURCE_CACHE_TTL.get(source, 600)
        self._store[key] = (data, time.monotonic() + ttl)

    def clear_expired(self):
        now = time.monotonic()
        expired = [k for k, (_, exp) in self._store.items() if now > exp]
        for k in expired:
            del self._store[k]


# Module-level singletons (shared across requests within the same process)
_rate_limiter = _RateLimiter()
_response_cache = _ResponseCache()


class MultiSourceWeatherService:
    """Aggregates weather data from multiple external APIs."""
    
    def __init__(self, config: Dict[str, str] = None):
        """
        Initialize with API keys from config.
        Keys not provided will skip those APIs gracefully.
        """
        self.config = config or {}
        self._client = None
        self.rate_limiter = _rate_limiter
        self.cache = _response_cache
    
    @property
    def client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(timeout=API_TIMEOUT)
        return self._client
    
    async def close(self):
        if self._client and not self._client.is_closed:
            await self._client.aclose()
    
    # =========================================================================
    # Multi-source aggregated fetch
    # =========================================================================
    
    async def get_multi_source_weather(
        self,
        latitude: float,
        longitude: float,
        sources: Optional[List[str]] = None
    ) -> Dict[str, Any]:
        """
        Fetch weather from multiple sources and aggregate results.
        If 'sources' is specified, only fetch from those APIs.
        """
        all_sources = {
            "open_meteo": self._fetch_open_meteo,
            "7timer": self._fetch_7timer,
            "met_norway": self._fetch_met_norway,
            "nws": self._fetch_nws,
            "openweathermap": self._fetch_openweathermap,
            "weatherbit": self._fetch_weatherbit,
            "stormglass": self._fetch_stormglass,
            "openuv": self._fetch_openuv,
            "dwd": self._fetch_dwd,
        }
        
        # Filter to requested sources
        if sources:
            fetch_sources = {k: v for k, v in all_sources.items() if k in sources}
        else:
            fetch_sources = all_sources
        
        # Fetch all in parallel (cache + rate-limit aware)
        tasks = {}
        for name, fetcher in fetch_sources.items():
            tasks[name] = asyncio.create_task(
                self._safe_fetch(name, fetcher, latitude, longitude)
            )
        
        results = {}
        available_sources = []
        cache_hits = []
        rate_limited = []
        for name, task in tasks.items():
            result = await task
            if result is not None:
                results[name] = result
                available_sources.append(name)
                if result.get("_from_cache"):
                    cache_hits.append(name)
            elif not self.rate_limiter.is_allowed(name):
                rate_limited.append(name)
        
        # Aggregate into unified response
        return {
            "latitude": latitude,
            "longitude": longitude,
            "sources": available_sources,
            "current": self._aggregate_current(results),
            "forecast": self._aggregate_forecast(results),
            "uv_index": results.get("openuv"),
            "marine": results.get("stormglass"),
            "metadata": {
                "fetched_at": datetime.utcnow().isoformat(),
                "source_count": len(available_sources),
                "total_sources_available": len(all_sources),
                "cache_hits": cache_hits,
                "rate_limited_sources": rate_limited,
                "usage": self.rate_limiter.get_usage()
            }
        }
    
    async def _safe_fetch(self, name: str, fetcher, lat: float, lon: float) -> Optional[Dict]:
        """
        Wraps a fetcher with:
        1. Cache lookup (return cached data if fresh)
        2. Rate-limit check (skip if daily budget exhausted)
        3. Min-interval wait (throttle burst calls to same source)
        4. Exception catching (return None on failure)
        """
        # 1. Check cache first
        cached = self.cache.get(name, lat, lon)
        if cached is not None:
            cached["_from_cache"] = True
            logger.debug(f"Cache hit for {name} at ({lat},{lon})")
            return cached
        
        # 2. Check rate limit
        if not self.rate_limiter.is_allowed(name):
            logger.warning(f"Rate limit exhausted for {name} (daily budget used up)")
            return None
        
        # 3. Throttle and fetch
        try:
            await self.rate_limiter.wait_and_record(name)
            data = await fetcher(lat, lon)
            if data is not None:
                data["_from_cache"] = False
                self.cache.put(name, lat, lon, data)
            return data
        except Exception as e:
            logger.warning(f"Source '{name}' failed: {e}")
            return None
    
    def get_rate_limit_usage(self) -> Dict[str, Any]:
        """Return current rate limit usage stats for all sources."""
        return self.rate_limiter.get_usage()
    
    # =========================================================================
    # Individual API Fetchers
    # =========================================================================
    
    async def _fetch_open_meteo(self, lat: float, lon: float) -> Dict:
        """Open-Meteo (free, no key). Primary weather source."""
        url = "https://api.open-meteo.com/v1/forecast"
        params = {
            "latitude": lat,
            "longitude": lon,
            "current": "temperature_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m,pressure_msl,weather_code",
            "daily": "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code",
            "forecast_days": 7,
            "timezone": "auto"
        }
        resp = await self.client.get(url, params=params)
        resp.raise_for_status()
        data = resp.json()
        
        current = data.get("current", {})
        return {
            "source": "open_meteo",
            "current": {
                "temperature": current.get("temperature_2m"),
                "humidity": current.get("relative_humidity_2m"),
                "wind_speed": current.get("wind_speed_10m"),
                "wind_direction": current.get("wind_direction_10m"),
                "pressure": current.get("pressure_msl"),
                "weather_code": current.get("weather_code"),
            },
            "daily": data.get("daily", {})
        }
    
    async def _fetch_7timer(self, lat: float, lon: float) -> Dict:
        """7Timer! (free, no key). Astronomy and weather forecasting."""
        url = f"https://www.7timer.info/bin/api.pl"
        params = {
            "lon": lon,
            "lat": lat,
            "product": "civil",
            "output": "json"
        }
        resp = await self.client.get(url, params=params)
        resp.raise_for_status()
        data = resp.json()
        
        series = data.get("dataseries", [])
        current_data = series[0] if series else {}
        
        return {
            "source": "7timer",
            "current": {
                "temperature": current_data.get("temp2m"),
                "humidity": current_data.get("rh2m"),
                "wind_speed": current_data.get("wind10m", {}).get("speed") if isinstance(current_data.get("wind10m"), dict) else None,
                "weather_description": current_data.get("weather"),
            },
            "forecast_hours": len(series)
        }
    
    async def _fetch_met_norway(self, lat: float, lon: float) -> Dict:
        """Meteorologisk Institutt / MET Norway (free, no key)."""
        url = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        headers = {"User-Agent": "ClimaAI/1.0 (climaai.app)"}
        params = {"lat": lat, "lon": lon}
        
        resp = await self.client.get(url, params=params, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        
        timeseries = data.get("properties", {}).get("timeseries", [])
        current = timeseries[0] if timeseries else {}
        instant = current.get("data", {}).get("instant", {}).get("details", {})
        
        return {
            "source": "met_norway",
            "current": {
                "temperature": instant.get("air_temperature"),
                "humidity": instant.get("relative_humidity"),
                "wind_speed": instant.get("wind_speed"),
                "wind_direction": instant.get("wind_from_direction"),
                "pressure": instant.get("air_pressure_at_sea_level"),
            },
            "forecast_points": len(timeseries)
        }
    
    async def _fetch_nws(self, lat: float, lon: float) -> Dict:
        """National Weather Service (free, no key). US only."""
        url = f"https://api.weather.gov/alerts/active"
        headers = {"User-Agent": "ClimaAI/1.0", "Accept": "application/geo+json"}
        params = {"point": f"{lat},{lon}"}
        
        resp = await self.client.get(url, params=params, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        
        features = data.get("features", [])
        alerts = []
        for f in features[:10]:
            props = f.get("properties", {})
            alerts.append({
                "id": props.get("id"),
                "event": props.get("event", "Unknown"),
                "headline": props.get("headline"),
                "severity": props.get("severity", "Unknown"),
                "urgency": props.get("urgency"),
                "areas": props.get("areaDesc"),
                "description": props.get("description"),
                "instruction": props.get("instruction"),
                "onset": props.get("onset"),
                "expires": props.get("expires"),
            })
        
        return {
            "source": "nws",
            "alert_count": len(alerts),
            "alerts": alerts
        }
    
    async def _fetch_openweathermap(self, lat: float, lon: float) -> Optional[Dict]:
        """OpenWeatherMap (requires apiKey)."""
        api_key = self.config.get("OPENWEATHERMAP_API_KEY")
        if not api_key:
            return None
        
        url = "https://api.openweathermap.org/data/2.5/weather"
        params = {"lat": lat, "lon": lon, "appid": api_key, "units": "metric"}
        
        resp = await self.client.get(url, params=params)
        resp.raise_for_status()
        data = resp.json()
        
        main = data.get("main", {})
        wind = data.get("wind", {})
        weather_arr = data.get("weather", [{}])
        
        return {
            "source": "openweathermap",
            "current": {
                "temperature": main.get("temp"),
                "feels_like": main.get("feels_like"),
                "humidity": main.get("humidity"),
                "wind_speed": wind.get("speed"),
                "wind_direction": wind.get("deg"),
                "pressure": main.get("pressure"),
                "visibility": data.get("visibility"),
                "weather_description": weather_arr[0].get("description") if weather_arr else None,
            }
        }
    
    async def _fetch_weatherbit(self, lat: float, lon: float) -> Optional[Dict]:
        """Weatherbit (requires apiKey)."""
        api_key = self.config.get("WEATHERBIT_API_KEY")
        if not api_key:
            return None
        
        url = "https://api.weatherbit.io/v2.0/current"
        params = {"lat": lat, "lon": lon, "key": api_key, "units": "M"}
        
        resp = await self.client.get(url, params=params)
        resp.raise_for_status()
        data = resp.json()
        
        current = data.get("data", [{}])[0] if data.get("data") else {}
        
        return {
            "source": "weatherbit",
            "current": {
                "temperature": current.get("temp"),
                "feels_like": current.get("app_temp"),
                "humidity": current.get("rh"),
                "wind_speed": current.get("wind_spd"),
                "wind_direction": current.get("wind_dir"),
                "pressure": current.get("pres"),
                "visibility": current.get("vis"),
                "weather_description": current.get("weather", {}).get("description") if isinstance(current.get("weather"), dict) else None,
            }
        }
    
    async def _fetch_stormglass(self, lat: float, lon: float) -> Optional[Dict]:
        """Storm Glass / Stormglass (requires apiKey). Marine weather."""
        api_key = self.config.get("STORMGLASS_API_KEY")
        if not api_key:
            return None
        
        url = "https://api.stormglass.io/v2/weather/point"
        headers = {"Authorization": api_key}
        params = {
            "lat": lat,
            "lng": lon,
            "params": "waveHeight,waveDirection,wavePeriod,waterTemperature,seaLevelPressure,windWaveHeight,swellHeight"
        }
        
        resp = await self.client.get(url, params=params, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        
        hours = data.get("hours", [])
        current = hours[0] if hours else {}
        
        def get_val(field):
            val = current.get(field)
            if isinstance(val, list) and val:
                return val[0].get("value")
            elif isinstance(val, dict):
                # Use first available source
                for v in val.values():
                    return v
            return val
        
        return {
            "source": "stormglass",
            "wave_height": get_val("waveHeight"),
            "wave_direction": get_val("waveDirection"),
            "wave_period": get_val("wavePeriod"),
            "water_temperature": get_val("waterTemperature"),
            "sea_level_pressure": get_val("seaLevelPressure"),
            "wind_wave_height": get_val("windWaveHeight"),
            "swell_height": get_val("swellHeight"),
        }
    
    async def _fetch_openuv(self, lat: float, lon: float) -> Optional[Dict]:
        """OpenUV (requires apiKey). Real-time UV index."""
        api_key = self.config.get("OPENUV_API_KEY")
        if not api_key:
            return None
        
        url = "https://api.openuv.io/api/v1/uv"
        headers = {"x-access-token": api_key}
        params = {"lat": lat, "lng": lon}
        
        resp = await self.client.get(url, params=params, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        
        result = data.get("result", {})
        safe_exposure = result.get("safe_exposure_time", {})
        sun_info = result.get("sun_info", {})
        
        return {
            "source": "openuv",
            "uv_index": result.get("uv"),
            "uv_index_max": result.get("uv_max"),
            "safe_exposure_time": {k: v for k, v in safe_exposure.items() if v is not None} if safe_exposure else None,
            "sun_info": {
                "sunrise": sun_info.get("sun_times", {}).get("sunrise"),
                "sunset": sun_info.get("sun_times", {}).get("sunset"),
                "solar_noon": sun_info.get("sun_times", {}).get("solarNoon"),
            } if sun_info else None
        }
    
    async def _fetch_dwd(self, lat: float, lon: float) -> Optional[Dict]:
        """DWD (Deutscher Wetterdienst) API — German Weather Service (free, no key)."""
        # DWD Bright Sky API — third-party open wrapper for DWD data
        url = "https://api.brightsky.dev/current_weather"
        params = {"lat": lat, "lon": lon}
        
        try:
            resp = await self.client.get(url, params=params)
            resp.raise_for_status()
            data = resp.json()
            
            weather = data.get("weather", {})
            return {
                "source": "dwd",
                "current": {
                    "temperature": weather.get("temperature"),
                    "humidity": weather.get("relative_humidity"),
                    "wind_speed": weather.get("wind_speed"),
                    "wind_direction": weather.get("wind_direction"),
                    "pressure": weather.get("pressure_msl"),
                    "visibility": weather.get("visibility"),
                    "weather_description": weather.get("condition"),
                }
            }
        except Exception:
            return None
    
    # =========================================================================
    # Individual Endpoint Methods
    # =========================================================================
    
    async def get_uv_index(self, lat: float, lon: float) -> Optional[Dict]:
        """Get UV index from OpenUV."""
        return await self._safe_fetch("openuv", self._fetch_openuv, lat, lon)
    
    async def get_marine_weather(self, lat: float, lon: float) -> Optional[Dict]:
        """Get marine weather from Storm Glass."""
        return await self._safe_fetch("stormglass", self._fetch_stormglass, lat, lon)
    
    async def get_nws_alerts(self, lat: float, lon: float) -> Dict:
        """Get NWS alerts for coordinates."""
        result = await self._safe_fetch("nws", self._fetch_nws, lat, lon)
        if result is None:
            return {"latitude": lat, "longitude": lon, "alert_count": 0, "alerts": [], "source": "nws"}
        return {
            "latitude": lat,
            "longitude": lon,
            "alert_count": result.get("alert_count", 0),
            "alerts": result.get("alerts", []),
            "source": "nws"
        }
    
    async def get_nws_state_alerts(self, state: str) -> Dict:
        """Get NWS alerts for a US state."""
        try:
            url = f"https://api.weather.gov/alerts/active/area/{state}"
            headers = {"User-Agent": "ClimaAI/1.0", "Accept": "application/geo+json"}
            resp = await self.client.get(url, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            
            features = data.get("features", [])
            alerts = []
            for f in features[:20]:
                props = f.get("properties", {})
                alerts.append({
                    "id": props.get("id"),
                    "event": props.get("event", "Unknown"),
                    "headline": props.get("headline"),
                    "severity": props.get("severity", "Unknown"),
                    "urgency": props.get("urgency"),
                    "areas": props.get("areaDesc"),
                    "description": props.get("description"),
                    "instruction": props.get("instruction"),
                    "onset": props.get("onset"),
                    "expires": props.get("expires"),
                })
            
            return {"state": state, "alert_count": len(alerts), "alerts": alerts}
        except Exception as e:
            logger.error(f"NWS state alerts error: {e}")
            return {"state": state, "alert_count": 0, "alerts": []}
    
    async def get_historical_weather(
        self, lat: float, lon: float, start_date: str, end_date: str
    ) -> Dict:
        """Get historical weather from Open-Meteo Archive API."""
        try:
            url = "https://archive-api.open-meteo.com/v1/archive"
            params = {
                "latitude": lat,
                "longitude": lon,
                "start_date": start_date,
                "end_date": end_date,
                "daily": "temperature_2m_max,temperature_2m_min,precipitation_sum",
                "timezone": "auto"
            }
            resp = await self.client.get(url, params=params)
            resp.raise_for_status()
            data = resp.json()
            
            daily = data.get("daily", {})
            return {
                "dates": daily.get("time", []),
                "temperatures_max": daily.get("temperature_2m_max", []),
                "temperatures_min": daily.get("temperature_2m_min", []),
                "precipitation_sum": daily.get("precipitation_sum", []),
                "source": "open_meteo_archive"
            }
        except Exception as e:
            logger.error(f"Historical weather error: {e}")
            return {
                "dates": [], "temperatures_max": [], "temperatures_min": [],
                "precipitation_sum": [], "source": "open_meteo_archive"
            }
    
    # =========================================================================
    # Aggregation Helpers
    # =========================================================================
    
    def _aggregate_current(self, results: Dict[str, Dict]) -> Optional[Dict]:
        """Merge current weather from multiple sources, preferring Open-Meteo."""
        primary = None
        for source in ["open_meteo", "met_norway", "openweathermap", "weatherbit", "dwd", "7timer"]:
            if source in results and "current" in results[source]:
                primary = results[source]["current"].copy()
                primary["source"] = source
                break
        
        if primary is None:
            return None
        
        # Enrich with data from other sources where primary is missing
        for source, data in results.items():
            if "current" in data:
                for key, value in data["current"].items():
                    if key != "source" and (key not in primary or primary[key] is None):
                        primary[key] = value
        
        return primary
    
    def _aggregate_forecast(self, results: Dict[str, Dict]) -> Optional[List[Dict]]:
        """Merge daily forecasts from multiple sources."""
        # Use Open-Meteo daily data as the base
        if "open_meteo" in results and "daily" in results["open_meteo"]:
            daily = results["open_meteo"]["daily"]
            dates = daily.get("time", [])
            if not dates:
                return None
            
            return [
                {
                    "date": dates[i],
                    "temp_max": daily.get("temperature_2m_max", [None] * len(dates))[i],
                    "temp_min": daily.get("temperature_2m_min", [None] * len(dates))[i],
                    "precipitation_probability": daily.get("precipitation_probability_max", [None] * len(dates))[i],
                    "weather_description": str(daily.get("weather_code", [None] * len(dates))[i]),
                    "source": "open_meteo"
                }
                for i in range(len(dates))
            ]
        return None
