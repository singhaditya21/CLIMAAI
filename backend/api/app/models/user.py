"""
User model with authentication and preferences.
"""
from sqlalchemy import Column, String, Boolean, JSON, Integer
from sqlalchemy.types import Uuid
from sqlalchemy.orm import relationship
import uuid
from ..database import Base


class User(Base):
    __tablename__ = "users"
    
    id = Column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email = Column(String(255), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=True)  # Can be null for OAuth
    full_name = Column(String(255))
    
    # Authentication
    is_active = Column(Boolean, default=True, nullable=False)
    is_verified = Column(Boolean, default=False, nullable=False)
    
    # Device info
    device_token = Column(String(500))  # For push notifications
    platform = Column(String(20))  # ios, android
    
    # Preferences
    preferences = Column(JSON, default={
        "temperature_unit": "celsius",  # celsius, fahrenheit
        "wind_speed_unit": "kmh",  # kmh, mph, ms
        "precipitation_unit": "mm",  # mm, inch
        "time_format": "24h",  # 24h, 12h
        "notifications_enabled": True,
        "theme": "auto",  # light, dark, auto
    })
    
    # Location preferences
    default_latitude = Column(String(50))
    default_longitude = Column(String(50))
    default_location_name = Column(String(255))
    
    # Relationships
    subscriptions = relationship("Subscription", back_populates="user", cascade="all, delete-orphan")
    
    def __repr__(self):
        return f"<User {self.email}>"
