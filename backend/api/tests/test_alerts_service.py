"""Alerts response shaping and the non-US degradation path.

Both cases here were live 500s: the state endpoint could not serialise its own
response, and any coordinate outside NWS coverage raised instead of returning
an empty result.
"""
from datetime import datetime, timezone

import pytest

from app.schemas.radar_alerts import AlertsListResponse
from app.services.alerts_service import AlertsResponse, AlertsService


class _StubResponse:
    def __init__(self, status_code, payload=None):
        self.status_code = status_code
        self._payload = payload or {}

    def raise_for_status(self):
        if self.status_code >= 400:
            raise AssertionError("raise_for_status should not be reached for 400")

    def json(self):
        return self._payload


class _StubClient:
    def __init__(self, status_code, payload=None):
        self._status_code = status_code
        self._payload = payload

    async def get(self, url, params=None, **kwargs):
        return _StubResponse(self._status_code, self._payload)


@pytest.fixture
def service():
    svc = AlertsService()
    svc.redis_client = None
    return svc


def test_alerts_response_accepts_a_state_location():
    """The state endpoint passes a string; Dict[str, float] rejected it."""
    response = AlertsResponse(
        alerts=[],
        location={"state": "NY"},
        updated=datetime.now(timezone.utc),
        total_count=0,
    )

    assert response.location["state"] == "NY"


def test_alerts_response_still_accepts_coordinates():
    response = AlertsResponse(
        alerts=[],
        location={"latitude": 40.7, "longitude": -74.0},
        updated=datetime.now(timezone.utc),
        total_count=0,
    )

    assert response.location["latitude"] == 40.7


def test_alerts_list_response_accepts_a_state_location():
    """Same flaw in the router's response_model, which 500'd separately."""
    response = AlertsListResponse(
        alerts=[],
        location={"state": "NY"},
        updated=datetime.now(timezone.utc),
        total_count=0,
        has_severe=False,
    )

    assert response.location["state"] == "NY"


async def test_non_us_point_returns_empty_rather_than_raising(service, monkeypatch):
    """NWS answers 400 outside the US; that is 'no alerts', not an error."""
    service.http_client = _StubClient(400)

    async def no_redis():
        raise RuntimeError("no redis in tests")

    monkeypatch.setattr(service, "_get_redis", no_redis)

    result = await service.get_alerts_by_point(51.5, -0.12)

    assert result.total_count == 0
    assert result.alerts == []
    assert result.location == {"latitude": 51.5, "longitude": -0.12}


async def test_us_point_parses_alerts(service, monkeypatch):
    payload = {
        "features": [
            {
                "properties": {
                    "id": "urn:oid:1.2.3",
                    "event": "Flood Watch",
                    "headline": "Flood Watch in effect",
                    "description": "Heavy rain expected.",
                    "instruction": "Avoid low-lying areas.",
                    "severity": "Severe",
                    "urgency": "Expected",
                    "certainty": "Likely",
                    "senderName": "NWS New York",
                    "areaDesc": "Kings; Queens",
                }
            }
        ]
    }
    service.http_client = _StubClient(200, payload)

    async def no_redis():
        raise RuntimeError("no redis in tests")

    monkeypatch.setattr(service, "_get_redis", no_redis)

    result = await service.get_alerts_by_point(40.7, -74.0)

    assert result.total_count == 1
    assert result.alerts[0].event == "Flood Watch"
    assert result.alerts[0].areas == ["Kings", "Queens"]
