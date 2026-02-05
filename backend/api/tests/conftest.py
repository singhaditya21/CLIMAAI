import pytest
from unittest.mock import AsyncMock, patch
from fastapi.testclient import TestClient
from app.main import app

@pytest.fixture
def client():
    # Mock init_db to avoid database connection attempt
    with patch("app.main.init_db", new_callable=AsyncMock):
        with TestClient(app) as c:
            yield c
