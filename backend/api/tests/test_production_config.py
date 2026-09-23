"""Production configuration must reject settings that expose accounts."""

import pytest
from pydantic import ValidationError

from app.config import Settings


SECRET = "S3cure-Mixed-Long-Key-For-Unit-Checks-2026"


def test_production_requires_unique_jwt_secret_and_restricted_cors():
    with pytest.raises(ValidationError, match="JWT_SECRET"):
        Settings(_env_file=None, ENVIRONMENT="production", CORS_ORIGINS=[])

    with pytest.raises(ValidationError, match="CORS_ORIGINS"):
        Settings(_env_file=None, ENVIRONMENT="production", JWT_SECRET=SECRET)

    with pytest.raises(ValidationError, match="DEBUG"):
        Settings(
            _env_file=None, ENVIRONMENT="production", JWT_SECRET=SECRET,
            CORS_ORIGINS=[], DEBUG=True,
        )

    with pytest.raises(ValidationError, match="DEMO_MODE"):
        Settings(
            _env_file=None, ENVIRONMENT="production", JWT_SECRET=SECRET,
            CORS_ORIGINS=[], DEMO_MODE=True,
        )

    with pytest.raises(ValidationError, match="AI insights"):
        Settings(
            _env_file=None, ENVIRONMENT="production", JWT_SECRET=SECRET,
            CORS_ORIGINS=[], ENABLE_AI_INSIGHTS=True,
        )

    safe = Settings(
        _env_file=None, ENVIRONMENT="production", JWT_SECRET=SECRET,
        CORS_ORIGINS=[], DEBUG=False,
    )
    assert safe.ENABLE_AI_INSIGHTS is False
