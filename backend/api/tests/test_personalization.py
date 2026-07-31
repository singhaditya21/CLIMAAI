"""Personalization endpoints.

Every handler here previously defaulted to `user_id: str = "demo_user"`, so the
router ignored authentication entirely and all callers shared a single profile.
These tests pin both halves of the fix: auth is required, and two users get
separate profiles.
"""
import pytest

TRACK = "/personalization/track"
PROFILE = "/personalization/profile"


@pytest.mark.parametrize("path", [TRACK, PROFILE, "/personalization/should-notify"])
async def test_endpoints_require_authentication(client, path):
    response = await client.get(path) if path != TRACK else await client.post(
        path, json={"event_type": "app_open", "event_data": {}}
    )

    assert response.status_code in (401, 403), (
        f"{path} answered {response.status_code} without a token"
    )


async def test_track_event_accepts_an_authenticated_call(auth_client):
    response = await auth_client.post(
        TRACK,
        json={
            "event_type": "app_open",
            "event_data": {"screen": "home"},
            "weather_context": {"temperature": 18.0},
        },
    )

    assert response.status_code == 200, response.text


async def test_profile_is_scoped_to_the_authenticated_user(client):
    """Two users must not share one profile, as they did with "demo_user"."""
    tokens = {}
    for name in ("alpha", "beta"):
        registered = await client.post(
            "/api/auth/register",
            json={
                "email": f"{name}-personalize@example.com",
                "password": "Test1234!",
                "full_name": name,
                "platform": "ios",
            },
        )
        assert registered.status_code == 201, registered.text
        tokens[name] = registered.json()["access_token"]

    # alpha records activity
    client.headers["Authorization"] = f"Bearer {tokens['alpha']}"
    for _ in range(3):
        await client.post(
            TRACK, json={"event_type": "app_open", "event_data": {"screen": "radar"}}
        )
    alpha_profile = (await client.get(PROFILE)).json()

    # beta records nothing
    client.headers["Authorization"] = f"Bearer {tokens['beta']}"
    beta_profile = (await client.get(PROFILE)).json()

    assert alpha_profile["user_id"] != beta_profile["user_id"]
