"""
Location search and management router.
"""
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
import httpx
from ..models import User
from ..services.auth import get_current_user
from ..database import get_db
from ..config import get_settings

settings = get_settings()

router = APIRouter(prefix="/api/locations", tags=["locations"])


# Simple model for database storage (not using ORM for this simple table)
class FavoriteLocation:
    """Schema for favorite locations"""
    def __init__(self, id: int, user_id: int, name: str, latitude: float,
                 longitude: float, is_default: bool = False):
        self.id = id
        self.user_id = user_id
        self.name = name
        self.latitude = latitude
        self.longitude = longitude
        self.is_default = is_default


@router.get("/search")
async def search_locations(
    query: str = Query(..., min_length=2, max_length=100),
    limit: int = Query(10, ge=1, le=50),
    current_user: User = Depends(get_current_user),
):
    """
    Search locations using Open-Meteo Geocoding API.
    
    Returns location suggestions with coordinates.
    """
    try:
        # Routed through the licence helper like every other Open-Meteo call:
        # with a commercial key configured this becomes the customer- host.
        geo_url, geo_params = get_settings().open_meteo_request(
            "https://geocoding-api.open-meteo.com/v1/search",
            {
                "name": query,
                "count": limit,
                "language": "en",
                "format": "json",
            },
        )
        async with httpx.AsyncClient() as client:
            response = await client.get(
                geo_url,
                params=geo_params,
                timeout=10.0
            )
            response.raise_for_status()
            data = response.json()
            
            results = data.get("results", [])
            
            # Format results
            locations = []
            for result in results:
                location = {
                    "name": result.get("name"),
                    "latitude": result.get("latitude"),
                    "longitude": result.get("longitude"),
                    "country": result.get("country"),
                    "admin1": result.get("admin1"),  # State/Province
                    "timezone": result.get("timezone"),
                    "display_name": f"{result.get('name')}, {result.get('admin1', '')}, {result.get('country', '')}".strip(", ")
                }
                locations.append(location)
            
            return {
                "query": query,
                "count": len(locations),
                "results": locations
            }
            
    except httpx.HTTPError as e:
        raise HTTPException(status_code=503, detail=f"Location search service unavailable: {str(e)}")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/favorites")
async def get_favorite_locations(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Get user's saved favorite locations.
    """
    try:
        # Manual query since we're not using full ORM models for this table
        # Using text() with parameters prevents SQL injection and ensures valid SQL for UUIDs
        stmt = text("""
            SELECT id, name, latitude, longitude, is_default, created_at
            FROM favorite_locations
            WHERE user_id = :user_id
            ORDER BY is_default DESC, created_at DESC
        """)
        result = await db.execute(stmt, {"user_id": current_user.id})
        rows = result.fetchall()
        
        favorites = []
        for row in rows:
            favorites.append({
                "id": row[0],
                "name": row[1],
                "latitude": float(row[2]),
                "longitude": float(row[3]),
                "is_default": row[4],
                "created_at": row[5].isoformat() if row[5] else None
            })
        
        return {
            "count": len(favorites),
            "favorites": favorites
        }
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/favorites")
async def add_favorite_location(
    name: str = Query(..., min_length=1, max_length=255),
    latitude: float = Query(..., ge=-90, le=90),
    longitude: float = Query(..., ge=-180, le=180),
    is_default: bool = Query(False),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Add a location to favorites.
    """
    try:
        # If setting as default, unset other defaults first
        if is_default:
            await db.execute(
                text("""
                UPDATE favorite_locations
                SET is_default = FALSE
                WHERE user_id = :user_id
                """),
                {"user_id": current_user.id}
            )

        # Insert new favorite
        stmt = text("""
            INSERT INTO favorite_locations (user_id, name, latitude, longitude, is_default)
            VALUES (:user_id, :name, :latitude, :longitude, :is_default)
            ON CONFLICT (user_id, latitude, longitude)
            DO UPDATE SET name = EXCLUDED.name, is_default = EXCLUDED.is_default
            RETURNING id, name, latitude, longitude, is_default, created_at
        """)
        result = await db.execute(stmt, {
            "user_id": current_user.id,
            "name": name,
            "latitude": latitude,
            "longitude": longitude,
            "is_default": is_default,
        })
        await db.commit()
        
        row = result.fetchone()
        
        return {
            "message": "Location added to favorites",
            "location": {
                "id": row[0],
                "name": row[1],
                "latitude": float(row[2]),
                "longitude": float(row[3]),
                "is_default": row[4],
                "created_at": row[5].isoformat() if row[5] else None
            }
        }
        
    except Exception as e:
        await db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.delete("/favorites/{location_id}")
async def delete_favorite_location(
    location_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Remove a location from favorites.
    """
    try:
        stmt = text("""
            DELETE FROM favorite_locations
            WHERE id = :location_id AND user_id = :user_id
            RETURNING id
        """)
        result = await db.execute(
            stmt, {"location_id": location_id, "user_id": current_user.id}
        )
        await db.commit()
        
        if result.rowcount == 0:
            raise HTTPException(status_code=404, detail="Favorite location not found")
        
        return {"message": "Location removed from favorites"}
        
    except HTTPException:
        raise
    except Exception as e:
        await db.rollback()
        raise HTTPException(status_code=500, detail=str(e))


@router.patch("/favorites/{location_id}/default")
async def set_default_location(
    location_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Set a favorite location as the default.
    """
    try:
        # Unset all defaults
        await db.execute(
            text("""
            UPDATE favorite_locations
            SET is_default = FALSE
            WHERE user_id = :user_id
            """),
            {"user_id": current_user.id}
        )

        # Set new default
        stmt = text("""
            UPDATE favorite_locations
            SET is_default = TRUE
            WHERE id = :location_id AND user_id = :user_id
            RETURNING id
        """)
        result = await db.execute(
            stmt, {"location_id": location_id, "user_id": current_user.id}
        )
        await db.commit()
        
        if result.rowcount == 0:
            raise HTTPException(status_code=404, detail="Favorite location not found")
        
        return {"message": "Default location updated"}
        
    except HTTPException:
        raise
    except Exception as e:
        await db.rollback()
        raise HTTPException(status_code=500, detail=str(e))
