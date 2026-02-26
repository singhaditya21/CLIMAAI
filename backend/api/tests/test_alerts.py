import pytest
from httpx import AsyncClient
from unittest.mock import AsyncMock, patch
from datetime import datetime

@pytest.mark.asyncio
async def test_get_alerts_by_point(client: AsyncClient):
    with patch("app.services.alerts_service.AlertsService") as MockServiceClass:
        mock_instance = MockServiceClass.return_value
        class MockResponse:
            def __init__(self):
                self.alerts = []
                self.location = {"lat": 40.7128, "lon": -74.0060}
                self.updated = datetime.utcnow()
                self.total_count = 0
                self.has_severe = False
        mock_instance.get_alerts_by_point = AsyncMock(return_value=MockResponse())
        mock_instance.close = AsyncMock()
        response = await client.get("/api/weather/alerts?latitude=40.7128&longitude=-74.0060")
        assert response.status_code == 200

@pytest.mark.asyncio
async def test_invalid_state_code(client: AsyncClient):
    response = await client.get("/api/weather/alerts/state/NEWYORK")
    assert response.status_code == 400
