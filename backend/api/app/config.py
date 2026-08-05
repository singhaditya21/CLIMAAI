"""
Configuration management using Pydantic BaseSettings.
Loads environment variables for database, Redis, OpenAI, and Open-Meteo.
"""
from pydantic_settings import BaseSettings
from functools import lru_cache
from typing import Any, Dict, Tuple
from urllib.parse import urlsplit, urlunsplit


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
    # The air-quality host carries the "-api" suffix; the bare
    # air-quality.open-meteo.com does not resolve, so the old default made
    # every backend air-quality fetch fail DNS and silently return None.
    OPEN_METEO_AIR_QUALITY_URL: str = "https://air-quality-api.open-meteo.com/v1"
    # Commercial Open-Meteo licence key (API subscription). Empty means the
    # free non-commercial tier; once set, open_meteo_request() moves every
    # Open-Meteo call to the licensed customer- host with the key attached.
    # Full flip procedure: docs/LICENSING.md.
    OPEN_METEO_API_KEY: str = ""
    
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
    
    def open_meteo_request(
        self, url: str, params: Dict[str, Any]
    ) -> Tuple[str, Dict[str, Any]]:
        """Rewrite an Open-Meteo request for the licence currently held.

        Without a key the request passes through untouched (free,
        non-commercial tier). With a key, the host gains Open-Meteo's
        ``customer-`` prefix — api.open-meteo.com becomes
        customer-api.open-meteo.com, and the air-quality, archive and
        geocoding hosts follow the same shape — and the key rides along as
        the ``apikey`` query parameter, which is how the paid API
        authenticates. Centralised here so every call site flips with the
        one env var and none can drift onto the free tier once licensed.

        Hosts outside open-meteo.com are never rewritten: a self-hosted
        Open-Meteo instance takes no key.
        """
        if not self.OPEN_METEO_API_KEY:
            return url, params
        parts = urlsplit(url)
        if not parts.netloc.endswith(".open-meteo.com"):
            return url, params
        if not parts.netloc.startswith("customer-"):
            url = urlunsplit(parts._replace(netloc=f"customer-{parts.netloc}"))
        return url, {**params, "apikey": self.OPEN_METEO_API_KEY}

    class Config:
        env_file = ".env"
        case_sensitive = True


@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()
