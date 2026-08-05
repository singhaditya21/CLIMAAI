"""DELETE /api/auth/me — the account-deletion flow Play reviewers exercise.

Deletion must take every trace of the account with it: subscription rows,
favorite locations, device tokens, weather alerts, and the personalization
state that lives outside Postgres. And the bearer token must stop working the
moment the account is gone — a deleted user whose token still authenticates
has not been deleted.
"""
from sqlalchemy import text

from app.services.personalization_service import personalization_service

DELETE_ME = "/api/auth/me"


async def _count(db_session, table: str) -> int:
    result = await db_session.execute(text(f"SELECT COUNT(*) FROM {table}"))
    return result.scalar_one()


async def _seed_user_data(auth_client, db_session) -> str:
    """Give the fixture user one row in every user-owned table.

    Returns the user id, fetched before deletion makes it unavailable.
    """
    me = await auth_client.get("/api/auth/me")
    assert me.status_code == 200, me.text
    user_id = me.json()["id"]

    trial = await auth_client.post(
        "/api/subscriptions/trial",
        json={"platform": "apple", "plan": "monthly", "receipt_data": "-"},
    )
    assert trial.status_code == 201, trial.text

    favorite = await auth_client.post(
        "/api/locations/favorites",
        params={"name": "Home", "latitude": 52.52, "longitude": 13.405},
    )
    assert favorite.status_code == 200, favorite.text

    device = await auth_client.post(
        "/notifications/register-device",
        json={"token": "delete-me-device-token", "platform": "ios"},
    )
    assert device.status_code == 200, device.text

    # No API creates alerts directly; the table still references the user.
    await db_session.execute(
        text(
            """
            INSERT INTO weather_alerts (user_id, alert_type, severity, title, message)
            VALUES (CAST(:user_id AS uuid), 'severe_weather', 'HIGH', 'Storm', 'Take cover')
            """
        ),
        {"user_id": user_id},
    )
    await db_session.commit()

    return user_id


async def test_delete_removes_the_user(auth_client, db_session):
    response = await auth_client.delete(DELETE_ME)

    assert response.status_code == 204, response.text
    assert await _count(db_session, "users") == 0


async def test_delete_cascades_to_every_user_owned_table(auth_client, db_session):
    await _seed_user_data(auth_client, db_session)

    response = await auth_client.delete(DELETE_ME)
    assert response.status_code == 204, response.text

    for table in ("subscriptions", "favorite_locations", "device_tokens", "weather_alerts"):
        assert await _count(db_session, table) == 0, f"{table} rows survived account deletion"


async def test_the_token_stops_working_the_moment_the_account_is_gone(auth_client):
    """JWTs are stateless; revocation *is* the user row disappearing."""
    deleted = await auth_client.delete(DELETE_ME)
    assert deleted.status_code == 204

    me = await auth_client.get("/api/auth/me")
    assert me.status_code == 401

    update = await auth_client.put("/api/auth/me", json={"full_name": "Ghost"})
    assert update.status_code == 401

    second_delete = await auth_client.delete(DELETE_ME)
    assert second_delete.status_code == 401


async def test_delete_purges_personalization_state(auth_client):
    """The personalization store is not in Postgres, so no cascade reaches it."""
    me = await auth_client.get("/api/auth/me")
    user_id = me.json()["id"]

    tracked = await auth_client.post(
        "/personalization/track",
        json={"event_type": "screen_view", "event_data": {"screen": "home"}},
    )
    assert tracked.status_code == 200, tracked.text
    profile = await auth_client.get("/personalization/profile")
    assert profile.status_code == 200, profile.text

    assert user_id in personalization_service._events
    assert user_id in personalization_service._profiles

    response = await auth_client.delete(DELETE_ME)
    assert response.status_code == 204

    assert user_id not in personalization_service._events
    assert user_id not in personalization_service._profiles


async def test_the_email_is_free_for_reregistration_after_deletion(auth_client, client):
    """A deleted account must not squat its email address."""
    await auth_client.delete(DELETE_ME)

    # auth_client and client share headers; drop the dead token explicitly.
    client.headers.pop("Authorization", None)
    response = await client.post(
        "/api/auth/register",
        json={
            "email": "fixture-user@example.com",
            "password": "Test1234!",
            "full_name": "Fixture User Again",
            "platform": "android",
        },
    )

    assert response.status_code == 201, response.text
