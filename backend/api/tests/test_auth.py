"""Registration, login and token handling.

These exercise bcrypt through passlib. A bcrypt version incompatible with
passlib 1.7.4 breaks every hash, so these tests double as a guard on that pin.
"""
import pytest

VALID = {
    "email": "new-user@example.com",
    "password": "Test1234!",
    "full_name": "New User",
    "platform": "ios",
}


async def test_register_returns_a_token(client):
    response = await client.post("/api/auth/register", json=VALID)

    assert response.status_code == 201, response.text
    body = response.json()
    assert body["access_token"]
    assert body["token_type"].lower() == "bearer"


async def test_register_rejects_duplicate_email(client):
    first = await client.post("/api/auth/register", json=VALID)
    assert first.status_code == 201

    second = await client.post("/api/auth/register", json=VALID)

    assert second.status_code == 400
    assert "already" in second.json()["detail"].lower()


async def test_register_requires_platform(client):
    payload = {k: v for k, v in VALID.items() if k != "platform"}

    response = await client.post("/api/auth/register", json=payload)

    assert response.status_code == 422


async def test_register_rejects_malformed_email(client):
    response = await client.post("/api/auth/register", json={**VALID, "email": "not-an-email"})

    assert response.status_code == 422


async def test_login_succeeds_with_correct_password(client):
    await client.post("/api/auth/register", json=VALID)

    response = await client.post(
        "/api/auth/login", json={"email": VALID["email"], "password": VALID["password"]}
    )

    assert response.status_code == 200, response.text
    assert response.json()["access_token"]


async def test_login_rejects_wrong_password(client):
    await client.post("/api/auth/register", json=VALID)

    response = await client.post(
        "/api/auth/login", json={"email": VALID["email"], "password": "WrongPassword1!"}
    )

    assert response.status_code == 401


async def test_login_rejects_unknown_email(client):
    response = await client.post(
        "/api/auth/login", json={"email": "nobody@example.com", "password": "Test1234!"}
    )

    assert response.status_code == 401


async def test_password_is_not_stored_in_clear(client, db_session):
    from sqlalchemy import text

    await client.post("/api/auth/register", json=VALID)

    stored = await db_session.execute(
        text("SELECT password_hash FROM users WHERE email = :email"),
        {"email": VALID["email"]},
    )
    password_hash = stored.scalar_one()

    assert password_hash != VALID["password"]
    assert password_hash.startswith("$2")  # bcrypt


async def test_me_returns_the_authenticated_user(auth_client):
    response = await auth_client.get("/api/auth/me")

    assert response.status_code == 200, response.text
    assert response.json()["email"] == "fixture-user@example.com"


async def test_me_requires_a_token(client):
    response = await client.get("/api/auth/me")

    assert response.status_code in (401, 403)


async def test_me_rejects_a_garbage_token(client):
    response = await client.get(
        "/api/auth/me", headers={"Authorization": "Bearer not-a-real-token"}
    )

    assert response.status_code == 401
