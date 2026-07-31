"""Device token registration and notification preferences.

These cover two Postgres-only code paths — the jsonb cast when writing
preferences, and the parameterised token update — plus the injection regression
for device tokens, which were also interpolated into SQL previously.
"""
import pytest
from sqlalchemy import text

REGISTER = "/notifications/register-device"
UNREGISTER = "/notifications/unregister-device"
PREFERENCES = "/notifications/preferences"


async def test_register_device_token(auth_client):
    response = await auth_client.post(
        REGISTER,
        json={"token": "device-token-1", "platform": "ios", "device_info": {"model": "iPhone15"}},
    )

    assert response.status_code == 200, response.text
    assert response.json()["platform"] == "ios"


async def test_registering_the_same_token_twice_is_idempotent(auth_client, db_session):
    payload = {"token": "device-token-2", "platform": "ios", "device_info": {}}

    await auth_client.post(REGISTER, json=payload)
    await auth_client.post(REGISTER, json=payload)

    count = await db_session.execute(
        text("SELECT count(*) FROM device_tokens WHERE token = :token"),
        {"token": "device-token-2"},
    )
    assert count.scalar_one() == 1


async def test_unregister_deactivates_the_token(auth_client, db_session):
    await auth_client.post(
        REGISTER, json={"token": "device-token-3", "platform": "android", "device_info": {}}
    )

    # The handler declares `token: str = Body(...)` unembedded, so the body is
    # the bare string rather than an object.
    response = await auth_client.post(UNREGISTER, json="device-token-3")
    assert response.status_code == 200, response.text

    active = await db_session.execute(
        text("SELECT is_active FROM device_tokens WHERE token = :token"),
        {"token": "device-token-3"},
    )
    assert active.scalar_one() is False


async def test_device_registration_requires_authentication(client):
    response = await client.post(
        REGISTER, json={"token": "x", "platform": "ios", "device_info": {}}
    )

    assert response.status_code in (401, 403)


async def test_rejects_unknown_platform(auth_client):
    response = await auth_client.post(
        REGISTER, json={"token": "device-token-4", "platform": "blackberry", "device_info": {}}
    )

    # Either schema validation or the CHECK constraint must refuse it.
    assert response.status_code != 200


@pytest.mark.parametrize(
    "payload",
    ["tok'); DROP TABLE device_tokens; --", "' OR '1'='1", "x'; DELETE FROM users; --"],
)
async def test_token_injection_payload_is_stored_literally(auth_client, db_session, payload):
    response = await auth_client.post(
        REGISTER, json={"token": payload, "platform": "ios", "device_info": {}}
    )
    assert response.status_code == 200, response.text

    stored = await db_session.execute(
        text("SELECT token FROM device_tokens WHERE token = :token"), {"token": payload}
    )
    assert stored.scalar_one() == payload

    tables = await db_session.execute(
        text("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
    )
    assert {"users", "device_tokens"} <= {row[0] for row in tables}


async def test_get_default_preferences(auth_client):
    response = await auth_client.get(PREFERENCES)

    assert response.status_code == 200, response.text
    preferences = response.json()["preferences"]
    assert set(preferences) >= {"weather_alerts", "daily_summary", "severe_weather"}


async def test_update_preferences_persists(auth_client):
    """Exercises the jsonb cast; this raised AttributeError before the model fix."""
    response = await auth_client.put(
        PREFERENCES, json={"weather_alerts": False, "daily_summary": True}
    )

    assert response.status_code == 200, response.text
    updated = response.json()["preferences"]
    assert updated["weather_alerts"] is False
    assert updated["daily_summary"] is True

    reread = (await auth_client.get(PREFERENCES)).json()["preferences"]
    assert reread["weather_alerts"] is False
    assert reread["daily_summary"] is True


async def test_partial_preference_update_leaves_others_alone(auth_client):
    await auth_client.put(
        PREFERENCES,
        json={"weather_alerts": True, "daily_summary": True, "severe_weather": True},
    )

    await auth_client.put(PREFERENCES, json={"daily_summary": False})

    preferences = (await auth_client.get(PREFERENCES)).json()["preferences"]
    assert preferences["daily_summary"] is False
    assert preferences["weather_alerts"] is True
    assert preferences["severe_weather"] is True
