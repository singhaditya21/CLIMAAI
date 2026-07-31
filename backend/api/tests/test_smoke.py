"""App-level wiring checks.

Deliberately cheap and broad: most of the outages found in this codebase were
import-time or startup failures (a missing dependency, a model out of sync with
the schema) that any request at all would have caught.
"""
from app.config import get_settings


async def test_health_endpoint(client):
    response = await client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "healthy"


async def test_root_endpoint(client):
    response = await client.get("/")

    assert response.status_code == 200
    assert "version" in response.json()


async def test_openapi_schema_builds(client):
    """A model/schema mismatch usually shows up here first."""
    response = await client.get("/openapi.json")

    assert response.status_code == 200
    assert len(response.json()["paths"]) > 20


async def test_demo_router_follows_the_flag(client):
    """/demo serves generated mock data and must not be mounted by default."""
    response = await client.get("/demo/")

    if get_settings().DEMO_MODE:
        assert response.status_code == 200
    else:
        assert response.status_code == 404


def test_user_model_matches_the_schema_columns():
    """Guards the class of bug where a column exists in SQL but not the model.

    notification_preferences is the concrete case: 002_add_features.sql adds it
    and both /notifications/preferences handlers read it, but the model lacked
    it, so each request raised AttributeError.
    """
    from app.models import User

    columns = {c.name for c in User.__table__.columns}

    assert {"notification_preferences", "preferences", "reset_token", "email"} <= columns
