"""
Radar Service - Weather radar imagery from RainViewer API.
Provides radar tiles for map overlays and animation.
"""
import httpx
import json
from typing import Dict, List, Optional
from datetime import datetime
import redis.asyncio as redis
from pydantic import BaseModel
from ..config import get_settings

settings = get_settings()

_radar_service: Optional['RadarService'] = None


class RadarFrame(BaseModel):
    """Single radar frame metadata."""
    time: int  # Unix timestamp
    path: str  # Tile path template
    
    @property
    def datetime(self) -> datetime:
        return datetime.utcfromtimestamp(self.time)


class RadarResponse(BaseModel):
    """Radar frames response."""
    host: str  # Base URL for tiles
    generated: int  # When data was generated
    past: List[RadarFrame]  # Past radar frames (actual)
    nowcast: List[RadarFrame]  # Future prediction frames
    
    @property
    def all_frames(self) -> List[RadarFrame]:
        """All frames sorted by time."""
        return sorted(self.past + self.nowcast, key=lambda f: f.time)


class RadarService:
    """
    Service for fetching weather radar data from RainViewer.
    
    RainViewer provides:
    - Global radar coverage
    - Past 2 hours of actual radar
    - 30 minutes of nowcast (prediction)
    - Tiles in standard XYZ format for map overlay
    
    Tile URL format:
    {host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png
    
    Example:
    https://tilecache.rainviewer.com/v2/radar/1706695200/256/5/16/11/2/1_1.png
    """
    
    RAINVIEWER_API = "https://api.rainviewer.com/public/weather-maps.json"
    
    def __init__(self):
        self.redis_client: Optional[redis.Redis] = None
        self.http_client = httpx.AsyncClient(timeout=30.0)
    
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
    
    async def get_radar_frames(self) -> RadarResponse:
        """
        Get available radar frames from RainViewer.
        
        Returns metadata about available frames including:
        - Past frames (actual radar data, ~2 hours)
        - Nowcast frames (predicted, ~30 minutes)
        """
        # Check cache first (valid for 2 minutes)
        cache_key = "radar:frames"
        
        try:
            redis_client = await self._get_redis()
            cached = await redis_client.get(cache_key)
            if cached:
                data = json.loads(cached)
                return RadarResponse(**data)
        except Exception as e:
            print(f"Redis cache error: {e}")
        
        # Fetch from RainViewer API
        response = await self.http_client.get(self.RAINVIEWER_API)
        response.raise_for_status()
        data = response.json()
        
        # Parse response
        radar_data = data.get("radar", {})
        
        past_frames = [
            RadarFrame(time=f["time"], path=f["path"])
            for f in radar_data.get("past", [])
        ]
        
        nowcast_frames = [
            RadarFrame(time=f["time"], path=f["path"])
            for f in radar_data.get("nowcast", [])
        ]
        
        result = RadarResponse(
            host=data.get("host", "https://tilecache.rainviewer.com"),
            generated=data.get("generated", int(datetime.utcnow().timestamp())),
            past=past_frames,
            nowcast=nowcast_frames
        )
        
        # Cache for 2 minutes
        try:
            redis_client = await self._get_redis()
            cache_data = result.model_dump()
            await redis_client.setex(cache_key, 120, json.dumps(cache_data))
        except Exception as e:
            print(f"Redis cache set error: {e}")
        
        return result
    
    def get_tile_url(
        self,
        host: str,
        path: str,
        z: int,
        x: int,
        y: int,
        size: int = 256,
        color: int = 2,  # 0=black/white, 1=original, 2=noaa, 3=black, 4=white
        smooth: bool = True,
        snow: bool = True
    ) -> str:
        """
        Generate a radar tile URL.
        
        Args:
            host: Base URL from radar frames response
            path: Frame path from radar frames response
            z: Zoom level (0-12)
            x: Tile X coordinate
            y: Tile Y coordinate
            size: Tile size (256 or 512)
            color: Color scheme (0-4)
            smooth: Apply smoothing
            snow: Show snow in blue
        
        Returns:
            Full URL to the radar tile PNG
        """
        options = f"{1 if smooth else 0}_{1 if snow else 0}"
        return f"{host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png"
    
    async def get_tile(
        self,
        path: str,
        z: int,
        x: int,
        y: int,
        size: int = 256,
        color: int = 2
    ) -> bytes:
        """
        Fetch a radar tile image.
        
        Returns PNG bytes for the requested tile.
        Tiles are cached for 5 minutes.
        """
        # Get current frames to get host
        frames = await self.get_radar_frames()
        
        # Build tile URL
        tile_url = self.get_tile_url(
            host=frames.host,
            path=path,
            z=z, x=x, y=y,
            size=size,
            color=color
        )
        
        # Check cache
        cache_key = f"radar:tile:{path}:{z}:{x}:{y}"
        
        try:
            redis_client = await self._get_redis()
            cached = await redis_client.get(cache_key)
            if cached:
                return cached
        except Exception as e:
            print(f"Redis cache error: {e}")
        
        # Fetch tile
        response = await self.http_client.get(tile_url)
        response.raise_for_status()
        tile_data = response.content
        
        # Cache tile for 5 minutes
        try:
            redis_client = await self._get_redis()
            await redis_client.setex(cache_key, 300, tile_data)
        except Exception as e:
            print(f"Redis cache set error: {e}")
        
        return tile_data


def get_radar_service() -> RadarService:
    """Get the global RadarService instance."""
    global _radar_service
    if _radar_service is None:
        _radar_service = RadarService()
    return _radar_service


async def close_radar_service():
    """Close the global RadarService instance."""
    global _radar_service
    if _radar_service:
        await _radar_service.close()
        _radar_service = None
