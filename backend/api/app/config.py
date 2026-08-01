"""
Configuration management using Pydantic BaseSettings.
Loads environment variables for database, Redis, OpenAI, and Open-Meteo.
"""
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # Application
    APP_NAME: str = "ClimaAI API"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    
    # Database
    DATABASE_URL: str = "postgresql+asyncpg://climaai:climaai123@localhost:5432/climaai"
    
    # Redis
    REDIS_URL: str = "redis://localhost:6379/0"
    WEATHER_CACHE_TTL: int = 1800  # 30 minutes
    AI_CACHE_TTL: int = 3600  # 1 hour
    
    # OpenAI
    OPENAI_API_KEY: str = ""
    OPENAI_MODEL: str = "gpt-4-turbo-preview"
    
    # Open-Meteo API
    OPEN_METEO_BASE_URL: str = "https://api.open-meteo.com/v1"
    OPEN_METEO_AIR_QUALITY_URL: str = "https://air-quality.open-meteo.com/v1"
    
    # JWT
    JWT_SECRET: str = "your-secret-key-change-in-production"
    JWT_ALGORITHM: str = "HS256"
    JWT_EXPIRATION_HOURS: int = 24 * 30  # 30 days
    
    # CORS
    CORS_ORIGINS: list = ["*"]
    
    # Subscription
    APPLE_BUNDLE_ID: str = "com.climaai.app"
    GOOGLE_PACKAGE_NAME: str = "com.climaai.app"
    GOOGLE_PRODUCT_ID_MONTHLY: str = "climaai_pro_monthly"
    GOOGLE_PRODUCT_ID_ANNUAL: str = "climaai_pro_yearly"
    
    # Receipt Validation
    APPLE_SHARED_SECRET: str = ""  # App Store Connect shared secret
    GOOGLE_SERVICE_ACCOUNT_JSON: str = ""  # JSON string of service account key
    
    # Feature flags
    ENABLE_AI_INSIGHTS: bool = True
    # Mounts the /demo router, which serves generated mock weather with no
    # external API dependencies. Keep off in production.
    DEMO_MODE: bool = False
    
    # External Weather API Keys (for multi-source weather service)
    OPENWEATHERMAP_API_KEY: str = ""   # https://openweathermap.org/api
    WEATHERBIT_API_KEY: str = ""       # https://www.weatherbit.io/api
    STORMGLASS_API_KEY: str = ""       # https://stormglass.io/
    OPENUV_API_KEY: str = ""           # https://www.openuv.io/
    METEOBLUE_API_KEY: str = ""        # https://www.meteoblue.com/en/weather-api
    NOAA_API_KEY: str = ""             # https://www.ncdc.noaa.gov/cdo-web/webservices/v2
    PIRATE_WEATHER_API_KEY: str = ""   # https://pirateweather.net (20k/month free)
    WEATHERAPI_KEY: str = ""           # https://www.weatherapi.com (1M/month free)
    
    class Config:
        env_file = ".env"
        case_sensitive = True


@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()
