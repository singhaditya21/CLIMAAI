"""Favourite locations CRUD, plus SQL-injection regressions.

The injection cases matter: these handlers previously built SQL with f-strings
and interpolated the caller-supplied location name directly, so a crafted name
could terminate the statement. The payloads below must round-trip as literal
text and leave the schema intact.
"""
import pytest
from sqlalchemy import text

FAVOURITES = "/api/locations/favorites"

INJECTION_PAYLOADS = [
    "x'); DROP TABLE weather_alerts; --",
    "'; DELETE FROM favorite_locations; --",
    "Paris'--",
    "\\'; DROP TABLE users; --",
    "' OR '1'='1",
]


async def _add(client, name="London", lat=51.5074, lon=-0.1278, default=False):
    return await client.post(
        FAVOURITES,
        params={"name": name, "latitude": lat, "longitude": lon, "is_default": default},
    )


async def test_add_and_list_favourite(auth_client):
    created = await _add(auth_client, name="London")
    assert created.status_code == 200, created.text

    listed = await auth_client.get(FAVOURITES)
    assert listed.status_code == 200
    body = listed.json()
    assert body["count"] == 1
    assert body["favorites"][0]["name"] == "London"
    assert body["favorites"][0]["latitude"] == pytest.approx(51.5074)


async def test_favourites_require_authentication(client):
    response = await client.get(FAVOURITES)

    assert response.status_code in (401, 403)


async def test_users_cannot_see_each_others_favourites(client):
    first = await client.post(
        "/api/auth/register",
        json={"email": "a@example.com", "password": "Test1234!", "full_name": "A", "platform": "ios"},
    )
    second = await client.post(
        "/api/auth/register",
        json={"email": "b@example.com", "password": "Test1234!", "full_name": "B", "platform": "ios"},
    )
    a_token = first.json()["access_token"]
    b_token = second.json()["access_token"]

    client.headers["Authorization"] = f"Bearer {a_token}"
    await _add(client, name="A-Location")

    client.headers["Authorization"] = f"Bearer {b_token}"
    listed = await client.get(FAVOURITES)

    assert listed.json()["count"] == 0


async def test_setting_default_unsets_the_previous_default(auth_client):
    await _add(auth_client, name="London", lat=51.5, lon=-0.12, default=True)
    second = await _add(auth_client, name="Paris", lat=48.85, lon=2.35, default=False)
    paris_id = second.json()["location"]["id"]

    response = await auth_client.patch(f"{FAVOURITES}/{paris_id}/default")
    assert response.status_code == 200, response.text

    favourites = (await auth_client.get(FAVOURITES)).json()["favorites"]
    defaults = [f["name"] for f in favourites if f["is_default"]]
    assert defaults == ["Paris"]


async def test_delete_removes_the_favourite(auth_client):
    created = await _add(auth_client, name="Tokyo", lat=35.68, lon=139.69)
    location_id = created.json()["location"]["id"]

    deleted = await auth_client.delete(f"{FAVOURITES}/{location_id}")
    assert deleted.status_code == 200

    assert (await auth_client.get(FAVOURITES)).json()["count"] == 0


async def test_deleting_a_missing_favourite_is_404(auth_client):
    response = await auth_client.delete(f"{FAVOURITES}/999999")

    assert response.status_code == 404


async def test_rejects_out_of_range_coordinates(auth_client):
    response = await _add(auth_client, name="Nowhere", lat=999.0, lon=0.0)

    assert response.status_code == 422


@pytest.mark.parametrize("payload", INJECTION_PAYLOADS)
async def test_injection_payload_is_stored_as_literal_text(auth_client, payload):
    """A crafted name must be data, never executed SQL."""
    created = await _add(auth_client, name=payload, lat=1.0, lon=1.0)

    assert created.status_code == 200, created.text
    assert created.json()["location"]["name"] == payload

    listed = await auth_client.get(FAVOURITES)
    assert listed.json()["favorites"][0]["name"] == payload


@pytest.mark.parametrize("payload", INJECTION_PAYLOADS)
async def test_injection_payload_leaves_schema_intact(auth_client, db_session, payload):
    await _add(auth_client, name=payload, lat=2.0, lon=2.0)

    tables = await db_session.execute(
        text("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
    )
    present = {row[0] for row in tables}

    assert {"users", "favorite_locations", "device_tokens", "weather_alerts"} <= present
