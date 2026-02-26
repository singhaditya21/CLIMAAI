import pytest
from httpx import AsyncClient

@pytest.mark.asyncio
async def test_register_user(client: AsyncClient):
    response = await client.post("/api/auth/register", json={
        "email": "test@example.com",
        "password": "password123",
        "full_name": "Test User",
        "platform": "ios"
    })
    assert response.status_code == 201
    data = response.json()
    assert "access_token" in data
    assert data["user"]["email"] == "test@example.com"

@pytest.mark.asyncio
async def test_login_user(client: AsyncClient):
    # Register first
    await client.post("/api/auth/register", json={
        "email": "login@example.com",
        "password": "password123",
        "full_name": "Login User",
        "platform": "android"
    })

    # Login
    response = await client.post("/api/auth/login", json={
        "email": "login@example.com",
        "password": "password123"
    })
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data

@pytest.mark.asyncio
async def test_get_me(client: AsyncClient):
    # Register
    reg_response = await client.post("/api/auth/register", json={
        "email": "me@example.com",
        "password": "password123",
        "full_name": "Me User",
        "platform": "web"
    })
    assert reg_response.status_code == 201
    token = reg_response.json()["access_token"]

    # Get me
    response = await client.get("/api/auth/me", headers={
        "Authorization": f"Bearer {token}"
    })
    assert response.status_code == 200
    assert response.json()["email"] == "me@example.com"

@pytest.mark.asyncio
async def test_duplicate_registration(client: AsyncClient):
    payload = {
        "email": "duplicate@test.com",
        "password": "password123",
        "full_name": "Dupe User",
        "platform": "web"
    }
    # First registration
    resp1 = await client.post("/api/auth/register", json=payload)
    assert resp1.status_code == 201

    # Second registration
    resp2 = await client.post("/api/auth/register", json=payload)
    assert resp2.status_code == 400
    assert resp2.json()["detail"] == "Email already registered"

@pytest.mark.asyncio
async def test_login_invalid_credentials(client: AsyncClient):
    response = await client.post("/api/auth/login", json={
        "email": "nonexistent@test.com",
        "password": "wrongpassword"
    })
    assert response.status_code == 401
