"""
Weather history model for tracking historical data.
Required for trend-based health indices (e.g., Migraine pressure trends).
"""
from sqlalchemy import Column, Float, Index
from sqlalchemy.dialects.postgresql import UUID
from ..database import Base
import uuid

class WeatherHistory(Base):
    __tablename__ = "weather_history"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)

    # Core metrics needed for history/health
    temperature = Column(Float)
    pressure_msl = Column(Float)  # Mean Sea Level Pressure in hPa
    humidity = Column(Float)

    # Index for fast lookup of location history sorted by time
    __table_args__ = (
        Index('idx_weather_history_loc_time', 'latitude', 'longitude', 'created_at'),
    )

    def __repr__(self):
        return f"<WeatherHistory {self.latitude},{self.longitude} @ {self.created_at}>"
