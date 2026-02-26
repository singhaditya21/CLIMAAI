import pytest
from httpx import AsyncClient
from unittest.mock import AsyncMock
from datetime import datetime

@pytest.mark.asyncio
async def test_get_alerts_by_point(client: AsyncClient):
    from app.services.alerts_service import get_alerts_service
    from app.main import app

    mock_service = AsyncMock()

    class MockResponse:
        def __init__(self):
            self.alerts = []
            self.location = {"lat": 40.7128, "lon": -74.0060}
            self.updated = datetime.utcnow()
            self.total_count = 0
            self.has_severe = False

    mock_service.get_alerts_by_point.return_value = MockResponse()
    app.dependency_overrides[get_alerts_service] = lambda: mock_service

    try:
        response = await client.get("/api/weather/alerts?latitude=40.7128&longitude=-74.0060")
        assert response.status_code == 200
    finally:
        app.dependency_overrides.clear()

@pytest.mark.asyncio
async def test_get_alerts_by_state(client: AsyncClient):
    from app.services.alerts_service import get_alerts_service
    from app.main import app

    mock_service = AsyncMock()

    class MockResponse:
        def __init__(self):
            self.alerts = []
            self.location = {"state": "NY"}
            self.updated = datetime.utcnow()
            self.total_count = 0
            self.has_severe = False

    mock_service.get_alerts_by_state.return_value = MockResponse()
    app.dependency_overrides[get_alerts_service] = lambda: mock_service

    try:
        response = await client.get("/api/weather/alerts/state/NY")
        if response.status_code != 200:
            print(f"Error: {response.json()}")
        assert response.status_code == 200
        data = response.json()
        assert data["location"]["state"] == "NY"
    finally:
        app.dependency_overrides.clear()

@pytest.mark.asyncio
async def test_invalid_state_code(client: AsyncClient):
    response = await client.get("/api/weather/alerts/state/NEWYORK")
    assert response.status_code == 400
